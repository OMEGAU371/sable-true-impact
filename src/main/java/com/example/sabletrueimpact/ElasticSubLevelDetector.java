package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class ElasticSubLevelDetector {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_ALL_SUBLEVELS = findMethod("dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer", "getAllSubLevels");
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method SUBLEVEL_BOUNDING_BOX = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");
    private static final Method GET_PLOT = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "getPlot");

    private static long cacheTick = Long.MIN_VALUE;
    private static String cacheDimension = "";
    private static final Map<ProbeKey, Boolean> NEARBY_ELASTIC_CACHE = new HashMap<>();
    private static final Map<ProbeKey, Boolean> NEARBY_SUBLEVEL_CACHE = new HashMap<>();
    private static final Map<ProbeKey, Double> NEARBY_MASS_CACHE = new HashMap<>();
    private static final Map<Object, Boolean> ELASTIC_SUBLEVEL_CACHE = new IdentityHashMap<>();
    private static final Map<Class<?>, Method> PLOT_BOUNDING_BOX_METHODS = new HashMap<>();
    private static final Map<Class<?>, Map<String, Method>> NUMBER_METHODS = new HashMap<>();

    private ElasticSubLevelDetector() {
    }

    public static boolean hasNearbyElasticSubLevel(ServerLevel level, BlockPos impactPos) {
        resetCacheIfNeeded(level);
        ProbeKey key = key(impactPos);
        Boolean cached = NEARBY_ELASTIC_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean result = false;
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container != null) {
                Iterable<?> subLevels = getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if ((plotContains(subLevel, impactPos)
                            || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get()))
                            && containsElasticBlock(subLevel, level)) {
                        result = true;
                        break;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            result = false;
        }
        NEARBY_ELASTIC_CACHE.put(key, result);
        return result;
    }

    public static boolean hasNearbySubLevel(ServerLevel level, BlockPos impactPos) {
        resetCacheIfNeeded(level);
        ProbeKey key = key(impactPos);
        Boolean cached = NEARBY_SUBLEVEL_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        boolean result = false;
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container != null) {
                Iterable<?> subLevels = getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (plotContains(subLevel, impactPos)
                            || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                        result = true;
                        break;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            result = false;
        }
        NEARBY_SUBLEVEL_CACHE.put(key, result);
        return result;
    }

    public static double nearbyMaxMass(ServerLevel level, BlockPos impactPos, double fallbackMass) {
        resetCacheIfNeeded(level);
        ProbeKey key = key(impactPos);
        Double cached = NEARBY_MASS_CACHE.get(key);
        if (cached != null) {
            return Math.max(fallbackMass, cached);
        }

        double maxMass = fallbackMass;
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container != null) {
                Iterable<?> subLevels = getAllSubLevels(container);
                for (Object subLevel : subLevels) {
                    if (plotContains(subLevel, impactPos)
                            || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                        maxMass = Math.max(maxMass, mass(subLevel));
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return maxMass;
        }
        NEARBY_MASS_CACHE.put(key, maxMass);
        return maxMass;
    }

    private static Iterable<?> getAllSubLevels(Object container) throws ReflectiveOperationException {
        Object result = GET_ALL_SUBLEVELS.invoke(container);
        return result instanceof Iterable<?> iterable ? iterable : Collections.emptyList();
    }

    private static boolean isNear(Object subLevel, BlockPos impactPos, double range) throws ReflectiveOperationException {
        Object bounds = SUBLEVEL_BOUNDING_BOX.invoke(subLevel);
        double minX = number(bounds, "minX") - range;
        double minY = number(bounds, "minY") - range;
        double minZ = number(bounds, "minZ") - range;
        double maxX = number(bounds, "maxX") + range;
        double maxY = number(bounds, "maxY") + range;
        double maxZ = number(bounds, "maxZ") + range;
        return impactPos.getX() + 0.5 >= minX && impactPos.getX() + 0.5 <= maxX
                && impactPos.getY() + 0.5 >= minY && impactPos.getY() + 0.5 <= maxY
                && impactPos.getZ() + 0.5 >= minZ && impactPos.getZ() + 0.5 <= maxZ;
    }

    private static boolean plotContains(Object subLevel, BlockPos pos) throws ReflectiveOperationException {
        Object bounds = plotBounds(subLevel);
        double minX = number(bounds, "minX");
        double minY = number(bounds, "minY");
        double minZ = number(bounds, "minZ");
        double maxX = number(bounds, "maxX");
        double maxY = number(bounds, "maxY");
        double maxZ = number(bounds, "maxZ");
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean containsElasticBlock(Object subLevel, ServerLevel level) throws ReflectiveOperationException {
        Boolean cached = ELASTIC_SUBLEVEL_CACHE.get(subLevel);
        if (cached != null) {
            return cached;
        }

        Object bounds = plotBounds(subLevel);
        int minX = (int) number(bounds, "minX");
        int minY = (int) number(bounds, "minY");
        int minZ = (int) number(bounds, "minZ");
        int maxX = (int) number(bounds, "maxX");
        int maxY = (int) number(bounds, "maxY");
        int maxZ = (int) number(bounds, "maxZ");
        int scanned = 0;
        int limit = TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX && scanned < limit; x++) {
            for (int y = minY; y <= maxY && scanned < limit; y++) {
                for (int z = minZ; z <= maxZ && scanned < limit; z++) {
                    scanned++;
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()
                            && PhysicsBlockPropertyHelper.getRestitution(state) >= TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get()) {
                        ELASTIC_SUBLEVEL_CACHE.put(subLevel, true);
                        return true;
                    }
                }
            }
        }
        ELASTIC_SUBLEVEL_CACHE.put(subLevel, false);
        return false;
    }

    private static Object plotBounds(Object subLevel) throws ReflectiveOperationException {
        Object plot = GET_PLOT.invoke(subLevel);
        Method method = PLOT_BOUNDING_BOX_METHODS.computeIfAbsent(plot.getClass(), type -> {
            try {
                Method found = type.getMethod("getBoundingBox");
                found.setAccessible(true);
                return found;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing Sable plot getBoundingBox method", e);
            }
        });
        return method.invoke(plot);
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Map<String, Method> methods = NUMBER_METHODS.computeIfAbsent(target.getClass(), ignored -> new HashMap<>());
        Method method = methods.computeIfAbsent(methodName, name -> {
            try {
                Method found = target.getClass().getMethod(name);
                found.setAccessible(true);
                return found;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Missing numeric method " + name, e);
            }
        });
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static double mass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = GET_MASS_TRACKER.invoke(subLevel);
        return ((Number) GET_MASS.invoke(massTracker)).doubleValue();
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
        NEARBY_MASS_CACHE.clear();
        ELASTIC_SUBLEVEL_CACHE.clear();
    }

    private static ProbeKey key(BlockPos pos) {
        return new ProbeKey(pos.asLong());
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

    private record ProbeKey(long pos) {
    }
}
