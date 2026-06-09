package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.BodyType;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SableImpactCaptureTest {

    // ── fixture helpers ────────────────────────────────────────────────────────

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

    // ── discard rules ─────────────────────────────────────────────────────────

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

    // ── active-vs-active aggregation ──────────────────────────────────────────

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

    // ── totalImpulseJ calculation ─────────────────────────────────────────────

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

    // ── contactType classification ─────────────────────────────────────────────

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

    // ── effectiveMassKpg ──────────────────────────────────────────────────────

    @Test
    void effectiveMass_computed_correctly() {
        // mA=5, mB=10 -> effM = 1/(1/5 + 1/10) = 1/(0.2+0.1) = 1/0.3 ~= 3.333
        Map<Integer, BodySnapshot> snaps = Map.of(
                10, snap(10, 5.0),
                20, snap(20, 10.0));
        List<ImpactRecord> result = SableImpactCapture.process(
                oneContact(10, 20, 100.0), 1L, 2, snaps, Map.of());

        double expected = 1.0 / (1.0 / 5.0 + 1.0 / 10.0);
        assertEquals(expected, result.get(0).effectiveMassKpg(), 1e-9);
    }

    // ── ImpactRecord field plumbing ────────────────────────────────────────────

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
        // idA and idB are stored; pair may be canonicalized so check both directions
        assertTrue((r.idA() == 10 && r.idB() == 20) || (r.idA() == 20 && r.idB() == 10));
    }

    @Test
    void body_pair_key_is_symmetric() {
        assertEquals(
                SableImpactCapture.pairKey(10, 20),
                SableImpactCapture.pairKey(20, 10));
    }

    // ── mixed data: active+world in same batch ────────────────────────────────

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

    // ── impulseAlongNormalJ (T-6 UNCONFIRMED) ────────────────────────────────

    @Test
    void impulseAlongNormalJ_nonzero_when_tick_start_vels_available() {
        // Body A: tick-start vel (0,0,0), post-step vel (1,0,0)  -> dvAn along (1,0,0) = 1.0
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
}
