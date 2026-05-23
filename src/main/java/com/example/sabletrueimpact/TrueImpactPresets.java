/*
 * Decompiled with CFR 0.152.
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrueImpactPresets {
    private TrueImpactPresets() {
    }

    public static void apply() {
        String mode = TrueImpactPresets.normalized(TrueImpactConfig.PRESET_MODE.get());
        if (!"auto".equals(mode)) {
            return;
        }
        TrueImpactPresets.applyDestruction(TrueImpactPresets.normalized(TrueImpactConfig.DESTRUCTION_PRESET.get()));
        TrueImpactPresets.applyPerformance(TrueImpactPresets.normalized(TrueImpactConfig.PERFORMANCE_PRESET.get()));
    }

    private static void applyDestruction(String preset) {
        switch (preset) {
            case "off": {
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
                TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE.set(false);
                break;
            }
            case "low": {
                TrueImpactPresets.detailToggles(true, true, false, false, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.034);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.04);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(0.65);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(130.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.45);
                break;
            }
            case "high": {
                TrueImpactPresets.detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.048);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.09);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(1.6);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(285.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.8);
                break;
            }
            case "cinematic": {
                TrueImpactPresets.detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.06);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.12);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(2.2);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(380.0);
                TrueImpactConfig.EXPLOSION_IMPACT_CONFINEMENT_SCALE.set(6.5);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(1.0);
                break;
            }
            default: {
                TrueImpactPresets.detailToggles(true, true, true, true, true, true);
                TrueImpactConfig.DAMAGE_SCALE.set(0.042);
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.set(0.068);
                TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.set(1.25);
                TrueImpactConfig.EXPLOSION_IMPACT_FORCE_SCALE.set(225.0);
                TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_SCALE.set(0.65);
            }
        }
    }

    // beta.7 — B2 fix. Previously every preset (low/default/high/cinematic) unconditionally
    // overwrote every destruction toggle to true. Result: any user who set, say,
    // `enablePhysicalDestruction=false` in their config had their value silently flipped back
    // to true on every load — preset's wishes always won. Bug reported by John Aeronautics
    // (Discord, 2026-05-18 ~290~300): "toggle doesn't work, blocks still break".
    //
    // New rule: a preset only writes a toggle if that toggle is at its DEFAULT value (no
    // user override). If the user has explicitly changed a toggle, it's left alone. Defaults
    // for all destruction toggles are `true`, so `setIfDefault(cfg, target, true)` means:
    //   - user value `true` (default)  → preset can change to `target`
    //   - user value `false` (override) → preset SKIPS, keeps user value
    //
    // The `off` preset above is unaffected — it's the explicit "bulk disable" convenience
    // and still uses .set() directly. Anyone using `destructionPreset = off` is asking for
    // everything turned off, so we honour that.
    private static void detailToggles(boolean cracks, boolean blockBreaking, boolean propagation, boolean fracture, boolean explosions, boolean materialMatchup) {
        setIfDefault(TrueImpactConfig.ENABLE_CRACKS, cracks, true);
        setIfDefault(TrueImpactConfig.ENABLE_CUMULATIVE_BLOCK_DAMAGE, cracks, true);
        setIfDefault(TrueImpactConfig.ENABLE_BLOCK_BREAKING, blockBreaking, true);
        setIfDefault(TrueImpactConfig.ENABLE_WORLD_DESTRUCTION, blockBreaking, true);
        setIfDefault(TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE, blockBreaking, true);
        setIfDefault(TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS, blockBreaking, true);
        setIfDefault(TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION, fracture, true);
        setIfDefault(TrueImpactConfig.ENABLE_CRACK_PROPAGATION, propagation, true);
        setIfDefault(TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE, fracture, true);
        setIfDefault(TrueImpactConfig.ENABLE_EXPLOSION_IMPACT_FRACTURE, explosions, true);
        setIfDefault(TrueImpactConfig.ENABLE_EXPLOSION_IMPULSE, explosions, true);
        setIfDefault(TrueImpactConfig.ENABLE_MATERIAL_MATCHUP_DAMAGE, materialMatchup, true);
    }

    // Helper for the "respect user override" semantics — see detailToggles' comment.
    private static void setIfDefault(ModConfigSpec.BooleanValue cfg, boolean target, boolean defaultValue) {
        try {
            if (cfg.get() == defaultValue) {
                cfg.set(target);
            }
        } catch (Throwable t) {
            // Best-effort — never crash config load over preset application.
        }
    }

    private static void applyPerformance(String preset) {
        switch (preset) {
            case "potato": {
                TrueImpactPresets.budgets(2, 24, 12, 4, 1, false, 4, 2, 4, 512, 6, 64, 12, 4);
                break;
            }
            case "very_low": {
                TrueImpactPresets.budgets(3, 48, 24, 6, 1, false, 8, 2, 6, 1024, 12, 96, 16, 6);
                break;
            }
            case "low": {
                TrueImpactPresets.budgets(4, 96, 48, 8, 2, false, 16, 3, 10, 2048, 24, 128, 24, 8);
                break;
            }
            case "high": {
                TrueImpactPresets.budgets(8, 768, 160, 32, 8, true, 64, 8, 32, 8192, 96, 384, 64, 24);
                break;
            }
            case "very_high": {
                TrueImpactPresets.budgets(12, 1200, 256, 48, 12, true, 96, 12, 48, 12000, 128, 512, 96, 32);
                break;
            }
            case "destructive": {
                TrueImpactPresets.budgets(24, 2400, 512, 96, 24, true, 160, 24, 96, 20000, 192, 768, 160, 48);
                break;
            }
            default: {
                TrueImpactPresets.budgets(6, 384, 96, 18, 4, false, 32, 4, 18, 4096, 48, 256, 32, 18);
            }
        }
    }

    private static void budgets(int fractureAttempts, int fractureChecks, int fractureCandidates, int fractureBlocks, int asyncApplied, boolean asyncFracture, int explosionRays, int terrainBlocks, int terrainContactSamples, int cumulativeEntries, int elasticScanLimit, int explosionSubLevels, int maxPropagationBlocks, int impactExplosionBatch) {
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.set(fractureAttempts);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS.set(fractureChecks);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.set(fractureCandidates);
        TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.set(fractureBlocks);
        TrueImpactConfig.ENABLE_ASYNC_FRACTURE_ANALYSIS.set(asyncFracture);
        TrueImpactConfig.ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK.set(asyncApplied);
        TrueImpactConfig.EXPLOSION_IMPACT_RAY_SAMPLES.set(explosionRays);
        TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.set(terrainBlocks);
        TrueImpactConfig.TERRAIN_IMPACT_CONTACT_SAMPLES.set(terrainContactSamples);
        TrueImpactConfig.CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES.set(cumulativeEntries);
        TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.set(elasticScanLimit);
        TrueImpactConfig.EXPLOSION_IMPACT_MAX_SUBLEVELS.set(explosionSubLevels);
        TrueImpactConfig.MAX_PROPAGATION_BLOCKS.set(maxPropagationBlocks);
        TrueImpactConfig.IMPACT_EXPLOSION_MAX_PER_BATCH.set(impactExplosionBatch);
    }

    private static String normalized(Object value) {
        return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
