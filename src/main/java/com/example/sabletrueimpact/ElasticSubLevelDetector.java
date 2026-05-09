package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.Collections;

public final class ElasticSubLevelDetector {
    private static final Method GET_CONTAINER = findMethod("dev.ryanhcode.sable.api.sublevel.SubLevelContainer", "getContainer", Level.class);
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");

    private ElasticSubLevelDetector() {
    }

    public static boolean hasNearbyElasticSubLevel(ServerLevel level, BlockPos impactPos) {
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return false;
            }
            Iterable<?> subLevels = getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if ((plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get()))
                        && containsElasticBlock(subLevel, level)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }

    public static boolean hasNearbySubLevel(ServerLevel level, BlockPos impactPos) {
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return false;
            }
            Iterable<?> subLevels = getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if (plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }

    public static double nearbyMaxMass(ServerLevel level, BlockPos impactPos, double fallbackMass) {
        double maxMass = fallbackMass;
        try {
            Object container = GET_CONTAINER.invoke(null, level);
            if (container == null) {
                return maxMass;
            }
            Iterable<?> subLevels = getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if (plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                    maxMass = Math.max(maxMass, mass(subLevel));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return maxMass;
        }
        return maxMass;
    }

    private static Iterable<?> getAllSubLevels(Object container) throws ReflectiveOperationException {
        Method method = container.getClass().getMethod("getAllSubLevels");
        Object result = method.invoke(container);
        return result instanceof Iterable<?> iterable ? iterable : Collections.emptyList();
    }

    private static boolean isNear(Object subLevel, BlockPos impactPos, double range) throws ReflectiveOperationException {
        Method boundingBoxMethod = subLevel.getClass().getMethod("boundingBox");
        Object bounds = boundingBoxMethod.invoke(subLevel);
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
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Object plotBounds(Object subLevel) throws ReflectiveOperationException {
        Method getPlot = subLevel.getClass().getMethod("getPlot");
        Object plot = getPlot.invoke(subLevel);
        Method getBoundingBox = plot.getClass().getMethod("getBoundingBox");
        return getBoundingBox.invoke(plot);
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static double mass(Object subLevel) throws ReflectiveOperationException {
        Object massTracker = GET_MASS_TRACKER.invoke(subLevel);
        return ((Number) GET_MASS.invoke(massTracker)).doubleValue();
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
}
