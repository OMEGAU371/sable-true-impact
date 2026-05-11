package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;

import java.lang.reflect.Field;
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
    
    private static final Map<Integer, List<Offset>> OFFSET_CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService FRACTURE_EXECUTOR = Executors.newSingleThreadExecutor(new FractureThreadFactory());
    private static final ConcurrentLinkedQueue<PendingFracture> COMPLETED_FRACTURES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED_ASYNC_JOBS = new AtomicInteger();
    private static final Map<FractureKey, Long> LAST_FRACTURE_TICK = new ConcurrentHashMap<>();
    private static final Map<String, TickBudget> FRACTURE_BUDGETS = new ConcurrentHashMap<>();

    private SubLevelFracture() {
    }

    public static void tryFracture(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.get()
                || subLevel == null
                || forceAmount < TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()
                || TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get() <= 0) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();

        ServerLevel level = level(subLevel);
        if (level == null) return;

        Vector3d worldPoint = toWorldPoint(subLevel, localPoint);
        if (worldPoint == null || !Double.isFinite(worldPoint.x)) return;

        if (!claimFractureBudget(level, subLevel)) {
            TrueImpactPerformance.recordFractureSkippedBudget();
            return;
        }

        BlockPos center = BlockPos.containing(worldPoint.x, worldPoint.y, worldPoint.z);
        double scaledForce = scaledForceAboveThreshold(forceAmount, TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get(), TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_EXPONENT.get());
        double fracturePower = (scaledForce - TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get())
                * TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.get()
                * structureMultiplier(subLevel, localPoint)
                * TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
        
        if (fracturePower <= 0.0) return;

        Vector3d planeNormal = new Vector3d(normal);
        if (planeNormal.lengthSquared() < 1.0E-8) planeNormal.set(0.0, 1.0, 0.0);
        else planeNormal.normalize();

        FractureSnapshot snapshot = FractureSnapshot.capture(level, center, radiusForSnapshot());
        Object heatMapManager = heatMapManager(subLevel);
        
        if (TrueImpactConfig.ENABLE_ASYNC_FRACTURE_ANALYSIS.get()) {
            submitAsync(level, snapshot, center, planeNormal, fracturePower, heatMapManager, startedAt);
            return;
        }
        level.getServer().execute(() -> {
            CandidateScan scan = candidates(snapshot, center, planeNormal, fracturePower);
            int removed = applyCandidates(level, heatMapManager, scan.candidates());
            TrueImpactPerformance.recordFracture(startedAt, scan.checkedBlocks(), scan.candidates().size(), removed);
        });
    }

    private static CandidateScan candidates(FractureSnapshot snapshot, BlockPos center, Vector3d normal, double fracturePower) {
        List<Candidate> result = new ArrayList<>();
        int radius = (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double radiusSquared = Math.pow(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get(), 2);
        int maxChecks = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS.get();
        int maxCandidates = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get();
        int checked = 0;

        for (Offset offset : offsets(radius)) {
            if (checked >= maxChecks || result.size() >= maxCandidates) break;
            if (offset.distanceSquared() > radiusSquared) continue;
            checked++;
            
            BlockPos pos = center.offset(offset.x(), offset.y(), offset.z());
            BlockState state = snapshot.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK) || snapshot.destroySpeed(pos) < 0.0f || StructuralStrengthAnalyzer.isAdhesiveBlock(state)) continue;

            StructuralStrengthAnalyzer.Result structure = StructuralStrengthAnalyzer.analyze(snapshot, pos, state, normal);
            if (structure.seamWeakness() <= 0.0) continue;

            double hardness = Math.max(0.05, snapshot.destroySpeed(pos));
            double blast = Math.max(0.0, snapshot.blastResistance(pos));
            double baseResistance = TrueImpactConfig.BASE_STRENGTH.get()
                    + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get()
                    + blast * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get();
            
            // The ultimate compounded threshold (Strength * Toughness)
            double breakThreshold = Math.max(MaterialImpactProperties.breakThreshold(state, baseResistance) * structure.connectionStrength(), 1.0);
            
            // Reference strength (without toughness) to identify relative toughness bonus
            double refStrength = Math.max(baseResistance * MaterialImpactProperties.strengthMultiplier(state) * structure.connectionStrength(), 1.0);
            double toughnessBonus = Math.max(1.0, breakThreshold / refStrength);

            double impactFocus = impactFocus(offset.distanceSquared());
            double rawStress = fracturePower * structure.seamWeakness() * impactFocus;
            
            // Use compounded breakThreshold for stress判定
            double overStress = rawStress - (refStrength * 1.5); // Allow some wiggle room before fatigue starts
            if (overStress <= 0.0) continue;

            double fatigueDamage = MaterialImpactProperties.fatigueDamage(state, overStress);
            double crackBonus = 1.0 + snapshot.damageRatio(pos) * TrueImpactConfig.SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE.get();
            double spreadBonus = 1.0 + structure.weakPlaneSpread() * TrueImpactConfig.SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD.get();
            
            // Cubed toughness bonus for elite materials like Netherite
            double score = (fatigueDamage * crackBonus * spreadBonus) / (breakThreshold * Math.pow(toughnessBonus, 2));
            
            if (score > 1.0E-8) {
                result.add(new Candidate(pos.immutable(), score, fatigueDamage, breakThreshold));
            }
        }
        return new CandidateScan(result, checked);
    }

    private static int applyCandidates(ServerLevel level, Object heatMapManager, List<Candidate> candidates) {
        if (candidates.isEmpty()) return 0;
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int removed = 0;
        int limit = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get();
        for (Candidate candidate : candidates) {
            if (removed >= limit) break;
            if (BlockDamageAccumulator.apply(level, candidate.pos(), candidate.fatigueDamage(), candidate.breakThreshold(), candidate.pos().hashCode() * 23)) {
                notifyRemoved(heatMapManager, candidate.pos());
                removed++;
                continue;
            }
            if (passesFractureChance(level, candidate)) {
                level.destroyBlock(candidate.pos(), true);
                notifyRemoved(heatMapManager, candidate.pos());
                removed++;
            }
        }
        return removed;
    }

    private static boolean passesFractureChance(ServerLevel level, Candidate candidate) {
        return level.getRandom().nextDouble() < candidate.score() * TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.get();
    }

    private static int radiusForSnapshot() {
        return (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get()) + 1;
    }

    private static double impactFocus(double distanceSq) {
        return 1.0 / Math.pow(1.0 + distanceSq, TrueImpactConfig.SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT.get());
    }

    private static double structureMultiplier(Object subLevel, Vector3d localPoint) {
        try {
            double mass = mass(subLevel);
            if (mass < TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_REFERENCE.get()) return 1.0;
            return 1.0 + (mass / TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_REFERENCE.get()) * TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_BONUS_SCALE.get();
        } catch (Exception e) { return 1.0; }
    }

    private static Vector3d toWorldPoint(Object subLevel, Vector3d local) {
        try {
            Vector3d rp = rotationPoint(subLevel);
            return rp == null ? null : new Vector3d(local).add(rp);
        } catch (Exception e) { return null; }
    }

    private static void notifyRemoved(Object heatMapManager, BlockPos pos) {
        try { if (heatMapManager != null) GET_ON_SOLID_REMOVED.invoke(heatMapManager, pos); } catch (Exception ignored) {}
    }

    private static void submitAsync(ServerLevel level, FractureSnapshot snapshot, BlockPos center, Vector3d normal, double power, Object hmm, long start) {
        if (QUEUED_ASYNC_JOBS.get() >= TrueImpactConfig.ASYNC_FRACTURE_MAX_QUEUED_JOBS.get()) return;
        QUEUED_ASYNC_JOBS.incrementAndGet();
        FRACTURE_EXECUTOR.submit(() -> {
            try {
                CandidateScan scan = candidates(snapshot, center, normal, power);
                COMPLETED_FRACTURES.add(new PendingFracture(level, hmm, scan, start));
            } finally { QUEUED_ASYNC_JOBS.decrementAndGet(); }
        });
    }

    private static double scaledForceAboveThreshold(double forceAmount, double threshold, double exponent) {
        if (exponent == 1.0 || forceAmount <= 0.0) return forceAmount;
        double reference = Math.max(threshold, 1.0);
        return forceAmount * Math.pow(Math.max(forceAmount / reference, 0.0), exponent - 1.0);
    }

    private static boolean claimFractureBudget(ServerLevel level, Object subLevel) {
        long tick = level.getGameTime();
        String dim = level.dimension().location().toString();
        TickBudget budget = FRACTURE_BUDGETS.computeIfAbsent(dim, k -> new TickBudget(Long.MIN_VALUE, 0));
        synchronized (budget) {
            if (budget.tick != tick) { budget.tick = tick; budget.used = 0; }
            if (budget.used >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.get()) return false;
            FractureKey key = new FractureKey(dim, System.identityHashCode(subLevel));
            Long last = LAST_FRACTURE_TICK.get(key);
            if (last != null && last + TrueImpactConfig.SUBLEVEL_FRACTURE_COOLDOWN_TICKS.get() > tick) return false;
            LAST_FRACTURE_TICK.put(key, tick); budget.used++;
            return true;
        }
    }

    private static List<Offset> offsets(int radius) { return OFFSET_CACHE.computeIfAbsent(radius, SubLevelFracture::buildOffsets); }
    private static List<Offset> buildOffsets(int radius) {
        List<Offset> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++)
            for (int y = -radius; y <= radius; y++)
                for (int z = -radius; z <= radius; z++)
                    offsets.add(new Offset(x, y, z, x*x + y*y + z*z));
        offsets.sort(Comparator.comparingDouble(Offset::distanceSquared));
        return offsets;
    }

    private static ServerLevel level(Object subLevel) { try { return (ServerLevel) GET_LEVEL.invoke(subLevel); } catch (Exception e) { return null; } }
    private static Object heatMapManager(Object subLevel) { try { return GET_HEAT_MAP_MANAGER.invoke(subLevel); } catch (Exception e) { return null; } }
    private static double mass(Object subLevel) { try { return ((Number) GET_MASS.invoke(GET_MASS_TRACKER.invoke(subLevel))).doubleValue(); } catch (Exception e) { return 1.0; } }
    private static Vector3d rotationPoint(Object subLevel) { try { Object rp = ROTATION_POINT.invoke(LOGICAL_POSE.invoke(subLevel)); return new Vector3d((double)rp.getClass().getMethod("x").invoke(rp), (double)rp.getClass().getMethod("y").invoke(rp), (double)rp.getClass().getMethod("z").invoke(rp)); } catch (Exception e) { return null; } }
    private static Method findMethod(String cl, String m, Class<?>... args) { try { Method method = Class.forName(cl).getMethod(m, args); method.setAccessible(true); return method; } catch (Exception e) { throw new RuntimeException(e); } }

    private record Offset(int x, int y, int z, double distanceSquared) {}
    private record Candidate(BlockPos pos, double score, double fatigueDamage, double breakThreshold) {}
    private record CandidateScan(List<Candidate> candidates, int checkedBlocks) {}
    private record PendingFracture(ServerLevel level, Object heatMapManager, CandidateScan scan, long startedAt) {}
    private record FractureKey(String dimension, int identityHash) {}
    private static class TickBudget { long tick; int used; TickBudget(long t, int u) { this.tick = t; this.used = u; } }

    private static final class FractureSnapshot implements StructuralStrengthAnalyzer.BlockLookup {
        private final Map<Long, BlockState> states;
        private final Map<Long, BlockMaterial> materials;
        private final Map<Long, Double> damageRatios;
        private final Set<Long> glueEntities;
        private FractureSnapshot(Map<Long, BlockState> states, Map<Long, BlockMaterial> materials, Map<Long, Double> damageRatios, Set<Long> glueEntities) { this.states = states; this.materials = materials; this.damageRatios = damageRatios; this.glueEntities = glueEntities; }
        public static FractureSnapshot capture(ServerLevel level, BlockPos center, int radius) {
            Map<Long, BlockState> states = new HashMap<>(); Map<Long, BlockMaterial> materials = new HashMap<>(); Map<Long, Double> damageRatios = new HashMap<>(); Set<Long> glueEntities = new HashSet<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        long key = pos.asLong(); BlockState state = level.getBlockState(pos);
                        states.put(key, state); materials.put(key, new BlockMaterial(state.getDestroySpeed(level, pos), state.getBlock().getExplosionResistance()));
                        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f) {
                            double br = TrueImpactConfig.BASE_STRENGTH.get() + Math.max(0.05, state.getDestroySpeed(level, pos)) * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get() + Math.max(0.0, state.getBlock().getExplosionResistance()) * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get();
                            damageRatios.put(key, BlockDamageAccumulator.damageRatio(level, pos, MaterialImpactProperties.breakThreshold(state, br)));
                        }
                        if (StructuralStrengthAnalyzer.hasGlueEntity(level, pos)) glueEntities.add(key);
                    }
                }
            }
            return new FractureSnapshot(states, materials, damageRatios, glueEntities);
        }
        @Override public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState()); }
        public double destroySpeed(BlockPos pos) { BlockMaterial m = materials.get(pos.asLong()); return m == null ? 0.0 : m.destroySpeed(); }
        public double blastResistance(BlockPos pos) { BlockMaterial m = materials.get(pos.asLong()); return m == null ? 0.0 : m.blastResistance(); }
        public double damageRatio(BlockPos pos) { return damageRatios.getOrDefault(pos.asLong(), 0.0); }
        @Override public boolean hasGlueEntity(BlockPos pos) { return glueEntities.contains(pos.asLong()); }
    }
    private record BlockMaterial(float destroySpeed, float blastResistance) {}
    private static final class FractureThreadFactory implements ThreadFactory { public Thread newThread(Runnable r) { Thread t = new Thread(r, "Sable True Impact Fracture Worker"); t.setDaemon(true); return t; } }
}
