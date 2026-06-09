package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.DamageResolver;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.physics.ImpactRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 1 of the Phase 1B damage pipeline.
 *
 * Reads raw double[N*15] from Rapier3D.clearCollisions(), groups contact records by
 * active-vs-active body pair, assembles ImpactRecord per pair, and passes each to
 * DamageResolver.
 *
 * Discard rule: any contact record where either body ID is absent from lastPostSnaps
 * (i.e., not a live active sub-level) is silently discarded. No ImpactRecord is created
 * for world-vs-active or unknown pairs.
 *
 * Phase 1C contract:
 *   - DamageResolver.resolve() is called for every assembled record.
 *   - Resolver still returns NONE. No block damage, no destroyBlock.
 *   - ImpactMetrics is diagnostic-only and never flows into DamageResolver.
 *   - The returned list is available for test and diagnostic inspection; production callers
 *     may ignore it.
 *
 * [arch] This class lives in sable/ and may depend on physics/, damage/, observation/.
 *        It must NOT import diagnostic/ or command/ packages.
 */
public final class SableImpactCapture {

    private SableImpactCapture() {}

    /**
     * Minimum totalImpulseJ per contact record to classify a pair as ACTIVE_IMPACT.
     * Below this -> ACTIVE_SUSTAINED (resting/sliding).
     * Mirrors ContactLogger.T3_IMPACT_IMPULSE_PER_CONTACT for consistent classification.
     */
    static final double IMPACT_IMPULSE_PER_CONTACT_THRESHOLD = 5.0;
    static final double PHASE_1C_PLACEHOLDER_MATERIAL_THRESHOLD_J = 50.0;

    private static long totalProcessCalls;
    private static long totalRawContactsSeen;
    private static long totalImpactRecordsCreated;
    private static long lastTick = -1L;
    private static int  lastRecordCount;
    private static int  lastActiveImpactCount;
    private static int  lastSustainedCount;

    // Last tick on which at least one ImpactRecord was created.
    // Not overwritten by zero-record ticks -- useful for manual in-game verification
    // where status is queried after the bodies have separated.
    private static long lastNonZeroRecordTick      = -1L;
    private static int  lastNonZeroRecordCount;
    private static int  lastNonZeroActiveImpactCount;
    private static int  lastNonZeroSustainedCount;
    private static ImpactMetrics lastImpactMetrics;

    public record RuntimeStats(
            long totalProcessCalls,
            long totalRawContactsSeen,
            long totalImpactRecordsCreated,
            long lastTick,
            int  lastRecordCount,
            int  lastActiveImpactCount,
            int  lastSustainedCount,
            long lastNonZeroRecordTick,
            int  lastNonZeroRecordCount,
            int  lastNonZeroActiveImpactCount,
            int  lastNonZeroSustainedCount,
            ImpactMetrics lastImpactMetrics
    ) {}

    public static synchronized RuntimeStats stats() {
        return new RuntimeStats(
                totalProcessCalls,
                totalRawContactsSeen,
                totalImpactRecordsCreated,
                lastTick,
                lastRecordCount,
                lastActiveImpactCount,
                lastSustainedCount,
                lastNonZeroRecordTick,
                lastNonZeroRecordCount,
                lastNonZeroActiveImpactCount,
                lastNonZeroSustainedCount,
                lastImpactMetrics);
    }

    public static synchronized void resetCounters() {
        totalProcessCalls        = 0L;
        totalRawContactsSeen     = 0L;
        totalImpactRecordsCreated = 0L;
        lastTick                 = -1L;
        lastRecordCount          = 0;
        lastActiveImpactCount    = 0;
        lastSustainedCount       = 0;
        lastNonZeroRecordTick         = -1L;
        lastNonZeroRecordCount        = 0;
        lastNonZeroActiveImpactCount  = 0;
        lastNonZeroSustainedCount     = 0;
        lastImpactMetrics             = null;
    }

