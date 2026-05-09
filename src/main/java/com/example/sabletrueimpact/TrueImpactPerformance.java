package com.example.sabletrueimpact;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrueImpactPerformance {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrueImpactMod.MODID);

    private static long lastLogTick = Long.MIN_VALUE;
    private static long collisionBatches;
    private static long collisionRecords;
    private static long fractureAttempts;
    private static long fractureCandidateChecks;
    private static long fractureCandidates;
    private static long fractureRemovedBlocks;
    private static long fractureSkippedBudget;
    private static long fractureNanos;
    private static long entityScanTicks;
    private static long entitySubLevels;
    private static long entityCandidates;
    private static long entityHits;
    private static long entityNanos;

    private TrueImpactPerformance() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            maybeLog(level);
        }
    }

    public static long start() {
        return TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get() ? System.nanoTime() : 0L;
    }

    public static void recordCollisionBatch(int records) {
        if (!TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()) {
            return;
        }
        collisionBatches++;
        collisionRecords += Math.max(records, 0);
    }

    public static void recordFracture(long startedAt, int checked, int candidates, int removed) {
        if (!TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()) {
            return;
        }
        fractureAttempts++;
        fractureCandidateChecks += Math.max(checked, 0);
        fractureCandidates += Math.max(candidates, 0);
        fractureRemovedBlocks += Math.max(removed, 0);
        fractureNanos += elapsed(startedAt);
    }

    public static void recordFractureSkippedBudget() {
        if (!TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()) {
            return;
        }
        fractureSkippedBudget++;
    }

    public static void recordEntityScan(long startedAt, int subLevels, int candidates, int hits) {
        if (!TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()) {
            return;
        }
        entityScanTicks++;
        entitySubLevels += Math.max(subLevels, 0);
        entityCandidates += Math.max(candidates, 0);
        entityHits += Math.max(hits, 0);
        entityNanos += elapsed(startedAt);
    }

    public static void maybeLog(ServerLevel level) {
        if (!TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()) {
            return;
        }
        long tick = level.getGameTime();
        int interval = TrueImpactConfig.PERFORMANCE_LOG_INTERVAL_TICKS.get();
        if (lastLogTick != Long.MIN_VALUE && tick - lastLogTick < interval) {
            return;
        }
        lastLogTick = tick;

        LOGGER.info("True Impact perf: collisionBatches={}, collisionRecords={}, fractureAttempts={}, fractureChecked={}, fractureCandidates={}, fractureRemoved={}, fractureSkippedBudget={}, fractureMs={}, entityScanTicks={}, entitySubLevels={}, entityCandidates={}, entityHits={}, entityMs={}",
                collisionBatches,
                collisionRecords,
                fractureAttempts,
                fractureCandidateChecks,
                fractureCandidates,
                fractureRemovedBlocks,
                fractureSkippedBudget,
                nanosToMillis(fractureNanos),
                entityScanTicks,
                entitySubLevels,
                entityCandidates,
                entityHits,
                nanosToMillis(entityNanos));
        reset();
    }

    private static long elapsed(long startedAt) {
        return startedAt == 0L ? 0L : System.nanoTime() - startedAt;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void reset() {
        collisionBatches = 0L;
        collisionRecords = 0L;
        fractureAttempts = 0L;
        fractureCandidateChecks = 0L;
        fractureCandidates = 0L;
        fractureRemovedBlocks = 0L;
        fractureSkippedBudget = 0L;
        fractureNanos = 0L;
        entityScanTicks = 0L;
        entitySubLevels = 0L;
        entityCandidates = 0L;
        entityHits = 0L;
        entityNanos = 0L;
    }
}
