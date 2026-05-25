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
    private static final Set<Block> COMPACTABLE_SOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.FARMLAND);

    private static class HardnessFragileCallback
    implements BlockSubLevelCollisionCallback {
        private HardnessFragileCallback() {
        }

        public BlockSubLevelCollisionCallback.CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity) {
            try {
            boolean canBreakWorldBlocks;
            boolean protectedSubLevelImpact;
            boolean hasVelocity;
            Vector3d velocityVec;
            double actualSubLevelSpeed;
            double mass;
            double structuralIntegrity;
            BlockState state;
            ServerLevel level;
            SubLevelPhysicsSystem system;
            // 1.1.2: track which sub-level this contact came from, so we can normalize
            // per-tick kinetic energy by total contact count (tank treads → low per-cell pressure).
            int matchedSslId = -1;
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
                // beta.9.1 — B2 real real fix. The callback's bakery mixin attaches this
                // callback to SUB-LEVEL voxel blocks (per RapierVoxelColliderBakery), so any
                // time this callback fires, `pos` is on a sub-level block. Hence gating at the
                // TOP is safe (and correct): when ENABLE_PHYSICAL_DESTRUCTION=false, no
                // destruction path downstream (soil compact / crack / break / propagate /
                // BlockDamageAccumulator) runs against sub-level blocks. The earlier
                // queryIntersecting-based gate (added in beta.9) sometimes missed when the
                // sub-level's bbox was stale mid-callback — accumulator then ran on lines
                // 273+ and accumulated fatigue → cumulative crack break. The user observed
                // exactly this ("还是会碎,都是累积裂纹碎的"). This top-level gate fixes it.
                // Terrain destruction is not affected: it goes through ElasticPairReaction's
                // applyPerContactTerrainHit, not this callback.
                if (!((Boolean)TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.get()).booleanValue()) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                level = system.getLevel();
                state = level.getBlockState(pos);
                if (state.isAir()) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                // beta.5/7: constraint-anchor block protection. Empirically: destroying the
                // block at a Sable constraint anchor (rope, rotary/cardan, fixed, free,
                // generic) orphans the joint → narrow_phase panic. The protection has two
                // legs, both belt-and-suspenders:
                //   1. Block TYPE match — known anchor block IDs (rope_connector and friends).
                //      Static, immune to sub-level movement.
                //   2. Position match — any block at a world position registered by the
                //      RapierRopeHandle / Rapier{Rotary,Fixed,Free,Generic}ConstraintHandle
                //      mixins. AUTOMATIC for every mod that uses Sable's constraint API.
                if (RopeBindingRegistry.isRopeAnchorBlockType(state)
                        || RopeBindingRegistry.isConstraintAnchorPosition(pos)) {
                    org.apache.logging.log4j.LogManager.getLogger("TIDetach").info(
                        "[beta] protected constraint-anchor block at {} ({}); skipping destruction",
                        pos, state.getBlock());
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                float hardness = state.getDestroySpeed((BlockGetter)level, pos);
                if (hardness < 0.0f) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                if (((Boolean)TrueImpactConfig.ENABLE_SOIL_COMPACTION.get()).booleanValue() && COMPACTABLE_SOIL.contains(state.getBlock()) && impactVelocity >= (Double)TrueImpactConfig.SOIL_COMPACTION_MIN_VELOCITY.get() && impactVelocity < (Double)TrueImpactConfig.SOIL_COMPACTION_MAX_VELOCITY.get()) {
                    // 1.1.2: probability gate. Without this, a tracked vehicle with ~20 simultaneous
                    // contacts compacts every grass cell underneath on every tick. With the default
                    // 0.05 chance, ~1 of every 20 contacts succeeds, leaving sparse footprints.
                    double soilChance = ((Double)TrueImpactConfig.SOIL_COMPACTION_PER_CONTACT_CHANCE.get()).doubleValue();
                    boolean compactFired = soilChance >= 1.0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < soilChance;
                    TIDiag.soil(pos, impactVelocity, soilChance, compactFired);
                    if (compactFired) {
                        final ServerLevel compactLevel = level;
                        final BlockPos compactPos = pos;
                        ImpactBreakQueue.enqueue(() -> compactLevel.setBlock(compactPos, Blocks.DIRT.defaultBlockState(), 3));
                    }
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                // Block transforms: user-defined block → block conversions (checked after soil compaction).
                if (((Boolean)TrueImpactConfig.ENABLE_BLOCK_TRANSFORMS.get()).booleanValue()
                    && impactVelocity >= (Double)TrueImpactConfig.BLOCK_TRANSFORM_MIN_VELOCITY.get()
                    && impactVelocity < (Double)TrueImpactConfig.BLOCK_TRANSFORM_MAX_VELOCITY.get()) {
                    BlockState transformed = BlockTransformRegistry.tryTransform(state);
                    if (transformed != null) {
                        double chance = (Double)TrueImpactConfig.BLOCK_TRANSFORM_PER_CONTACT_CHANCE.get();
                        if (chance >= 1.0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
                            final ServerLevel tl = level;
                            final BlockPos tp = pos;
                            final BlockState ts = transformed;
                            ImpactBreakQueue.enqueue(() -> tl.setBlock(tp, ts, 3));
                        }
                        return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                    }
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
                actualSubLevelSpeed = Double.NaN;
                // Bug 1 fix: use queryIntersecting to find the sublevel physically at the impact point.
                // getSubLevels() was removed from SubLevelPhysicsSystem in Sable 1.2.2.
                try {
                    BoundingBox3d queryBox = new BoundingBox3d(
                        pos.getX() - 0.1, pos.getY() - 0.1, pos.getZ() - 0.1,
                        pos.getX() + 1.1, pos.getY() + 1.1, pos.getZ() + 1.1
                    );
                    for (SubLevel sl : system.queryIntersecting(queryBox)) {
                        if (sl instanceof ServerSubLevel ssl) {
                            matchedSslId = ssl.getRuntimeId();
                            // beta.6: broad rope-sub-level exemption REMOVED. The crash was never
                            // about "rope-connected structures take damage" — it was specifically
                            // about the rope_connector block being destroyed (which orphans the
                            // joint). With that block type now hard-protected at the top of this
                            // callback, the rest of a rope structure CAN take normal destruction:
                            // hull blocks crumble, fragments fly, heatmap split may fire on
                            // non-anchor parts (free fragments, no rope → safe). The rope-anchored
                            // portion stays alive because its anchor block can't be destroyed.
                            //
                            // beta.9.1 — the redundant queryIntersecting-based ENABLE_PHYSICAL_-
                            // DESTRUCTION gate from beta.9 has been REMOVED. It only fired when
                            // queryIntersecting actually found `ssl`, which is unreliable
                            // mid-callback (sub-level bbox can be stale). The new top-level gate
                            // (around line ~98) catches the callback at entry regardless of
                            // queryIntersecting's state. This block now only handles mass /
                            // velocity collection for the destruction physics.
                            if (SUBLEVEL_GET_MASS_TRACKER != null && SUBLEVEL_MASS_DATA_GET_MASS != null) {
                                Object tracker = SUBLEVEL_GET_MASS_TRACKER.invoke(ssl);
                                mass = Math.max(((Number)SUBLEVEL_MASS_DATA_GET_MASS.invoke(tracker)).doubleValue(), 1.0E-6);
                                Vector3d linVel = ssl.latestLinearVelocity;
                                actualSubLevelSpeed = linVel.length();
                                if (actualSubLevelSpeed > 0.001) {
                                    velocityVec = new Vector3d((Vector3dc)linVel).normalize();
                                    hasVelocity = true;
                                }
                            }
                        }
                        break;
                    }
                }
                catch (Exception ignored) {}
            }
            if (Double.isFinite(actualSubLevelSpeed)) {
                if (actualSubLevelSpeed < (Double)TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
                    return BlockSubLevelCollisionCallback.CollisionResult.NONE;
                }
                impactVelocity = Math.min(impactVelocity, actualSubLevelSpeed);
            }
            mass = Math.max(mass, ElasticSubLevelDetector.nearbyMaxMass(level, pos, mass));
            double angleMultiplier = 1.0;
            if (hasVelocity) {
                Vector3d blockCenter = new Vector3d((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
                Vector3d normal = new Vector3d((Vector3dc)hitPos).sub((Vector3dc)blockCenter).normalize();
                double dot = Math.abs(velocityVec.dot((Vector3dc)normal));
                // 1.1.3: floor lowered from hardcoded 0.1 to config (default 0.02). Tracked
                // vehicles moving horizontally have dot≈0; with the old 0.1 floor each contact
                // still applied 10% of full KE to the ground, enough to dig trenches at speed.
                angleMultiplier = Math.max(((Double)TrueImpactConfig.IMPACT_ANGLE_FLOOR.get()).doubleValue(), dot);
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
            // 1.1.2: pressure normalization. Divide by smoothed per-tick contact count so a wide
            // flat sub-level (tank treads, ship hull) spreads its kinetic energy across all
            // contacts instead of dumping the full sub-level KE into each terrain cell. Without
            // this, tracked vehicles dig trenches just by moving across grass.
            double kineticEnergyRaw = kineticEnergy;
            int diagContactCount = 1;
            if (matchedSslId != -1) {
                try {
                    double contactDivisor = ContactPressureTracker.recordAndGetDivisor(
                        matchedSslId, level.getGameTime(),
                        ((Double)TrueImpactConfig.CONTACT_PRESSURE_EXPONENT.get()).doubleValue());
                    kineticEnergy /= contactDivisor;
                    diagContactCount = (int) Math.round(Math.pow(contactDivisor,
                        1.0 / Math.max(0.01, ((Double)TrueImpactConfig.CONTACT_PRESSURE_EXPONENT.get()).doubleValue())));
                } catch (Throwable ignored) {
                    // Normalization must never disturb the destruction path.
                }
            }
            double matchupScale = ImpactDamageContextCache.getNearby(level, pos, 2, 1.0);
            double scaledKineticEnergy = kineticEnergy * matchupScale;
            // 1.1.4-diag: capture for outcome logging below.
            final int DIAG_CONTACT_COUNT = diagContactCount;
            final double DIAG_KE_RAW = kineticEnergyRaw;
            double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, structuralIntegrity), 1.0);
            double materialToughness = Math.max(MaterialImpactProperties.displayToughness(state, structuralIntegrity), materialStrength);
            double crackResistance = Math.sqrt(materialStrength * materialToughness);
            double crackRatio = scaledKineticEnergy / Math.max(crackResistance, 1.0);
            double yieldRatio = scaledKineticEnergy / materialStrength;
            double elasticBreakVelocity = (Double)TrueImpactConfig.MIN_BREAK_VELOCITY.get() * (1.0 + restitution * (Double)TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            double elasticPropagationVelocity = (Double)TrueImpactConfig.MIN_PROPAGATION_VELOCITY.get() * (1.0 + restitution * (Double)TrueImpactConfig.RESTITUTION_BREAK_VELOCITY_MULTIPLIER.get());
            boolean elasticImpactingSubLevel = (Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && ElasticSubLevelDetector.hasNearbyElasticSubLevel(level, pos);
            boolean bl = protectedSubLevelImpact = (Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get() == false && (Boolean)TrueImpactConfig.PROTECT_NEARBY_SUBLEVEL_IMPACTS.get() != false && ElasticSubLevelDetector.hasNearbySubLevel(level, pos);
            if ((restitution >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get() || elasticImpactingSubLevel || protectedSubLevelImpact) && !((Boolean)TrueImpactConfig.ELASTIC_BLOCKS_BREAK_BLOCKS.get()).booleanValue() && !fragile) {
                // tangentMotion=zero: Rapier already computes elastic bounce from the block's restitution.
                // Returning reactionMotion here was ADDITIVE to Rapier's response, not a replacement.
                // With many contact points (large flat structure), the accumulated extra impulse was
                // multiplied across all contacts and launched the sub-level to extreme Y, triggering
                // Sable's recoverSubLevel() (remove + re-add) mid-tick. The remove freed Rapier collider
                // handles while stale narrow-phase pairs still referenced them → narrow_phase.rs:1115
                // "No element at index" panic. Returning NONE lets Rapier handle the bounce alone.
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "ELASTIC_OR_PROTECTED");
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            if (restitution >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get() && impactVelocity < (Double)TrueImpactConfig.ELASTIC_SHATTER_VELOCITY.get() && !fragile) {
                // Same reason as above: Rapier's native elastic response handles this.
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "ELASTIC_SHATTER_BELOW");
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            boolean bl2 = canBreakWorldBlocks = (Boolean)TrueImpactConfig.ENABLE_BLOCK_BREAKING.get() != false && (Boolean)TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get() != false && (Boolean)TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get() != false && MaterialImpactProperties.isDestructible(state, true);
            if (canBreakWorldBlocks && impactVelocity >= elasticPropagationVelocity && yieldRatio >= (Double)TrueImpactConfig.PROPAGATION_YIELD_THRESHOLD.get()) {
                // Defer destroyBlock — calling it during Rapier3D.step() removes the voxel collider
                // mid-step which corrupts island wake state and causes the "island should be awake" panic.
                final ServerLevel destroyLevel = level;
                final BlockPos destroyPos = pos;
                final Block blockForPropagation = state.getBlock();
                final double propagEnergy = scaledKineticEnergy * (Double)TrueImpactConfig.PROPAGATION_ENERGY_SCALE.get();
                final boolean doPropagation = ((Boolean)TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()).booleanValue();
                ImpactBreakQueue.enqueue(() -> {
                    destroyLevel.destroyBlock(destroyPos, false);
                    if (doPropagation) {
                        CrackPropagationUtils.propagateCracks(destroyLevel, destroyPos, blockForPropagation, propagEnergy);
                    }
                });
                // removeCollision=false: let Sable/Rapier keep the voxel collider until destroyBlock fires
                // next tick. Returning true here removes the collider mid-step → island state inconsistency
                // → "island should be awake" panic in narrow_phase.rs.
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "PROPAGATION_QUEUED");
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get()) {
                {
                    final ServerLevel destroyLevel = level;
                    final BlockPos destroyPos = pos;
                    ImpactBreakQueue.enqueue(() -> destroyLevel.destroyBlock(destroyPos, true));
                    TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "HEAVY_BREAK_QUEUED");
                }
                // removeCollision=false: same reason as propagation path above.
                // tangentMotion=zero: Rapier already computes the elastic bounce; adding reactionMotion
                // on top was double-counting the impulse and could launch sub-levels to extreme Y,
                // triggering a stale narrow-phase handle in Sable → narrow_phase.rs "No element at index".
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() && impactVelocity * yieldRatio > elasticBreakVelocity * ((Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() + 1.5)) {
                double overstress = Math.max(0.0, scaledKineticEnergy - materialStrength);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, overstress), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "REG_BREAK_SPIKE_FATIGUE(over=" + String.format("%.1f", overstress) + ")");
                // Same reason: Rapier handles the elastic bounce; accumulating extra impulse causes crash.
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            if (canBreakWorldBlocks && impactVelocity >= elasticBreakVelocity && yieldRatio > (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()) {
                double overstress = Math.max(0.0, scaledKineticEnergy - materialStrength);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, overstress * 0.65), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "REG_BREAK_FATIGUE(over=" + String.format("%.1f", overstress * 0.65) + ")");
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
            if (((Boolean)TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()).booleanValue() && ((Boolean)TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue() && crackRatio > (Double)TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
                double crackOverStress = Math.max(0.0, scaledKineticEnergy - crackResistance);
                BlockDamageAccumulator.apply(level, pos, MaterialImpactProperties.fatigueDamage(state, crackOverStress), materialToughness * (Double)TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(), system.hashCode() + pos.hashCode());
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "CRACK_ONLY_FATIGUE(over=" + String.format("%.1f", crackOverStress) + ")");
            } else {
                TIDiag.hardness(pos, matchedSslId, impactVelocity, mass, DIAG_KE_RAW, kineticEnergy, materialStrength, yieldRatio, DIAG_CONTACT_COUNT, "NO_DAMAGE");
            }
            return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            } catch (Throwable __callbackEx) {
                org.apache.logging.log4j.LogManager.getLogger("TrueImpact").error(
                    "[TrueImpact] sable$onCollision threw unexpectedly at pos={} — returning NONE to prevent hooks.rs unwrap panic",
                    pos, __callbackEx);
                return BlockSubLevelCollisionCallback.CollisionResult.NONE;
            }
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
