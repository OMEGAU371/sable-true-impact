package io.github.omegau371.trueimpact.diagnostic;

import java.util.concurrent.ConcurrentHashMap;

/**
 * State for T-4: applyForce semantics experiment.
 *
 * [C1-audit] pending is per-level + per-runtimeId, NOT a single global volatile.
 *   Key = "dimension:runtimeId", e.g. "minecraft:overworld:42"
 *   Prevents a second command from silently overwriting the first.
 *
 * [safety] Only populated by explicit admin command (/trueimpact experiment t4 apply).
 *   Never auto-triggered.
 * [safety] ONE-SHOT: cleared on first POST_STEP match for that key.
 *
 * T-4 measurement is INDEPENDENT of LOG_BODY_SNAPSHOTS.
 * SableEventBridge.onPostStep() checks pendingByKey regardless of snapshot logging flags.
 *
 * Input hard limit: MAX_INPUT_MAGNITUDE_KPG_BLOCK_S
 *   Conservative ceiling — researcher must stay well below to avoid corrupting the run.
 *
 * Recorded quantities:
 *   - M (kpg), inputRaw vector, dt (substep seconds), vBefore, vAfter
 *   - Δv = vAfter − vBefore (full vector)
 *   - deltaVAlongInput = Δv · normalize(input)       [projection onto input direction]
 *   - measuredMomentumAlongInput = M * deltaVAlongInput
 *   - input/(M * deltaVAlongInput) ratio             [NOT auto-interpreted as conclusion]
 *
 * Error sources explicitly recorded in log:
 *   - gravity impulse during substep (≈ M * g * dt)
 *   - possible contact during experiment (cannot be excluded without isolation proof)
 *   - velocity measurement precision (getLinearVelocity vs latestLinearVelocity difference)
 *
 * === T-4 EXPERIMENTAL CONCLUSIONS (2026-06-06, confirmed in live game) ===
 *
 *   Test structure: M=1 kpg, isolated (floating, no contacts), input=(100,0,0).
 *
 *   variant=linear-only (applyLinearAndAngularImpulse, torque=0):
 *     ratio ≈ 1.0037, angVel ≈ 0  →  IMPULSE SEMANTICS CONFIRMED.
 *     applyLinearAndAngularImpulse applies a direct velocity change: Δv ≈ F/M per call.
 *
 *   variant=com-current (applyImpulse at getCenterOfMass()):
 *     ratio ≈ 1.0037, angVel ≈ 0  →  Same impulse semantics; COM application generates no torque.
 *     COM application is SAFE and PHYSICALLY CORRECT.
 *
 *   variant=at-pose-pos [PERMANENTLY UNSAFE — REMOVED]:
 *     vAfter ≈ (1.9e8, 1.9e9, 9.6e8), |Δv| ≈ 2.15e9, |ω| ≈ 3.61e9.
 *     Server ran 21 753 ms / 435 ticks behind; Sable emergency-removed sub-level.
 *     Root cause: logicalPose().position() is in embedded/plot space (≈ 204810xx).
 *     Lever arm = (pose_pos − COM) in plot-space units → astronomically large.
 *     logicalPose().position() is NEVER a valid applyImpulse application point.
 *
 *   Rule for future point-based impulses:
 *     - Any position argument to applyImpulse MUST be in the same coordinate space as getCenterOfMass().
 *     - Verify: lever arm |position − COM| must be ≪ 1e3 before calling API.
 *     - Unknown coordinate space → REJECT at the guard layer; do not call through.
 */
public final class T4ApplyForceExperiment {

    private T4ApplyForceExperiment() {}

    /** Conservative hard ceiling on input vector magnitude (kpg·block/s equivalent). */
    public static final double MAX_INPUT_MAGNITUDE = 200.0;

    /** Reject experiment if pre-experiment |v| exceeds this threshold. */
    public static final double MAX_PRE_VELOCITY_THRESHOLD = 2.0;

    /** After this many ticks with no POST_STEP match, pending is considered stuck and auto-cleared. */
    public static final int PENDING_TIMEOUT_TICKS = 100;

    public record Pending(
            String levelKey,
            int runtimeId,
            double fx, double fy, double fz,
            double vbx, double vby, double vbz,
            double massKpg,
            double substepDt,
            long tickApplied,
            /** Which apply variant created this pending entry. */
            String variant
    ) {}

    /**
     * All active T-4 experiments, keyed by levelKey + ":" + runtimeId.
     * Thread-safe: written by server-thread command, read by server-thread physics.
     */
    public static final ConcurrentHashMap<String, Pending> pendingByKey = new ConcurrentHashMap<>();

    public static String key(String levelDimension, int runtimeId) {
        return levelDimension + ":" + runtimeId;
    }

    public static void clearAll() {
        pendingByKey.clear();
    }
}
