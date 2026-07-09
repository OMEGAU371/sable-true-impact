package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Phase3ADamage.applyDamage.
 *
 * No Minecraft runtime needed — all Minecraft types (BlockPos) are hidden behind
 * Phase3ADamage.BlockDestroyer so we can use plain Java lambdas.
 *
 * Coordinate convention: event.cpX/Y/Z is the Rapier body-COM-local contact point.
 * Phase3ADamage.faceAwareRound converts cp to block-local index with a small epsilon
 * to handle face contacts at ±0.5 correctly.
 */
class Phase3ADamageTest {

    static final double THRESHOLD = Phase3ADamage.stoneBreakThreshold(); // 500 J

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** Builds a DeferredSublevelDamageEvent with cp=(0,0,0) and given kImpact. */
    private static DeferredSublevelDamageEvent event(double kImpact) {
        return new DeferredSublevelDamageEvent(0L, "minecraft:overworld", 1,
                0.0, 0.0, 0.0, kImpact, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, 0.0,
                -1, Double.NaN, Double.NaN, Double.NaN);
    }

    /** Builds an event with given body-local contact point and kImpact. */
    private static DeferredSublevelDamageEvent event(double cpX, double cpY, double cpZ, double kImpact) {
        return new DeferredSublevelDamageEvent(0L, "minecraft:overworld", 1,
                cpX, cpY, cpZ, kImpact, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, 0.0,
                -1, Double.NaN, Double.NaN, Double.NaN);
    }

    /** Collects (x,y,z) triples of all destroyBlock calls. */
    private static List<int[]> collectDestroyed(DeferredSublevelDamageEvent e) {
        List<int[]> calls = new ArrayList<>();
        Phase3ADamage.applyDamage((x, y, z) -> {
            calls.add(new int[]{x, y, z});
            return true;
        }, e);
        return calls;
    }

    // ─── threshold boundary ───────────────────────────────────────────────────

    @Test
    void highImpact_callsDestroyer() {
        var calls = collectDestroyed(event(THRESHOLD * 2));
        assertEquals(1, calls.size(), "expected exactly one destroyBlock call");
    }

    @Test
    void lowImpact_doesNotCallDestroyer() {
        var calls = collectDestroyed(event(THRESHOLD * 0.5));
        assertTrue(calls.isEmpty(), "should not call destroyBlock below threshold");
    }

    @Test
    void exactThreshold_doesNotBreak() {
        var calls = collectDestroyed(event(THRESHOLD));
        assertTrue(calls.isEmpty(), "kImpact == threshold should not break (requires strictly >)");
    }

    @Test
    void oneJoulAboveThreshold_breaks() {
        var calls = collectDestroyed(event(THRESHOLD + 1.0));
        assertEquals(1, calls.size());
    }

    @Test
    void zeroImpact_doesNotBreak() {
        var calls = collectDestroyed(event(0.0));
        assertTrue(calls.isEmpty());
    }

    // ─── faceAwareRound — coordinate mapping ─────────────────────────────────

    @Test
    void centerContact_givesLocalZero() {
        // cp=(0,0,0) → block (0,0,0)
        var calls = collectDestroyed(event(0.0, 0.0, 0.0, THRESHOLD * 10));
        assertArrayEquals(new int[]{0, 0, 0}, calls.get(0));
    }

    @Test
    void bottomFaceContact_givesLocalZeroY() {
        // cp.Y = -0.500 (bottom face of a single-block sublevel) → block Y=0
        // This is the most common Phase 3A scenario: sublevel hitting the floor.
        var calls = collectDestroyed(event(0.0, -0.500, 0.0, THRESHOLD * 10));
        assertArrayEquals(new int[]{0, 0, 0}, calls.get(0),
                "bottom-face contact at Y=-0.5 should map to block Y=0, not Y=-1");
    }

    @Test
    void rightFaceContact_givesLocalZeroX() {
        // cp.X = +0.500 (right face) → block X=0 (the face belongs to the block on the interior side)
        var calls = collectDestroyed(event(0.500, 0.0, 0.0, THRESHOLD * 10));
        assertArrayEquals(new int[]{0, 0, 0}, calls.get(0),
                "face contact at X=+0.5 should map to block X=0, not X=1");
    }

    @Test
    void cornerContact_givesLocalZero() {
        // cp=(0.500,-0.500,0.500) — the corner case observed with glass sublevel in GameTest
        var calls = collectDestroyed(event(0.500, -0.500, 0.500, THRESHOLD * 10));
        assertArrayEquals(new int[]{0, 0, 0}, calls.get(0),
                "corner contact at (0.5,-0.5,0.5) should map to block (0,0,0)");
    }

    @Test
    void interiorContact_roundsToNearestBlock() {
        // cp=(0.971,-1.070,1.029) — interior contact in a multi-block sublevel
        var calls = collectDestroyed(event(0.971, -1.070, 1.029, THRESHOLD * 10));
        assertArrayEquals(new int[]{1, -1, 1}, calls.get(0),
                "interior cp should round to nearest block center");
    }

    @Test
    void negativeInteriorContact() {
        // cp.Y = -1.07 → block Y=-1 (block below center in multi-block sublevel)
        var calls = collectDestroyed(event(0.0, -1.07, 0.0, THRESHOLD * 10));
        assertArrayEquals(new int[]{0, -1, 0}, calls.get(0));
    }

