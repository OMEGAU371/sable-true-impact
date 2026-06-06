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
     * @param data           raw double[N*15] from clearCollisions
     * @param serverTick     level.getGameTime()
     * @param substepCount   configured substeps per tick (for T-5 annotation)
     * @param lastPostSnaps  most-recent POST_STEP snapshots, keyed by runtimeId
     *                       (from SableEventBridge — may be empty if LOG_BODY_SNAPSHOTS was off)
     */
    /**
     * @param tickStartVels  linVel captured at substep-0 PRE_STEP; used by T-3-MISS.
     *                       Empty if LOG_RAW_CONTACTS was off before first substep, or
     *                       if debug bodies was off (caller still provides the map).
     */
    public static void onClearCollisions(double[] data, long serverTick,
                                          int substepCount,
                                          Map<Integer, BodySnapshot> lastPostSnaps,
                                          Map<Integer, double[]> tickStartVels) {
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) return;

        int count = data.length / 15;

        // T-5: raw count from Rapier3D.clearCollisions() — TI applies NO filter here.
        // Sable's forceAmountRaw > 25*min(mass) filter runs in processCollisionEffects AFTER this.
        // If count=0, Rapier itself returned an empty array; sub-level vs sub-level pairs
        // may never appear here if Sable configures Rapier collision groups to exclude them.
        ExperimentLog.info("[T-5] tick={} rawContactCount={} substepCount={}" +
                " [raw Rapier3D.clearCollisions output; TI applies no filter;" +
                " sub-level vs sub-level contacts may not exist in this array]",
                serverTick, count, substepCount);

        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int idA = (int) data[base];
            int idB = (int) data[base + 1];
            double forceAmountRaw = data[base + 2];

            double nAx = data[base+3], nAy = data[base+4], nAz = data[base+5];
            double nBx = data[base+6], nBy = data[base+7], nBz = data[base+8];
            double pAx = data[base+9], pAy = data[base+10], pAz = data[base+11];
            double pBx = data[base+12], pBy = data[base+13], pBz = data[base+14];

            // T-3: correlate with last known body snapshots
            // [C2] Body not in snapshots → NON_ACTIVE_SUBLEVEL_BODY (NOT proven terrain)
            BodySnapshot snapA = lastPostSnaps.get(idA);
            BodySnapshot snapB = lastPostSnaps.get(idB);
            String bodyTypeA = (snapA != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";
            String bodyTypeB = (snapB != null) ? "ACTIVE_SUBLEVEL" : "NON_ACTIVE_SUBLEVEL_BODY";

            // T-3: normal relative velocity and momentum change (only if both snaps available)
            // Snapshot timing: LAST SUBSTEP POST_STEP — may not match contact substep [T-5 pending]
            String t3Data;
            if (snapA != null && snapB != null
                    && snapA.velocityReadValid() && snapB.velocityReadValid()) {
                // Contact point A in world: world = Q_A * localPointA + posA
                double[] wPA = rotateAndAdd(snapA, pAx, pAy, pAz);
                // Velocity at contact point (post-step approx): v_cm + ω × r
                // r_A = wPA - posA = Q_A * localPointA (already computed)
                double[] vPA = contactPointVelocity(snapA, wPA[0]-snapA.posX(), wPA[1]-snapA.posY(), wPA[2]-snapA.posZ());
                double[] vPB = contactPointVelocity(snapB,
                        rotateAndAdd(snapB, pBx, pBy, pBz)[0]-snapB.posX(),
                        rotateAndAdd(snapB, pBx, pBy, pBz)[1]-snapB.posY(),
                        rotateAndAdd(snapB, pBx, pBy, pBz)[2]-snapB.posZ());
                // Normal in world (using A's normal, direction [UC: T-6])
                double[] nWorld = rotateVec(snapA, nAx, nAy, nAz);
                double nMag = Math.sqrt(nWorld[0]*nWorld[0]+nWorld[1]*nWorld[1]+nWorld[2]*nWorld[2]);
                if (nMag > 1e-9) { nWorld[0]/=nMag; nWorld[1]/=nMag; nWorld[2]/=nMag; }
                // Closing velocity along normal
                double relVx = vPA[0]-vPB[0], relVy = vPA[1]-vPB[1], relVz = vPA[2]-vPB[2];
                double closingVel = relVx*nWorld[0]+relVy*nWorld[1]+relVz*nWorld[2];
                double massA = snapA.massKpg(), massB = snapB.massKpg();
                double kA = (massA > 0) ? 1.0/massA : 0;
                double kB = (massB > 0) ? 1.0/massB : 0;
                double mEff = (kA + kB > 0) ? 1.0/(kA+kB) : Double.NaN;
                double normalMomChange = Double.isNaN(mEff) ? Double.NaN
                        : mEff * Math.abs(closingVel);
                // T-6: dot products for normal direction analysis
                double[] wPB = rotateAndAdd(snapB, pBx, pBy, pBz);
                double bMinusAx = wPB[0]-wPA[0], bMinusAy = wPB[1]-wPA[1], bMinusAz = wPB[2]-wPA[2];
                double dotSep = nWorld[0]*bMinusAx + nWorld[1]*bMinusAy + nWorld[2]*bMinusAz;
                double dotRelVel = nWorld[0]*relVx + nWorld[1]*relVy + nWorld[2]*relVz;
                t3Data = String.format("closingVel=%.4f normalMomChange=%.6f mEff=%.4f" +
                        " [T-6] dot(nA_world,wPointB-wPointA)=%.4f dot(nA_world,relVel)=%.4f" +
                        " [snapshot timing=LAST_SUBSTEP_POST, NOT exact contact substep]",
                        closingVel, Double.isNaN(normalMomChange) ? Double.NaN : normalMomChange,
                        Double.isNaN(mEff) ? Double.NaN : mEff, dotSep, dotRelVel);
            } else {
                String reason = "";
                if (snapA == null) reason += " snapA=unavailable(NON_ACTIVE_SUBLEVEL)";
                if (snapB == null) reason += " snapB=unavailable(NON_ACTIVE_SUBLEVEL)";
                if (snapA != null && !snapA.velocityReadValid()) reason += " snapA.velValid=false";
                if (snapB != null && !snapB.velocityReadValid()) reason += " snapB.velValid=false";
                t3Data = "T3_T6=unavailable" + reason;
            }

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

        // T-3-MISS: when Rapier returned no contacts, scan active body pairs for
        // correlated velocity changes that indicate a hidden transfer mechanism.
        if (count == 0) {
            logT3Miss(serverTick, substepCount, lastPostSnaps, tickStartVels);
        }
    }

    // ── T-3-MISS ─────────────────────────────────────────────────────────────

    // Proximity threshold: bodies must be within this many WORLD blocks to be scanned.
    private static final double T3_MISS_PROXIMITY = 20.0;
    // Minimum Δv magnitude to report (filters ticks with no meaningful velocity change).
    private static final double T3_MISS_DV_MIN = 0.05;

    /**
     * Pairwise Δv scan when rawContactCount=0.
     *
     * For every pair of active bodies within T3_MISS_PROXIMITY blocks (world distance),
     * if either body has |Δv| ≥ T3_MISS_DV_MIN, emit [T-3-MISS] with full kinematic data.
     *
     * Δv = vAfter (lastPostSnaps linVel) − vBefore (tickStartVels, substep-0 PRE_STEP).
     *
     * Requires: debug bodies ON (lastPostSnaps populated) AND LOG_RAW_CONTACTS ON.
     * If lastPostSnaps is empty, a single warning is emitted and the scan is skipped.
     */
    private static void logT3Miss(long serverTick, int substepCount,
                                   Map<Integer, BodySnapshot> lastPostSnaps,
                                   Map<Integer, double[]> tickStartVels) {
        if (lastPostSnaps.size() < 2) {
            if (!lastPostSnaps.isEmpty()) return; // only 1 body, no pairs
            // 0 bodies: snapshots unavailable (debug bodies off?)
            ExperimentLog.info("[T-3-MISS] tick={} skipped: lastPostSnaps empty" +
                    " — enable 'debug bodies on' alongside 'debug contacts on'", serverTick);
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
                if (vbA == null || vbB == null) continue; // tick-start not captured

                // World-space distance between body origins (logicalPose position)
                double dx = sA.posX() - sB.posX();
                double dy = sA.posY() - sB.posY();
                double dz = sA.posZ() - sB.posZ();
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (dist > T3_MISS_PROXIMITY) continue;

                // Δv: POST_STEP linVel − substep-0 PRE_STEP linVel (full tick Δv)
                double dvAx = sA.linVelX() - vbA[0];
                double dvAy = sA.linVelY() - vbA[1];
                double dvAz = sA.linVelZ() - vbA[2];
                double dvBx = sB.linVelX() - vbB[0];
                double dvBy = sB.linVelY() - vbB[1];
                double dvBz = sB.linVelZ() - vbB[2];
                double dvAMag = Math.sqrt(dvAx*dvAx + dvAy*dvAy + dvAz*dvAz);
                double dvBMag = Math.sqrt(dvBx*dvBx + dvBy*dvBy + dvBz*dvBz);
                if (dvAMag < T3_MISS_DV_MIN && dvBMag < T3_MISS_DV_MIN) continue;

                if (!ExperimentLog.info(
                        "[T-3-MISS] tick={} substepCount={} rawContactCount=0 idA={} idB={} dist={}" +
                        " massA={}kpg vBeforeA=({},{},{}) vAfterA=({},{},{}) deltaVA=({},{},{}) |deltaVA|={}" +
                        " massB={}kpg vBeforeB=({},{},{}) vAfterB=({},{},{}) deltaVB=({},{},{}) |deltaVB|={}" +
                        " [clearCollisions empty; transfer likely via Sable-level mechanism," +
                        " NOT Rapier contact manifold; sub-level vs sub-level collision groups unconfirmed]",
                        serverTick, substepCount, idA, idB, fmt(dist),
                        fmt(sA.massKpg()), fmt(vbA[0]), fmt(vbA[1]), fmt(vbA[2]),
                        fmt(sA.linVelX()), fmt(sA.linVelY()), fmt(sA.linVelZ()),
                        fmt(dvAx), fmt(dvAy), fmt(dvAz), fmt(dvAMag),
                        fmt(sB.massKpg()), fmt(vbB[0]), fmt(vbB[1]), fmt(vbB[2]),
                        fmt(sB.linVelX()), fmt(sB.linVelY()), fmt(sB.linVelZ()),
                        fmt(dvBx), fmt(dvBy), fmt(dvBz), fmt(dvBMag))) {
                    break; // rate limit hit
                }
            }
        }
    }

    private static String fmt(double v) { return String.format("%.4f", v); }

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