    /**
     * Processes raw clearCollisions output for one physics tick.
     *
     * @param data          raw double[N*15] from Rapier3D.clearCollisions()
     * @param serverTick    level.getGameTime()
     * @param substepCount  configured substeps per tick; <=0 treated as 1
     * @param lastPostSnaps most-recent POST_STEP body snapshots keyed by runtimeId
     * @param tickStartVels linVel captured at substep-0 PRE_STEP keyed by runtimeId
     * @return list of assembled ImpactRecord (active-vs-active only); empty if none
     */
    public static List<ImpactRecord> process(
            double[] data,
            long serverTick,
            int substepCount,
            Map<Integer, BodySnapshot> lastPostSnaps,
            Map<Integer, double[]> tickStartVels) {

        int count = (data != null) ? data.length / 15 : 0;
        if (count <= 0) {
            recordStats(serverTick, count, 0, 0, 0);
            return List.of();
        }
        double substepDt = (substepCount > 0) ? 0.05 / substepCount : 0.05;

        // Aggregate contact records by active-vs-active pair.
        Map<Long, PairAccum> pairMap = new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int    idA      = (int) data[base];
            int    idB      = (int) data[base + 1];
            double forceRaw = data[base + 2];
            double nAx = data[base + 3], nAy = data[base + 4], nAz = data[base + 5];

            BodySnapshot snapA = lastPostSnaps.get(idA);
            BodySnapshot snapB = lastPostSnaps.get(idB);

            // Discard: world-vs-active, active-vs-world, or unknown-vs-unknown
            if (snapA == null || snapB == null) continue;

            long key = pairKey(idA, idB);
            PairAccum acc = pairMap.computeIfAbsent(key, k -> new PairAccum(idA, idB));

            acc.sumForce += forceRaw;
            acc.contactCount++;

            if (!acc.massValid) {
                acc.massA     = snapA.massKpg();
                acc.massB     = snapB.massKpg();
                acc.massValid = true;
            }

            // impulseAlongNormalJ reconstruction (T-6 UNCONFIRMED; abs applied).
            // Requires valid post-step velocity and tick-start velocity for both bodies.
            if (snapA.velocityReadValid() && snapB.velocityReadValid()) {
                double[] vbA = tickStartVels.get(idA);
                double[] vbB = tickStartVels.get(idB);
                if (vbA != null && vbB != null) {
                    double[] nW  = rotateVec(snapA, nAx, nAy, nAz);
                    double   nMg = Math.sqrt(nW[0]*nW[0] + nW[1]*nW[1] + nW[2]*nW[2]);
                    if (nMg > 1e-9) { nW[0] /= nMg; nW[1] /= nMg; nW[2] /= nMg; }
                    double dvAn = Math.abs(
                            (snapA.linVelX() - vbA[0]) * nW[0]
                          + (snapA.linVelY() - vbA[1]) * nW[1]
                          + (snapA.linVelZ() - vbA[2]) * nW[2]);
                    double dvBn = Math.abs(
                            (snapB.linVelX() - vbB[0]) * nW[0]
                          + (snapB.linVelY() - vbB[1]) * nW[1]
                          + (snapB.linVelZ() - vbB[2]) * nW[2]);
                    acc.sumImpulseNorm += (snapA.massKpg() * dvAn + snapB.massKpg() * dvBn) / 2.0;
                }
            }
        }

        if (pairMap.isEmpty()) {
            recordStats(serverTick, count, 0, 0, 0);
            return List.of();
        }

        List<ImpactRecord> result = new ArrayList<>(pairMap.size());
        int activeImpactCount = 0;
        int sustainedCount = 0;
        ImpactMetrics latestMetrics = null;
        for (PairAccum acc : pairMap.values()) {
            double totalImpulseJ   = acc.sumForce * substepDt;
            double impulsePerPair  = (acc.contactCount > 0)
                    ? totalImpulseJ / acc.contactCount : 0.0;
            ContactType type = (impulsePerPair > IMPACT_IMPULSE_PER_CONTACT_THRESHOLD)
                    ? ContactType.ACTIVE_IMPACT : ContactType.ACTIVE_SUSTAINED;
            if (type == ContactType.ACTIVE_IMPACT) {
                activeImpactCount++;
            } else {
                sustainedCount++;
            }

            double kA     = (acc.massA > 0) ? 1.0 / acc.massA : 0.0;
            double kB     = (acc.massB > 0) ? 1.0 / acc.massB : 0.0;
            double effMass = (acc.massValid && kA + kB > 0) ? 1.0 / (kA + kB) : Double.NaN;

            ImpactRecord record = new ImpactRecord(
                    serverTick,
                    pairKey(acc.idA, acc.idB),
                    acc.idA,
                    acc.idB,
                    acc.massA,
                    acc.massB,
                    effMass,
                    acc.contactCount,          // DIAGNOSTIC METADATA -- UNCONFIRMED as area proxy
                    totalImpulseJ,             // CANONICAL T-3
                    acc.sumImpulseNorm,        // T-6 UNCONFIRMED; abs applied
                    substepDt,
                    type
            );
            result.add(record);
            if (type == ContactType.ACTIVE_IMPACT) {
                latestMetrics = computeMetrics(record);
            }
            DamageResolver.resolve(record);    // Phase 1C: still always NONE
        }

