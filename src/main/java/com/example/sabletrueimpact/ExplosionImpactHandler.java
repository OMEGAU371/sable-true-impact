package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.joml.Vector3d;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ExplosionImpactHandler {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels");
    private static final Method BOUNDING_BOX = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_CENTER_OF_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
    private static final java.lang.reflect.Field RUNTIME_ID_FIELD = findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "runtimeId");
    private static final Method IS_SLEEPING = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "isSleeping");
    private static final Method WAKE_UP = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "wakeUp");

    private ExplosionImpactHandler() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Vec3 center = event.getExplosion().center();
        double radius = Math.max(0.0, event.getExplosion().radius());
        if (radius <= 0.0) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();
        int rays = 0;
        int hitsCount = 0;
        int fractures = 0;

        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) return;
            
            double searchRadius = radius * TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get();
            List<SubLevelEntry> nearby = nearbySubLevels(container, center, searchRadius);
            if (nearby.isEmpty()) return;

            WaveScan scan = scanShockwave(level, center, radius, searchRadius, nearby);
            rays = scan.rays();
            hitsCount = scan.hits().size();
            
            double confinement = 1.0 + (scan.blockedRatio() * scan.blockedRatio()) * TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.get();
            int maxSubLevels = TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();
            int processed = 0;

            for (SubLevelEntry entry : nearby) {
                WaveHit hit = scan.hits().get(entry.subLevel());
                if (hit == null) continue;
                if (processed >= maxSubLevels) break;

                // Shockwaves for fracture ignore some surface hardness and focus on internal connections
                double force = hit.pressure() * confinement;
                if (force < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get() * 0.25) continue;

                Vector3d localPoint = new Vector3d(hit.point().x, hit.point().y, hit.point().z);
                Vector3d normal = new Vector3d(hit.direction());
                if (normal.lengthSquared() < 1e-8) normal.set(0, 1, 0);

                SubLevelFracture.tryFracture(entry.subLevel(), localPoint, normal, force);
                if (TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.get()) {
                    applyExplosionImpulse(level, entry.subLevel(), normal, force);
                }
                
                processed++;
                fractures++;
            }
        } catch (Exception ignored) {
        } finally {
            TrueImpactPerformance.recordExplosionImpact(startedAt, rays, hitsCount, fractures);
        }
    }

    private static WaveScan scanShockwave(ServerLevel level, Vec3 center, double radius, double searchRadius, List<SubLevelEntry> subLevels) {
        Map<Object, WaveHit> hits = new IdentityHashMap<>();
        int blocked = 0;
        int samples = TrueImpactConfig.EXPLOSION_IMPACT_RAY_SAMPLES.get();
        double stepSize = TrueImpactConfig.EXPLOSION_IMPACT_RAY_STEP.get();
        int steps = Math.max(1, (int) Math.ceil(searchRadius / stepSize));

        for (int ray = 0; ray < samples; ray++) {
            Vec3 direction = rayDirection(ray, samples);
            boolean isBlocked = traceRay(level, center, direction, radius, stepSize, steps, subLevels, hits);
            if (isBlocked) blocked++;
        }
        return new WaveScan(hits, blocked / (double) Math.max(samples, 1), samples);
    }

    private static boolean traceRay(ServerLevel level, Vec3 center, Vec3 direction, double radius, double stepSize, int steps, List<SubLevelEntry> subLevels, Map<Object, WaveHit> hits) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double remainingPressure = radius * radius * TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.get();
        boolean metObstacle = false;

        for (int step = 1; step <= steps; step++) {
            double distance = step * stepSize;
            if (remainingPressure <= 1.0) break;

            Vec3 point = center.add(direction.scale(distance));
            pos.set(point.x, point.y, point.z);
            
            // Check for SubLevel collision
            for (SubLevelEntry entry : subLevels) {
                if (entry.bounds().contains(point)) {
                    double falloff = 1.0 / (distance * 0.5 + 1.0); // Slower distance falloff for shockwaves
                    double currentPressure = remainingPressure * falloff;
                    hits.merge(entry.subLevel(), new WaveHit(point, new Vector3d(direction.x, direction.y, direction.z), currentPressure), ExplosionImpactHandler::strongerHit);
                    // Shockwave continues through the structure but is dampened
                    remainingPressure *= 0.85; 
                }
            }

            // Check for world block obstruction (Terrain dampening)
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                float res = state.getBlock().getExplosionResistance();
                if (res > 0) {
                    remainingPressure *= Math.max(0.1, 1.0 - (res / 100.0));
                    metObstacle = true;
                }
            }
        }
        return metObstacle;
    }

    private static WaveHit strongerHit(WaveHit a, WaveHit b) {
        return a.pressure() >= b.pressure() ? a : b;
    }

    private static List<SubLevelEntry> nearbySubLevels(Object container, Vec3 center, double searchRadius) throws Exception {
        List<SubLevelEntry> nearby = new ArrayList<>();
        Iterable<?> all = (Iterable<?>) GET_ALL_SUBLEVELS.invoke(container);
        AABB searchArea = new AABB(center, center).inflate(searchRadius);
        for (Object sl : all) {
            AABB bounds = (AABB) BOUNDING_BOX.invoke(sl);
            if (bounds.intersects(searchArea)) {
                nearby.add(new SubLevelEntry(sl, bounds));
            }
        }
        return nearby;
    }

    private static void applyExplosionImpulse(ServerLevel level, Object subLevel, Vector3d direction, double force) {
        try {
            // Safety: check if sub-level is valid and not sleeping in a way that causes panic
            if (subLevel == null) return;
            
            // Validate force and direction to prevent NaN/Infinity in native code
            if (!Double.isFinite(force) || force <= 1e-6) return;
            if (!Double.isFinite(direction.x) || !Double.isFinite(direction.y) || !Double.isFinite(direction.z)) return;

            // Attempt to wake up the sub-level before applying force. 
            // This prevents "island should be awake" panic when applying forces during massive entity cleanup.
            WAKE_UP.invoke(subLevel);
            
            Integer rid = ((Number) RUNTIME_ID_FIELD.get(subLevel)).intValue();
            Object tracker = GET_MASS_TRACKER.invoke(subLevel);
            Object com = GET_CENTER_OF_MASS.invoke(tracker);
            double comX = (double) com.getClass().getMethod("x").invoke(com);
            double comY = (double) com.getClass().getMethod("y").invoke(com);
            double comZ = (double) com.getClass().getMethod("z").invoke(com);

            double impulseScale = TrueImpactConfig.EXPLOSION_IMPULSE_SCALE.get();
            double maxImpulse = TrueImpactConfig.EXPLOSION_MAX_IMPULSE.get();
            double impulse = Math.min(maxImpulse, force * impulseScale);
            
            Vector3d impulseVec = new Vector3d(direction).normalize().mul(impulse);
            
            // Final check on impulse vector
            if (!Double.isFinite(impulseVec.x) || !Double.isFinite(impulseVec.y) || !Double.isFinite(impulseVec.z)) return;

            // Apply at center of mass
            Rapier3D.applyForce(0, rid, 0, 0, 0, impulseVec.x, impulseVec.y, impulseVec.z, true);
        } catch (Exception ignored) {}
    }

    private static Vec3 rayDirection(int index, int total) {
        double phi = Math.acos(1.0 - 2.0 * (index + 0.5) / total);
        double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * (index + 0.5);
        return new Vec3(Math.cos(theta) * Math.sin(phi), Math.sin(theta) * Math.sin(phi), Math.cos(phi));
    }

    private static Method findMethod(String cl, String m, Class<?>... params) {
        try { Method method = Class.forName(cl).getMethod(m, params); method.setAccessible(true); return method; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static java.lang.reflect.Field findField(String cl, String f) {
        try { java.lang.reflect.Field field = Class.forName(cl).getDeclaredField(f); field.setAccessible(true); return field; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private record WaveHit(Vec3 point, Vector3d direction, double pressure) {}
    private record WaveScan(Map<Object, WaveHit> hits, double blockedRatio, int rays) {}
    private record SubLevelEntry(Object subLevel, AABB bounds) {}
}
