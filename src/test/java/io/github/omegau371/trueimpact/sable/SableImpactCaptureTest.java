package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.BodyType;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.physics.ImpactRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SableImpactCaptureTest {

    // -- fixture helpers ----------------------------------------------------------

    @BeforeEach
    void resetCaptureCounters() {
        SableImpactCapture.resetCounters();
    }

    /** Minimal active snapshot: identity orientation, no valid velocity. */
    private static BodySnapshot snap(int id, double mass) {
        return new BodySnapshot(
                1L, 0, SnapshotPhase.POST_STEP, BodyType.ACTIVE_SUBLEVEL, id,
                mass,
                false, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0,  // identity quaternion
                0.0, 0.0, 0.0,
                false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /** Snapshot with valid post-step linear velocity (for impulseAlongNormalJ test). */
    private static BodySnapshot snapWithVel(int id, double mass,
                                             double vx, double vy, double vz) {
        return new BodySnapshot(
                1L, 0, SnapshotPhase.POST_STEP, BodyType.ACTIVE_SUBLEVEL, id,
                mass,
                false, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 1.0,  // identity quaternion
                0.0, 0.0, 0.0,
                true, vx, vy, vz, 0.0, 0.0, 0.0);
    }

    /** Build a single raw contact record (15 doubles). */
    private static double[] oneContact(int idA, int idB, double force) {
        return new double[]{
                idA, idB, force,
                1.0, 0.0, 0.0,   // normalA
                -1.0, 0.0, 0.0,  // normalB
                0.0, 0.0, 0.0,   // pointA
                0.0, 0.0, 0.0    // pointB
        };
    }

    /** Concatenate multiple 15-element contact records into one double[]. */
    private static double[] concat(double[]... recs) {
        int total = 0;
        for (double[] r : recs) total += r.length;
        double[] out = new double[total];
        int pos = 0;
        for (double[] r : recs) {
            System.arraycopy(r, 0, out, pos, r.length);
            pos += r.length;
        }
        return out;
    }

    // -- discard rules ------------------------------------------------------------

    @Test
    void world_vs_active_pair_produces_no_record() {
        // Body 10 is active; body 99 is not in the snapshot map (world)
        Map<Integer, BodySnapshot> snaps = Map.of(10, snap(10, 5.0));
        double[] data = oneContact(10, 99, 100.0);

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertTrue(result.isEmpty(), "world-vs-active must not produce an ImpactRecord");
    }

    @Test
    void active_vs_world_pair_produces_no_record() {
        // Body 99 not in snaps (world); body 20 is active
        Map<Integer, BodySnapshot> snaps = Map.of(20, snap(20, 8.0));
        double[] data = oneContact(99, 20, 50.0);

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertTrue(result.isEmpty(), "active-vs-world must not produce an ImpactRecord");
    }

    @Test
    void unknown_pair_produces_no_record() {
        // Neither body is in the snapshot map
        double[] data = oneContact(7, 8, 200.0);
        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, Map.of(), Map.of());
        assertTrue(result.isEmpty(), "unknown pair must not produce an ImpactRecord");
    }

    @Test
    void empty_data_produces_no_record() {
        List<ImpactRecord> result = SableImpactCapture.process(
                new double[0], 1L, 2, Map.of(), Map.of());
        assertTrue(result.isEmpty());
    }

    // -- active-vs-active aggregation ---------------------------------------------

    @Test
    void active_vs_active_single_contact_produces_one_record() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = oneContact(10, 20, 100.0);

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(1, result.size());
    }

    @Test
    void two_separate_pairs_produce_two_records() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0),
                30, snap(30, 8.0),
                40, snap(40, 6.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(30, 40, 200.0));

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(2, result.size());
    }

    @Test
    void multiple_contacts_same_pair_aggregate_into_one_record() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(10, 20, 150.0),
                oneContact(10, 20,  50.0));

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(1, result.size(), "three contacts for the same pair -> one ImpactRecord");
        assertEquals(3, result.get(0).contactCount(), "contactCount must equal raw record count");
    }

    @Test
    void pair_key_is_order_independent() {
        // Records with idA/idB swapped must map to the same pair
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(20, 10, 200.0));   // reversed

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(1, result.size(), "reversed-id contacts must merge into the same pair");
        assertEquals(2, result.get(0).contactCount());
    }

    // -- totalImpulseJ calculation ------------------------------------------------

    @Test
    void totalImpulseJ_equals_sumForce_times_substepDt() {
        // substepCount=2 -> substepDt = 0.05/2 = 0.025
        // forceRaw = 100.0 -> totalImpulseJ = 100.0 * 0.025 = 2.5
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = oneContact(10, 20, 100.0);

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(1, result.size());
        assertEquals(100.0 * (0.05 / 2), result.get(0).totalImpulseJ(), 1e-9);
    }

    @Test
    void totalImpulseJ_sums_force_across_multiple_contacts_for_same_pair() {
        // 3 contacts: 100 + 150 + 50 = 300; substepDt=0.025 -> J = 7.5
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(10, 20, 150.0),
                oneContact(10, 20,  50.0));

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(300.0 * (0.05 / 2), result.get(0).totalImpulseJ(), 1e-9);
    }

    @Test
    void substepDt_stored_correctly() {
        // substepCount=4 -> substepDt = 0.05/4 = 0.0125
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 1.0), 1L, 4, snaps, Map.of());

        assertEquals(0.05 / 4, result.get(0).substepDt(), 1e-9);
    }

    // -- contactType classification -----------------------------------------------

    @Test
    void contactType_is_ACTIVE_IMPACT_when_impulse_per_contact_above_threshold() {
        // threshold = 5.0; substepDt=0.025; force=300 -> J=7.5; J/1=7.5 > 5.0 -> IMPACT
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 300.0), 1L, 2, snaps, Map.of());

        assertEquals(ContactType.ACTIVE_IMPACT, result.get(0).contactType());
    }

    @Test
    void contactType_is_ACTIVE_SUSTAINED_when_impulse_per_contact_below_threshold() {
        // threshold = 5.0; substepDt=0.025; force=100 -> J=2.5; J/1=2.5 < 5.0 -> SUSTAINED
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, Map.of());

        assertEquals(ContactType.ACTIVE_SUSTAINED, result.get(0).contactType());
    }

    @Test
    void contactType_uses_per_contact_impulse_not_total() {
        // 3 contacts, each force=100. sumForce=300, J=7.5, J/contactCount=7.5/3=2.5 < 5.0
        // -> SUSTAINED even though totalImpulseJ > threshold
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(10, 20, 100.0),
                oneContact(10, 20, 100.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(ContactType.ACTIVE_SUSTAINED, result.get(0).contactType(),
                "classification must use impulse/contact, not total impulse");
    }

    // -- effectiveMassKpg ---------------------------------------------------------

    @Test
    void effectiveMass_computed_correctly() {
        // mA=5, mB=10 -> effM = 1/(1/5 + 1/10) = 1/0.3 ~= 3.333
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, Map.of());

        double expected = 1.0 / (1.0 / 5.0 + 1.0 / 10.0);
        assertEquals(expected, result.get(0).effectiveMassKpg(), 1e-9);
    }

    // -- ImpactRecord field plumbing ----------------------------------------------

    @Test
    void serverTick_is_propagated() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 42L, 2, snaps, Map.of());
        assertEquals(42L, result.get(0).serverTick());
    }

    @Test
    void body_ids_are_set() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, Map.of());

        ImpactRecord r = result.get(0);
        assertTrue((r.idA() == 10 && r.idB() == 20) || (r.idA() == 20 && r.idB() == 10));
    }

    @Test
    void body_pair_key_is_symmetric() {
        assertEquals(
                SableImpactCapture.pairKey(10, 20),
                SableImpactCapture.pairKey(20, 10));
    }

    // -- mixed data: active+world in same batch -----------------------------------

    @Test
    void mixed_batch_only_active_active_pairs_produce_records() {
        // Contact 1: active 10 vs active 20 -> keep
        // Contact 2: active 10 vs world 99  -> discard
        // Contact 3: world 77 vs world 88   -> discard
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(10, 99, 200.0),
                oneContact(77, 88, 300.0));

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 1L, 2, snaps, Map.of());

        assertEquals(1, result.size(), "only the active-vs-active contact produces a record");
        assertEquals(100.0 * 0.025, result.get(0).totalImpulseJ(), 1e-9);
    }

    // -- impulseAlongNormalJ (T-6 UNCONFIRMED) ------------------------------------

    @Test
    void impulseAlongNormalJ_nonzero_when_tick_start_vels_available() {
        // Body A: tick-start vel (0,0,0), post-step vel (1,0,0)  -> dvAn = 1.0
        // Body B: tick-start vel (0,0,0), post-step vel (-2,0,0) -> dvBn = 2.0
        // normal in record = (1,0,0); identity quaternion -> world normal = (1,0,0)
        // sumImpulseNorm = (mA*dvAn + mB*dvBn)/2 = (4.0*1.0 + 8.0*2.0)/2 = 10.0
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0,  1.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0, -2.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = new HashMap<>();
        startVels.put(10, new double[]{0.0, 0.0, 0.0});
        startVels.put(20, new double[]{0.0, 0.0, 0.0});

        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, startVels);

        assertEquals(1, result.size());
        double expected = (4.0 * 1.0 + 8.0 * 2.0) / 2.0;
        assertEquals(expected, result.get(0).impulseAlongNormalJ(), 1e-9);
    }

    @Test
    void impulseAlongNormalJ_zero_when_tick_start_vels_absent() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 5.0, 1.0, 0.0, 0.0),
                20, snapWithVel(20, 5.0, -1.0, 0.0, 0.0));
        // No tick-start vels provided
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, Map.of());

        assertEquals(0.0, result.get(0).impulseAlongNormalJ(), 1e-9);
    }

    // -- stale-id / removed-body protection ---------------------------------------

    /**
     * Regression guard: after a body is removed, lastPostSnaps must not retain its
     * stale entry. SableImpactCapture must not generate ImpactRecords for expired ids.
     *
     * SableEventBridge.onPostStep() now calls lastPostSnaps.clear() before every
     * substep iteration, so only currently-active bodies appear in the map.
     *
     * This test simulates the "tick N+1" state: id=20 was active in tick N but has
     * since been removed. lastPostSnaps now contains only id=10. A contact record
     * that still references id=20 must be discarded.
     */
    @Test
    void removed_body_not_in_snaps_produces_no_record() {
        // tick N: both id=10 and id=20 were active.
        // tick N+1: id=20 removed -> onPostStep cleared map, only id=10 repopulated.
        Map<Integer, BodySnapshot> snapsAfterRemoval = Map.of(10, snap(10, 5.0));

        // Contact still references old id=20 (e.g. Rapier contact buffer not yet flushed).
        double[] data = oneContact(10, 20, 300.0);

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 2L, 2, snapsAfterRemoval, Map.of());

        assertTrue(result.isEmpty(),
                "contact referencing a removed body (id=20 not in snaps) must produce no ImpactRecord");
    }

    @Test
    void only_currently_active_pair_produces_record_after_partial_removal() {
        // Three bodies: id=10 active, id=20 removed, id=30 active.
        // Contacts: (10 vs 20) and (10 vs 30).
        // Only (10 vs 30) should produce a record.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                30, snap(30, 8.0));
        double[] data = concat(
                oneContact(10, 20, 100.0),   // id=20 stale -> discard
                oneContact(10, 30, 200.0));  // both active -> keep

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 2L, 2, snaps, Map.of());

        assertEquals(1, result.size(),
                "only the contact between currently-active bodies produces a record");
        assertEquals(200.0 * (0.05 / 2), result.get(0).totalImpulseJ(), 1e-9);
    }

    @Test
    void empty_snaps_produces_no_records_even_with_contacts() {
        // Simulates container==null case: onPostStep cleared snaps and returned early,
        // leaving an empty map. No contacts should produce records.
        double[] data = concat(
                oneContact(10, 20, 100.0),
                oneContact(30, 40, 200.0));

        List<ImpactRecord> result = SableImpactCapture.process(
                data, 2L, 2, Map.of(), Map.of());

        assertTrue(result.isEmpty(),
                "empty lastPostSnaps (no active bodies) must produce no records");
    }

    // -- debug-off scenario -------------------------------------------------------

    /**
     * SableImpactCapture.process() must produce ImpactRecords whenever lastPostSnaps
     * is populated with active sub-levels, regardless of any DiagnosticConfig state.
     *
     * This test represents the "all diagnostics off" production path:
     *   - DiagnosticConfig.ENABLED = false (default)
     *   - DiagnosticConfig.LOG_RAW_CONTACTS = false (default)
     *   - DiagnosticConfig.LOG_BODY_SNAPSHOTS = false (default)
     *
     * SableEventBridge now populates lastPostSnaps unconditionally, so snaps will be
     * present here. SableImpactCapture must not gate on any diagnostic flag.
     */
    @Test
    void produces_records_with_all_diagnostics_off() {
        // Verify defaults: all diagnostic flags must be off at test start.
        // If they were on, this would not be a "debug-off" test.
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.ENABLED,
                "DiagnosticConfig.ENABLED must default to false");
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.LOG_RAW_CONTACTS,
                "LOG_RAW_CONTACTS must default to false");
        assertFalse(io.github.omegau371.trueimpact.observation.DiagnosticConfig.LOG_BODY_SNAPSHOTS,
                "LOG_BODY_SNAPSHOTS must default to false");

        // Simulate what SableEventBridge.onPostStep() now provides unconditionally:
        // populated lastPostSnaps with two active sub-levels.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        // process() must work and produce a record -- no diagnostic gate should block it.
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 300.0), 99L, 2, snaps, Map.of());

        assertEquals(1, result.size(),
                "active-vs-active pair must produce ImpactRecord even when all diagnostics are off");
        assertEquals(ContactType.ACTIVE_IMPACT, result.get(0).contactType());
        assertEquals(300.0 * (0.05 / 2), result.get(0).totalImpulseJ(), 1e-9);
    }

    // -- pipeline gate independence -----------------------------------------------

    @Test
    void process_method_does_not_reference_DiagnosticConfig() {
        // SableImpactCapture must not check any diagnostic flag internally.
        // The gate must live in the caller (DiagnosticContactCaptureMixin), not here.
        // This test verifies the class has no dependency on DiagnosticConfig at the
        // bytecode level by checking the declared class references.
        boolean refsConfig = false;
        for (var field : SableImpactCapture.class.getDeclaredFields()) {
            if (field.getType().getName().contains("DiagnosticConfig")) {
                refsConfig = true;
                break;
            }
        }
        for (var method : SableImpactCapture.class.getDeclaredMethods()) {
            for (var param : method.getParameterTypes()) {
                if (param.getName().contains("DiagnosticConfig")) refsConfig = true;
            }
        }
        assertFalse(refsConfig,
                "SableImpactCapture must not reference DiagnosticConfig -- "
                + "diagnostic gating belongs in the mixin caller, not the capture layer");
    }

    // -- runtime counters ---------------------------------------------------------

    @Test
    void stats_increment_process_calls_even_when_no_contacts() {
        SableImpactCapture.process(new double[0], 11L, 2, Map.of(), Map.of());

        SableImpactCapture.RuntimeStats stats = SableImpactCapture.stats();
        assertEquals(1L, stats.totalProcessCalls());
        assertEquals(0L, stats.totalRawContactsSeen());
        assertEquals(0L, stats.totalImpactRecordsCreated());
        assertEquals(11L, stats.lastTick());
        assertEquals(0, stats.lastRecordCount());
    }

    @Test
    void stats_count_raw_contacts_and_created_records() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        double[] data = concat(
                oneContact(10, 20, 300.0),
                oneContact(10, 20, 300.0));

        SableImpactCapture.process(data, 12L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats stats = SableImpactCapture.stats();
        assertEquals(1L, stats.totalProcessCalls());
        assertEquals(2L, stats.totalRawContactsSeen());
        assertEquals(1L, stats.totalImpactRecordsCreated());
        assertEquals(1, stats.lastRecordCount());
        assertEquals(1, stats.lastActiveImpactCount());
        assertEquals(0, stats.lastSustainedCount());
    }

    @Test
    void stats_count_raw_contacts_even_when_world_pairs_are_discarded() {
        Map<Integer, BodySnapshot> snaps = Map.of(10, snap(10, 5.0));
        double[] data = concat(
                oneContact(10, 99, 300.0),
                oneContact(77, 88, 300.0));

        SableImpactCapture.process(data, 13L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats stats = SableImpactCapture.stats();
        assertEquals(1L, stats.totalProcessCalls());
        assertEquals(2L, stats.totalRawContactsSeen());
        assertEquals(0L, stats.totalImpactRecordsCreated());
        assertEquals(0, stats.lastRecordCount());
        assertEquals(0, stats.lastActiveImpactCount());
        assertEquals(0, stats.lastSustainedCount());
    }

    @Test
    void stats_accumulate_across_process_calls_and_track_last_call() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        SableImpactCapture.process(oneContact(10, 20, 300.0), 21L, 2, snaps, Map.of());
        SableImpactCapture.process(oneContact(10, 99, 100.0), 22L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats stats = SableImpactCapture.stats();
        assertEquals(2L, stats.totalProcessCalls());
        assertEquals(2L, stats.totalRawContactsSeen());
        assertEquals(1L, stats.totalImpactRecordsCreated());
        assertEquals(22L, stats.lastTick());
        assertEquals(0, stats.lastRecordCount());
    }

    // -- lastNonZero* counters ----------------------------------------------------

    @Test
    void lastNonZero_fields_updated_when_records_produced() {
        // substepDt=0.025, force=300 -> J=7.5/contact > 5.0 -> ACTIVE_IMPACT
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 42L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        assertEquals(42L, s.lastNonZeroRecordTick(),
                "lastNonZeroRecordTick must be set to the tick that produced records");
        assertEquals(1, s.lastNonZeroRecordCount());
        assertEquals(1, s.lastNonZeroActiveImpactCount());
        assertEquals(0, s.lastNonZeroSustainedCount());
    }

    @Test
    void lastNonZero_fields_not_overwritten_by_zero_record_tick() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        // tick 50: impact -> sets lastNonZero* to tick=50, records=1
        SableImpactCapture.process(oneContact(10, 20, 300.0), 50L, 2, snaps, Map.of());

        // tick 51: no active-vs-active contact -> records=0
        SableImpactCapture.process(new double[0], 51L, 2, Map.of(), Map.of());

        // tick 52: world-only contact -> records=0
        SableImpactCapture.process(oneContact(10, 99, 100.0), 52L, 2,
                Map.of(10, snap(10, 5.0)), Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();

        // lastTick must reflect the most recent call (tick 52)
        assertEquals(52L, s.lastTick());
        assertEquals(0, s.lastRecordCount());

        // lastNonZero* must still reflect tick 50 -- not overwritten by zero-record ticks
        assertEquals(50L, s.lastNonZeroRecordTick(),
                "zero-record ticks must not overwrite lastNonZeroRecordTick");
        assertEquals(1, s.lastNonZeroRecordCount(),
                "zero-record ticks must not overwrite lastNonZeroRecordCount");
        assertEquals(1, s.lastNonZeroActiveImpactCount());
        assertEquals(0, s.lastNonZeroSustainedCount());
    }

    @Test
    void lastNonZero_fields_updated_to_latest_nonzero_tick() {
        // Two separate impact ticks; lastNonZero* should reflect the second one.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 60L, 2, snaps, Map.of());

        // A zero-record tick in between
        SableImpactCapture.process(new double[0], 61L, 2, Map.of(), Map.of());

        // Second impact tick (now SUSTAINED: force=100 -> J=2.5/contact < 5.0)
        SableImpactCapture.process(oneContact(10, 20, 100.0), 62L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        assertEquals(62L, s.lastNonZeroRecordTick(),
                "lastNonZeroRecordTick must advance to the second non-zero tick");
        assertEquals(1, s.lastNonZeroRecordCount());
        assertEquals(0, s.lastNonZeroActiveImpactCount(),
                "second impact was SUSTAINED (low impulse), not ACTIVE_IMPACT");
        assertEquals(1, s.lastNonZeroSustainedCount());
    }

    // -- Phase 1C diagnostic metrics ---------------------------------------------

    @Test
    void active_impact_computes_lastImpactMetrics() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));

        SableImpactCapture.process(oneContact(10, 20, 300.0), 70L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastImpactMetrics();
        assertNotNull(m, "ACTIVE_IMPACT must compute diagnostic ImpactMetrics");
        double totalImpulseJ = 300.0 * (0.05 / 2);
        double effMass = 1.0 / (1.0 / 5.0 + 1.0 / 10.0);
        double expectedEnergy = (totalImpulseJ * totalImpulseJ) / (2.0 * effMass);

        assertEquals(70L, m.serverTick());
        assertEquals(expectedEnergy, m.impactEnergyJ(), 1e-9);
        assertEquals(0.0, m.normalImpulseJ(), 1e-9);
        assertEquals(totalImpulseJ, m.contactPressureProxy(), 1e-9);
        assertEquals(expectedEnergy, m.candidateStressEstimate(), 1e-9);
        assertEquals(50.0, m.materialThresholdJ(), 1e-9);
        assertFalse(m.exceedsThreshold());
    }

    @Test
    void sustained_record_does_not_overwrite_lastImpactMetrics() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        SableImpactCapture.process(oneContact(10, 20, 300.0), 71L, 2, snaps, Map.of());
        ImpactMetrics first = SableImpactCapture.stats().lastImpactMetrics();
        assertNotNull(first);

        SableImpactCapture.process(oneContact(10, 20, 100.0), 72L, 2, snaps, Map.of());

        ImpactMetrics afterSustained = SableImpactCapture.stats().lastImpactMetrics();
        assertNotNull(afterSustained);
        assertEquals(first.serverTick(), afterSustained.serverTick(),
                "ACTIVE_SUSTAINED must not overwrite last ACTIVE_IMPACT metrics");
        assertEquals(first.impactEnergyJ(), afterSustained.impactEnergyJ(), 1e-9);
    }

    @Test
    void high_energy_metric_sets_exceedsThreshold_but_resolver_still_NONE() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));

        List<ImpactRecord> records = SableImpactCapture.process(
                oneContact(10, 20, 1000.0), 73L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastImpactMetrics();
        assertNotNull(m);
        assertTrue(m.exceedsThreshold(),
                "Phase 1C threshold comparison is diagnostic-only but should be visible");
        assertEquals(io.github.omegau371.trueimpact.damage.DamageResolver.DamageEvent.NONE,
                io.github.omegau371.trueimpact.damage.DamageResolver.resolve(records.get(0)),
                "Phase 1C metrics must not enable damage yet");
    }

    @Test
    void resetCounters_clears_runtime_stats() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 31L, 2, snaps, Map.of());

        SableImpactCapture.resetCounters();

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        assertEquals(0L,  s.totalProcessCalls());
        assertEquals(0L,  s.totalRawContactsSeen());
        assertEquals(0L,  s.totalImpactRecordsCreated());
        assertEquals(-1L, s.lastTick());
        assertEquals(0,   s.lastRecordCount());
        assertEquals(0,   s.lastActiveImpactCount());
        assertEquals(0,   s.lastSustainedCount());
        assertEquals(-1L, s.lastNonZeroRecordTick(),
                "resetCounters must set lastNonZeroRecordTick to -1");
        assertEquals(0, s.lastNonZeroRecordCount());
        assertEquals(0, s.lastNonZeroActiveImpactCount());
        assertEquals(0, s.lastNonZeroSustainedCount());
        assertNull(s.lastImpactMetrics());
    }
}
