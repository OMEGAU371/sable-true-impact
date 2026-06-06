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
 * Executes T-4 applyForce semantics experiment.
 * Called only via explicit admin command — never auto-triggered.
 *
 * Pre-conditions checked before applying:
 *   1. Target sub-level exists and is not removed.
 *   2. Input magnitude ≤ MAX_INPUT_MAGNITUDE.
 *   3. Pre-experiment velocity |v| ≤ MAX_PRE_VELOCITY_THRESHOLD (reject if structure moving).
 *   4. Isolation WARNING: always issued — we cannot prove the structure has no contacts.
 *
 * Post-step measurement is handled by SableEventBridge.onPostStep() independently.
 * Recorded: M, inputRaw, dt, vBefore, vAfter, Δv, deltaVAlongInput, measuredMomentumAlongInput,
 *           input/(M·deltaVAlongInput), gravityErrorEst.
 */
public final class SableT4Command {

    private SableT4Command() {}

    public static int listBodies(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("[T-4] No Sable container on this level."));
            return 0;
        }
        List<ServerSubLevel> subLevels = container.getAllSubLevels();
        src.sendSuccess(() -> Component.literal("[T-4] Active sub-levels (" + subLevels.size() + "):"), false);
        for (ServerSubLevel sl : subLevels) {
            if (sl.isRemoved()) continue;
            double mass = sl.getMassTracker().getMass();
            double lvx = sl.latestLinearVelocity.x;
            double lvy = sl.latestLinearVelocity.y;
            double lvz = sl.latestLinearVelocity.z;
            double speed = Math.sqrt(lvx*lvx + lvy*lvy + lvz*lvz);
            final int id = sl.getRuntimeId();
            final double m = mass, s = speed;
            src.sendSuccess(() -> Component.literal(
                    String.format("  id=%d  mass=%.3f kpg  latestSpeed=%.3f", id, m, s)), false);
        }
        return subLevels.size();
    }

    /**
     * @param src  command source (op level 4 required — enforced by DiagnosticCommand)
     * @param runtimeId  target sub-level runtime ID (use 'bodies' to list)
     * @param fx/fy/fz   input vector (kpg·block/s equivalent); magnitude hard-capped
     */
    public static int applyForExperiment(CommandSourceStack src,
                                          int runtimeId, double fx, double fy, double fz) {
        ServerLevel level = src.getLevel();

        // ── 0. Finite input guard — must precede any arithmetic ──────────────
        if (!InputVectorGuard.isFiniteInput(fx, fy, fz)) {
            src.sendFailure(Component.literal(
                    "[T-4] Input contains NaN or Infinity — rejected."));
            return 0;
        }

        // ── 1. Input magnitude check ─────────────────────────────────────────
        double inputMag = Math.sqrt(fx*fx + fy*fy + fz*fz);
        if (inputMag > T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE) {
            src.sendFailure(Component.literal(String.format(
                    "[T-4] Input magnitude %.2f exceeds hard limit %.1f. Reduce input.",
                    inputMag, T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE)));
            return 0;
        }
        if (inputMag < 1e-9) {
            src.sendFailure(Component.literal("[T-4] Input is zero vector — nothing to measure."));
            return 0;
        }

        // ── 2. Find target sub-level ─────────────────────────────────────────
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("[T-4] No Sable container on this level."));
            return 0;
        }
        ServerSubLevel target = null;
        for (ServerSubLevel sl : container.getAllSubLevels()) {
            if (!sl.isRemoved() && sl.getRuntimeId() == runtimeId) { target = sl; break; }
        }
        if (target == null) {
            src.sendFailure(Component.literal("[T-4] No active sub-level with runtimeId=" + runtimeId));
            return 0;
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (system == null) {
            src.sendFailure(Component.literal("[T-4] PhysicsSystem not available."));
            return 0;
        }

        // ── 3. Pre-velocity check ────────────────────────────────────────────
        var handle = system.getPhysicsHandle(target);
        Vector3d vBefore = new Vector3d();
        boolean velValid = false;
        try {
            if (handle != null && handle.isValid()) {
                handle.getLinearVelocity(vBefore);
                velValid = true;
            }
        } catch (Exception ignored) {}

        double preSpeed = vBefore.length();
        if (preSpeed > T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD) {
            src.sendFailure(Component.literal(String.format(
                    "[T-4] REJECTED: structure is not at rest (|v|=%.3f > threshold %.1f). " +
                    "Wait for it to stop before running experiment.",
                    preSpeed, T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD)));
            return 0;
        }
        if (!velValid) {
            src.sendSuccess(() -> Component.literal(
                    "[T-4] WARNING: pre-velocity read failed. " +
                    "vBefore will be recorded as (0,0,0) — result may be invalid."), true);
        }

        // ── 4. COM check ─────────────────────────────────────────────────────
        var com = target.getMassTracker().getCenterOfMass();
        if (com == null) {
            src.sendFailure(Component.literal("[T-4] Sub-level COM is null — cannot apply at COM."));
            return 0;
        }

        // ── 5. Isolation WARNING ──────────────────────────────────────────────
        // Cannot programmatically prove the structure has no contacts.
        final int idFinal = runtimeId;
        final double gravEstFinal = target.getMassTracker().getMass() * 0.08 *
                (0.05 / system.getConfig().substepsPerTick);
        src.sendSuccess(() -> Component.literal(String.format(
                "[T-4] ISOLATION WARNING: Cannot confirm structure id=%d has no contacts. " +
                "Contact forces and gravity (≈%.4f kpg·block/s per substep) are error sources. " +
                "Run in a position where the structure is free-falling or clearly isolated.",
                idFinal, gravEstFinal)), true);

        double mass = target.getMassTracker().getMass();
        double dt = 0.05 / system.getConfig().substepsPerTick;
        String levelKey = level.dimension().location().toString();
        String key = T4ApplyForceExperiment.key(levelKey, runtimeId);

        // ── 6. Check no duplicate pending for same key ───────────────────────
        if (T4ApplyForceExperiment.pendingByKey.containsKey(key)) {
            src.sendFailure(Component.literal(
                    "[T-4] Experiment for id=" + runtimeId + " in " + levelKey +
                    " already pending. Wait for result or run /trueimpact debug all off to clear."));
            return 0;
        }

        // ── 7. Apply impulse at COM ───────────────────────────────────────────
        system.getPipeline().applyImpulse(target, com, new Vector3d(fx, fy, fz));

        // ── 8. Store pending for onPostStep measurement ───────────────────────
        T4ApplyForceExperiment.pendingByKey.put(key, new T4ApplyForceExperiment.Pending(
                levelKey, runtimeId, fx, fy, fz,
                vBefore.x, vBefore.y, vBefore.z,
                mass, dt, level.getGameTime()
        ));
        // Diagnostic: log exactly what key was stored so onPostStep mismatch can be diagnosed
        io.github.omegau371.trueimpact.diagnostic.ExperimentLog.info(
                "[T-4] PENDING stored: key='{}' levelKey='{}' runtimeId={} pendingMapSize={}",
                key, levelKey, runtimeId, T4ApplyForceExperiment.pendingByKey.size());

        final double vbx = vBefore.x, vby = vBefore.y, vbz = vBefore.z;
        final double fxF = fx, fyF = fy, fzF = fz, magF = inputMag, mF = mass;
        final boolean velValidF = velValid;
        src.sendSuccess(() -> Component.literal(String.format(
                "[T-4] Applied input=(%.3f,%.3f,%.3f) |input|=%.3f to id=%d (M=%.3f kpg). " +
                "vBefore=(%.4f,%.4f,%.4f) velValid=%b. " +
                "Results will be logged on next POST_STEP. " +
                "DO NOT move the structure manually before POST_STEP fires.",
                fxF, fyF, fzF, magF, idFinal, mF, vbx, vby, vbz, velValidF)), false);
        return 1;
    }
}
