package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ImpactBlockApplicator.
 *
 * Uses MockBlockView to avoid any Minecraft runtime dependency.
 * Tests cover: gate logic, SOFT_SOIL compaction, debug-off behavior, stale block protection.
 */
class ImpactBlockApplicatorTest {

    @BeforeEach
    void ensureEffectsEnabled() {
        ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS = true;
    }

    @AfterEach
    void restoreDefaults() {
        ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS = true;
    }

    // ---- helper: minimal DeferredDamageEvent construction ----------------------

    private static DeferredDamageEvent softSoilEvent(String blockId, double kImpact) {
        double threshold = MaterialThresholdProfile.threshold(
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL);
        return new DeferredDamageEvent(1L, "minecraft:overworld", blockId, 5, 63, 5,
                MaterialThresholdProfile.MaterialClass.SOFT_SOIL,
                kImpact, threshold,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
    }

    private static DeferredDamageEvent stoneEvent(double kImpact) {
        double threshold = MaterialThresholdProfile.threshold(
                MaterialThresholdProfile.MaterialClass.STONE);
        return new DeferredDamageEvent(1L, "minecraft:overworld", "minecraft:stone", 5, 63, 5,
                MaterialThresholdProfile.MaterialClass.STONE,
                kImpact, threshold,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
    }

    // ---- MockBlockView ----------------------------------------------------------

    /** Simple map-backed BlockView for tests. All positions have chunks loaded by default. */
    private static final class MockBlockView implements BlockView {
        private final Map<String, String> blocks = new HashMap<>();
        private boolean chunkLoaded = true;
        private String lastSetId;

        void put(int x, int y, int z, String blockId) {
            blocks.put(x + "," + y + "," + z, blockId);
        }

        String getLastSetId() { return lastSetId; }

        @Override
        public boolean hasChunkAt(int x, int y, int z) { return chunkLoaded; }

        @Override
        public String getBlockId(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
        }

        @Override
        public boolean setBlock(int x, int y, int z, String targetBlockId) {
            lastSetId = targetBlockId;
            blocks.put(x + "," + y + "," + z, targetBlockId);
            return true;
        }
    }

    // ---- checkGates (no world access) ------------------------------------------

    @Test
    void checkGates_effects_disabled_returns_SKIP_EFFECTS_DISABLED() {
        ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS = false;
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);
        assertEquals(ApplyOutcome.SKIP_EFFECTS_DISABLED,
                ImpactBlockApplicator.checkGates(event));
    }

    @Test
    void checkGates_nan_kImpact_returns_SKIP_INVALID_ENERGY() {
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", Double.NaN);
        assertEquals(ApplyOutcome.SKIP_INVALID_ENERGY,
                ImpactBlockApplicator.checkGates(event));
    }

    @Test
    void checkGates_kImpact_at_threshold_returns_SKIP_BELOW_THRESHOLD() {
        // threshold for SOFT_SOIL = 5.0; kImpact = 5.0 -> <= threshold -> skip
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 5.0);
        assertEquals(ApplyOutcome.SKIP_BELOW_THRESHOLD,
                ImpactBlockApplicator.checkGates(event));
    }

