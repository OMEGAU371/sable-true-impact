/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.tick.LevelTickEvent$Post
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.BlockDamageAccumulator;
import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.StructuralStrengthAnalyzer;
import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPerformance;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SubLevelFracture {
    private static final Method GET_LEVEL = SubLevelFracture.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel", new Class[0]);
    private static final Method GET_HEAT_MAP_MANAGER = SubLevelFracture.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getHeatMapManager", new Class[0]);
    private static final Method GET_MASS_TRACKER = SubLevelFracture.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker", new Class[0]);
    private static final Method GET_MASS = SubLevelFracture.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass", new Class[0]);
    private static final Method GET_CENTER_OF_MASS = SubLevelFracture.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass", new Class[0]);
    private static final Method GET_ON_SOLID_REMOVED = SubLevelFracture.findMethod("dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager", "onSolidRemoved", BlockPos.class);
    private static final Method LOGICAL_POSE = SubLevelFracture.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose", new Class[0]);
    private static final Method ROTATION_POINT = SubLevelFracture.findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint", new Class[0]);
    // Sublevel blocks are embedded in the real world at plotLocalPos + plotCenter (fixed offset).
    // rotationPoint is the physics-world position and diverges from plotCenter when the structure moves.
    private static final Method GET_PLOT = SubLevelFracture.findMethodSafe("dev.ryanhcode.sable.sublevel.SubLevel", "getPlot", new Class[0]);
    private static final Method GET_CENTER_BLOCK = SubLevelFracture.findMethodSafe("dev.ryanhcode.sable.sublevel.plot.LevelPlot", "getCenterBlock", new Class[0]);
    private static final Method GET_BOUNDING_BOX = SubLevelFracture.findMethodSafe("dev.ryanhcode.sable.sublevel.plot.LevelPlot", "getBoundingBox", new Class[0]);
    private static final Map<Integer, List<Offset>> OFFSET_CACHE = new ConcurrentHashMap<Integer, List<Offset>>();
    private static final ExecutorService FRACTURE_EXECUTOR = Executors.newSingleThreadExecutor(new FractureThreadFactory());
    private static final ConcurrentLinkedQueue<PendingFracture> COMPLETED_FRACTURES = new ConcurrentLinkedQueue();
    private static final AtomicInteger QUEUED_ASYNC_JOBS = new AtomicInteger();
    private static final Map<FractureKey, Long> LAST_FRACTURE_TICK = new ConcurrentHashMap<FractureKey, Long>();
    private static final Map<String, TickBudget> FRACTURE_BUDGETS = new ConcurrentHashMap<String, TickBudget>();

    private SubLevelFracture() {
    }

    public static void tryFracture(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount) {
        SubLevelFracture.tryFracture(subLevel, localPoint, normal, forceAmount, 1.0);
    }

    public static void tryFracture(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount, double externalDamageScale) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.get()).booleanValue() || subLevel == null || externalDamageScale <= 0.0 || forceAmount < (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get() || (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get() <= 0) {
            return;
        }
        long startedAt = TrueImpactPerformance.start();
        ServerLevel level = SubLevelFracture.level(subLevel);
        if (level == null) {
            return;
        }
        Vector3d worldPoint = SubLevelFracture.toWorldPoint(subLevel, localPoint);
        if (!(worldPoint != null && Double.isFinite(worldPoint.x) && Double.isFinite(worldPoint.y) && Double.isFinite(worldPoint.z))) {
            return;
        }
        if (!SubLevelFracture.claimFractureBudget(level, subLevel)) {
            TrueImpactPerformance.recordFractureSkippedBudget();
            return;
        }
        BlockPos center = BlockPos.containing((double)worldPoint.x, (double)worldPoint.y, (double)worldPoint.z);
        SubLevelBounds bounds = SubLevelFracture.subLevelBounds(subLevel);
        if (bounds == null || !bounds.contains(center)) {
            return;
        }
        double scaledForce = SubLevelFracture.scaledForceAboveThreshold(forceAmount, (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get(), (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_EXPONENT.get());
        double fracturePower = (scaledForce - (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_SCALE.get() * SubLevelFracture.structureMultiplier(subLevel, localPoint) * (Double)TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get() * externalDamageScale;
        if (fracturePower <= 0.0) {
            return;
        }
        Vector3d planeNormal = new Vector3d((Vector3dc)normal);
        if (planeNormal.lengthSquared() < 1.0E-8) {
            planeNormal.set(0.0, 1.0, 0.0);
        } else {
            planeNormal.normalize();
        }
        FractureSnapshot snapshot = FractureSnapshot.capture(level, center, SubLevelFracture.radiusForSnapshot());
        Object heatMapManager = SubLevelFracture.heatMapManager(subLevel);
        if (((Boolean)TrueImpactConfig.ENABLE_ASYNC_FRACTURE_ANALYSIS.get()).booleanValue()) {
            SubLevelFracture.submitAsync(level, snapshot, center, planeNormal, fracturePower, heatMapManager, bounds, startedAt);
            return;
        }
        level.getServer().execute(() -> {
            CandidateScan scan = SubLevelFracture.candidates(snapshot, center, planeNormal, fracturePower, bounds);
            int removed = SubLevelFracture.applyCandidates(level, heatMapManager, scan.candidates());
            TrueImpactPerformance.recordFracture(startedAt, scan.checkedBlocks(), scan.candidates().size(), removed);
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static boolean claimFractureBudget(ServerLevel level, Object subLevel) {
        TickBudget budget;
        long tick = level.getGameTime();
        String dimension = level.dimension().location().toString();
        TickBudget tickBudget = budget = FRACTURE_BUDGETS.computeIfAbsent(dimension, ignored -> new TickBudget(Long.MIN_VALUE, 0));
        synchronized (tickBudget) {
            if (budget.tick != tick) {
                budget.tick = tick;
                budget.used = 0;
            }
            if (budget.used >= (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.get()) {
                return false;
            }
            FractureKey key = new FractureKey(dimension, System.identityHashCode(subLevel));
            long cooldown = ((Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_COOLDOWN_TICKS.get()).intValue();
            Long lastTick = LAST_FRACTURE_TICK.get(key);
            if (cooldown > 0L && lastTick != null && lastTick + cooldown > tick) {
                return false;
            }
            LAST_FRACTURE_TICK.put(key, tick);
            ++budget.used;
            SubLevelFracture.cleanupFractureCooldowns(tick);
            return true;
        }
    }

    private static void cleanupFractureCooldowns(long tick) {
        if (tick % 200L != 0L) {
            return;
        }
        LAST_FRACTURE_TICK.entrySet().removeIf(entry -> tick - (Long)entry.getValue() > 1200L);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        PendingFracture pending;
        ServerLevel level;
        block6: {
            block5: {
                Level level2 = event.getLevel();
                if (!(level2 instanceof ServerLevel)) break block5;
                level = (ServerLevel)level2;
                if (!COMPLETED_FRACTURES.isEmpty()) break block6;
            }
            return;
        }
        String dimension = level.dimension().location().toString();
        int applied = 0;
        int maxApplied = (Integer)TrueImpactConfig.ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK.get();
        int attempts = COMPLETED_FRACTURES.size();
        while (applied < maxApplied && attempts-- > 0 && (pending = COMPLETED_FRACTURES.peek()) != null) {
            COMPLETED_FRACTURES.poll();
            if (!pending.dimension().equals(dimension)) {
                COMPLETED_FRACTURES.add(pending);
                continue;
            }
            int removed = SubLevelFracture.applyCandidates(level, pending.heatMapManager(), pending.scan().candidates());
            TrueImpactPerformance.recordFracture(pending.startedAt(), pending.scan().checkedBlocks(), pending.scan().candidates().size(), removed);
            ++applied;
        }
    }

    private static void submitAsync(ServerLevel level, FractureSnapshot snapshot, BlockPos center, Vector3d normal, double fracturePower, Object heatMapManager, SubLevelBounds bounds, long startedAt) {
        int maxQueued = (Integer)TrueImpactConfig.ASYNC_FRACTURE_MAX_QUEUED_JOBS.get();
        if (QUEUED_ASYNC_JOBS.incrementAndGet() > maxQueued) {
            QUEUED_ASYNC_JOBS.decrementAndGet();
            return;
        }
        String dimension = level.dimension().location().toString();
        Vector3d normalCopy = new Vector3d((Vector3dc)normal);
        FRACTURE_EXECUTOR.execute(() -> {
            try {
                CandidateScan scan = SubLevelFracture.candidates(snapshot, center, normalCopy, fracturePower, bounds);
                COMPLETED_FRACTURES.add(new PendingFracture(dimension, heatMapManager, scan, startedAt));
            }
            finally {
                QUEUED_ASYNC_JOBS.decrementAndGet();
            }
        });
    }

    private static int applyCandidates(ServerLevel level, Object heatMapManager, List<Candidate> candidates) {
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        int removed = 0;
        int maxBlocks = (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_BLOCKS.get();
        for (Candidate candidate : candidates) {
            if (removed >= maxBlocks) break;
            BlockState current = level.getBlockState(candidate.pos());
            if (current.isAir() || current.is(Blocks.BEDROCK) || current.getDestroySpeed((BlockGetter)level, candidate.pos()) < 0.0f || !MaterialImpactProperties.isDestructible(current, true)) continue;
            boolean brokeFromFatigue = BlockDamageAccumulator.apply(level, candidate.pos(), candidate.fatigueDamage() * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FATIGUE_SCALE.get(), candidate.breakThreshold(), candidate.pos().hashCode() * 23);
            if (brokeFromFatigue) {
                SubLevelFracture.notifyRemoved(heatMapManager, candidate.pos());
                ++removed;
                continue;
            }
            if (!SubLevelFracture.passesFractureChance(level, candidate)) continue;
            level.destroyBlock(candidate.pos(), true);
            SubLevelFracture.notifyRemoved(heatMapManager, candidate.pos());
            ++removed;
        }
        return removed;
    }

    private static CandidateScan candidates(FractureSnapshot snapshot, BlockPos center, Vector3d normal, double fracturePower, SubLevelBounds bounds) {
        ArrayList<Candidate> result = new ArrayList<Candidate>();
        int radius = (int)Math.ceil((Double)TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double radiusSquared = (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get() * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get();
        int maxChecks = (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS.get();
        int maxCandidates = (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_CANDIDATES.get();
        int checked = 0;
        for (Offset offset : SubLevelFracture.offsets(radius)) {
            StructuralStrengthAnalyzer.Result structure;
            if (checked >= maxChecks || result.size() >= maxCandidates) break;
            if (offset.distanceSquared() > radiusSquared) continue;
            ++checked;
            BlockPos pos = center.offset(offset.x(), offset.y(), offset.z());
            if (bounds == null || !bounds.contains(pos)) continue;
            BlockState state = snapshot.getBlockState(pos);
            double destroySpeed = snapshot.destroySpeed(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK) || destroySpeed < 0.0 || StructuralStrengthAnalyzer.isAdhesiveBlock(state) || (structure = StructuralStrengthAnalyzer.analyze(snapshot, pos, state, normal)).seamWeakness() <= 0.0) continue;
            double baseResistance = MaterialImpactProperties.baseStrength(destroySpeed, snapshot.blastResistance(pos));
            double connectionStrength = structure.connectionStrength();
            double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, baseResistance) * connectionStrength, 1.0);
            double materialToughness = Math.max(MaterialImpactProperties.displayToughness(state, baseResistance) * connectionStrength, materialStrength);
            double impactFocus = SubLevelFracture.impactFocus(offset.distanceSquared());
            double rawStress = fracturePower * structure.seamWeakness() * impactFocus;
            double overStress = rawStress - materialStrength;
            if (overStress <= 0.0) continue;
            double toughnessBonus = materialToughness / materialStrength;
            double fatigueDamage = MaterialImpactProperties.fatigueDamage(state, overStress);
            double breakThreshold = Math.max(materialToughness, 1.0);
            double crackRatio = snapshot.damageRatio(pos);
            double crackBonus = 1.0 + crackRatio * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE.get();
            double spreadBonus = 1.0 + structure.weakPlaneSpread() * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD.get();
            double score = fatigueDamage * crackBonus * spreadBonus / (breakThreshold * toughnessBonus);
            result.add(new Candidate(pos.immutable(), score, fatigueDamage, breakThreshold));
        }
        return new CandidateScan(result, checked);
    }

    private static int radiusForSnapshot() {
        return (int)Math.ceil((Double)TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get()) + (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_SNAPSHOT_PADDING.get();
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
        ArrayList<Offset> offsets = new ArrayList<Offset>();
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                for (int z = -radius; z <= radius; ++z) {
                    double distanceSquared = x * x + y * y + z * z;
                    offsets.add(new Offset(x, y, z, distanceSquared));
                }
            }
        }
        offsets.sort(Comparator.comparingDouble(Offset::distanceSquared));
        return List.copyOf(offsets);
    }

    private static double impactFocus(double distanceSquared) {
        double radius = Math.max(0.001, (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_RADIUS.get());
        double normalizedDistance = Math.min(1.0, Math.sqrt(distanceSquared) / radius);
        double focus = 1.0 - normalizedDistance;
        return Math.pow(Math.max(0.0, focus), (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT.get());
    }

    private static boolean passesFractureChance(ServerLevel level, Candidate candidate) {
        if (candidate.score() >= 1.0) {
            return true;
        }
        double chance = 1.0 - Math.exp(-candidate.score() * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_CHANCE_SCALE.get());
        return level.getRandom().nextDouble() < chance;
    }

    private static double structureMultiplier(Object subLevel, Vector3d localPoint) {
        Object massTracker = SubLevelFracture.massTracker(subLevel);
        double mass = SubLevelFracture.mass(massTracker);
        double massReference = Math.max(1.0, (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_REFERENCE.get());
        double massRatio = Math.max(0.0, mass / massReference);
        double massBonus = 1.0 + Math.log1p(massRatio) * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_MASS_BONUS_SCALE.get();
        Vector3d centerOfMass = SubLevelFracture.centerOfMass(massTracker);
        if (centerOfMass == null) {
            return massBonus;
        }
        double characteristicLength = Math.max(1.0, Math.cbrt(Math.max(mass, 1.0)));
        double offCenter = localPoint.distance((Vector3dc)centerOfMass) / characteristicLength;
        double imbalanceBonus = 1.0 + offCenter * (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_BONUS_SCALE.get();
        imbalanceBonus = Math.min((Double)TrueImpactConfig.SUBLEVEL_FRACTURE_IMBALANCE_MAX_MULTIPLIER.get(), imbalanceBonus);
        return massBonus * imbalanceBonus;
    }

    private static Object massTracker(Object subLevel) {
        try {
            return GET_MASS_TRACKER.invoke(subLevel, new Object[0]);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double mass(Object massTracker) {
        if (massTracker == null) {
            return 1.0;
        }
        try {
            return Math.max(1.0, ((Number)GET_MASS.invoke(massTracker, new Object[0])).doubleValue());
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    private static Vector3d centerOfMass(Object massTracker) {
        if (massTracker == null) {
            return null;
        }
        try {
            Object center = GET_CENTER_OF_MASS.invoke(massTracker, new Object[0]);
            if (center == null) {
                return null;
            }
            Method x = center.getClass().getMethod("x", new Class[0]);
            Method y = center.getClass().getMethod("y", new Class[0]);
            Method z = center.getClass().getMethod("z", new Class[0]);
            return new Vector3d(((Number)x.invoke(center, new Object[0])).doubleValue(), ((Number)y.invoke(center, new Object[0])).doubleValue(), ((Number)z.invoke(center, new Object[0])).doubleValue());
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static ServerLevel level(Object subLevel) {
        try {
            ServerLevel serverLevel;
            Object level = GET_LEVEL.invoke(subLevel, new Object[0]);
            return level instanceof ServerLevel ? (serverLevel = (ServerLevel)level) : null;
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object heatMapManager(Object subLevel) {
        try {
            return GET_HEAT_MAP_MANAGER.invoke(subLevel, new Object[0]);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void notifyRemoved(Object heatMapManager, BlockPos pos) {
        if (heatMapManager == null) {
            return;
        }
        try {
            GET_ON_SOLID_REMOVED.invoke(heatMapManager, pos);
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            // empty catch block
        }
    }

    private static Method findMethod(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }

    private static Method findMethodSafe(String className, String methodName, Class<?> ... parameterTypes) {
        try {
            Method method = Class.forName(className).getMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static BlockPos plotCenter(Object subLevel) {
        if (GET_PLOT == null || GET_CENTER_BLOCK == null) return null;
        try {
            Object plot = GET_PLOT.invoke(subLevel, new Object[0]);
            if (plot == null) return null;
            return (BlockPos) GET_CENTER_BLOCK.invoke(plot, new Object[0]);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static SubLevelBounds subLevelBounds(Object subLevel) {
        if (GET_PLOT == null || GET_CENTER_BLOCK == null || GET_BOUNDING_BOX == null) return null;
        try {
            Object plot = GET_PLOT.invoke(subLevel, new Object[0]);
            if (plot == null) return null;
            BlockPos center = (BlockPos)GET_CENTER_BLOCK.invoke(plot, new Object[0]);
            Object box = GET_BOUNDING_BOX.invoke(plot, new Object[0]);
            if (center == null || box == null) return null;
            int minX = center.getX() + (int)SubLevelFracture.number(box, "minX");
            int minY = center.getY() + (int)SubLevelFracture.number(box, "minY");
            int minZ = center.getZ() + (int)SubLevelFracture.number(box, "minZ");
            int maxX = center.getX() + (int)SubLevelFracture.number(box, "maxX");
            int maxY = center.getY() + (int)SubLevelFracture.number(box, "maxY");
            int maxZ = center.getZ() + (int)SubLevelFracture.number(box, "maxZ");
            return new SubLevelBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        return ((Number)target.getClass().getMethod(methodName, new Class[0]).invoke(target, new Object[0])).doubleValue();
    }

    private static Vector3d rotationPointVec(Object subLevel) {
        try {
            Object pose = LOGICAL_POSE.invoke(subLevel, new Object[0]);
            Object rp = ROTATION_POINT.invoke(pose, new Object[0]);
            double rx = ((Number)rp.getClass().getMethod("x", new Class[0]).invoke(rp, new Object[0])).doubleValue();
            double ry = ((Number)rp.getClass().getMethod("y", new Class[0]).invoke(rp, new Object[0])).doubleValue();
            double rz = ((Number)rp.getClass().getMethod("z", new Class[0]).invoke(rp, new Object[0])).doubleValue();
            return new Vector3d(rx, ry, rz);
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Converts a body-frame local position to the embedded-world position where the sublevel block
     * is actually stored: embeddedWorld = localPoint + plotCenter.
     * This is NOT the physics-world position — use tryFracturePhysicsWorldPos() when the caller
     * has a physics-world coordinate (e.g. from an explosion ray or bounding-box hit).
     */
    static Vector3d toWorldPoint(Object subLevel, Vector3d localPoint) {
        BlockPos center = SubLevelFracture.plotCenter(subLevel);
        if (center == null) return null;
        return new Vector3d(localPoint.x + center.getX(), localPoint.y + center.getY(), localPoint.z + center.getZ());
    }

    /**
     * Entry point for callers that have a physics-world coordinate (e.g. ExplosionImpactHandler).
     * Converts physicsWorldPoint → body-frame by subtracting rotationPoint, then calls tryFracture().
     */
    public static void tryFracturePhysicsWorldPos(Object subLevel, Vector3d physicsWorldPoint, Vector3d normal, double forceAmount) {
        Vector3d rp = SubLevelFracture.rotationPointVec(subLevel);
        if (rp == null) {
            // rotationPoint unavailable — skip rather than fracturing at wrong position
            return;
        }
        Vector3d bodyFramePoint = new Vector3d(physicsWorldPoint.x - rp.x, physicsWorldPoint.y - rp.y, physicsWorldPoint.z - rp.z);
        SubLevelFracture.tryFracture(subLevel, bodyFramePoint, normal, forceAmount);
    }

    private static final class FractureSnapshot
    implements StructuralStrengthAnalyzer.BlockLookup {
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
            HashMap<Long, BlockState> states = new HashMap<Long, BlockState>();
            HashMap<Long, BlockMaterial> materials = new HashMap<Long, BlockMaterial>();
            HashMap<Long, Double> damageRatios = new HashMap<Long, Double>();
            HashSet<Long> glueEntities = new HashSet<Long>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = -radius; x <= radius; ++x) {
                for (int y = -radius; y <= radius; ++y) {
                    for (int z = -radius; z <= radius; ++z) {
                        pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                        long key = pos.asLong();
                        BlockState state = level.getBlockState((BlockPos)pos);
                        states.put(key, state);
                        materials.put(key, new BlockMaterial(state.getDestroySpeed((BlockGetter)level, (BlockPos)pos), state.getBlock().getExplosionResistance()));
                        if (!state.isAir() && state.getDestroySpeed((BlockGetter)level, (BlockPos)pos) >= 0.0f) {
                            double baseThreshold = MaterialImpactProperties.baseStrength((BlockGetter)level, (BlockPos)pos, state);
                            double breakThreshold = Math.max(Math.max(MaterialImpactProperties.displayStrength(state, baseThreshold), MaterialImpactProperties.displayToughness(state, baseThreshold)), 1.0);
                            damageRatios.put(key, BlockDamageAccumulator.damageRatio(level, (BlockPos)pos, breakThreshold));
                        }
                        if (!StructuralStrengthAnalyzer.hasGlueEntity(level, (BlockPos)pos)) continue;
                        glueEntities.add(key);
                    }
                }
            }
            return new FractureSnapshot(states, materials, damageRatios, glueEntities);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
        }

        private double destroySpeed(BlockPos pos) {
            BlockMaterial material = this.materials.get(pos.asLong());
            return material == null ? 0.0 : (double)material.destroySpeed();
        }

        private double blastResistance(BlockPos pos) {
            BlockMaterial material = this.materials.get(pos.asLong());
            return material == null ? 0.0 : (double)material.blastResistance();
        }

        private double damageRatio(BlockPos pos) {
            return this.damageRatios.getOrDefault(pos.asLong(), 0.0);
        }

        @Override
        public boolean hasGlueEntity(BlockPos pos) {
            return this.glueEntities.contains(pos.asLong());
        }
    }

    private static final class TickBudget {
        private long tick;
        private int used;

        private TickBudget(long tick, int used) {
            this.tick = tick;
            this.used = used;
        }
    }

    private record FractureKey(String dimension, int subLevelId) {
    }

    private record PendingFracture(String dimension, Object heatMapManager, CandidateScan scan, long startedAt) {
    }

    private record SubLevelBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= this.minX && pos.getX() <= this.maxX && pos.getY() >= this.minY && pos.getY() <= this.maxY && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
        }
    }

    private record CandidateScan(List<Candidate> candidates, int checkedBlocks) {
    }

    private record Candidate(BlockPos pos, double score, double fatigueDamage, double breakThreshold) {
    }

    private record Offset(int x, int y, int z, double distanceSquared) {
    }

    private static final class FractureThreadFactory
    implements ThreadFactory {
        private FractureThreadFactory() {
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Sable True Impact Fracture Worker");
            thread.setDaemon(true);
            return thread;
        }
    }

    private record BlockMaterial(float destroySpeed, float blastResistance) {
    }
}
