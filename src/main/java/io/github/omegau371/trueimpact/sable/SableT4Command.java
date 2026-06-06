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
import org.joml.Vector3dc;

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

    /**
     * Lists all active sub-levels with both latestLinearVelocity (cached) and
     * handle.getLinearVelocity() (authoritative — same source the t4 apply rejection uses).
     * t4Ready=true means handleSpeed is within MAX_PRE_VELOCITY_THRESHOLD.
     */
    public static int listBodies(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("[T-4] No Sable container on this level."));
            return 0;
        }
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        List<ServerSubLevel> subLevels = container.getAllSubLevels();

        int active = 0;
        for (ServerSubLevel sl : subLevels) { if (!sl.isRemoved()) active++; }
        final int activeCount = active;
        src.sendSuccess(() -> Component.literal(
                "[T-4] Active sub-levels (" + activeCount + ")" +
                " — handleSpeed is the authoritative velocity used by t4 apply:"), false);

        for (ServerSubLevel sl : subLevels) {
            if (sl.isRemoved()) continue;

            final int id = sl.getRuntimeId();
            final double mass = sl.getMassTracker().getMass();
            final double lvx = sl.latestLinearVelocity.x;
            final double lvy = sl.latestLinearVelocity.y;
            final double lvz = sl.latestLinearVelocity.z;
            final double latestSpeed = Math.sqrt(lvx*lvx + lvy*lvy + lvz*lvz);

            // Authoritative handle velocity — same read path as applyForExperiment pre-check
            double _hx = 0, _hy = 0, _hz = 0;
            boolean _handleValid = false;
            if (system != null) {
                try {
                    var handle = system.getPhysicsHandle(sl);
                    if (handle != null && handle.isValid()) {
                        var hv = handle.getLinearVelocity(new Vector3d());
                        _hx = hv.x; _hy = hv.y; _hz = hv.z;
                        _handleValid = true;
                    }
                } catch (Exception ignored) {}
            }
            final double hx = _hx, hy = _hy, hz = _hz;
            final boolean handleValid = _handleValid;
            final double handleSpeed = handleValid ? Math.sqrt(hx*hx + hy*hy + hz*hz) : Double.NaN;
            final boolean t4Ready = handleValid
                    && handleSpeed <= T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD;

            src.sendSuccess(() -> {
                String hStr = handleValid
                        ? String.format("handleSpeed=%.3f handleVel=(%.3f,%.3f,%.3f)", handleSpeed, hx, hy, hz)
                        : "handleSpeed=invalid";
                return Component.literal(String.format(
                        "  id=%d  mass=%.3f kpg  latestSpeed=%.3f  %s  handleValid=%b  t4Ready=%b",
                        id, mass, latestSpeed, hStr, handleValid, t4Ready));
            }, false);
        }
        return activeCount;
    }

    /**
     * Detailed single-body readout: mass, latestVel, handleVel, pose, COM, t4Ready.
     * Use before t4 apply to confirm exact handle velocity and isolation state.
     */
    public static int inspectBody(CommandSourceStack src, int runtimeId) {
        ServerLevel level = src.getLevel();
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

        final int id = runtimeId;
        final double mass = target.getMassTracker().getMass();

        // latestLinearVelocity (cached, may lag)
        final double lvx = target.latestLinearVelocity.x;
        final double lvy = target.latestLinearVelocity.y;
        final double lvz = target.latestLinearVelocity.z;
        final double latestSpeed = Math.sqrt(lvx*lvx + lvy*lvy + lvz*lvz);

        // handle velocity (authoritative — same path as t4 apply)
        double _hx = 0, _hy = 0, _hz = 0;
        boolean _handleValid = false;
        try {
            var handle = system.getPhysicsHandle(target);
            if (handle != null && handle.isValid()) {
                var hv = handle.getLinearVelocity(new Vector3d());
                _hx = hv.x; _hy = hv.y; _hz = hv.z;
                _handleValid = true;
            }
        } catch (Exception ignored) {}
        final double hx = _hx, hy = _hy, hz = _hz;
        final boolean handleValid = _handleValid;
        final double handleSpeed = handleValid ? Math.sqrt(hx*hx + hy*hy + hz*hz) : Double.NaN;
        final boolean t4Ready = handleValid
                && handleSpeed <= T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD;

        // Logical pose
        var pose = target.logicalPose();
        final double px = pose.position().x(), py = pose.position().y(), pz = pose.position().z();
        final double ox = pose.orientation().x(), oy = pose.orientation().y();
        final double oz = pose.orientation().z(), ow = pose.orientation().w();

        // Center of mass
        var comData = target.getMassTracker().getCenterOfMass();
        final boolean comValid = comData != null;
        final double cx = comValid ? comData.x() : 0;
        final double cy = comValid ? comData.y() : 0;
        final double cz = comValid ? comData.z() : 0;

        src.sendSuccess(() -> Component.literal(String.format(
                "[T-4 inspect] id=%d  mass=%.4f kpg", id, mass)), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "  latestVel=(%.4f,%.4f,%.4f)  |latest|=%.4f", lvx, lvy, lvz, latestSpeed)), false);
        src.sendSuccess(() -> {
            String hStr = handleValid
                    ? String.format("(%.4f,%.4f,%.4f)  |handle|=%.4f", hx, hy, hz, handleSpeed)
                    : "invalid";
            return Component.literal(String.format(
                    "  handleVel=%s  handleValid=%b", hStr, handleValid));
        }, false);
        src.sendSuccess(() -> Component.literal(String.format(
                "  pos=(%.4f,%.4f,%.4f)", px, py, pz)), false);
        src.sendSuccess(() -> Component.literal(String.format(
                "  ori=(%.4f,%.4f,%.4f,%.4f) [xyzw]", ox, oy, oz, ow)), false);
        src.sendSuccess(() -> {
            String cStr = comValid ? String.format("(%.4f,%.4f,%.4f)", cx, cy, cz) : "invalid";
            return Component.literal("  com=" + cStr + "  comValid=" + comValid);
        }, false);
        src.sendSuccess(() -> Component.literal(String.format(
                "  t4Ready=%b  [threshold=%.1f — handleSpeed is what t4 apply checks]",
                t4Ready, T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD)), false);
        return 1;
    }

    // ── Shared pre-experiment validation ────────────────────────────────────

    /** All state needed after pre-checks pass. */
    private record ValidatedT4(
            ServerLevel level,
            ServerSubLevel target,
            SubLevelPhysicsSystem system,
            Vector3d vBefore,
            boolean velValid,
            Vector3dc com,       // getCenterOfMass() — non-null (checked by validate)
            double mass,
            double dt,
            String levelKey,
            String key,
            double inputMag
    ) {}

    /**
     * Runs all pre-experiment checks common to every apply variant.
     * Returns null and sends failure if any check fails.
     */
    private static ValidatedT4 validate(CommandSourceStack src,
                                         int runtimeId, double fx, double fy, double fz) {
        // ── 0. Finite input guard ────────────────────────────────────────────
        if (!InputVectorGuard.isFiniteInput(fx, fy, fz)) {
            src.sendFailure(Component.literal("[T-4] Input contains NaN or Infinity — rejected."));
            return null;
        }

        // ── 1. Input magnitude check ─────────────────────────────────────────
        double inputMag = Math.sqrt(fx*fx + fy*fy + fz*fz);
        if (inputMag > T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE) {
            src.sendFailure(Component.literal(String.format(
                    "[T-4] Input magnitude %.2f exceeds hard limit %.1f. Reduce input.",
                    inputMag, T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE)));
            return null;
        }
        if (inputMag < 1e-9) {
            src.sendFailure(Component.literal("[T-4] Input is zero vector — nothing to measure."));
            return null;
        }

        // ── 2. Find target sub-level ─────────────────────────────────────────
        ServerLevel level = src.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            src.sendFailure(Component.literal("[T-4] No Sable container on this level."));
            return null;
        }
        ServerSubLevel target = null;
        for (ServerSubLevel sl : container.getAllSubLevels()) {
            if (!sl.isRemoved() && sl.getRuntimeId() == runtimeId) { target = sl; break; }
        }
        if (target == null) {
            src.sendFailure(Component.literal("[T-4] No active sub-level with runtimeId=" + runtimeId));
            return null;
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        if (system == null) {
            src.sendFailure(Component.literal("[T-4] PhysicsSystem not available."));
            return null;
        }

        // ── 3. Pre-velocity check (authoritative handle read) ────────────────
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
            return null;
        }
        if (!velValid) {
            src.sendSuccess(() -> Component.literal(
                    "[T-4] WARNING: pre-velocity read failed. " +
                    "vBefore will be recorded as (0,0,0) — result may be invalid."), true);
        }

        // ── 4. COM check ─────────────────────────────────────────────────────
        var com = target.getMassTracker().getCenterOfMass();
        if (com == null) {
            src.sendFailure(Component.literal("[T-4] Sub-level COM is null."));
            return null;
        }

        // ── 5. Duplicate pending check ────────────────────────────────────────
        String levelKey = level.dimension().location().toString();
        String key = T4ApplyForceExperiment.key(levelKey, runtimeId);
        if (T4ApplyForceExperiment.pendingByKey.containsKey(key)) {
            src.sendFailure(Component.literal(
                    "[T-4] Experiment for id=" + runtimeId + " in " + levelKey +
                    " already pending. Wait for result or run /trueimpact debug all off to clear."));
            return null;
        }

        double mass = target.getMassTracker().getMass();
        double dt = 0.05 / system.getConfig().substepsPerTick;
        return new ValidatedT4(level, target, system, vBefore, velValid, com, mass, dt, levelKey, key, inputMag);
    }

    /** Sends the isolation warning — common to all apply variants. */
    private static void sendIsolationWarning(CommandSourceStack src, int runtimeId,
                                              SubLevelPhysicsSystem system, ServerSubLevel target) {
        final int idF = runtimeId;
        final double gravEst = target.getMassTracker().getMass() * 0.08 *
                (0.05 / system.getConfig().substepsPerTick);
        src.sendSuccess(() -> Component.literal(String.format(
                "[T-4] ISOLATION WARNING: Cannot confirm structure id=%d has no contacts. " +
                "Contact forces and gravity (≈%.4f kpg·block/s per substep) are error sources.",
                idF, gravEst)), true);
    }

    /** Stores pending and sends confirmation. */
    private static void storePendingAndConfirm(
            CommandSourceStack src, ValidatedT4 v,
            int runtimeId, double fx, double fy, double fz,
            String variant, String pointLabel) {
        T4ApplyForceExperiment.pendingByKey.put(v.key(), new T4ApplyForceExperiment.Pending(
                v.levelKey(), runtimeId, fx, fy, fz,
                v.vBefore().x, v.vBefore().y, v.vBefore().z,
                v.mass(), v.dt(), v.level().getGameTime(), variant));
        io.github.omegau371.trueimpact.diagnostic.ExperimentLog.info(
                "[T-4] PENDING stored: key='{}' variant={} point={} levelKey='{}' runtimeId={} mapSize={}",
                v.key(), variant, pointLabel, v.levelKey(), runtimeId,
                T4ApplyForceExperiment.pendingByKey.size());
        final double vbx = v.vBefore().x, vby = v.vBefore().y, vbz = v.vBefore().z;
        final double magF = v.inputMag(), mF = v.mass();
        final boolean velValidF = v.velValid();
        final int idF = runtimeId;
        final double fxF = fx, fyF = fy, fzF = fz;
        final String variantF = variant, pointF = pointLabel;
        src.sendSuccess(() -> Component.literal(String.format(
                "[T-4] variant=%s point=%s  input=(%.3f,%.3f,%.3f) |input|=%.3f to id=%d (M=%.3f kpg). " +
                "vBefore=(%.4f,%.4f,%.4f) velValid=%b. " +
                "Results on next POST_STEP. DO NOT move structure before POST_STEP fires.",
                variantF, pointF, fxF, fyF, fzF, magF, idF, mF, vbx, vby, vbz, velValidF)), false);
    }

    // ── Variant A: com-current (original behaviour) ──────────────────────────

    /**
     * Original T-4: applies at getCenterOfMass() (plot space).
     * Rapier receives (com - com) = (0,0,0) as body-local point → intended pure linear.
     *
     * @param src  command source (op level 4 required — enforced by DiagnosticCommand)
     * @param runtimeId  target sub-level runtime ID (use 't4 bodies' to list)
     * @param fx/fy/fz   input vector; magnitude hard-capped at MAX_INPUT_MAGNITUDE
     */
    public static int applyForExperiment(CommandSourceStack src,
                                          int runtimeId, double fx, double fy, double fz) {
        ValidatedT4 v = validate(src, runtimeId, fx, fy, fz);
        if (v == null) return 0;
        sendIsolationWarning(src, runtimeId, v.system(), v.target());
        // Apply at getCenterOfMass() (plot space) → (com - com) = (0,0,0) in Rapier
        v.system().getPipeline().applyImpulse(v.target(), v.com(), new Vector3d(fx, fy, fz));
        storePendingAndConfirm(src, v, runtimeId, fx, fy, fz,
                "com-current", "PLOT_COM");
        return 1;
    }

    // ── Variant B: linear-only (applyLinearAndAngularImpulse, torque=0) ──────

    /**
     * T-4 variant B: bypasses the point system entirely.
     * Uses applyLinearAndAngularImpulse(body, force, zero_torque, wakeUp=true).
     * This is the only way to guarantee zero torque regardless of coordinate space.
     * Expected ratio: ~1/dt if applyForceAndTorque = Rapier force; ~1 if impulse.
     * Angular velocity after = 0 confirms no torque was generated.
     */
    public static int applyLinearExperiment(CommandSourceStack src,
                                             int runtimeId, double fx, double fy, double fz) {
        ValidatedT4 v = validate(src, runtimeId, fx, fy, fz);
        if (v == null) return 0;
        sendIsolationWarning(src, runtimeId, v.system(), v.target());
        // applyLinearAndAngularImpulse: force + zero torque, no application point
        v.system().getPipeline().applyLinearAndAngularImpulse(
                v.target(), new Vector3d(fx, fy, fz), new Vector3d(), true);
        storePendingAndConfirm(src, v, runtimeId, fx, fy, fz,
                "linear-only", "N/A(no-point)");
        return 1;
    }

    // ── Variant C: at-pose-pos (logicalPose().position() as application point) ─

    /**
     * T-4 variant C: applies at logicalPose().position() (the body's pivot in plot space).
     * Rapier receives (pose_pos - COM) as body-local point → typically off-COM → generates torque.
     * Useful as a sanity-check: if ratio and angular velocity change vs com-current,
     * it proves the application point does affect the result (coordinate space is correct).
     */
    public static int applyAtPoseExperiment(CommandSourceStack src,
                                             int runtimeId, double fx, double fy, double fz) {
        ValidatedT4 v = validate(src, runtimeId, fx, fy, fz);
        if (v == null) return 0;
        sendIsolationWarning(src, runtimeId, v.system(), v.target());
        // Apply at logicalPose().position() (body pivot in plot space)
        var posePos = v.target().logicalPose().position();
        v.system().getPipeline().applyImpulse(v.target(),
                new Vector3d(posePos.x(), posePos.y(), posePos.z()), new Vector3d(fx, fy, fz));
        storePendingAndConfirm(src, v, runtimeId, fx, fy, fz,
                "at-pose-pos",
                String.format("PLOT_POSE_POS(%.2f,%.2f,%.2f)", posePos.x(), posePos.y(), posePos.z()));
        return 1;
    }
}
