package io.github.omegau371.trueimpact.observation;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all mutable diagnostic state that must be cleared on server stop or world change.
 * Static state in SableEventBridge, T4ApplyForceExperiment, etc. must be flushed here.
 *
 * Called from TrueImpactMod on ServerStoppedEvent and on /trueimpact debug all off.
 */
public final class DiagnosticStateManager {

    private DiagnosticStateManager() {}

    // Registered flush callbacks (populated by sable/ package at module init)
    private static final java.util.List<Runnable> FLUSH_HOOKS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void registerFlushHook(Runnable hook) {
        FLUSH_HOOKS.add(hook);
    }

    /**
     * Clears all transient diagnostic state and resets flags.
     * Safe to call from any server-thread context.
     */
    public static void clearAll() {
        // Reset all flags
        DiagnosticConfig.ENABLED = false;
        DiagnosticConfig.LOG_BODY_SNAPSHOTS = false;
        DiagnosticConfig.LOG_RAW_CONTACTS = false;
        DiagnosticConfig.LOG_T1_CALLBACK_THREAD = false;
        DiagnosticConfig.LOG_T2_CALLBACK_COORD = false;
        DiagnosticConfig.LOG_T7_VELOCITY_RATIO = false;

        // Reset rate limiter
        DiagnosticConfig.LIMITER.resetTick();
        DiagnosticConfig.LIMITER.resetSecond();

        // Flush all registered hooks (T-4 pending map, prevPrePos, etc.)
        for (Runnable hook : FLUSH_HOOKS) {
            try { hook.run(); } catch (Throwable ignored) {}
        }
    }
}
