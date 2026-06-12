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
}
