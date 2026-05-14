/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback
 *  dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback$CollisionResult
 *  dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.BlockDamageAccumulator;
import com.example.sabletrueimpact.CrackPropagationUtils;
import com.example.sabletrueimpact.ElasticSubLevelDetector;
import com.example.sabletrueimpact.ImpactDamageContextCache;
import com.example.sabletrueimpact.ImpactResolver;
import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactConfig;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

public class TrueImpactPhysicsSolver {
    public static final BlockSubLevelCollisionCallback HARDNESS_CALLBACK = new HardnessFragileCallback();

    // Bug 1 fix: cached reflection methods for sublevel mass lookup via queryIntersecting
    private static final java.lang.reflect.Method SUBLEVEL_GET_MASS_TRACKER;
    private static final java.lang.reflect.Method SUBLEVEL_MASS_DATA_GET_MASS;
    static {
        java.lang.reflect.Method mt = null, gm = null;
        try {
            mt = Class.forName("dev.ryanhcode.sable.sublevel.ServerSubLevel").getMethod("getMassTracker");
            mt.setAccessible(true);
            gm = Class.forName("dev.ryanhcode.sable.api.physics.mass.MassData").getMethod("getMass");
            gm.setAccessible(true);
        } catch (Exception ignored) {}
        SUBLEVEL_GET_MASS_TRACKER = mt;
        SUBLEVEL_MASS_DATA_GET_MASS = gm;
    }

