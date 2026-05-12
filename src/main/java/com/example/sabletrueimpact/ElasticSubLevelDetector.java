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
    private static long cacheTick = Long.MIN_VALUE;
    private static String cacheDimension = "";
    private static final Map<ProbeKey, Boolean> NEARBY_ELASTIC_CACHE = new HashMap<>();
    private static final Map<ProbeKey, Boolean> NEARBY_SUBLEVEL_CACHE = new HashMap<>();
    private static final Map<ProbeKey, Double> NEARBY_MASS_CACHE = new HashMap<>();
    private static final Map<Object, Boolean> ELASTIC_SUBLEVEL_CACHE = new IdentityHashMap<>();

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
        Object container = SableReflector.getContainer(level);
        if (container != null) {
            Iterable<Object> subLevels = SableReflector.getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if ((plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get()))
                        && containsElasticBlock(subLevel, level)) {
                    result = true;
                    break;
                }
            }
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
        Object container = SableReflector.getContainer(level);
        if (container != null) {
            Iterable<Object> subLevels = SableReflector.getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if (plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                    result = true;
                    break;
                }
            }
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
        Object container = SableReflector.getContainer(level);
        if (container != null) {
            Iterable<Object> subLevels = SableReflector.getAllSubLevels(container);
            for (Object subLevel : subLevels) {
                if (plotContains(subLevel, impactPos)
                        || isNear(subLevel, impactPos, TrueImpactConfig.ELASTIC_SUBLEVEL_DETECTION_RANGE.get())) {
                    maxMass = Math.max(maxMass, SableReflector.getMass(subLevel));
                }
            }
        }
        NEARBY_MASS_CACHE.put(key, maxMass);
        return maxMass;
    }

    private static boolean isNear(Object subLevel, BlockPos impactPos, double range) {
        Object bounds = SableReflector.getBoundingBox(subLevel);
        if (bounds == null) return false;
        double minX = SableReflector.getMinX(bounds) - range;
        double minY = SableReflector.getMinY(bounds) - range;
        double minZ = SableReflector.getMinZ(bounds) - range;
        double maxX = SableReflector.getMaxX(bounds) + range;
        double maxY = SableReflector.getMaxY(bounds) + range;
        double maxZ = SableReflector.getMaxZ(bounds) + range;
        return impactPos.getX() + 0.5 >= minX && impactPos.getX() + 0.5 <= maxX
                && impactPos.getY() + 0.5 >= minY && impactPos.getY() + 0.5 <= maxY
                && impactPos.getZ() + 0.5 >= minZ && impactPos.getZ() + 0.5 <= maxZ;
    }

    private static boolean plotContains(Object subLevel, BlockPos pos) {
        Object bounds = SableReflector.plotBounds(subLevel);
        if (bounds == null) return false;
        double minX = SableReflector.getMinX(bounds);
        double minY = SableReflector.getMinY(bounds);
        double minZ = SableReflector.getMinZ(bounds);
        double maxX = SableReflector.getMaxX(bounds);
        double maxY = SableReflector.getMaxY(bounds);
        double maxZ = SableReflector.getMaxZ(bounds);
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static boolean containsElasticBlock(Object subLevel, ServerLevel level) {
        Boolean cached = ELASTIC_SUBLEVEL_CACHE.get(subLevel);
        if (cached != null) {
            return cached;
        }

        Object bounds = SableReflector.plotBounds(subLevel);
        if (bounds == null) return false;
        int minX = (int) SableReflector.getMinX(bounds);
        int minY = (int) SableReflector.getMinY(bounds);
        int minZ = (int) SableReflector.getMinZ(bounds);
        int maxX = (int) SableReflector.getMaxX(bounds);
        int maxY = (int) SableReflector.getMaxY(bounds);
        int maxZ = (int) SableReflector.getMaxZ(bounds);
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

    private record ProbeKey(long pos) {
    }
}
