package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ImpactDamageContextCache {
    private static final Map<ContextKey, CachedScale> CACHE = new ConcurrentHashMap<>();
    
    private ImpactDamageContextCache() {}

    public static void put(ServerLevel level, BlockPos pos, double scale) {
        String dimension = level.dimension().location().toString();
        long tick = level.getGameTime();
        CACHE.put(new ContextKey(dimension, pos.immutable()), new CachedScale(scale, tick));
        
        // Periodic cleanup
        if (tick % 100 == 0) {
            cleanup(tick);
        }
    }

    public static void putArea(ServerLevel level, BlockPos center, int radius, double scale) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    put(level, center.offset(x, y, z), scale);
                }
            }
        }
    }

    public static double get(ServerLevel level, BlockPos pos, double defaultScale) {
        String dimension = level.dimension().location().toString();
        CachedScale cached = CACHE.get(new ContextKey(dimension, pos));
        if (cached == null) return defaultScale;
        
        long currentTick = level.getGameTime();
        if (currentTick > cached.expiryTick + 3) {
            CACHE.remove(new ContextKey(dimension, pos));
            return defaultScale;
        }
        
        return cached.scale;
    }

    public static double getNearby(ServerLevel level, BlockPos pos, int radius, double defaultScale) {
        double scale = get(level, pos, defaultScale);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    scale = Math.min(scale, get(level, pos.offset(x, y, z), defaultScale));
                }
            }
        }
        return scale;
    }

    private static void cleanup(long currentTick) {
        CACHE.entrySet().removeIf(entry -> currentTick > entry.getValue().expiryTick + 20);
    }

    private record ContextKey(String dimension, BlockPos pos) {}
    private record CachedScale(double scale, long expiryTick) {}
}
