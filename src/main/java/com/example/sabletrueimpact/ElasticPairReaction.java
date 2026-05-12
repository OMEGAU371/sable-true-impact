package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public final class ElasticPairReaction {
    private static final Field RUNTIME_ID_FIELD = findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "runtimeId");
    private static final Method GET_LEVEL_METHOD = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel");
    private static final Method GET_MASS_TRACKER_METHOD = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS_METHOD = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method GET_CENTER_OF_MASS_METHOD = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
    private static final Method LOGICAL_POSE_METHOD = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose");
    private static final Method ROTATION_POINT_METHOD = findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint");

    private ElasticPairReaction() {}

    public static void apply(int sceneId, Int2ObjectMap<?> activeSubLevels, double[] collisions) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get() || collisions.length == 0) {
            return;
        }
        TrueImpactPerformance.recordCollisionBatch(collisions.length / 15);

        java.util.Map<Integer, CollisionCluster> clusters = new java.util.HashMap<>();
        List<ExplosionCandidate> explosionCandidates = new ArrayList<>();

        for (int i = 0; i < collisions.length / 15; i++) {
            int start = i * 15;
            int idA = (int) collisions[start];
            int idB = (int) collisions[start + 1];
            
            Object slA = activeSubLevels.get(idA);
            Object slB = activeSubLevels.get(idB);
            
            Object mainSl = slA != null ? slA : slB;
            if (mainSl == null) continue;
            
            Integer runtimeId = runtimeId(mainSl);
            if (runtimeId == null) continue;

            clusters.computeIfAbsent(runtimeId, k -> new CollisionCluster(sceneId, mainSl, slA == null || slB == null))
                    .addPoint(collisions, start, slA, slB);
        }

        for (CollisionCluster cluster : clusters.values()) {
            cluster.process(explosionCandidates);
        }

        processExplosions(explosionCandidates);
    }

    private static class CollisionCluster {
        private final int sceneId;
        private final Object subLevel;
        private final boolean isTerrain;
        private double totalForce = 0;
        private final List<PointData> points = new ArrayList<>();
        private final Vector3d min = new Vector3d(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
        private final Vector3d max = new Vector3d(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);

        public CollisionCluster(int sceneId, Object subLevel, boolean isTerrain) {
            this.sceneId = sceneId;
            this.subLevel = subLevel;
            this.isTerrain = isTerrain;
        }

        public void addPoint(double[] collisions, int start, Object slA, Object slB) {
            double f = collisions[start + 2];
            totalForce += f;
            
            Vector3d local = slA != null ? 
                new Vector3d(collisions[start + 9], collisions[start + 10], collisions[start + 11]) :
                new Vector3d(collisions[start + 12], collisions[start + 13], collisions[start + 14]);
            
            Vector3d normal = slA != null ?
                new Vector3d(collisions[start + 3], collisions[start + 4], collisions[start + 5]) :
                new Vector3d(collisions[start + 6], collisions[start + 7], collisions[start + 8]);

            points.add(new PointData(local, normal, f, slA, slB));
            
            min.x = Math.min(min.x, local.x); min.y = Math.min(min.y, local.y); min.z = Math.min(min.z, local.z);
            max.x = Math.max(max.x, local.x); max.y = Math.max(max.y, local.y); max.z = Math.max(max.z, local.z);
        }

        public void process(List<ExplosionCandidate> explosions) {
            if (points.isEmpty()) return;
            
            double dx = Math.max(0.1, max.x - min.x);
            double dy = Math.max(0.1, max.y - min.y);
            double dz = Math.max(0.1, max.z - min.z);
            double area = Math.max(1.0, dx * dz + dx * dy + dy * dz);
            boolean isFlat = points.size() > 3 && area > 1.5;

            if (isTerrain) {
                processTerrainImpact(isFlat, explosions);
            } else if (TrueImpactConfig.ENABLE_PAIR_REACTION.get()) {
                processPairReaction(explosions);
            }
        }

        private void processTerrainImpact(boolean isFlat, List<ExplosionCandidate> explosions) {
            ServerLevel level = level(subLevel);
            Vector3d rotation = rotationPoint(subLevel);
            if (level == null || rotation == null) return;

            double threshold = TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get();
            if (!isFlat && totalForce < threshold) return;
            if (isFlat && totalForce < threshold * 0.5) return;

            points.sort((a, b) -> Double.compare(b.force, a.force));
            
            if (isFlat) {
                int limit = Math.min(points.size(), 8);
                double distributedForce = totalForce / limit;
                for (int i = 0; i < limit; i++) {
                    PointData p = points.get(i);
                    applyTerrainImpact(subLevel, p.local, p.normal, distributedForce * 1.5, explosions);
                }
            } else {
                PointData lead = points.get(0);
                applyTerrainImpact(subLevel, lead.local, lead.normal, totalForce, explosions);
            }
        }

        private void processPairReaction(List<ExplosionCandidate> explosions) {
            BlockPos.MutableBlockPos tmpPos = new BlockPos.MutableBlockPos();
            for (PointData p : points) {
                if (p.slA == null || p.slB == null) continue;
                
                BlockState stateA = localState(p.slA, p.local, tmpPos);
                BlockState stateB = localState(p.slB, p.local, tmpPos);
                double res = Math.max(PhysicsBlockPropertyHelper.getRestitution(stateA), PhysicsBlockPropertyHelper.getRestitution(stateB));
                
                double threshold = TrueImpactConfig.PAIR_REACTION_FORCE_THRESHOLD.get();
                if (p.force < threshold) continue;

                double massA = Math.max(mass(p.slA), 1.0);
                double massB = Math.max(mass(p.slB), 1.0);
                double reducedMass = (massA * massB) / (massA + massB);
                double softening = Math.min(1.0, (p.force - threshold) / Math.max(threshold, 100.0));
                double cappedForce = Math.min(p.force, TrueImpactConfig.PAIR_REACTION_MAX_IMPULSE.get());
                double impulse = cappedForce * res * TrueImpactConfig.PAIR_REACTION_SCALE.get() * softening;
                impulse = Math.min(impulse, reducedMass * TrueImpactConfig.PAIR_REACTION_MAX_VELOCITY_CHANGE.get());

                if (impulse > 1.0E-6) {
                    applyImpulse(sceneId, p.slA, p.local, p.normal, impulse / massA);
                    applyImpulse(sceneId, p.slB, p.local, p.normal, -impulse / massB);
                    
                    Vector3d rot = rotationPoint(p.slA);
                    if (rot != null) {
                        collectExplosion(explosions, level(p.slA), new Vector3d(p.local).add(rot), p.force, Math.max(massA, massB));
                    }
                }
            }
        }

        private record PointData(Vector3d local, Vector3d normal, double force, Object slA, Object slB) {}
    }

    private static void applyTerrainImpact(Object subLevel, Vector3d localPoint, Vector3d normal, double forceAmount, List<ExplosionCandidate> explosions) {
        if (!TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE.get() || (TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && isElasticSubLevel(subLevel))) {
            return;
        }
        
        Vector3d rotationPoint = rotationPoint(subLevel);
        if (rotationPoint == null) return;
        Vector3d globalPoint = new Vector3d(localPoint).add(rotationPoint);

        if (isForgivenStepContact(globalPoint, normal)) return;

        ServerLevel level = level(subLevel);
        if (level == null) return;

        // Material-aware scaling: determine damage split between structure and terrain
        BlockPos.MutableBlockPos selfPos = new BlockPos.MutableBlockPos();
        BlockState selfState = findNearestNonAirSubLevelState(subLevel, localPoint, selfPos);
        
        BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
        BlockState targetState = findNearestNonAirTerrainState(level, globalPoint, targetPos);

        // Warning: if detection systems overlap, the split logic will use the same block for both sides
        if (selfPos.equals(targetPos)) {
            org.apache.logging.log4j.LogManager.getLogger().warn("[TrueImpact] Collision overlap: Self and Target position are identical at {}! Material matchup split may be incorrect.", selfPos);
        }

        double selfFractureScale = ImpactDamageAllocator.damageScaleForSelf(level, selfPos, selfState, targetPos, targetState);
        double terrainDamageScale = ImpactDamageAllocator.damageScaleForTarget(level, targetPos, targetState, selfPos, selfState);

        ImpactDamageContextCache.put(level, selfPos, selfFractureScale);
        ImpactDamageContextCache.put(level, targetPos, terrainDamageScale);

        SubLevelFracture.tryFracture(subLevel, localPoint, normal, forceAmount, selfFractureScale);
        
        if (TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()) {
            double mass = Math.min(TrueImpactConfig.TERRAIN_IMPACT_MAX_EFFECTIVE_MASS.get(), Math.pow(Math.max(mass(subLevel), 1.0), TrueImpactConfig.TERRAIN_IMPACT_MASS_EXPONENT.get()));
            double force = scaledForce(forceAmount, TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get(), TrueImpactConfig.TERRAIN_IMPACT_FORCE_EXPONENT.get());
            
            // Scaled energy for terrain damage
            double energy = force * mass * TrueImpactConfig.TERRAIN_IMPACT_DAMAGE_SCALE.get() * TrueImpactConfig.DAMAGE_SCALE.get() * terrainDamageScale;
            
            damageTerrain(level, targetPos, normal, energy);
            damageEntities(level, new Vec3(globalPoint.x, globalPoint.y, globalPoint.z), energy);
            collectExplosion(explosions, level, globalPoint, forceAmount, mass(subLevel));
        }
    }

    private static BlockState findNearestNonAirTerrainState(ServerLevel level, Vector3d worldPoint, BlockPos.MutableBlockPos outPos) {
        BlockPos center = BlockPos.containing(worldPoint.x, worldPoint.y, worldPoint.z);
        BlockState direct = level.getBlockState(center);
        if (!direct.isAir()) {
            outPos.set(center);
            return direct;
        }

        BlockPos nearestPos = null;
        double minDistSq = Double.MAX_VALUE;
        BlockState nearestState = Blocks.AIR.defaultBlockState();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = center.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir()) {
                        double d = p.distToCenterSqr(worldPoint.x, worldPoint.y, worldPoint.z);
                        if (d < minDistSq) {
                            minDistSq = d;
                            nearestPos = p;
                            nearestState = s;
                        }
                    }
                }
            }
        }

        if (nearestPos != null) {
            outPos.set(nearestPos);
            return nearestState;
        }

        outPos.set(center);
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState findNearestNonAirSubLevelState(Object subLevel, Vector3d localPoint, BlockPos.MutableBlockPos outPos) {
        ServerLevel level = level(subLevel);
        Vector3d rotationPoint = rotationPoint(subLevel);
        if (level == null || rotationPoint == null) return Blocks.AIR.defaultBlockState();
        
        Vector3d worldPoint = new Vector3d(localPoint).add(rotationPoint);
        BlockPos center = BlockPos.containing(worldPoint.x, worldPoint.y, worldPoint.z);
        
        BlockState direct = level.getBlockState(center);
        if (!direct.isAir()) {
            outPos.set(center);
            return direct;
        }
        
        BlockPos nearestPos = null;
        double minDistSq = Double.MAX_VALUE;
        BlockState nearestState = Blocks.AIR.defaultBlockState();
        
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = center.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (!s.isAir()) {
                        double d = p.distToCenterSqr(worldPoint.x, worldPoint.y, worldPoint.z);
                        if (d < minDistSq) {
                            minDistSq = d;
                            nearestPos = p;
                            nearestState = s;
                        }
                    }
                }
            }
        }
        
        if (nearestPos != null) {
            outPos.set(nearestPos);
            return nearestState;
        }
        
        return Blocks.AIR.defaultBlockState();
    }

    private static boolean isForgivenStepContact(Vector3d terrainPoint, Vector3d normal) {
        if (Math.abs(normal.y) > TrueImpactConfig.TERRAIN_STEP_SIDE_NORMAL_THRESHOLD.get()) return false;
        double yInBlock = terrainPoint.y - Math.floor(terrainPoint.y);
        return 1.0 - yInBlock <= TrueImpactConfig.TERRAIN_STEP_CONTACT_FORGIVENESS.get();
    }

    private static double scaledForce(double forceAmount, double threshold, double exponent) {
        if (exponent == 1.0 || forceAmount <= 0.0) return forceAmount;
        double reference = Math.max(threshold, 1.0);
        return forceAmount * Math.pow(Math.max(forceAmount / reference, 0.0), exponent - 1.0);
    }

    private static void damageEntities(ServerLevel level, Vec3 impactPoint, double energy) {
        if (!TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.get() || energy <= 0.0) return;
        double radius = TrueImpactConfig.ENTITY_IMPACT_RADIUS.get();
        AABB bounds = AABB.ofSize(impactPoint, radius * 2.0, radius * 2.0, radius * 2.0);
        double baseDamage = Math.sqrt(energy) * TrueImpactConfig.ENTITY_IMPACT_DAMAGE_SCALE.get();
        if (baseDamage < TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) return;
        
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, e -> e.isAlive() && !e.isSpectator())) {
            double distance = entity.position().distanceTo(impactPoint);
            double falloff = Math.max(0.0, 1.0 - distance / Math.max(radius, 0.001));
            float damage = (float) Math.min(TrueImpactConfig.ENTITY_IMPACT_MAX_DAMAGE.get() <= 0 ? 1000 : TrueImpactConfig.ENTITY_IMPACT_MAX_DAMAGE.get(), baseDamage * falloff);
            if (damage >= TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) {
                entity.hurt(level.damageSources().cramming(), damage);
            }
        }
    }

    private static void damageTerrain(ServerLevel level, BlockPos center, Vector3d normal, double energy) {
        level.getServer().execute(() -> {
            PriorityQueue<TerrainNode> queue = new PriorityQueue<>(Comparator.comparingDouble(TerrainNode::cost));
            Set<BlockPos> visited = new HashSet<>();
            Vector3d direction = new Vector3d(normal).normalize();
            if (direction.lengthSquared() < 0.1) direction.set(0, -1, 0);

            queue.add(new TerrainNode(center.immutable(), energy, 0.0));
            visited.add(center.immutable());
            int broken = 0;

            while (!queue.isEmpty() && broken < TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.get()) {
                TerrainNode node = queue.poll();
                BlockPos pos = node.pos();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || state.is(Blocks.BEDROCK)) continue;

                double strength = (state.getDestroySpeed(level, pos) * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get())
                        + TrueImpactConfig.BASE_STRENGTH.get();
                if (state.getDestroySpeed(level, pos) < 1.0f) strength *= TrueImpactConfig.SOFT_BLOCK_STRENGTH_MULTIPLIER.get();
                
                double materialThreshold = Math.max(MaterialImpactProperties.breakThreshold(state, strength), 1.0);
                double yield = node.energy() / materialThreshold;

                if (yield >= TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get()) {
                    level.destroyBlock(pos, true);
                    broken++;
                    double remaining = (node.energy() - materialThreshold) * 0.55;
                    if (remaining > materialThreshold * 0.2) {
                        for (Direction dir : Direction.values()) {
                            BlockPos next = pos.relative(dir).immutable();
                            if (visited.add(next)) {
                                double bias = 1.0 + Math.max(0.0, new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ()).dot(direction)) * 0.8;
                                queue.add(new TerrainNode(next, remaining * bias, node.cost() + 1.0 / bias));
                            }
                        }
                    }
                } else if (TrueImpactConfig.ENABLE_CRACKS.get() && yield > TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
                    BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, node.energy() - materialThreshold), 
                        materialThreshold * TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get(), pos.hashCode());
                }
            }
        });
    }

    private static void collectExplosion(List<ExplosionCandidate> list, ServerLevel level, Vector3d point, double force, double mass) {
        if (!TrueImpactConfig.ENABLE_IMPACT_EXPLOSIONS.get() || force < TrueImpactConfig.IMPACT_EXPLOSION_FORCE_THRESHOLD.get() || mass < TrueImpactConfig.IMPACT_EXPLOSION_MASS_THRESHOLD.get()) return;
        float radius = (float) Math.min(TrueImpactConfig.IMPACT_EXPLOSION_MAX_RADIUS.get(), Math.sqrt(force * mass) * TrueImpactConfig.IMPACT_EXPLOSION_SCALE.get());
        if (radius >= 1.0f) list.add(new ExplosionCandidate(level, point, radius, force));
    }

    private static void processExplosions(List<ExplosionCandidate> candidates) {
        if (candidates.isEmpty()) return;
        candidates.sort((a, b) -> Double.compare(b.force, a.force));
        List<ExplosionCandidate> toTrigger = new ArrayList<>();
        double coalesceSq = Math.pow(TrueImpactConfig.IMPACT_EXPLOSION_COALESCE_RADIUS.get(), 2);
        for (ExplosionCandidate cand : candidates) {
            if (toTrigger.size() >= TrueImpactConfig.IMPACT_EXPLOSION_MAX_PER_BATCH.get()) break;
            boolean suppressed = false;
            for (ExplosionCandidate active : toTrigger) {
                if (active.point.distanceSquared(cand.point) < coalesceSq) { suppressed = true; break; }
            }
            if (!suppressed) toTrigger.add(cand);
        }
        for (ExplosionCandidate e : toTrigger) {
            boolean fire = e.level.getRandom().nextDouble() < TrueImpactConfig.IMPACT_EXPLOSION_FIRE_CHANCE.get();
            e.level.getServer().execute(() -> e.level.explode(null, null, null, e.point.x, e.point.y, e.point.z, e.radius, fire, Level.ExplosionInteraction.BLOCK));
        }
    }

    private static boolean isElasticSubLevel(Object subLevel) {
        ServerLevel level = level(subLevel);
        if (level == null) return false;
        try {
            Method getPlot = subLevel.getClass().getMethod("getPlot");
            Object plot = getPlot.invoke(subLevel);
            Object box = plot.getClass().getMethod("getBoundingBox").invoke(plot);
            int minX = (int) number(box, "minX"); int minY = (int) number(box, "minY"); int minZ = (int) number(box, "minZ");
            int maxX = (int) number(box, "maxX"); int maxY = (int) number(box, "maxY"); int maxZ = (int) number(box, "maxZ");
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int limit = TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.get();
            for (int x = minX; x <= maxX && limit-- > 0; x++)
                for (int y = minY; y <= maxY && limit-- > 0; y++)
                    for (int z = minZ; z <= maxZ && limit-- > 0; z++)
                        if (PhysicsBlockPropertyHelper.getRestitution(level.getBlockState(pos.set(x, y, z))) >= TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static void applyImpulse(int sceneId, Object subLevel, Vector3d localPoint, Vector3d normal, double impulse) {
        Vector3d com = centerOfMass(subLevel);
        Integer rid = runtimeId(subLevel);
        if (normal.lengthSquared() < 1e-8 || com == null || rid == null) return;
        Vector3d localNormal = new Vector3d(normal).normalize().mul(impulse);
        Rapier3D.applyForce(sceneId, rid, localPoint.x - com.x, localPoint.y - com.y, localPoint.z - com.z, localNormal.x, localNormal.y, localNormal.z, true);
    }

    private static double mass(Object subLevel) {
        try { return ((Number) GET_MASS_METHOD.invoke(GET_MASS_TRACKER_METHOD.invoke(subLevel))).doubleValue(); } catch (Exception e) { return 1.0; }
    }

    private static Vector3d centerOfMass(Object subLevel) {
        try {
            Object center = GET_CENTER_OF_MASS_METHOD.invoke(GET_MASS_TRACKER_METHOD.invoke(subLevel));
            return new Vector3d((double)center.getClass().getMethod("x").invoke(center), (double)center.getClass().getMethod("y").invoke(center), (double)center.getClass().getMethod("z").invoke(center));
        } catch (Exception e) { return null; }
    }

    private static Vector3d rotationPoint(Object subLevel) {
        try {
            Object rp = ROTATION_POINT_METHOD.invoke(LOGICAL_POSE_METHOD.invoke(subLevel));
            return new Vector3d((double)rp.getClass().getMethod("x").invoke(rp), (double)rp.getClass().getMethod("y").invoke(rp), (double)rp.getClass().getMethod("z").invoke(rp));
        } catch (Exception e) { return null; }
    }

    private static ServerLevel level(Object subLevel) {
        try { return (ServerLevel) GET_LEVEL_METHOD.invoke(subLevel); } catch (Exception e) { return null; }
    }

    private static Integer runtimeId(Object subLevel) {
        try { return ((Number) RUNTIME_ID_FIELD.get(subLevel)).intValue(); } catch (Exception e) { return null; }
    }

    private static double number(Object target, String methodName) throws Exception {
        return ((Number) target.getClass().getMethod(methodName).invoke(target)).doubleValue();
    }

    private static Field findField(String cl, String f) {
        try { Field field = Class.forName(cl).getDeclaredField(f); field.setAccessible(true); return field; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static Method findMethod(String cl, String m) {
        try { Method method = Class.forName(cl).getMethod(m); method.setAccessible(true); return method; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static BlockState localState(Object subLevel, Vector3d localPoint, BlockPos.MutableBlockPos blockPos) {
        ServerLevel level = level(subLevel);
        Vector3d rotationPoint = rotationPoint(subLevel);
        if (level == null || rotationPoint == null) return Blocks.AIR.defaultBlockState();
        blockPos.set(localPoint.x + rotationPoint.x, localPoint.y + rotationPoint.y, localPoint.z + rotationPoint.z);
        return level.getBlockState(blockPos);
    }

    private record PointData(Vector3d local, Vector3d normal, double force, Object slA, Object slB) {}
    private record ExplosionCandidate(ServerLevel level, Vector3d point, float radius, double force) {}
    private record TerrainNode(BlockPos pos, double energy, double cost) {}
}
