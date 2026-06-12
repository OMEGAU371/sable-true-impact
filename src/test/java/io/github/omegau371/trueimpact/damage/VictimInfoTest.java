package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VictimInfo factory methods and field values.
 * No Minecraft runtime required.
 */
class VictimInfoTest {

    // -- unknown() -----------------------------------------------------------------

    @Test
    void unknown_has_UNKNOWN_kind() {
        assertEquals(VictimInfo.Kind.UNKNOWN, VictimInfo.unknown().kind());
    }

    @Test
    void unknown_has_null_blockId() {
        assertNull(VictimInfo.unknown().blockId());
    }

    @Test
    void unknown_has_no_pos() {
        assertFalse(VictimInfo.unknown().hasPos());
    }

    @Test
    void unknown_has_UNKNOWN_confidence() {
        assertEquals(VictimInfo.Confidence.UNKNOWN, VictimInfo.unknown().confidence());
    }

    @Test
    void unknown_has_NONE_source() {
        assertEquals(VictimInfo.Source.NONE, VictimInfo.unknown().source());
    }

    @Test
    void unknown_falls_back_to_GENERIC_class() {
        assertEquals(MaterialThresholdProfile.MaterialClass.GENERIC,
                VictimInfo.unknown().materialClass());
    }

    @Test
    void unknown_has_GENERIC_threshold_50() {
        assertEquals(50.0, VictimInfo.unknown().materialThresholdJ(), 0.001);
    }

    // -- activeSublevel() ----------------------------------------------------------

    @Test
    void activeSublevel_has_ACTIVE_SUBLEVEL_kind() {
        assertEquals(VictimInfo.Kind.ACTIVE_SUBLEVEL, VictimInfo.activeSublevel().kind());
    }

    @Test
    void activeSublevel_has_EXACT_confidence() {
        assertEquals(VictimInfo.Confidence.EXACT, VictimInfo.activeSublevel().confidence());
    }

    @Test
    void activeSublevel_has_NONE_source() {
        assertEquals(VictimInfo.Source.NONE, VictimInfo.activeSublevel().source());
    }

    @Test
    void activeSublevel_has_no_pos() {
        assertFalse(VictimInfo.activeSublevel().hasPos());
    }

    @Test
    void activeSublevel_uses_GENERIC_threshold() {
        assertEquals(50.0, VictimInfo.activeSublevel().materialThresholdJ(), 0.001);
    }

    // -- worldBlock: dirt -> SOFT_SOIL threshold 5 ---------------------------------

    @Test
    void worldBlock_dirt_has_WORLD_BLOCK_kind() {
        VictimInfo vi = worldBlockAt("minecraft:dirt");
        assertEquals(VictimInfo.Kind.WORLD_BLOCK, vi.kind());
    }

    @Test
    void worldBlock_dirt_maps_to_SOFT_SOIL() {
        assertEquals(MaterialThresholdProfile.MaterialClass.SOFT_SOIL,
                worldBlockAt("minecraft:dirt").materialClass());
    }

    @Test
    void worldBlock_dirt_threshold_is_5() {
        assertEquals(5.0, worldBlockAt("minecraft:dirt").materialThresholdJ(), 0.001);
    }

    @Test
    void worldBlock_dirt_carries_blockId() {
        assertEquals("minecraft:dirt", worldBlockAt("minecraft:dirt").blockId());
    }

    @Test
    void worldBlock_dirt_has_pos_when_provided() {
        VictimInfo vi = VictimInfo.worldBlock("minecraft:dirt", 10, 64, 10,
                VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
        assertTrue(vi.hasPos());
        assertEquals(10, vi.posX());
        assertEquals(64, vi.posY());
        assertEquals(10, vi.posZ());
    }

    // -- worldBlock: oak_planks -> WOOD threshold 20 -------------------------------

    @Test
    void worldBlock_oak_planks_maps_to_WOOD_threshold_20() {
        VictimInfo vi = worldBlockAt("minecraft:oak_planks");
        assertEquals(MaterialThresholdProfile.MaterialClass.WOOD, vi.materialClass());
        assertEquals(20.0, vi.materialThresholdJ(), 0.001);
    }

    // -- worldBlock: stone -> STONE threshold 50 -----------------------------------

    @Test
    void worldBlock_stone_maps_to_STONE_threshold_50() {
        VictimInfo vi = worldBlockAt("minecraft:stone");
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, vi.materialClass());
        assertEquals(50.0, vi.materialThresholdJ(), 0.001);
    }

