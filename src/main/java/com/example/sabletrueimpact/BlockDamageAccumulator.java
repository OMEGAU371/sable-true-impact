/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockDamageAccumulator {
    private static final Map<DamageKey, DamageRecord> DAMAGE = new ConcurrentHashMap<DamageKey, DamageRecord>();
    private static volatile long lastCleanupTick = 0L;

    private BlockDamageAccumulator() {
    }

    public static boolean apply(ServerLevel level, BlockPos pos, double damage, double breakThreshold, int crackId) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_CUMULATIVE_BLOCK_DAMAGE.get()).booleanValue() || damage <= 0.0 || breakThreshold <= 0.0) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed((BlockGetter)level, pos) < 0.0f) {
            return false;
        }
        double scaledDamage = damage * (Double)TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.get();
        if (scaledDamage < breakThreshold * (Double)TrueImpactConfig.MIN_CUMULATIVE_DAMAGE_RATIO.get()) {
            return false;
        }
        BlockDamageAccumulator.cleanup(level.getGameTime());
        DamageKey key = new DamageKey(level.dimension().location().toString(), pos.asLong());
        DamageRecord record = DAMAGE.computeIfAbsent(key, ignored -> new DamageRecord(0.0, level.getGameTime()));
        record.damage += scaledDamage;
        record.lastTick = level.getGameTime();
        if (record.damage >= breakThreshold) {
            DAMAGE.remove(key);
            final ServerLevel destroyLevel = level;
            final BlockPos destroyPos = pos.immutable();
            level.getServer().execute(() -> destroyLevel.destroyBlock(destroyPos, true));
            return true;
        }
        if (((Boolean)TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue()) {
            int progress = (int)Math.min(9.0, Math.floor(record.damage / breakThreshold * 10.0));
            level.destroyBlockProgress(crackId, pos, progress);
        }
        return false;
    }

    public static double damageRatio(ServerLevel level, BlockPos pos, double breakThreshold) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || breakThreshold <= 0.0) {
            return 0.0;
        }
        BlockDamageAccumulator.cleanup(level.getGameTime());
        DamageKey key = new DamageKey(level.dimension().location().toString(), pos.asLong());
        DamageRecord record = DAMAGE.get(key);
        return record == null ? 0.0 : Math.min(1.0, record.damage / breakThreshold);
    }

    private static void cleanup(long gameTime) {
        if (gameTime - lastCleanupTick < 200L) {
            return;
        }
        lastCleanupTick = gameTime;
        long expiry = ((Integer)TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS.get()).intValue();
        int maxEntries = (Integer)TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES.get();
        Iterator<Map.Entry<DamageKey, DamageRecord>> iterator = DAMAGE.entrySet().iterator();
        while (iterator.hasNext()) {
            DamageRecord record = iterator.next().getValue();
            if (gameTime - record.lastTick <= expiry && DAMAGE.size() <= maxEntries) continue;
            iterator.remove();
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
