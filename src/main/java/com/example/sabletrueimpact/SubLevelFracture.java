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
    private static final Method GET_CENTER_OF_MASS = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
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
        if (level == null) {
            return;
        }

        // Convert local-space contact point → world-space before touching block world.
        // Without this, ships near the origin would destroy blocks at (0,0,0).
        Vector3d worldPoint = toWorldPoint(subLevel, localPoint);
        if (worldPoint == null
                || !Double.isFinite(worldPoint.x)
                || !Double.isFinite(worldPoint.y)
                || !Double.isFinite(worldPoint.z)) {
            return;
        }

        if (!claimFractureBudget(level, subLevel)) {
            TrueImpactPerformance.recordFractureSkippedBudget();
            return;
        }

        BlockPos center = BlockPos.containing(worldPoint.x, worldPoint.y, worldPoint.z);
        double scaledForce = scaledForceAboveThreshold(
                forceAmount,
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get(),
                TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_EXPONENT.get()
        );
        double fracturePower = (scaledForce - TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get())
                * TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.get()
                * structureMultiplier(subLevel, localPoint)
                * TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
        if (fracturePower <= 0.0) {
            return;
        }

        Vector3d planeNormal = new Vector3d(normal);
        if (planeNormal.lengthSquared() < 1.0E-8) {
            planeNormal.set(0.0, 1.0, 0.0);
        } else {
            planeNormal.normalize();
        }

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

    private static boolean claimFractureBudget(ServerLevel level, Object subLevel) {
        long tick = level.getGameTime();
        String dimension = level.dimension().location().toString();
        TickBudget budget = FRACTURE_BUDGETS.computeIfAbsent(dimension, ignored -> new TickBudget(Long.MIN_VALUE, 0));
        synchronized (budget) {
            if (budget.tick != tick) {
                budget.tick = tick;
                budget.used = 0;
            }
            if (budget.used >= TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.get()) {
                return false;
            }
            FractureKey key = new FractureKey(dimension, System.identityHashCode(subLevel));
            long cooldown = TrueImpactConfig.SUBLEVEL_FRACTURE_COOLDOWN_TICKS.get();
            Long lastTick = LAST_FRACTURE_TICK.get(key);
            if (cooldown > 0 && lastTick != null && lastTick + cooldown > tick) {
                return false;
            }
            LAST_FRACTURE_TICK.put(key, tick);
            budget.used++;
            cleanupFractureCooldowns(tick);
            return true;
        }
    }

    private static void cleanupFractureCooldowns(long tick) {
        if (tick % 200L != 0L) {
            return;
        }
        LAST_FRACTURE_TICK.entrySet().removeIf(entry -> tick - entry.getValue() > 1200L);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || COMPLETED_FRACTURES.isEmpty()) {
            return;
        }
        String dimension = level.dimension().location().toString();
        int applied = 0;
        int maxApplied = TrueImpactConfig.ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK.get();
        int attempts = COMPLETED_FRACTURES.size();
        while (applied < maxApplied && attempts-- > 0) {
            PendingFracture pending = COMPLETED_FRACTURES.peek();
            if (pending == null) {
                break;
            }
            COMPLETED_FRACTURES.poll();
            if (!pending.dimension().equals(dimension)) {
                COMPLETED_FRACTURES.add(pending);
                continue;
            }
            int removed = applyCandidates(level, pending.heatMapManager(), pending.scan().candidates());
            TrueImpactPerformance.recordFracture(pending.startedAt(), pending.scan().checkedBlocks(), pending.scan().candidates().size(), removed);
            applied++;
        }
    }

    private static void submitAsync(ServerLevel level, FractureSnapshot snapshot, BlockPos center, Vector3d normal, double fracturePower, Object heatMapManager, long startedAt) {
        int maxQueued = TrueImpactConfig.ASYNC_FRACTURE_MAX_QUEUED_JOBS.get();
        if (QUEUED_ASYNC_JOBS.incrementAndGet() > maxQueued) {
            QUEUED_ASYNC_JOBS.decrementAndGet();
            return;
        }
        String dimension = level.dimension().location().toString();
        Vector3d normalCopy = new Vector3d(normal);
        FRACTURE_EXECUTOR.execute(() -> {
            try {
                CandidateScan scan = candidates(snapshot, center, normalCopy, fracturePower);
                COMPLETED_FRACTURES.add(new PendingFracture(dimension, heatMapManager, scan, startedAt));
            } finally {
                QUEUED_ASYNC_JOBS.decrementAndGet();
            }
        });
    }

    private static int applyCandidates(ServerLevel level, Object heatMapManager, List<Candidate> candidates) {
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int removed = 0;
        int maxBlocks = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get();
        for (Candidate candidate : candidates) {
            if (removed >= maxBlocks) {
                break;
            }
            BlockState current = level.getBlockState(candidate.pos());
            if (current.isAir() || current.is(Blocks.BEDROCK) || current.getDestroySpeed(level, candidate.pos()) < 0.0f) {
                continue;
            }
            boolean brokeFromFatigue = BlockDamageAccumulator.apply(
                    level,
                    candidate.pos(),
                    candidate.fatigueDamage() * TrueImpactConfig.SUBLEVEL_FRACTURE_FATIGUE_SCALE.get(),
                    candidate.breakThreshold(),
                    candidate.pos().hashCode() * 23
            );
            if (brokeFromFatigue) {
                notifyRemoved(heatMapManager, candidate.pos());
                removed++;
                continue;
            }
            if (!passesFractureChance(level, candidate)) {
                continue;
            }
            level.destroyBlock(candidate.pos(), true);
            notifyRemoved(heatMapManager, candidate.pos());
            removed++;
        }
        return removed;
    }

    private static CandidateScan candidates(FractureSnapshot snapshot, BlockPos center, Vector3d normal, double fracturePower) {
        List<Candidate> result = new ArrayList<>();
        int radius = (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double radiusSquared = TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get() * TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get();
        int maxChecks = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS.get();
        int maxCandidates = TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get();
        int checked = 0;

        for (Offset offset : offsets(radius)) {
            if (checked >= maxChecks || result.size() >= maxCandidates) {
                break;
            }
            if (offset.distanceSquared() > radiusSquared) {
                continue;
            }
            checked++;
            BlockPos pos = center.offset(offset.x(), offset.y(), offset.z());
            BlockState state = snapshot.getBlockState(pos);
            double destroySpeed = snapshot.destroySpeed(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK) || destroySpeed < 0.0f
                    || StructuralStrengthAnalyzer.isAdhesiveBlock(state)) {
                continue;
            }
            StructuralStrengthAnalyzer.Result structure = StructuralStrengthAnalyzer.analyze(snapshot, pos, state, normal);
            if (structure.seamWeakness() <= 0.0) {
                continue;
            }
            double hardness = Math.max(0.05, destroySpeed);
            double blast = Math.max(0.0, snapshot.blastResistance(pos));
            double baseResistance = (TrueImpactConfig.BASE_STRENGTH.get()
                    + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get()
                    + blast * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get());
            double connectionStrength = structure.connectionStrength();
            double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, baseResistance) * connectionStrength, 1.0);
            double materialToughness = Math.max(
                    MaterialImpactProperties.displayToughness(state, baseResistance) * connectionStrength,
                    materialStrength);
            double impactFocus = impactFocus(offset.distanceSquared());
            double rawStress = fracturePower * structure.seamWeakness() * impactFocus;
            double overStress = rawStress - materialStrength;
            if (overStress <= 0.0) {
                continue;
            }
            double fatigueDamage = MaterialImpactProperties.fatigueDamage(state, overStress);
            double breakThreshold = Math.max(materialToughness, 1.0);
            double crackRatio = snapshot.damageRatio(pos);
            double crackBonus = 1.0 + crackRatio * TrueImpactConfig.SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE.get();
            double spreadBonus = 1.0 + structure.weakPlaneSpread() * TrueImpactConfig.SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD.get();
            double score = fatigueDamage * crackBonus * spreadBonus / breakThreshold;
            result.add(new Candidate(pos.immutable(), score, fatigueDamage, breakThreshold));
        }
        return new CandidateScan(result, checked);
    }

    private static int radiusForSnapshot() {
        return (int) Math.ceil(TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get())
                + TrueImpactConfig.SUBLEVEL_FRACTURE_SNAPSHOT_PADDING.get();
    }

    private static double scaledForceAboveThreshold(double forceAmount, double threshold, double exponent) {
        if (exponent == 1.0 || forceAmount <= 0.0) {
            return forceAmount;
        }
        double reference = Math.max(threshold, 1.0);
        double normalized = Math.max(forceAmount / reference, 0.0);
        return forceAmount * Math.pow(normalized, exponent - 1.0);
    }

    private static List<Offset> offsets(int radius) {
        return OFFSET_CACHE.computeIfAbsent(radius, SubLevelFracture::buildOffsets);
    }

    private static List<Offset> buildOffsets(int radius) {
        List<Offset> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    double distanceSquared = x * x + y * y + z * z;
                    offsets.add(new Offset(x, y, z, distanceSquared));
                }
            }
        }
        offsets.sort(Comparator.comparingDouble(Offset::distanceSquared));
        return List.copyOf(offsets);
    }

    private static double impactFocus(double distanceSquared) {
        double radius = Math.max(0.001, TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double normalizedDistance = Math.min(1.0, Math.sqrt(distanceSquared) / radius);
        double focus = 1.0 - normalizedDistance;
        return Math.pow(Math.max(0.0, focus), TrueImpactConfig.SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT.get());
    }

    private static boolean passesFractureChance(ServerLevel level, Candidate candidate) {
        if (candidate.score() >= 1.0) {
            return true;
        }
        double chance = 1.0 - Math.exp(-candidate.score() * TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.get());
        return level.getRandom().nextDouble() < chance;
    }

    private static double structureMultiplier(Object subLevel, Vector3d localPoint) {
        Object massTracker = massTracker(subLevel);
        double mass = mass(massTracker);
        double massReference = Math.max(1.0, TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_REFERENCE.get());
        double massRatio = Math.max(0.0, mass / massReference);
        double massBonus = 1.0 + Math.log1p(massRatio) * TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_BONUS_SCALE.get();

        Vector3d centerOfMass = centerOfMass(massTracker);
        if (centerOfMass == null) {
            return massBonus;
        }
        double characteristicLength = Math.max(1.0, Math.cbrt(Math.max(mass, 1.0)));
        double offCenter = localPoint.distance(centerOfMass) / characteristicLength;
        double imbalanceBonus = 1.0 + offCenter * TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_BONUS_SCALE.get();
        imbalanceBonus = Math.min(TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_MAX_MULTIPLIER.get(), imbalanceBonus);
        return massBonus * imbalanceBonus;
    }

    private static Object massTracker(Object subLevel) {
        try {
            return GET_MASS_TRACKER.invoke(subLevel);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double mass(Object massTracker) {
        if (massTracker == null) {
            return 1.0;
        }
        try {
            return Math.max(1.0, ((Number) GET_MASS.invoke(massTracker)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    private static Vector3d centerOfMass(Object massTracker) {
        if (massTracker == null) {
            return null;
        }
        try {
            Object center = GET_CENTER_OF_MASS.invoke(massTracker);
            if (center == null) {
                return null;
            }
            Method x = center.getClass().getMethod("x");
            Method y = center.getClass().getMethod("y");
            Method z = center.getClass().getMethod("z");
            return new Vector3d(((Number) x.invoke(center)).doubleValue(), ((Number) y.invoke(center)).doubleValue(), ((Number) z.invoke(center)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static ServerLevel level(Object subLevel) {
        try {
            Object level = GET_LEVEL.invoke(subLevel);
            return level instanceof ServerLevel serverLevel ? serverLevel : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object heatMapManager(Object subLevel) {
        try {
            return GET_HEAT_MAP_MANAGER.invoke(subLevel);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void notifyRemoved(Object heatMapManager, BlockPos pos) {
        if (heatMapManager == null) {
            return;
        }
        try {
            GET_ON_SOLID_REMOVED.invoke(heatMapManager, pos);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method findMethod(String className, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }

    /** Resolves a Rapier local-space point to Minecraft world-space by adding the sub-level's rotation point. */
    static Vector3d toWorldPoint(Object subLevel, Vector3d localPoint) {
        try {
            Object pose = LOGICAL_POSE.invoke(subLevel);
            Object rp = ROTATION_POINT.invoke(pose);
            double rx = ((Number) rp.getClass().getMethod("x").invoke(rp)).doubleValue();
            double ry = ((Number) rp.getClass().getMethod("y").invoke(rp)).doubleValue();
            double rz = ((Number) rp.getClass().getMethod("z").invoke(rp)).doubleValue();
            return new Vector3d(localPoint.x + rx, localPoint.y + ry, localPoint.z + rz);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private record Candidate(BlockPos pos, double score, double fatigueDamage, double breakThreshold) {
    }

    private record CandidateScan(List<Candidate> candidates, int checkedBlocks) {
    }

    private record PendingFracture(String dimension, Object heatMapManager, CandidateScan scan, long startedAt) {
    }

    private record Offset(int x, int y, int z, double distanceSquared) {
    }

    private record FractureKey(String dimension, int subLevelId) {
    }

    private static final class TickBudget {
        private long tick;
        private int used;

        private TickBudget(long tick, int used) {
            this.tick = tick;
            this.used = used;
        }
    }

    private static final class FractureSnapshot implements StructuralStrengthAnalyzer.BlockLookup {
        private final Map<Long, BlockState> states;
        private final Map<Long, BlockMaterial> materials;
        private final Map<Long, Double> damageRatios;
        private final Set<Long> glueEntities;

        private FractureSnapshot(Map<Long, BlockState> states, Map<Long, BlockMaterial> materials, Map<Long, Double> damageRatios, Set<Long> glueEntities) {
            this.states = states;
            this.materials = materials;
            this.damageRatios = damageRatios;
            this.glueEntities = glueEntities;
        }

        private static FractureSnapshot capture(ServerLevel level, BlockPos center, int radius) {
            Map<Long, BlockState> states = new HashMap<>();
            Map<Long, BlockMaterial> materials = new HashMap<>();
            Map<Long, Double> damageRatios = new HashMap<>();
            Set<Long> glueEntities = new HashSet<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        long key = pos.asLong();
                        BlockState state = level.getBlockState(pos);
                        states.put(key, state);
                        materials.put(key, new BlockMaterial(state.getDestroySpeed(level, pos), state.getBlock().getExplosionResistance()));
                        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0f) {
                            double hardness = Math.max(0.05, state.getDestroySpeed(level, pos));
                            double blast = Math.max(0.0, state.getBlock().getExplosionResistance());
                            double baseThreshold = TrueImpactConfig.BASE_STRENGTH.get()
                                    + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get()
                                    + blast * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get();
                            double breakThreshold = Math.max(
                                    Math.max(
                                            MaterialImpactProperties.displayStrength(state, baseThreshold),
                                            MaterialImpactProperties.displayToughness(state, baseThreshold)
                                    ),
                                    1.0);
                            damageRatios.put(key, BlockDamageAccumulator.damageRatio(level, pos, breakThreshold));
                        }
                        if (StructuralStrengthAnalyzer.hasGlueEntity(level, pos)) {
                            glueEntities.add(key);
                        }
                    }
                }
            }
            return new FractureSnapshot(states, materials, damageRatios, glueEntities);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
        }

        private double destroySpeed(BlockPos pos) {
            BlockMaterial material = materials.get(pos.asLong());
            return material == null ? 0.0 : material.destroySpeed();
        }

        private double blastResistance(BlockPos pos) {
            BlockMaterial material = materials.get(pos.asLong());
            return material == null ? 0.0 : material.blastResistance();
        }

        private double damageRatio(BlockPos pos) {
            return damageRatios.getOrDefault(pos.asLong(), 0.0);
        }

        @Override
        public boolean hasGlueEntity(BlockPos pos) {
            return glueEntities.contains(pos.asLong());
        }
    }

    private record BlockMaterial(float destroySpeed, float blastResistance) {
    }

    private static final class FractureThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Sable True Impact Fracture Worker");
            thread.setDaemon(true);
            return thread;
        }
    }
}
