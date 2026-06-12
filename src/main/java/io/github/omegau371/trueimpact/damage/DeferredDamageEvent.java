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
        double threshold,        // material threshold at enqueue time
        VictimInfo.Source     source,
        VictimInfo.Confidence confidence
) {}
