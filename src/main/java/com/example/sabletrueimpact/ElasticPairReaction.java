/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper
 *  dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level$ExplosionInteraction
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.AABB
 *  net.minecraft.world.phys.Vec3
 *  org.apache.logging.log4j.LogManager
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.BlockDamageAccumulator;
import com.example.sabletrueimpact.CreateContraptionAnchorDamage;
import com.example.sabletrueimpact.ImpactDamageAllocator;
import com.example.sabletrueimpact.ImpactDamageContextCache;
import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPerformance;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ElasticPairReaction {
    private static final Field RUNTIME_ID_FIELD = ElasticPairReaction.findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "runtimeId");
    private static final Field LATEST_LINEAR_VELOCITY_FIELD = ElasticPairReaction.findFieldSafe("dev.ryanhcode.sable.sublevel.ServerSubLevel", "latestLinearVelocity");
    private static final Method GET_LEVEL_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel");
    private static final Method GET_MASS_TRACKER_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method GET_CENTER_OF_MASS_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
    private static final Method LOGICAL_POSE_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose");
    private static final Method ROTATION_POINT_METHOD = ElasticPairReaction.findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint");
    // Sublevel block storage: blocks are embedded in the world level at plotLocalPos + plotCenter.
    // rotationPoint tracks physics-world position and diverges from plotCenter when the structure moves.
    // Using plotCenter for block lookups gives the correct stored position regardless of motion.
    private static final Method GET_PLOT_METHOD = ElasticPairReaction.findMethodSafe("dev.ryanhcode.sable.sublevel.SubLevel", "getPlot");
    private static final Method GET_CENTER_BLOCK_METHOD = ElasticPairReaction.findMethodSafe("dev.ryanhcode.sable.sublevel.plot.LevelPlot", "getCenterBlock");

    // Bug 2 fix: queue impulses during post-step and flush them in pre-step to avoid Rapier island panic.
    private record PendingImpulse(int sceneId, int runtimeId, Object subLevel, Vector3d localPoint, Vector3d normal, double impulse) {}
    private static final java.util.List<PendingImpulse> PENDING_IMPULSES = new java.util.ArrayList<>();
    private static final int MAX_PENDING_IMPULSES = 512;

    private ElasticPairReaction() {
    }

    public static void apply(int sceneId, Int2ObjectMap<?> activeSubLevels, double[] collisions) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || collisions.length == 0) {
            return;
        }
        TrueImpactPerformance.recordCollisionBatch(collisions.length / 15);
        HashMap<Integer, CollisionCluster> clusters = new HashMap<Integer, CollisionCluster>();
        ArrayList<ExplosionCandidate> explosionCandidates = new ArrayList<ExplosionCandidate>();
        for (int i = 0; i < collisions.length / 15; ++i) {
            Integer runtimeId;
            Object mainSl;
            int start = i * 15;
            int idA = (int)collisions[start];
            int idB = (int)collisions[start + 1];
            Object slA = activeSubLevels.get(idA);
            Object slB = activeSubLevels.get(idB);
            Object object = mainSl = slA != null ? slA : slB;
            if (mainSl == null || (runtimeId = ElasticPairReaction.runtimeId(mainSl)) == null) continue;
            clusters.computeIfAbsent(runtimeId, k -> new CollisionCluster(sceneId, mainSl, slA == null || slB == null)).addPoint(collisions, start, slA, slB);
        }
        for (CollisionCluster cluster : clusters.values()) {
            cluster.process(explosionCandidates);
        }
        ElasticPairReaction.processExplosions(explosionCandidates);
    }

    public static void flushPendingImpulses(Int2ObjectMap<?> activeSubLevels) {
        java.util.List<PendingImpulse> batch;
        synchronized (PENDING_IMPULSES) {
            if (PENDING_IMPULSES.isEmpty()) {
                return;
            }
            batch = new java.util.ArrayList<>(PENDING_IMPULSES);
            PENDING_IMPULSES.clear();
        }
        for (PendingImpulse p : batch) {
            ElasticPairReaction.applyImpulse(activeSubLevels, p);
        }
    }

    private static void applyTerrainImpact(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount, List<ExplosionCandidate> explosions) {
        TerrainImpactSeed seed = ElasticPairReaction.buildTerrainImpactSeed(subLevel, localPoint, null, normal, forceAmount, explosions);
        if (seed != null) {
            ElasticPairReaction.damageTerrain(seed.level(), seed.pos(), seed.normal(), seed.energy());
        }
    }

    // worldPointOverride: when non-null, used directly as the terrain contact world position instead of
    // computing localPoint + rotationPoint. Pass the terrain-side body-frame point from Rapier —
    // for static bodies (terrain) the body frame equals the world frame, so this is already world space.
    private static TerrainImpactSeed buildTerrainImpactSeed(Object subLevel, Vector3d localPoint, Vector3d worldPointOverride, Vector3d normal, double forceAmount, List<ExplosionCandidate> explosions) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE.get()).booleanValue() || !((Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()).booleanValue() && ElasticPairReaction.isElasticSubLevel(subLevel)) {
            return null;
        }
        final Vector3d globalPoint;
        if (worldPointOverride != null) {
            globalPoint = worldPointOverride;
        } else {
            Vector3d rotationPoint = ElasticPairReaction.rotationPoint(subLevel);
            if (rotationPoint == null) { return null; }
            globalPoint = new Vector3d((Vector3dc)localPoint).add((Vector3dc)rotationPoint);
        }
        if (ElasticPairReaction.isForgivenStepContact(globalPoint, normal)) {
            return null;
        }
        ServerLevel level = ElasticPairReaction.level(subLevel);
        if (level == null) { return null; }
        BlockPos.MutableBlockPos selfPos = new BlockPos.MutableBlockPos();
        BlockState selfState = ElasticPairReaction.findNearestNonAirSubLevelState(subLevel, localPoint, null, selfPos);
        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        BlockState targetState = ElasticPairReaction.findSoftestNearbyTerrainState(level, globalPoint, selfState, targetPos);
        if (selfPos.equals(targetPos)) {
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                LogManager.getLogger().warn("[TrueImpact] Collision overlap: Self and Target position are identical at {}! Material matchup split may be incorrect.", selfPos);
            }
        }
        double selfFractureScale = ImpactDamageAllocator.damageScaleForSelf(level, (BlockPos)selfPos, selfState, (BlockPos)targetPos, targetState);
        double terrainDamageScale = ImpactDamageAllocator.damageScaleForTarget(level, (BlockPos)targetPos, targetState, (BlockPos)selfPos, selfState);
        ImpactDamageContextCache.put(level, (BlockPos)selfPos, selfFractureScale);
        ImpactDamageContextCache.putArea(level, BlockPos.containing((double)globalPoint.x, (double)globalPoint.y, (double)globalPoint.z), 2, selfFractureScale);
        ImpactDamageContextCache.put(level, (BlockPos)targetPos, terrainDamageScale);
        if (((Boolean)TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()).booleanValue()) {
            double rawMass = ElasticPairReaction.mass(subLevel);
            double mass = Math.min((Double)TrueImpactConfig.TERRAIN_IMPACT_MAX_EFFECTIVE_MASS.get(), Math.pow(Math.max(rawMass, 1.0), (Double)TrueImpactConfig.TERRAIN_IMPACT_MASS_EXPONENT.get()));
            double force = ElasticPairReaction.scaledForce(forceAmount, (Double)TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get(), (Double)TrueImpactConfig.TERRAIN_IMPACT_FORCE_EXPONENT.get());
            double energy = force * mass * (Double)TrueImpactConfig.TERRAIN_IMPACT_DAMAGE_SCALE.get() * (Double)TrueImpactConfig.DAMAGE_SCALE.get() * terrainDamageScale;
            CreateContraptionAnchorDamage.apply(level, globalPoint, energy);
            ElasticPairReaction.damageEntities(level, new Vec3(globalPoint.x, globalPoint.y, globalPoint.z), energy);
            ElasticPairReaction.collectExplosion(explosions, level, globalPoint, forceAmount, rawMass);
            return new TerrainImpactSeed(level, targetPos.immutable(), new Vector3d((Vector3dc)normal), energy);
        }
        return null;
    }

    private static BlockState findNearestNonAirTerrainState(ServerLevel level, Vector3d worldPoint, BlockPos.MutableBlockPos outPos) {
        BlockPos center = BlockPos.containing((double)worldPoint.x, (double)worldPoint.y, (double)worldPoint.z);
        BlockState direct = level.getBlockState(center);
        if (!direct.isAir()) {
            outPos.set((Vec3i)center);
            return direct;
        }
        BlockPos nearestPos = null;
        double minDistSq = Double.MAX_VALUE;
        BlockState nearestState = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    double d;
                    BlockPos p = center.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !((d = p.distToCenterSqr(worldPoint.x, worldPoint.y, worldPoint.z)) < minDistSq)) continue;
                    minDistSq = d;
                    nearestPos = p;
                    nearestState = s;
                }
            }
        }
        if (nearestPos != null) {
            outPos.set(nearestPos);
            return nearestState;
        }
        outPos.set((Vec3i)center);
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState findSoftestNearbyTerrainState(ServerLevel level, Vector3d worldPoint, BlockState selfState, BlockPos.MutableBlockPos outPos) {
        BlockPos center = BlockPos.containing((double)worldPoint.x, (double)worldPoint.y, (double)worldPoint.z);
        BlockState nearest = ElasticPairReaction.findNearestNonAirTerrainState(level, worldPoint, outPos);
        double bestResistance = selfState.isAir() ? Double.MAX_VALUE : ImpactDamageAllocator.impactResistance(level, (BlockPos)outPos, nearest);
        BlockPos bestPos = outPos.immutable();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // ±1 radius: keep target selection tight to the sampled contact point so the center of a
        // large flat contact area actually gets damaged, not a soft block far from impact.
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    double resistance;
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState candidate = level.getBlockState((BlockPos)pos);
                    if (candidate.isAir() || candidate.getBlock() == selfState.getBlock() || !((resistance = ImpactDamageAllocator.impactResistance(level, (BlockPos)pos, candidate)) < bestResistance)) continue;
                    bestResistance = resistance;
                    bestPos = pos.immutable();
                    nearest = candidate;
                }
            }
        }
        outPos.set((Vec3i)bestPos);
        return nearest;
    }

    private static BlockState findNearestNonAirSubLevelState(Object subLevel, Vector3d localPoint, BlockPos excludedWorldPos, BlockPos.MutableBlockPos outPos) {
        ServerLevel level = ElasticPairReaction.level(subLevel);
        // Sublevel blocks are stored in the world level at plotLocalPos + plotCenter.
        // rotationPoint diverges from plotCenter when the structure moves, so we must use plotCenter.
        BlockPos center = ElasticPairReaction.plotCenter(subLevel);
        if (level == null || center == null) {
            return Blocks.AIR.defaultBlockState();
        }
        Vector3d embeddedPoint = new Vector3d(localPoint.x + center.getX(), localPoint.y + center.getY(), localPoint.z + center.getZ());
        return ElasticPairReaction.findNearestNonAirState(level, embeddedPoint, excludedWorldPos, outPos);
    }

    private static BlockState findNearestNonAirState(ServerLevel level, Vector3d point, BlockPos excludedPos, BlockPos.MutableBlockPos outPos) {
        BlockPos center = BlockPos.containing((double)point.x, (double)point.y, (double)point.z);
        BlockState direct = level.getBlockState(center);
        if (!direct.isAir() && !center.equals(excludedPos)) {
            outPos.set((Vec3i)center);
            return direct;
        }
        BlockPos nearestPos = null;
        double minDistSq = Double.MAX_VALUE;
        BlockState nearestState = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= 1; ++x) {
            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    double d;
                    BlockState s;
                    BlockPos p = center.offset(x, y, z);
                    if (p.equals(excludedPos) || (s = level.getBlockState(p)).isAir() || !((d = p.distToCenterSqr(point.x, point.y, point.z)) < minDistSq)) continue;
                    minDistSq = d;
                    nearestPos = p;
                    nearestState = s;
                }
            }
        }
        if (nearestPos != null) {
            outPos.set(nearestPos);
            return nearestState;
        }
        outPos.set((Vec3i)center);
        return Blocks.AIR.defaultBlockState();
    }

    private static boolean isForgivenStepContact(Vector3d terrainPoint, Vector3d normal) {
        if (Math.abs(normal.y) > (Double)TrueImpactConfig.TERRAIN_STEP_SIDE_NORMAL_THRESHOLD.get()) {
            return false;
        }
        double yInBlock = terrainPoint.y - Math.floor(terrainPoint.y);
        return 1.0 - yInBlock <= (Double)TrueImpactConfig.TERRAIN_STEP_CONTACT_FORGIVENESS.get();
    }

    private static double scaledForce(double forceAmount, double threshold, double exponent) {
        if (exponent == 1.0 || forceAmount <= 0.0) {
            return forceAmount;
        }
        double reference = Math.max(threshold, 1.0);
        return forceAmount * Math.pow(Math.max(forceAmount / reference, 0.0), exponent - 1.0);
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    private static boolean isFinite(Vector3d value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static void damageEntities(ServerLevel level, Vec3 impactPoint, double energy) {
        if (!((Boolean)TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.get()).booleanValue() || energy <= 0.0) {
            return;
        }
        double radius = (Double)TrueImpactConfig.ENTITY_IMPACT_RADIUS.get();
        AABB bounds = AABB.ofSize((Vec3)impactPoint, (double)(radius * 2.0), (double)(radius * 2.0), (double)(radius * 2.0));
        double baseDamage = Math.sqrt(energy) * (Double)TrueImpactConfig.ENTITY_IMPACT_DAMAGE_SCALE.get();
        if (baseDamage < (Double)TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, e -> e.isAlive() && !e.isSpectator())) {
            double distance = entity.position().distanceTo(impactPoint);
            double falloff = Math.max(0.0, 1.0 - distance / Math.max(radius, 0.001));
            double d = (Double)TrueImpactConfig.ENTITY_IMPACT_MAX_DAMAGE.get() <= 0.0 ? 1000.0 : (Double)TrueImpactConfig.ENTITY_IMPACT_MAX_DAMAGE.get();
            float damage = (float)Math.min(d, baseDamage * falloff);
            if (!((double)damage >= (Double)TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get())) continue;
            entity.hurt(level.damageSources().cramming(), damage);
        }
    }

    private static void damageTerrain(ServerLevel level, BlockPos center, Vector3d normal, double energy) {
        ElasticPairReaction.damageTerrain(level, List.of(new TerrainImpactSeed(level, center.immutable(), new Vector3d((Vector3dc)normal), energy)));
    }

    private static void damageTerrain(ServerLevel level, List<TerrainImpactSeed> seeds) {
        ElasticPairReaction.damageTerrain(level, seeds, (Integer)TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.get());
    }

    private static void damageTerrain(ServerLevel level, List<TerrainImpactSeed> seeds, int maxBlocksOverride) {
        if (seeds.isEmpty()) {
            return;
        }
        level.getServer().execute(() -> {
            PriorityQueue<TerrainNode> queue = new PriorityQueue<TerrainNode>(Comparator.comparingDouble(TerrainNode::cost));
            HashSet<BlockPos> visited = new HashSet<BlockPos>();
            Vector3d direction = new Vector3d();
            LinkedHashMap<BlockPos, Double> merged = new LinkedHashMap<BlockPos, Double>();
            for (TerrainImpactSeed seed : seeds) {
                if (seed == null || seed.energy() <= 0.0 || seed.pos() == null || seed.normal() == null) continue;
                direction.add(seed.normal());
                merged.merge(seed.pos().immutable(), seed.energy(), Double::sum);
            }
            if (merged.isEmpty()) {
                return;
            }
            if (direction.lengthSquared() < 0.1) {
                direction.set(0.0, -1.0, 0.0);
            } else {
                direction.normalize();
            }
            for (java.util.Map.Entry<BlockPos, Double> entry : merged.entrySet()) {
                queue.add(new TerrainNode(entry.getKey(), entry.getValue(), 0.0));
                visited.add(entry.getKey());
            }
            int broken = 0;
            int maxBlocks = Math.max(0, maxBlocksOverride);
            while (!queue.isEmpty() && broken < maxBlocks) {
                TerrainNode node = queue.poll();
                BlockPos pos = node.pos();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed((BlockGetter)level, pos) < 0.0f || state.is(Blocks.BEDROCK)) continue;
                double strength = MaterialImpactProperties.baseStrength((BlockGetter)level, pos, state);
                double materialThreshold = Math.max(MaterialImpactProperties.breakThreshold(state, strength), 1.0);
                double yield = node.energy() / materialThreshold;
                if (yield >= (Double)TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get()) {
                    level.destroyBlock(pos, true);
                    ++broken;
                    double remaining = (node.energy() - materialThreshold) * 0.55;
                    if (!(remaining > materialThreshold * 0.2)) continue;
                    for (Direction dir : Direction.values()) {
                        BlockPos next = pos.relative(dir).immutable();
                        if (!visited.add(next)) continue;
                        double bias = 1.0 + Math.max(0.0, new Vector3d((double)dir.getStepX(), (double)dir.getStepY(), (double)dir.getStepZ()).dot((Vector3dc)direction)) * 0.8;
                        queue.add(new TerrainNode(next, remaining * bias, node.cost() + 1.0 / bias));
                    }
                    continue;
                }
                if (!((Boolean)TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue() || !(yield > (Double)TrueImpactConfig.CRACK_YIELD_THRESHOLD.get())) continue;
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, node.energy() - materialThreshold), materialThreshold * (Double)TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get(), pos.hashCode());
            }
        });
    }

    private static void collectExplosion(List<ExplosionCandidate> list, ServerLevel level, Vector3d point, double force, double mass) {
        if (!((Boolean)TrueImpactConfig.ENABLE_IMPACT_EXPLOSIONS.get()).booleanValue() || force < (Double)TrueImpactConfig.IMPACT_EXPLOSION_FORCE_THRESHOLD.get() || mass < (Double)TrueImpactConfig.IMPACT_EXPLOSION_MASS_THRESHOLD.get()) {
            return;
        }
        float radius = (float)Math.min((Double)TrueImpactConfig.IMPACT_EXPLOSION_MAX_RADIUS.get(), Math.sqrt(force * mass) * (Double)TrueImpactConfig.IMPACT_EXPLOSION_SCALE.get());
        if (radius >= 1.0f) {
            list.add(new ExplosionCandidate(level, point, radius, force));
        }
    }

    private static void processExplosions(List<ExplosionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort((a, b) -> Double.compare(b.force, a.force));
        ArrayList<ExplosionCandidate> toTrigger = new ArrayList<ExplosionCandidate>();
        double coalesceSq = Math.pow((Double)TrueImpactConfig.IMPACT_EXPLOSION_COALESCE_RADIUS.get(), 2.0);
        for (ExplosionCandidate cand : candidates) {
            if (toTrigger.size() >= (Integer)TrueImpactConfig.IMPACT_EXPLOSION_MAX_PER_BATCH.get()) break;
            boolean suppressed = false;
            for (ExplosionCandidate active : toTrigger) {
                if (!(active.point.distanceSquared((Vector3dc)cand.point) < coalesceSq)) continue;
                suppressed = true;
                break;
            }
            if (suppressed) continue;
            toTrigger.add(cand);
        }
        for (ExplosionCandidate e : toTrigger) {
            boolean fire = e.level.getRandom().nextDouble() < (Double)TrueImpactConfig.IMPACT_EXPLOSION_FIRE_CHANCE.get();
            e.level.getServer().execute(() -> e.level.explode(null, null, null, e.point.x, e.point.y, e.point.z, e.radius, fire, Level.ExplosionInteraction.BLOCK));
        }
    }

    private static boolean isElasticSubLevel(Object subLevel) {
        ServerLevel level = ElasticPairReaction.level(subLevel);
        BlockPos center = ElasticPairReaction.plotCenter(subLevel);
        if (level == null || center == null) {
            return false;
        }
        try {
            Method getPlot = subLevel.getClass().getMethod("getPlot", new Class[0]);
            Object plot = getPlot.invoke(subLevel, new Object[0]);
            Object box = plot.getClass().getMethod("getBoundingBox", new Class[0]).invoke(plot, new Object[0]);
            int minX = (int)ElasticPairReaction.number(box, "minX");
            int minY = (int)ElasticPairReaction.number(box, "minY");
            int minZ = (int)ElasticPairReaction.number(box, "minZ");
            int maxX = (int)ElasticPairReaction.number(box, "maxX");
            int maxY = (int)ElasticPairReaction.number(box, "maxY");
            int maxZ = (int)ElasticPairReaction.number(box, "maxZ");
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int limit = (Integer)TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.get();
            // Plot bounding box is in plot-local coordinates; add plotCenter to reach embedded world.
            for (int x = minX; x <= maxX && limit-- > 0; ++x) {
                for (int y = minY; y <= maxY && limit-- > 0; ++y) {
                    for (int z = minZ; z <= maxZ && limit-- > 0; ++z) {
                        if (!(PhysicsBlockPropertyHelper.getRestitution((BlockState)level.getBlockState((BlockPos)pos.set(x + center.getX(), y + center.getY(), z + center.getZ()))) >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get())) continue;
                        return true;
                    }
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return false;
    }

    private static void applyImpulse(Int2ObjectMap<?> activeSubLevels, PendingImpulse pending) {
        Object subLevel = pending.subLevel;
        Vector3d localPoint = pending.localPoint;
        Vector3d normal = pending.normal;
        double impulse = pending.impulse;
        if (!Double.isFinite(impulse) || impulse == 0.0 || !Double.isFinite(localPoint.x) || !Double.isFinite(localPoint.y) || !Double.isFinite(localPoint.z) || !Double.isFinite(normal.x) || !Double.isFinite(normal.y) || !Double.isFinite(normal.z)) {
            return;
        }
        if (subLevel == null || ElasticPairReaction.level(subLevel) == null || activeSubLevels == null || activeSubLevels.get(pending.runtimeId) != subLevel) {
            return;
        }
        Vector3d com = ElasticPairReaction.centerOfMass(subLevel);
        Integer rid = ElasticPairReaction.runtimeId(subLevel);
        if (normal.lengthSquared() < 1.0E-8 || com == null || rid == null || rid.intValue() != pending.runtimeId) {
            return;
        }
        if (!Double.isFinite(com.x) || !Double.isFinite(com.y) || !Double.isFinite(com.z)) {
            return;
        }
        Vector3d localNormal = new Vector3d((Vector3dc)normal).normalize().mul(impulse);
        if (!Double.isFinite(localNormal.x) || !Double.isFinite(localNormal.y) || !Double.isFinite(localNormal.z)) {
            return;
        }
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        try {
            handle.applyImpulseAtPoint(localPoint, localNormal);
        }
        catch (RuntimeException | LinkageError e) {
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                LogManager.getLogger().warn("[TrueImpact] Skipping delayed pair impulse after Rapier rejected it.", e);
            }
        }
    }

    private static double mass(Object subLevel) {
        try {
            return ((Number)GET_MASS_METHOD.invoke(GET_MASS_TRACKER_METHOD.invoke(subLevel, new Object[0]), new Object[0])).doubleValue();
        }
        catch (Exception e) {
            return 1.0;
        }
    }

    private static Vector3d centerOfMass(Object subLevel) {
        try {
            Object center = GET_CENTER_OF_MASS_METHOD.invoke(GET_MASS_TRACKER_METHOD.invoke(subLevel, new Object[0]), new Object[0]);
            return new Vector3d(((Double)center.getClass().getMethod("x", new Class[0]).invoke(center, new Object[0])).doubleValue(), ((Double)center.getClass().getMethod("y", new Class[0]).invoke(center, new Object[0])).doubleValue(), ((Double)center.getClass().getMethod("z", new Class[0]).invoke(center, new Object[0])).doubleValue());
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Vector3d rotationPoint(Object subLevel) {
        try {
            Object rp = ROTATION_POINT_METHOD.invoke(LOGICAL_POSE_METHOD.invoke(subLevel, new Object[0]), new Object[0]);
            return new Vector3d(((Double)rp.getClass().getMethod("x", new Class[0]).invoke(rp, new Object[0])).doubleValue(), ((Double)rp.getClass().getMethod("y", new Class[0]).invoke(rp, new Object[0])).doubleValue(), ((Double)rp.getClass().getMethod("z", new Class[0]).invoke(rp, new Object[0])).doubleValue());
        }
        catch (Exception e) {
            return null;
        }
    }

    private static ServerLevel level(Object subLevel) {
        try {
            return (ServerLevel)GET_LEVEL_METHOD.invoke(subLevel, new Object[0]);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Integer runtimeId(Object subLevel) {
        try {
            return ((Number)RUNTIME_ID_FIELD.get(subLevel)).intValue();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static double number(Object target, String methodName) throws Exception {
        return ((Number)target.getClass().getMethod(methodName, new Class[0]).invoke(target, new Object[0])).doubleValue();
    }

    private static Field findField(String cl, String f) {
        try {
            Field field = Class.forName(cl).getDeclaredField(f);
            field.setAccessible(true);
            return field;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Field findFieldSafe(String cl, String f) {
        try {
            Field field = Class.forName(cl).getDeclaredField(f);
            field.setAccessible(true);
            return field;
        }
        catch (Exception e) {
            LogManager.getLogger().warn("[TrueImpact] Could not cache {}.{}: {}", cl, f, e.getMessage());
            return null;
        }
    }

    private static Method findMethod(String cl, String m) {
        try {
            Method method = Class.forName(cl).getMethod(m, new Class[0]);
            method.setAccessible(true);
            return method;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method findMethodSafe(String cl, String m) {
        try {
            Method method = Class.forName(cl).getMethod(m, new Class[0]);
            method.setAccessible(true);
            return method;
        }
        catch (Exception e) {
            LogManager.getLogger().warn("[TrueImpact] Could not cache {}.{}: {}", cl, m, e.getMessage());
            return null;
        }
    }

    private static BlockPos plotCenter(Object subLevel) {
        if (GET_PLOT_METHOD == null || GET_CENTER_BLOCK_METHOD == null) return null;
        try {
            Object plot = GET_PLOT_METHOD.invoke(subLevel);
            if (plot == null) return null;
            return (BlockPos) GET_CENTER_BLOCK_METHOD.invoke(plot);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static BlockState localState(Object subLevel, Vector3d localPoint, BlockPos.MutableBlockPos blockPos) {
        ServerLevel level = ElasticPairReaction.level(subLevel);
        // Sublevel blocks are embedded in the world level at plotLocalPos + plotCenter (fixed offset).
        // rotationPoint tracks the physics-world position and diverges from plotCenter while moving,
        // so using rotationPoint here would query the wrong world position for any moving structure.
        BlockPos center = ElasticPairReaction.plotCenter(subLevel);
        if (level == null || center == null) {
            return Blocks.AIR.defaultBlockState();
        }
        blockPos.set(localPoint.x + center.getX(), localPoint.y + center.getY(), localPoint.z + center.getZ());
        return level.getBlockState((BlockPos)blockPos);
    }

    private static Vector3d latestLinearVelocity(Object subLevel) {
        if (LATEST_LINEAR_VELOCITY_FIELD == null || subLevel == null) {
            return new Vector3d();
        }
        try {
            Object velocity = LATEST_LINEAR_VELOCITY_FIELD.get(subLevel);
            if (velocity == null) {
                return new Vector3d();
            }
            Method x = velocity.getClass().getMethod("x", new Class[0]);
            Method y = velocity.getClass().getMethod("y", new Class[0]);
            Method z = velocity.getClass().getMethod("z", new Class[0]);
            return new Vector3d(((Number)x.invoke(velocity, new Object[0])).doubleValue(), ((Number)y.invoke(velocity, new Object[0])).doubleValue(), ((Number)z.invoke(velocity, new Object[0])).doubleValue());
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            return new Vector3d();
        }
    }

    private static ImpactKinematics impactKinematics(CollisionCluster.PointData point) {
        if (point == null || point.slA == null || point.slB == null) {
            return new ImpactKinematics(0.0, 0.0);
        }
        Vector3d relative = ElasticPairReaction.latestLinearVelocity(point.slA).sub((Vector3dc)ElasticPairReaction.latestLinearVelocity(point.slB));
        double relativeSpeed = relative.length();
        Vector3d normal = new Vector3d((Vector3dc)point.normalA);
        if (normal.lengthSquared() < 1.0E-8) {
            normal.set((Vector3dc)point.normalB);
        }
        double closingSpeed = 0.0;
        if (normal.lengthSquared() >= 1.0E-8) {
            normal.normalize();
            closingSpeed = Math.abs(relative.dot((Vector3dc)normal));
        }
        return new ImpactKinematics(Double.isFinite(relativeSpeed) ? relativeSpeed : 0.0, Double.isFinite(closingSpeed) ? closingSpeed : 0.0);
    }

    private static void queueImpulse(int sceneId, Object subLevel, Vector3d localPoint, Vector3d normal, double impulse) {
        if (!ElasticPairReaction.isFinite(localPoint) || !ElasticPairReaction.isFinite(normal) || !Double.isFinite(impulse) || impulse == 0.0) {
            return;
        }
        Integer runtimeId = ElasticPairReaction.runtimeId(subLevel);
        if (runtimeId == null) {
            return;
        }
        synchronized (PENDING_IMPULSES) {
            if (PENDING_IMPULSES.size() >= MAX_PENDING_IMPULSES) {
                return;
            }
            PENDING_IMPULSES.add(new PendingImpulse(sceneId, runtimeId, subLevel, new Vector3d((Vector3dc)localPoint), new Vector3d((Vector3dc)normal), impulse));
        }
    }

    private static class CollisionCluster {
        private final int sceneId;
        private final Object subLevel;
        private final boolean isTerrain;
        private double totalForce = 0.0;
        private final List<PointData> points = new ArrayList<PointData>();
        private final Vector3d min = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        private final Vector3d max = new Vector3d(-1.7976931348623157E308, -1.7976931348623157E308, -1.7976931348623157E308);

        public CollisionCluster(int sceneId, Object subLevel, boolean isTerrain) {
            this.sceneId = sceneId;
            this.subLevel = subLevel;
            this.isTerrain = isTerrain;
        }

        public void addPoint(double[] collisions, int start, Object slA, Object slB) {
            double f = collisions[start + 2];
            this.totalForce += f;
            Vector3d localA = new Vector3d(collisions[start + 9], collisions[start + 10], collisions[start + 11]);
            Vector3d localB = new Vector3d(collisions[start + 12], collisions[start + 13], collisions[start + 14]);
            Vector3d normalA = new Vector3d(collisions[start + 3], collisions[start + 4], collisions[start + 5]);
            Vector3d normalB = new Vector3d(collisions[start + 6], collisions[start + 7], collisions[start + 8]);
            Vector3d local = slA != null ? localA : localB;
            Vector3d normal = slA != null ? normalA : normalB;
            this.points.add(new PointData(local, normal, f, slA, slB, localA, normalA, localB, normalB));
            this.min.x = Math.min(this.min.x, local.x);
            this.min.y = Math.min(this.min.y, local.y);
            this.min.z = Math.min(this.min.z, local.z);
            this.max.x = Math.max(this.max.x, local.x);
            this.max.y = Math.max(this.max.y, local.y);
            this.max.z = Math.max(this.max.z, local.z);
        }

        public void process(List<ExplosionCandidate> explosions) {
            boolean isFlat;
            if (this.points.isEmpty()) {
                return;
            }
            double dx = Math.max(0.1, this.max.x - this.min.x);
            double dy = Math.max(0.1, this.max.y - this.min.y);
            double dz = Math.max(0.1, this.max.z - this.min.z);
            double area = Math.max(1.0, dx * dz + dx * dy + dy * dz);
            boolean bl = isFlat = this.points.size() > 3 && area > 1.5;
            if (this.isTerrain) {
                this.processTerrainImpact(isFlat, explosions);
            } else {
                this.processPairDamage(explosions);
                if (((Boolean)TrueImpactConfig.ENABLE_PAIR_REACTION.get()).booleanValue()) {
                    this.processPairReaction(explosions);
                }
            }
        }

        private void processTerrainImpact(boolean isFlat, List<ExplosionCandidate> explosions) {
            ServerLevel level = ElasticPairReaction.level(this.subLevel);
            if (level == null) {
                return;
            }
            double subLevelSpeed = ElasticPairReaction.latestLinearVelocity(this.subLevel).length();
            if (!Double.isFinite(subLevelSpeed) || subLevelSpeed < (Double)TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
                return;
            }
            double threshold = (Double)TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get();
            if (!isFlat && this.totalForce < threshold) {
                return;
            }
            if (isFlat && this.totalForce < threshold * 0.5) {
                return;
            }
            this.points.sort((a, b) -> Double.compare(b.force, a.force));
            // Per-contact loop: each Rapier contact point independently targets the terrain-side world coord.
            // Static bodies (terrain) have body frame = world frame, so localB (slA = structure) is world space.
            double representativeEnergy = -1;
            Vector3d representativeNormal = this.points.get(0).normal;
            for (PointData p : this.points) {
                if (p.force <= 0.0 || !Double.isFinite(p.force)) continue;
                Vector3d terrainWorldPoint = (p.slA != null) ? p.localB : p.localA;
                TerrainImpactSeed seed = ElasticPairReaction.buildTerrainImpactSeed(
                    this.subLevel, p.local, terrainWorldPoint, p.normal, p.force, explosions);
                if (seed != null) {
                    if (representativeEnergy < 0) representativeEnergy = seed.energy();
                    ElasticPairReaction.damageTerrain(seed.level(), seed.pos(), seed.normal(), seed.energy());
                }
            }
            // Supplemental grid scan: fills the gaps between sparse Rapier corner contacts.
            // Terrain contact points (localB for static terrain = world frame) define the footprint.
            if (representativeEnergy > 0 && isFlat) {
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                double contactY = 0.0; int contactCount = 0;
                for (PointData p : this.points) {
                    if (p.force <= 0.0 || !Double.isFinite(p.force)) continue;
                    Vector3d terrainPt = (p.slA != null) ? p.localB : p.localA;
                    if (!Double.isFinite(terrainPt.x) || !Double.isFinite(terrainPt.y) || !Double.isFinite(terrainPt.z)) continue;
                    minX = Math.min(minX, terrainPt.x); maxX = Math.max(maxX, terrainPt.x);
                    minZ = Math.min(minZ, terrainPt.z); maxZ = Math.max(maxZ, terrainPt.z);
                    contactY += terrainPt.y; contactCount++;
                }
                if (contactCount > 0 && minX <= maxX && minZ <= maxZ) {
                    contactY /= contactCount;
                    // Rapier reports horizontal normals (normalY≈0) for flat box-on-terrain contacts.
                    // signum(0) = 0 would give floor(contactY) = AIR block above the surface.
                    // Always subtract 0.5 so floor lands inside the surface block.
                    int iy = (int)Math.floor(contactY - 0.5);
                    int bxMin = (int)Math.floor(minX);
                    int bxMax = (int)Math.floor(maxX);
                    int bzMin = (int)Math.floor(minZ);
                    int bzMax = (int)Math.floor(maxZ);
                    int area = (bxMax - bxMin + 1) * (bzMax - bzMin + 1);
                    // Cap at 4096 (≈64×64) so large ships don't freeze the server on one tick.
                    // Per-contact BFS already covered Rapier-sampled positions; grid scan fills gaps.
                    if (area > 0 && area <= 4096) {
                        ArrayList<TerrainImpactSeed> gridSeeds = new ArrayList<TerrainImpactSeed>(Math.min(area, 4096));
                        for (int bx = bxMin; bx <= bxMax; ++bx) {
                            for (int bz = bzMin; bz <= bzMax; ++bz) {
                                BlockPos gp = new BlockPos(bx, iy, bz);
                                BlockState gs = level.getBlockState(gp);
                                if (gs.isAir()) {
                                    // Average contact Y can be biased high by taller terrain elsewhere;
                                    // check one block below first so those shorter-surface positions are hit.
                                    gp = new BlockPos(bx, iy - 1, bz);
                                    gs = level.getBlockState(gp);
                                }
                                if (gs.isAir()) {
                                    // Bias may also run the other way; try one block above as last resort.
                                    gp = new BlockPos(bx, iy + 1, bz);
                                    gs = level.getBlockState(gp);
                                }
                                if (!gs.isAir() && !gs.is(Blocks.BEDROCK)
                                        && gs.getDestroySpeed((BlockGetter)level, gp) >= 0f) {
                                    gridSeeds.add(new TerrainImpactSeed(level, gp, representativeNormal, representativeEnergy));
                                }
                            }
                        }
                        if (!gridSeeds.isEmpty()) {
                            ElasticPairReaction.damageTerrain(level, gridSeeds,
                                Math.max((Integer)TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.get(), gridSeeds.size()));
                        }
                    }
                }
            }
        }

        private void processPairReaction(List<ExplosionCandidate> explosions) {
            if (!ElasticPairReaction.isElasticSubLevel(this.subLevel)) {
                return;
            }
            BlockPos.MutableBlockPos tmpPos = new BlockPos.MutableBlockPos();
            for (PointData p : this.points) {
                if (p.slA == null || p.slB == null) continue;
                BlockState stateA = ElasticPairReaction.localState(p.slA, p.localA, tmpPos);
                BlockState stateB = ElasticPairReaction.localState(p.slB, p.localB, tmpPos);
                double res = Math.max(PhysicsBlockPropertyHelper.getRestitution((BlockState)stateA), PhysicsBlockPropertyHelper.getRestitution((BlockState)stateB));
                double threshold = (Double)TrueImpactConfig.PAIR_REACTION_FORCE_THRESHOLD.get();
                if (p.force < threshold) continue;
                double massA = Math.max(ElasticPairReaction.mass(p.slA), 1.0);
                double massB = Math.max(ElasticPairReaction.mass(p.slB), 1.0);
                double reducedMass = massA * massB / (massA + massB);
                double softening = Math.min(1.0, (p.force - threshold) / Math.max(threshold, 100.0));
                double cappedForce = Math.min(p.force, (Double)TrueImpactConfig.PAIR_REACTION_MAX_IMPULSE.get());
                double impulse = cappedForce * res * (Double)TrueImpactConfig.PAIR_REACTION_SCALE.get() * softening;
                if (!((impulse = Math.min(impulse, reducedMass * (Double)TrueImpactConfig.PAIR_REACTION_MAX_VELOCITY_CHANGE.get())) > 1.0E-6)) continue;
                // Bug 2 fix: queue impulses for pre-step application to avoid Rapier island panic.
                ElasticPairReaction.queueImpulse(this.sceneId, p.slA, p.localA, p.normalA, impulse / massA);
                ElasticPairReaction.queueImpulse(this.sceneId, p.slB, p.localB, p.normalB, -impulse / massB);
                Vector3d rot = ElasticPairReaction.rotationPoint(p.slA);
                if (rot == null) continue;
                ElasticPairReaction.collectExplosion(explosions, ElasticPairReaction.level(p.slA), new Vector3d((Vector3dc)p.localA).add((Vector3dc)rot), p.force, Math.max(massA, massB));
            }
        }

        private void processPairDamage(List<ExplosionCandidate> explosions) {
            if (!((Boolean)TrueImpactConfig.ENABLE_SUBLEVEL_FRACTURE.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.get()).booleanValue()) {
                return;
            }
            this.points.sort((a, b) -> Double.compare(b.force, a.force));
            int limit = Math.min(this.points.size(), Math.max(1, (Integer)TrueImpactConfig.SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK.get()));
            BlockPos.MutableBlockPos posA = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos posB = new BlockPos.MutableBlockPos();
            for (int i = 0; i < limit; ++i) {
                PointData p = this.points.get(i);
                if (p.slA == null || p.slB == null || !ElasticPairReaction.isFinite(p.force) || p.force < (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_FORCE_THRESHOLD.get()) {
                    continue;
                }
                if (!ElasticPairReaction.isFinite(p.localA) || !ElasticPairReaction.isFinite(p.localB) || !ElasticPairReaction.isFinite(p.normalA) || !ElasticPairReaction.isFinite(p.normalB)) {
                    continue;
                }
                ImpactKinematics kinematics = ElasticPairReaction.impactKinematics(p);
                boolean dynamicImpact = kinematics.relativeSpeed() >= (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_MIN_RELATIVE_SPEED.get() && kinematics.closingSpeed() >= (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_MIN_CLOSING_SPEED.get();
                double staticScale = (Double)TrueImpactConfig.SUBLEVEL_FRACTURE_STATIC_FORCE_DAMAGE_SCALE.get();
                if (!dynamicImpact && staticScale <= 0.0) {
                    continue;
                }
                BlockState stateA = ElasticPairReaction.localState(p.slA, p.localA, posA);
                BlockState stateB = ElasticPairReaction.localState(p.slB, p.localB, posB);
                if (stateA.isAir() && stateB.isAir()) {
                    continue;
                }
                double scaleA = ImpactDamageAllocator.damageScaleForSelf(ElasticPairReaction.level(p.slA), posA.immutable(), stateA, posB.immutable(), stateB);
                double scaleB = ImpactDamageAllocator.damageScaleForSelf(ElasticPairReaction.level(p.slB), posB.immutable(), stateB, posA.immutable(), stateA);
                if (!dynamicImpact) {
                    scaleA *= staticScale;
                    scaleB *= staticScale;
                }
                SubLevelFracture.tryFracture(p.slA, p.localA, p.normalA, p.force, scaleA);
                SubLevelFracture.tryFracture(p.slB, p.localB, p.normalB, p.force, scaleB);
                Vector3d rot = ElasticPairReaction.rotationPoint(p.slA);
                if (rot == null) continue;
                ElasticPairReaction.collectExplosion(explosions, ElasticPairReaction.level(p.slA), new Vector3d((Vector3dc)p.localA).add((Vector3dc)rot), p.force, Math.max(ElasticPairReaction.mass(p.slA), ElasticPairReaction.mass(p.slB)));
            }
        }

        private record PointData(Vector3d local, Vector3d normal, double force, Object slA, Object slB, Vector3d localA, Vector3d normalA, Vector3d localB, Vector3d normalB) {
        }
    }

    private record ExplosionCandidate(ServerLevel level, Vector3d point, float radius, double force) {
    }

    private record ImpactKinematics(double relativeSpeed, double closingSpeed) {
    }

    private record TerrainImpactSeed(ServerLevel level, BlockPos pos, Vector3d normal, double energy) {
    }

    private record TerrainNode(BlockPos pos, double energy, double cost) {
    }

    private record PointData(Vector3d local, Vector3d normal, double force, Object slA, Object slB) {
    }
}
