package io.github.omegau371.trueimpact.diagnostic;

import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;

import java.util.Map;

/**
 * Processes the raw double[] from Rapier3D.clearCollisions() for T-3, T-5, T-6.
 *
 * [T-5] clearCollisions covers ALL substeps combined; substep attribution is UNCONFIRMED.
 *       Only raw statistics are output — no substep claim is made.
 *
 * [T-3] Correlates contacts with the most recent POST_STEP body snapshots (last substep).
 *       The snapshot timing may not match the substep in which the contact occurred.
 *       If snapshot unavailable for a body, explicitly outputs "unavailable".
 *
 * [T-6] Converts localNormalA/B from BODY_COM_LOCAL [inferred] to WORLD using post-step pose.
 *       Outputs dot(normalWorld, worldPointB - worldPointA) and dot(normalWorld, relVel).
 *       No direction reversal — raw data only.
 *
 * [C2-audit] Bodies with id not in activeSubLevels are labeled NON_ACTIVE_SUBLEVEL_BODY,
 *            NOT "terrain".
 */
public final class ContactLogger {

    private ContactLogger() {}

    /**
     * Called from DiagnosticContactCaptureMixin after Rapier3D.clearCollisions() returns.
     *
     * @param data           raw double[N*15] from clearCollisions — NO TI filter applied
     * @param serverTick     level.getGameTime()
     * @param substepCount   configured substeps per tick (for T-5 annotation)
     * @param lastPostSnaps  most-recent POST_STEP snapshots, keyed by runtimeId
     * @param tickStartVels  linVel captured at substep-0 PRE_STEP; used by T-3-MISS
     */
    public static void onClearCollisions(double[] data, long serverTick,
                                          int substepCount,
                                          Map<Integer, BodySnapshot> lastPostSnaps,
                                          Map<Integer, double[]> tickStartVels) {
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) return;

        int count = data.length / 15;
        double substepDt = (substepCount > 0) ? 0.05 / substepCount : 0.05;

        // T-5: raw count (TI applies NO filter; Sable's 25*min(mass) filter follows AFTER this)
        ExperimentLog.info("[T-5] tick={} rawContactCount={} substepCount={}" +
                " [raw Rapier3D.clearCollisions output; TI applies no filter;" +
                " sub-level vs sub-level contacts may not exist in this array]",
                serverTick, count, substepCount);

        // Per-tick accumulators for [T-3-SUMMARY]
        double sumForceAmountRaw  = 0;
        double sumNormalMomChange = 0;
        double sumReconJ          = 0;
        double maxClosingVel      = 0;
        int    activeActiveCount  = 0;
        boolean reconJAvailable   = false;
        boolean nomMomAvailable   = false;

        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int idA = (int) data[base];
            int idB = (int) data[base + 1];
            double forceAmountRaw = data[base + 2];

            double nAx = data[base+3], nAy = data[base+4], nAz = data[base+5];
            double nBx = data[base+6], nBy = data[base+7], nBz = data[base+8];
            double pAx = data[base+9], pAy = data[base+10], pAz = data[base+11];
            double pBx = data[base+12], pBy = data[base+13], pBz = data[base+14];

