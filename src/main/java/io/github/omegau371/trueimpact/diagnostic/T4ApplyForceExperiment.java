package io.github.omegau371.trueimpact.diagnostic;

/**
 * State holder for T-4: applyForce semantics experiment.
 *
 * Flow (see docs/phase-1-observation-proposal.md §T-4):
 *   1. Admin runs: /trueimpact experiment t4 <runtimeId> <fx> <fy> <fz>
 *   2. Command reads vBefore, applies impulse at COM, stores Pending.
 *   3. SableEventBridge.onPostStep() finds the matching runtimeId and reads vAfter.
 *   4. Computes and logs: M, inputRaw, dt, vBefore, vAfter, Δv, M·Δv, input/(M·Δv).
 *   5. Clears Pending.
 *
 * [safety] Only set via explicit admin command — never auto-triggered.
 * [safety] Measurement is ONE-SHOT: cleared immediately after first matching POST_STEP.
 */
public final class T4ApplyForceExperiment {

    private T4ApplyForceExperiment() {}

    public record Pending(
            int runtimeId,
            double fx, double fy, double fz,
            double vbx, double vby, double vbz,
            double massKpg,
            double substepDt,
            long tickApplied
    ) {}

    /** Volatile — written by server-thread command, read by server-thread physics observer. */
    public static volatile Pending pending = null;

    public static void clear() {
        pending = null;
    }
}
