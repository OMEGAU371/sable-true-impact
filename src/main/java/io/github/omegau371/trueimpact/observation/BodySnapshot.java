package io.github.omegau371.trueimpact.observation;

/**
 * Immutable point-in-time capture of a Sable rigid body's state.
 *
 * Coordinate spaces (see docs/coordinate-systems.md):
 *   comX/Y/Z        — PLOT_RELATIVE [block], MassData.getCenterOfMass()
 *   posX/Y/Z        — WORLD [block], logicalPose().position()
 *   rotPtX/Y/Z      — PLOT_RELATIVE [block], logicalPose().rotationPoint() [inferred, see study §3.2]
 *   linVelX/Y/Z     — unit UNKNOWN, pipeline.getLinearVelocity() raw native value (T-7 pending)
 *   angVelX/Y/Z     — unit UNKNOWN, pipeline.getAngularVelocity() raw native value (T-7 pending)
 *   oriX/Y/Z/W      — orientation quaternion (WORLD rotation)
 *
 * massKpg: Sable mass unit (kpg), default 1.0 per solid block.
 *          [C4] NOT assumed to equal 1 kg; absolute SI mapping unconfirmed.
 */
public record BodySnapshot(
        long serverTick,
        int substepIndex,
        SnapshotPhase phase,
        BodyType bodyType,
        int runtimeId,
        double massKpg,
        double comX, double comY, double comZ,
        double posX, double posY, double posZ,
        double oriX, double oriY, double oriZ, double oriW,
        double rotPtX, double rotPtY, double rotPtZ,
        double linVelX, double linVelY, double linVelZ,
        double angVelX, double angVelY, double angVelZ
) {}
