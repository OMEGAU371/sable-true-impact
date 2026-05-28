package com.example.sabletrueimpact;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.1.4-diag: full-path diagnostic logger.
 *
 * Rate-limited per (path, blockpos) tuple. Default cooldown 1 second so a
 * tracked vehicle pumping out hundreds of contacts per second doesn't drown
 * the log. Each path has its own short prefix:
 *
 * <ul>
 *   <li>{@code [TIDiag] [Hardness@x,y,z]} — HardnessFragileCallback per-contact callback</li>
 *   <li>{@code [TIDiag] [Soil@x,y,z]} — soil compaction probability gate</li>
 *   <li>{@code [TIDiag] [Terrain@x,y,z]} — ElasticPairReaction per-contact terrain damage</li>
 *   <li>{@code [TIDiag] [Grid@x,y,z]} — ElasticPairReaction grid scan summary (one line per scan)</li>
 *   <li>{@code [TIDiag] [Clip@x,y,z]} — ClippingDamageScanner per-overlap damage application</li>
 * </ul>
 *
 * Strip before tagging 1.1.4 stable.
 */
public final class TIDiag {
    private static final Logger LOG = LogManager.getLogger("TIDiag");
    private static final Map<String, Long> LAST_LOG_AT = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000L;

    private TIDiag() {}

    private static boolean enabled() {
        try {
            return ((Boolean) TrueImpactConfig.ENABLE_DIAGNOSTIC_LOGGING.get()).booleanValue();
        } catch (Throwable t) {
            // Config not yet loaded (early init) — disable to avoid spam.
            return false;
        }
    }

    private static boolean shouldLog(String key) {
        long now = System.currentTimeMillis();
        Long prev = LAST_LOG_AT.get(key);
        if (prev != null && now - prev < COOLDOWN_MS) return false;
        LAST_LOG_AT.put(key, now);
        return true;
    }

    /** HardnessFragileCallback — fires for every block-sublevel collision. */
    public static void hardness(BlockPos pos, int sslId, double impactVelocity,
                                double mass, double kineticEnergyRaw, double kineticEnergyNorm,
                                double materialStrength, double yieldRatio,
                                int contactCount, String outcome) {
        if (!enabled() || pos == null) return;
        String key = "H:" + pos.asLong();
        if (!shouldLog(key)) return;
        try {
            LOG.info(String.format(Locale.ROOT,
                "[Hardness@%s] ssl=%d v=%.2f m=%.0f KE_raw=%.1f KE_norm=%.1f str=%.1f yR=%.2f contacts=%d -> %s",
                pos.toShortString(), sslId, impactVelocity, mass,
                kineticEnergyRaw, kineticEnergyNorm, materialStrength, yieldRatio, contactCount, outcome));
        } catch (Throwable ignored) {}
    }

    /** Soil compaction probability gate. */
    public static void soil(BlockPos pos, double impactVelocity, double chance, boolean fired) {
        if (!enabled() || pos == null) return;
        String key = "S:" + pos.asLong();
        if (!shouldLog(key)) return;
        try {
            LOG.info(String.format(Locale.ROOT,
                "[Soil@%s] v=%.2f chance=%.3f -> %s",
                pos.toShortString(), impactVelocity, chance, fired ? "COMPACTED" : "skipped"));
        } catch (Throwable ignored) {}
    }

    /** ElasticPairReaction.processTerrainImpact per-contact seed damageTerrain call. */
    public static void terrainContact(BlockPos pos, int contactIdx, int contactCount,
                                       double energyRaw, double energyNorm, double divisor) {
        if (!enabled() || pos == null) return;
        String key = "T:" + pos.asLong();
        if (!shouldLog(key)) return;
        try {
            LOG.info(String.format(Locale.ROOT,
                "[Terrain@%s] contact %d/%d energy_raw=%.1f energy_norm=%.1f div=%.1f",
                pos.toShortString(), contactIdx, contactCount, energyRaw, energyNorm, divisor));
        } catch (Throwable ignored) {}
    }

    /** Grid scan summary — one line per scan invocation (not per cell). */
    public static void gridScan(BlockPos approxPos, int cellCount, double representativeEnergyRaw,
                                double representativeEnergyNorm, double divisor) {
        if (!enabled() || approxPos == null) return;
        String key = "G:" + approxPos.asLong();
        if (!shouldLog(key)) return;
        try {
            LOG.info(String.format(Locale.ROOT,
                "[Grid@%s] cells=%d rep_raw=%.1f rep_norm=%.1f div=%.1f",
                approxPos.toShortString(), cellCount, representativeEnergyRaw,
                representativeEnergyNorm, divisor));
        } catch (Throwable ignored) {}
    }

    /** ClippingDamageScanner per-overlap damage application — one line per scan per ssl. */
    public static void clip(BlockPos approxPos, int sslId, int overlapCount,
                            double crackEnergyBase, double perOverlapEnergy, double divisor) {
        if (!enabled() || approxPos == null) return;
        String key = "C:" + sslId;
        if (!shouldLog(key)) return;
        try {
            LOG.info(String.format(Locale.ROOT,
                "[Clip@%s] ssl=%d overlaps=%d base=%.1f per_overlap=%.2f div=%.1f",
                approxPos.toShortString(), sslId, overlapCount,
                crackEnergyBase, perOverlapEnergy, divisor));
        } catch (Throwable ignored) {}
    }
}
