package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.DamageResolver;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.damage.DeferredContraptionDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredContraptionDamageQueue;
import io.github.omegau371.trueimpact.damage.DeferredSublevelDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredSublevelDamageQueue;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.VictimInfo;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.physics.ImpactRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(SableImpactCapture.class);

    private SableImpactCapture() {}

    /**
     * Minimum totalImpulseJ per contact record to classify a pair as ACTIVE_IMPACT.
     * Below this -> ACTIVE_SUSTAINED (resting/sliding).
     * Mirrors ContactLogger.T3_IMPACT_IMPULSE_PER_CONTACT for consistent classification.
     */
    static final double IMPACT_IMPULSE_PER_CONTACT_THRESHOLD = 5.0;
    static final double PHASE_1C_PLACEHOLDER_MATERIAL_THRESHOLD_J = 50.0;

    /**
     * Minimum kImpact (Sable units) for any world contact to be recorded as damage.
     * Calibrated for 72J typical moderate impact: detect at 40J.
     * Intentionally LOWER than any materialThresholdJ so that impacts below the break
     * threshold can still accumulate over multiple hits.
     */
    public static double GLOBAL_DETECTION_THRESHOLD_J = 40.0;

    /**
     * Multiplier applied to materialThresholdJ when constructing DeferredDamageEvents.
     * Separates the per-material break threshold from the detection sensitivity:
     *   break threshold (passed to accumulator) = materialThresholdJ * BREAK_THRESHOLD_MULTIPLIER
     *   detection threshold (enqueue filter)    = GLOBAL_DETECTION_THRESHOLD_J
     *
     * With multiplier=10 and a typical 72J moderate impact:
     *   STONE  break=500J → ratio 0.14 per hit → ~7 hits to CRITICAL
     *   WOOD   break=200J → ratio 0.36 per hit → ~3 hits to CRITICAL
     *   SOFT_SOIL break=50J → ratio 1.44 → CRITICAL in 1 hit (soil is soft)
     */
    static final double BREAK_THRESHOLD_MULTIPLIER = 10.0;

    // -- Phase 1E world kImpact diagnostic status --------------------------------
    // Reported per tick to explain why worldKImpact is NaN or finite.
    public enum WorldKImpactStatus {
        OK,                  // worldKImpact finite and valid
        NO_ACTIVE_SNAPSHOT,  // active body not found in lastPostSnaps
        NO_POST_VEL,         // active body velocityReadValid() == false
        NO_START_VEL,        // no tickStartVels entry for active body (enable 'debug contacts on')
        NON_FINITE,          // computed but non-finite (unexpected)
        NOT_SEEN             // no world contact this tick
    }

    // The Phase 1C capture gate (captureGate) has been removed.
    // process() now runs unconditionally on every physics tick.
    // Diagnostic flags (DiagnosticConfig) only control log output, not pipeline execution.
    // The dependency between DiagnosticConfig.ENABLED and capture was the root cause of
    // world-vs-active impacts never enqueuing in production (debug=off) use.

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

    // Phase 1D: most recent victim detection result from any contact event.
    // Updated when active-vs-active records are produced or when world-vs-active contacts
    // are detected. Not overwritten by zero-contact ticks (preserves last known info).
    // null until first contact event since last reset.
    private static VictimInfo lastVictimInfo;

    // Phase 1E: world kImpact diagnostic fields -- always overwritten each tick that
    // has a world contact; NOT "last non-zero" semantics.
    // worldKImpactStatusLastTick explains why worldKImpactLastTick is NaN or finite.
    private static boolean            lastWorldContactSeen;
    private static double             lastWorldKImpact       = Double.NaN;
    private static int                lastWorldActiveId      = -1;
    private static boolean            lastWorldHasPostVel;
    private static boolean            lastWorldHasStartVel;
    private static WorldKImpactStatus lastWorldKImpactStatus = WorldKImpactStatus.NOT_SEEN;

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
            T8Stats       t8Stats,                   // rolling calibration stats
            VictimInfo    lastVictimInfo,            // Phase 1D: most recent victim detection; null if none
            // Phase 1E world kImpact diagnostics (always reflects last tick that had a world contact)
            boolean            worldContactSeenLastTick,
            double             worldKImpactLastTick,
            int                worldActiveIdLastTick,
            boolean            worldHasPostVelLastTick,
            boolean            worldHasStartVelLastTick,
            WorldKImpactStatus worldKImpactStatusLastTick
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
                snapshotT8Stats(),
                lastVictimInfo,
                lastWorldContactSeen,
                lastWorldKImpact,
                lastWorldActiveId,
                lastWorldHasPostVel,
                lastWorldHasStartVel,
                lastWorldKImpactStatus);
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
        lastVictimInfo           = null;
        lastWorldContactSeen   = false;
        lastWorldKImpact       = Double.NaN;
        lastWorldActiveId      = -1;
        lastWorldHasPostVel    = false;
        lastWorldHasStartVel   = false;
        lastWorldKImpactStatus = WorldKImpactStatus.NOT_SEEN;
        t8SampleCount  = 0;
        t8LastRatio    = Double.NaN;
        t8MinRatio     = Double.NaN;
        t8MaxRatio     = Double.NaN;
        t8SumRatio     = 0.0;
        t8WindowHead   = 0;
        t8WindowSize   = 0;
        Arrays.fill(t8RatioWindow, 0.0);
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
        return process(data, serverTick, substepCount, lastPostSnaps, tickStartVels,
                "minecraft:overworld");
    }

    /**
     * Full-signature version with explicit level key for DeferredDamageEvent.
     * Called by DiagnosticContactCaptureMixin with the actual level dimension ID.
     * The 5-arg overload defaults to "minecraft:overworld" for test compat.
     */
    public static List<ImpactRecord> process(
            double[] data,
            long serverTick,
            int substepCount,
            Map<Integer, BodySnapshot> lastPostSnaps,
            Map<Integer, double[]> tickStartVels,
            String levelKey) {

        int count = (data != null) ? data.length / 15 : 0;
        if (count <= 0) {
            recordStats(serverTick, count, 0, 0, 0);
            return List.of();
        }
        double substepDt = (substepCount > 0) ? 0.05 / substepCount : 0.05;

        // Aggregate contact records by active-vs-active pair.
        Map<Long, PairAccum> pairMap = new LinkedHashMap<>();
        boolean sawWorldContact = false;
        // Phase 1E: best single-body kImpact estimate from world-vs-active contacts.
        // Used when no active-vs-active metrics are available (world-only tick).
        // = 0.5 * massActive * |vBefore^2 - vAfter^2|; NaN when velocity unavailable.
        double worldKImpact = Double.NaN;

        // Phase 1E world kImpact diagnostic tracking (per-tick, OR semantics for flags).
        int     worldDiagActiveId    = -1;
        boolean worldDiagHasPostVel  = false;
        boolean worldDiagHasStartVel = false;
        WorldKImpactStatus worldDiagStatus = WorldKImpactStatus.NOT_SEEN;

        // Impact direction: normalized pre-impact velocity of the first active body that
        // contacts a world block. Used to populate DeferredDamageEvent.impactDir*.
        double worldImpactDirX = Double.NaN;
        double worldImpactDirY = Double.NaN;
        double worldImpactDirZ = Double.NaN;

        // E: block outward face normal (world space) and sliding speed for friction/entity-damage.
        // Captured from raw Rapier contact data: static bodies have identity orientation,
        // so their contact normal is already in world space (no quaternion rotation needed).
        double worldContactNormalX  = Double.NaN;
        double worldContactNormalY  = Double.NaN;
        double worldContactNormalZ  = Double.NaN;
        double worldTangentialSpeedMs = Double.NaN;

        // Phase 3A: per-sublevel world contact tracking.
        // Maps each active sublevel (by runtimeId) to its first contact point (body-local)
        // and its per-sublevel kImpact.  Replaces single-slot tracking so all sublevels
        // that simultaneously hit the world each get their own damage event.
        java.util.Map<Integer, double[]>     phase3aCpMap   = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, BodySnapshot> phase3aSnapMap = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, Double>       phase3aKMap    = new java.util.LinkedHashMap<>();
        // Per-sublevel normalized pre-impact velocity direction (from tickStartVels).
        // NaN entries mean vBefore was unavailable; ConfinementFactor falls back to isotropic.
        java.util.Map<Integer, double[]>     phase3aDirMap  = new java.util.LinkedHashMap<>();
        // Path 1: per-sublevel world-body contact positions (world-space BlockPos key → raw cp).
        // Populated alongside phase3aCpMap; world body's local frame = world frame.
        java.util.Map<Integer, java.util.Map<Long, double[]>> phase1WorldCpMap = new java.util.LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int    idA      = (int) data[base];
            int    idB      = (int) data[base + 1];
            double forceRaw = data[base + 2];
            // normalA (indices 3-5): points from A toward B, in A's local space.
            // normalB (indices 6-8): points from B toward A, in B's local space.
            double nAx = data[base + 3], nAy = data[base + 4], nAz = data[base + 5];
            double nBx = data[base + 6], nBy = data[base + 7], nBz = data[base + 8];

            BodySnapshot snapA = lastPostSnaps.get(idA);
            BodySnapshot snapB = lastPostSnaps.get(idB);

            if (snapA == null && snapB == null) continue; // both unknown -- no active body
            if (snapA == null || snapB == null) {
                // Phase 1D: world-vs-active or active-vs-world contact detected.
                sawWorldContact = true;

                // Phase 1E: compute single-body kinetic energy change for world contact.
                // Active body loses/gains energy due to collision with static world.
                // mEff for world contact = massActive (world is infinitely massive).
                BodySnapshot aSnap = (snapA != null) ? snapA : snapB;
                int aId = (snapA != null) ? idA : idB;
                worldDiagActiveId = aId; // last active id seen (diagnostic)

                // E: block outward face normal (first contact wins).
                // Static bodies (world) have identity orientation, so their local normal = world normal.
                // normalB (B→A) when A=active; normalA (A→B) when B=active → both point block→active body.
                if (Double.isNaN(worldContactNormalX)) {
                    double nx, ny, nz;
                    if (snapA != null) {
                        // A is active, B is world: normalB points from world toward A (block outward)
                        nx = nBx; ny = nBy; nz = nBz;
                    } else {
                        // A is world, B is active: normalA points from world toward B (block outward)
                        nx = nAx; ny = nAy; nz = nAz;
                    }
                    double nMag = Math.sqrt(nx*nx + ny*ny + nz*nz);
                    if (nMag > 1e-9) {
                        worldContactNormalX = nx / nMag;
                        worldContactNormalY = ny / nMag;
                        worldContactNormalZ = nz / nMag;
                    }
                }

                // Phase 3A: record first contact point + impact direction per sublevel.
                if (!phase3aCpMap.containsKey(aId)) {
                    double cpX, cpY, cpZ;
                    if (snapA != null) {
                        cpX = data[base + 9];  cpY = data[base + 10]; cpZ = data[base + 11];
                    } else {
                        cpX = data[base + 12]; cpY = data[base + 13]; cpZ = data[base + 14];
                    }
                    phase3aCpMap.put(aId, new double[]{cpX, cpY, cpZ});
                    phase3aSnapMap.put(aId, aSnap);
                    // Normalize pre-impact velocity as the impact direction for directional confinement.
                    double[] vb = tickStartVels.get(aId);
                    if (vb != null) {
                        double mag = Math.sqrt(vb[0]*vb[0] + vb[1]*vb[1] + vb[2]*vb[2]);
                        if (mag > 1e-9) {
                            phase3aDirMap.put(aId, new double[]{vb[0]/mag, vb[1]/mag, vb[2]/mag});
                        }
                    }
                }
                // Path 1: record world body contact point (world-space) for each distinct BlockPos.
                // World body local frame = world frame; opposite indices from active-body cp above.
                {
                    double wcpX, wcpY, wcpZ;
                    if (snapA != null) {
                        // A=active, B=world → B's local cp (indices 12-14) = world coords
                        wcpX = data[base + 12]; wcpY = data[base + 13]; wcpZ = data[base + 14];
                    } else {
                        // A=world, B=active → A's local cp (indices 9-11) = world coords
                        wcpX = data[base + 9]; wcpY = data[base + 10]; wcpZ = data[base + 11];
                    }
                    // Subtract epsilon on Y (0.1 block) to land below the top face of the block
                    // being struck. Rapier contact points can be ~0.01 above the exact face due to
                    // floating-point drift; 0.1 absorbs this without crossing into adjacent blocks.
                    long bkey = worldBlockKey(
                        (int) Math.floor(wcpX),
                        (int) Math.floor(wcpY - 0.1),
                        (int) Math.floor(wcpZ));
                    phase1WorldCpMap
                        .computeIfAbsent(aId, k -> new java.util.LinkedHashMap<>())
                        .putIfAbsent(bkey, new double[]{wcpX, wcpY, wcpZ});
                }

                if (!aSnap.velocityReadValid()) {
                    // Only set failure status if we don't already have a better one.
                    if (worldDiagStatus != WorldKImpactStatus.OK
                            && worldDiagStatus != WorldKImpactStatus.NON_FINITE
                            && worldDiagStatus != WorldKImpactStatus.NO_START_VEL) {
                        worldDiagStatus = WorldKImpactStatus.NO_POST_VEL;
                    }
                } else {
                    worldDiagHasPostVel = true;
                    double[] vb = tickStartVels.get(aId);
                    if (vb != null) worldDiagHasStartVel = true;
                    if (vb == null) {
                        if (worldDiagStatus != WorldKImpactStatus.OK
                                && worldDiagStatus != WorldKImpactStatus.NON_FINITE) {
                            worldDiagStatus = WorldKImpactStatus.NO_START_VEL;
                        }
                    } else {
                        double vbSq = vb[0]*vb[0] + vb[1]*vb[1] + vb[2]*vb[2];
                        double vaSq = aSnap.linVelX()*aSnap.linVelX()
                                    + aSnap.linVelY()*aSnap.linVelY()
                                    + aSnap.linVelZ()*aSnap.linVelZ();
                        // Capture normalized pre-impact velocity as impact direction (first hit wins).
                        if (Double.isNaN(worldImpactDirX) && vbSq > 1e-18) {
                            double mag = Math.sqrt(vbSq);
                            worldImpactDirX = vb[0] / mag;
                            worldImpactDirY = vb[1] / mag;
                            worldImpactDirZ = vb[2] / mag;
                        }
                        // kImpact uses velocity change projected onto the contact normal, not full
                        // speed magnitude: a body accelerating/decelerating tangentially (e.g. a
                        // wheeled vehicle under its own power rolling along the ground) changes its
                        // total kinetic energy with no force along the contact normal at all, and
                        // must not be charged as impact energy for that -- only normal-direction
                        // velocity change (the body actually being pushed into or rebounding off the
                        // surface) represents a genuine collision. Falls back to full-magnitude delta
                        // when no usable contact normal was resolved (near-zero-magnitude edge case).
                        double k;
                        if (!Double.isNaN(worldContactNormalX)) {
                            double vbDotNormal = vb[0]*worldContactNormalX
                                                + vb[1]*worldContactNormalY
                                                + vb[2]*worldContactNormalZ;
                            // E: tangential speed = sqrt(|v|^2 - (v·n)^2); sign-independent of normal direction.
                            if (Double.isNaN(worldTangentialSpeedMs)) {
                                worldTangentialSpeedMs = Math.sqrt(Math.max(0.0, vbSq - vbDotNormal*vbDotNormal));
                            }
                            double vaDotNormal = aSnap.linVelX()*worldContactNormalX
                                                + aSnap.linVelY()*worldContactNormalY
                                                + aSnap.linVelZ()*worldContactNormalZ;
                            k = 0.5 * aSnap.massKpg() * Math.abs(vbDotNormal*vbDotNormal - vaDotNormal*vaDotNormal);
                        } else {
                            k = 0.5 * aSnap.massKpg() * Math.abs(vbSq - vaSq);
                        }
                        if (Double.isNaN(worldKImpact) || k > worldKImpact) {
                            worldKImpact = k;
                        }
                        if (Double.isFinite(k)) {
                            phase3aKMap.merge(aId, k, Math::max);
                        }
                        if (Double.isFinite(k)) {
                            worldDiagStatus = WorldKImpactStatus.OK; // once OK, stays OK
                        } else if (worldDiagStatus != WorldKImpactStatus.OK) {
                            worldDiagStatus = WorldKImpactStatus.NON_FINITE;
                        }
                    }
                }
                continue;
            }

            long key = pairKey(idA, idB);
            PairAccum acc = pairMap.computeIfAbsent(key, k -> new PairAccum(idA, idB));

            acc.sumForce += forceRaw;
            acc.contactCount++;

            // First-contact-wins geometry for active-vs-active mutual damage.
            // cpA = data[9..11] (body-A local), cpB = data[12..14] (body-B local).
            // PairAccum stores idA/idB from the first contact; subsequent contacts may swap
            // the bodies, so we check identity before assigning contact points.
            if (!acc.contactGeomValid) {
                boolean sameOrder = (idA == acc.idA);
                BodySnapshot snpA = sameOrder ? snapA : snapB;  // snap for acc.idA
                BodySnapshot snpB = sameOrder ? snapB : snapA;  // snap for acc.idB
                int cpBaseA = sameOrder ? 9  : 12;              // data index for acc.idA cp
                int cpBaseB = sameOrder ? 12 : 9;               // data index for acc.idB cp
                acc.cpAx = data[base + cpBaseA];     acc.cpAy = data[base + cpBaseA + 1]; acc.cpAz = data[base + cpBaseA + 2];
                acc.cpBx = data[base + cpBaseB];     acc.cpBy = data[base + cpBaseB + 1]; acc.cpBz = data[base + cpBaseB + 2];
                acc.snapARef = snpA;
                acc.snapBRef = snpB;
                // Impact direction per body = its velocity RELATIVE to the other body.
                // Each body's damaged face is its leading face in the RELATIVE motion:
                // dirA = normalize(vA − vB), dirB = −dirA. The first implementation used
                // the other body's ABSOLUTE velocity, which is the exact OPPOSITE for a
                // struck (static) body — its surface snap walked THROUGH the body to the
                // far face ("hit the south face, the north face broke") — and NaN for a
                // striker hitting a resting victim (vA ≈ 0 → no direction → no face
                // spread → the striker's full energy landed on one cell and broke it,
                // "the striking side takes more damage").
                double[] vbA = tickStartVels.get(acc.idA);
                double[] vbB = tickStartVels.get(acc.idB);
                double vAx = vbA != null ? vbA[0] : snpA.linVelX();
                double vAy = vbA != null ? vbA[1] : snpA.linVelY();
                double vAz = vbA != null ? vbA[2] : snpA.linVelZ();
                double vBx = vbB != null ? vbB[0] : snpB.linVelX();
                double vBy = vbB != null ? vbB[1] : snpB.linVelY();
                double vBz = vbB != null ? vbB[2] : snpB.linVelZ();
                double relX = vAx - vBx, relY = vAy - vBy, relZ = vAz - vBz;
                acc.vRelSq = relX*relX + relY*relY + relZ*relZ;
                double relSpd = Math.sqrt(acc.vRelSq);
                if (relSpd > 1e-9) {
                    acc.impDirAx =  relX/relSpd; acc.impDirAy =  relY/relSpd; acc.impDirAz =  relZ/relSpd;
                    acc.impDirBx = -relX/relSpd; acc.impDirBy = -relY/relSpd; acc.impDirBz = -relZ/relSpd;
                }
                acc.contactGeomValid = true;
            }

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
            // No active-vs-active pairs. Still update VictimInfo + enqueue if world contact.
            VictimInfo noActiveVictim = null;
            if (sawWorldContact) {
                noActiveVictim = SableVictimCapture.hasCaptureThisTick()
                        ? SableVictimCapture.buildWorldVictimInfo()
                        : VictimInfo.worldContactNoCallback();

                // Phase 2G: enqueue per-block events from onCollision registry when energy threshold met.
                // Registry contains the exact BlockPos for each block the physics body touched this
                // substep. Energy is distributed equally across all contacted blocks.
                if (Double.isFinite(worldKImpact) && worldKImpact > GLOBAL_DETECTION_THRESHOLD_J) {
                    enqueueFromRegistry(worldKImpact, serverTick, levelKey,
                            worldImpactDirX, worldImpactDirY, worldImpactDirZ,
                            worldContactNormalX, worldContactNormalY, worldContactNormalZ,
                            worldTangentialSpeedMs);
                    // Phase 3A: enqueue each world-touching sublevel individually.
                    for (java.util.Map.Entry<Integer, BodySnapshot> ws : phase3aSnapMap.entrySet()) {
                        int wsId = ws.getKey();
                        double[] wsCp = phase3aCpMap.get(wsId);
                        Double wsK = phase3aKMap.get(wsId);
                        if (wsK != null && wsCp != null) {
                            double[] wsDir = phase3aDirMap.get(wsId);
                            java.util.Map<Long, double[]> wsWcpMap = phase1WorldCpMap.get(wsId);
                            double wsKCapped = capKImpact(wsK, ws.getValue());
                            if (isTerrainContact(wsWcpMap, ws.getValue())) {
                                double[] twcp = wsWcpMap.values().iterator().next();
                                enqueueEmbeddedContact(wsKCapped, serverTick, levelKey,
                                        wsCp[0], wsCp[1], wsCp[2], wsId, ws.getValue(),
                                        wsDir != null ? wsDir[0] : Double.NaN,
                                        wsDir != null ? wsDir[1] : Double.NaN,
                                        wsDir != null ? wsDir[2] : Double.NaN,
                                        twcp[0], twcp[1], twcp[2]);
                            }
                            enqueueContraptionContact(wsKCapped, levelKey,
                                    wsCp[0], wsCp[1], wsCp[2], wsId, ws.getValue());
                            enqueueWorldBlockContact(wsKCapped, levelKey, wsWcpMap);
                        }
                    }
                }
            }
            recordStats(serverTick, count, 0, 0, 0, null, null, noActiveVictim,
                    sawWorldContact, worldKImpact, worldDiagActiveId,
                    worldDiagHasPostVel, worldDiagHasStartVel, worldDiagStatus);
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

            // Phase 3A mutual: enqueue sublevel damage for BOTH bodies of an ACTIVE_IMPACT.
            // Energy = dissipated KE of the pair, 0.5·μ·v_rel² (perfectly-plastic upper
            // bound; μ = effective mass, so a light body limits the damage a heavy one
            // takes — a wood block cannot wreck a stone slab regardless of who moves).
            // Each face runs the normal apply pipeline (elastic floor, confinement,
            // accumulation), which absorbs the plastic-bound overestimate.
            // vis = NaN: the "terrain" here is the other BODY, so the phantom-terrain
            // filter (and Phase 3C terrain penetration) must not see this event.
            // First-wins dedup per (runtimeId, tick) still applies — a body that both
            // hits terrain and another body this tick is charged only once.
            if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_PHYSICS_STRUCTURE_DAMAGE
                    && io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_STRUCTURE_VS_STRUCTURE
                    && acc.contactGeomValid && type == ContactType.ACTIVE_IMPACT) {
                double eDiss = Double.isFinite(effMass) ? 0.5 * effMass * acc.vRelSq : 0.0;
                enqueueActiveVsActiveContact(eDiss, serverTick, levelKey,
                        acc.idA, acc.snapARef, acc.cpAx, acc.cpAy, acc.cpAz,
                        acc.impDirAx, acc.impDirAy, acc.impDirAz,
                        acc.idB, acc.snapBRef, acc.cpBx, acc.cpBy, acc.cpBz);
                enqueueActiveVsActiveContact(eDiss, serverTick, levelKey,
                        acc.idB, acc.snapBRef, acc.cpBx, acc.cpBy, acc.cpBz,
                        acc.impDirBx, acc.impDirBy, acc.impDirBz,
                        acc.idA, acc.snapARef, acc.cpAx, acc.cpAy, acc.cpAz);
            }
        }

        // Phase 1D: build VictimInfo for this tick.
        // World contacts take priority when both world and active-vs-active occurred.
        // Active-vs-active-only -> ACTIVE_SUBLEVEL (definitively no world block involved).
        // World contact without any capture (callback + sampling both failed) -> worldContactNoCallback().
        VictimInfo tickVictim;
        if (sawWorldContact) {
            tickVictim = SableVictimCapture.hasCaptureThisTick()
                    ? SableVictimCapture.buildWorldVictimInfo()
                    : VictimInfo.worldContactNoCallback();
        } else {
            tickVictim = VictimInfo.activeSublevel();
        }

        // Phase 2G: per-block events from onCollision registry.
        // Uses kineticImpactEnergyJ from active-vs-active metrics when available;
        // falls back to worldKImpact (single-body estimate) for world-only contacts.
        if (sawWorldContact) {
            double kImpact = (latestRecordMetrics != null
                    && Double.isFinite(latestRecordMetrics.kineticImpactEnergyJ()))
                    ? latestRecordMetrics.kineticImpactEnergyJ()
                    : worldKImpact;
            if (Double.isFinite(kImpact) && kImpact > GLOBAL_DETECTION_THRESHOLD_J) {
                enqueueFromRegistry(kImpact, serverTick, levelKey,
                        worldImpactDirX, worldImpactDirY, worldImpactDirZ,
                        worldContactNormalX, worldContactNormalY, worldContactNormalZ,
                        worldTangentialSpeedMs);
            }
            // Phase 3A: enqueue each world-touching sublevel individually.
            // Gate moved outside the active-vs-active kImpact check: Sable's own test
            // sublevels (testgravity/testsnag) can create low-energy active-vs-active resting
            // contacts in the same substep as a high-speed world impact, making kImpact < 40J
            // even though worldKImpact is large. enqueueEmbeddedContact has its own
            // per-sublevel gate (wsK > GLOBAL_DETECTION_THRESHOLD_J), so moving the loop here
            // is safe — low-kImpact sublevels are still filtered by the inner guard.
            for (java.util.Map.Entry<Integer, BodySnapshot> ws : phase3aSnapMap.entrySet()) {
                int wsId = ws.getKey();
                double[] wsCp = phase3aCpMap.get(wsId);
                Double wsK = phase3aKMap.get(wsId);
                if (wsK != null && wsCp != null) {
                    double[] wsDir = phase3aDirMap.get(wsId);
                    java.util.Map<Long, double[]> wsWcpMap = phase1WorldCpMap.get(wsId);
                    double wsKCapped = capKImpact(wsK, ws.getValue());
                    if (isTerrainContact(wsWcpMap, ws.getValue())) {
                        double[] twcp = wsWcpMap.values().iterator().next();
                        enqueueEmbeddedContact(wsKCapped, serverTick, levelKey,
                                wsCp[0], wsCp[1], wsCp[2], wsId, ws.getValue(),
                                wsDir != null ? wsDir[0] : Double.NaN,
                                wsDir != null ? wsDir[1] : Double.NaN,
                                wsDir != null ? wsDir[2] : Double.NaN,
                                twcp[0], twcp[1], twcp[2]);
                    }
                    enqueueContraptionContact(wsKCapped, levelKey,
                            wsCp[0], wsCp[1], wsCp[2], wsId, ws.getValue());
                    enqueueWorldBlockContact(wsKCapped, levelKey, wsWcpMap);
                }
            }
        }

        recordStats(serverTick, count, result.size(), activeImpactCount, sustainedCount,
                latestRecordMetrics, latestActiveImpactMetrics, tickVictim,
                sawWorldContact, worldKImpact, worldDiagActiveId,
                worldDiagHasPostVel, worldDiagHasStartVel, worldDiagStatus);
        return result;
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained) {
        recordStats(tick, rawContacts, records, activeImpacts, sustained, null, null, null,
                false, Double.NaN, -1, false, false, WorldKImpactStatus.NOT_SEEN);
    }

    private static synchronized void recordStats(long tick, int rawContacts, int records,
                                                  int activeImpacts, int sustained,
                                                  ImpactMetrics latestRecordMetrics,
                                                  ImpactMetrics latestActiveImpactMetrics,
                                                  VictimInfo tickVictimInfo,
                                                  boolean worldContactSeen,
                                                  double worldKImpact,
                                                  int worldActiveId,
                                                  boolean worldHasPostVel,
                                                  boolean worldHasStartVel,
                                                  WorldKImpactStatus worldStatus) {
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
        // lastVictimInfo: updated when any contact (world or active-vs-active) was detected.
        // null tickVictimInfo means no contacts this tick; preserve previous known value.
        if (tickVictimInfo != null) {
            lastVictimInfo = tickVictimInfo;
        }
        // Phase 1E world kImpact diagnostics: always overwrite (not last-non-zero semantics).
        lastWorldContactSeen   = worldContactSeen;
        lastWorldKImpact       = worldKImpact;
        lastWorldActiveId      = worldActiveId;
        lastWorldHasPostVel    = worldHasPostVel;
        lastWorldHasStartVel   = worldHasStartVel;
        lastWorldKImpactStatus = worldStatus;
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
        // Extract last t8WindowSize samples in insertion order from the circular buffer.
        // t8WindowHead points to the NEXT write slot, so the oldest retained sample is at
        // (t8WindowHead - t8WindowSize + T8_WINDOW) % T8_WINDOW.
        // Arrays.copyOf(window, size) is WRONG after wrapping: it reads positions [0..size-1]
        // which are no longer the last 'size' entries when the head has wrapped past T8_WINDOW.
        double[] copy = new double[t8WindowSize];
        for (int i = 0; i < t8WindowSize; i++) {
            int idx = (t8WindowHead - t8WindowSize + i + T8_WINDOW) % T8_WINDOW;
            copy[i] = t8RatioWindow[idx];
        }
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
        double threshold = PHASE_1C_PLACEHOLDER_MATERIAL_THRESHOLD_J;

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

        // Phase 1C canonical: velocity-derived kinetic impact energy.
        // kineticImpactEnergyJ = abs(kBefore - kAfter) = kineticDelta (same value, canonical role).
        // Uses abs() not max(0,...): Rapier spring model + gravity can make kAfter > kBefore
        // in genuine impacts, so max(0,...) would give 0 when we need a non-zero signal.
        double kineticImpactEnergyJ = kineticDelta;

        // velocityDerivedImpulseJ = mEff * ||deltaVRel_3D||.
        // deltaVRel = (vA_after - vA_before) - (vB_after - vB_before): change in relative velocity
        // using 3D vectors for all four readings. Requires all four velocity availability flags.
        double velocityDerivedImpulseJ = Double.NaN;
        if (hasStartVelA && hasStartVelB && hasPostVelA && hasPostVelB && mEffValid) {
            double dvRelX = (snapA.linVelX() - startVelA[0]) - (snapB.linVelX() - startVelB[0]);
            double dvRelY = (snapA.linVelY() - startVelA[1]) - (snapB.linVelY() - startVelB[1]);
            double dvRelZ = (snapA.linVelZ() - startVelA[2]) - (snapB.linVelZ() - startVelB[2]);
            double dvRelMag = Math.sqrt(dvRelX*dvRelX + dvRelY*dvRelY + dvRelZ*dvRelZ);
            velocityDerivedImpulseJ = mEff * dvRelMag;
        }

        // Canonical threshold comparison now uses velocity-derived energy.
        // NaN when velocity data unavailable (debug contacts off) -> exceeds = false (no spurious damage).
        double candidateStress = kineticImpactEnergyJ;
        boolean exceeds = Double.isFinite(candidateStress) && candidateStress > threshold;

        // Unit audit: expose rawSumForce so DiagnosticCommand can compare candidate formulas.
        double substepDt   = record.substepDt();
        double rawSumForce = (substepDt > 0) ? record.totalImpulseJ() / substepDt : Double.NaN;

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
                kineticDelta,
                record.contactCount(),
                rawSumForce,
                substepDt,
                kineticImpactEnergyJ,
                velocityDerivedImpulseJ);
    }

    // Bit-packing identical to BlockPos.asLong — avoids a MC import in this class so unit
    // tests (no MC runtime) can still call process() without ClassNotFoundException.
    private static long worldBlockKey(int x, int y, int z) {
        return ((long)(y & 0xFFFFF) << 44) | ((long)(x & 0x3FFFFFF) << 18) | (long)(z & 0x3FFFFFF);
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

    /**
     * Rotate a WORLD-space vector into the body's local (plot-grid) frame — the inverse
     * of rotateVec (conjugate quaternion). The plot block grid never rotates with the
     * body, so any world-space offset or direction must pass through this before being
     * used as grid coordinates. Falls back to identity when the orientation is invalid.
     */
    private static double[] rotateVecInverse(BodySnapshot s, double wx, double wy, double wz) {
        double qx = -s.oriX(), qy = -s.oriY(), qz = -s.oriZ(), qw = s.oriW();
        if (!Double.isFinite(qw) || (qx == 0 && qy == 0 && qz == 0 && qw == 0)) {
            return new double[]{wx, wy, wz};
        }
        double tx = 2 * (qy * wz - qz * wy);
        double ty = 2 * (qz * wx - qx * wz);
        double tz = 2 * (qx * wy - qy * wx);
        return new double[]{
            wx + qw * tx + qy * tz - qz * ty,
            wy + qw * ty + qz * tx - qx * tz,
            wz + qw * tz + qx * ty - qy * tx
        };
    }

    /**
     * Phase 3A: velocity-delta enqueue for persistent contacts (resting body given impulse).
     *
     * Rapier's clearCollisions() only emits events for NEW contact establishment.
     * Bodies already in contact with terrain (sleeping on floor) don't re-appear in that
     * buffer when an external impulse is applied to them. This method detects such impacts
     * by comparing the tick-start velocity (captured at substep-0 PRE_STEP, after any
     * externally applied impulse) with the end-of-tick velocity from lastPostSnaps.
     *
     * Deduplication: DeferredSublevelDamageQueue deduplicates by
     * (runtimeId, faceAwareRound(cpX), faceAwareRound(cpY), faceAwareRound(cpZ)).
     * For single-block sublevels all three faceAwareRound(cp) values yield 0, producing
     * the same key as the clearCollisions() path. If the contact was already enqueued
     * via clearCollisions(), this call is a silent no-op -- no double damage.
     *
     * Called from SableEventBridge.onPostStep() at the final substep only, after
     * lastPostSnaps is fully populated for the tick.
     */
    public static void tryEnqueueVelocityDelta(
            int runtimeId, BodySnapshot snap, double[] vBefore,
            long serverTick, String levelKey) {
        if (!snap.velocityReadValid() || snap.massKpg() <= 0.0) return;
        if (runtimeId < 0) return;

        double vbx = vBefore[0], vby = vBefore[1], vbz = vBefore[2];
        double vax = snap.linVelX(), vay = snap.linVelY(), vaz = snap.linVelZ();

        // Gravity compensation: a body coasting UPWARD decelerates by g·dt every tick with
        // zero contact — that kinetic energy became potential energy, not damage. Comparing
        // vaSq against raw vbSq fired a synthetic "impact" on every post-bounce ascent tick,
        // with dir = up → surface snap walked to the TOP face → "hit the bottom, the top
        // cracked". Predict the free-flight end-of-tick velocity under gravity alone and
        // only treat energy MISSING relative to that prediction as contact dissipation.
        // (For a falling body the prediction is FASTER than vb, so landing detection keeps
        // working and gets slightly more accurate.)
        final double GRAVITY_DV_PER_TICK = 9.81 * 0.05; // one full server tick
        double vaExpY  = vby - GRAVITY_DV_PER_TICK;
        double vaExpSq = vbx*vbx + vaExpY*vaExpY + vbz*vbz;
        double vaSq    = vax*vax + vay*vay + vaz*vaz;

        // Only trigger when the body lost kinetic energy versus free flight
        // (contact dissipation). Gravity-only coasting matches the prediction → no event.
        if (vaSq >= vaExpSq) return;

        // Resting-body filter: a SUPPORTED body has va = 0 while the free-flight prediction
        // is −g·dt, so the deficit is (g·dt)² ≈ 0.24 m²/s² EVERY tick — that "missing" energy
        // is the ground's support force, not damage. For heavy bodies 0.5·m·0.24 exceeds the
        // 40 J detection threshold (observed: 2200+ constant-120 J events from one resting
        // 1000 kpg structure). Require the deficit to exceed a real contact Δv of ~1 m/s;
        // a genuine landing from even half a block (3.1 m/s) gives a deficit of ~9.6.
        final double MIN_CONTACT_DV_SQ = 1.0; // (1 m/s)²
        if (vaExpSq - vaSq < MIN_CONTACT_DV_SQ) return;

        double k = capKImpact(
                0.5 * snap.massKpg() * (vaExpSq - vaSq), snap); // positive since vaExpSq > vaSq

        // Infer contact face from dominant pre-impact velocity direction.
        // Placed at ±0.5 on the dominant axis (face of the block).
        // faceAwareRound(±0.5) = 0, so local block index is always (0,0,0)
        // for a single-block sublevel -- same as the clearCollisions() path.
        double absX = Math.abs(vbx), absY = Math.abs(vby), absZ = Math.abs(vbz);
        double cpX = 0.0, cpY = 0.0, cpZ = 0.0;
        if (absY >= absX && absY >= absZ) {
            cpY = (vby < 0.0) ? -0.5 : 0.5;
        } else if (absX >= absZ) {
            cpX = (vbx < 0.0) ? -0.5 : 0.5;
        } else {
            cpZ = (vbz < 0.0) ? -0.5 : 0.5;
        }

        double mag = Math.sqrt(vbx*vbx + vby*vby + vbz*vbz);
        double dX = mag > 1e-9 ? vbx/mag : Double.NaN;
        double dY = mag > 1e-9 ? vby/mag : Double.NaN;
        double dZ = mag > 1e-9 ? vbz/mag : Double.NaN;
        // The synthetic cp is fabricated in WORLD-ALIGNED axes (dominant world travel axis
        // ± 0.5), so the synthetic terrain contact is snap.pos + cp directly. Applying the
        // body rotation here (old code) double-rotated it: enqueueEmbeddedContact converts
        // world→grid with R⁻¹, and R⁻¹(R(cp)) put tumbling bodies' contacts back at the
        // raw cp while the vis position swung mid-air (genuine landings phantom-dropped).
        double tWcpX = snap.posX() + cpX;
        double tWcpY = snap.posY() + cpY;
        double tWcpZ = snap.posZ() + cpZ;
        // Fallback only: when Rapier already reported a real contact for this body this tick,
        // the synthetic event is a duplicate at a WRONG position (COM±0.5 single-block
        // assumption maps to a near-COM block, not the contact face, on multi-block bodies).
        if (!wasEmbeddedEnqueuedThisTick(serverTick, runtimeId)) {
            enqueueEmbeddedContact(k, serverTick, levelKey, cpX, cpY, cpZ, runtimeId, snap, dX, dY, dZ,
                    tWcpX, tWcpY, tWcpZ);
        }
        enqueueContraptionContact(k, levelKey, cpX, cpY, cpZ, runtimeId, snap);

        // Path 1: estimate world body contact position from sublevel world coords + rotated body-local cp.
        // snap.posX/Y/Z is the sublevel's Minecraft world-space position; rotate(cpXYZ) gives the
        // contact offset in world space. Subtract epsilon on Y to land in the floor block being struck.
        if (snap.posX() != 0.0 || snap.posY() != 0.0) { // guard: snap position available
            double wcpX = tWcpX;
            double wcpY = tWcpY;
            double wcpZ = tWcpZ;
            long bkey = worldBlockKey(
                (int) Math.floor(wcpX),
                (int) Math.floor(wcpY - 0.1),
                (int) Math.floor(wcpZ));
            java.util.Map<Long, double[]> wcpMap = new java.util.LinkedHashMap<>();
            wcpMap.put(bkey, new double[]{wcpX, wcpY, wcpZ});
            enqueueWorldBlockContact(k, levelKey, wcpMap);
        }
    }

    /**
     * Returns true when the static-body contact map contains at least one point within 50 blocks
     * of the body's visual Minecraft position (snap.posX/Z).
     *
     * Embedded plot-wall contacts have world-body coordinates at ~20 000 000 (far from any real
     * Minecraft position), so their distance to snap.posX/Z always exceeds the threshold.
     * Real terrain contacts are within a few blocks of the structure's visual position.
     */
    private static boolean isTerrainContact(java.util.Map<Long, double[]> wcpMap, BodySnapshot snap) {
        if (wcpMap == null || wcpMap.isEmpty() || snap == null) return false;
        double pvX = snap.posX(), pvZ = snap.posZ();
        for (double[] wcp : wcpMap.values()) {
            if (Math.abs(wcp[0] - pvX) < 50.0 && Math.abs(wcp[2] - pvZ) < 50.0) return true;
        }
        return false;
    }

    /**
     * Phase 3A: Enqueues a DeferredSublevelDamageEvent for the active sublevel body.
     *
     * terrainWcp is the Minecraft-world contact point on the static terrain body (from
     * phase1WorldCpMap, the first entry for this sublevel's runtimeId). This is the
     * ground-truth contact position — it doesn't change when the body bounces.
     *
     * plotCp formula derivation (rotation and bounce cancel):
     *   At contact time: visCenter_c + rotate(bodyLocalCp) = terrainWcp
     *   → visCenter_c = terrainWcp - rotate(bodyLocalCp)
     *   comOff_c = comOff_snap + (visCenter_c - snap.pos) = comOff_snap + (terrainWcp - rotate(cp) - snap.pos)
     *   plotCp = comOff_c + rotate(bodyLocalCp) = terrainWcp + comOff_snap - snap.pos
     * The rotate terms cancel — plotCp depends only on terrain contact position and translational state.
     */
    /**
     * runtimeIds that already got an embedded damage event this tick (any source).
     * One landing produces the same body-ΔKE twice: once from the real Rapier contact
     * and once from the velocity-delta synthetic path — and dev-server testing showed
     * the synthetic one can enqueue FIRST within a tick, so per-source gating is not
     * enough. enqueueEmbeddedContact enforces first-wins per (runtimeId, tick): the
     * duplicate carries no extra information, only double-counted damage. The synthetic
     * event's near-COM contact point is corrected at apply time by the surface snap in
     * TrueImpactMod.tryBreakSublevelBlock.
     */
    private static final java.util.Set<Integer> EMBEDDED_ENQUEUED_THIS_TICK = new java.util.HashSet<>();
    private static long embeddedEnqueueTick = Long.MIN_VALUE;

    private static void markEmbeddedEnqueued(long serverTick, int runtimeId) {
        if (serverTick != embeddedEnqueueTick) {
            EMBEDDED_ENQUEUED_THIS_TICK.clear();
            embeddedEnqueueTick = serverTick;
        }
        EMBEDDED_ENQUEUED_THIS_TICK.add(runtimeId);
    }

    private static boolean wasEmbeddedEnqueuedThisTick(long serverTick, int runtimeId) {
        return serverTick == embeddedEnqueueTick && EMBEDDED_ENQUEUED_THIS_TICK.contains(runtimeId);
    }

    /**
     * Δv sanity cap: constraint yanks (grab tool) and post-break angular whip report
     * velocity changes of 100+ m/s, producing absurd kImpact (534 000 J observed).
     * Clamps to the energy of a maxΔv impact for this body's mass. Applied to EVERY
     * damage path fed by body ΔKE (sublevel, world block, contraption) — capping only
     * the sublevel path let an uncapped Path 1 turn a swung wooden skeleton into a
     * stone-crushing hammer that itself took capped (negligible) damage.
     */
    private static double capKImpact(double kImpact, BodySnapshot snap) {
        if (snap == null) return kImpact;
        double bodyMass = (snap.massKpg() > 0 && Double.isFinite(snap.massKpg())) ? snap.massKpg() : 0.0;
        double maxDv = io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.SUBLEVEL_MAX_DELTA_V_MS;
        if (bodyMass > 0 && maxDv > 0) {
            return Math.min(kImpact, 0.5 * bodyMass * maxDv * maxDv);
        }
        return kImpact;
    }

    private static void enqueueEmbeddedContact(double kImpact, long serverTick, String levelKey,
                                                double cpX, double cpY, double cpZ,
                                                int runtimeId, BodySnapshot snap,
                                                double impactDirX, double impactDirY, double impactDirZ,
                                                double terrainWcpX, double terrainWcpY, double terrainWcpZ) {
        if (!Double.isFinite(kImpact) || kImpact <= GLOBAL_DETECTION_THRESHOLD_J) return;
        if (Double.isNaN(cpX) || snap == null || runtimeId < 0) return;
        // First-wins per (runtimeId, tick): real and synthetic events for the same landing
        // carry the same body ΔKE — a second enqueue is pure double-counting.
        if (wasEmbeddedEnqueuedThisTick(serverTick, runtimeId)) return;
        markEmbeddedEnqueued(serverTick, runtimeId);
        kImpact = capKImpact(kImpact, snap);
        try { kImpact *= io.github.omegau371.trueimpact.TrueImpactConfig.effectivePhysicsStructureMultiplier(); } catch (Throwable ignored) {}
        double comOffX = snap.comValid() ? snap.comX() - snap.plotCenterX() : 0.0;
        double comOffY = snap.comValid() ? snap.comY() - snap.plotCenterY() : 0.0;
        double comOffZ = snap.comValid() ? snap.comZ() - snap.plotCenterZ() : 0.0;
        // plotCp = comOff + R⁻¹(terrainWcp − snap.pos).
        // Derivation: a grid point g maps to world as  world(g) = comWorld + R(g − comPlot),
        // with comWorld ≈ snap.pos and comPlot = plotCenter + comOff. Solving for g at the
        // terrain contact:  g − plotCenter = comOff + R⁻¹(terrainWcp − snap.pos).
        // The old formula omitted R⁻¹ ("rotate terms cancel" — false for R ≠ identity):
        // correct for axis-aligned drops, but a tumbling corner landing mapped the contact
        // to a rotated grid position — observed as damage on the OPPOSITE corner.
        double[] gridOff = rotateVecInverse(snap,
                terrainWcpX - snap.posX(), terrainWcpY - snap.posY(), terrainWcpZ - snap.posZ());
        double plotCpX = comOffX + gridOff[0];
        double plotCpY = comOffY + gridOff[1];
        double plotCpZ = comOffZ + gridOff[2];
        // Impact direction in plot-grid space, for all grid-walk consumers downstream.
        double gridDirX = Double.NaN, gridDirY = Double.NaN, gridDirZ = Double.NaN;
        if (Double.isFinite(impactDirX) && Double.isFinite(impactDirY) && Double.isFinite(impactDirZ)) {
            double[] gd = rotateVecInverse(snap, impactDirX, impactDirY, impactDirZ);
            gridDirX = gd[0]; gridDirY = gd[1]; gridDirZ = gd[2];
        }
        // Visual contact = terrain contact position (it IS the Minecraft-world contact).
        double visX = terrainWcpX;
        double visY = terrainWcpY;
        double visZ = terrainWcpZ;
        if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.LOG_PHASE3A)
            LOGGER.info("[TI3A-D] enqueue: kImpact={} massKpg={} plotCp=({},{},{}) bodyLocalCp=({},{},{}) comOff=({},{},{}) runtimeId={} plotCenter=({},{},{}) dir=({},{},{}) vis=({},{},{})",
                    String.format("%.2f", kImpact),
                    String.format("%.3f", snap.massKpg()),
                    String.format("%.3f", plotCpX), String.format("%.3f", plotCpY), String.format("%.3f", plotCpZ),
                    String.format("%.3f", cpX), String.format("%.3f", cpY), String.format("%.3f", cpZ),
                    String.format("%.3f", comOffX), String.format("%.3f", comOffY), String.format("%.3f", comOffZ),
                    runtimeId,
                    snap.plotCenterX(), snap.plotCenterY(), snap.plotCenterZ(),
                    String.format("%.3f", impactDirX), String.format("%.3f", impactDirY), String.format("%.3f", impactDirZ),
                    String.format("%.2f", visX), String.format("%.2f", visY), String.format("%.2f", visZ));
        // Face energy distribution happens at APPLY time (TrueImpactMod.tryBreakSublevelBlock):
        // it needs block access to spread energy over SOLID face cells only, which is not
        // available on the physics thread. Enqueue the single contact with full energy.
        DeferredSublevelDamageQueue.enqueue(new DeferredSublevelDamageEvent(
                serverTick, levelKey, runtimeId,
                plotCpX, plotCpY, plotCpZ,
                kImpact,
                impactDirX, impactDirY, impactDirZ,
                visX, visY, visZ,
                gridDirX, gridDirY, gridDirZ,
                (snap.massKpg() > 0 && Double.isFinite(snap.massKpg())) ? snap.massKpg() : 0.0,
                -1, Double.NaN, Double.NaN, Double.NaN));
    }

    /**
     * Phase 3A mutual: enqueues sublevel damage for ONE side of an active-vs-active pair.
     *
     * Unlike enqueueEmbeddedContact, the contact point arrives already in the body's
     * COM-local frame (Rapier pair data[9..11]/[12..14]), so the grid mapping is simply
     * plotCp = comOff + cp — no world round-trip, no R⁻¹ (the local frame IS the grid
     * frame up to the COM offset). impactDir stays world-space for the event contract;
     * gridDir = R⁻¹(impactDir) as usual.
     *
     * vis = NaN by design: there is no Minecraft-world terrain at this contact (the other
     * side is a physics body), so the phantom filter must skip and Phase 3C terrain
     * penetration must not fire (both check Double.isFinite(visX)).
     */
    private static void enqueueActiveVsActiveContact(double kImpact, long serverTick, String levelKey,
                                                     int runtimeId, BodySnapshot snap,
                                                     double cpX, double cpY, double cpZ,
                                                     double impactDirX, double impactDirY, double impactDirZ,
                                                     int otherRuntimeId, BodySnapshot otherSnap,
                                                     double otherCpX, double otherCpY, double otherCpZ) {
        if (!Double.isFinite(kImpact) || kImpact <= GLOBAL_DETECTION_THRESHOLD_J) return;
        if (Double.isNaN(cpX) || snap == null || runtimeId < 0) return;
        if (wasEmbeddedEnqueuedThisTick(serverTick, runtimeId)) return;
        markEmbeddedEnqueued(serverTick, runtimeId);
        kImpact = capKImpact(kImpact, snap);
        try { kImpact *= io.github.omegau371.trueimpact.TrueImpactConfig.effectivePhysicsStructureMultiplier(); } catch (Throwable ignored) {}
        double comOffX = snap.comValid() ? snap.comX() - snap.plotCenterX() : 0.0;
        double comOffY = snap.comValid() ? snap.comY() - snap.plotCenterY() : 0.0;
        double comOffZ = snap.comValid() ? snap.comZ() - snap.plotCenterZ() : 0.0;
        double plotCpX = comOffX + cpX;
        double plotCpY = comOffY + cpY;
        double plotCpZ = comOffZ + cpZ;
        // Other body's contact point in ITS plot-local frame, for apply-time face lookup.
        double otherPlotCpX = Double.NaN, otherPlotCpY = Double.NaN, otherPlotCpZ = Double.NaN;
        if (otherSnap != null && !Double.isNaN(otherCpX)) {
            double oComX = otherSnap.comValid() ? otherSnap.comX() - otherSnap.plotCenterX() : 0.0;
            double oComY = otherSnap.comValid() ? otherSnap.comY() - otherSnap.plotCenterY() : 0.0;
            double oComZ = otherSnap.comValid() ? otherSnap.comZ() - otherSnap.plotCenterZ() : 0.0;
            otherPlotCpX = oComX + otherCpX;
            otherPlotCpY = oComY + otherCpY;
            otherPlotCpZ = oComZ + otherCpZ;
        }
        double gridDirX = Double.NaN, gridDirY = Double.NaN, gridDirZ = Double.NaN;
        if (Double.isFinite(impactDirX) && Double.isFinite(impactDirY) && Double.isFinite(impactDirZ)) {
            double[] gd = rotateVecInverse(snap, impactDirX, impactDirY, impactDirZ);
            gridDirX = gd[0]; gridDirY = gd[1]; gridDirZ = gd[2];
        }
        if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.LOG_PHASE3A)
            LOGGER.info("[TI3A-AA] enqueue: kImpact={} runtimeId={} plotCp=({},{},{}) dir=({},{},{})",
                    String.format("%.2f", kImpact), runtimeId,
                    String.format("%.3f", plotCpX), String.format("%.3f", plotCpY), String.format("%.3f", plotCpZ),
                    String.format("%.3f", impactDirX), String.format("%.3f", impactDirY), String.format("%.3f", impactDirZ));
        DeferredSublevelDamageQueue.enqueue(new DeferredSublevelDamageEvent(
                serverTick, levelKey, runtimeId,
                plotCpX, plotCpY, plotCpZ,
                kImpact,
                impactDirX, impactDirY, impactDirZ,
                Double.NaN, Double.NaN, Double.NaN,
                gridDirX, gridDirY, gridDirZ,
                (snap.massKpg() > 0 && Double.isFinite(snap.massKpg())) ? snap.massKpg() : 0.0,
                otherRuntimeId, otherPlotCpX, otherPlotCpY, otherPlotCpZ));
    }

    /**
     * Enqueues a contraption damage check alongside an embedded sublevel contact.
     * Gated on ENABLE_CREATE_INTERACTION; fast-path if Create is absent or gate is off.
     * The application phase (TrueImpactMod) does the entity search and anchor destruction.
     *
     * snap.posX/Y/Z are the body's Minecraft world-space coordinates at the time of contact
     * (BodySnapshot coordinate space: "WORLD [block]"). Stored in the event so the application
     * phase can AABB-search for contraption entities at the correct real-world position
     * (not at the embedded plot offset ~20M from origin).
     */
    private static void enqueueContraptionContact(double kImpact, String levelKey,
                                                   double cpX, double cpY, double cpZ,
                                                   int sublevelRuntimeId, BodySnapshot snap) {
        if (!Double.isFinite(kImpact) || kImpact <= GLOBAL_DETECTION_THRESHOLD_J) return;
        if (!io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_CREATE_INTERACTION) return;
        if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.LOG_PHASE4A)
            LOGGER.info("[TI4A-ENQ] kImpact={} runtimeId={}", String.format("%.2f", kImpact), sublevelRuntimeId);
        double wx = (snap != null) ? snap.posX() : Double.NaN;
        double wy = (snap != null) ? snap.posY() : Double.NaN;
        double wz = (snap != null) ? snap.posZ() : Double.NaN;
        DeferredContraptionDamageQueue.enqueue(new DeferredContraptionDamageEvent(
                levelKey, cpX, cpY, cpZ, wx, wy, wz, kImpact, sublevelRuntimeId));
    }

    /**
     * Path 1: enqueues world block contact positions derived from Rapier clearCollisions() data.
     * Each distinct BlockPos in wcpMap gets kImpact / N energy.
     * Gated on ENABLE_BLOCK_BREAKING so it's a no-op when block damage is disabled.
     */
    private static void enqueueWorldBlockContact(double kImpact, String levelKey,
                                                  java.util.Map<Long, double[]> wcpMap) {
        if (!Double.isFinite(kImpact) || kImpact <= GLOBAL_DETECTION_THRESHOLD_J) return;
        if (wcpMap == null || wcpMap.isEmpty()) return;
        if (!io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING) return;
        double kPerBlock = kImpact / wcpMap.size();
        for (double[] wcp : wcpMap.values()) {
            if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.LOG_PATH1)
                LOGGER.info("[TI-P1-ENQ] kPerBlock={} worldCp=({},{},{})",
                    String.format("%.2f", kPerBlock),
                    String.format("%.3f", wcp[0]), String.format("%.3f", wcp[1]), String.format("%.3f", wcp[2]));
            io.github.omegau371.trueimpact.damage.DeferredWorldContactQueue.enqueue(
                new io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent(
                    levelKey, wcp[0], wcp[1], wcp[2], kPerBlock));
        }
    }

    /**
     * Drains TrueImpactBlockCallbackRegistry and enqueues one DeferredDamageEvent per
     * distinct block that was contacted this substep.
     *
     * Total energy (totalEnergyJ) is divided equally across all contacted blocks.
     * For a single-block hit: full energy to that block.
     * For N blocks (e.g. 4 legs): totalEnergyJ/N per block -- physically correct since
     * each contact absorbs its share of the total impulse.
     *
     * Fallback: if registry is empty (callback never fired, e.g. on 1.x Sable or bake not
     * yet complete), falls back to SableVictimCapture single-block path for compatibility.
     */
    private static void enqueueFromRegistry(double totalEnergyJ, long serverTick, String levelKey,
                                             double dirX, double dirY, double dirZ,
                                             double normalX, double normalY, double normalZ,
                                             double tangentialSpeedMs) {
        java.util.List<TrueImpactBlockCallbackRegistry.BlockContact> contacts =
                TrueImpactBlockCallbackRegistry.snapshotAndClear();

        double worldMult = 1.0;
        try { worldMult = io.github.omegau371.trueimpact.TrueImpactConfig.effectiveWorldBlockMultiplier(); } catch (Throwable ignored) {}

        if (!contacts.isEmpty()) {
            // Speed²-weighted energy attribution. The registry is GLOBAL — it holds every
            // world block ANY physics body touched this tick, including the resting support
            // blocks of bodies far from the actual impact. Equal division charged a digging
            // body's per-tick ΔKE to a bystander's support blocks tens of blocks away, in
            // lockstep, every tick ("entanglement" co-destruction, observed 2026-07-08:
            // two block clusters 14+ blocks apart with identical kImpact/accumulated/hitCount).
            // KE flux through a contact scales with v² — weight each block by its own
            // recorded contact speed², and drop resting-support entries (< 1 m/s) entirely.
            final double MIN_CONTACT_SPEED_MS = 1.0;
            double sumSpeedSq = 0.0;
            for (TrueImpactBlockCallbackRegistry.BlockContact c : contacts) {
                double s = c.speed();
                if (Double.isFinite(s) && s >= MIN_CONTACT_SPEED_MS) sumSpeedSq += s * s;
            }
            if (sumSpeedSq <= 0.0) return; // all contacts are static bearing — no impact
            for (TrueImpactBlockCallbackRegistry.BlockContact c : contacts) {
                double s = c.speed();
                if (!Double.isFinite(s) || s < MIN_CONTACT_SPEED_MS) continue;
                double energyPerBlock = totalEnergyJ * worldMult * (s * s / sumSpeedSq);
                MaterialThresholdProfile.MaterialClass mc =
                        MaterialThresholdProfile.classify(c.blockId());
                DeferredDamageQueue.enqueue(new DeferredDamageEvent(
                        serverTick, levelKey,
                        c.blockId(), c.posX(), c.posY(), c.posZ(),
                        mc,
                        energyPerBlock,
                        MaterialThresholdProfile.threshold(mc)
                                * MaterialThresholdProfile.breakMultiplier(mc),
                        VictimInfo.Source.CALLBACK_BLOCK_POS,
                        VictimInfo.Confidence.APPROX,
                        dirX, dirY, dirZ,
                        normalX, normalY, normalZ,
                        tangentialSpeedMs));
            }
            return;
        }

        // Fallback: registry empty (no onCollision fired this substep).
        // Uses legacy SableVictimCapture single-block result.
        VictimInfo fallback = SableVictimCapture.hasCaptureThisTick()
                ? SableVictimCapture.buildWorldVictimInfo()
                : VictimInfo.worldContactNoCallback();
        if (fallback.kind() == VictimInfo.Kind.WORLD_BLOCK) {
            DeferredDamageQueue.enqueue(new DeferredDamageEvent(
                    serverTick, levelKey,
                    fallback.blockId(),
                    fallback.posX(), fallback.posY(), fallback.posZ(),
                    fallback.materialClass(),
                    totalEnergyJ * worldMult,
                    MaterialThresholdProfile.threshold(fallback.materialClass())
                            * MaterialThresholdProfile.breakMultiplier(fallback.materialClass()),
                    fallback.source(),
                    fallback.confidence(),
                    dirX, dirY, dirZ,
                    normalX, normalY, normalZ,
                    tangentialSpeedMs));
        }
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

        // Active-vs-active contact geometry (first-contact-wins).
        // cpA/B: body-local contact point for acc.idA / acc.idB.
        // impDirA: world-space direction B was moving (impact felt by A).
        // impDirB: world-space direction A was moving (impact felt by B).
        boolean contactGeomValid = false;
        double cpAx = Double.NaN, cpAy = Double.NaN, cpAz = Double.NaN;
        double cpBx = Double.NaN, cpBy = Double.NaN, cpBz = Double.NaN;
        double impDirAx = Double.NaN, impDirAy = Double.NaN, impDirAz = Double.NaN;
        double impDirBx = Double.NaN, impDirBy = Double.NaN, impDirBz = Double.NaN;
        double vRelSq = 0.0; // |vA − vB|² pre-contact, for dissipated energy 0.5·μ·v_rel²
        BodySnapshot snapARef = null, snapBRef = null;

        PairAccum(int idA, int idB) {
            this.idA = idA;
            this.idB = idB;
        }
    }
}
