package io.github.omegau371.trueimpact.damage;

/**
 * Immutable candidate damage event produced by the deferred damage pipeline.
 *
 * Created when:
 *   - A WORLD_BLOCK victim was detected (via contact-point sampling or callback)
 *   - A finite kImpact exceeding the victim's material threshold was measured
 *
 * Enqueued by SableImpactCapture during clearCollisions processing (inside physics tick,
 * server thread). Applied by TrueImpactMod on ServerTickEvent.Post (safe window).
 *
 * Phase 2A: ImpactBlockApplicator.tryApply() handles SOFT_SOIL compaction.
 *
 * victimKind is implicitly WORLD_BLOCK -- only WORLD_BLOCK events are enqueued.
 * levelKey identifies the dimension (e.g. "minecraft:overworld") so the applicator
 * can find the correct ServerLevel during flush.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public record DeferredDamageEvent(
        long   serverTick,
        String levelKey,         // dimension registry ID, e.g. "minecraft:overworld"
        String victimBlock,      // registry ID e.g. "minecraft:grass_block"
        int    posX, int posY, int posZ,
        MaterialThresholdProfile.MaterialClass materialClass,
        double kImpact,          // velocity-derived kinetic energy (Phase 1C canonical)
        double threshold,        // break threshold: accumulated damage at which ratio >= 1.0
        VictimInfo.Source     source,
        VictimInfo.Confidence confidence,
        // Normalized impact direction: from the moving body toward the victim block.
        // Derived from active body pre-impact velocity. NaN when unavailable.
        double impactDirX,
        double impactDirY,
        double impactDirZ,
        // Block outward face normal (world space): points from block surface toward active body.
        // Derived from the static-body contact normal in raw Rapier contact data (no rotation needed).
        // NaN when unavailable -- check hasContactNormal() before use.
        double contactNormalX,
        double contactNormalY,
        double contactNormalZ,
        // Speed of the active body along the contact surface before impact (m/s in Sable units).
        // = sqrt(|v|^2 - (v · contactNormal)^2). Used for friction force estimation.
        // NaN when velocity or contact normal is unavailable.
        double tangentialSpeedMs
) {
    /**
     * Backward-compatible constructor (direction + contact context unknown).
     * Used by test code and legacy enqueue paths; sets all new fields to NaN.
     */
    public DeferredDamageEvent(long serverTick, String levelKey, String victimBlock,
            int posX, int posY, int posZ,
            MaterialThresholdProfile.MaterialClass materialClass,
            double kImpact, double threshold,
            VictimInfo.Source source, VictimInfo.Confidence confidence) {
        this(serverTick, levelKey, victimBlock, posX, posY, posZ,
                materialClass, kImpact, threshold, source, confidence,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN);
    }

    /** Backward-compatible constructor (contact context unknown, direction known). */
    public DeferredDamageEvent(long serverTick, String levelKey, String victimBlock,
            int posX, int posY, int posZ,
            MaterialThresholdProfile.MaterialClass materialClass,
            double kImpact, double threshold,
            VictimInfo.Source source, VictimInfo.Confidence confidence,
            double impactDirX, double impactDirY, double impactDirZ) {
        this(serverTick, levelKey, victimBlock, posX, posY, posZ,
                materialClass, kImpact, threshold, source, confidence,
                impactDirX, impactDirY, impactDirZ,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN);
    }

    /** True when a valid normalized impact direction is available. */
    public boolean hasDirection() {
        return Double.isFinite(impactDirX);
    }

    /** True when a valid block outward contact normal is available. */
    public boolean hasContactNormal() {
        return Double.isFinite(contactNormalX);
    }

    /** True when tangential speed was successfully derived. */
    public boolean hasTangentialSpeed() {
        return Double.isFinite(tangentialSpeedMs);
    }

    /** Returns a copy with a new break threshold, preserving all other fields. */
    public DeferredDamageEvent withThreshold(double newThreshold) {
        return new DeferredDamageEvent(serverTick, levelKey, victimBlock, posX, posY, posZ,
                materialClass, kImpact, newThreshold, source, confidence,
                impactDirX, impactDirY, impactDirZ,
                contactNormalX, contactNormalY, contactNormalZ,
                tangentialSpeedMs);
    }

    /** Returns a copy with a new kImpact, preserving all other fields. */
    public DeferredDamageEvent withKImpact(double newKImpact) {
        return new DeferredDamageEvent(serverTick, levelKey, victimBlock, posX, posY, posZ,
                materialClass, newKImpact, threshold, source, confidence,
                impactDirX, impactDirY, impactDirZ,
                contactNormalX, contactNormalY, contactNormalZ,
                tangentialSpeedMs);
    }

    /** Returns a copy with a new normalized impact direction, preserving contact context. */
    public DeferredDamageEvent withDirection(double dx, double dy, double dz) {
        return new DeferredDamageEvent(serverTick, levelKey, victimBlock, posX, posY, posZ,
                materialClass, kImpact, threshold, source, confidence,
                dx, dy, dz,
                contactNormalX, contactNormalY, contactNormalZ,
                tangentialSpeedMs);
    }
}
