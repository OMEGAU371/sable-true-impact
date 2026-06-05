package io.github.omegau371.trueimpact.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.BodyType;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;
import org.joml.Vector3d;

/**
 * Reads ServerSubLevel properties into immutable BodySnapshot instances.
 * This class references Sable API directly — only loaded when Sable is present.
 */
public final class SableBodyReader {

    private SableBodyReader() {}

    /**
     * Creates a BodySnapshot from the current state of a ServerSubLevel.
     * Safe to call from ForgeSablePrePhysicsTickEvent and ForgeSablePostPhysicsTickEvent handlers.
     */
    public static BodySnapshot snapshot(
            ServerSubLevel subLevel,
            SubLevelPhysicsSystem system,
            long serverTick,
            int substepIndex,
            SnapshotPhase phase
    ) {
        MassData mass = subLevel.getMassTracker();
        Pose3d pose = subLevel.logicalPose();

        double comX = 0, comY = 0, comZ = 0;
        if (mass.getCenterOfMass() != null) {
            comX = mass.getCenterOfMass().x();
            comY = mass.getCenterOfMass().y();
            comZ = mass.getCenterOfMass().z();
        }

        double linX = 0, linY = 0, linZ = 0;
        double angX = 0, angY = 0, angZ = 0;
        try {
            RigidBodyHandle handle = system.getPhysicsHandle(subLevel);
            if (handle != null && handle.isValid()) {
                Vector3d lv = handle.getLinearVelocity(new Vector3d());
                Vector3d av = handle.getAngularVelocity(new Vector3d());
                linX = lv.x; linY = lv.y; linZ = lv.z;
                angX = av.x; angY = av.y; angZ = av.z;
            }
        } catch (Exception ignored) {
            // pipeline may reject velocity reads mid-step; record zeros
        }

        return new BodySnapshot(
                serverTick,
                substepIndex,
                phase,
                BodyType.ACTIVE_SUBLEVEL,
                subLevel.getRuntimeId(),
                mass.getMass(),
                comX, comY, comZ,
                pose.position().x(), pose.position().y(), pose.position().z(),
                pose.orientation().x(), pose.orientation().y(),
                pose.orientation().z(), pose.orientation().w(),
                pose.rotationPoint().x(), pose.rotationPoint().y(), pose.rotationPoint().z(),
                linX, linY, linZ,
                angX, angY, angZ
        );
    }

    /** @return substep index (0-based) derived from getPartialPhysicsTick(). */
    public static int substepIndex(SubLevelPhysicsSystem system) {
        int substepCount = system.getConfig().substepsPerTick;
        double partial = system.getPartialPhysicsTick();
        return Math.max(0, (int) Math.round(partial * substepCount) - 1);
    }

    /** @return substep timestep in seconds. */
    public static double substepDt(SubLevelPhysicsSystem system) {
        return 0.05 / system.getConfig().substepsPerTick;
    }
}