    // -- worldBlock: iron_block -> METAL threshold 120 ----------------------------

    @Test
    void worldBlock_iron_block_maps_to_METAL_threshold_120() {
        VictimInfo vi = worldBlockAt("minecraft:iron_block");
        assertEquals(MaterialThresholdProfile.MaterialClass.METAL, vi.materialClass());
        assertEquals(120.0, vi.materialThresholdJ(), 0.001);
    }

    // -- worldBlock: obsidian -> HIGH_STRENGTH threshold 300 -----------------------

    @Test
    void worldBlock_obsidian_maps_to_HIGH_STRENGTH_threshold_300() {
        VictimInfo vi = worldBlockAt("minecraft:obsidian");
        assertEquals(MaterialThresholdProfile.MaterialClass.HIGH_STRENGTH, vi.materialClass());
        assertEquals(300.0, vi.materialThresholdJ(), 0.001);
    }

    // -- worldBlock: unknown block -> GENERIC threshold 50 -------------------------

    @Test
    void worldBlock_unknown_id_falls_back_to_GENERIC_threshold_50() {
        VictimInfo vi = worldBlockAt("minecraft:glass");
        assertEquals(MaterialThresholdProfile.MaterialClass.GENERIC, vi.materialClass());
        assertEquals(50.0, vi.materialThresholdJ(), 0.001);
    }

    // -- worldBlockNoPos -----------------------------------------------------------

    @Test
    void worldBlockNoPos_has_no_pos() {
        VictimInfo vi = VictimInfo.worldBlockNoPos("minecraft:stone",
                VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
        assertFalse(vi.hasPos());
        assertEquals(VictimInfo.Kind.WORLD_BLOCK, vi.kind());
        assertEquals("minecraft:stone", vi.blockId());
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, vi.materialClass());
    }

    // -- wouldExceed via MaterialThresholdProfile ----------------------------------

    @Test
    void wouldExceed_NaN_kImpact_returns_false_for_all_classes() {
        for (MaterialThresholdProfile.MaterialClass mc : MaterialThresholdProfile.MaterialClass.values()) {
            assertFalse(MaterialThresholdProfile.wouldExceed(Double.NaN, mc),
                    "NaN kImpact must never exceed threshold for " + mc);
        }
    }

    @Test
    void wouldExceed_kImpact_above_stone_threshold_returns_true() {
        assertTrue(MaterialThresholdProfile.wouldExceed(100.0,
                MaterialThresholdProfile.MaterialClass.STONE));  // 100 > 50
    }

    @Test
    void wouldExceed_kImpact_below_stone_threshold_returns_false() {
        assertFalse(MaterialThresholdProfile.wouldExceed(30.0,
                MaterialThresholdProfile.MaterialClass.STONE));  // 30 < 50
    }

    @Test
    void wouldExceed_exactly_at_threshold_is_false() {
        // > threshold, not >= threshold
        assertFalse(MaterialThresholdProfile.wouldExceed(50.0,
                MaterialThresholdProfile.MaterialClass.STONE));
    }

    // -- confidence and source preserved -------------------------------------------

    @Test
    void worldBlock_preserves_confidence_and_source() {
        VictimInfo vi = VictimInfo.worldBlock("minecraft:dirt", 0, 0, 0,
                VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
        assertEquals(VictimInfo.Confidence.APPROX, vi.confidence());
        assertEquals(VictimInfo.Source.CALLBACK_BLOCK_POS, vi.source());
    }

    // -- helper --------------------------------------------------------------------

    private static VictimInfo worldBlockAt(String blockId) {
        return VictimInfo.worldBlock(blockId, 0, 0, 0,
                VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
    }
}
