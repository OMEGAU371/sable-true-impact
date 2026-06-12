package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.BodyType;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.physics.ImpactRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SableImpactCaptureTest {

    // -- fixture helpers ----------------------------------------------------------

    @BeforeEach
    void resetCaptureCounters() {
        SableImpactCapture.resetCounters();
        SableVictimCapture.clearForTick();
        io.github.omegau371.trueimpact.damage.DeferredDamageQueue.clear();
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
     * SableImpactCapture.process() works correctly when called directly regardless of
     * DiagnosticConfig state or the captureGate flag, as long as the gate is active.
     *
     * In production, DiagnosticContactCaptureMixin sets captureGate = DiagnosticConfig.ENABLED
     * before each clearCollisions() call. In these unit tests, process() is called directly
     * (bypassing the mixin); the gate defaults to true after @BeforeEach resetCounters().
     *
     * This test verifies the internal pipeline logic, not the mixin gate behavior.
     * See capture_returns_empty_when_gate_inactive() for gate behavior.
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

    // -- T-8 rolling stats --------------------------------------------------------

    /** Helpers: bodies with post-step velocity so kineticAfterJ is populated. */
    private static Map<Integer, BodySnapshot> velSnaps(double vAx, double vBx) {
        return Map.of(
                10, snapWithVel(10, 4.0, vAx, 0.0, 0.0),
                20, snapWithVel(20, 8.0, vBx, 0.0, 0.0));
    }

    /** Helpers: tick-start vels so kineticBeforeJ is populated too. */
    private static Map<Integer, double[]> startVels(double svAx, double svBx) {
        Map<Integer, double[]> m = new HashMap<>();
        m.put(10, new double[]{svAx, 0.0, 0.0});
        m.put(20, new double[]{svBx, 0.0, 0.0});
        return m;
    }

    @Test
    void t8_stats_empty_when_kineticDelta_is_NaN() {
        // snap() has no valid velocity -> kineticDelta NaN -> no T-8 sample
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 200L, 2, snaps, Map.of());

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(0, t8.sampleCount(), "kineticDelta=NaN -> no sample");
        assertFalse(t8.hasSamples());
        assertTrue(Double.isNaN(t8.lastRatio()));
    }

    @Test
    void t8_stats_empty_for_ACTIVE_SUSTAINED() {
        // force=100 -> J=2.5/contact < 5.0 -> ACTIVE_SUSTAINED -> no T-8 sample
        Map<Integer, BodySnapshot> snaps = velSnaps(1.0, -1.0);
        SableImpactCapture.process(oneContact(10, 20, 100.0), 201L, 2, snaps,
                startVels(0.0, 0.0));

        assertEquals(0, SableImpactCapture.stats().t8Stats().sampleCount(),
                "ACTIVE_SUSTAINED must not update T-8 stats");
    }

    @Test
    void t8_stats_one_sample_populates_all_fields() {
        // All four velocity flags true -> kineticDelta is computable
        // relBefore = ||(0,0,0)-(0,0,0)|| = 0; relAfter = ||(3,0,0)-(-1,0,0)|| = 4
        // mEff = 1/(1/4+1/8) = 8/3
        // kBefore=0, kAfter=0.5*(8/3)*16=(64/3), kDelta=(64/3)
        // impactEnergyJ = 7.5^2/(2*(8/3)) = 56.25/(16/3) = 56.25*3/16 = 10.547
        // ratio = kDelta / impactEnergyJ
        Map<Integer, BodySnapshot> snaps = velSnaps(3.0, -1.0);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 202L, 2, snaps,
                startVels(0.0, 0.0));

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(1, t8.sampleCount());
        assertTrue(t8.hasSamples());
        assertFalse(Double.isNaN(t8.lastRatio()));
        assertEquals(t8.lastRatio(), t8.minRatio(),   1e-9, "min == last for single sample");
        assertEquals(t8.lastRatio(), t8.maxRatio(),   1e-9, "max == last for single sample");
        assertEquals(t8.lastRatio(), t8.averageRatio(),1e-9,"avg == last for single sample");
        assertEquals(t8.lastRatio(), t8.p50Ratio(),   1e-9, "p50 == last for single sample");

        // Verify the ratio matches manual computation
        double mEff       = 1.0 / (1.0 / 4.0 + 1.0 / 8.0);
        double J          = 300.0 * 0.025;
        double impE       = (J * J) / (2.0 * mEff);
        double relAfter   = 4.0;  // ||(3,0,0)-(-1,0,0)||
        double kAfter     = 0.5 * mEff * relAfter * relAfter;
        double kDelta     = Math.abs(0.0 - kAfter);
        double expectedRatio = kDelta / impE;
        assertEquals(expectedRatio, t8.lastRatio(), 1e-9);
    }

    @Test
    void t8_stats_accumulate_min_max_avg_across_multiple_samples() {
        // Two impacts with different velocities -> different ratios
        Map<Integer, BodySnapshot> snaps1 = velSnaps(2.0, -1.0);  // relAfter=3
        Map<Integer, BodySnapshot> snaps2 = velSnaps(5.0, -2.0);  // relAfter=7
        Map<Integer, double[]> sv = startVels(0.0, 0.0);

        SableImpactCapture.process(oneContact(10, 20, 300.0), 210L, 2, snaps1, sv);
        double ratio1 = SableImpactCapture.stats().t8Stats().lastRatio();

        SableImpactCapture.process(oneContact(10, 20, 300.0), 211L, 2, snaps2, sv);
        double ratio2 = SableImpactCapture.stats().t8Stats().lastRatio();

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(2, t8.sampleCount());
        assertEquals(ratio2, t8.lastRatio(), 1e-9, "last = most recent");
        assertEquals(Math.min(ratio1, ratio2), t8.minRatio(), 1e-9);
        assertEquals(Math.max(ratio1, ratio2), t8.maxRatio(), 1e-9);
        assertEquals((ratio1 + ratio2) / 2.0,  t8.averageRatio(), 1e-9);
        // p50 of 2 samples = average of the two
        assertEquals((ratio1 + ratio2) / 2.0,  t8.p50Ratio(), 1e-9);
    }

    @Test
    void t8_stats_p50_is_median_of_window() {
        // Insert 3 samples with known ratios: verify p50 is the middle value
        // We use different post-step velocities to produce different ratios
        // Ratios will be ratio for relAfter = 1, 3, 5 (with relBefore=0)
        double J    = 300.0 * 0.025;
        double mEff = 1.0 / (1.0 / 4.0 + 1.0 / 8.0);
        double impE = (J * J) / (2.0 * mEff);
        Map<Integer, double[]> sv = startVels(0.0, 0.0);

        // relAfter=1 (vA=1, vB=0): kDelta=0.5*mEff*1, ratio small
        SableImpactCapture.process(oneContact(10, 20, 300.0), 220L, 2,
                velSnaps(1.0, 0.0), sv);
        // relAfter=3 (vA=2, vB=-1): kDelta=0.5*mEff*9, ratio medium
        SableImpactCapture.process(oneContact(10, 20, 300.0), 221L, 2,
                velSnaps(2.0, -1.0), sv);
        // relAfter=5 (vA=3, vB=-2): kDelta=0.5*mEff*25, ratio large
        SableImpactCapture.process(oneContact(10, 20, 300.0), 222L, 2,
                velSnaps(3.0, -2.0), sv);

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(3, t8.sampleCount());

        // For odd count, p50 is the middle element after sorting
        double r1 = 0.5 * mEff * 1.0  / impE;  // relAfter=1
        double r2 = 0.5 * mEff * 9.0  / impE;  // relAfter=3
        double r3 = 0.5 * mEff * 25.0 / impE;  // relAfter=5
        double[] sorted = {r1, r2, r3};
        Arrays.sort(sorted);
        assertEquals(sorted[1], t8.p50Ratio(), 1e-9, "p50 is middle of 3 sorted samples");
    }

    @Test
    void t8_stats_window_capped_at_32() {
        // Insert 35 samples; sampleCount tracks all, window only holds last 32
        Map<Integer, BodySnapshot> snaps = velSnaps(3.0, -1.0);
        Map<Integer, double[]> sv = startVels(0.0, 0.0);
        for (int i = 0; i < 35; i++) {
            SableImpactCapture.process(oneContact(10, 20, 300.0), 300L + i, 2, snaps, sv);
        }
        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(35, t8.sampleCount(), "sampleCount tracks all samples");
        // All samples have the same ratio, so all stats are equal
        assertFalse(Double.isNaN(t8.p50Ratio()), "p50 computed from window regardless of total count");
    }

    @Test
    void t8_stats_invariant_min_le_avg_le_max() {
        // Fundamental invariant: for any non-empty sample set, min <= avg <= max.
        // Uses two different velocity pairs to produce two distinct ratios.
        Map<Integer, double[]> sv = startVels(0.0, 0.0);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 500L, 2, velSnaps(2.0, -1.0), sv);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 501L, 2, velSnaps(10.0, -5.0), sv);

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(2, t8.sampleCount());
        assertTrue(t8.minRatio() <= t8.averageRatio(),
                "invariant violated: min=" + t8.minRatio() + " avg=" + t8.averageRatio());
        assertTrue(t8.averageRatio() <= t8.maxRatio(),
                "invariant violated: avg=" + t8.averageRatio() + " max=" + t8.maxRatio());
    }

    @Test
    void t8_stats_invariant_min_le_p50_le_max() {
        // Fundamental invariant: for any non-empty sample set, min <= p50 <= max.
        Map<Integer, double[]> sv = startVels(0.0, 0.0);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 510L, 2, velSnaps(2.0, -1.0), sv);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 511L, 2, velSnaps(10.0, -5.0), sv);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 512L, 2, velSnaps(5.0, -2.0), sv);

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(3, t8.sampleCount());
        assertTrue(t8.minRatio() <= t8.p50Ratio(),
                "invariant violated: min=" + t8.minRatio() + " p50=" + t8.p50Ratio());
        assertTrue(t8.p50Ratio() <= t8.maxRatio(),
                "invariant violated: p50=" + t8.p50Ratio() + " max=" + t8.maxRatio());
    }

    @Test
    void t8_stats_lastRatio_finite_when_samples_exist() {
        Map<Integer, BodySnapshot> snaps = velSnaps(3.0, -1.0);
        Map<Integer, double[]> sv = startVels(0.0, 0.0);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 520L, 2, snaps, sv);

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertTrue(t8.hasSamples());
        assertTrue(Double.isFinite(t8.lastRatio()),
                "lastRatio must be finite when sampleCount > 0, got: " + t8.lastRatio());
        assertTrue(Double.isFinite(t8.minRatio()),  "minRatio must be finite");
        assertTrue(Double.isFinite(t8.maxRatio()),  "maxRatio must be finite");
        assertTrue(Double.isFinite(t8.averageRatio()), "averageRatio must be finite");
        assertTrue(Double.isFinite(t8.p50Ratio()),  "p50Ratio must be finite");
    }

    @Test
    void t8_stats_p50_from_last_32_not_first_8() {
        // Insert 8 large-ratio samples then 32 small-ratio samples (40 total).
        // After 40 samples the circular window (capacity 32) contains only the 32 small ones.
        // p50 must reflect the small group. max must still reflect the large group (tracked overall).
        double mEff       = 1.0 / (1.0 / 4.0 + 1.0 / 8.0);
        double J          = 300.0 * 0.025;
        double impE       = (J * J) / (2.0 * mEff);
        double ratioLarge = (0.5 * mEff * 100.0 * 100.0) / impE;  // relAfter=100
        double ratioSmall = (0.5 * mEff * 1.0 * 1.0)     / impE;  // relAfter=1

        Map<Integer, double[]> sv = startVels(0.0, 0.0);

        // 8 large-ratio samples: vA=50, vB=-50 -> relAfter=100
        for (int i = 0; i < 8; i++) {
            SableImpactCapture.process(oneContact(10, 20, 300.0), 600L + i, 2,
                    velSnaps(50.0, -50.0), sv);
        }
        // 32 small-ratio samples: vA=0.5, vB=-0.5 -> relAfter=1
        for (int i = 0; i < 32; i++) {
            SableImpactCapture.process(oneContact(10, 20, 300.0), 608L + i, 2,
                    velSnaps(0.5, -0.5), sv);
        }

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(40, t8.sampleCount(), "total samples = 8 + 32");

        // p50 from last 32 samples (all small): ratioSmall
        assertEquals(ratioSmall, t8.p50Ratio(), 1e-9,
                "p50 must come from the last 32 window samples (small group), not first 8 (large)");
        // max tracks ALL 40 samples: ratioLarge
        assertEquals(ratioLarge, t8.maxRatio(), 1e-9,
                "max must reflect all-time max across all 40 samples");
        // min tracks ALL 40 samples: ratioSmall (since ratioSmall < ratioLarge)
        assertEquals(ratioSmall, t8.minRatio(), 1e-9,
                "min must reflect all-time min across all 40 samples");

        // Invariants must hold even with mismatched window vs overall tracking
        assertTrue(t8.minRatio() <= t8.p50Ratio(),    "min <= p50");
        assertTrue(t8.p50Ratio() <= t8.maxRatio(),    "p50 <= max");
        assertTrue(t8.minRatio() <= t8.averageRatio(), "min <= avg");
        assertTrue(t8.averageRatio() <= t8.maxRatio(), "avg <= max");
    }

    @Test
    void t8_stats_reset_clears_all_fields() {
        Map<Integer, BodySnapshot> snaps = velSnaps(3.0, -1.0);
        Map<Integer, double[]> sv = startVels(0.0, 0.0);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 400L, 2, snaps, sv);
        assertTrue(SableImpactCapture.stats().t8Stats().hasSamples());

        SableImpactCapture.resetCounters();

        SableImpactCapture.T8Stats t8 = SableImpactCapture.stats().t8Stats();
        assertEquals(0, t8.sampleCount(), "reset must clear sampleCount");
        assertFalse(t8.hasSamples());
        assertTrue(Double.isNaN(t8.lastRatio()),    "reset must clear lastRatio");
        assertTrue(Double.isNaN(t8.minRatio()),     "reset must clear minRatio");
        assertTrue(Double.isNaN(t8.maxRatio()),     "reset must clear maxRatio");
        assertTrue(Double.isNaN(t8.averageRatio()), "reset must clear averageRatio");
        assertTrue(Double.isNaN(t8.p50Ratio()),     "reset must clear p50Ratio");
    }

    // -- Phase 1C diagnostic metrics ---------------------------------------------

    @Test
    void active_impact_sets_both_metrics_fields() {
        // force=300, substepDt=0.025 -> J=7.5/contact > 5.0 -> ACTIVE_IMPACT
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 70L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        ImpactMetrics rec    = s.lastRecordMetrics();
        ImpactMetrics impact = s.lastActiveImpactMetrics();

        assertNotNull(rec,    "ACTIVE_IMPACT must set lastRecordMetrics");
        assertNotNull(impact, "ACTIVE_IMPACT must set lastActiveImpactMetrics");

        double totalImpulseJ = 300.0 * (0.05 / 2);
        double effMass = 1.0 / (1.0 / 5.0 + 1.0 / 10.0);
        double expectedEnergy = (totalImpulseJ * totalImpulseJ) / (2.0 * effMass);

        // Both fields point to the same record
        assertEquals(70L, rec.serverTick());
        assertEquals(70L, impact.serverTick());
        assertEquals(ContactType.ACTIVE_IMPACT, rec.contactType());
        assertEquals(ContactType.ACTIVE_IMPACT, impact.contactType());

        // T-8 traceability fields
        assertEquals(totalImpulseJ, rec.totalImpulseJ(), 1e-9);
        assertEquals(effMass, rec.effectiveMassKpg(), 1e-9);
        assertEquals(5.0, rec.massAKpg(), 1e-9);
        assertEquals(10.0, rec.massBKpg(), 1e-9);

        // Solver diagnostic energy (impactEnergyJ stays but is no longer canonical)
        assertEquals(expectedEnergy, rec.impactEnergyJ(), 1e-9);
        // candidateStressEstimate is now kineticImpactEnergyJ (velocity-derived).
        // No velocity data in this test -> kineticImpactEnergyJ = NaN -> exceedsThreshold = false.
        assertTrue(Double.isNaN(rec.candidateStressEstimate()),
                "candidateStressEstimate is now velocity-derived; NaN when no velocity data");
        assertEquals(50.0, rec.materialThresholdJ(), 1e-9);
        assertFalse(rec.exceedsThreshold(),
                "exceedsThreshold = false when candidateStress = NaN (velocity unavailable)");

        // Secondary fields
        assertEquals(0.0, rec.normalImpulseJ(), 1e-9);
        assertEquals(totalImpulseJ, rec.contactPressureProxy(), 1e-9);

        // T-8 velocity availability: no tick-start vels, but post vels from snap() are invalid
        assertFalse(rec.hasStartVelA(),  "no tickStartVels -> hasStartVelA=false");
        assertFalse(rec.hasStartVelB(),  "no tickStartVels -> hasStartVelB=false");
        assertFalse(rec.hasPostVelA(),   "snap() has velocityReadValid=false");
        assertFalse(rec.hasPostVelB(),   "snap() has velocityReadValid=false");
        assertTrue(Double.isNaN(rec.relativeSpeedBeforeMagnitude()));
        assertTrue(Double.isNaN(rec.relativeSpeedAfterMagnitude()));
        assertTrue(Double.isNaN(rec.kineticBeforeJ()));
        assertTrue(Double.isNaN(rec.kineticAfterJ()));
        assertTrue(Double.isNaN(rec.kineticDeltaMagnitudeJ()));
    }

    @Test
    void sustained_contact_sets_lastRecordMetrics_only() {
        // force=100, substepDt=0.025 -> J=2.5/contact < 5.0 -> ACTIVE_SUSTAINED
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 100.0), 80L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        assertNotNull(s.lastRecordMetrics(),
                "ACTIVE_SUSTAINED must set lastRecordMetrics for threshold calibration");
        assertNull(s.lastActiveImpactMetrics(),
                "ACTIVE_SUSTAINED must NOT set lastActiveImpactMetrics");
        assertEquals(ContactType.ACTIVE_SUSTAINED, s.lastRecordMetrics().contactType());
        assertEquals(80L, s.lastRecordMetrics().serverTick());
    }

    @Test
    void sustained_updates_lastRecordMetrics_does_not_overwrite_lastActiveImpactMetrics() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        // tick 71: ACTIVE_IMPACT -> sets both fields
        SableImpactCapture.process(oneContact(10, 20, 300.0), 71L, 2, snaps, Map.of());
        ImpactMetrics impactAfterImpact = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(impactAfterImpact);
        assertEquals(71L, impactAfterImpact.serverTick());

        // tick 72: ACTIVE_SUSTAINED -> updates lastRecordMetrics but NOT lastActiveImpactMetrics
        SableImpactCapture.process(oneContact(10, 20, 100.0), 72L, 2, snaps, Map.of());

        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();

        // lastRecordMetrics: updated to the sustained tick
        assertEquals(72L, s.lastRecordMetrics().serverTick(),
                "ACTIVE_SUSTAINED must update lastRecordMetrics");
        assertEquals(ContactType.ACTIVE_SUSTAINED, s.lastRecordMetrics().contactType());

        // lastActiveImpactMetrics: must still reflect the impact tick (not overwritten)
        assertEquals(71L, s.lastActiveImpactMetrics().serverTick(),
                "ACTIVE_SUSTAINED must NOT overwrite lastActiveImpactMetrics");
        assertEquals(ContactType.ACTIVE_IMPACT, s.lastActiveImpactMetrics().contactType());
    }

    @Test
    void metrics_contactType_matches_record_classification() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        // Impact: J/contact = 7.5 > 5.0
        SableImpactCapture.process(oneContact(10, 20, 300.0), 90L, 2, snaps, Map.of());
        assertEquals(ContactType.ACTIVE_IMPACT,
                SableImpactCapture.stats().lastRecordMetrics().contactType());

        SableImpactCapture.resetCounters();

        // Sustained: J/contact = 2.5 < 5.0
        SableImpactCapture.process(oneContact(10, 20, 100.0), 91L, 2, snaps, Map.of());
        assertEquals(ContactType.ACTIVE_SUSTAINED,
                SableImpactCapture.stats().lastRecordMetrics().contactType());
    }

    // -- T-8 kinetic energy validation fields ------------------------------------

    @Test
    void t8_all_vel_flags_false_when_snaps_have_no_velocity() {
        // snap() has velocityReadValid=false; no tickStartVels -> all four flags false
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 100L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertFalse(m.hasStartVelA(), "no tickStartVels -> hasStartVelA=false");
        assertFalse(m.hasStartVelB(), "no tickStartVels -> hasStartVelB=false");
        assertFalse(m.hasPostVelA(),  "snap() velocityReadValid=false");
        assertFalse(m.hasPostVelB(),  "snap() velocityReadValid=false");
        assertTrue(Double.isNaN(m.relativeSpeedBeforeMagnitude()), "NaN when start vels missing");
        assertTrue(Double.isNaN(m.relativeSpeedAfterMagnitude()),  "NaN when post vels missing");
        assertTrue(Double.isNaN(m.kineticBeforeJ()),   "NaN when start vels missing");
        assertTrue(Double.isNaN(m.kineticAfterJ()),    "NaN when post vels missing");
        assertTrue(Double.isNaN(m.kineticDeltaMagnitudeJ()), "NaN when either kinetic unavail");
    }

    @Test
    void t8_post_vel_available_from_snapWithVel() {
        // snapWithVel has velocityReadValid=true; no tickStartVels -> only post flags true
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 5.0,  3.0, 0.0, 0.0),
                20, snapWithVel(20, 10.0, -1.0, 0.0, 0.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 100L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertFalse(m.hasStartVelA(), "no tickStartVels");
        assertFalse(m.hasStartVelB(), "no tickStartVels");
        assertTrue(m.hasPostVelA(),   "snapWithVel velocityReadValid=true");
        assertTrue(m.hasPostVelB(),   "snapWithVel velocityReadValid=true");

        // relBefore NaN; relAfter computable
        assertTrue(Double.isNaN(m.relativeSpeedBeforeMagnitude()), "start vels absent");
        assertEquals(4.0, m.relativeSpeedAfterMagnitude(), 1e-9,
                "||vA - vB|| = ||(3,0,0) - (-1,0,0)|| = 4.0");
        assertTrue(Double.isNaN(m.kineticBeforeJ()), "start vels absent");
        assertFalse(Double.isNaN(m.kineticAfterJ()), "post vels present");
        assertTrue(Double.isNaN(m.kineticDeltaMagnitudeJ()), "NaN: kBefore unavail");
    }

    @Test
    void t8_full_kinetic_computation_when_all_vels_present() {
        // Both tick-start and post vels present -> all T-8 fields populated
        // Body A: start=(0,0,0), post=(2,0,0)
        // Body B: start=(0,0,0), post=(-1,0,0)
        // relBefore = ||(0,0,0) - (0,0,0)|| = 0.0
        // relAfter  = ||(2,0,0) - (-1,0,0)|| = 3.0
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0, 2.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0, -1.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = new HashMap<>();
        startVels.put(10, new double[]{0.0, 0.0, 0.0});
        startVels.put(20, new double[]{0.0, 0.0, 0.0});

        SableImpactCapture.process(oneContact(10, 20, 300.0), 101L, 2, snaps, startVels);

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(m.hasStartVelA()); assertTrue(m.hasStartVelB());
        assertTrue(m.hasPostVelA());  assertTrue(m.hasPostVelB());

        double mEff = 1.0 / (1.0 / 4.0 + 1.0 / 8.0);
        assertEquals(0.0, m.relativeSpeedBeforeMagnitude(), 1e-9, "relBefore");
        assertEquals(3.0, m.relativeSpeedAfterMagnitude(),  1e-9, "relAfter");
        assertEquals(3.0, m.deltaRelativeSpeedMagnitude(),  1e-9, "|relAfter - relBefore|");
        assertEquals(0.0,             m.kineticBeforeJ(), 1e-9, "0.5*mEff*0^2");
        assertEquals(0.5*mEff*9.0,    m.kineticAfterJ(),  1e-9, "0.5*mEff*3^2");
        assertEquals(0.5*mEff*9.0,    m.kineticDeltaMagnitudeJ(), 1e-9, "|kAfter - kBefore|");
    }

    @Test
    void t8_formula_trace_fields_match_impact_record() {
        // Verify totalImpulseJ, effectiveMassKpg, massA, massB copied faithfully from ImpactRecord.
        // forceRaw=300: J = 300*0.025 = 7.5 > threshold 5.0 -> ACTIVE_IMPACT
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 4.0),
                20, snap(20, 6.0));
        double forceRaw     = 300.0;
        double substepDt    = 0.05 / 2;
        double expectedJ    = forceRaw * substepDt;
        double expectedMeff = 1.0 / (1.0 / 4.0 + 1.0 / 6.0);
        double expectedE    = (expectedJ * expectedJ) / (2.0 * expectedMeff);

        SableImpactCapture.process(oneContact(10, 20, forceRaw), 102L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m, "forceRaw=300 -> J/contact=7.5 > 5.0 -> ACTIVE_IMPACT");
        assertEquals(expectedJ,    m.totalImpulseJ(),    1e-9, "totalImpulseJ");
        assertEquals(expectedMeff, m.effectiveMassKpg(), 1e-9, "effectiveMassKpg");
        assertEquals(4.0,          m.massAKpg(),         1e-9, "massA");
        assertEquals(6.0,          m.massBKpg(),         1e-9, "massB");
        assertEquals(expectedE,    m.impactEnergyJ(),    1e-9, "E=J^2/(2mEff)");
    }

    // -- unit audit fields (rawSumForce, substepDtUsed, contactCount) -----------------

    @Test
    void unit_audit_rawSumForce_is_totalImpulseJ_divided_by_substepDt() {
        // rawSumForce = sum of forceAmountRaw values = totalImpulseJ / substepDt
        // substepCount=2 -> substepDt=0.025; forceRaw=300 -> sumForce=300 -> J=7.5
        // rawSumForce = 7.5 / 0.025 = 300.0
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 700L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        double expectedJ    = 300.0 * (0.05 / 2);   // 7.5
        double expectedSubDt = 0.05 / 2;             // 0.025
        double expectedRaw  = expectedJ / expectedSubDt; // 300.0
        assertEquals(expectedSubDt, m.substepDtUsed(), 1e-9, "substepDtUsed");
        assertEquals(expectedRaw,   m.rawSumForce(),   1e-9, "rawSumForce = J/substepDt");
        assertEquals(expectedJ,     m.totalImpulseJ(), 1e-9, "totalImpulseJ round-trip");
    }

    @Test
    void unit_audit_contactCount_matches_aggregated_entry_count() {
        // 3 contact entries for the same pair -> contactCount=3 in ImpactMetrics
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        double[] data = concat(
                oneContact(10, 20, 300.0),
                oneContact(10, 20, 300.0),
                oneContact(10, 20, 300.0));
        SableImpactCapture.process(data, 701L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastRecordMetrics();
        assertNotNull(m);
        assertEquals(3, m.contactCount(), "contactCount must equal raw entry count for pair");
        // rawSumForce = 3 contacts * 300 force = 900; J = 900 * 0.025 = 22.5
        double expectedRaw = 900.0;
        double expectedJ   = 900.0 * (0.05 / 2);
        assertEquals(expectedRaw, m.rawSumForce(),   1e-9, "rawSumForce = sumForce (3*300)");
        assertEquals(expectedJ,   m.totalImpulseJ(), 1e-9, "totalImpulseJ = sumForce*substepDt");
    }

    @Test
    void unit_audit_substepDt_consistent_with_substepCount() {
        // substepCount=4 -> substepDt=0.05/4=0.0125; force=600 -> J=7.5/contact > 5.0 -> IMPACT
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 600.0), 702L, 4, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m, "force=600, substepCount=4 -> J/contact=7.5 > 5.0 -> ACTIVE_IMPACT");
        double expectedSubDt = 0.05 / 4;
        assertEquals(expectedSubDt, m.substepDtUsed(), 1e-9, "substepDtUsed for substepCount=4");
        double expectedJ   = 600.0 * expectedSubDt;
        double expectedRaw = 600.0;
        assertEquals(expectedJ,   m.totalImpulseJ(), 1e-9);
        assertEquals(expectedRaw, m.rawSumForce(),   1e-9, "rawSumForce = 600.0 (single force entry)");
    }

    @Test
    void unit_audit_fields_propagated_for_sustained_as_well() {
        // ACTIVE_SUSTAINED records also get rawSumForce/substepDtUsed/contactCount in lastRecordMetrics
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 100.0), 703L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastRecordMetrics();
        assertNotNull(m, "ACTIVE_SUSTAINED still sets lastRecordMetrics");
        assertEquals(io.github.omegau371.trueimpact.physics.ContactType.ACTIVE_SUSTAINED,
                m.contactType());
        assertEquals(1, m.contactCount());
        assertEquals(0.025, m.substepDtUsed(), 1e-9);
        double expectedRaw = 100.0;
        assertEquals(expectedRaw, m.rawSumForce(), 1e-9, "rawSumForce for ACTIVE_SUSTAINED");
    }

    @Test
    void high_energy_metric_sets_exceedsThreshold_but_resolver_still_NONE() {
        // candidateStressEstimate = kineticImpactEnergyJ (velocity-derived).
        // Use snapWithVel + startVels to provide velocity data so kImpact can be computed.
        // Body A: start=(6,0,0), after=(0,0,0); Body B: start=(0,0,0), after=(0,0,0)
        // relBefore = 6.0; mEff=1/(1/5+1/10)=10/3; kBefore=0.5*(10/3)*36=60J > threshold 50J
        // relAfter = 0.0; kAfter = 0; kDelta = 60J -> exceedsThreshold = true
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 5.0,  0.0, 0.0, 0.0),
                20, snapWithVel(20, 10.0, 0.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = new java.util.HashMap<>();
        startVels.put(10, new double[]{6.0, 0.0, 0.0});
        startVels.put(20, new double[]{0.0, 0.0, 0.0});

        List<ImpactRecord> records = SableImpactCapture.process(
                oneContact(10, 20, 300.0), 73L, 2, snaps, startVels);

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(Double.isFinite(m.kineticImpactEnergyJ()),
                "kineticImpactEnergyJ must be finite when velocity data is available");
        assertTrue(m.kineticImpactEnergyJ() > 50.0,
                "kImpact > threshold 50J (kBefore=60J, kAfter=0, kDelta=60J)");
        assertTrue(m.exceedsThreshold(),
                "Phase 1C threshold comparison uses velocity-derived energy; should exceed 50J");
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
        assertNull(s.lastRecordMetrics(),       "resetCounters must clear lastRecordMetrics");
        assertNull(s.lastActiveImpactMetrics(), "resetCounters must clear lastActiveImpactMetrics");
        assertNull(s.lastVictimInfo(),          "resetCounters must clear lastVictimInfo");
        assertTrue(s.captureActive(),
                "resetCounters must restore captureGate to true (active) for test isolation");
        // verify T-8 fields don't linger after reset
        assertNull(s.lastActiveImpactMetrics()); // already checked above, but explicit
    }

    // -- Part B: capture gate (performance) ----------------------------------------

    @Test
    void capture_returns_empty_and_no_counters_when_gate_inactive() {
        // Gate inactive: process() must be a no-op -- no counter increments, no records.
        // In production, DiagnosticContactCaptureMixin sets gate=false when ENABLED=false.
        // Tests simulate this by calling setCaptureGate(false) directly.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));

        SableImpactCapture.setCaptureGate(false);
        List<io.github.omegau371.trueimpact.physics.ImpactRecord> result =
                SableImpactCapture.process(oneContact(10, 20, 300.0), 900L, 2, snaps, Map.of());

        assertTrue(result.isEmpty(), "gate inactive -> empty list");
        SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
        assertEquals(0L, s.totalProcessCalls(),      "gate inactive -> no call counted");
        assertEquals(0L, s.totalRawContactsSeen(),   "gate inactive -> no contacts counted");
        assertEquals(0L, s.totalImpactRecordsCreated(), "gate inactive -> no records counted");
        assertFalse(s.captureActive(),               "captureActive reflects gate state");
    }

    @Test
    void capture_resumes_after_gate_reactivated() {
        SableImpactCapture.setCaptureGate(false);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 901L, 2,
                Map.of(10, snap(10, 5.0), 20, snap(20, 8.0)), Map.of());
        assertEquals(0L, SableImpactCapture.stats().totalProcessCalls(),
                "still 0 while gate is inactive");

        SableImpactCapture.setCaptureGate(true);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 902L, 2,
                Map.of(10, snap(10, 5.0), 20, snap(20, 8.0)), Map.of());
        assertEquals(1L, SableImpactCapture.stats().totalProcessCalls(),
                "gate reactivated -> call is counted");
        assertTrue(SableImpactCapture.stats().captureActive());
    }

    @Test
    void resetCounters_restores_gate_to_active() {
        SableImpactCapture.setCaptureGate(false);
        assertFalse(SableImpactCapture.isCaptureActive(), "gate is inactive before reset");

        SableImpactCapture.resetCounters();

        assertTrue(SableImpactCapture.isCaptureActive(),
                "resetCounters() must restore gate to true for test isolation");
        assertTrue(SableImpactCapture.stats().captureActive());
    }

    @Test
    void gate_inactive_does_not_affect_subsequent_active_session() {
        // Simulate: all-off → all-on → impact occurs → counters correct
        SableImpactCapture.setCaptureGate(false);
        for (int i = 0; i < 5; i++) {
            SableImpactCapture.process(oneContact(10, 20, 300.0), 910L + i, 2,
                    Map.of(10, snap(10, 5.0), 20, snap(20, 8.0)), Map.of());
        }
        assertEquals(0L, SableImpactCapture.stats().totalProcessCalls(),
                "5 calls while inactive -> still 0");

        SableImpactCapture.setCaptureGate(true);
        SableImpactCapture.process(oneContact(10, 20, 300.0), 915L, 2,
                Map.of(10, snap(10, 5.0), 20, snap(20, 8.0)), Map.of());

        assertEquals(1L, SableImpactCapture.stats().totalProcessCalls(),
                "exactly 1 call after reactivation");
        assertEquals(1L, SableImpactCapture.stats().totalImpactRecordsCreated());
    }

    // -- Phase 1D victim detection -------------------------------------------------

    @Test
    void active_vs_active_contact_sets_lastVictimInfo_to_ACTIVE_SUBLEVEL() {
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 950L, 2, snaps, Map.of());

        io.github.omegau371.trueimpact.damage.VictimInfo vi =
                SableImpactCapture.stats().lastVictimInfo();
        assertNotNull(vi, "active-vs-active must set lastVictimInfo");
        assertEquals(io.github.omegau371.trueimpact.damage.VictimInfo.Kind.ACTIVE_SUBLEVEL,
                vi.kind(), "active-vs-active -> ACTIVE_SUBLEVEL, not UNKNOWN");
        assertEquals(io.github.omegau371.trueimpact.damage.VictimInfo.Confidence.EXACT,
                vi.confidence(), "active-vs-active kind is definitively known");
    }

    @Test
    void world_vs_active_contact_without_capture_sets_NO_CALLBACK() {
        // One body (id=99) is NOT in lastPostSnaps -> world-vs-active contact.
        // No SableVictimCapture data -> worldContactNoCallback() with source=NO_CALLBACK.
        // (Contact-point sampling runs only in the mixin which isn't available in unit tests.)
        Map<Integer, BodySnapshot> snaps = Map.of(10, snap(10, 5.0));
        SableImpactCapture.process(oneContact(10, 99, 300.0), 951L, 2, snaps, Map.of());

        io.github.omegau371.trueimpact.damage.VictimInfo vi =
                SableImpactCapture.stats().lastVictimInfo();
        assertNotNull(vi, "world-vs-active contact must update lastVictimInfo");
        assertEquals(io.github.omegau371.trueimpact.damage.VictimInfo.Kind.UNKNOWN,
                vi.kind(),
                "world contact without capture -> UNKNOWN kind");
        assertEquals(io.github.omegau371.trueimpact.damage.VictimInfo.Source.NO_CALLBACK,
                vi.source(),
                "world contact without capture -> source=NO_CALLBACK (not generic UNKNOWN)");
    }

    @Test
    void world_vs_active_with_callback_data_sets_WORLD_BLOCK() {
        // Simulate callback capture before process() is called.
        SableVictimCapture.captureCallbackBlock("minecraft:stone", 5, 64, 5, true);

        Map<Integer, BodySnapshot> snaps = Map.of(10, snap(10, 5.0));
        SableImpactCapture.process(oneContact(10, 99, 300.0), 952L, 2, snaps, Map.of());

        io.github.omegau371.trueimpact.damage.VictimInfo vi =
                SableImpactCapture.stats().lastVictimInfo();
        assertNotNull(vi);
        assertEquals(io.github.omegau371.trueimpact.damage.VictimInfo.Kind.WORLD_BLOCK, vi.kind());
        assertEquals("minecraft:stone", vi.blockId());
        assertEquals(io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass.STONE,
                vi.materialClass());
        assertEquals(50.0, vi.materialThresholdJ(), 0.001);
    }

    @Test
    void both_unknown_bodies_do_not_update_lastVictimInfo() {
        // Both bodies absent from snaps -> should be completely discarded, not update victim
        Map<Integer, BodySnapshot> snaps = Map.of();
        SableImpactCapture.process(oneContact(88, 99, 300.0), 953L, 2, snaps, Map.of());

        io.github.omegau371.trueimpact.damage.VictimInfo vi =
                SableImpactCapture.stats().lastVictimInfo();
        assertNull(vi, "both-unknown contact must not update lastVictimInfo");
    }

    // -- Phase 1E deferred damage queue enqueue conditions ----------------------------

    @Test
    void active_sublevel_contact_does_not_enqueue() {
        // active-vs-active only -> ACTIVE_SUBLEVEL kind -> must not enqueue
        Map<Integer, BodySnapshot> snaps = Map.of(10, snap(10, 5.0), 20, snap(20, 8.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 960L, 2, snaps, Map.of());

        assertEquals(0L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "active-vs-active contact must not enqueue");
    }

    @Test
    void world_contact_below_threshold_does_not_enqueue() {
        // World contact: active body (id=10) vs world (id=99).
        // dirt threshold = 5.0; single-body kImpact = 0.5*5*|0-0| = 0 < 5 -> no enqueue
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        Map<Integer, BodySnapshot> snaps = Map.of(10, snapWithVel(10, 5.0, 0.1, 0.0, 0.0));
        // start vel also near-zero -> delta near-zero kImpact
        Map<Integer, double[]> startVels = Map.of(10, new double[]{0.08, 0.0, 0.0});
        SableImpactCapture.process(oneContact(10, 99, 50.0), 961L, 2, snaps, startVels);

        assertEquals(0L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "kImpact below threshold must not enqueue");
    }

    @Test
    void world_contact_above_threshold_enqueues() {
        // Active body: mass=5.0, vBefore=(10,0,0), vAfter=(2,0,0)
        // kImpact = 0.5 * 5.0 * |100 - 4| = 240 > dirt threshold 5.0 -> enqueue
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        Map<Integer, BodySnapshot> snaps = Map.of(10, snapWithVel(10, 5.0, 2.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = Map.of(10, new double[]{10.0, 0.0, 0.0});
        SableImpactCapture.process(oneContact(10, 99, 100.0), 962L, 2, snaps, startVels);

        assertEquals(1L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "WORLD_BLOCK with kImpact > threshold must enqueue");
    }

    @Test
    void world_contact_nan_kImpact_does_not_enqueue() {
        // No start vels -> worldKImpact = NaN -> no enqueue
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        Map<Integer, BodySnapshot> snaps = Map.of(10, snapWithVel(10, 5.0, 2.0, 0.0, 0.0));
        SableImpactCapture.process(oneContact(10, 99, 100.0), 963L, 2, snaps, Map.of());

        assertEquals(0L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "NaN kImpact (no start vels) must not enqueue");
    }

    @Test
    void world_contact_dedup_same_tick_pos_block() {
        // Two process() calls same tick, same pos/block -> dedup -> only 1 enqueue
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        Map<Integer, BodySnapshot> snaps = Map.of(10, snapWithVel(10, 5.0, 2.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = Map.of(10, new double[]{10.0, 0.0, 0.0});
        SableImpactCapture.process(oneContact(10, 99, 100.0), 964L, 2, snaps, startVels);
        SableImpactCapture.process(oneContact(10, 99, 100.0), 964L, 2, snaps, startVels);

        assertEquals(1L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "same tick/pos/block must be deduped");
    }

    @Test
    void world_contact_different_ticks_both_enqueue() {
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        Map<Integer, BodySnapshot> snaps = Map.of(10, snapWithVel(10, 5.0, 2.0, 0.0, 0.0));
        Map<Integer, double[]> startVels = Map.of(10, new double[]{10.0, 0.0, 0.0});
        SableImpactCapture.process(oneContact(10, 99, 100.0), 965L, 2, snaps, startVels);
        SableVictimCapture.captureContactPointBlock("minecraft:dirt", 5, 63, 5);
        SableImpactCapture.process(oneContact(10, 99, 100.0), 966L, 2, snaps, startVels);

        assertEquals(2L,
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.stats().totalEnqueued(),
                "different ticks same pos/block must both enqueue");
    }

    // -- Phase 1C canonical velocity-derived fields ------------------------------------

    @Test
    void canonical_kineticImpactEnergyJ_equals_kineticDeltaMagnitude_when_vels_present() {
        // kineticImpactEnergyJ = abs(kBefore-kAfter) = kineticDeltaMagnitudeJ (same value).
        // Both are non-NaN when all four velocities are present.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0,  3.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0, -1.0, 0.0, 0.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 800L, 2, snaps, startVels(0.0, 0.0));

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(Double.isFinite(m.kineticImpactEnergyJ()),
                "kineticImpactEnergyJ must be finite when velocity data present");
        assertEquals(m.kineticDeltaMagnitudeJ(), m.kineticImpactEnergyJ(), 1e-9,
                "kineticImpactEnergyJ == kineticDeltaMagnitudeJ (both = abs(kBefore-kAfter))");
    }

    @Test
    void canonical_kineticImpactEnergyJ_nan_when_vels_missing() {
        // snap() has no valid velocity -> kineticImpactEnergyJ = NaN
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 801L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(Double.isNaN(m.kineticImpactEnergyJ()),
                "kineticImpactEnergyJ = NaN when velocity data unavailable");
        assertFalse(m.exceedsThreshold(),
                "exceedsThreshold = false when canonical energy is NaN (no spurious damage)");
        assertTrue(Double.isNaN(m.candidateStressEstimate()),
                "candidateStressEstimate = kineticImpactEnergyJ = NaN");
    }

    @Test
    void canonical_velocityDerivedImpulseJ_computed_from_3D_relative_velocity_change() {
        // Body A: start=(10,0,0), after=(-5,0,0) -> dvA = (-15,0,0)
        // Body B: start=(-10,0,0), after=(5,0,0) -> dvB = (15,0,0)
        // deltaVRel_3D = dvA - dvB = (-30,0,0), ||deltaVRel|| = 30
        // mEff = 1/(1/4+1/8) = 8/3
        // velocityDerivedImpulseJ = (8/3) * 30 = 80.0
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0, -5.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0,  5.0, 0.0, 0.0));
        Map<Integer, double[]> sv = new java.util.HashMap<>();
        sv.put(10, new double[]{ 10.0, 0.0, 0.0});
        sv.put(20, new double[]{-10.0, 0.0, 0.0});

        SableImpactCapture.process(oneContact(10, 20, 300.0), 802L, 2, snaps, sv);

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(m.hasStartVelA()); assertTrue(m.hasStartVelB());
        assertTrue(m.hasPostVelA());  assertTrue(m.hasPostVelB());

        double mEff     = 1.0 / (1.0 / 4.0 + 1.0 / 8.0);
        double expected = mEff * 30.0;  // ||dvA - dvB|| = 30
        assertEquals(expected, m.velocityDerivedImpulseJ(), 1e-9,
                "velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||");
    }

    @Test
    void canonical_velocityDerivedImpulseJ_nan_when_start_vels_missing() {
        // post vels only (no tick-start vels) -> velocityDerivedImpulseJ = NaN
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0, -5.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0,  5.0, 0.0, 0.0));

        SableImpactCapture.process(oneContact(10, 20, 300.0), 803L, 2, snaps, Map.of());

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertFalse(m.hasStartVelA()); assertFalse(m.hasStartVelB());
        assertTrue(Double.isNaN(m.velocityDerivedImpulseJ()),
                "velocityDerivedImpulseJ = NaN when start vels missing (requires all 4)");
    }

    @Test
    void canonical_candidateStress_is_kineticImpactEnergy_not_forceAmount_energy() {
        // With velocity data: candidateStress = kineticImpactEnergyJ (not impactEnergyJ).
        // The two values are very different: impactEnergyJ >> kineticImpactEnergyJ.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 4.0, 0.0, 0.0, 0.0),
                20, snapWithVel(20, 8.0, 0.0, 0.0, 0.0));
        SableImpactCapture.process(oneContact(10, 20, 300.0), 804L, 2, snaps, startVels(5.0, -5.0));

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        // impactEnergyJ (solver diagnostic) is NOT candidateStressEstimate
        assertNotEquals(m.impactEnergyJ(), m.candidateStressEstimate(),
                "impactEnergyJ (solver diagnostic) must NOT equal candidateStressEstimate");
        // candidateStressEstimate == kineticImpactEnergyJ (velocity-derived canonical)
        assertEquals(m.kineticImpactEnergyJ(), m.candidateStressEstimate(), 1e-9,
                "candidateStressEstimate == kineticImpactEnergyJ");
    }

    @Test
    void canonical_resolver_still_NONE_regardless_of_canonical_energy() {
        // Ensure DamageResolver.NONE regardless of whether threshold is exceeded.
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snapWithVel(10, 5.0,  0.0, 0.0, 0.0),
                20, snapWithVel(20, 10.0, 0.0, 0.0, 0.0));
        Map<Integer, double[]> sv = new java.util.HashMap<>();
        sv.put(10, new double[]{10.0, 0.0, 0.0});
        sv.put(20, new double[]{0.0,  0.0, 0.0});

        List<ImpactRecord> records = SableImpactCapture.process(
                oneContact(10, 20, 300.0), 805L, 2, snaps, sv);

        ImpactMetrics m = SableImpactCapture.stats().lastActiveImpactMetrics();
        assertNotNull(m);
        assertTrue(m.exceedsThreshold(), "threshold exceeded with high kImpact; prereq for this test");
        assertEquals(io.github.omegau371.trueimpact.damage.DamageResolver.DamageEvent.NONE,
                io.github.omegau371.trueimpact.damage.DamageResolver.resolve(records.get(0)),
                "DamageResolver must always return NONE in Phase 1C regardless of canonical energy");
    }
}
