package io.github.omegau371.trueimpact.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.List;

/**
 * Executes the T-4 applyForce semantics experiment.
 * Called only from DiagnosticCommand — never auto-triggered.
 * Requires operator permission (enforced by DiagnosticCommand).
 */
public final class SableT4Command {

    private SableT4Command() {}

    /**
     * Find and print all currently active sub-levels (for choosing a runtimeId).
     */
    public static int listBodies(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("No Sable container on this level."));
            return 0;
        }
        List<ServerSubLevel> subLevels = container.getAllSubLevels();
        src.sendSuccess(() -> Component.literal("Active sub-levels (" + subLevels.size() + "):"), false);
        for (ServerSubLevel sl : subLevels) {
            if (sl.isRemoved()) continue;
            double mass = sl.getMassTracker().getMass();
            double vx = sl.latestLinearVelocity.x;
            double vy = sl.latestLinearVelocity.y;
            double vz = sl.latestLinearVelocity.z;
            src.sendSuccess(() -> Component.literal(
                    String.format("  id=%d mass=%.3f kpg latestVel=(%.3f,%.3f,%.3f)",
                            sl.getRuntimeId(), mass, vx, vy, vz)
            ), false);
        }
        return subLevels.size();
    }

    /**
     * Apply a known impulse vector to the specified sub-level at its center of mass.
     * Records vBefore, stores T4Pending for the next POST_STEP measurement.
     *
     * [SAFETY] Prints a warning that this WILL change the structure's motion.
     * [SAFETY] Only applies when runtimeId matches an existing, non-removed sub-level.
     * [T-4 requirement] input/(M*Δv) ratio is computed by SableEventBridge on next POST_STEP.
     */
    public static int applyForExperiment(CommandSourceStack src,
                                         int runtimeId, double fx, double fy, double fz) {
        ServerLevel level = src.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("No Sable container on this level."));
            return 0;
        }

        ServerSubLevel target = null;
        for (ServerSubLevel sl : container.getAllSubLevels()) {
            if (!sl.isRemoved() && sl.getRuntimeId() == runtimeId) {
                target = sl;
                break;
            }
        }

        if (target == null) {
            src.sendFailure(Component.literal("No active sub-level with runtimeId=" + runtimeId));
            return 0;
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (system == null) {
            src.sendFailure(Component.literal("PhysicsSystem not available."));
            return 0;
        }

        double mass = target.getMassTracker().getMass();
        double dt = SableBodyReader.substepDt(system);

        // Read vBefore via Rapier getLinearVelocity
        var handle = system.getPhysicsHandle(target);
        Vector3d vBefore = new Vector3d();
        if (handle != null && handle.isValid()) {
            handle.getLinearVelocity(vBefore);
        }

        // WARN the user — this WILL change the structure's motion
        final double fxF = fx, fyF = fy, fzF = fz;
        final double vbxF = vBefore.x, vbyF = vBefore.y, vbzF = vBefore.z;
        final double massF = mass;
        src.sendSuccess(() -> Component.literal(
                "[T-4 WARNING] Applying input (" + fxF + "," + fyF + "," + fzF +
                ") to sub-level id=" + runtimeId +
                " (mass=" + String.format("%.3f", massF) + " kpg). " +
                "This WILL change the structure's motion. " +
                "vBefore=("+String.format("%.4f",vbxF)+","+
                String.format("%.4f",vbyF)+","+String.format("%.4f",vbzF)+")"
        ), true);

        // Apply impulse at center of mass (position = COM)
        var com = target.getMassTracker().getCenterOfMass();
        if (com == null) {
            src.sendFailure(Component.literal("Sub-level COM is null — cannot apply."));
            return 0;
        }
        system.getPipeline().applyImpulse(target, com, new Vector3d(fx, fy, fz));

        // Store pending T-4 measurement
        T4ApplyForceExperiment.pending = new T4ApplyForceExperiment.Pending(
                runtimeId, fx, fy, fz,
                vBefore.x, vBefore.y, vBefore.z,
                mass, dt, level.getGameTime()
        );

        src.sendSuccess(() -> Component.literal(
                "[T-4] Experiment applied. Results will be logged on next POST_STEP for id=" + runtimeId
        ), false);
        return 1;
    }
}