            BodySnapshot snapA = lastPostSnaps.get(idA);
            BodySnapshot snapB = lastPostSnaps.get(idB);
            String bodyTypeA = (snapA != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";
            String bodyTypeB = (snapB != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";

            // Calibration fields — NaN when data unavailable
            double closingVel      = Double.NaN;
            double normalMomChange = Double.NaN;
            double mEff            = Double.NaN;
            double dotSep          = Double.NaN, dotRelVel = Double.NaN;
            double J_avg           = Double.NaN; // reconstructed from tick-start + POST_STEP Δv
            String t3Data;

            if (snapA != null && snapB != null
                    && snapA.velocityReadValid() && snapB.velocityReadValid()) {
                // ── Contact point world positions ────────────────────────────
                double[] wPA   = rotateAndAdd(snapA, pAx, pAy, pAz);
                double[] wPB   = rotateAndAdd(snapB, pBx, pBy, pBz);
                double[] vPA   = contactPointVelocity(snapA,
                        wPA[0]-snapA.posX(), wPA[1]-snapA.posY(), wPA[2]-snapA.posZ());
                double[] vPB   = contactPointVelocity(snapB,
                        wPB[0]-snapB.posX(), wPB[1]-snapB.posY(), wPB[2]-snapB.posZ());

                // ── Normal in world (A's normal; direction UC: T-6) ──────────
                double[] nWorld = rotateVec(snapA, nAx, nAy, nAz);
                double nMag = Math.sqrt(nWorld[0]*nWorld[0]+nWorld[1]*nWorld[1]+nWorld[2]*nWorld[2]);
                if (nMag > 1e-9) { nWorld[0]/=nMag; nWorld[1]/=nMag; nWorld[2]/=nMag; }

                // ── Closing velocity (POST_STEP contact point relative vel) ──
                double relVx = vPA[0]-vPB[0], relVy = vPA[1]-vPB[1], relVz = vPA[2]-vPB[2];
                closingVel = relVx*nWorld[0]+relVy*nWorld[1]+relVz*nWorld[2];
                double massA = snapA.massKpg(), massB = snapB.massKpg();
                double kA = (massA > 0) ? 1.0/massA : 0;
                double kB = (massB > 0) ? 1.0/massB : 0;
                mEff = (kA + kB > 0) ? 1.0/(kA+kB) : Double.NaN;
                normalMomChange = Double.isNaN(mEff) ? Double.NaN : mEff * Math.abs(closingVel);

                // ── T-6 dot products ─────────────────────────────────────────
                double bAx = wPB[0]-wPA[0], bAy = wPB[1]-wPA[1], bAz = wPB[2]-wPA[2];
                dotSep    = nWorld[0]*bAx    + nWorld[1]*bAy    + nWorld[2]*bAz;
                dotRelVel = nWorld[0]*relVx  + nWorld[1]*relVy  + nWorld[2]*relVz;

                // ── Reconstructed impulse J from tick-start + POST_STEP Δv ──
                // Uses abs(dot) because T-6 normal direction is unconfirmed.
                double[] vbA = tickStartVels.get(idA);
                double[] vbB = tickStartVels.get(idB);
                if (vbA != null && vbB != null) {
                    double dvAn = Math.abs(
                            (snapA.linVelX()-vbA[0])*nWorld[0]
                          + (snapA.linVelY()-vbA[1])*nWorld[1]
                          + (snapA.linVelZ()-vbA[2])*nWorld[2]);
                    double dvBn = Math.abs(
                            (snapB.linVelX()-vbB[0])*nWorld[0]
                          + (snapB.linVelY()-vbB[1])*nWorld[1]
                          + (snapB.linVelZ()-vbB[2])*nWorld[2]);
                    J_avg = (massA * dvAn + massB * dvBn) / 2.0;
                }

                // ── Calibration ratios ───────────────────────────────────────
                // rawOverNomMom : compares forceAmountRaw to mEff*|closingVel_after|
                // rawOverReconJ : compares forceAmountRaw to (mA*|dvAn| + mB*|dvBn|)/2
                // impulseFromRaw_dt    : forceAmountRaw * 0.05  (treat raw as force/tick)
                // impulseFromRaw_subDt : forceAmountRaw * substepDt (treat raw as force/substep)
                double rawOverNomMom = (!Double.isNaN(normalMomChange) && normalMomChange > 1e-9)
                        ? forceAmountRaw / normalMomChange : Double.NaN;
                double rawOverReconJ = (!Double.isNaN(J_avg) && J_avg > 1e-9)
                        ? forceAmountRaw / J_avg : Double.NaN;

                t3Data = String.format(
                        "closingVel=%s normalMomChange=%s mEff=%s" +
                        " rawOverNomMom=%s rawOverReconJ=%s" +
                        " impulseFromRaw_dt=%s impulseFromRaw_subDt=%s J_avg_recon=%s" +
                        " [T-6] dotSep=%s dotRelVel=%s" +
                        " [snap=LAST_SUBSTEP_POST; T-6 normal dir UC; abs(dot) for recon]",
                        fmtOrNaN(closingVel), fmtOrNaN(normalMomChange), fmtOrNaN(mEff),
                        fmtOrNaN(rawOverNomMom), fmtOrNaN(rawOverReconJ),
                        fmt(forceAmountRaw * 0.05), fmt(forceAmountRaw * substepDt),
                        fmtOrNaN(J_avg),
                        fmt(dotSep), fmt(dotRelVel));

                if (!Double.isNaN(normalMomChange)) { sumNormalMomChange += normalMomChange; nomMomAvailable = true; }
                if (!Double.isNaN(J_avg))            { sumReconJ          += J_avg;           reconJAvailable = true; }
                if (!Double.isNaN(closingVel))        maxClosingVel = Math.max(maxClosingVel, Math.abs(closingVel));
                activeActiveCount++;
            } else {
                String reason = "";
                if (snapA == null) reason += " snapA=unavailable(NON_ACTIVE_SUBLEVEL)";
                if (snapB == null) reason += " snapB=unavailable(NON_ACTIVE_SUBLEVEL)";
                if (snapA != null && !snapA.velocityReadValid()) reason += " snapA.velValid=false";
                if (snapB != null && !snapB.velocityReadValid()) reason += " snapB.velValid=false";
                t3Data = "T3_T6=unavailable" + reason;
            }

            sumForceAmountRaw += forceAmountRaw;

            if (!ExperimentLog.info(
                    "[T-3] tick={} idx={} idA={}({}) idB={}({}) forceAmountRaw={}" +
                    " nA=({},{},{}) nB=({},{},{}) pA=({},{},{}) pB=({},{},{})" +
                    " {}",
                    serverTick, i, idA, bodyTypeA, idB, bodyTypeB,
                    String.format("%.6f", forceAmountRaw),
                    String.format("%.4f", nAx), String.format("%.4f", nAy), String.format("%.4f", nAz),
                    String.format("%.4f", nBx), String.format("%.4f", nBy), String.format("%.4f", nBz),
                    String.format("%.4f", pAx), String.format("%.4f", pAy), String.format("%.4f", pAz),
                    String.format("%.4f", pBx), String.format("%.4f", pBy), String.format("%.4f", pBz),
                    t3Data)) {
                ExperimentLog.info("[T-3] tick={} rate limit hit after {} contacts", serverTick, i);
                break;
            }
        }

        // [T-3-SUMMARY]: per-tick aggregate — main calibration output
        // ratioRawOverNomMom ≈ 1        → forceAmountRaw is impulse (mEff·|closingVel| units)
        // ratioRawOverReconJ ≈ 1        → forceAmountRaw is impulse (mX·|ΔvX_n| units)
        // ratioRawOverReconJ ≈ 1/subDt  → forceAmountRaw is force per substep
        // ratioRawOverReconJ ≈ 1/0.05   → forceAmountRaw is force per tick
        if (count > 0) {
            double ratioRawOverNomMom = nomMomAvailable  && sumNormalMomChange > 1e-9
                    ? sumForceAmountRaw / sumNormalMomChange : Double.NaN;
            double ratioRawOverReconJ = reconJAvailable  && sumReconJ > 1e-9
                    ? sumForceAmountRaw / sumReconJ          : Double.NaN;
            ExperimentLog.info(
                    "[T-3-SUMMARY] tick={} substepCount={} substepDt={} contactCount={} activeActiveCount={}" +
                    " sumForceAmountRaw={} sumNormalMomChange={} sumReconJ={}" +
                    " maxClosingVel={}" +
                    " ratioRawOverNomMom={} ratioRawOverReconJ={}" +
                    " sumRaw_times_dt={} sumRaw_times_subDt={}" +
                    " [~1→raw≈impulse; ~1/subDt→raw≈force/substep; ~1/0.05→raw≈force/tick]",
                    serverTick, substepCount, fmt(substepDt), count, activeActiveCount,
                    fmt(sumForceAmountRaw),
                    nomMomAvailable  ? fmt(sumNormalMomChange) : "NaN",
                    reconJAvailable  ? fmt(sumReconJ)          : "NaN",
                    fmt(maxClosingVel),
                    fmtOrNaN(ratioRawOverNomMom), fmtOrNaN(ratioRawOverReconJ),
                    fmt(sumForceAmountRaw * 0.05), fmt(sumForceAmountRaw * substepDt));
        }

        // T-3-MISS: when Rapier returned no contacts, scan active body pairs for
        // correlated velocity changes that indicate a hidden transfer mechanism.
        if (count == 0) {
            logT3Miss(serverTick, substepCount, lastPostSnaps, tickStartVels);
        }
    }