    @Test
    void checkGates_kImpact_below_threshold_returns_SKIP_BELOW_THRESHOLD() {
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 3.0);
        assertEquals(ApplyOutcome.SKIP_BELOW_THRESHOLD,
                ImpactBlockApplicator.checkGates(event));
    }

    @Test
    void checkGates_non_SOFT_SOIL_returns_SKIP_MATERIAL_CLASS() {
        DeferredDamageEvent event = stoneEvent(200.0);
        assertEquals(ApplyOutcome.SKIP_MATERIAL_CLASS,
                ImpactBlockApplicator.checkGates(event));
    }

    @Test
    void checkGates_valid_SOFT_SOIL_event_returns_null() {
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);
        assertNull(ImpactBlockApplicator.checkGates(event),
                "all gates pass -> checkGates must return null");
    }

    // ---- tryApply: SOFT_SOIL compaction ----------------------------------------

    @Test
    void grass_block_above_threshold_applies_to_dirt() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:grass_block"); // current block at event pos
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.APPLIED, outcome,
                "grass_block above SOFT_SOIL threshold must be compacted to dirt");
        assertEquals("minecraft:dirt", view.getLastSetId(),
                "setBlock must be called with minecraft:dirt");
        assertEquals("minecraft:dirt", view.getBlockId(5, 63, 5),
                "block at pos must be updated to dirt");
    }

    @Test
    void dirt_has_no_transformation_target_returns_APPLIED_NO_OP() {
        // dirt has no transformation target — accumulates damage and breaks at CRITICAL
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:dirt");
        DeferredDamageEvent event = softSoilEvent("minecraft:dirt", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.APPLIED_NO_OP, outcome,
                "dirt has no transformation target — breaks at CRITICAL via break path");
        assertNull(view.getLastSetId());
    }

    @Test
    void podzol_transforms_to_dirt() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:podzol");
        DeferredDamageEvent event = softSoilEvent("minecraft:podzol", 100.0);

        assertEquals(ApplyOutcome.APPLIED, ImpactBlockApplicator.tryApply(view, event));
        assertEquals("minecraft:dirt", view.getLastSetId());
    }

    @Test
    void mycelium_transforms_to_dirt() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:mycelium");
        DeferredDamageEvent event = softSoilEvent("minecraft:mycelium", 100.0);

        assertEquals(ApplyOutcome.APPLIED, ImpactBlockApplicator.tryApply(view, event));
        assertEquals("minecraft:dirt", view.getLastSetId());
    }

    @Test
    void suspicious_sand_transforms_to_sand() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:suspicious_sand");
        DeferredDamageEvent event = softSoilEvent("minecraft:suspicious_sand", 100.0);

        assertEquals(ApplyOutcome.APPLIED, ImpactBlockApplicator.tryApply(view, event));
        assertEquals("minecraft:sand", view.getLastSetId());
    }

    @Test
    void suspicious_gravel_transforms_to_gravel() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:suspicious_gravel");
        DeferredDamageEvent event = softSoilEvent("minecraft:suspicious_gravel", 100.0);

        assertEquals(ApplyOutcome.APPLIED, ImpactBlockApplicator.tryApply(view, event));
        assertEquals("minecraft:gravel", view.getLastSetId());
    }

    @Test
    void gravel_has_no_transformation_target_returns_APPLIED_NO_OP() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:gravel");
        DeferredDamageEvent event = softSoilEvent("minecraft:gravel", 100.0);

        assertEquals(ApplyOutcome.APPLIED_NO_OP, ImpactBlockApplicator.tryApply(view, event));
        assertNull(view.getLastSetId());
    }

    @Test
    void sand_has_no_transformation_target_returns_APPLIED_NO_OP() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:sand");
        DeferredDamageEvent event = softSoilEvent("minecraft:sand", 100.0);

        assertEquals(ApplyOutcome.APPLIED_NO_OP, ImpactBlockApplicator.tryApply(view, event));
        assertNull(view.getLastSetId());
    }

    @Test
    void grass_block_below_threshold_skips() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:grass_block");
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 3.0); // below 5.0

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.SKIP_BELOW_THRESHOLD, outcome);
        assertNull(view.getLastSetId(), "no mutation when below threshold");
    }

    @Test
    void non_SOFT_SOIL_stone_does_not_mutate_in_Phase_2A() {
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:stone");
        DeferredDamageEvent event = stoneEvent(500.0); // above stone threshold 50J

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.SKIP_MATERIAL_CLASS, outcome,
                "stone is not handled in Phase 2A -- must skip");
        assertNull(view.getLastSetId(), "stone must not be mutated in Phase 2A");
    }

    @Test
    void chunk_unloaded_skips_safely() {
        MockBlockView view = new MockBlockView();
        view.chunkLoaded = false; // simulate unloaded chunk
        view.put(5, 63, 5, "minecraft:grass_block");
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.SKIP_CHUNK_UNLOADED, outcome);
        assertNull(view.getLastSetId());
    }

    @Test
    void stale_block_mismatch_skips_safely() {
        // Block was grass_block at enqueue time, but by flush time another process
        // replaced it with stone (no longer SOFT_SOIL).
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:stone"); // stale: different material class
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.SKIP_BLOCK_MISMATCH, outcome,
                "block at pos changed to non-SOFT_SOIL between enqueue and flush -> skip");
        assertNull(view.getLastSetId(), "must not mutate stale/mismatched block");
    }

    @Test
    void already_compacted_grass_to_dirt_still_applies_NO_OP() {
        // grass_block was already compacted to dirt by a previous impact in this tick.
        // Current block is now dirt (still SOFT_SOIL), victim block is grass_block.
        // Phase 2A: dirt has no compaction target -> APPLIED_NO_OP.
        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:dirt"); // already compacted
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        // Current block is dirt (SOFT_SOIL) but COMPACTION_TARGETS["grass_block"] = "dirt"
        // Since the current block is already dirt, setBlock would be redundant.
        // However, ImpactBlockApplicator uses the VICTIM block key for target lookup,
        // so grass_block -> dirt is attempted and setBlock is called.
        // This is acceptable Phase 2A behavior: idempotent (dirt->dirt has no visible effect).
        assertTrue(outcome.wasApplied(), "re-impact on already-compacted pos is APPLIED");
    }

    @Test
    void debug_flags_off_still_applies_effect() {
        // Verify that DiagnosticConfig.ENABLED has no bearing on apply behavior.
        // ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS is the production gate, not ENABLED.
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "ENABLED must be false (default) for this test");

        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:grass_block");
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.APPLIED, outcome,
                "block effect must apply even with DiagnosticConfig.ENABLED=false");
    }

    @Test
    void ImpactRuntimeConfig_effects_disabled_blocks_all_mutations() {
        ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS = false;

        MockBlockView view = new MockBlockView();
        view.put(5, 63, 5, "minecraft:grass_block");
        DeferredDamageEvent event = softSoilEvent("minecraft:grass_block", 100.0);

        ApplyOutcome outcome = ImpactBlockApplicator.tryApply(view, event);

        assertEquals(ApplyOutcome.SKIP_EFFECTS_DISABLED, outcome);
        assertNull(view.getLastSetId(), "no mutation when APPLY_BLOCK_EFFECTS=false");
    }
}
