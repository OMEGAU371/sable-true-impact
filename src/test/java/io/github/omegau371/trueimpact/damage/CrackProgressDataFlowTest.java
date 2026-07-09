package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-flow audit: crack overlay progress (0..9) is driven solely by
 * accumulatedEffectiveDamageJ / thresholdJ (the running ratio), never by
 * lastRawImpactJ or lastEffectiveDamageJ.
 *
 * Pipeline under test:
 *   DeferredDamageEvent.kImpact()
 *   -> BlockDamageAccumulator.accumulate()  effectiveJ = min(kImpact, threshold)
 *   -> Snapshot.ratio() = accumulatedEffectiveJ / threshold
 *   -> DamageState.of(ratio)
 *   -> CrackOverlayTracker.ratioToProgress(state, ratio)
 *   -> destroyBlockProgress()  [progress value asserted; no MC runtime needed]
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
class CrackProgressDataFlowTest {

    // STONE: crackThreshold (used as DeferredDamageEvent.threshold) = 50J.
    // Unified cap: effectiveJ = min(kImpact, 50J).
    private static final double STONE_THRESHOLD = 50.0;

    @BeforeEach
    void reset() {
        BlockDamageAccumulator.clear();
        CrackOverlayTracker.clear();
        ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION = true;
        // Exact-value data-flow assertions: disable elastic floor and relaxation.
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.0;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 0;
    }

    @org.junit.jupiter.api.AfterEach
    void restoreConfig() {
        ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR = 0.2;
        ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;
    }

    // -- shared helpers -----------------------------------------------------------

    private static DeferredDamageEvent stoneHit(double kImpact) {
        return new DeferredDamageEvent(
                1L, "minecraft:overworld", "minecraft:stone",
                10, 64, 10,
                MaterialThresholdProfile.MaterialClass.STONE,
                kImpact, STONE_THRESHOLD,
                VictimInfo.Source.CONTACT_POINT_SAMPLE, VictimInfo.Confidence.APPROX);
    }

    /** Accumulate one event and return the updated snapshot for (overworld, 10,64,10, stone). */
    private static BlockDamageAccumulator.Snapshot hit(double kImpact) {
        BlockDamageAccumulator.accumulate(stoneHit(kImpact));
        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot("minecraft:overworld", 10, 64, 10, "minecraft:stone");
        assertNotNull(snap);
        return snap;
    }

    private static int progress(BlockDamageAccumulator.Snapshot snap) {
        return CrackOverlayTracker.ratioToProgress(snap.damageState(), snap.ratio());
    }

    // -- test 1: multi-hit accumulation -------------------------------------------

    /**
     * Three 12J hits on STONE (threshold=50J).
     * effectiveJ per hit = min(12, 50) = 12J.
     *
     * hit 1: accumulated= 12J, ratio=0.24 -> INTACT  -> no overlay (-1)
     * hit 2: accumulated= 24J, ratio=0.48 -> BRUISED -> progress 3  (0.40..0.50 band)
     * hit 3: accumulated= 36J, ratio=0.72 -> CRACKED -> progress 6  (0.70..0.80 band)
     *
     * The crack level rises with accumulated ratio, not with any single hit value.
     */
    @Test
    void multi_hit_accumulation_drives_crack_progress_upward() {
        BlockDamageAccumulator.Snapshot snap1 = hit(12.0);
        assertEquals(DamageState.INTACT, snap1.damageState(),  "hit 1: ratio=0.24 -> INTACT");
        assertEquals(-1, progress(snap1), "hit 1: INTACT -> no overlay");

        BlockDamageAccumulator.Snapshot snap2 = hit(12.0);
        assertEquals(DamageState.BRUISED, snap2.damageState(), "hit 2: ratio=0.48 -> BRUISED");
        assertEquals(3, progress(snap2), "hit 2: BRUISED ratio=0.48 -> crack progress 3 (0.40..0.50 band)");

        BlockDamageAccumulator.Snapshot snap3 = hit(12.0);
        assertEquals(DamageState.CRACKED, snap3.damageState(), "hit 3: ratio=0.72 -> CRACKED");
        assertEquals(6, progress(snap3), "hit 3: CRACKED ratio=0.72 -> crack progress 6 (0.70..0.80 band)");

        // The per-hit lastEffectiveDamageJ is still 12J (unchanged),
        // but the accumulated total drives the transition to CRACKED.
        assertEquals(12.0, snap3.lastEffectiveDamageJ(), 0.001,
                "lastEffectiveDamageJ remains the per-hit value (12J)");
        assertEquals(36.0, snap3.accumulatedEffectiveDamageJ(), 0.001,
                "accumulatedEffectiveDamageJ is the running total (36J) that drives the state");
    }

    // -- test 2: single hit below threshold produces no crack ---------------------

