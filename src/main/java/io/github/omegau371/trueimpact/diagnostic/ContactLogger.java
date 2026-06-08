package io.github.omegau371.trueimpact.diagnostic;

import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;

import java.util.Map;

/**
 * Processes the raw double[] from Rapier3D.clearCollisions() for T-3, T-5, T-6.
 *
 * Logging contract:
 *   [T-5] and [T-3-SUMMARY] use infoAlways -- they are structural (1/tick) and must never
 *   be dropped by the rate limiter. Individual [T-3] entries are rate-limited and capped.
 *
 * T-3 calibration conclusion (2026-06-09):
 *   forceAmountRaw is NOT an impulse. It is a per-substep force value.
 *   Canonical conversion: J = forceAmountRaw * substepDt
 *   Evidence: ratioRawOverReconJ ~= 40 ~= 1/substepDt (substepDt=0.025s) in live impact test.
 *   This is exposed as estimatedImpulseJ in [T-3-SUMMARY].
 *
 * Contact phase classification:
 *   IMPACT_CANDIDATE    -- activeVsActive>0 AND estimatedImpulseJ/contact > T3_IMPACT_IMPULSE_PER_CONTACT
 *   SUSTAINED_CONTACT   -- activeVsActive>0 AND impulse/contact below threshold (resting/sliding)
 *   WORLD_VS_ACTIVE     -- activeVsActive=0 AND worldVsActive>0 (terrain pushing sub-level)
 *   NO_ACTIVE_CONTACT   -- no active sub-level on either side
 *
 * [C2-audit] Bodies with id not in activeSubLevels -> NON_ACTIVE_SUBLEVEL_BODY, NOT "terrain".
 */
public final class ContactLogger {

    private ContactLogger() {}

    /** Max individual [T-3] detail entries per tick; [T-3-SUMMARY] always fires regardless. */
    private static final int    T3_DETAIL_MAX = 5;

    /**
     * Minimum estimatedImpulseJ per active-active contact pair to classify as IMPACT_CANDIDATE.
     * Below this threshold -> SUSTAINED_CONTACT (resting/sliding support force).
     * Calibration reference: live impact test showed ~24.5/contact; gravity support << 1.0.
     */
    private static final double T3_IMPACT_IMPULSE_PER_CONTACT = 5.0;

    /**
     * Called from DiagnosticContactCaptureMixin after Rapier3D.clearCollisions() returns.
     *
     * @param data           raw double[N*15] from clearCollisions -- no TI filter applied
     * @param serverTick     level.getGameTime()
     * @param substepCount   configured substeps per tick (for T-5 annotation)
     * @param lastPostSnaps  most-recent POST_STEP snapshots, keyed by runtimeId
     * @param tickStartVels  linVel captured at substep-0 PRE_STEP; used for J reconstruction
     */
    public static void onClearCollisions(double[] data, long serverTick,
                                          int substepCount,
                                          Map<Integer, BodySnapshot> lastPostSnaps,
                                          Map<Integer, double[]> tickStartVels) {
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) return;

        int count = data.length / 15;
        double substepDt = (substepCount > 0) ? 0.05 / substepCount : 0.05;

        // -- Pass 1: category counts + aggregate stats (no logging) --
        int    worldVsActiveCount  = 0, activeVsActiveCount = 0, otherCount = 0;
        double sumForceAmountRaw   = 0;
        double sumNormalMomChange  = 0;   // mEff * |closingVel_after| basis (diagnostic only)
        double sumReconJ           = 0;   // (mA*|dvAn| + mB*|dvBn|)/2 reconstruction basis
        double maxClosingVel       = 0;
        boolean nomMomAvailable    = false;
        boolean reconJAvailable    = false;

        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int    idA      = (int) data[base];
            int    idB      = (int) data[base + 1];
            double forceRaw = data[base + 2];
            double nAx = data[base+3], nAy = data[base+4], nAz = data[base+5];
            double pAx = data[base+9], pAy = data[base+10], pAz = data[base+11];
            double pBx = data[base+12], pBy = data[base+13], pBz = data[base+14];

