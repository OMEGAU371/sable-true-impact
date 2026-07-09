package io.github.omegau371.trueimpact.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.BodyType;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;
import org.joml.Vector3d;

/**
 * Reads ServerSubLevel properties into BodySnapshot records.
 * Captures validity flags: comValid and velocityReadValid.
 *
 * [validity contract] When comValid=false, comX/Y/Z=0.0 (not real measurements).
 * [validity contract] When velocityReadValid=false, linVel/angVel=0.0 (not real measurements).
 * Read failures are rate-limited warn-logged, not silently swallowed as 0.
 */
public final class SableBodyReader {

    private SableBodyReader() {}

    public static BodySnapshot snapshot(
            ServerSubLevel subLevel,
            SubLevelPhysicsSystem system,
            long serverTick,
            int substepIndex,
            SnapshotPhase phase
    ) {
        MassData mass = subLevel.getMassTracker();
        Pose3d pose = subLevel.logicalPose();

        // COM validity
        boolean comValid = mass.getCenterOfMass() != null;
        double comX = 0, comY = 0, comZ = 0;
        if (comValid) {
            comX = mass.getCenterOfMass().x();
            comY = mass.getCenterOfMass().y();
            comZ = mass.getCenterOfMass().z();
        } else if (DiagnosticConfig.ENABLED) {
            ExperimentLog.warn("[SNAP] id={} tick={} comValid=false (getCenterOfMass() null)",
                    subLevel.getRuntimeId(), serverTick);
        }

        // Velocity validity
        double linX = 0, linY = 0, linZ = 0;
        double angX = 0, angY = 0, angZ = 0;
        boolean velocityReadValid = false;
        try {
            RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
            if (handle != null && handle.isValid()) {
                Vector3d lv = handle.getLinearVelocity(new Vector3d());
                Vector3d av = handle.getAngularVelocity(new Vector3d());
                linX = lv.x; linY = lv.y; linZ = lv.z;
                angX = av.x; angY = av.y; angZ = av.z;
                velocityReadValid = true;
            } else if (DiagnosticConfig.ENABLED) {
                ExperimentLog.warn("[SNAP] id={} tick={} velocityReadValid=false (handle null/invalid)",
                        subLevel.getRuntimeId(), serverTick);
            }
        } catch (Exception e) {
            if (DiagnosticConfig.ENABLED) {
                ExperimentLog.warn("[SNAP] id={} tick={} velocityReadValid=false ({})",
                        subLevel.getRuntimeId(), serverTick, e.getClass().getSimpleName());
            }
        }

        return new BodySnapshot(
                serverTick, substepIndex, phase, BodyType.ACTIVE_SUBLEVEL,
                subLevel.getRuntimeId(),
                mass.getMass(),
                comValid, comX, comY, comZ,
                pose.position().x(), pose.position().y(), pose.position().z(),
                pose.orientation().x(), pose.orientation().y(),
                pose.orientation().z(), pose.orientation().w(),
                pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z(),
                subLevel.getPlot().getCenterBlock().getX(),
                subLevel.getPlot().getCenterBlock().getY(),
                subLevel.getPlot().getCenterBlock().getZ(),
                velocityReadValid, linX, linY, linZ, angX, angY, angZ
        );
    }

    /** Substep index (0-based) derived from getPartialPhysicsTick(). */
    public static int substepIndex(SubLevelPhysicsSystem system) {
        int n = system.getConfig().substepsPerTick;
        double partial = system.getPartialPhysicsTick();
        return Math.max(0, (int) Math.round(partial * n) - 1);
    }

    /** Substep duration in seconds. */
    public static double substepDt(SubLevelPhysicsSystem system) {
        return 0.05 / system.getConfig().substepsPerTick;
    }
}
