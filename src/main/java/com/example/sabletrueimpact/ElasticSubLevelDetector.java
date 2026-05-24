/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class ElasticSubLevelDetector {
    private static final Method GET_CONTAINER = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels", new Class[0]);
    private static final Method GET_MASS_TRACKER = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker", new Class[0]);
    private static final Method GET_MASS = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass", new Class[0]);
    private static final Method SUBLEVEL_BOUNDING_BOX = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox", new Class[0]);
    private static final Method GET_PLOT = ElasticSubLevelDetector.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "getPlot", new Class[0]);
    private static long cacheTick = Long.MIN_VALUE;
    private static String cacheDimension = "";
    private static final Map<ProbeKey, Boolean> NEARBY_ELASTIC_CACHE = new HashMap<ProbeKey, Boolean>();
    private static final Map<ProbeKey, Boolean> NEARBY_SUBLEVEL_CACHE = new HashMap<ProbeKey, Boolean>();
    private static final Map<ProbeKey, Boolean> INSIDE_SUBLEVEL_CACHE = new HashMap<ProbeKey, Boolean>();
    private static final Map<ProbeKey, Double> NEARBY_MASS_CACHE = new HashMap<ProbeKey, Double>();
    private static final Map<Object, Boolean> ELASTIC_SUBLEVEL_CACHE = new IdentityHashMap<Object, Boolean>();
    private static final Map<Class<?>, Method> PLOT_BOUNDING_BOX_METHODS = new HashMap();
    private static final Map<Class<?>, Map<String, Method>> NUMBER_METHODS = new HashMap();

    private ElasticSubLevelDetector() {
    }

    public static boolean hasNearbyElasticSubLevel(ServerLevel level, BlockPos impactPos) {
        boolean result;
        ProbeKey key;
        block4: {
            ElasticSubLevelDetector.resetCacheIfNeeded(level);
            key = ElasticSubLevelDetector.key(impactPos);
            Boolean cached = NEARBY_ELASTIC_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            result = false;
            try {
                Object container = GET_CONTAINER.invoke(null, level);
                if (container == null) break block4;
                Iterable<?> subLevels = ElasticSubLevelDetector.getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (!ElasticSubLevelDetector.plotContains(subLevel, impactPos) && !ElasticSubLevelDetector.isNear(subLevel, impactPos, (Double)TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get()) || !ElasticSubLevelDetector.containsElasticBlock(subLevel, level)) continue;
                    result = true;
                    break;
                }
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                result = false;
            }
        }
        NEARBY_ELASTIC_CACHE.put(key, result);
        return result;
    }

    public static boolean hasNearbySubLevel(ServerLevel level, BlockPos impactPos) {
        boolean result;
        ProbeKey key;
        block4: {
            ElasticSubLevelDetector.resetCacheIfNeeded(level);
            key = ElasticSubLevelDetector.key(impactPos);
            Boolean cached = NEARBY_SUBLEVEL_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            result = false;
            try {
                Object container = GET_CONTAINER.invoke(null, level);
                if (container == null) break block4;
                Iterable<?> subLevels = ElasticSubLevelDetector.getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (!ElasticSubLevelDetector.plotContains(subLevel, impactPos) && !ElasticSubLevelDetector.isNear(subLevel, impactPos, (Double)TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) continue;
                    result = true;
                    break;
                }
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                result = false;
            }
        }
        NEARBY_SUBLEVEL_CACHE.put(key, result);
        return result;
    }

    public static boolean isInsideSubLevelPlot(ServerLevel level, BlockPos impactPos) {
        boolean result;
        ProbeKey key;
        block4: {
            ElasticSubLevelDetector.resetCacheIfNeeded(level);
            key = ElasticSubLevelDetector.key(impactPos);
            Boolean cached = INSIDE_SUBLEVEL_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            result = false;
            try {
                Object container = GET_CONTAINER.invoke(null, level);
                if (container == null) break block4;
                Iterable<?> subLevels = ElasticSubLevelDetector.getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (!ElasticSubLevelDetector.plotContains(subLevel, impactPos)) continue;
                    result = true;
                    break;
                }
            }
            catch (ReflectiveOperationException | RuntimeException e) {
                result = false;
            }
        }
        INSIDE_SUBLEVEL_CACHE.put(key, result);
        return result;
    }

    public static double nearbyMaxMass(ServerLevel level, BlockPos impactPos, double fallbackMass) {
        ElasticSubLevelDetector.resetCacheIfNeeded(level);
        ProbeKey key = ElasticSubLevelDetector.key(impactPos);
        Double cached = NEARBY_MASS_CACHE.get(key);
        if (cached != null) {
            return Math.max(fallbackMass, cached);
        }
        double maxMass = fallbackMass;
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container != null) {
                Iterable<?> subLevels = ElasticSubLevelDetector.getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (!ElasticSubLevelDetector.plotContains(subLevel, impactPos) && !ElasticSubLevelDetector.isNear(subLevel, impactPos, (Double)TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) continue;
                    maxMass = Math.max(maxMass, ElasticSubLevelDetector.mass(subLevel));
                }
            }
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return maxMass;
        }
        NEARBY_MASS_CACHE.put(key, maxMass);
        return maxMass;
    }

    private static Iterable<?> getAllSubLevels(Object container) throws ReflectiveOperationException {
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

    private static boolean isNear(Object subLevel, BlockPos impactPos, double range) throws ReflectiveOperationException {
        Object bounds = SUBLEVEL_BOUNDING_BOX.invoke(subLevel, new Object[0]);
        double minX = ElasticSubLevelDetector.number(bounds, "minX") - range;
        double minY = ElasticSubLevelDetector.number(bounds, "minY") - range;
        double minZ = ElasticSubLevelDetector.number(bounds, "minZ") - range;
        double maxX = ElasticSubLevelDetector.number(bounds, "maxX") + range;
        double maxY = ElasticSubLevelDetector.number(bounds, "maxY") + range;
        double maxZ = ElasticSubLevelDetector.number(bounds, "maxZ") + range;
        return (double)impactPos.getX() + 0.5 >= minX && (double)impactPos.getX() + 0.5 <= maxX && (double)impactPos.getY() + 0.5 >= minY && (double)impactPos.getY() + 0.5 <= maxY && (double)impactPos.getZ() + 0.5 >= minZ && (double)impactPos.getZ() + 0.5 <= maxZ;
    }

    private static boolean plotContains(Object subLevel, BlockPos pos) throws ReflectiveOperationException {
        Object bounds = ElasticSubLevelDetector.plotBounds(subLevel);
        double minX = ElasticSubLevelDetector.number(bounds, "minX");
        double minY = ElasticSubLevelDetector.number(bounds, "minY");
        double minZ = ElasticSubLevelDetector.number(bounds, "minZ");
        double maxX = ElasticSubLevelDetector.number(bounds, "maxX");
        double maxY = ElasticSubLevelDetector.number(bounds, "maxY");
        double maxZ = ElasticSubLevelDetector.number(bounds, "maxZ");
        return (double)pos.getX() >= minX && (double)pos.getX() <= maxX && (double)pos.getY() >= minY && (double)pos.getY() <= maxY && (double)pos.getZ() >= minZ && (double)pos.getZ() <= maxZ;
    }

    private static boolean containsElasticBlock(Object subLevel, ServerLevel level) throws ReflectiveOperationException {
        Boolean cached = ELASTIC_SUBLEVEL_CACHE.get(subLevel);
        if (cached != null) {
            return cached;
        }
        Object bounds = ElasticSubLevelDetector.plotBounds(subLevel);
        int minX = (int)ElasticSubLevelDetector.number(bounds, "minX");
        int minY = (int)ElasticSubLevelDetector.number(bounds, "minY");
        int minZ = (int)ElasticSubLevelDetector.number(bounds, "minZ");
        int maxX = (int)ElasticSubLevelDetector.number(bounds, "maxX");
        int maxY = (int)ElasticSubLevelDetector.number(bounds, "maxY");
        int maxZ = (int)ElasticSubLevelDetector.number(bounds, "maxZ");
        int scanned = 0;
        int limit = (Integer)TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX && scanned < limit; ++x) {
            for (int y = minY; y <= maxY && scanned < limit; ++y) {
                for (int z = minZ; z <= maxZ && scanned < limit; ++scanned, ++z) {
                    BlockState state = level.getBlockState((BlockPos)pos.set(x, y, z));
                    if (state.isAir() || !(PhysicsBlockPropertyHelper.getRestitution((BlockState)state) >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get())) continue;
                    ELASTIC_SUBLEVEL_CACHE.put(subLevel, true);
                    return true;
                }
            }
        }
        ELASTIC_SUBLEVEL_CACHE.put(subLevel, false);
        return false;
    }

    private static Object plotBounds(Object subLevel) throws ReflectiveOperationException {
        Object plot = GET_PLOT.invoke(subLevel, new Object[0]);
        Method method = PLOT_BOUNDING_BOX_METHODS.computeIfAbsent(plot.getClass(), type -> {
            try {
                Method found = type.getMethod("getBoundingBox", new Class[0]);
                found.setAccessible(true);
                return found;
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing Sable plot getBoundingBox method", e);
            }
        });
        return method.invoke(plot, new Object[0]);
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Map<String, Method> methods = NUMBER_METHODS.computeIfAbsent(target.getClass(), ignored -> new HashMap());
        Method method = methods.computeIfAbsent(methodName, name -> {
            try {
                Method found = target.getClass().getMethod((String)name, new Class[0]);
                found.setAccessible(true);
                return found;
            }
            catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing numeric method " + name, e);
            }
        });
        return ((Number)method.invoke(target, new Object[0])).doubleValue();
    }

    private static double mass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = GET_MASS_TRACKER.invoke(subLevel, new Object[0]);
        return ((Number)GET_MASS.invoke(massTracker, new Object[0])).doubleValue();
    }

    private static void resetCacheIfNeeded(ServerLevel level) {
        long tick = level.getGameTime();
        String dimension = level.dimension().location().toString();
        if (tick == cacheTick && dimension.equals(cacheDimension)) {
            return;
        }
        cacheTick = tick;
        cacheDimension = dimension;
        NEARBY_ELASTIC_CACHE.clear();
        NEARBY_SUBLEVEL_CACHE.clear();
        INSIDE_SUBLEVEL_CACHE.clear();
        NEARBY_MASS_CACHE.clear();
        ELASTIC_SUBLEVEL_CACHE.clear();
    }

    private static ProbeKey key(BlockPos pos) {
        return new ProbeKey(pos.asLong());
    }

    private static Method findMethod(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }

    private record ProbeKey(long pos) {
    }
}
