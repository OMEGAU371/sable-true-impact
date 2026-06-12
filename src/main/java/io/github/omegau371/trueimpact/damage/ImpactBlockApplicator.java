package io.github.omegau371.trueimpact.damage;

import java.util.Map;

/**
 * Phase 2A: applies real block effects for impact events in the deferred damage queue.
 *
 * Scope (Phase 2A only): SOFT_SOIL compaction.
 *   grass_block  -> dirt  (compaction under impact)
 *   Other SOFT_SOIL blocks (dirt, coarse_dirt, rooted_dirt, ...): APPLIED_NO_OP
 *
 * All logic is expressed through the BlockView interface (MC-free).
 * The MC-dependent ServerLevelBlockView lives in TrueImpactMod.
 *
 * Two-stage design:
 *   checkGates(event)           -- fast pre-check, no world access, fully testable
 *   tryApply(view, event)       -- calls checkGates then performs block read/write
 *
 * DamageResolver remains NONE -- Phase 2A does not route through the resolver.
 * ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS must be true for any mutation to occur.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class ImpactBlockApplicator {

    private ImpactBlockApplicator() {}

    // Phase 2A: SOFT_SOIL compaction targets.
    // Key: victim block ID (what was hit). Value: what it becomes.
    // Blocks not listed but classified as SOFT_SOIL get APPLIED_NO_OP.
    private static final Map<String, String> COMPACTION_TARGETS = Map.of(
            "minecraft:grass_block", "minecraft:dirt"
    );

    /**
     * Fast pre-check: validates all gates without world access.
     *
     * Returns an ApplyOutcome if any gate fails, or null if all gates pass
     * (meaning tryApply may proceed to world access).
     *
     * Fully MC-free and testable without a game level.
     */
    public static ApplyOutcome checkGates(DeferredDamageEvent event) {
        if (!ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS) {
            return ApplyOutcome.SKIP_EFFECTS_DISABLED;
        }
        if (!Double.isFinite(event.kImpact())) {
            return ApplyOutcome.SKIP_INVALID_ENERGY;
        }
        if (event.kImpact() <= event.threshold()) {
            return ApplyOutcome.SKIP_BELOW_THRESHOLD;
        }
        if (event.materialClass() != MaterialThresholdProfile.MaterialClass.SOFT_SOIL) {
            return ApplyOutcome.SKIP_MATERIAL_CLASS;
        }
        return null; // all gates passed
    }

    /**
     * Attempts to apply a block effect for the given event.
     *
     * 1. Runs checkGates (returns SKIP_* if any gate fails).
     * 2. Checks hasChunkAt (returns SKIP_CHUNK_UNLOADED if not loaded).
     * 3. Reads current block; verifies it is still SOFT_SOIL class
     *    (stale events where another process already changed the block are skipped).
     * 4. Looks up compaction target:
     *    - If found: calls view.setBlock(); returns APPLIED or SKIP_SET_FAILED.
     *    - If not found: returns APPLIED_NO_OP (SOFT_SOIL but no Phase 2A effect).
     */
    public static ApplyOutcome tryApply(BlockView view, DeferredDamageEvent event) {
        ApplyOutcome gate = checkGates(event);
        if (gate != null) return gate;

        int x = event.posX(), y = event.posY(), z = event.posZ();
        if (!view.hasChunkAt(x, y, z)) return ApplyOutcome.SKIP_CHUNK_UNLOADED;

        // Revalidate: current block must still be SOFT_SOIL class.
        // If a previous tick already compacted grass->dirt, the current block is dirt
        // (still SOFT_SOIL), so repeated impacts won't re-apply the same effect.
        // If a completely different block replaced it, skip to avoid mismatched mutations.
        String currentBlock = view.getBlockId(x, y, z);
        MaterialThresholdProfile.MaterialClass currentClass =
                MaterialThresholdProfile.classify(currentBlock);
        if (currentClass != MaterialThresholdProfile.MaterialClass.SOFT_SOIL) {
            return ApplyOutcome.SKIP_BLOCK_MISMATCH;
        }

        // Look up compaction target by ORIGINAL victim block (not current block).
        // The original block is recorded at enqueue time; use it to determine the
        // effect even if the block has already been compacted to an intermediate state.
        String target = COMPACTION_TARGETS.get(event.victimBlock());
        if (target == null) {
            // SOFT_SOIL but no Phase 2A compaction defined (e.g., dirt -> stays dirt)
            return ApplyOutcome.APPLIED_NO_OP;
        }

        boolean ok = view.setBlock(x, y, z, target);
        return ok ? ApplyOutcome.APPLIED : ApplyOutcome.SKIP_SET_FAILED;
    }
}
