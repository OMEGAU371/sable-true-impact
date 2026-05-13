package com.example.sabletrueimpact;

public final class TrueImpactPresets {
    private TrueImpactPresets() {
    }

    public static void apply() {
        if (TrueImpactConfig.PRESET_MODE.get() != TrueImpactConfig.PresetMode.auto) {
            return;
        }
        applyDestruction(TrueImpactConfig.DESTRUCTION_PRESET.get());
        applyPerformance(TrueImpactConfig.PERFORMANCE_PRESET.get());
    }

    private static void applyDestruction(TrueImpactConfig.DestructionPreset preset) {
        switch (preset) {
            case off -> {
                TrueImpactConfig.ENABLE_CRACKS.set(false);
                TrueImpactConfig.ENABLE_CUMULATIVE_BLOCK_DAMAGE.set(false);
                TrueImpactConfig.ENABLE_BLOCK_BREAKING.set(false);
                TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.set(false);
                TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE.set(false);
                TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.set(false);
                TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.set(false);
                TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.set(false);
                TrueImpactConfig.ENABLE_CRACK_PROPAGATION.set(false);
                TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE.set(false);
                TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.set(false);
                TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.set(false);
                TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.set(false);
                TrueImpactConfig.ENABLE_PAIR_REACTION.set(false);
            }
            case low -> {
                detailToggles(true, true, false, false, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.034);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.04);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(0.65);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(130.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.45);
            }
            case high -> {
                detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.048);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.09);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(1.6);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(285.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.8);
            }
            case cinematic -> {
                detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.06);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.12);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(2.2);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(380.0);
                TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.set(6.5);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(1.0);
            }
            default -> {
                detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.042);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.068);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(1.25);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(225.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.65);
            }
        }
    }

    private static void detailToggles(boolean cracks, boolean blockBreaking, boolean propagation,
                                      boolean fracture, boolean explosions, boolean materialMatchup) {
        TrueImpactConfig.ENABLE_CRACKS.set(cracks);
        TrueImpactConfig.ENABLE_CUMULATIVE_BLOCK_DAMAGE.set(cracks);
        TrueImpactConfig.ENABLE_BLOCK_BREAKING.set(blockBreaking);
        TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.set(blockBreaking);
        TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE.set(blockBreaking);
        TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.set(blockBreaking);
        TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.set(fracture);
        TrueImpactConfig.ENABLE_CRACK_PROPAGATION.set(propagation);
        TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.set(fracture);
        TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE.set(explosions);
        TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE.set(explosions);
        TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.set(blockBreaking);
        TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.set(materialMatchup);
        TrueImpactConfig.ENABLE_PAIR_REACTION.set(false);
    }

    private static void applyPerformance(TrueImpactConfig.PerformancePreset preset) {
        switch (preset) {
            case potato -> budgets(2, 24, 12, 4, 1, 1, false, 6, 4, 2, 512, 6, 64, 12, 4);
            case very_low -> budgets(3, 48, 24, 6, 1, 2, false, 10, 8, 2, 1024, 12, 96, 16, 6);
            case low -> budgets(4, 96, 48, 8, 2, 2, false, 18, 16, 3, 2048, 24, 128, 24, 8);
            case high -> budgets(8, 768, 160, 32, 8, 1, true, 80, 64, 8, 8192, 96, 384, 64, 24);
            case very_high -> budgets(12, 1200, 256, 48, 12, 1, true, 128, 96, 12, 12000, 128, 512, 96, 32);
            case destructive -> budgets(24, 2400, 512, 96, 24, 1, true, 256, 160, 24, 20000, 192, 768, 160, 48);
            default -> budgets(6, 384, 96, 18, 4, 2, false, 48, 32, 4, 4096, 48, 256, 32, 18);
        }
    }

    private static void budgets(int fractureAttempts, int fractureChecks, int fractureCandidates,
                                int fractureBlocks, int asyncApplied, int entityScanInterval,
                                boolean asyncFracture, int entitySubLevels, int explosionRays,
                                int terrainBlocks, int cumulativeEntries, int elasticScanLimit,
                                int explosionSubLevels, int maxPropagationBlocks, int impactExplosionBatch) {
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.set(fractureAttempts);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS.set(fractureChecks);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.set(fractureCandidates);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.set(fractureBlocks);
        TrueImpactConfig.ENABLE_ASYNC_FRACTURE_ANALYSIS.set(asyncFracture);
        TrueImpactConfig.ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK.set(asyncApplied);
        TrueImpactConfig.ENTITY_IMPACT_SCAN_INTERVAL_TICKS.set(entityScanInterval);
        TrueImpactConfig.ENTITY_IMPACT_MAX_SUBLEVELS_PER_SCAN.set(entitySubLevels);
        TrueImpactConfig.EXPLOSION_IMPACT_RAY_SAMPLES.set(explosionRays);
        TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.set(terrainBlocks);
        TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES.set(cumulativeEntries);
        TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.set(elasticScanLimit);
        TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.set(explosionSubLevels);
        TrueImpactConfig.MAX_PROPAGATION_BLOCKS.set(maxPropagationBlocks);
        TrueImpactConfig.IMPACT_EXPLOSION_MAX_PER_BATCH.set(impactExplosionBatch);
    }

}