    /**
     * One 5J hit on STONE (threshold=50J) yields ratio=0.10 -> INTACT.
     * No crack overlay should be emitted, regardless of raw impact magnitude.
     */
    @Test
    void single_hit_below_crack_threshold_produces_no_overlay() {
        BlockDamageAccumulator.Snapshot snap = hit(5.0);
        assertEquals(DamageState.INTACT, snap.damageState(), "ratio=0.10 -> INTACT");
        assertEquals(-1, progress(snap), "INTACT -> ratioToProgress returns -1");

        // Confirm the actual tryUpdate call path (mirrors TrueImpactMod) also returns -1.
        int result = CrackOverlayTracker.tryUpdate(
                snap.key(), snap.damageState(), snap.ratio(), 1L);
        assertEquals(-1, result, "tryUpdate must return -1 for INTACT (no packet sent)");
    }

    // -- test 3: same lastEffective, different accumulated -> crack follows accumulated

    /**
     * Two snapshots share the same lastEffectiveDamageJ (10J) but differ in
     * accumulatedEffectiveDamageJ (10J vs 45J).
     *
     * Snapshot A: accumulated=10J -> ratio=0.20 -> INTACT  -> no overlay (-1)
     * Snapshot B: accumulated=45J -> ratio=0.90 -> CRACKED -> progress 8  (0.90..1.00 band)
     *
     * Proves the formula uses the running accumulated ratio, not the last-hit value.
     */
    @Test
    void same_lastEffective_different_accumulated_crack_follows_accumulated_ratio() {
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                "minecraft:overworld", 10, 64, 10, "minecraft:stone");

        // Snapshot A: low accumulated, same lastEffectiveDamageJ as B.
        BlockDamageAccumulator.Snapshot snapA = new BlockDamageAccumulator.Snapshot(
                key, MaterialThresholdProfile.MaterialClass.STONE,
                10.0,            // accumulatedEffectiveDamageJ -> ratio 0.20
                STONE_THRESHOLD,
                10.0,            // lastRawImpactJ
                10.0,            // lastEffectiveDamageJ -- same as B
                1L, 1,
                DamageState.of(10.0 / STONE_THRESHOLD));

        // Snapshot B: high accumulated, same lastEffectiveDamageJ as A.
        BlockDamageAccumulator.Snapshot snapB = new BlockDamageAccumulator.Snapshot(
                key, MaterialThresholdProfile.MaterialClass.STONE,
                45.0,            // accumulatedEffectiveDamageJ -> ratio 0.90
                STONE_THRESHOLD,
                10.0,            // lastRawImpactJ
                10.0,            // lastEffectiveDamageJ -- same as A
                2L, 4,
                DamageState.of(45.0 / STONE_THRESHOLD));

        assertEquals(snapA.lastEffectiveDamageJ(), snapB.lastEffectiveDamageJ(), 0.001,
                "both snapshots have the same lastEffectiveDamageJ (10J)");

        // But damage state and crack progress differ because accumulated totals differ.
        assertEquals(DamageState.INTACT,  snapA.damageState(), "A: accumulated 10J -> INTACT");
        assertEquals(DamageState.CRACKED, snapB.damageState(), "B: accumulated 45J -> CRACKED");

        int progressA = CrackOverlayTracker.ratioToProgress(snapA.damageState(), snapA.ratio());
        int progressB = CrackOverlayTracker.ratioToProgress(snapB.damageState(), snapB.ratio());

        assertEquals(-1, progressA, "A: INTACT (accumulated=10J) -> no overlay");
        assertEquals(8,  progressB, "B: CRACKED (accumulated=45J, ratio=0.90) -> crack progress 8 (0.90..1.00 band)");
        assertNotEquals(progressA, progressB,
                "same lastEffectiveDamageJ but different accumulated -> different crack progress");
    }

    // -- test 4: CRITICAL ratio >= 1.0 always yields progress 9 ------------------

    /**
     * A 60J hit on STONE (threshold=50J) pushes the accumulated ratio to 1.0 -> CRITICAL.
     * Unified cap: effectiveJ = min(60, 50) = 50J → accumulated = 50J → ratio = 1.0.
     * Crack progress must be 9 (maximum pre-break stage).
     */
    @Test
    void critical_accumulated_ratio_produces_max_crack_progress_9() {
        BlockDamageAccumulator.Snapshot snap = hit(60.0);
        assertEquals(DamageState.CRITICAL, snap.damageState(), "60J on 50J threshold -> CRITICAL");
        assertEquals(1.0, snap.ratio(), 0.001, "effectiveJ = min(60,50) = 50J -> ratio = 50/50 = 1.0");
        assertEquals(9, progress(snap), "CRITICAL -> crack progress must be 9");

        // Confirm via tryUpdate (the actual TrueImpactMod call path).
        int result = CrackOverlayTracker.tryUpdate(
                snap.key(), snap.damageState(), snap.ratio(), 1L);
        assertEquals(9, result, "tryUpdate for CRITICAL must return 9 (max stage)");
    }
}
