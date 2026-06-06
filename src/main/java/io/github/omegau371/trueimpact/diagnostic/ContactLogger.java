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
    public static void onClearCollisions(double[] data, long serverTick,
                                          int substepCount,
                                          Map<Integer, BodySnapshot> lastPostSnaps) {
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) return;

        int count = data.length / 15;

        // T-5: raw contact count — NO substep attribution claim
        ExperimentLog.info("[T-5] tick={} totalContacts={} substepCount={}" +
                " [substepIndex=UNCONFIRMED — clearCollisions covers all substeps combined]",
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
    }

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
