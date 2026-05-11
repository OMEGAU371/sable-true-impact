package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.joml.Vector3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ExplosionImpactHandler {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", ServerLevel.class);
    private static final Method GET_ALL_SUBLEVELS = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getAllSubLevels");
    private static final Method BOUNDING_BOX = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");
    private static final Method APPLY_IMPULSE = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "applyImpulse", Vector3d.class, Vector3d.class);
    private static final Random RANDOM = new Random();

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
        if (radius <= 0.0) return;

        long startedAt = TrueImpactPerformance.start();

        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) return;
            
            double searchRadius = radius * TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get();
            List<SubLevelEntry> nearby = nearbySubLevels(container, center, searchRadius);
            if (nearby.isEmpty()) return;

            WaveScan scan = scanShockwave(level, center, radius, searchRadius, nearby);
            double confinement = 1.0 + (scan.blockedRatio() * scan.blockedRatio()) * TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.get();
            
            for (SubLevelEntry entry : nearby) {
                WaveHit hit = scan.hits().get(entry.subLevel());
                if (hit == null) continue;

                double finalPressure = hit.pressure() * confinement;
                
                // 1. Structural Fracture
                SubLevelFracture.tryFracture(entry.subLevel(), toLocalPoint(entry.subLevel(), hit.point()), hit.normal(), finalPressure);
                
                // 2. Physical Impulse
                if (TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.get()) {
                    applyImpulse(entry.subLevel(), hit.point(), hit.normal(), finalPressure);
                }
            }
            
            TrueImpactPerformance.recordExplosionImpact(startedAt, scan.rays(), scan.hits().size(), 0);
        } catch (Exception e) {
            TrueImpactMod.LOGGER.error("Failed to process explosion impact", e);
        }
    }

    private static WaveScan scanShockwave(ServerLevel level, Vec3 center, double radius, double searchRadius, List<SubLevelEntry> nearby) {
        Map<Object, WaveHit> hits = new HashMap<>();
        int samples = TrueImpactConfig.EXPLOSION_IMPACT_RAY_SAMPLES.get();
        double stepSize = TrueImpactConfig.EXPLOSION_IMPACT_RAY_STEP.get();
        int steps = (int) (searchRadius / stepSize);
        int blocked = 0;

        boolean smartCulling = TrueImpactConfig.ENABLE_SMART_RAY_CULLING.get();
        
        for (int i = 0; i < samples; i++) {
            Vec3 direction;
            if (smartCulling && !nearby.isEmpty()) {
                SubLevelEntry target = nearby.get(RANDOM.nextInt(nearby.size()));
                direction = randomDirectionToBox(center, target.bounds());
            } else {
                direction = randomSphereDirection();
            }

            boolean isBlocked = traceRay(level, center, direction, radius, stepSize, steps, nearby, hits);
            if (isBlocked) blocked++;
        }

        return new WaveScan(hits, blocked / (double) Math.max(samples, 1), samples);
    }

    private static Vec3 randomDirectionToBox(Vec3 center, BoundingBox3dc bounds) {
        double x = (bounds.minX() + bounds.maxX()) * 0.5;
        double y = (bounds.minY() + bounds.maxY()) * 0.5;
        double z = (bounds.minZ() + bounds.maxZ()) * 0.5;
        // Randomly nudge within the box for coverage
        x += (RANDOM.nextDouble() - 0.5) * (bounds.maxX() - bounds.minX());
        y += (RANDOM.nextDouble() - 0.5) * (bounds.maxY() - bounds.minY());
        z += (RANDOM.nextDouble() - 0.5) * (bounds.maxZ() - bounds.minZ());
        return new Vec3(x - center.x, y - center.y, z - center.z).normalize();
    }

    private static Vec3 randomSphereDirection() {
        double u = RANDOM.nextDouble();
        double v = RANDOM.nextDouble();
        double theta = 2 * Math.PI * u;
        double phi = Math.acos(2 * v - 1);
        return new Vec3(Math.sin(phi) * Math.cos(theta), Math.sin(phi) * Math.sin(theta), Math.cos(phi));
    }

    private static boolean traceRay(ServerLevel level, Vec3 center, Vec3 direction, double radius, double stepSize, int steps, List<SubLevelEntry> subLevels, Map<Object, WaveHit> hits) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double forceBase = radius * radius * TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.get();
        double remainingPressure = forceBase;
        boolean metObstacle = false;

        for (int step = 1; step <= steps; step++) {
            double distance = step * stepSize;
            if (remainingPressure <= 1.0) break;

            Vec3 point = center.add(direction.scale(distance));
            pos.set(point.x, point.y, point.z);
            
            for (SubLevelEntry entry : subLevels) {
                if (entry.bounds().contains(point.x, point.y, point.z)) {
                    double falloff = 1.0 / (distance * 0.35 + 1.0); 
                    double currentPressure = remainingPressure * falloff;
                    hits.merge(entry.subLevel(), new WaveHit(point, new Vector3d(direction.x, direction.y, direction.z), currentPressure), ExplosionImpactHandler::strongerHit);
                    remainingPressure *= 0.88; 
                }
            }

            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                float res = state.getBlock().getExplosionResistance();
                if (res > 0) {
                    remainingPressure *= Math.max(0.05, 1.0 - (res / 80.0));
                    metObstacle = true;
                }
            }
        }
        return metObstacle;
    }

    private static void applyImpulse(Object subLevel, Vec3 point, Vector3d normal, double pressure) {
        try {
            double amount = Math.min(pressure * TrueImpactConfig.EXPLOSION_IMPULSE_SCALE.get(), TrueImpactConfig.EXPLOSION_MAX_IMPULSE.get());
            Vector3d impulse = new Vector3d(normal).mul(amount);
            APPLY_IMPULSE.invoke(subLevel, impulse, new Vector3d(point.x, point.y, point.z));
        } catch (Exception ignored) {}
    }

    private static WaveHit strongerHit(WaveHit a, WaveHit b) {
        return a.pressure() >= b.pressure() ? a : b;
    }

    private static List<SubLevelEntry> nearbySubLevels(Object container, Vec3 center, double searchRadius) throws Exception {
        List<SubLevelEntry> nearby = new ArrayList<>();
        Iterable<?> all = (Iterable<?>) GET_ALL_SUBLEVELS.invoke(container);
        double sr2 = searchRadius * searchRadius;
        for (Object sl : all) {
            BoundingBox3dc b = (BoundingBox3dc) BOUNDING_BOX.invoke(sl);
            double dx = Math.max(0, Math.max(b.minX() - center.x, center.x - b.maxX()));
            double dy = Math.max(0, Math.max(b.minY() - center.y, center.y - b.maxY()));
            double dz = Math.max(0, Math.max(b.minZ() - center.z, center.z - b.maxZ()));
            if (dx*dx + dy*dy + dz*dz <= sr2) {
                nearby.add(new SubLevelEntry(sl, b));
            }
        }
        return nearby;
    }

    private static Vector3d toLocalPoint(Object subLevel, Vec3 world) {
        try {
            Method logicalPose = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose");
            Method rotationPoint = findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint");
            Object pose = logicalPose.invoke(subLevel);
            Vector3d rp = (Vector3d) rotationPoint.invoke(pose);
            return new Vector3d(world.x - rp.x, world.y - rp.y, world.z - rp.z);
        } catch (Exception e) { return new Vector3d(0,0,0); }
    }

    private static Method findMethod(String cl, String m, Class<?>... params) {
        try { Method method = Class.forName(cl).getMethod(m, params); method.setAccessible(true); return method; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private record SubLevelEntry(Object subLevel, BoundingBox3dc bounds) {}
    private record WaveHit(Vec3 point, Vector3d normal, double pressure) {}
    private record WaveScan(Map<Object, WaveHit> hits, double blockedRatio, int rays) {}
}
