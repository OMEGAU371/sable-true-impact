package io.github.omegau371.trueimpact.damage;

/**
 * Phase 1D diagnostic result of victim block detection for one contact event.
 *
 * For active-vs-active contacts (both bodies are sub-levels) there is no single
 * world block "victim" -- kind=ACTIVE_SUBLEVEL.
 * For world-vs-active contacts, kind=WORLD_BLOCK when callback data is available,
 * UNKNOWN when it is not.
 *
 * CONTRACT (Phase 1D):
 *   - Diagnostic-only. MUST NOT trigger any game effect.
 *   - DamageResolver must still return NONE regardless of this record.
 *   - materialThresholdJ uses MaterialThresholdProfile placeholder thresholds.
 *   - blockId/pos are from BlockSubLevelCollisionCallback (T-2 space UNCONFIRMED);
 *     confidence=APPROX until T-2 experiment confirms coordinate space.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public record VictimInfo(
        Kind       kind,
        String     blockId,           // registry id e.g. "minecraft:stone"; null if no block
        int        posX, int posY, int posZ,
        boolean    hasPos,            // false if pos unknown or suspected embedded coords
        Confidence confidence,
        Source     source,
        MaterialThresholdProfile.MaterialClass materialClass,
        double     materialThresholdJ // from MaterialThresholdProfile.threshold(materialClass)
) {

    /** Nature of the struck body. */
    public enum Kind {
        ACTIVE_SUBLEVEL, // both parties are sub-levels; no world block involved
        WORLD_BLOCK,     // a world/terrain block was detected as the struck surface
        UNKNOWN          // cannot determine victim from available data
    }

    /** Reliability of the detected block identity and position. */
    public enum Confidence {
        EXACT,   // confirmed (coordinate space verified, e.g. after T-2 passes)
        APPROX,  // plausible but unconfirmed (T-2 space unconfirmed; large-coord heuristic applied)
        UNKNOWN  // no reliable data
    }

    /** Which technique produced this VictimInfo. */
    public enum Source {
        CALLBACK_BLOCK_POS,    // BlockSubLevelCollisionCallback(BlockPos, BlockState) -- Phase 1D
                               // NOTE: only fires for blocks that implement BlockWithSubLevelCollisionCallback.
                               // Most vanilla blocks (stone, dirt, ...) have null callback -> never fires.
        CONTACT_POINT_SAMPLE,  // contact point transformed to world space; nearby block sampled -- Phase 1D
        NO_CALLBACK,           // world contact detected in clearCollisions, but no block data obtained.
                               // Means: callback didn't fire (block lacks BlockWithSubLevelCollisionCallback)
                               // AND contact-point sampling found no solid block.
        NONE                   // no data source (ACTIVE_SUBLEVEL or UNKNOWN with no detected contact)
    }

    // -- factories -----------------------------------------------------------------

    /**
     * Active-vs-active contact: both parties are sub-levels; no world block involved.
     * confidence=EXACT because the nature of the contact is definitively known.
     */
    public static VictimInfo activeSublevel() {
        return new VictimInfo(
                Kind.ACTIVE_SUBLEVEL, null, 0, 0, 0, false,
                Confidence.EXACT, Source.NONE,
                MaterialThresholdProfile.MaterialClass.GENERIC,
                MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.GENERIC));
    }

    /** Victim not determinable from available data (world contact but no callback). */
    public static VictimInfo unknown() {
        return new VictimInfo(
                Kind.UNKNOWN, null, 0, 0, 0, false,
                Confidence.UNKNOWN, Source.NONE,
                MaterialThresholdProfile.MaterialClass.GENERIC,
                MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.GENERIC));
    }

    /**
     * World block identified via callback, with usable position.
     *
     * pos space is UNCONFIRMED (T-2 pending); confidence should be APPROX until T-2 confirms.
     */
    public static VictimInfo worldBlock(String blockId, int x, int y, int z,
                                         Confidence confidence, Source source) {
        MaterialThresholdProfile.MaterialClass mc = MaterialThresholdProfile.classify(blockId);
        return new VictimInfo(
                Kind.WORLD_BLOCK, blockId, x, y, z, true,
                confidence, source, mc,
                MaterialThresholdProfile.threshold(mc));
    }

    /**
     * World contact detected in clearCollisions but no block data obtained.
     * source=NO_CALLBACK: callback never fired (block lacks BlockWithSubLevelCollisionCallback)
     * AND contact-point sampling did not find a solid block.
     */
    public static VictimInfo worldContactNoCallback() {
        return new VictimInfo(
                Kind.UNKNOWN, null, 0, 0, 0, false,
                Confidence.UNKNOWN, Source.NO_CALLBACK,
                MaterialThresholdProfile.MaterialClass.GENERIC,
                MaterialThresholdProfile.threshold(MaterialThresholdProfile.MaterialClass.GENERIC));
    }

    /**
     * World block identified via callback, but position not usable
     * (embedded-level coords detected, or position data unavailable).
     */
    public static VictimInfo worldBlockNoPos(String blockId, Confidence confidence, Source source) {
        MaterialThresholdProfile.MaterialClass mc = MaterialThresholdProfile.classify(blockId);
        return new VictimInfo(
                Kind.WORLD_BLOCK, blockId, 0, 0, 0, false,
                confidence, source, mc,
                MaterialThresholdProfile.threshold(mc));
    }
}