            boolean aAct = lastPostSnaps.containsKey(idA);
            boolean bAct = lastPostSnaps.containsKey(idB);
            if      (aAct && bAct) activeVsActiveCount++;
            else if (aAct || bAct) worldVsActiveCount++;
            else                   otherCount++;
            sumForceAmountRaw += forceRaw;

            if (aAct && bAct) {
                BodySnapshot sA = lastPostSnaps.get(idA);
                BodySnapshot sB = lastPostSnaps.get(idB);
                if (sA != null && sB != null && sA.velocityReadValid() && sB.velocityReadValid()) {
                    double[] wPA = rotateAndAdd(sA, pAx, pAy, pAz);
                    double[] wPB = rotateAndAdd(sB, pBx, pBy, pBz);
                    double[] vPA = contactPointVelocity(sA, wPA[0]-sA.posX(), wPA[1]-sA.posY(), wPA[2]-sA.posZ());
                    double[] vPB = contactPointVelocity(sB, wPB[0]-sB.posX(), wPB[1]-sB.posY(), wPB[2]-sB.posZ());
                    double[] nW  = rotateVec(sA, nAx, nAy, nAz);
                    double nMag  = Math.sqrt(nW[0]*nW[0] + nW[1]*nW[1] + nW[2]*nW[2]);
                    if (nMag > 1e-9) { nW[0] /= nMag; nW[1] /= nMag; nW[2] /= nMag; }
                    double relVx = vPA[0]-vPB[0], relVy = vPA[1]-vPB[1], relVz = vPA[2]-vPB[2];
                    double cv    = relVx*nW[0] + relVy*nW[1] + relVz*nW[2];
                    double mA    = sA.massKpg(), mB = sB.massKpg();
                    double kA    = (mA > 0) ? 1.0/mA : 0, kB = (mB > 0) ? 1.0/mB : 0;
                    double me    = (kA+kB > 0) ? 1.0/(kA+kB) : Double.NaN;
                    double nmc   = Double.isNaN(me) ? Double.NaN : me * Math.abs(cv);
                    if (!Double.isNaN(nmc)) { sumNormalMomChange += nmc; nomMomAvailable = true; }
                    maxClosingVel = Math.max(maxClosingVel, Math.abs(cv));
                    // Reconstruct J from tick-start velocity; abs() because T-6 normal dir is unconfirmed
                    double[] vbA = tickStartVels.get(idA);
                    double[] vbB = tickStartVels.get(idB);
                    if (vbA != null && vbB != null) {
                        double dvAn = Math.abs(
                                (sA.linVelX()-vbA[0])*nW[0]
                              + (sA.linVelY()-vbA[1])*nW[1]
                              + (sA.linVelZ()-vbA[2])*nW[2]);
                        double dvBn = Math.abs(
                                (sB.linVelX()-vbB[0])*nW[0]
                              + (sB.linVelY()-vbB[1])*nW[1]
                              + (sB.linVelZ()-vbB[2])*nW[2]);
                        sumReconJ += (mA*dvAn + mB*dvBn) / 2.0;
                        reconJAvailable = true;
                    }
                }
            }
        }

        // Canonical impulse estimate: J = forceAmountRaw * substepDt (confirmed 2026-06-09)
        double estimatedImpulseJ = sumForceAmountRaw * substepDt;

        // Contact phase classification
        String contactPhase;
        String phaseNote;
        if (activeVsActiveCount > 0) {
            double impulsePerContact = estimatedImpulseJ / activeVsActiveCount;
            if (impulsePerContact > T3_IMPACT_IMPULSE_PER_CONTACT) {
                contactPhase = "IMPACT_CANDIDATE";
                phaseNote = String.format(" [impulse/contact=%.2f > threshold %.1f]",
                        impulsePerContact, T3_IMPACT_IMPULSE_PER_CONTACT);
            } else {
                contactPhase = "SUSTAINED_CONTACT";
                phaseNote = String.format(" [impulse/contact=%.4f <= threshold %.1f; resting/sliding]",
                        impulsePerContact, T3_IMPACT_IMPULSE_PER_CONTACT);
            }
        } else if (worldVsActiveCount > 0) {
            contactPhase = "WORLD_VS_ACTIVE";
            phaseNote = " [terrain vs sub-level; not used for structure-structure damage]";
        } else {
            contactPhase = "NO_ACTIVE_CONTACT";
            phaseNote = " [no active sub-level on either side]";
        }

        // -- Always emit [T-5] -- structural, never rate-limited --
        ExperimentLog.infoAlways(
                "[T-5] tick={} rawContactCount={} substepCount={}"
                + " worldVsActive={} activeVsActive={} other={}"
                + " [raw Rapier3D.clearCollisions; no TI filter]",
                serverTick, count, substepCount,
                worldVsActiveCount, activeVsActiveCount, otherCount);

        // -- Always emit [T-3-SUMMARY] when contacts exist -- never rate-limited --
        //
        // T-3 canonical formula (confirmed): J = forceAmountRaw * substepDt
        //   estimatedImpulseJ = sumForceAmountRaw * substepDt
        //   ratioRawOverReconJ ~= 40 ~= 1/substepDt confirms force-per-substep semantics.
        //
        // ratioRawOverNomMom is DIAGNOSTIC-ONLY: it uses post-step closing velocity,
        //   which is near zero after the solver resolves the collision. Do not use it
        //   to determine forceAmountRaw's physical meaning.
        if (count > 0) {
            double ratioRawOverNomMom = (nomMomAvailable && sumNormalMomChange > 1e-9)
                    ? sumForceAmountRaw / sumNormalMomChange : Double.NaN;
            double ratioRawOverReconJ = (reconJAvailable  && sumReconJ > 1e-9)
                    ? sumForceAmountRaw / sumReconJ          : Double.NaN;
            ExperimentLog.infoAlways(
                    "[T-3-SUMMARY] tick={} substepCount={} substepDt={}"
                    + " contactCount={} worldVsActive={} activeVsActive={}"
                    + " contactPhase={}{}"
                    + " estimatedImpulseJ={} [=sumForce*substepDt; canonical for damage formula]"
                    + " sumForceAmountRaw={} sumReconJ={}"
                    + " maxClosingVel={}"
                    + " ratioRawOverReconJ={} [~1/substepDt=force/substep confirmed;"
                    + " ~1=impulse; ~1/0.05=force/tick]"
                    + " ratioRawOverNomMom={} [diagnostic-only: post-step residual vel basis]",
                    serverTick, substepCount, fmt(substepDt),
                    count, worldVsActiveCount, activeVsActiveCount,
                    contactPhase, phaseNote,
                    fmt(estimatedImpulseJ),
                    fmt(sumForceAmountRaw),
                    reconJAvailable  ? fmt(sumReconJ)          : "NaN",
                    fmt(maxClosingVel),
                    fmtOrNaN(ratioRawOverReconJ),
                    fmtOrNaN(ratioRawOverNomMom));
        }

        // -- Pass 2: individual [T-3] entries -- rate-limited, capped at T3_DETAIL_MAX --
        int detailEmitted = 0;
        for (int i = 0; i < count; i++) {
            if (detailEmitted >= T3_DETAIL_MAX) {
                ExperimentLog.info("[T-3] tick={} {} more entries omitted (cap={})",
                        serverTick, count - T3_DETAIL_MAX, T3_DETAIL_MAX);
                break;
            }
            int base = i * 15;
            int    idA          = (int) data[base];
            int    idB          = (int) data[base + 1];
            double forceAmountRaw = data[base + 2];
            double nAx = data[base+3], nAy = data[base+4], nAz = data[base+5];
            double nBx = data[base+6], nBy = data[base+7], nBz = data[base+8];
            double pAx = data[base+9], pAy = data[base+10], pAz = data[base+11];
            double pBx = data[base+12], pBy = data[base+13], pBz = data[base+14];

            BodySnapshot snapA = lastPostSnaps.get(idA);
            BodySnapshot snapB = lastPostSnaps.get(idB);
            String bodyTypeA = (snapA != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";
            String bodyTypeB = (snapB != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";

            double closingVel = Double.NaN, normalMomChange = Double.NaN, mEff = Double.NaN;
            double dotSep = Double.NaN, dotRelVel = Double.NaN, J_avg = Double.NaN;
            String t3Data;

            if (snapA != null && snapB != null
                    && snapA.velocityReadValid() && snapB.velocityReadValid()) {
                double[] wPA   = rotateAndAdd(snapA, pAx, pAy, pAz);
                double[] wPB   = rotateAndAdd(snapB, pBx, pBy, pBz);
                double[] vPA   = contactPointVelocity(snapA, wPA[0]-snapA.posX(), wPA[1]-snapA.posY(), wPA[2]-snapA.posZ());
                double[] vPB   = contactPointVelocity(snapB, wPB[0]-snapB.posX(), wPB[1]-snapB.posY(), wPB[2]-snapB.posZ());
                double[] nWorld = rotateVec(snapA, nAx, nAy, nAz);
                double nMag = Math.sqrt(nWorld[0]*nWorld[0] + nWorld[1]*nWorld[1] + nWorld[2]*nWorld[2]);
                if (nMag > 1e-9) { nWorld[0] /= nMag; nWorld[1] /= nMag; nWorld[2] /= nMag; }
                double relVx = vPA[0]-vPB[0], relVy = vPA[1]-vPB[1], relVz = vPA[2]-vPB[2];
                closingVel = relVx*nWorld[0] + relVy*nWorld[1] + relVz*nWorld[2];
                double massA = snapA.massKpg(), massB = snapB.massKpg();
                double kA = (massA > 0) ? 1.0/massA : 0, kB = (massB > 0) ? 1.0/massB : 0;
                mEff = (kA+kB > 0) ? 1.0/(kA+kB) : Double.NaN;
                normalMomChange = Double.isNaN(mEff) ? Double.NaN : mEff * Math.abs(closingVel);
                double bAx = wPB[0]-wPA[0], bAy = wPB[1]-wPA[1], bAz = wPB[2]-wPA[2];
                dotSep    = nWorld[0]*bAx   + nWorld[1]*bAy   + nWorld[2]*bAz;
                dotRelVel = nWorld[0]*relVx + nWorld[1]*relVy + nWorld[2]*relVz;
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
                    J_avg = (massA*dvAn + massB*dvBn) / 2.0;
                }
                double estJ_single = forceAmountRaw * substepDt;
                // rawOverNomMom is diagnostic-only; post-step closing vel is near zero after impact
                double rawOverNomMom = (!Double.isNaN(normalMomChange) && normalMomChange > 1e-9)
                        ? forceAmountRaw / normalMomChange : Double.NaN;
                double rawOverReconJ = (!Double.isNaN(J_avg) && J_avg > 1e-9)
                        ? forceAmountRaw / J_avg : Double.NaN;
                t3Data = String.format(
                        "closingVel=%s normalMomChange=%s mEff=%s"
                        + " estJ_single=%s rawOverReconJ=%s rawOverNomMom=%s(diag-only)"
                        + " [T-6] dotSep=%s dotRelVel=%s [snap=LAST_SUBSTEP_POST; T-6 dir UC]",
                        fmtOrNaN(closingVel), fmtOrNaN(normalMomChange), fmtOrNaN(mEff),
                        fmt(estJ_single), fmtOrNaN(rawOverReconJ), fmtOrNaN(rawOverNomMom),
                        fmt(dotSep), fmt(dotRelVel));
            } else {
                String reason = "";
                if (snapA == null) reason += " snapA=unavailable";
                if (snapB == null) reason += " snapB=unavailable";
                if (snapA != null && !snapA.velocityReadValid()) reason += " snapA.velValid=false";
                if (snapB != null && !snapB.velocityReadValid()) reason += " snapB.velValid=false";
                t3Data = "T3_T6=unavailable" + reason;
            }

            if (!ExperimentLog.info(
                    "[T-3] tick={} idx={} idA={}({}) idB={}({}) forceAmountRaw={}"
                    + " nA=({},{},{}) nB=({},{},{}) pA=({},{},{}) pB=({},{},{})"
                    + " {}",
                    serverTick, i, idA, bodyTypeA, idB, bodyTypeB,
                    String.format("%.6f", forceAmountRaw),
                    String.format("%.4f", nAx), String.format("%.4f", nAy), String.format("%.4f", nAz),
                    String.format("%.4f", nBx), String.format("%.4f", nBy), String.format("%.4f", nBz),
                    String.format("%.4f", pAx), String.format("%.4f", pAy), String.format("%.4f", pAz),
                    String.format("%.4f", pBx), String.format("%.4f", pBy), String.format("%.4f", pBz),
                    t3Data)) {
                ExperimentLog.info("[T-3] tick={} rate limit hit (detail cap={} not fully reached)",
                        serverTick, T3_DETAIL_MAX);
                break;
            }
            detailEmitted++;
        }

        // T-3-MISS: when Rapier returned no contacts, scan active body pairs for
        // correlated velocity changes that indicate a hidden transfer mechanism.
        if (count == 0) {
            logT3Miss(serverTick, substepCount, lastPostSnaps, tickStartVels);
        }
    }

    // -- T-3-MISS ------------------------------------------------------------------

    // Proximity: bodies must be within this many WORLD blocks.
    private static final double T3_MISS_PROXIMITY    = 4.0;
    // Minimum |dv along pair axis| for a pair to be evaluated.
    private static final double T3_MISS_AXIS_DV_MIN  = 0.05;
    // STRONG criterion: momentum residual along pair axis below this fraction.
    // residual = |mA*dvA_n + mB*dvB_n| / max(|mA*dvA_n|, |mB*dvB_n|)
    // 0.0 = perfect conservation, 1.0 = completely non-conserved.
    private static final double T3_MISS_RESIDUAL_MAX = 0.50;

    /**
     * Pairwise delta-v scan when rawContactCount=0.
     *
     * STRONG -- opposite dv along pair axis AND residual < RESIDUAL_MAX -> [T-3-MISS-STRONG]
     * WEAK   -- same-direction (gravity, global accel) -> silently suppressed
     *
     * Requires: debug bodies ON (lastPostSnaps populated) AND debug contacts ON.
     */
    private static void logT3Miss(long serverTick, int substepCount,
                                   Map<Integer, BodySnapshot> lastPostSnaps,
                                   Map<Integer, double[]> tickStartVels) {
        if (lastPostSnaps.size() < 2) {
            if (lastPostSnaps.isEmpty()) {
                ExperimentLog.info("[T-3-MISS] tick={} skipped: no POST_STEP snapshots"
                        + " -- enable 'debug bodies on' alongside 'debug contacts on'", serverTick);
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

                double sepX = sB.posX() - sA.posX();
                double sepY = sB.posY() - sA.posY();
                double sepZ = sB.posZ() - sA.posZ();
                double dist = Math.sqrt(sepX*sepX + sepY*sepY + sepZ*sepZ);
                if (dist > T3_MISS_PROXIMITY || dist < 1e-6) continue;

                double nx = sepX/dist, ny = sepY/dist, nz = sepZ/dist;

                double dvAx = sA.linVelX() - vbA[0];
                double dvAy = sA.linVelY() - vbA[1];
                double dvAz = sA.linVelZ() - vbA[2];
                double dvBx = sB.linVelX() - vbB[0];
                double dvBy = sB.linVelY() - vbB[1];
                double dvBz = sB.linVelZ() - vbB[2];

                double dvA_n = dvAx*nx + dvAy*ny + dvAz*nz;
                double dvB_n = dvBx*nx + dvBy*ny + dvBz*nz;

                if (Math.abs(dvA_n) < T3_MISS_AXIS_DV_MIN
                        && Math.abs(dvB_n) < T3_MISS_AXIS_DV_MIN) continue;

                double mA = sA.massKpg(), mB = sB.massKpg();
                double momA_n = mA * dvA_n, momB_n = mB * dvB_n;
                double maxMom = Math.max(Math.abs(momA_n), Math.abs(momB_n));
                double residual = (maxMom > 1e-9)
                        ? Math.abs(momA_n + momB_n) / maxMom : Double.NaN;

                boolean oppositeSigns = dvA_n * dvB_n < 0;
                boolean residualLow   = !Double.isNaN(residual) && residual < T3_MISS_RESIDUAL_MAX;

                if (oppositeSigns && residualLow) {
                    if (!ExperimentLog.info(
                            "[T-3-MISS-STRONG] tick={} substepCount={} rawContactCount=0"
                            + " idA={} idB={} dist={}"
                            + " mA={}kpg dvA_n={} dvA=({},{},{})"
                            + " mB={}kpg dvB_n={} dvB=({},{},{})"
                            + " momA_n={} momB_n={} residual={}"
                            + " [opp-sign dv along pair axis; residual<{};"
                            + " clearCollisions empty; Sable-level transfer suspected]",
                            serverTick, substepCount,
                            idA, idB, fmt(dist),
                            fmt(mA), fmt(dvA_n), fmt(dvAx), fmt(dvAy), fmt(dvAz),
                            fmt(mB), fmt(dvB_n), fmt(dvBx), fmt(dvBy), fmt(dvBz),
                            fmt(momA_n), fmt(momB_n),
                            Double.isNaN(residual) ? "NaN" : fmt(residual),
                            T3_MISS_RESIDUAL_MAX)) {
                        break;
                    }
                }
                // WEAK (same-direction or high-residual): silently suppressed.
                // Gravity/global-acceleration artefacts -- not collision evidence.
            }
        }
    }

    private static String fmt(double v)      { return String.format("%.4f", v); }
    private static String fmtOrNaN(double v) { return Double.isNaN(v) ? "NaN" : String.format("%.4f", v); }

    // -- Coordinate helpers -------------------------------------------------------

    /** Rotate body-COM-local point to WORLD: world = Q * local + pos */
    private static double[] rotateAndAdd(BodySnapshot s, double lx, double ly, double lz) {
        double[] r = rotateVec(s, lx, ly, lz);
        return new double[]{r[0]+s.posX(), r[1]+s.posY(), r[2]+s.posZ()};
    }

    /** Rotate body-COM-local vector to WORLD using orientation quaternion. */
    private static double[] rotateVec(BodySnapshot s, double lx, double ly, double lz) {
        double qx = s.oriX(), qy = s.oriY(), qz = s.oriZ(), qw = s.oriW();
        // Standard quaternion rotation: q * v * q^-1
        double tx = 2*(qy*lz - qz*ly);
        double ty = 2*(qz*lx - qx*lz);
        double tz = 2*(qx*ly - qy*lx);
        return new double[]{
            lx + qw*tx + qy*tz - qz*ty,
            ly + qw*ty + qz*tx - qx*tz,
            lz + qw*tz + qx*ty - qy*tx
        };
    }

    /** v_contact ~= v_cm + omega x r  (post-step approximation) */
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
