package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public final class ElasticPairReaction {
    private static final Field RUNTIME_ID_FIELD = findField("dev.ryanhcode.sable.sublevel.ServerSubLevel", "runtimeId");
    private static final Method GET_LEVEL_METHOD = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getLevel");
    private static final Method GET_MASS_TRACKER_METHOD = findMethod("dev.ryanhcode.sable.sublevel.ServerSubLevel", "getMassTracker");
    private static final Method GET_MASS_METHOD = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getMass");
    private static final Method GET_CENTER_OF_MASS_METHOD = findMethod("dev.ryanhcode.sable.api.physics.mass.MassData", "getCenterOfMass");
    private static final Method LOGICAL_POSE_METHOD = findMethod("dev.ryanhcode.sable.sublevel.SubLevel", "logicalPose");
    private static final Method ROTATION_POINT_METHOD = findMethod("dev.ryanhcode.sable.companion.math.Pose3d", "rotationPoint");

    private ElasticPairReaction() {
    }

    public static void apply(int sceneId, Int2ObjectMap<?> activeSubLevels, double[] collisions) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_PAIR_REACTION.get()
                || collisions.length == 0) {
            return;
        }
        TrueImpactPerformance.recordCollisionBatch(collisions.length / 15);

        Vector3d localPointA = new Vector3d();
        Vector3d localPointB = new Vector3d();
        Vector3d normalA = new Vector3d();
        Vector3d normalB = new Vector3d();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < collisions.length / 15; i++) {
            int start = i * 15;
            int idA = (int) collisions[start];
            int idB = (int) collisions[start + 1];
            double forceAmount = collisions[start + 2];
            normalA.set(collisions[start + 3], collisions[start + 4], collisions[start + 5]);
            normalB.set(collisions[start + 6], collisions[start + 7], collisions[start + 8]);
            localPointA.set(collisions[start + 9], collisions[start + 10], collisions[start + 11]);
            localPointB.set(collisions[start + 12], collisions[start + 13], collisions[start + 14]);

            Object subLevelA = activeSubLevels.get(idA);
            Object subLevelB = activeSubLevels.get(idB);
            if (forceAmount <= 0.0) {
                continue;
            }
            if (subLevelA == null ^ subLevelB == null) {
                applyTerrainImpact(subLevelA != null ? subLevelA : subLevelB,
                        subLevelA != null ? localPointA : localPointB,
                        subLevelA != null ? localPointB : localPointA,
                        subLevelA != null ? normalA : normalB,
                        forceAmount);
                continue;
            }
            if (subLevelA == null) {
                continue;
            }
            SubLevelFracture.tryFracture(subLevelA, localPointA, normalA, forceAmount);
            SubLevelFracture.tryFracture(subLevelB, localPointB, normalB, forceAmount);

            BlockState stateA = localState(subLevelA, localPointA, blockPos);
            BlockState stateB = localState(subLevelB, localPointB, blockPos);
            double restitutionA = clamp01(PhysicsBlockPropertyHelper.getRestitution(stateA));
            double restitutionB = clamp01(PhysicsBlockPropertyHelper.getRestitution(stateB));
            double restitution = Math.max(restitutionA, restitutionB);
            if (restitution < TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get()) {
                continue;
            }

            double massA = Math.max(mass(subLevelA), 1.0);
            double massB = Math.max(mass(subLevelB), 1.0);
            double reducedMass = (massA * massB) / (massA + massB);
            double cappedForce = Math.min(forceAmount, TrueImpactConfig.PAIR_REACTION_MAX_IMPULSE.get());
            double impulse = cappedForce * restitution * TrueImpactConfig.PAIR_REACTION_SCALE.get();
            impulse = Math.min(impulse, reducedMass * TrueImpactConfig.PAIR_REACTION_MAX_VELOCITY_CHANGE.get());
            if (impulse <= 1.0E-6) {
                continue;
            }

            applyImpulse(sceneId, subLevelA, localPointA, normalA, impulse);
            applyImpulse(sceneId, subLevelB, localPointB, normalB, impulse);
        }
    }

    private static void applyTerrainImpact(Object subLevel, Vector3d localPoint, Vector3d terrainPoint, Vector3d normal, double forceAmount) {
        if (!TrueImpactConfig.ENABLE_TERRAIN_IMPACT_DAMAGE.get()
                || forceAmount < TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get()
                || isForgivenStepContact(terrainPoint, normal)
                || (TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && isElasticSubLevel(subLevel))) {
            return;
        }
        ServerLevel level = level(subLevel);
        if (level == null) {
            return;
        }
        SubLevelFracture.tryFracture(subLevel, localPoint, normal, forceAmount);
        double mass = Math.min(TrueImpactConfig.TERRAIN_IMPACT_MAX_EFFECTIVE_MASS.get(),
                Math.pow(Math.max(mass(subLevel), 1.0), TrueImpactConfig.TERRAIN_IMPACT_MASS_EXPONENT.get()));
        double force = scaledForce(forceAmount, TrueImpactConfig.TERRAIN_IMPACT_FORCE_THRESHOLD.get(), TrueImpactConfig.TERRAIN_IMPACT_FORCE_EXPONENT.get());
        double energy = force * mass * TrueImpactConfig.TERRAIN_IMPACT_DAMAGE_SCALE.get() * TrueImpactConfig.DAMAGE_SCALE.get();
        if (TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()) {
            BlockPos center = BlockPos.containing(terrainPoint.x, terrainPoint.y, terrainPoint.z);
            damageTerrain(level, center, normal, energy);
        }
        damageEntities(level, new Vec3(terrainPoint.x, terrainPoint.y, terrainPoint.z), energy);
    }

    private static boolean isForgivenStepContact(Vector3d terrainPoint, Vector3d normal) {
        if (Math.abs(normal.y) > TrueImpactConfig.TERRAIN_STEP_SIDE_NORMAL_THRESHOLD.get()) {
            return false;
        }
        double yInBlock = terrainPoint.y - Math.floor(terrainPoint.y);
        return 1.0 - yInBlock <= TrueImpactConfig.TERRAIN_STEP_CONTACT_FORGIVENESS.get();
    }

    private static double scaledForce(double forceAmount, double threshold, double exponent) {
        if (exponent == 1.0 || forceAmount <= 0.0) {
            return forceAmount;
        }
        double reference = Math.max(threshold, 1.0);
        double normalized = Math.max(forceAmount / reference, 0.0);
        return forceAmount * Math.pow(normalized, exponent - 1.0);
    }

    private static void damageEntities(ServerLevel level, Vec3 impactPoint, double energy) {
        if (!TrueImpactConfig.ENABLE_ENTITY_IMPACT_DAMAGE.get() || energy <= 0.0) {
            return;
        }
        double radius = TrueImpactConfig.ENTITY_IMPACT_RADIUS.get();
        AABB bounds = AABB.ofSize(impactPoint, radius * 2.0, radius * 2.0, radius * 2.0);
        double baseDamage = Math.sqrt(energy) * TrueImpactConfig.ENTITY_IMPACT_DAMAGE_SCALE.get();
        if (baseDamage < TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) {
            return;
        }
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity.isAlive() && !entity.isSpectator())) {
            double distance = Math.max(0.0, entity.position().distanceTo(impactPoint));
            double falloff = Math.max(0.0, 1.0 - distance / Math.max(radius, 0.001));
            double maxDamage = TrueImpactConfig.ENTITY_IMPACT_MAX_DAMAGE.get();
            float damage = (float) (maxDamage <= 0.0 ? baseDamage * falloff : Math.min(maxDamage, baseDamage * falloff));
            if (damage >= TrueImpactConfig.ENTITY_IMPACT_MIN_DAMAGE.get()) {
                entity.hurt(level.damageSources().cramming(), damage);
            }
        }
    }

    private static void damageTerrain(ServerLevel level, BlockPos center, Vector3d normal, double energy) {
        PriorityQueue<TerrainNode> queue = new PriorityQueue<>(Comparator.comparingDouble(TerrainNode::cost));
        Set<BlockPos> visited = new HashSet<>();
        Vector3d direction = new Vector3d(normal);
        if (direction.lengthSquared() < 1.0E-8) {
            direction.set(0.0, -1.0, 0.0);
        } else {
            direction.normalize();
        }
        queue.add(new TerrainNode(center.immutable(), energy, 0.0));
        visited.add(center.immutable());
        int broken = 0;

        while (!queue.isEmpty() && broken < TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.get()) {
            TerrainNode node = queue.poll();
            BlockPos pos = node.pos();
            double localEnergy = node.energy();
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0f || state.is(Blocks.BEDROCK)) {
                continue;
            }
            double strength = (state.getDestroySpeed(level, pos) * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get())
                    + (state.getBlock().getExplosionResistance() * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get())
                    + TrueImpactConfig.BASE_STRENGTH.get();
            if (state.getDestroySpeed(level, pos) < 1.0f) {
                strength *= TrueImpactConfig.SOFT_BLOCK_STRENGTH_MULTIPLIER.get();
            }
            double yield = localEnergy / Math.max(strength, 1.0);
            if (yield >= TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get()) {
                level.destroyBlock(pos, true);
                broken++;
                double remaining = (localEnergy - strength) * 0.55;
                if (remaining > strength * 0.25) {
                    for (Direction dir : Direction.values()) {
                        BlockPos next = pos.relative(dir).immutable();
                        if (!visited.add(next)) {
                            continue;
                        }
                        Vector3d step = new Vector3d(dir.getStepX(), dir.getStepY(), dir.getStepZ());
                        double directionalBias = 1.0 + Math.max(0.0, step.dot(direction)) * 0.75;
                        double cost = node.cost() + 1.0 / directionalBias;
                        queue.add(new TerrainNode(next, remaining * directionalBias, cost));
                    }
                }
            } else if (TrueImpactConfig.ENABLE_CRACKS.get() && yield > TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
                boolean broke = BlockDamageAccumulator.apply(
                        level,
                        pos,
                        localEnergy,
                        strength * TrueImpactConfig.TERRAIN_IMPACT_BREAK_YIELD.get(),
                        pos.hashCode() * 17
                );
                if (broke) {
                    broken++;
                } else {
                    level.destroyBlockProgress(pos.hashCode() * 17, pos, (int) Math.min(5, yield * 3.0));
                }
            }
        }
    }

    private record TerrainNode(BlockPos pos, double energy, double cost) {
    }

    private static boolean isElasticSubLevel(Object subLevel) {
        ServerLevel level = level(subLevel);
        if (level == null) {
            return false;
        }
        try {
            Object bounds = plotBounds(subLevel);
            int minX = (int) number(bounds, "minX");
            int minY = (int) number(bounds, "minY");
            int minZ = (int) number(bounds, "minZ");
            int maxX = (int) number(bounds, "maxX");
            int maxY = (int) number(bounds, "maxY");
            int maxZ = (int) number(bounds, "maxZ");
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int scanned = 0;
            int limit = TrueImpactConfig.ELASTIC_SUBLEVEL_SCAN_LIMIT.get();
            for (int x = minX; x <= maxX && scanned < limit; x++) {
                for (int y = minY; y <= maxY && scanned < limit; y++) {
                    for (int z = minZ; z <= maxZ && scanned < limit; z++) {
                        scanned++;
                        if (PhysicsBlockPropertyHelper.getRestitution(level.getBlockState(pos.set(x, y, z))) >= TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get()) {
                            return true;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }

    private static BlockState localState(Object subLevel, Vector3d localPoint, BlockPos.MutableBlockPos blockPos) {
        ServerLevel level = level(subLevel);
        Vector3d rotationPoint = rotationPoint(subLevel);
        if (level == null || rotationPoint == null) {
            return net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        }
        blockPos.set(
                localPoint.x + rotationPoint.x,
                localPoint.y + rotationPoint.y,
                localPoint.z + rotationPoint.z
        );
        return level.getBlockState(blockPos);
    }

    private static void applyImpulse(int sceneId, Object subLevel, Vector3d localPoint, Vector3d normal, double impulse) {
        Vector3d centerOfMass = centerOfMass(subLevel);
        Integer runtimeId = runtimeId(subLevel);
        if (normal.lengthSquared() < 1.0E-8 || centerOfMass == null || runtimeId == null) {
            return;
        }
        Vector3d localNormal = new Vector3d(normal).normalize().mul(impulse);
        Rapier3D.applyForce(
                sceneId,
                runtimeId,
                localPoint.x - centerOfMass.x,
                localPoint.y - centerOfMass.y,
                localPoint.z - centerOfMass.z,
                localNormal.x,
                localNormal.y,
                localNormal.z,
                true
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double mass(Object subLevel) {
        try {
            Object massTracker = GET_MASS_TRACKER_METHOD.invoke(subLevel);
            return ((Number) GET_MASS_METHOD.invoke(massTracker)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0;
        }
    }

    private static Object plotBounds(Object subLevel) throws ReflectiveOperationException {
        Method getPlot = subLevel.getClass().getMethod("getPlot");
        Object plot = getPlot.invoke(subLevel);
        Method getBoundingBox = plot.getClass().getMethod("getBoundingBox");
        return getBoundingBox.invoke(plot);
    }

    private static double number(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return ((Number) method.invoke(target)).doubleValue();
    }

    private static Vector3d centerOfMass(Object subLevel) {
        try {
            Object massTracker = GET_MASS_TRACKER_METHOD.invoke(subLevel);
            Object center = GET_CENTER_OF_MASS_METHOD.invoke(massTracker);
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

    private static Vector3d rotationPoint(Object subLevel) {
        try {
            Object pose = LOGICAL_POSE_METHOD.invoke(subLevel);
            Object rotationPoint = ROTATION_POINT_METHOD.invoke(pose);
            Method x = rotationPoint.getClass().getMethod("x");
            Method y = rotationPoint.getClass().getMethod("y");
            Method z = rotationPoint.getClass().getMethod("z");
            return new Vector3d(((Number) x.invoke(rotationPoint)).doubleValue(), ((Number) y.invoke(rotationPoint)).doubleValue(), ((Number) z.invoke(rotationPoint)).doubleValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static ServerLevel level(Object subLevel) {
        try {
            Object level = GET_LEVEL_METHOD.invoke(subLevel);
            return level instanceof ServerLevel serverLevel ? serverLevel : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Integer runtimeId(Object subLevel) {
        try {
            return ((Number) RUNTIME_ID_FIELD.get(subLevel)).intValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field findField(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable field " + className + "#" + fieldName, e);
        }
    }

    private static Method findMethod(String className, String methodName) {
        try {
            Method method = Class.forName(className).getMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing Sable method " + className + "#" + methodName, e);
        }
    }
}
