package io.github.omegau371.trueimpact.damage;

/**
 * Immutable candidate damage event produced by the Phase 1E deferred damage pipeline.
 *
 * Created when:
 *   - A WORLD_BLOCK victim was detected (via contact-point sampling or callback)
 *   - A finite kImpact exceeding the victim's material threshold was measured
 *
 * Enqueued by SableImpactCapture during clearCollisions processing (inside physics tick,
 * server thread). Flushed by TrueImpactMod on ServerTickEvent.Post (safe window).
 *
 * Phase 1E: flush is count/log only. DamageResolver still returns NONE.
 * Phase 2A will promote flush to call DamageResolver and apply a real crack/break.
 *
 * victimKind is implicitly WORLD_BLOCK -- only WORLD_BLOCK events are enqueued.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public record DeferredDamageEvent(
        long   serverTick,
        String victimBlock,      // registry id e.g. "minecraft:stone"
        int    posX, int posY, int posZ,
        MaterialThresholdProfile.MaterialClass materialClass,
        double kImpact,          // velocity-derived kinetic energy (Phase 1C canonical)
        double threshold,        // material threshold at enqueue time
        VictimInfo.Source     source,
        VictimInfo.Confidence confidence
) {}
