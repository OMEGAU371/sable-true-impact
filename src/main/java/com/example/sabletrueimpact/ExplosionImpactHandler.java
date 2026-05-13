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
        int hits = 0;
        int fractures = 0;

        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return;
            }
            double searchRadius = radius * TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get();
            List<SubLevelEntry> nearby = nearbySubLevels(container, center, searchRadius);
            if (nearby.isEmpty()) {
                return;
            }
            WaveScan scan = scanShockwave(level, center, radius, searchRadius, nearby);
            rays = scan.rays();
            hits = scan.hits().size();
            // Use square scaling for blocked ratio to make fully enclosed spaces feel exponentially more violent
            double ratio = scan.blockedRatio();
            double confinement = 1.0 + (ratio * ratio) * TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.get();
            int processed = 0;
            int maxSubLevels = TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();
            for (SubLevelEntry entry : nearby) {
                Object subLevel = entry.subLevel();
                WaveHit hit = scan.hits().get(entry.subLevel());
                if (hit == null) {
                    continue;
                }
                if (processed >= maxSubLevels) {
                    break;
                }
                double force = hit.pressure() * confinement;
                if (force < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) {
                    continue;
                }
                Vector3d localPoint = SubLevelFracture.toLocalPoint(subLevel, new Vector3d(hit.point().x, hit.point().y, hit.point().z));
                if (localPoint == null) {
                    continue;
                }
                Vector3d normal = new Vector3d(hit.direction());
                if (normal.lengthSquared() < 1.0E-8) {
                    normal.set(0.0, 1.0, 0.0);
                }
                SubLevelFracture.tryFracture(subLevel, localPoint, normal, force);
                applyExplosionImpulse(level, subLevel, normal, force);
                processed++;
                fractures++;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        } finally {
            TrueImpactPerformance.recordExplosionImpact(startedAt, rays, hits, fractures);
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
            RayResult result = traceRay(level, center, direction, radius, stepSize, steps, subLevels, hits);
            if (result.blocked()) {
                blocked++;
            }
        }
        return new WaveScan(hits, blocked / (double) Math.max(samples, 1), samples);
    }

    private static RayResult traceRay(ServerLevel level, Vec3 center, Vec3 direction, double radius, double stepSize, int steps, List<SubLevelEntry> subLevels, Map<Object, WaveHit> hits) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int step = 1; step <= steps; step++) {
            double distance = step * stepSize;
            Vec3 point = center.add(direction.scale(distance));
            pos.set(point.x, point.y, point.z);
            for (SubLevelEntry entry : subLevels) {
                if (!entry.bounds().contains(point)) {
                    continue;
                }
                double falloff = 1.0 - distance / Math.max(radius * TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get(), 0.001);
                double nearField = 1.0 / (distance * distance + 1.0);
                double pressure = radius * radius
                        * TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.get()
                        * (0.35 + Math.max(0.0, falloff) * 0.65)
                        * (1.0 + nearField);
                hits.merge(entry.subLevel(), new WaveHit(point, new Vector3d(direction.x, direction.y, direction.z), pressure), ExplosionImpactHandler::strongerHit);
                return RayResult.BLOCKED;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f) {
                return RayResult.BLOCKED;
            }
        }
        return RayResult.ESCAPED;
    }

    private static WaveHit strongerHit(WaveHit first, WaveHit second) {
        return first.pressure() >= second.pressure() ? first : second;
    }

    private static List<SubLevelEntry> nearbySubLevels(Object container, Vec3 center, double searchRadius) throws ReflectiveOperationException {
        List<SubLevelEntry> nearby = new ArrayList<>();
        int maxSubLevels = TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();
        for (Object subLevel : subLevels(container)) {
            if (nearby.size() >= maxSubLevels) {
                break;
            }
            AABB bounds = bounds(subLevel);
            Vec3 closest = closestPoint(bounds, center);
            if (closest.distanceTo(center) <= searchRadius) {
                nearby.add(new SubLevelEntry(subLevel, bounds));
            }
        }
        return nearby;
    }

    private static Vec3 rayDirection(int index, int samples) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        double y = 1.0 - (index + 0.5) * 2.0 / samples;
        double radius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = index * goldenAngle;
        return new Vec3(Math.cos(theta) * radius, y, Math.sin(theta) * radius);
    }

    private static Iterable<?> subLevels(Object container) throws ReflectiveOperationException {
        Object result = GET_ALL_SUBLEVELS.invoke(container);
        return result instanceof Iterable<?> iterable ? iterable : Collections.emptyList();
    }

    private static AABB bounds(Object subLevel) throws ReflectiveOperationException {
        Object bounds = BOUNDING_BOX.invoke(subLevel);
        return new AABB(number(bounds, "minX"), number(bounds, "minY"), number(bounds, "minZ"),
                number(bounds, "maxX"), number(bounds, "maxY"), number(bounds, "maxZ"));
    }

    private static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(
                clamp(point.x, bounds.minX, bounds.maxX),
                clamp(point.y, bounds.minY, bounds.maxY),
                clamp(point.z, bounds.minZ, bounds.maxZ)
        );
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static void applyExplosionImpulse(ServerLevel level, Object subLevel, Vector3d normal, double force) {
        if (!TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.get() || normal.lengthSquared() < 1.0E-8) {
            return;
        }
        Integer runtimeId = runtimeId(subLevel);
        Vector3d centerOfMass = centerOfMass(subLevel);
        if (runtimeId == null || centerOfMass == null) {
            return;
        }
        double impulse = Math.min(TrueImpactConfig.EXPLOSION_MAX_IMPULSE.get(), force * TrueImpactConfig.EXPLOSION_IMPULSE_SCALE.get());
        if (impulse <= 0.0) {
            return;
        }
        Vector3d outward = new Vector3d(normal).normalize().mul(impulse);
        Rapier3D.applyForce(
                Rapier3D.getID(level),
                runtimeId,
                0.0,
                0.0,
                0.0,
                outward.x,
                outward.y,
                outward.z,
                true
        );
    }

    private static Vector3d centerOfMass(Object subLevel) {
        try {
            Object massTracker = GET_MASS_TRACKER.invoke(subLevel);
            Object center = GET_CENTER_OF_MASS.invoke(massTracker);
            if (center == null) {
                return null;
            }
            Method x = center.getClass().getMethod("x");
            Method y = center.getClass().getMethod("y");
            Method z = center.getClass().getMethod("z");
            return new Vector3d(((Number) x.invoke(center)).doubleValue(), ((Number) y.invoke(center)).doubleValue(), ((Number) z.invoke(center)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Integer runtimeId(Object subLevel) {
        try {
            return ((Number) RUNTIME_ID_FIELD.get(subLevel)).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }

    private static java.lang.reflect.Field findField(String className, String fieldName) {
        try {
            java.lang.reflect.Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable field " + className + "#" + fieldName, e);
        }
    }

    private record SubLevelEntry(Object subLevel, AABB bounds) {
    }

    private record WaveHit(Vec3 point, Vector3d direction, double pressure) {
    }

    private record WaveScan(Map<Object, WaveHit> hits, double blockedRatio, int rays) {
    }

    private record RayResult(boolean blocked) {
        private static final RayResult BLOCKED = new RayResult(true);
        private static final RayResult ESCAPED = new RayResult(false);
    }
}
