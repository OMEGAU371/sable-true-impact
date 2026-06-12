package io.github.omegau371.trueimpact.sable;

import io.github.omegau371.trueimpact.damage.VictimInfo;

/**
 * Phase 1D: static capture buffer for world-block collision data.
 *
 * Two capture paths (last-write-wins; callback takes priority over contact-point):
 *
 *   PATH A -- callback-based (DiagnosticCallbackWrapperMixin during Rapier3D.step()):
 *     Only fires for blocks that implement BlockWithSubLevelCollisionCallback.
 *     Most vanilla blocks (stone, dirt, planks, obsidian ...) have null callbacks and
 *     DO NOT fire this path. Captured source: CALLBACK_BLOCK_POS.
 *
 *   PATH B -- contact-point sampling (DiagnosticContactCaptureMixin after clearCollisions()):
 *     Scans clearCollisions array for world-vs-active contacts, transforms the active
 *     body's contact point from BODY_COM_LOCAL to WORLD, samples level.getBlockState()
 *     at and around that point. Fires for ALL blocks including vanilla stone/dirt.
 *     Captured source: CONTACT_POINT_SAMPLE.
 *
 * PATH B fires only when PATH A has no data yet (hasCaptureThisTick == false before PATH B runs).
 *
 * Lifecycle per tick:
 *   1. clearForTick() at substep 0 (SableEventBridge.onPreStep).
 *   2. [Optional] captureCallbackBlock() during substeps (PATH A).
 *   3. [Optional] captureContactPointBlock() in captureContactData() if PATH A empty (PATH B).
 *   4. buildWorldVictimInfo() called by SableImpactCapture.process() -- reads capture.
 *
 * Thread safety: volatile fields. PATH A writes from JNI callback thread happen-before
 * PATH B/4 reads on server thread (after Rapier3D.step() completes). Sufficient for diagnostics.
 *
 * This class does NOT import DiagnosticConfig (R13 preserved for SableImpactCapture callers).
 */
public final class SableVictimCapture {

    private SableVictimCapture() {}

    // Most recent block data captured this tick (either path).
    private static volatile String         lastBlockId;
    private static volatile int            lastPosX;
    private static volatile int            lastPosY;
    private static volatile int            lastPosZ;
    private static volatile boolean        lastPosLooksWorld;
    private static volatile VictimInfo.Source lastSource;
    private static volatile int            captureCount; // total captures this tick (for diagnostics)
    private static volatile boolean        hasCaptureThisTick;

    // ---- PATH A: BlockSubLevelCollisionCallback ----------------------------------

    /**
     * Records a block-vs-sublevel collision from BlockSubLevelCollisionCallback.
     * Called from DiagnosticCallbackWrapperMixin when DiagnosticConfig.ENABLED.
     * Only fires for blocks that implement BlockWithSubLevelCollisionCallback.
     *
     * @param blockId       block registry id, e.g. "minecraft:stone"
     * @param posX,Y,Z      raw block position from callback (T-2 space UNCONFIRMED)
     * @param posLooksWorld heuristic: |posX|<=1_000_000 && |posZ|<=1_000_000
     */
    public static void captureCallbackBlock(String blockId,
                                             int posX, int posY, int posZ,
                                             boolean posLooksWorld) {
        storeCapture(blockId, posX, posY, posZ, posLooksWorld, VictimInfo.Source.CALLBACK_BLOCK_POS);
    }

    // ---- PATH B: contact-point block sampling ------------------------------------

    /**
     * Records a world block detected by contact-point sampling.
     * Called from DiagnosticContactCaptureMixin after clearCollisions(), only when PATH A
     * has no data (most common case: vanilla blocks lacking BlockSubLevelCollisionCallback).
     * Position is considered world-space (sampled via level.getBlockState after transform).
     *
     * @param blockId   block registry id, e.g. "minecraft:stone"
     * @param posX,Y,Z  sampled block position in world space
     */
    public static void captureContactPointBlock(String blockId, int posX, int posY, int posZ) {
        storeCapture(blockId, posX, posY, posZ, true, VictimInfo.Source.CONTACT_POINT_SAMPLE);
    }

    // ---- shared store ------------------------------------------------------------

    private static void storeCapture(String blockId, int posX, int posY, int posZ,
                                      boolean posLooksWorld, VictimInfo.Source source) {
        lastBlockId        = blockId;
        lastPosX           = posX;
        lastPosY           = posY;
        lastPosZ           = posZ;
        lastPosLooksWorld  = posLooksWorld;
        lastSource         = source;
        captureCount++;
        hasCaptureThisTick = true;
    }

    // ---- lifecycle ---------------------------------------------------------------

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
        lastSource         = null;
        captureCount       = 0;
        hasCaptureThisTick = false;
    }

    // ---- accessors ---------------------------------------------------------------

    /** True if any block data was captured this tick (via callback or contact-point). */
    public static boolean hasCaptureThisTick() { return hasCaptureThisTick; }

    /** Number of capture events this tick (one per callback or sampling call). */
    public static int captureCount() { return captureCount; }

    /**
     * Builds a VictimInfo from this tick's captured data.
     * Returns VictimInfo.unknown() if no data was captured.
     *
     * confidence=APPROX for both paths:
     *   CALLBACK_BLOCK_POS: T-2 coord space unconfirmed.
     *   CONTACT_POINT_SAMPLE: contact-point transform + nearby block sampling is approximate.
     */
    public static VictimInfo buildWorldVictimInfo() {
        if (!hasCaptureThisTick) {
            return VictimInfo.unknown();
        }
        String bid = lastBlockId;
        if (bid == null) {
            return VictimInfo.unknown();
        }
        VictimInfo.Source src = (lastSource != null) ? lastSource : VictimInfo.Source.NONE;
        if (lastPosLooksWorld) {
            return VictimInfo.worldBlock(bid, lastPosX, lastPosY, lastPosZ,
                    VictimInfo.Confidence.APPROX, src);
        } else {
            return VictimInfo.worldBlockNoPos(bid, VictimInfo.Confidence.APPROX, src);
        }
    }
}