    @Test
    void positiveXContact_nearCenter() {
        // cp.X = 0.9 → interior of block 1 (just under 1.0) → rounds to 1
        var calls = collectDestroyed(event(0.9, -0.1, 0.9, THRESHOLD * 10));
        assertArrayEquals(new int[]{1, 0, 1}, calls.get(0),
                "cp=0.9 is inside block 1 (rounds to 1), cp=-0.1 is inside block 0 (rounds to 0)");
    }

    // ─── faceAwareRound unit tests ────────────────────────────────────────────

    @Test
    void faceAwareRound_positiveFace() {
        assertEquals(0, Phase3ADamage.faceAwareRound(0.5),
                "+0.5 face should stay in block 0");
    }

    @Test
    void faceAwareRound_negativeFace() {
        assertEquals(0, Phase3ADamage.faceAwareRound(-0.5),
                "-0.5 face should stay in block 0");
    }

    @Test
    void faceAwareRound_slightlyAboveHalf() {
        assertEquals(1, Phase3ADamage.faceAwareRound(0.52),
                "0.52 is past the face epsilon and rounds to block 1");
    }

    @Test
    void faceAwareRound_slightlyBelowNegativeHalf() {
        assertEquals(-1, Phase3ADamage.faceAwareRound(-0.52),
                "-0.52 rounds to block -1");
    }

    // ─── destroyer return value ───────────────────────────────────────────────

    @Test
    void returnsTrue_whenDestroyerReturnsTrue() {
        boolean result = Phase3ADamage.applyDamage(
                (x, y, z) -> true,
                event(THRESHOLD * 10));
        assertTrue(result);
    }

    @Test
    void returnsFalse_whenDestroyerReturnsFalse() {
        boolean result = Phase3ADamage.applyDamage(
                (x, y, z) -> false,
                event(THRESHOLD * 10));
        assertFalse(result, "applyDamage should propagate destroyer's false (air block)");
    }

    @Test
    void returnsFalse_whenBelowThreshold() {
        boolean result = Phase3ADamage.applyDamage(
                (x, y, z) -> { throw new AssertionError("should not be called"); },
                event(THRESHOLD * 0.1));
        assertFalse(result);
    }

    // ─── explicit threshold overload ──────────────────────────────────────────

    @Test
    void explicitThreshold_belowKImpact_breaks() {
        boolean result = Phase3ADamage.applyDamage((x, y, z) -> true, event(1000.0), 500.0);
        assertTrue(result, "kImpact=1000 > explicit threshold=500 → should break");
    }

    @Test
    void explicitThreshold_aboveKImpact_doesNotBreak() {
        boolean result = Phase3ADamage.applyDamage(
                (x, y, z) -> { throw new AssertionError("should not call destroyer"); },
                event(44.0), 45.0);
        assertFalse(result, "kImpact=44 < explicit threshold=45 → should not break");
    }

    @Test
    void explicitThreshold_exactlyAtThreshold_doesNotBreak() {
        boolean result = Phase3ADamage.applyDamage((x, y, z) -> true, event(45.0), 45.0);
        assertFalse(result, "kImpact == threshold: strictly > required");
    }

    @Test
    void brittleThreshold_45J_glassBoundary() {
        double brittle = MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.BRITTLE)
                * MaterialThresholdProfile.breakMultiplier(MaterialThresholdProfile.MaterialClass.BRITTLE);
        assertEquals(45.0, brittle, 0.001);
        // 45 J → no break; 46 J → break
        assertFalse(Phase3ADamage.applyDamage((x, y, z) -> true, event(45.0), brittle));
        assertTrue(Phase3ADamage.applyDamage((x, y, z) -> true, event(46.0), brittle));
    }

    @Test
    void stoneThreshold_500J_boundary() {
        double stone = MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.STONE)
                * MaterialThresholdProfile.breakMultiplier(MaterialThresholdProfile.MaterialClass.STONE);
        assertEquals(500.0, stone, 0.001);
        assertFalse(Phase3ADamage.applyDamage((x, y, z) -> true, event(500.0), stone));
        assertTrue(Phase3ADamage.applyDamage((x, y, z) -> true, event(501.0), stone));
    }

    @Test
    void noArgOverload_usesStonethreshold() {
        // applyDamage(destroyer, event) defaults to STONE break threshold (500 J)
        assertFalse(Phase3ADamage.applyDamage((x, y, z) -> true, event(499.0)));
        assertTrue(Phase3ADamage.applyDamage((x, y, z) -> true, event(501.0)));
    }

    // ─── parametrized impact levels ───────────────────────────────────────────

    @ParameterizedTest(name = "kImpact={0} → shouldBreak={1}")
    @CsvSource({
        "0,     false",
        "100,   false",
        "499,   false",
        "500,   false",   // exactly at threshold: NOT broken (strictly >)
        "501,   true",
        "1000,  true",
        "50000, true",
        "200000, true",
    })
    void thresholdTable(double kImpact, boolean shouldBreak) {
        boolean[] called = {false};
        Phase3ADamage.applyDamage((x, y, z) -> {
            called[0] = true;
            return true;
        }, event(kImpact));
        assertEquals(shouldBreak, called[0],
                "kImpact=" + kImpact + " expected shouldBreak=" + shouldBreak);
    }
}
