package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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
        
        float hardness = Math.max(0.05f, state.getDestroySpeed(level, pos));
        if (hardness < 0) {
            return 1_000_000.0;
        }

        double baseStrength = TrueImpactConfig.BASE_STRENGTH.get() + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get();
        double strength = Math.max(MaterialImpactProperties.displayStrength(state, baseStrength), 1.0);
        double toughness = Math.max(MaterialImpactProperties.displayToughness(state, baseStrength), strength);
        
        return Math.sqrt(strength * toughness);
    }

    public static double damageScaleForSelf(ServerLevel level, BlockPos selfPos, BlockState selfState, BlockPos targetPos, BlockState targetState) {
        if (!TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.get()) {
            return 1.0;
        }

        double selfResistance = impactResistance(level, selfPos, selfState);
        double targetResistance = impactResistance(level, targetPos, targetState);

        // Soft-target protection: prevent heavy structures from breaking when hitting grass/crops
        if (targetResistance < selfResistance * 0.08) {
            double scale = Math.pow(targetResistance / Math.max(selfResistance, 1.0), TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get());
            double capped = Math.min(scale, 0.05);
            if (TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()) {
                LOGGER.info("[TrueImpact] Soft-target protection triggered: self={}, target={}, scale={} -> capped={}", 
                    selfState.getBlock().getName().getString(), targetState.getBlock().getName().getString(), scale, capped);
            }
            return Math.max(capped, TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get());
        }

        double ratio = targetResistance / Math.max(selfResistance, 1.0);
        double scale = Math.pow(ratio, TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get());
        double finalScale = Math.max(TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get(), 
                                    Math.min(scale, TrueImpactConfig.MAX_SELF_DAMAGE_SCALE.get()));

        if (TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()) {
            LOGGER.info("[TrueImpact] Self Damage Scale: self={}, target={}, ratio={}, scale={} -> final={}", 
                selfState.getBlock().getName().getString(), targetState.getBlock().getName().getString(), ratio, scale, finalScale);
        }

        return finalScale;
    }

    public static double damageScaleForTarget(ServerLevel level, BlockPos targetPos, BlockState targetState, BlockPos selfPos, BlockState selfState) {
        if (!TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.get()) {
            return 1.0;
        }

        double targetResistance = impactResistance(level, targetPos, targetState);
        double selfResistance = impactResistance(level, selfPos, selfState);

        double ratio = selfResistance / Math.max(targetResistance, 1.0);
        double scale = Math.pow(ratio, TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get());
        double finalScale = Math.max(TrueImpactConfig.MIN_TARGET_DAMAGE_SCALE.get(), 
                                    Math.min(scale, TrueImpactConfig.MAX_TARGET_DAMAGE_SCALE.get()));

        if (TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()) {
            LOGGER.info("[TrueImpact] Target Damage Scale: target={}, self={}, ratio={}, scale={} -> final={}", 
                targetState.getBlock().getName().getString(), selfState.getBlock().getName().getString(), ratio, scale, finalScale);
        }

        return finalScale;
    }
}