    // ── T-3-MISS ─────────────────────────────────────────────────────────────

    // Proximity: bodies must be within this many WORLD blocks.
    // 4 blocks catches touching/overlapping structures; excludes distant pairs.
    private static final double T3_MISS_PROXIMITY    = 4.0;
    // Minimum |dv along pair axis| for a pair to be evaluated.
    private static final double T3_MISS_AXIS_DV_MIN  = 0.05;
    // STRONG criterion: momentum residual along pair axis below this fraction.
    // residual = |mA*dvA_n + mB*dvB_n| / max(|mA*dvA_n|, |mB*dvB_n|)
    // → 0.0 = perfect momentum conservation; 1.0 = completely non-conserved.
    private static final double T3_MISS_RESIDUAL_MAX = 0.50;

    /**
     * Pairwise Δv scan when rawContactCount=0.
     *
     * Classification:
     *   STRONG — opposite Δv along pair axis AND momentum residual < RESIDUAL_MAX
     *            → strong evidence of hidden impulse transfer; emits [T-3-MISS-STRONG]
     *   WEAK   — same-direction Δv (gravity, global accel) or high residual
     *            → silently suppressed; not collision evidence
     *
     * Pair axis  n = normalize(posB − posA)  (world space)
     * dvX_n     = dot(deltaVX, n)
     * residual   = |mA*dvA_n + mB*dvB_n| / max(|mA*dvA_n|, |mB*dvB_n|)
     *
     * Requires: debug bodies ON (lastPostSnaps populated) AND debug contacts ON.
     */
    private static void logT3Miss(long serverTick, int substepCount,
                                   Map<Integer, BodySnapshot> lastPostSnaps,
                                   Map<Integer, double[]> tickStartVels) {
        if (lastPostSnaps.size() < 2) {
            if (lastPostSnaps.isEmpty()) {
                ExperimentLog.info("[T-3-MISS] tick={} skipped: no POST_STEP snapshots" +
                        " — enable 'debug bodies on' alongside 'debug contacts on'", serverTick);
            }
            return;
        }

        Integer[] ids = lastPostSnaps.keySet().toArray(new Integer[0]);
        for (int i = 0; i < ids.length; i++) {
            for (int j = i + 1; j < ids.length; j++) {
                int idA = ids[i], idB = ids[j];
                BodySnapshot sA = lastPostSnaps.get(idA);
                BodySnapshot sB = lastPostSnaps.get(idB);
                if (sA == null || sB == null) continue;
                if (!sA.velocityReadValid() || !sB.velocityReadValid()) continue;

                double[] vbA = tickStartVels.get(idA);
                double[] vbB = tickStartVels.get(idB);
                if (vbA == null || vbB == null) continue;

                // ── Proximity (world distance between body pose origins) ──────
                double sepX = sB.posX() - sA.posX();
                double sepY = sB.posY() - sA.posY();
                double sepZ = sB.posZ() - sA.posZ();
                double dist = Math.sqrt(sepX*sepX + sepY*sepY + sepZ*sepZ);
                if (dist > T3_MISS_PROXIMITY || dist < 1e-6) continue;

                // ── Pair axis n = normalize(posB − posA) ────────────────────
                double nx = sepX/dist, ny = sepY/dist, nz = sepZ/dist;

                // ── Full-tick Δv (POST_STEP − substep-0 PRE_STEP) ───────────
                double dvAx = sA.linVelX() - vbA[0];
                double dvAy = sA.linVelY() - vbA[1];
                double dvAz = sA.linVelZ() - vbA[2];
                double dvBx = sB.linVelX() - vbB[0];
                double dvBy = sB.linVelY() - vbB[1];
                double dvBz = sB.linVelZ() - vbB[2];

                // ── Project onto pair axis ───────────────────────────────────
                double dvA_n = dvAx*nx + dvAy*ny + dvAz*nz;
                double dvB_n = dvBx*nx + dvBy*ny + dvBz*nz;

                // Skip if neither body changed along pair axis beyond threshold
                if (Math.abs(dvA_n) < T3_MISS_AXIS_DV_MIN
                        && Math.abs(dvB_n) < T3_MISS_AXIS_DV_MIN) continue;

                // ── Momentum residual (Newton 3rd: mA*dvA_n + mB*dvB_n ≈ 0) ─
                double mA = sA.massKpg(), mB = sB.massKpg();
                double momA_n = mA * dvA_n, momB_n = mB * dvB_n;
                double maxMom = Math.max(Math.abs(momA_n), Math.abs(momB_n));
                double residual = (maxMom > 1e-9)
                        ? Math.abs(momA_n + momB_n) / maxMom : Double.NaN;

                // ── Classify ─────────────────────────────────────────────────
                boolean oppositeSigns    = dvA_n * dvB_n < 0;
                boolean residualLow      = !Double.isNaN(residual) && residual < T3_MISS_RESIDUAL_MAX;
                boolean isStrong         = oppositeSigns && residualLow;

                if (isStrong) {
                    // Opposite Δv along pair axis + momentum roughly conserved
                    // → likely real impulse transfer not captured by clearCollisions
                    if (!ExperimentLog.info(
                            "[T-3-MISS-STRONG] tick={} substepCount={} rawContactCount=0" +
                            " idA={} idB={} dist={}" +
                            " mA={}kpg dvA_n={} dvA=({},{},{})" +
                            " mB={}kpg dvB_n={} dvB=({},{},{})" +
                            " momA_n={} momB_n={} residual={}" +
                            " [opp-sign dv along pair axis; residual<{};" +
                            " clearCollisions empty; Sable-level transfer suspected]",
                            serverTick, substepCount,
                            idA, idB, fmt(dist),
                            fmt(mA), fmt(dvA_n), fmt(dvAx), fmt(dvAy), fmt(dvAz),
                            fmt(mB), fmt(dvB_n), fmt(dvBx), fmt(dvBy), fmt(dvBz),
                            fmt(momA_n), fmt(momB_n),
                            Double.isNaN(residual) ? "NaN" : fmt(residual),
                            T3_MISS_RESIDUAL_MAX)) {
                        break; // rate limit hit — stop scanning this tick
                    }
                }
                // WEAK (same-direction or high-residual): silently suppressed.
                // These are gravity/global-acceleration artefacts; not collision evidence.
                // Use SNAP logs to observe them independently.
            }
        }
    }