    private static final TagKey<Block> SABLE_FRAGILE = TagKey.create((ResourceKey)Registries.BLOCK, (ResourceLocation)ResourceLocation.fromNamespaceAndPath((String)"sable", (String)"fragile"));
    private static final Set<Block> COMPACTABLE_SOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);

    private static class HardnessFragileCallback
    implements BlockSubLevelCollisionCallback {
        private HardnessFragileCallback() {
        }

        public BlockSubLevelCollisionCallback.CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity) {
            boolean canBreakWorldBlocks;
            boolean protectedSubLevelImpact;
            boolean hasVelocity;
            Vector3d velocityVec;
            double mass;
            double structuralIntegrity;
            BlockState state;
            ServerLevel level;
            SubLevelPhysicsSystem system;
            block21: {
                // Bug 4 fix: getCurrentlySteppingSystem() throws IllegalStateException in Sable 1.2.2 when null
                try {
                    system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
                } catch (IllegalStateException notStepping) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || impactVelocity < (Double)TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                level = system.getLevel();
                state = level.getBlockState(pos);
                if (state.isAir()) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                float hardness = state.getDestroySpeed((BlockGetter)level, pos);
                if (hardness < 0.0f) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                if (((Boolean)TrueImpactConfig.ENABLE_SOIL_COMPACTION.get()).booleanValue() && COMPACTABLE_SOIL.contains(state.getBlock()) && impactVelocity >= (Double)TrueImpactConfig.SOIL_COMPACTION_MIN_VELOCITY.get() && impactVelocity < (Double)TrueImpactConfig.SOIL_COMPACTION_MAX_VELOCITY.get()) {
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                    return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)new Vector3d(), false);
                }
                float blastResistance = state.getBlock().getExplosionResistance();
                int solidNeighbors = 0;
                for (Direction dir : Direction.values()) {
                    if (level.getBlockState(pos.relative(dir)).isAir()) continue;
                    ++solidNeighbors;
                }
                double supportMultiplier = 0.5 + (double)solidNeighbors * 0.25;
                if (hardness < 1.0f) {
                    supportMultiplier = Math.min(1.0, supportMultiplier);
                }
                structuralIntegrity = MaterialImpactProperties.baseStrength((BlockGetter)level, pos, state) * supportMultiplier;
                mass = (Double)TrueImpactConfig.FALLBACK_IMPACT_MASS.get();
                velocityVec = new Vector3d(0.0, -1.0, 0.0);
                hasVelocity = false;
                // Bug 1 fix: use queryIntersecting to find the sublevel physically at the impact point.
                // getSubLevels() was removed from SubLevelPhysicsSystem in Sable 1.2.2.
                try {
                    BoundingBox3d queryBox = new BoundingBox3d(
                        pos.getX() - 0.1, pos.getY() - 0.1, pos.getZ() - 0.1,
                        pos.getX() + 1.1, pos.getY() + 1.1, pos.getZ() + 1.1
                    );
                    for (SubLevel sl : system.queryIntersecting(queryBox)) {
                        if (sl instanceof ServerSubLevel ssl && SUBLEVEL_GET_MASS_TRACKER != null && SUBLEVEL_MASS_DATA_GET_MASS != null) {
                            Object tracker = SUBLEVEL_GET_MASS_TRACKER.invoke(ssl);
                            mass = Math.max(((Number)SUBLEVEL_MASS_DATA_GET_MASS.invoke(tracker)).doubleValue(), 1.0E-6);
                            Vector3d linVel = ssl.latestLinearVelocity;
                            if (linVel.lengthSquared() > 0.001) {
                                velocityVec = new Vector3d((Vector3dc)linVel).normalize();
                                hasVelocity = true;
                            }
                        }
                        break;
                    }
                }
                catch (Exception ignored) {}
            }
            mass = Math.max(mass, ElasticSubLevelDetector.nearbyMaxMass(level, pos, mass));
            double angleMultiplier = 1.0;
            if (hasVelocity) {
                Vector3d blockCenter = new Vector3d((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
                Vector3d normal = new Vector3d((Vector3dc)hitPos).sub((Vector3dc)blockCenter).normalize();
                double dot = Math.abs(velocityVec.dot((Vector3dc)normal));
                angleMultiplier = Math.max(0.1, dot);
            }
            double restitution = HardnessFragileCallback.clamp01(MaterialImpactProperties.getRestitution(state, PhysicsBlockPropertyHelper.getRestitution((BlockState)state)));
            double friction = Math.max(0.0, MaterialImpactProperties.getFriction(state, PhysicsBlockPropertyHelper.getFriction((BlockState)state)));
            boolean fragile = state.is(SABLE_FRAGILE);
            double effectiveMass = Math.min((Double)TrueImpactConfig.MAX_EFFECTIVE_MASS.get(), Math.pow(Math.max(mass, 1.0), (Double)TrueImpactConfig.MASS_EXPONENT.get()));
            double reboundFactor = Math.max(0.05, 1.0 - restitution * (Double)TrueImpactConfig.RESTITUTION_DAMAGE_REDUCTION.get());
            double frictionFactor = 1.0 - (1.0 - Math.min(1.0, friction)) * (Double)TrueImpactConfig.LOW_FRICTION_DAMAGE_REDUCTION.get();
            double fragileFactor = fragile ? (Double)TrueImpactConfig.FRAGILE_DAMAGE_MULTIPLIER.get() : 1.0;
            double velocityEnergy = Math.pow(Math.max(impactVelocity, 0.0), (Double)TrueImpactConfig.IMPACT_VELOCITY_EXPONENT.get());
            double kineticEnergy = 0.5 * effectiveMass * velocityEnergy * angleMultiplier * reboundFactor * frictionFactor * fragileFactor * (Double)TrueImpactConfig.DAMAGE_SCALE.get() * (Double)TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
            double matchupScale = ImpactDamageContextCache.getNearby(level, pos, 2, 1.0);
            double scaledKineticEnergy = kineticEnergy * matchupScale;
            double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, structuralIntegrity), 1.0);
            double materialToughness = Math.max(MaterialImpactProperties.displayToughness(state, structuralIntegrity), materialStrength);
            double crackResistance = Math.sqrt(materialStrength * materialToughness);
            double crackRatio = scaledKineticEnergy / Math.max(crackResistance, 1.0);
            double yieldRatio = scaledKineticEnergy / materialStrength;
            boolean suppressCallbackDamage = ImpactResolver.shouldSuppressCallbackDamage(level, pos, state);
            double elasticBreakVelocity = (Double)TrueImpactConfig.MIN_BREAK_VELOCITY.get() * (1.0 + restitution * (Double)TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            double elasticPropagationVelocity = (Double)TrueImpactConfig.MIN_PROPAGATION_VELOCITY.get() * (1.0 + restitution * (Double)TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            boolean elasticImpactingSubLevel = (Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && ElasticSubLevelDetector.hasNearbyElasticSubLevel(level, pos);
            boolean bl = protectedSubLevelImpact = (Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && (Boolean)TrueImpactConfig.PROTECT_NEARBY_SUBLEVEL_IMPACTS.get() != false && ElasticSubLevelDetector.hasNearbySubLevel(level, pos);
            if ((suppressCallbackDamage || restitution >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get() || elasticImpactingSubLevel || protectedSubLevelImpact) && !((Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()).booleanValue() && !fragile) {
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)HardnessFragileCallback.reactionMotion(pos, hitPos, impactVelocity, Math.max(yieldRatio, 1.0), Math.max(restitution, 0.5)), false);
            }
            if (restitution >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get() && impactVelocity < (Double)TrueImpactConfig.ELASTIC_SHATTER_VELOCITY.get() && !fragile) {
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)HardnessFragileCallback.reactionMotion(pos, hitPos, impactVelocity, Math.max(yieldRatio, 1.0), restitution), false);
            }
            boolean bl2 = canBreakWorldBlocks = (Boolean)TrueImpactConfig.ENABLE_BLOCK_BREAKING.get() != false && (Boolean)TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get() != false && (Boolean)TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get() != false && !suppressCallbackDamage && MaterialImpactProperties.isDestructible(state, true);
            if (canBreakWorldBlocks && impactVelocity >= elasticPropagationVelocity && yieldRatio >= (Double)TrueImpactConfig.PROPAGATION_YIELD_THRESHOLD.get()) {
                level.destroyBlock(pos, false);
                if (((Boolean)TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()).booleanValue()) {
                    CrackPropagationUtils.propagateCracks(level, pos, state.getBlock(), scaledKineticEnergy * (Double)TrueImpactConfig.PROPAGATION_ENERGY_SCALE.get());
                }
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)new Vector3d(), true);
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get()) {
                if (yieldRatio < (Double)TrueImpactConfig.REACTION_YIELD_LIMIT.get()) {
                    return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)HardnessFragileCallback.reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
                }
                level.destroyBlock(pos, true);
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)new Vector3d(), true);
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() && impactVelocity * yieldRatio > elasticBreakVelocity * ((Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() + 1.5)) {
                double overstress = Math.max(0.0, scaledKineticEnergy - materialStrength);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, overstress), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)HardnessFragileCallback.reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()) {
                double overstress = Math.max(0.0, scaledKineticEnergy - materialStrength);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, overstress * 0.65), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
                return new BlockSubLevelCollisionCallback.CollisionResult((Vector3dc)HardnessFragileCallback.reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
            }
            if (((Boolean)TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()).booleanValue() && ((Boolean)TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue() && !suppressCallbackDamage && crackRatio > (Double)TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
                double crackOverStress = Math.max(0.0, scaledKineticEnergy - crackResistance);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, crackOverStress), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
            }
            return BlockSubLevelCollisionCallback.CollisionResult.NONE;
        }

        private static double clamp01(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }

        private static Vector3d bounceMotion(BlockPos pos, Vector3d hitPos, double impactVelocity, double restitution) {
            Vector3d center = new Vector3d((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
            Vector3d normal = new Vector3d((Vector3dc)hitPos).sub((Vector3dc)center);
            if (normal.lengthSquared() < 1.0E-6) {
                normal.set(0.0, 1.0, 0.0);
            } else {
                normal.normalize();
            }
            return normal.mul(impactVelocity * restitution * (Double)TrueImpactConfig.BOUNCE_RESPONSE_SCALE.get());
        }

        private static Vector3d reactionMotion(BlockPos pos, Vector3d hitPos, double impactVelocity, double yieldRatio, double restitution) {
            double response = Math.min(1.0, yieldRatio / Math.max((Double)TrueImpactConfig.REACTION_YIELD_LIMIT.get(), 1.0));
            double elasticBonus = 1.0 + restitution;
            return HardnessFragileCallback.bounceMotion(pos, hitPos, impactVelocity, Math.max(0.15, restitution)).mul((Double)TrueImpactConfig.REACTION_RESPONSE_SCALE.get() * response * elasticBonus);
        }
    }
}
