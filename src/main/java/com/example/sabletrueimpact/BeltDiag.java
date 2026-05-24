package com.example.sabletrueimpact;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * beta.11-diag: belt-vs-shaft destruction diagnostic.
 *
 * Per Discord report (~2026-05-24) belts get cracks but never break, while
 * shafts in the same impact do break. Log every callback decision for
 * {@code create:belt} and {@code create:shaft} so we can see which gate
 * stops belt blocks short of {@code destroyBlock}.
 *
 * Rate-limited per BlockPos (one line every {@value #COOLDOWN_MS} ms) to
 * keep latest.log readable when a player rams a long belt repeatedly.
 *
 * Strip before tagging 1.1.0-gamma.
 */
public final class BeltDiag {
    private static final Logger LOG = LogManager.getLogger("BeltDiag");
    private static final Map<Long, Long> LAST_LOG_AT = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LAST_ENTRY_AT = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 5000L;
    private static final long ENTRY_COOLDOWN_MS = 1000L;

    private BeltDiag() {}

    /** Returns true iff this block is one we want to instrument. */
    public static boolean tracked(BlockState state) {
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null) return false;
            String s = id.toString();
            return "create:belt".equals(s) || "create:shaft".equals(s);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Short log — only takes the outcome label, plus a few key numbers. */
    public static void log(BlockPos pos, BlockState state, String outcome,
                           double impactVelocity, double kineticEnergy,
                           double materialStrength, double yieldRatio,
                           double crackRatio, boolean destructible,
                           boolean nearbySub) {
        if (!tracked(state)) return;
        long key = pos.asLong();
        long now = System.currentTimeMillis();
        Long prev = LAST_LOG_AT.get(key);
        if (prev != null && now - prev < COOLDOWN_MS) return;
        LAST_LOG_AT.put(key, now);
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            LOG.info(String.format(Locale.ROOT,
                "[B@%s] %s v=%.2f ke=%.1f str=%.1f yieldR=%.2f crackR=%.2f destruct=%b nearSub=%b -> %s",
                pos.toShortString(),
                id == null ? "?" : id.toString(),
                impactVelocity, kineticEnergy, materialStrength,
                yieldRatio, crackRatio, destructible, nearbySub, outcome));
        } catch (Throwable t) {
            // Logging must never disturb physics.
        }
    }

    /** Entry probe — fires on EVERY callback invocation for tracked blocks (with shorter cooldown).
     *  Use this to confirm the callback is even reaching us. */
    public static void logEntry(BlockPos pos, BlockState state, double impactVelocity) {
        if (!tracked(state)) return;
        long key = pos.asLong();
        long now = System.currentTimeMillis();
        Long prev = LAST_ENTRY_AT.get(key);
        if (prev != null && now - prev < ENTRY_COOLDOWN_MS) return;
        LAST_ENTRY_AT.put(key, now);
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            LOG.info(String.format(Locale.ROOT,
                "[B@%s] %s ENTRY v=%.4f (callback fired)",
                pos.toShortString(),
                id == null ? "?" : id.toString(),
                impactVelocity));
        } catch (Throwable t) {
        }
    }

    /** Minimal log — for early-return paths where most numbers aren't computed yet. */
    public static void logEarly(BlockPos pos, BlockState state, String outcome,
                                double impactVelocity) {
        if (!tracked(state)) return;
        long key = pos.asLong();
        long now = System.currentTimeMillis();
        Long prev = LAST_LOG_AT.get(key);
        if (prev != null && now - prev < COOLDOWN_MS) return;
        LAST_LOG_AT.put(key, now);
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            LOG.info(String.format(Locale.ROOT,
                "[B@%s] %s v=%.2f -> %s (early-return)",
                pos.toShortString(),
                id == null ? "?" : id.toString(),
                impactVelocity, outcome));
        } catch (Throwable t) {
        }
    }
}
