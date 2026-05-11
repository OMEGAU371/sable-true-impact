package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockDamageAccumulator {
    private static final Map<DamageKey, DamageRecord> DAMAGE = new ConcurrentHashMap<>();
    private static volatile long lastCleanupTick = 0L;

    private BlockDamageAccumulator() {
    }

    public static boolean apply(ServerLevel level, BlockPos pos, double damage, double breakThreshold, int crackId) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_CUMULATIVE_BLOCK_DAMAGE.get()
                || !TrueImpactConfig.PERFORMANCE_QUALITY_MODE.get().enableCracks
                || damage <= 0.0
                || breakThreshold <= 0.0) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0f) {
            return false;
        }

        cleanup(level.getGameTime());
        DamageKey key = new DamageKey(level.dimension().location().toString(), pos.asLong());
        DamageRecord record = DAMAGE.computeIfAbsent(key, ignored -> new DamageRecord(0.0, level.getGameTime()));
        record.damage += damage * TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.get();
        record.lastTick = level.getGameTime();

        if (record.damage >= breakThreshold) {
            DAMAGE.remove(key);
            level.destroyBlock(pos, true);
            return true;
        }

        if (TrueImpactConfig.ENABLE_CRACKS.get()) {
            int progress = (int) Math.min(9, Math.floor((record.damage / breakThreshold) * 10.0));
            level.destroyBlockProgress(crackId, pos, progress);
        }
        return false;
    }

    public static double damageRatio(ServerLevel level, BlockPos pos, double breakThreshold) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get() || breakThreshold <= 0.0) {
            return 0.0;
        }
        cleanup(level.getGameTime());
        DamageKey key = new DamageKey(level.dimension().location().toString(), pos.asLong());
        DamageRecord record = DAMAGE.get(key);
        return record == null ? 0.0 : Math.min(1.0, record.damage / breakThreshold);
    }

    private static void cleanup(long gameTime) {
        if (gameTime - lastCleanupTick < 200L) {
            return;
        }
        lastCleanupTick = gameTime;
        long expiry = TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS.get();
        int maxEntries = TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES.get();
        Iterator<Map.Entry<DamageKey, DamageRecord>> iterator = DAMAGE.entrySet().iterator();
        while (iterator.hasNext()) {
            DamageRecord record = iterator.next().getValue();
            if (gameTime - record.lastTick > expiry || DAMAGE.size() > maxEntries) {
                iterator.remove();
            }
        }
    }

    private record DamageKey(String dimension, long pos) {
    }

    private static final class DamageRecord {
        private double damage;
        private long lastTick;

        private DamageRecord(double damage, long lastTick) {
            this.damage = damage;
            this.lastTick = lastTick;
        }
    }
}
