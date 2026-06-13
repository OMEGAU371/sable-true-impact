package io.github.omegau371.trueimpact.damage;

/**
 * Production runtime configuration for the TrueImpact damage pipeline.
 *
 * These flags control whether real game effects are applied.
 * They are INDEPENDENT of DiagnosticConfig -- debug logging state must never
 * determine whether block damage occurs.
 *
 * All fields default to true (effects enabled). Set to false to disable
 * specific effect categories for testing or compatibility.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class ImpactRuntimeConfig {

    private ImpactRuntimeConfig() {}

    /**
     * Master switch for all block mutation effects.
     * When false: no block states are changed; all flush events are SKIP_EFFECTS_DISABLED.
     * Default: true.
     */
    public static volatile boolean APPLY_BLOCK_EFFECTS = true;

    /**
     * Controls cosmetic damage feedback (particles/sounds) for CRACKED/CRITICAL blocks.
     * When false: DamageFeedbackTracker.shouldEmit() always returns false.
     * Rate limiting and state classification still occur; only the MC-side emission is suppressed.
     * Default: true.
     */
    public static volatile boolean ENABLE_DAMAGE_FEEDBACK = true;

    /**
     * Controls whether debris items are spawned for STONE/GENERIC blocks at CRITICAL state.
     * When false: no ItemEntity is spawned; MaterialResponsePlanner plans still computed.
     * Debris deduplication (one drop per block) applies regardless of this flag.
     * Default: false (disabled in Phase 2E hotfix; reserved for Phase 2F).
     */
    public static volatile boolean ENABLE_DEBRIS_DROPS = false;

    /**
     * Controls the vanilla block-breaking crack overlay (progress 0..9) for CRACKED/CRITICAL blocks.
     * When true: sends destroyBlockProgress packets via CrackOverlayTracker (rate-limited).
     * Does not destroy blocks -- purely visual feedback.
     * Default: true.
     */
    public static volatile boolean ENABLE_VANILLA_CRACK_OVERLAY = true;
}
