package com.example.sabletrueimpact;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.Set;

public class TrueImpactPhysicsSolver {
    public static final BlockSubLevelCollisionCallback HARDNESS_CALLBACK = new HardnessFragileCallback();
    private static TagKey<net.minecraft.world.level.block.Block> fragileTag;

    private static TagKey<net.minecraft.world.level.block.Block> getFragileTag() {
        if (fragileTag == null) {
            fragileTag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", "fragile"));
        }
        return fragileTag;
    }

    /** Blocks that can be compacted into dirt by a light impact instead of being broken. */
    private static final Set<Block> COMPACTABLE_SOIL = Set.of(
            Blocks.GRASS_BLOCK,
            Blocks.PODZOL,
            Blocks.MYCELIUM
    );

    private static class HardnessFragileCallback implements BlockSubLevelCollisionCallback {
        @Override
        public BlockSubLevelCollisionCallback.CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity) {
            SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
            if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                    || system == null
                    || impactVelocity < TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }

            ServerLevel level = system.getLevel();
            BlockState state = level.getBlockState(pos);

            if (state.isAir()) {
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }

            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0.0f) { // Unbreakable
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }

            // ── Soil compaction: light impact presses grass/podzol/mycelium into dirt ──
            if (TrueImpactConfig.ENABLE_SOIL_COMPACTION.get()
                    && COMPACTABLE_SOIL.contains(state.getBlock())
                    && impactVelocity >= TrueImpactConfig.SOIL_COMPACTION_MIN_VELOCITY.get()
                    && impactVelocity < TrueImpactConfig.SOIL_COMPACTION_MAX_VELOCITY.get()) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                return new BlockSubLevelCollisionCallback.CollisionResult(new org.joml.Vector3d(), false);
            }
            // ─────────────────────────────────────────────────────────────────────────

            float blastResistance = state.getBlock().getExplosionResistance();
            
            // 1. Calculate structural support (Sharpness / Area)
            // A block with many solid neighbors is part of a wall/hull and shares the load.
            // A block with few solid neighbors is a spike/corner and takes concentrated force.
            int solidNeighbors = 0;
            for (Direction dir : Direction.values()) {
                if (!level.getBlockState(pos.relative(dir)).isAir()) {
                    solidNeighbors++;
                }
            }
            // Support multiplier: 0.5 (spike) to 2.0 (fully encased)
            double supportMultiplier = 0.5 + (solidNeighbors * 0.25);
            if (hardness < 1.0f) {
                // Soil and other soft materials should not become stronger than timber just because they are surrounded.
                supportMultiplier = Math.min(1.0, supportMultiplier);
            }
            double structuralIntegrity = ((hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get())
                    + (blastResistance * TrueImpactConfig.BLAST_STRENGTH_FACTOR.get())
                    + TrueImpactConfig.BASE_STRENGTH.get()) * supportMultiplier;
            if (hardness < 1.0f) {
                structuralIntegrity *= TrueImpactConfig.SOFT_BLOCK_STRENGTH_MULTIPLIER.get();
            }

            // 2. Fetch Mass and Velocity Vector via Reflection
            double mass = TrueImpactConfig.FALLBACK_IMPACT_MASS.get();
            Vector3d velocityVec = new Vector3d(0, -1, 0); // Default to falling if unknown
            boolean hasVelocity = false;
            
            try {
                Object[] subLevels = (Object[]) system.getClass().getMethod("getSubLevels").invoke(system);
                if (subLevels != null && subLevels.length > 0) {
                    Object ship = subLevels[0];
                    Object massTracker = ship.getClass().getMethod("getMassTracker").invoke(ship);
                    mass = Math.max((double) massTracker.getClass().getMethod("getMass").invoke(massTracker), 1.0E-6);
                    
                    // Try to get linear velocity
                    try {
                        Object phys = ship.getClass().getMethod("getPhysics").invoke(ship);
                        velocityVec = (Vector3d) phys.getClass().getMethod("getLinearVelocity").invoke(phys);
                        if (velocityVec.lengthSquared() > 0.001) {
                            velocityVec = new Vector3d(velocityVec).normalize();
                            hasVelocity = true;
                        }
                    } catch (Exception e2) {
                        // Ignore
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            mass = Math.max(mass, ElasticSubLevelDetector.nearbyMaxMass(level, pos, mass));

            // 3. Calculate Impact Angle Decay (Normal Vector)
            double angleMultiplier = 1.0;
            if (hasVelocity) {
                // Estimate block normal based on hitPos relative to block center
                Vector3d blockCenter = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vector3d normal = new Vector3d(hitPos).sub(blockCenter).normalize();
                
                // Dot product of velocity and normal. 
                // If velocity is opposite to normal, it's a direct hit (-1).
                // If orthogonal, it's a scrape (0).
                double dot = Math.abs(velocityVec.dot(normal));
                angleMultiplier = Math.max(0.1, dot); // 10% damage for pure scrapes
            }

            double restitution = clamp01(PhysicsBlockPropertyHelper.getRestitution(state));
            double friction = Math.max(0.0, PhysicsBlockPropertyHelper.getFriction(state));
            boolean fragile = state.is(getFragileTag());

            // Distributed kinetic energy. Sable's restitution/friction are treated as material response:
            // bouncy or low-friction blocks shed more energy into rebound/sliding instead of fracture.
            double effectiveMass = Math.min(TrueImpactConfig.MAX_EFFECTIVE_MASS.get(),
                    Math.pow(Math.max(mass, 1.0), TrueImpactConfig.MASS_EXPONENT.get()));
            double reboundFactor = Math.max(0.05, 1.0 - restitution * TrueImpactConfig.RESTITUTION_DAMAGE_REDUCTION.get());
            double frictionFactor = 1.0 - ((1.0 - Math.min(1.0, friction)) * TrueImpactConfig.LOW_FRICTION_DAMAGE_REDUCTION.get());
            double fragileFactor = fragile ? TrueImpactConfig.FRAGILE_DAMAGE_MULTIPLIER.get() : 1.0;
            double velocityEnergy = Math.pow(Math.max(impactVelocity, 0.0), TrueImpactConfig.IMPACT_VELOCITY_EXPONENT.get());
            double kineticEnergy = 0.5 * effectiveMass * velocityEnergy * angleMultiplier
                    * reboundFactor * frictionFactor * fragileFactor
                    * TrueImpactConfig.DAMAGE_SCALE.get()
                    * TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
            
            double breakThreshold = Math.max(MaterialImpactProperties.breakThreshold(state, structuralIntegrity), 1.0);
            double overStress = Math.max(0.0, kineticEnergy - breakThreshold);
            double yieldRatio = kineticEnergy / breakThreshold;
            double elasticBreakVelocity = TrueImpactConfig.MIN_BREAK_VELOCITY.get()
                    * (1.0 + restitution * TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            double elasticPropagationVelocity = TrueImpactConfig.MIN_PROPAGATION_VELOCITY.get()
                    * (1.0 + restitution * TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            boolean elasticImpactingSubLevel = !TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()
                    && ElasticSubLevelDetector.hasNearbyElasticSubLevel(level, pos);
            boolean protectedSubLevelImpact = !TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()
                    && TrueImpactConfig.PROTECT_NEARBY_SUBLEVEL_IMPACTS.get()
                    && ElasticSubLevelDetector.hasNearbySubLevel(level, pos);

            if ((restitution >= TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get() || elasticImpactingSubLevel || protectedSubLevelImpact)
                    && !TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()
                    && !fragile) {
                return new BlockSubLevelCollisionCallback.CollisionResult(
                        reactionMotion(pos, hitPos, impactVelocity, Math.max(yieldRatio, 1.0), Math.max(restitution, 0.5)), false);
            }

            if (restitution >= TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get()
                    && impactVelocity < TrueImpactConfig.ELASTIC_SHATTER_VELOCITY.get()
                    && !fragile) {
                return new BlockSubLevelCollisionCallback.CollisionResult(
                        reactionMotion(pos, hitPos, impactVelocity, Math.max(yieldRatio, 1.0), restitution), false);
            }

            boolean canBreakWorldBlocks = TrueImpactConfig.ENABLE_BLOCK_BREAKING.get()
                    && TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get();

            if (canBreakWorldBlocks
                    && impactVelocity >= elasticPropagationVelocity
                    && yieldRatio >= TrueImpactConfig.PROPAGATION_YIELD_THRESHOLD.get()) {
                // Catastrophic
                level.destroyBlock(pos, false);
                if (TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()) {
                    CrackPropagationUtils.propagateCracks(level, pos, state.getBlock(), kineticEnergy * TrueImpactConfig.PROPAGATION_ENERGY_SCALE.get());
                }
                return new BlockSubLevelCollisionCallback.CollisionResult(new org.joml.Vector3d(), true);
            } else if (canBreakWorldBlocks
                    && impactVelocity >= elasticBreakVelocity
                    && yieldRatio > TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get()) {
                if (yieldRatio < TrueImpactConfig.REACTION_YIELD_LIMIT.get()) {
                    return new BlockSubLevelCollisionCallback.CollisionResult(
                            reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
                }
                // Medium break
                level.destroyBlock(pos, true);
                return new BlockSubLevelCollisionCallback.CollisionResult(new org.joml.Vector3d(), true);
            } else if (canBreakWorldBlocks
                    && impactVelocity >= elasticBreakVelocity
                    && yieldRatio > TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()
                    && impactVelocity * yieldRatio > elasticBreakVelocity * (TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() + 1.5)) {
                return new BlockSubLevelCollisionCallback.CollisionResult(
                        reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
            } else if (canBreakWorldBlocks
                    && impactVelocity >= elasticBreakVelocity
                    && yieldRatio > TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()) {
                // Gentle break
                return new BlockSubLevelCollisionCallback.CollisionResult(
                        reactionMotion(pos, hitPos, impactVelocity, yieldRatio, restitution), false);
            } else if (TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()
                    && TrueImpactConfig.ENABLE_CRACKS.get()
                    && yieldRatio > TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
                // Cracks
                int crackProgress = (int) Math.min(5, ((yieldRatio - TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) / 1.25) * 6);
                boolean broke = BlockDamageAccumulator.apply(
                        level,
                        pos,
                        MaterialImpactProperties.fatigueDamage(state, overStress),
                        breakThreshold * TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(),
                        system.hashCode() + pos.hashCode()
                );
                if (!broke) {
                    level.destroyBlockProgress(system.hashCode() + pos.hashCode(), pos, crackProgress);
                }
            }

            return BlockSubLevelCollisionCallback.CollisionResult.NONE;
        }

        private static double clamp01(double value) {
            return Math.max(0.0, Math.min(1.0, value));
        }

        private static Vector3d bounceMotion(BlockPos pos, Vector3d hitPos, double impactVelocity, double restitution) {
            Vector3d center = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vector3d normal = new Vector3d(hitPos).sub(center);
            if (normal.lengthSquared() < 1.0E-6) {
                normal.set(0.0, 1.0, 0.0);
            } else {
                normal.normalize();
            }
            return normal.mul(impactVelocity * restitution * TrueImpactConfig.BOUNCE_RESPONSE_SCALE.get());
        }

        private static Vector3d reactionMotion(BlockPos pos, Vector3d hitPos, double impactVelocity, double yieldRatio, double restitution) {
            double response = Math.min(1.0, yieldRatio / Math.max(TrueImpactConfig.REACTION_YIELD_LIMIT.get(), 1.0));
            double elasticBonus = 1.0 + restitution;
            return bounceMotion(pos, hitPos, impactVelocity, Math.max(0.15, restitution))
                    .mul(TrueImpactConfig.REACTION_RESPONSE_SCALE.get() * response * elasticBonus);
        }
    }
}
