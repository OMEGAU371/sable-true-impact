package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.VictimInfo;

/**
 * Phase 1D: static capture buffer for block-vs-sublevel collision data.
 *
 * Written by DiagnosticCallbackWrapperMixin (mixin/) during Rapier3D.step() callbacks
 * when DiagnosticConfig.ENABLED is true. Read by SableImpactCapture.process() after all
 * substeps complete (during clearCollisions() processing).
 *
 * Lifecycle per tick:
 *   1. clearForTick() -- called by SableEventBridge.onPreStep() at substep 0.
 *      Clears data captured during the PREVIOUS tick before this tick's callbacks fire.
 *   2. captureCallbackBlock(...) -- called zero or more times during substeps.
 *      Last-write-wins; only the most recently struck block is retained per tick.
 *   3. buildWorldVictimInfo() -- called once by SableImpactCapture.process() after all substeps.
 *      Reads the captured data and builds a VictimInfo.
 *
 * Thread safety: all fields are volatile. Writes (during JNI callback) happen-before reads
 * (during post-step processing) because the native Rapier3D.step() completes before
 * clearCollisions() is called. Sufficient for diagnostic-only use (no multi-field atomicity).
 *
 * This class does NOT import DiagnosticConfig (R13 preserved for SableImpactCapture callers).
 */
public final class SableVictimCapture {

    private SableVictimCapture() {}

    // Most recent block callback data captured this tick.
    private static volatile String  lastBlockId;
    private static volatile int     lastPosX;
    private static volatile int     lastPosY;
    private static volatile int     lastPosZ;
    private static volatile boolean lastPosLooksWorld;
    private static volatile boolean hasCaptureThisTick;

    /**
     * Records one block-vs-sublevel collision event.
     * Called from DiagnosticCallbackWrapperMixin when DiagnosticConfig.ENABLED.
     *
     * @param blockId        block registry id, e.g. "minecraft:stone"
     * @param posX,Y,Z       raw block position from callback (T-2 space UNCONFIRMED)
     * @param posLooksWorld  heuristic: true when |posX| <= 1_000_000 and |posZ| <= 1_000_000.
     *                       Large values suggest embedded-level coords (~4e7); exclude those.
     */
    public static void captureCallbackBlock(String blockId,
                                             int posX, int posY, int posZ,
                                             boolean posLooksWorld) {
        lastBlockId        = blockId;
        lastPosX           = posX;
        lastPosY           = posY;
        lastPosZ           = posZ;
        lastPosLooksWorld  = posLooksWorld;
        hasCaptureThisTick = true;
    }

    /**
     * Clears the capture buffer for the upcoming tick.
     * Called by SableEventBridge.onPreStep() at substep 0 when ENABLED.
     */
    public static void clearForTick() {
        lastBlockId        = null;
        lastPosX           = 0;
        lastPosY           = 0;
        lastPosZ           = 0;
        lastPosLooksWorld  = false;
        hasCaptureThisTick = false;
    }

    /** True if at least one block callback was captured this tick. */
    public static boolean hasCaptureThisTick() {
        return hasCaptureThisTick;
    }

    /**
     * Builds a VictimInfo from this tick's captured callback data.
     * Returns VictimInfo.unknown() if no block callback was captured this tick.
     *
     * confidence=APPROX because T-2 (callback coordinate space) is unconfirmed.
     * When T-2 confirms the space, upgrade confidence to EXACT here.
     */
    public static VictimInfo buildWorldVictimInfo() {
        if (!hasCaptureThisTick) {
            return VictimInfo.unknown();
        }
        String bid = lastBlockId;
        if (bid == null) {
            return VictimInfo.unknown();
        }
        if (lastPosLooksWorld) {
            return VictimInfo.worldBlock(bid, lastPosX, lastPosY, lastPosZ,
                    VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
        } else {
            // Pos excluded: heuristic says coords may be embedded-level range, not world
            return VictimInfo.worldBlockNoPos(bid,
                    VictimInfo.Confidence.APPROX, VictimInfo.Source.CALLBACK_BLOCK_POS);
        }
    }
}
