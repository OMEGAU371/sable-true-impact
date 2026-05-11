package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class SubLevelFracture {
    private static final Method GET_LEVEL = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel");
    private static final Method GET_HEAT_MAP_MANAGER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getHeatMapManager");
    private static final Method GET_MASS_TRACKER = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method GET_ON_SOLID_REMOVED = findMethod("dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager", "onSolidRemoved", BlockPos.class);
    private static final Method LOGICAL_POSE = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose");
    private static final Method ROTATION_POINT = findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint");
    
    private static final ExecutorService FRACTURE_EXECUTOR = Executors.newSingleThreadExecutor(new FractureThreadFactory());
    private static final ConcurrentLinkedQueue<PendingFracture> COMPLETED_FRACTURES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED_ASYNC_JOBS = new AtomicInteger();
    private static final Map<FractureKey, Long> LAST_FRACTURE_TICK = new ConcurrentHashMap<>();
    private static final Map<String, TickBudget> FRACTURE_BUDGETS = new ConcurrentHashMap<>();
    
    private static final AtomicInteger GLOBAL_FRACTURES_THIS_TICK = new AtomicInteger(0);
    private static long lastGlobalTick = -1;

    private SubLevelFracture() {
    }

    public static void tryFracture(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.get()
                || subLevel == null
                || forceAmount < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) {
            return;
        }

        ServerLevel level = level(subLevel);
        if (level == null) return;
        
        long tick = level.getGameTime();
        checkAndResetGlobalBudget(tick);

        if (GLOBAL_FRACTURES_THIS_TICK.get() >= TrueImpactConfig.GLOBAL_MAX_FRACTURES_PER_TICK.get()) return;

        double mass = mass(subLevel);
        if (mass < TrueImpactConfig.SUBLEVEL_FRACTURE_MIN_BLOCKS_LIMIT.get()) return;

        Vector3d worldPoint = toWorldPoint(subLevel, localPoint);
        if (worldPoint == null || !Double.isFinite(worldPoint.x)) return;

        if (!claimFractureBudget(level, subLevel)) return;

        long startedAt = TrueImpactPerformance.start();
        BlockPos center = BlockPos.containing(worldPoint.x, worldPoint.y, worldPoint.z);
        double fracturePower = scaledForceAboveThreshold(forceAmount, TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get(), TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_EXPONENT.get())
                * TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.get() 
                * TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
        
        if (fracturePower <= 0.0) return;

        Vector3d planeNormal = new Vector3d(normal);
        if (planeNormal.lengthSquared() < 1e-8) planeNormal.set(0, 1, 0);
        else planeNormal.normalize();

        FractureSnapshot snapshot = FractureSnapshot.capture(level, center, radiusForSnapshot());
        Object heatMapManager = heatMapManager(subLevel);

        if (TrueImpactConfig.ENABLE_ASYNC_FRACTURE_ANALYSIS.get() && QUEUED_ASYNC_JOBS.get() < TrueImpactConfig.ASYNC_FRACTURE_MAX_QUEUED_JOBS.get()) {
            submitAsync(level, snapshot, center, planeNormal, fracturePower, heatMapManager, startedAt);
        } else {
            level.getServer().execute(() -> {
                if (GLOBAL_FRACTURES_THIS_TICK.get() < TrueImpactConfig.GLOBAL_MAX_FRACTURES_PER_TICK.get()) {
                    CandidateScan scan = candidates(snapshot, center, planeNormal, fracturePower);
                    if (!scan.candidates().isEmpty()) {
                        GLOBAL_FRACTURES_THIS_TICK.incrementAndGet();
                        int removed = applyCandidates(level, heatMapManager, scan.candidates());
                        TrueImpactPerformance.recordFracture(startedAt, scan.checkedBlocks(), scan.candidates().size(), removed);
                    }
                }
            });
        }
    }

    private static void checkAndResetGlobalBudget(long tick) {
        if (lastGlobalTick != tick) {
            lastGlobalTick = tick;
            GLOBAL_FRACTURES_THIS_TICK.set(0);
        }
    }

    private static double mass(Object subLevel) {
        try {
            Object tracker = GET_MASS_TRACKER.invoke(subLevel);
            return (double) GET_MASS.invoke(tracker);
        } catch (Exception e) { return 1000.0; }
    }

    private static boolean claimFractureBudget(ServerLevel level, Object subLevel) {
        long tick = level.getGameTime();
        String dimension = level.dimension().location().toString();
        TickBudget budget = FRACTURE_BUDGETS.computeIfAbsent(dimension, d -> new TickBudget(tick, 0));
        synchronized (budget) {
            if (budget.tick != tick) { budget.tick = tick; budget.used = 0; }
            if (budget.used >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.get()) return false;
            FractureKey key = new FractureKey(dimension, System.identityHashCode(subLevel));
            Long last = LAST_FRACTURE_TICK.get(key);
            if (last != null && last + TrueImpactConfig.SUBLEVEL_FRACTURE_COOLDOWN_TICKS.get() > tick) return false;
            LAST_FRACTURE_TICK.put(key, tick);
            budget.used++;
            return true;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        int maxApplied = TrueImpactConfig.ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK.get();
        int applied = 0;
        while (applied < maxApplied && !COMPLETED_FRACTURES.isEmpty()) {
            PendingFracture pf = COMPLETED_FRACTURES.poll();
            if (pf != null && GLOBAL_FRACTURES_THIS_TICK.get() < TrueImpactConfig.GLOBAL_MAX_FRACTURES_PER_TICK.get()) {
                GLOBAL_FRACTURES_THIS_TICK.incrementAndGet();
                applyCandidates(level, pf.heatMapManager, pf.candidates);
                applied++;
            }
        }
    }

    private static void submitAsync(ServerLevel level, FractureSnapshot snapshot, BlockPos center, Vector3d normal, double power, Object heatMapManager, long startedAt) {
        QUEUED_ASYNC_JOBS.incrementAndGet();
        FRACTURE_EXECUTOR.submit(() -> {
            try {
                CandidateScan scan = candidates(snapshot, center, normal, power);
                COMPLETED_FRACTURES.add(new PendingFracture(heatMapManager, scan.candidates));
            } finally {
                QUEUED_ASYNC_JOBS.decrementAndGet();
            }
        });
    }

    private static int applyCandidates(ServerLevel level, Object heatMapManager, List<BlockPos> candidates) {
        int count = 0;
        for (BlockPos pos : candidates) {
            try { GET_ON_SOLID_REMOVED.invoke(heatMapManager, pos); count++; } catch (Exception ignored) {}
        }
        return count;
    }

    private static CandidateScan candidates(FractureSnapshot snapshot, BlockPos center, Vector3d normal, double power) {
        List<BlockPos> candidates = new ArrayList<>();
        int checked = 0;
        int radius = radiusForSnapshot();
        double powerSq = power * power;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    checked++;
                    if (shouldFracture(snapshot, center, pos, normal, powerSq)) {
                        candidates.add(pos);
                    }
                    if (candidates.size() >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get()) break;
                }
                if (candidates.size() >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get()) break;
            }
            if (candidates.size() >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get()) break;
        }
        return new CandidateScan(candidates, checked);
    }

    private static boolean shouldFracture(FractureSnapshot snapshot, BlockPos center, BlockPos pos, Vector3d normal, double powerSq) {
        BlockState state = snapshot.states.get(pos.asLong());
        if (state == null || state.isAir()) return false;
        Vector3d toPos = new Vector3d(pos.getX() - center.getX(), pos.getY() - center.getY(), pos.getZ() - center.getZ());
        double distSq = toPos.lengthSquared();
        if (distSq > powerSq) return false;
        if (toPos.length() > 0.1) {
            double dot = Math.abs(toPos.normalize().dot(normal));
            if (dot > TrueImpactConfig.SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD.get()) return false;
        }
        double strength = MaterialImpactProperties.breakThreshold(state, state.getBlock().getExplosionResistance());
        return powerSq > (strength * strength * 0.5);
    }

    private static ServerLevel level(Object subLevel) {
        try { return (ServerLevel) GET_LEVEL.invoke(subLevel); } catch (Exception e) { return null; }
    }

    private static Object heatMapManager(Object subLevel) {
        try { return GET_HEAT_MAP_MANAGER.invoke(subLevel); } catch (Exception e) { return null; }
    }

    private static Vector3d toWorldPoint(Object subLevel, Vector3d local) {
        try {
            Object pose = LOGICAL_POSE.invoke(subLevel);
            Vector3d rp = (Vector3d) ROTATION_POINT.invoke(pose);
            return new Vector3d(local).add(rp);
        } catch (Exception e) { return null; }
    }

    private static int radiusForSnapshot() {
        return (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
    }

    private static double scaledForceAboveThreshold(double force, double threshold, double exponent) {
        if (force <= threshold) return 0;
        return Math.pow(force - threshold, exponent) + threshold;
    }

    private static Method findMethod(String cl, String m, Class<?>... params) {
        try { Method method = Class.forName(cl).getMethod(m, params); method.setAccessible(true); return method; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private record PendingFracture(Object heatMapManager, List<BlockPos> candidates) {}
    private record CandidateScan(List<BlockPos> candidates, int checkedBlocks) {}
    private record FractureKey(String dimension, int subLevelId) {}
    private static class TickBudget { long tick; int used; TickBudget(long t, int u) { tick = t; used = u; } }
    private static class FractureThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) { Thread t = new Thread(r, "TrueImpact-FractureThread"); t.setDaemon(true); return t; }
    }
    private static class FractureSnapshot {
        Map<Long, BlockState> states = new HashMap<>();
        static FractureSnapshot capture(ServerLevel level, BlockPos center, int radius) {
            FractureSnapshot s = new FractureSnapshot();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        p.set(center.getX()+x, center.getY()+y, center.getZ()+z);
                        s.states.put(p.asLong(), level.getBlockState(p));
                    }
                }
            }
            return s;
        }
    }
}
