/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.level.ExplosionEvent$Detonate
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.SubLevelFracture;
import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPerformance;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ExplosionImpactHandler {
    private static final Method GET_CONTAINER = ExplosionImpactHandler.findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = ExplosionImpactHandler.findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels", new Class[0]);
    private static final Method BOUNDING_BOX = ExplosionImpactHandler.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox", new Class[0]);
    private static final Method GET_MASS_TRACKER = ExplosionImpactHandler.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker", new Class[0]);
    private static final Method GET_CENTER_OF_MASS = ExplosionImpactHandler.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass", new Class[0]);
    private static final Field RUNTIME_ID_FIELD = ExplosionImpactHandler.findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "runtimeId");

    private ExplosionImpactHandler() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level;
        if (!(((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() && ((Boolean)TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE.get()).booleanValue() && (level = event.getLevel()) instanceof ServerLevel)) {
            return;
        }
        ServerLevel level2 = (ServerLevel)level;
        Vec3 center = event.getExplosion().center();
        double radius = Math.max(0.0, (double)event.getExplosion().radius());
        if (radius <= 0.0) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();
        int rays = 0;
        int hits = 0;
        int fractures = 0;
        try {
            Object container = GET_CONTAINER.invoke(null, level2);
            if (container == null) {
                return;
            }
            double searchRadius = radius * (Double)TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get();
            List<SubLevelEntry> nearby = ExplosionImpactHandler.nearbySubLevels(container, center, searchRadius);
            if (nearby.isEmpty()) {
                return;
            }
            WaveScan scan = ExplosionImpactHandler.scanShockwave(level2, center, radius, searchRadius, nearby);
            rays = scan.rays();
            hits = scan.hits().size();
            double ratio = scan.blockedRatio();
            double confinement = 1.0 + ratio * ratio * (Double)TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.get();
            int processed = 0;
            int maxSubLevels = (Integer)TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();
            for (SubLevelEntry entry : nearby) {
                Object subLevel = entry.subLevel();
                WaveHit hit = scan.hits().get(entry.subLevel());
                if (hit == null) continue;
                if (processed >= maxSubLevels) {
                    break;
                }
                double force = hit.pressure() * confinement;
                if (force < (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) continue;
                Vector3d localPoint = new Vector3d(hit.point().x, hit.point().y, hit.point().z);
                Vector3d normal = new Vector3d((Vector3dc)hit.direction());
                if (normal.lengthSquared() < 1.0E-8) {
                    normal.set(0.0, 1.0, 0.0);
                }
                // localPoint here is a physics-world position from the explosion ray, not body-frame.
                SubLevelFracture.tryFracturePhysicsWorldPos(subLevel, localPoint, normal, force);
                ExplosionImpactHandler.applyExplosionImpulse(level2, subLevel, normal, force);
                ++processed;
                ++fractures;
            }
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
        }
        finally {
            TrueImpactPerformance.recordExplosionImpact(startedAt, rays, hits, fractures);
        }
    }

    private static WaveScan scanShockwave(ServerLevel level, Vec3 center, double radius, double searchRadius, List<SubLevelEntry> subLevels) {
        IdentityHashMap<Object, WaveHit> hits = new IdentityHashMap<Object, WaveHit>();
        int blocked = 0;
        int samples = (Integer)TrueImpactConfig.EXPLOSION_IMPACT_RAY_SAMPLES.get();
        double stepSize = (Double)TrueImpactConfig.EXPLOSION_IMPACT_RAY_STEP.get();
        int steps = Math.max(1, (int)Math.ceil(searchRadius / stepSize));
        for (int ray = 0; ray < samples; ++ray) {
            Vec3 direction = ExplosionImpactHandler.rayDirection(ray, samples);
            RayResult result = ExplosionImpactHandler.traceRay(level, center, direction, radius, stepSize, steps, subLevels, hits);
            if (!result.blocked()) continue;
            ++blocked;
        }
        return new WaveScan(hits, (double)blocked / (double)Math.max(samples, 1), samples);
    }

    private static RayResult traceRay(ServerLevel level, Vec3 center, Vec3 direction, double radius, double stepSize, int steps, List<SubLevelEntry> subLevels, Map<Object, WaveHit> hits) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int step = 1; step <= steps; ++step) {
            double distance = (double)step * stepSize;
            Vec3 point = center.add(direction.scale(distance));
            pos.set(point.x, point.y, point.z);
            for (SubLevelEntry entry : subLevels) {
                if (!entry.bounds().contains(point)) continue;
                double falloff = 1.0 - distance / Math.max(radius * (Double)TrueImpactConfig.EXPLOSION_IMPACT_RADIUS_MULTIPLIER.get(), 0.001);
                double nearField = 1.0 / (distance * distance + 1.0);
                double pressure = radius * radius * (Double)TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.get() * (0.35 + Math.max(0.0, falloff) * 0.65) * (1.0 + nearField);
                hits.merge(entry.subLevel(), new WaveHit(point, new Vector3d(direction.x, direction.y, direction.z), pressure), ExplosionImpactHandler::strongerHit);
                return RayResult.BLOCKED;
            }
            BlockState state = level.getBlockState((BlockPos)pos);
            if (state.isAir() || !(state.getDestroySpeed((BlockGetter)level, (BlockPos)pos) >= 0.0f)) continue;
            return RayResult.BLOCKED;
        }
        return RayResult.ESCAPED;
    }

    private static WaveHit strongerHit(WaveHit first, WaveHit second) {
        return first.pressure() >= second.pressure() ? first : second;
    }

    private static List<SubLevelEntry> nearbySubLevels(Object container, Vec3 center, double searchRadius) throws ReflectiveOperationException {
        ArrayList<SubLevelEntry> nearby = new ArrayList<SubLevelEntry>();
        int maxSubLevels = (Integer)TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.get();
        for (Object subLevel : ExplosionImpactHandler.subLevels(container)) {
            if (nearby.size() >= maxSubLevels) break;
            AABB bounds = ExplosionImpactHandler.bounds(subLevel);
            Vec3 closest = ExplosionImpactHandler.closestPoint(bounds, center);
            if (!(closest.distanceTo(center) <= searchRadius)) continue;
            nearby.add(new SubLevelEntry(subLevel, bounds));
        }
        return nearby;
    }

    private static Vec3 rayDirection(int index, int samples) {
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        double y = 1.0 - ((double)index + 0.5) * 2.0 / (double)samples;
        double radius = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double theta = (double)index * goldenAngle;
        return new Vec3(Math.cos(theta) * radius, y, Math.sin(theta) * radius);
    }

    private static Iterable<?> subLevels(Object container) throws ReflectiveOperationException {
        Iterable<Object> iterable;
        Object result = GET_ALL_SUBLEVELS.invoke(container, new Object[0]);
        if (result instanceof Iterable) {
            Iterable iterable2 = (Iterable)result;
            iterable = iterable2;
        } else {
            iterable = Collections.emptyList();
        }
        return iterable;
    }

    private static AABB bounds(Object subLevel) throws ReflectiveOperationException {
        Object bounds = BOUNDING_BOX.invoke(subLevel, new Object[0]);
        return new AABB(ExplosionImpactHandler.number(bounds, "minX"), ExplosionImpactHandler.number(bounds, "minY"), ExplosionImpactHandler.number(bounds, "minZ"), ExplosionImpactHandler.number(bounds, "maxX"), ExplosionImpactHandler.number(bounds, "maxY"), ExplosionImpactHandler.number(bounds, "maxZ"));
    }

    private static Vec3 closestPoint(AABB bounds, Vec3 point) {
        return new Vec3(ExplosionImpactHandler.clamp(point.x, bounds.minX, bounds.maxX), ExplosionImpactHandler.clamp(point.y, bounds.minY, bounds.maxY), ExplosionImpactHandler.clamp(point.z, bounds.minZ, bounds.maxZ));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, new Class[0]);
        return ((Number)method.invoke(target, new Object[0])).doubleValue();
    }

    private static void applyExplosionImpulse(ServerLevel level, Object subLevel, Vector3d normal, double force) {
        if (!((Boolean)TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.get()).booleanValue() || normal.lengthSquared() < 1.0E-8) {
            return;
        }
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return;
        }
        double impulse = Math.min((Double)TrueImpactConfig.EXPLOSION_MAX_IMPULSE.get(), force * (Double)TrueImpactConfig.EXPLOSION_IMPULSE_SCALE.get());
        if (impulse <= 0.0) {
            return;
        }
        Vector3d outward = new Vector3d((Vector3dc)normal).normalize().mul(impulse);
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        handle.applyLinearImpulse(outward);
    }

    private static Vector3d centerOfMass(Object subLevel) {
        try {
            Object massTracker = GET_MASS_TRACKER.invoke(subLevel, new Object[0]);
            Object center = GET_CENTER_OF_MASS.invoke(massTracker, new Object[0]);
            if (center == null) {
                return null;
            }
            Method x = center.getClass().getMethod("x", new Class[0]);
            Method y = center.getClass().getMethod("y", new Class[0]);
            Method z = center.getClass().getMethod("z", new Class[0]);
            return new Vector3d(((Number)x.invoke(center, new Object[0])).doubleValue(), ((Number)y.invoke(center, new Object[0])).doubleValue(), ((Number)z.invoke(center, new Object[0])).doubleValue());
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Integer runtimeId(Object subLevel) {
        try {
            return ((Number)RUNTIME_ID_FIELD.get(subLevel)).intValue();
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // Returns null on any failure so <clinit> can complete even if Sable internals or
    // client-only classes (e.g. ClientLevel referenced by SubLevelContainer) can't be loaded
    // on a dedicated server. The onExplosionDetonate body already catches RuntimeException,
    // so null-field NPEs are silently swallowed and explosion fracture simply does nothing.
    private static Method findMethod(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        }
        catch (Exception e) {
            return null;
        }
    }

    private record WaveScan(Map<Object, WaveHit> hits, double blockedRatio, int rays) {
    }

    private record SubLevelEntry(Object subLevel, AABB bounds) {
    }

    private record WaveHit(Vec3 point, Vector3d direction, double pressure) {
    }

    private record RayResult(boolean blocked) {
        private static final RayResult BLOCKED = new RayResult(true);
        private static final RayResult ESCAPED = new RayResult(false);
    }
}
