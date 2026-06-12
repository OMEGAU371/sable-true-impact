package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.VictimInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SableVictimCapture static capture buffer.
 * No Minecraft runtime required.
 */
class SableVictimCaptureTest {

    @BeforeEach
    void resetBefore() {
        SableVictimCapture.clearForTick();
    }

    // -- initial state after clear -------------------------------------------------

    @Test
    void initial_hasCaptureThisTick_is_false() {
        assertFalse(SableVictimCapture.hasCaptureThisTick());
    }

    @Test
    void initial_buildWorldVictimInfo_returns_UNKNOWN() {
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(VictimInfo.Kind.UNKNOWN, vi.kind());
    }

    // -- capture + build -----------------------------------------------------------

    @Test
    void after_capture_hasCaptureThisTick_is_true() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 5, 64, 5, true);
        assertTrue(SableVictimCapture.hasCaptureThisTick());
    }

    @Test
    void capture_stone_builds_WORLD_BLOCK() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 5, 64, 5, true);
        assertEquals(VictimInfo.Kind.WORLD_BLOCK, SableVictimCapture.buildWorldVictimInfo().kind());
    }

    @Test
    void capture_stone_preserves_blockId() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 5, 64, 5, true);
        assertEquals("minecraft:stone", SableVictimCapture.buildWorldVictimInfo().blockId());
    }

    @Test
    void capture_stone_preserves_pos_when_posLooksWorld_true() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 5, 64, 5, true);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertTrue(vi.hasPos());
        assertEquals(5,  vi.posX());
        assertEquals(64, vi.posY());
        assertEquals(5,  vi.posZ());
    }

    @Test
    void capture_stone_maps_to_STONE_materialClass() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE,
                SableVictimCapture.buildWorldVictimInfo().materialClass());
    }

    @Test
    void capture_stone_has_APPROX_confidence() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        assertEquals(VictimInfo.Confidence.APPROX,
                SableVictimCapture.buildWorldVictimInfo().confidence());
    }

    @Test
    void capture_stone_has_CALLBACK_BLOCK_POS_source() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        assertEquals(VictimInfo.Source.CALLBACK_BLOCK_POS,
                SableVictimCapture.buildWorldVictimInfo().source());
    }

    // -- posLooksWorld=false suppresses pos ----------------------------------------

    @Test
    void capture_with_posLooksWorld_false_sets_hasPos_false() {
        SableVictimCapture.captureCallbackBlock("minecraft:obsidian", 5_000_000, 64, 5_000_000, false);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(VictimInfo.Kind.WORLD_BLOCK, vi.kind());
        assertFalse(vi.hasPos(), "embedded-range coords must not be returned as world pos");
        assertEquals("minecraft:obsidian", vi.blockId());
    }

    // -- last-write-wins -----------------------------------------------------------

    @Test
    void second_capture_overwrites_first() {
        SableVictimCapture.captureCallbackBlock("minecraft:dirt", 1, 64, 1, true);
        SableVictimCapture.captureCallbackBlock("minecraft:obsidian", 2, 64, 2, true);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals("minecraft:obsidian", vi.blockId(),
                "last-write-wins: second capture should overwrite first");
        assertEquals(MaterialThresholdProfile.MaterialClass.HIGH_STRENGTH, vi.materialClass());
    }

    // -- clearForTick resets state -------------------------------------------------

    @Test
    void clearForTick_resets_hasCaptureThisTick() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        SableVictimCapture.clearForTick();
        assertFalse(SableVictimCapture.hasCaptureThisTick());
    }

    @Test
    void clearForTick_makes_buildWorldVictimInfo_return_UNKNOWN() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        SableVictimCapture.clearForTick();
        assertEquals(VictimInfo.Kind.UNKNOWN, SableVictimCapture.buildWorldVictimInfo().kind());
    }

    // -- captureContactPointBlock (PATH B) ----------------------------------------

    @Test
    void captureContactPointBlock_sets_WORLD_BLOCK_with_CONTACT_POINT_SAMPLE_source() {
        SableVictimCapture.captureContactPointBlock("minecraft:stone", 10, 63, 10);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(VictimInfo.Kind.WORLD_BLOCK, vi.kind());
        assertEquals("minecraft:stone", vi.blockId());
        assertEquals(VictimInfo.Source.CONTACT_POINT_SAMPLE, vi.source(),
                "contact-point sampling must use CONTACT_POINT_SAMPLE source");
        assertTrue(vi.hasPos());
        assertEquals(10, vi.posX());
        assertEquals(63, vi.posY());
        assertEquals(10, vi.posZ());
    }

    @Test
    void captureContactPointBlock_stone_maps_to_STONE_threshold_50() {
        SableVictimCapture.captureContactPointBlock("minecraft:stone", 0, 64, 0);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(MaterialThresholdProfile.MaterialClass.STONE, vi.materialClass());
        assertEquals(50.0, vi.materialThresholdJ(), 0.001);
    }

    @Test
    void captureContactPointBlock_obsidian_maps_to_HIGH_STRENGTH() {
        SableVictimCapture.captureContactPointBlock("minecraft:obsidian", 0, 64, 0);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(MaterialThresholdProfile.MaterialClass.HIGH_STRENGTH, vi.materialClass());
        assertEquals(300.0, vi.materialThresholdJ(), 0.001);
    }

    @Test
    void callback_path_overrides_contact_point_path_last_write_wins() {
        // Callback fires first (during substeps), contact-point sampling runs after.
        // But in practice contact-point only runs when callback has NO data.
        // If both somehow fire, last-write-wins.
        SableVictimCapture.captureCallbackBlock("minecraft:dirt", 0, 64, 0, true);
        SableVictimCapture.captureContactPointBlock("minecraft:obsidian", 0, 63, 0);
        // Last write wins -> obsidian
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals("minecraft:obsidian", vi.blockId());
        assertEquals(VictimInfo.Source.CONTACT_POINT_SAMPLE, vi.source());
    }

    // -- captureCount -------------------------------------------------------------

    @Test
    void captureCount_is_zero_initially() {
        assertEquals(0, SableVictimCapture.captureCount());
    }

    @Test
    void captureCount_increments_per_callback_capture() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        assertEquals(1, SableVictimCapture.captureCount());
        SableVictimCapture.captureCallbackBlock("minecraft:dirt", 0, 0, 0, true);
        assertEquals(2, SableVictimCapture.captureCount());
    }

    @Test
    void captureCount_increments_per_contact_point_capture() {
        SableVictimCapture.captureContactPointBlock("minecraft:stone", 0, 0, 0);
        assertEquals(1, SableVictimCapture.captureCount());
    }

    @Test
    void clearForTick_resets_captureCount() {
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 0, 0, 0, true);
        assertEquals(2, SableVictimCapture.captureCount());
        SableVictimCapture.clearForTick();
        assertEquals(0, SableVictimCapture.captureCount());
    }

    // -- block classification via capture ------------------------------------------

    @Test
    void capture_iron_block_maps_to_METAL_threshold_120() {
        SableVictimCapture.captureCallbackBlock("minecraft:iron_block", 0, 0, 0, true);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(MaterialThresholdProfile.MaterialClass.METAL, vi.materialClass());
        assertEquals(120.0, vi.materialThresholdJ(), 0.001);
    }

    @Test
    void capture_dirt_maps_to_SOFT_SOIL_threshold_5() {
        SableVictimCapture.captureCallbackBlock("minecraft:dirt", 0, 0, 0, true);
        VictimInfo vi = SableVictimCapture.buildWorldVictimInfo();
        assertEquals(MaterialThresholdProfile.MaterialClass.SOFT_SOIL, vi.materialClass());
        assertEquals(5.0, vi.materialThresholdJ(), 0.001);
    }
}