    private static String fmt(double v)       { return String.format("%.4f", v); }
    private static String fmtOrNaN(double v)  { return Double.isNaN(v) ? "NaN" : String.format("%.4f", v); }

    // ── Coordinate helpers ───────────────────────────────────────────────────

    /** Rotate body-COM-local point to WORLD: world = Q * local + pos */
    private static double[] rotateAndAdd(BodySnapshot s, double lx, double ly, double lz) {
        double[] r = rotateVec(s, lx, ly, lz);
        return new double[]{r[0]+s.posX(), r[1]+s.posY(), r[2]+s.posZ()};
    }

    /** Rotate body-COM-local vector to WORLD using orientation quaternion. */
    private static double[] rotateVec(BodySnapshot s, double lx, double ly, double lz) {
        double qx = s.oriX(), qy = s.oriY(), qz = s.oriZ(), qw = s.oriW();
        // Quaternion rotation: q * (lx,ly,lz,0) * q^-1
        double tx = 2*(qy*lz - qz*ly);
        double ty = 2*(qz*lx - qx*lz);
        double tz = 2*(qx*ly - qy*lx);
        return new double[]{
            lx + qw*tx + qy*tz - qz*ty,
            ly + qw*ty + qz*tx - qx*tz,
            lz + qw*tz + qx*ty - qy*tx
        };
    }

    /** v_contact ≈ v_cm + ω × r  (post-step approximation) */
    private static double[] contactPointVelocity(BodySnapshot s,
                                                  double rx, double ry, double rz) {
        double wx = s.angVelX(), wy = s.angVelY(), wz = s.angVelZ();
        return new double[]{
            s.linVelX() + wy*rz - wz*ry,
            s.linVelY() + wz*rx - wx*rz,
            s.linVelZ() + wx*ry - wy*rx
        };
    }
}
