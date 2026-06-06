package io.github.omegau371.trueimpact.observation;

/**
 * Immutable point-in-time capture of a Sable rigid body's state.
 *
 * Validity contract:
 *   comValid=false      → comX/Y/Z are 0,0,0 (MassData.getCenterOfMass() returned null)
 *   velocityReadValid=false → linVelX/Y/Z and angVelX/Y/Z are 0,0,0 (read threw or handle invalid)
 *
 * [C4-codex] massKpg is in Sable mass units (kpg); NOT assumed equal to 1 kg.
 * [T-7 pending] linVel/angVel units are UNKNOWN — do not label block/s without T-7 evidence.
 *
 * Coordinate spaces (see docs/coordinate-systems.md):
 *   comX/Y/Z    — PLOT_RELATIVE [block]
 *   posX/Y/Z    — WORLD [block]
 *   rotPtX/Y/Z  — PLOT_RELATIVE [block, inferred; see §3.2 note 1]
 *   linVelX/Y/Z — WORLD direction [IT], unit UNKNOWN [T-7]
 *   angVelX/Y/Z — WORLD direction [IT], unit UNKNOWN [T-7]
 *   oriX/Y/Z/W  — world orientation quaternion
 */
public record BodySnapshot(
        long serverTick,
        int substepIndex,
        SnapshotPhase phase,
        BodyType bodyType,
        int runtimeId,
        double massKpg,
        boolean comValid,
        double comX, double comY, double comZ,
        double posX, double posY, double posZ,
        double oriX, double oriY, double oriZ, double oriW,
        double rotPtX, double rotPtY, double rotPtZ,
        boolean velocityReadValid,
        double linVelX, double linVelY, double linVelZ,
        double angVelX, double angVelY, double angVelZ
) {}
