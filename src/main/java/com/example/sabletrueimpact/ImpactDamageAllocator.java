/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.state.BlockState
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ImpactDamageAllocator {
    private static final Logger LOGGER = LogManager.getLogger();

    private ImpactDamageAllocator() {
    }

    public static double impactResistance(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return 1.0;
        }
        float rawHardness = state.getDestroySpeed((BlockGetter)level, pos);
        if (rawHardness < 0.0f) {
            return 1000000.0;
        }
        return ImpactDamageAllocator.materialStats(level, pos, state).resistance();
    }

    public static double damageScaleForSelf(ServerLevel level, BlockPos selfPos, BlockState selfState, BlockPos targetPos, BlockState targetState) {
        double finalScale;
        if (!((Boolean)TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.get()).booleanValue()) {
            return 1.0;
        }
        MaterialStats self = ImpactDamageAllocator.materialStats(level, selfPos, selfState);
        MaterialStats target = ImpactDamageAllocator.materialStats(level, targetPos, targetState);
        double selfResistance = self.resistance();
        double targetResistance = target.resistance();
        double ratio = targetResistance / Math.max(selfResistance, 1.0);
        if (ratio <= (Double)TrueImpactConfig.STRONG_MATERIAL_FATIGUE_IMMUNITY_RATIO.get()) {
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                ImpactDamageAllocator.logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, 0.0, -1.0);
            }
            return 0.0;
        }
        if (ratio <= (Double)TrueImpactConfig.SELF_DAMAGE_IMMUNITY_RATIO.get()) {
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                ImpactDamageAllocator.logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, 0.0, -1.0);
            }
            return 0.0;
        }
        double toughnessRatio = target.toughness() / Math.max(self.toughness(), 1.0);
        double toughnessScale = Math.pow(Math.max(toughnessRatio, 0.001), (Double)TrueImpactConfig.TOUGHNESS_MATCHUP_EXPONENT.get());
        double scale = Math.pow(ratio, (Double)TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get()) * toughnessScale;
        if (targetResistance < selfResistance * 0.08) {
            double capped = Math.min(scale, 0.05);
            finalScale = Math.max(capped, (Double)TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get());
        } else {
            finalScale = Math.max((Double)TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get(), Math.min(scale, (Double)TrueImpactConfig.MAX_SELF_DAMAGE_SCALE.get()));
        }
        if (ratio < 0.75) {
            finalScale = Math.min(finalScale, (Double)TrueImpactConfig.STRONG_MATERIAL_SELF_DAMAGE_CAP.get());
        }
        if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
            ImpactDamageAllocator.logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, finalScale, -1.0);
        }
        return finalScale;
    }

    public static double damageScaleForTarget(ServerLevel level, BlockPos targetPos, BlockState targetState, BlockPos selfPos, BlockState selfState) {
        if (!((Boolean)TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.get()).booleanValue()) {
            return 1.0;
        }
        MaterialStats target = ImpactDamageAllocator.materialStats(level, targetPos, targetState);
        MaterialStats self = ImpactDamageAllocator.materialStats(level, selfPos, selfState);
        double targetResistance = target.resistance();
        double selfResistance = self.resistance();
        double ratio = selfResistance / Math.max(targetResistance, 1.0);
        double toughnessRatio = self.toughness() / Math.max(target.toughness(), 1.0);
        double toughnessScale = Math.pow(Math.max(toughnessRatio, 0.001), (Double)TrueImpactConfig.TOUGHNESS_MATCHUP_EXPONENT.get());
        double scale = Math.pow(ratio, (Double)TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get()) * toughnessScale;
        double finalScale = Math.max((Double)TrueImpactConfig.MIN_TARGET_DAMAGE_SCALE.get(), Math.min(scale, (Double)TrueImpactConfig.MAX_TARGET_DAMAGE_SCALE.get()));
        if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
            ImpactDamageAllocator.logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, -1.0, finalScale);
        }
        return finalScale;
    }

    public static boolean isProtectedHardMaterialNearSoftTarget(ServerLevel level, BlockPos selfPos, BlockState selfState, int radius) {
        MaterialStats self = ImpactDamageAllocator.materialStats(level, selfPos, selfState);
        if (self.resistance() <= 1.0) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                for (int z = -radius; z <= radius; ++z) {
                    MaterialStats target;
                    double ratio;
                    if (x == 0 && y == 0 && z == 0) continue;
                    pos.set(selfPos.getX() + x, selfPos.getY() + y, selfPos.getZ() + z);
                    BlockState targetState = level.getBlockState((BlockPos)pos);
                    if (targetState.isAir() || targetState.getBlock() == selfState.getBlock() || !((ratio = (target = ImpactDamageAllocator.materialStats(level, (BlockPos)pos, targetState)).resistance() / Math.max(self.resistance(), 1.0)) <= (Double)TrueImpactConfig.SELF_DAMAGE_IMMUNITY_RATIO.get())) continue;
                    return true;
                }
            }
        }
        return false;
    }

    private static MaterialStats materialStats(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return new MaterialStats(1.0, 1.0, 1.0);
        }
        float rawHardness = state.getDestroySpeed((BlockGetter)level, pos);
        if (rawHardness < 0.0f) {
            return new MaterialStats(1000000.0, 1000000.0, 1000000.0);
        }
        double baseStrength = MaterialImpactProperties.baseStrength((BlockGetter)level, pos, state);
        double strength = Math.max(MaterialImpactProperties.displayStrength(state, baseStrength), 1.0);
        double toughness = Math.max(MaterialImpactProperties.displayToughness(state, baseStrength), strength);
        double resistance = Math.sqrt(strength * toughness);
        return new MaterialStats(strength, toughness, resistance);
    }

    private static void logMatchup(ServerLevel level, BlockPos selfPos, BlockState selfState, double selfRes, BlockPos targetPos, BlockState targetState, double targetRes, double selfScale, double targetScale) {
        String selfId = selfState.getBlock().toString();
        String targetId = targetState.getBlock().toString();
        LOGGER.info("[TrueImpact] Matchup Detection:");
        LOGGER.info("  - Self: {} at {} (Res: {})", selfId, selfPos, String.format("%.2f", selfRes));
        LOGGER.info("  - Target: {} at {} (Res: {})", targetId, targetPos, String.format("%.2f", targetRes));
        if (selfScale >= 0.0) {
            LOGGER.info("  - Self Fracture Scale: {}", String.format("%.4f", selfScale));
        }
        if (targetScale >= 0.0) {
            LOGGER.info("  - Terrain Damage Scale: {}", String.format("%.4f", targetScale));
        }
    }

    private record MaterialStats(double strength, double toughness, double resistance) {
    }
}
