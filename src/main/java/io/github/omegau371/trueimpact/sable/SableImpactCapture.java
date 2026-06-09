package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.DamageResolver;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.physics.ImpactRecord;

import java.util.ArrayList;
import java.util.Arrays;
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

    // Most recent ImpactMetrics for any active-vs-active record (ACTIVE_IMPACT or ACTIVE_SUSTAINED).
    // Updated whenever any ImpactRecord is produced.
    private static ImpactMetrics lastRecordMetrics;

    // Most recent ImpactMetrics for an ACTIVE_IMPACT record only.
    // Not overwritten by ACTIVE_SUSTAINED ticks.
    // null until the first ACTIVE_IMPACT record is produced since last reset.
    private static ImpactMetrics lastActiveImpactMetrics;

    // -- T-8 rolling stats -------------------------------------------------------
    // Updated only when: ACTIVE_IMPACT AND impactEnergyJ finite > 0 AND kineticDelta finite.
    private static final int T8_WINDOW = 32;

    private static int    t8SampleCount;
    private static double t8LastRatio   = Double.NaN;
    private static double t8MinRatio    = Double.NaN;
    private static double t8MaxRatio    = Double.NaN;
    private static double t8SumRatio;
    private static final double[] t8RatioWindow = new double[T8_WINDOW];
    private static int    t8WindowHead;  // next write index (mod T8_WINDOW)
    private static int    t8WindowSize;  // valid entries: 0..T8_WINDOW

    /** Snapshot of T-8 rolling calibration statistics. */
    public record T8Stats(
            int    sampleCount,
            double lastRatio,
            double minRatio,
            double maxRatio,
            double averageRatio,
            double p50Ratio
    ) {
        /** True when at least one valid T-8 sample exists. */
        public boolean hasSamples() { return sampleCount > 0; }
    }

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
            ImpactMetrics lastRecordMetrics,         // any ContactType, most recent record
            ImpactMetrics lastActiveImpactMetrics,   // ACTIVE_IMPACT only; null if none since reset
            T8Stats       t8Stats                    // rolling calibration stats
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
                lastRecordMetrics,
                lastActiveImpactMetrics,
                snapshotT8Stats());
    }

    public static synchronized void resetCounters() {
        totalProcessCalls         = 0L;
        totalRawContactsSeen      = 0L;
        totalImpactRecordsCreated = 0L;
        lastTick                  = -1L;
        lastRecordCount           = 0;
        lastActiveImpactCount     = 0;
        lastSustainedCount        = 0;
        lastNonZeroRecordTick        = -1L;
        lastNonZeroRecordCount       = 0;
        lastNonZeroActiveImpactCount = 0;
        lastNonZeroSustainedCount    = 0;
        lastRecordMetrics        = null;
        lastActiveImpactMetrics  = null;
        t8SampleCount  = 0;
        t8LastRatio    = Double.NaN;
        t8MinRatio     = Double.NaN;
        t8MaxRatio     = Double.NaN;
        t8SumRatio     = 0.0;
        t8WindowHead   = 0;
        t8WindowSize   = 0;
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
        ImpactMetrics latestRecordMetrics      = null;  // any type -- last record this tick
        ImpactMetrics latestActiveImpactMetrics = null; // ACTIVE_IMPACT only -- last impact this tick
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

            // Compute metrics for ALL records -- threshold calibration needs visibility
            // into ACTIVE_SUSTAINED as well as ACTIVE_IMPACT during Phase 1C.
            ImpactMetrics metrics = computeMetrics(record, lastPostSnaps, tickStartVels);
            latestRecordMetrics = metrics;
            if (type == ContactType.ACTIVE_IMPACT) {
                latestActiveImpactMetrics = metrics;
                // T-8 rolling stats: only when both energy values are valid and positive.
                double impulseE = metrics.impactEnergyJ();
                double kDelta   = metrics.kineticDeltaMagnitudeJ();
                if (Double.isFinite(impulseE) && impulseE > 0.0 && Double.isFinite(kDelta)) {
                    updateT8Stats(kDelta / impulseE);
                }
            }

            DamageResolver.resolve(record);    // Phase 1C: still always NONE
        }

        recordStats(serverTick, count, result.size(), activeImpactCount, sustainedCount,
                latestRecordMetrics, latestActiveImpactMetrics);
        return result;
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained) {
        recordStats(tick, rawContacts, records, activeImpacts, sustained, null, null);
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained,
                                                  ImpactMetrics latestRecordMetrics,
                                                  ImpactMetrics latestActiveImpactMetrics) {
        totalProcessCalls++;
        totalRawContactsSeen      += rawContacts;
        totalImpactRecordsCreated += records;
        lastTick              = tick;
        lastRecordCount       = records;
        lastActiveImpactCount = activeImpacts;
        lastSustainedCount    = sustained;

        // lastNonZero* fields: only updated when at least one record was produced.
        if (records > 0) {
            lastNonZeroRecordTick        = tick;
            lastNonZeroRecordCount       = records;
            lastNonZeroActiveImpactCount = activeImpacts;
            lastNonZeroSustainedCount    = sustained;
        }

        // lastRecordMetrics: updated for any active-vs-active record (any ContactType).
        if (latestRecordMetrics != null) {
            lastRecordMetrics = latestRecordMetrics;
        }
        // lastActiveImpactMetrics: updated only for ACTIVE_IMPACT records.
        // ACTIVE_SUSTAINED ticks do NOT overwrite this -- it stays at the last impact.
        if (latestActiveImpactMetrics != null) {
            lastActiveImpactMetrics = latestActiveImpactMetrics;
        }
    }

    private static synchronized void updateT8Stats(double ratio) {
        t8SampleCount++;
        t8LastRatio = ratio;
        t8SumRatio += ratio;
        if (Double.isNaN(t8MinRatio) || ratio < t8MinRatio) t8MinRatio = ratio;
        if (Double.isNaN(t8MaxRatio) || ratio > t8MaxRatio) t8MaxRatio = ratio;
        t8RatioWindow[t8WindowHead % T8_WINDOW] = ratio;
        t8WindowHead++;
        t8WindowSize = Math.min(t8WindowSize + 1, T8_WINDOW);
    }

    private static synchronized T8Stats snapshotT8Stats() {
        if (t8SampleCount == 0) {
            return new T8Stats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
        double avg = t8SumRatio / t8SampleCount;
        // p50 from circular window (last up-to-T8_WINDOW samples)
        double[] copy = Arrays.copyOf(t8RatioWindow, t8WindowSize);
        Arrays.sort(copy);
        int mid = t8WindowSize / 2;
        double p50 = (t8WindowSize % 2 == 0)
                ? (copy[mid - 1] + copy[mid]) / 2.0
                : copy[mid];
        return new T8Stats(t8SampleCount, t8LastRatio, t8MinRatio, t8MaxRatio, avg, p50);
    }

    /**
     * Computes ImpactMetrics from an ImpactRecord plus live body velocity data.
     *
     * @param record        assembled ImpactRecord for this pair/tick
     * @param lastPostSnaps last POST_STEP snapshots (always populated; used for hasPostVel flags)
     * @param tickStartVels substep-0 PRE_STEP velocities (populated only when LOG_RAW_CONTACTS on)
     */
    static ImpactMetrics computeMetrics(
            ImpactRecord record,
            Map<Integer, BodySnapshot> lastPostSnaps,
            Map<Integer, double[]> tickStartVels) {

        double mEff      = record.effectiveMassKpg();
        boolean mEffValid = Double.isFinite(mEff) && mEff > 0.0;

        // Canonical energy formula: J^2/(2*mEff) [T-3 confirmed]
        double impactEnergyJ = mEffValid
                ? (record.totalImpulseJ() * record.totalImpulseJ()) / (2.0 * mEff)
                : Double.NaN;

        double contactPressureProxy = (record.contactCount() > 0)
                ? record.totalImpulseJ() / record.contactCount()
                : Double.NaN;
        double candidateStress = impactEnergyJ;
        double threshold       = PHASE_1C_PLACEHOLDER_MATERIAL_THRESHOLD_J;
        boolean exceeds        = Double.isFinite(candidateStress) && candidateStress > threshold;

        // T-8: explicit velocity availability -- tracked independently for each body/direction.
        int idA = record.idA(), idB = record.idB();

        double[] startVelA = tickStartVels.get(idA);
        double[] startVelB = tickStartVels.get(idB);
        boolean hasStartVelA = startVelA != null;
        boolean hasStartVelB = startVelB != null;

        BodySnapshot snapA = lastPostSnaps.get(idA);
        BodySnapshot snapB = lastPostSnaps.get(idB);
        boolean hasPostVelA = snapA != null && snapA.velocityReadValid();
        boolean hasPostVelB = snapB != null && snapB.velocityReadValid();

        // T-8: 3D relative speed -- no normal projection (T-6 independent).
        double relSpeedBefore = Double.NaN;
        double kineticBefore  = Double.NaN;
        if (hasStartVelA && hasStartVelB) {
            double rvx = startVelA[0] - startVelB[0];
            double rvy = startVelA[1] - startVelB[1];
            double rvz = startVelA[2] - startVelB[2];
            relSpeedBefore = Math.sqrt(rvx*rvx + rvy*rvy + rvz*rvz);
            if (mEffValid) {
                kineticBefore = 0.5 * mEff * relSpeedBefore * relSpeedBefore;
            }
        }

        double relSpeedAfter = Double.NaN;
        double kineticAfter  = Double.NaN;
        if (hasPostVelA && hasPostVelB) {
            double rvx = snapA.linVelX() - snapB.linVelX();
            double rvy = snapA.linVelY() - snapB.linVelY();
            double rvz = snapA.linVelZ() - snapB.linVelZ();
            relSpeedAfter = Math.sqrt(rvx*rvx + rvy*rvy + rvz*rvz);
            if (mEffValid) {
                kineticAfter = 0.5 * mEff * relSpeedAfter * relSpeedAfter;
            }
        }

        double deltaRelSpeed = (Double.isNaN(relSpeedBefore) || Double.isNaN(relSpeedAfter))
                ? Double.NaN : Math.abs(relSpeedBefore - relSpeedAfter);
        double kineticDelta  = (Double.isNaN(kineticBefore) || Double.isNaN(kineticAfter))
                ? Double.NaN : Math.abs(kineticBefore - kineticAfter);

        return new ImpactMetrics(
                record.serverTick(),
                record.bodyPairKey(),
                record.contactType(),
                record.totalImpulseJ(),
                mEff,
                record.massAKpg(),
                record.massBKpg(),
                impactEnergyJ,
                candidateStress,
                threshold,
                exceeds,
                record.impulseAlongNormalJ(),   // T-6 UC
                contactPressureProxy,            // area UC
                hasStartVelA,
                hasStartVelB,
                hasPostVelA,
                hasPostVelB,
                relSpeedBefore,
                relSpeedAfter,
                deltaRelSpeed,
                kineticBefore,
                kineticAfter,
                kineticDelta);
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
