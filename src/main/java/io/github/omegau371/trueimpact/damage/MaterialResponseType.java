package io.github.omegau371.trueimpact.damage;

/**
 * Phase 2E: primary classification of the planned material response to accumulated impact damage.
 *
 * NONE                -- no response; ratio below crack threshold (INTACT/BRUISED).
 * COSMETIC_CRACK      -- visual-only indication; ratio in [0.60, 1.00) (CRACKED state).
 * COMPACT_SOFT_SOIL   -- soil compaction (e.g. grass_block -> dirt); handled by ImpactBlockApplicator.
 * DROP_DEBRIS         -- spawn one block-item at the impact position (first hit only; block preserved).
 * FUTURE_BREAK_ELIGIBLE -- block flagged for future destruction; no world action in Phase 2E.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public enum MaterialResponseType {
    NONE,
    COSMETIC_CRACK,
    COMPACT_SOFT_SOIL,
    DROP_DEBRIS,
    FUTURE_BREAK_ELIGIBLE
}
