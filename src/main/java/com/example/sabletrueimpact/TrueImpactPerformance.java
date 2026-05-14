/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.tick.LevelTickEvent$Post
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrueImpactPerformance {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"sabletrueimpact");
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
    private static long explosionEvents;
    private static long explosionRays;
    private static long explosionHits;
    private static long explosionFractures;
    private static long explosionNanos;

    private TrueImpactPerformance() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level instanceof ServerLevel) {
            ServerLevel level2 = (ServerLevel)level;
            TrueImpactPerformance.maybeLog(level2);
        }
    }

    public static long start() {
        return (Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get() != false ? System.nanoTime() : 0L;
    }

    public static void recordCollisionBatch(int records) {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        ++collisionBatches;
        collisionRecords += (long)Math.max(records, 0);
    }

    public static void recordFracture(long startedAt, int checked, int candidates, int removed) {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        ++fractureAttempts;
        fractureCandidateChecks += (long)Math.max(checked, 0);
        fractureCandidates += (long)Math.max(candidates, 0);
        fractureRemovedBlocks += (long)Math.max(removed, 0);
        fractureNanos += TrueImpactPerformance.elapsed(startedAt);
    }

    public static void recordFractureSkippedBudget() {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        ++fractureSkippedBudget;
    }

    public static void recordEntityScan(long startedAt, int subLevels, int candidates, int hits) {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        ++entityScanTicks;
        entitySubLevels += (long)Math.max(subLevels, 0);
        entityCandidates += (long)Math.max(candidates, 0);
        entityHits += (long)Math.max(hits, 0);
        entityNanos += TrueImpactPerformance.elapsed(startedAt);
    }

    public static void recordExplosionImpact(long startedAt, int rays, int hits, int fractures) {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        ++explosionEvents;
        explosionRays += (long)Math.max(rays, 0);
        explosionHits += (long)Math.max(hits, 0);
        explosionFractures += (long)Math.max(fractures, 0);
        explosionNanos += TrueImpactPerformance.elapsed(startedAt);
    }

    public static void maybeLog(ServerLevel level) {
        if (!((Boolean)TrueImpactConfig.ENABLE_PERFORMANCE_LOGGING.get()).booleanValue()) {
            return;
        }
        long tick = level.getGameTime();
        int interval = (Integer)TrueImpactConfig.PERFORMANCE_LOG_INTERVAL_TICKS.get();
        if (lastLogTick != Long.MIN_VALUE && tick - lastLogTick < (long)interval) {
            return;
        }
        lastLogTick = tick;
        LOGGER.info("True Impact perf: collisionBatches={}, collisionRecords={}, fractureAttempts={}, fractureChecked={}, fractureCandidates={}, fractureRemoved={}, fractureSkippedBudget={}, fractureMs={}, entityScanTicks={}, entitySubLevels={}, entityCandidates={}, entityHits={}, entityMs={}, explosionEvents={}, explosionRays={}, explosionHits={}, explosionFractures={}, explosionMs={}", new Object[]{collisionBatches, collisionRecords, fractureAttempts, fractureCandidateChecks, fractureCandidates, fractureRemovedBlocks, fractureSkippedBudget, TrueImpactPerformance.nanosToMillis(fractureNanos), entityScanTicks, entitySubLevels, entityCandidates, entityHits, TrueImpactPerformance.nanosToMillis(entityNanos), explosionEvents, explosionRays, explosionHits, explosionFractures, TrueImpactPerformance.nanosToMillis(explosionNanos)});
        TrueImpactPerformance.reset();
    }

    private static long elapsed(long startedAt) {
        return startedAt == 0L ? 0L : System.nanoTime() - startedAt;
    }

    private static double nanosToMillis(long nanos) {
        return (double)nanos / 1000000.0;
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
        explosionEvents = 0L;
        explosionRays = 0L;
        explosionHits = 0L;
        explosionFractures = 0L;
        explosionNanos = 0L;
    }
}

