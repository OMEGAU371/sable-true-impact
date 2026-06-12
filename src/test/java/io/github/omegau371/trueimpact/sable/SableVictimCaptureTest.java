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