        recordStats(serverTick, count, result.size(), activeImpactCount, sustainedCount, latestMetrics);
        return result;
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained) {
        recordStats(tick, rawContacts, records, activeImpacts, sustained, null);
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained,
                                                  ImpactMetrics latestMetrics) {
        totalProcessCalls++;
        totalRawContactsSeen     += rawContacts;
        totalImpactRecordsCreated += records;
        lastTick              = tick;
        lastRecordCount       = records;
        lastActiveImpactCount = activeImpacts;
        lastSustainedCount    = sustained;

        // lastNonZero* fields: only updated when at least one record was produced.
        // Zero-record ticks (bodies separated, no contact) must not overwrite them.
        if (records > 0) {
            lastNonZeroRecordTick         = tick;
            lastNonZeroRecordCount        = records;
            lastNonZeroActiveImpactCount  = activeImpacts;
            lastNonZeroSustainedCount     = sustained;
        }
        if (latestMetrics != null) {
            lastImpactMetrics = latestMetrics;
        }
    }

    static ImpactMetrics computeMetrics(ImpactRecord record) {
        double impactEnergyJ = Double.NaN;
        if (Double.isFinite(record.effectiveMassKpg()) && record.effectiveMassKpg() > 0.0) {
            impactEnergyJ = (record.totalImpulseJ() * record.totalImpulseJ())
                    / (2.0 * record.effectiveMassKpg());
        }

        double contactPressureProxy = (record.contactCount() > 0)
                ? record.totalImpulseJ() / record.contactCount()
                : Double.NaN;
        double candidateStressEstimate = impactEnergyJ;
        double threshold = PHASE_1C_PLACEHOLDER_MATERIAL_THRESHOLD_J;
        boolean exceeds = Double.isFinite(candidateStressEstimate)
                && candidateStressEstimate > threshold;

        return new ImpactMetrics(
                record.serverTick(),
                record.bodyPairKey(),
                impactEnergyJ,
                record.impulseAlongNormalJ(),
                contactPressureProxy,
                candidateStressEstimate,
                threshold,
                exceeds);
    }

    // Canonical pair key: min(idA,idB)<<32 | max(idA,idB) (matches ImpactRecord contract)
    static long pairKey(int idA, int idB) {
        int minId = Math.min(idA, idB);
        int maxId = Math.max(idA, idB);
        return ((long) minId << 32) | (maxId & 0xFFFFFFFFL);
    }

    /** Rotate body-COM-local vector to world space using the body's orientation quaternion. */
    private static double[] rotateVec(BodySnapshot s, double lx, double ly, double lz) {
        double qx = s.oriX(), qy = s.oriY(), qz = s.oriZ(), qw = s.oriW();
        double tx = 2 * (qy * lz - qz * ly);
        double ty = 2 * (qz * lx - qx * lz);
        double tz = 2 * (qx * ly - qy * lx);
        return new double[]{
            lx + qw * tx + qy * tz - qz * ty,
            ly + qw * ty + qz * tx - qx * tz,
            lz + qw * tz + qx * ty - qy * tx
        };
    }

    private static final class PairAccum {
        final int idA;
        final int idB;
        double sumForce;
        int    contactCount;
        double sumImpulseNorm;
        double massA;
        double massB;
        boolean massValid;

        PairAccum(int idA, int idB) {
            this.idA = idA;
            this.idB = idB;
        }
    }
}
