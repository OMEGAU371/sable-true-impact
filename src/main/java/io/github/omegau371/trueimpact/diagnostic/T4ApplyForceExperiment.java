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
