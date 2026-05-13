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
        
        float rawHardness = state.getDestroySpeed(level, pos);
        if (rawHardness < 0.0f) {
            return 1_000_000.0;
        }
        double baseStrength = MaterialImpactProperties.baseStrength(level, pos, state);
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

        double ratio = targetResistance / Math.max(selfResistance, 1.0);
        double scale = Math.pow(ratio, TrueImpactConfig.MATERIAL_MATCHUP_EXPONENT.get());
        
        double finalScale;
        // Soft-target protection: prevent heavy structures from breaking when hitting grass/crops
        if (targetResistance < selfResistance * 0.08) {
            double capped = Math.min(scale, 0.05);
            finalScale = Math.max(capped, TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get());
        } else {
            finalScale = Math.max(TrueImpactConfig.MIN_SELF_DAMAGE_SCALE.get(), 
                                 Math.min(scale, TrueImpactConfig.MAX_SELF_DAMAGE_SCALE.get()));
        }

        if (TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()) {
            logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, finalScale, -1.0);
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
            logMatchup(level, selfPos, selfState, selfResistance, targetPos, targetState, targetResistance, -1.0, finalScale);
        }

        return finalScale;
    }

    private static void logMatchup(ServerLevel level, BlockPos selfPos, BlockState selfState, double selfRes, 
                                 BlockPos targetPos, BlockState targetState, double targetRes, 
                                 double selfScale, double targetScale) {
        String selfId = selfState.getBlock().toString();
        String targetId = targetState.getBlock().toString();
        
        LOGGER.info("[TrueImpact] Matchup Detection:");
        LOGGER.info("  - Self: {} at {} (Res: {})", selfId, selfPos, String.format("%.2f", selfRes));
        LOGGER.info("  - Target: {} at {} (Res: {})", targetId, targetPos, String.format("%.2f", targetRes));
        if (selfScale >= 0) LOGGER.info("  - Self Fracture Scale: {}", String.format("%.4f", selfScale));
        if (targetScale >= 0) LOGGER.info("  - Terrain Damage Scale: {}", String.format("%.4f", targetScale));
    }
}
