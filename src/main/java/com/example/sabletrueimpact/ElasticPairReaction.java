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
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    // fork_7: SubLevel.boundingBox() = globalBounds (post-pose) — world-space AABB used by the
    // predictive substep terrain sweep. (Stale during block callbacks per CLAUDE.md; we only
    // call this from between-substep window in SubLevelPhysicsSystemMixin, after step.)
    private static final Method BOUNDING_BOX_METHOD = ElasticPairReaction.findMethodSafe("dev.ryanhcode.sable.sublevel.SubLevel", "boundingBox");
    // fork_17: SubLevel.globalBoundsTransform — the org.joml.Matrix4d that maps plot-local
    // coords → world (Sable fills it in updateBoundingBox via globalBounds.transform(pose,...)).
    // Inverting it gives a ROTATION-CORRECT world→plot-local mapping — the keystone every
    // body-frame approach lacked.
    private static final Field GLOBAL_BOUNDS_TRANSFORM_FIELD = ElasticPairReaction.findFieldSafe("dev.ryanhcode.sable.sublevel.SubLevel", "globalBoundsTransform");
    // fork_22: Pose3d.orientation() → the structure's rotation quaternion. Used to un-rotate the
    // Rapier body-frame contact back to the axis-aligned plot-local block grid (固若金汤 fix).
    private static final Method ORIENTATION_METHOD = ElasticPairReaction.findMethodSafe("dev.ryanhcode.sable.companion.math.Pose3d", "orientation");
    private static volatile boolean SUBLEVEL_WINNER_LOGGED = false;

    // Bug 2 fix: queue impulses during post-step and flush them in pre-step to avoid Rapier island panic.
    private record PendingImpulse(int sceneId, int runtimeId, Object subLevel, Vector3d localPoint, Vector3d normal, double impulse) {}
    private static final java.util.List<PendingImpulse> PENDING_IMPULSES = new java.util.ArrayList<>();
    private static final int MAX_PENDING_IMPULSES = 512;
    // fork_9: ported from TrueImpactPhysicsSolver. Light impacts on these blocks compact to dirt
    // instead of breaking. Used by applyPerContactTerrainHit before the destruction ladder.
    private static final Set<Block> COMPACTABLE_SOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.FARMLAND);

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
            // beta.6: broad rope-sub-level exemption REMOVED. The narrow rope_connector block
            // type protection (in HardnessFragileCallback + SubLevelFracture.applyCandidates)
            // is the real, surgical fix; this skip-everything-rope was over-cautious. Now
            // rope-structure collisions process normally — per-contact terrain destruction
            // works, fracture clusters can form, debris flies. The rope's anchor blocks stay
            // safe because they're block-type-protected.
            // fork_6: per-contact terrain hit (HARDNESS_CALLBACK port, WORLD side).
            // fork_9: + sub-level-side hit so the structure's own blocks chip on impact.
            // fork_13: each call wrapped in try/catch — a throw inside per-contact (e.g. a
            //          getBlockState forcing far-out worldgen) must NEVER abort the apply()
            //          loop, or cluster destruction + every later contact silently die too
            //          (that was fork_12's "任何破坏都不发生" regression).
            if (slA != null && slB == null) {
                try { ElasticPairReaction.applyPerContactTerrainHit(sceneId, collisions, start, slA, true); }
                catch (RuntimeException | LinkageError e) { ElasticPairReaction.logPerContactError(e); }
                try { ElasticPairReaction.applyPerContactSubLevelSideHit(sceneId, collisions, start, slA, true); }
                catch (RuntimeException | LinkageError e) { ElasticPairReaction.logPerContactError(e); }
            } else if (slB != null && slA == null) {
                try { ElasticPairReaction.applyPerContactTerrainHit(sceneId, collisions, start, slB, false); }
                catch (RuntimeException | LinkageError e) { ElasticPairReaction.logPerContactError(e); }
                try { ElasticPairReaction.applyPerContactSubLevelSideHit(sceneId, collisions, start, slB, false); }
                catch (RuntimeException | LinkageError e) { ElasticPairReaction.logPerContactError(e); }
            }
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

    // Called from prePhysicsTicks (before the substep loop) — the only safe window to touch
    // Rapier bodies. Clamps the velocity of any structure that has blown up so it can never
    // reach an extreme Y coordinate. If it did, Sable's ServerSubLevel.tick() would markRemoved()
    // and free the Rapier collider between physics substeps while a stale narrow-phase contact
    // pair still references it → narrow_phase.rs:1115 "No element at index" non-unwinding panic
    // (hard JVM abort, uncatchable). Capping velocity removes the trigger entirely.
    public static void clampRunawaySubLevels(Int2ObjectMap<?> activeSubLevels) {
        if (activeSubLevels == null || !((Boolean)TrueImpactConfig.ENABLE_RUNAWAY_VELOCITY_CLAMP.get()).booleanValue()) {
            return;
        }
        double maxLin = (Double)TrueImpactConfig.MAX_SUBLEVEL_LINEAR_SPEED.get();
        double maxAng = (Double)TrueImpactConfig.MAX_SUBLEVEL_ANGULAR_SPEED.get();
        Vector3d lin = new Vector3d();
        Vector3d ang = new Vector3d();
        Vector3d linFix = new Vector3d();
        Vector3d angFix = new Vector3d();
        for (Object o : activeSubLevels.values()) {
            // fork_26: rope-connected sub-levels MUST be clamped too. fork_2 exempted them
            // ("don't touch rope physics"), but that let a hard rope-balloon smash fling them to
            // an extreme Y coordinate → Sable's own "extreme Y range, removing" → freed collider
            // + stale narrow-phase contact pair → narrow_phase.rs crash on the next step. The
            // clamp uses Rapier's add-delta velocity API only — it never frees/rebakes a
            // collider or touches the contact graph — so it is safe for rope sub-levels.
            if (o instanceof ServerSubLevel serverSubLevel) {
                ElasticPairReaction.clampOneSubLevel(serverSubLevel, maxLin, maxAng, lin, ang, linFix, angFix);
            }
        }
    }

    // Per-substep entry point (SubLevelPhysicsSystemMixin): runs after every Rapier3D.step()
    // and BEFORE container.processSubLevelRemovals(). A rope-joint / contact blow-up diverges
    // inside one substep's physicsTick(); the once-per-tick prePhysicsTicks clamp ran before
    // the whole substep loop and never gets another chance before the crash. Clamping every
    // substep catches the divergence on the first substep it appears — while velocity is still
    // finite-but-huge — and caps it before the next substep compounds it to Inf/NaN and Sable
    // frees the collider mid-loop.
    public static void clampRunawaySubLevels(Iterable<? extends ServerSubLevel> subLevels) {
        if (subLevels == null || !((Boolean)TrueImpactConfig.ENABLE_RUNAWAY_VELOCITY_CLAMP.get()).booleanValue()) {
            return;
        }
        double maxLin = (Double)TrueImpactConfig.MAX_SUBLEVEL_LINEAR_SPEED.get();
        double maxAng = (Double)TrueImpactConfig.MAX_SUBLEVEL_ANGULAR_SPEED.get();
        Vector3d lin = new Vector3d();
        Vector3d ang = new Vector3d();
        Vector3d linFix = new Vector3d();
        Vector3d angFix = new Vector3d();
        for (ServerSubLevel serverSubLevel : subLevels) {
            // fork_26: clamp rope-connected sub-levels too — see the other overload. Exempting
            // them let a hard rope smash fling them to extreme Y → Sable removal → crash.
            ElasticPairReaction.clampOneSubLevel(serverSubLevel, maxLin, maxAng, lin, ang, linFix, angFix);
        }
    }

    // NaN/Inf velocity is unrepairable: Sable/Rapier expose only an add-delta velocity API
    // (NaN + delta = NaN), no hard-set. So we only cap finite-but-extreme blow-ups. Correctness
    // depends entirely on running this often enough (every substep) to catch the divergence
    // before it leaves the finite range. The scratch vectors are reused across the loop.
    private static void clampOneSubLevel(ServerSubLevel serverSubLevel, double maxLin, double maxAng,
                                         Vector3d lin, Vector3d ang, Vector3d linFix, Vector3d angFix) {
        if (serverSubLevel == null || serverSubLevel.isRemoved()) {
            return;
        }
        RigidBodyHandle handle = RigidBodyHandle.of(serverSubLevel);
        if (handle == null || !handle.isValid()) {
            return;
        }
        try {
            handle.getLinearVelocity(lin);
            handle.getAngularVelocity(ang);
        }
        catch (RuntimeException | LinkageError e) {
            return;
        }
        linFix.set(0.0, 0.0, 0.0);
        angFix.set(0.0, 0.0, 0.0);
        boolean correct = false;
        if (ElasticPairReaction.isFinite(lin)) {
            double speed = lin.length();
            if (speed > maxLin && speed > 1.0E-6) {
                linFix.set((Vector3dc)lin).mul(maxLin / speed - 1.0);
                correct = true;
            }
        }
        if (ElasticPairReaction.isFinite(ang)) {
            double spin = ang.length();
            if (spin > maxAng && spin > 1.0E-6) {
                angFix.set((Vector3dc)ang).mul(maxAng / spin - 1.0);
                correct = true;
            }
        }
        if (!correct || !ElasticPairReaction.isFinite(linFix) || !ElasticPairReaction.isFinite(angFix)) {
            return;
        }
        try {
            handle.addLinearAndAngularVelocity((Vector3dc)linFix, (Vector3dc)angFix);
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                LogManager.getLogger().warn("[TrueImpact] Clamped runaway sub-level velocity: lin={}, ang={}", lin, ang);
            }
        }
        catch (RuntimeException | LinkageError e) {
            if (((Boolean)TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue()) {
                LogManager.getLogger().warn("[TrueImpact] Failed to clamp runaway sub-level velocity.", e);
            }
        }
    }

    // fork_7: predictive substep terrain sweep — bypasses Rapier's callback/contact-graph path
    // entirely. Called from SubLevelPhysicsSystemMixin between substeps. For each non-rope
    // sub-level moving above MIN_EFFECT_VELOCITY, scans world blocks inside its world AABB
    // (loaded chunks only — 亡夐而堠 guard). Non-air overlaps are penetrations; if
    // kineticEnergy/strength yield is met, schedule destroyBlock to next safe tick. This gives
    // substep-granular terrain destruction (much finer than the post-step cluster path) while
    // touching ZERO of Rapier's narrow_phase state, so constraint-connected sub-levels (ropes,
    // springs, joints) stay crash-safe. Doesn't fix structure-vs-structure block destruction —
    // that still needs the HARDNESS_CALLBACK path (legacy mode only).
    public static void predictiveSubLevelTerrainSweep(Iterable<? extends ServerSubLevel> subLevels) {
        if (subLevels == null
            || !((Boolean) TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_BLOCK_BREAKING.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get()).booleanValue()) {
            return;
        }
        // fork_9: raise sweep speed floor to 5.0 — gentle pushes / tap-down placements should
        // not trigger crater carving even on compactable terrain. Per-contact path (which has
        // soil compaction + finer ladder) covers gentle interactions.
        double minSpeed = Math.max((Double) TrueImpactConfig.MIN_EFFECT_VELOCITY.get(), 5.0);
        // fork_9: sweep iterates the AABB volume → a single yield gate is hit many times per
        // impact. Apply a 1.4× multiplier so the effective sweep threshold sits between
        // BREAK_YIELD_THRESHOLD (9.5) and HEAVY_BREAK_YIELD_THRESHOLD (16) — light/medium
        // overlaps no longer chew up a crater, only legitimately energetic impacts do.
        double breakYield = (Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get() * 1.4;
        int totalBudget = 200; // hard cap blocks-per-call across all sub-levels (perf safety)
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos bodyCheckPos = new BlockPos.MutableBlockPos();
        Vector3d bodyOffset = new Vector3d();
        for (ServerSubLevel sl : subLevels) {
            if (totalBudget <= 0) break;
            if (sl == null || RopeBindingRegistry.isRopeSubLevel(sl)) continue;
            Vector3d vel = ElasticPairReaction.latestLinearVelocity(sl);
            double speed = vel.length();
            if (!Double.isFinite(speed) || speed < minSpeed) continue;
            AABB bounds = ElasticPairReaction.worldBounds(sl);
            if (bounds == null) continue;
            ServerLevel level = ElasticPairReaction.level(sl);
            if (level == null) continue;
            // fork_8: use TERRAIN-specific mass params (0.7 exponent, cap 35) instead of the
            // general MASS_EXPONENT=1.0 / MAX_EFFECTIVE_MASS=100. The general params are tuned
            // for the per-block HARDNESS_CALLBACK ladder (where many high-yield decisions still
            // need to feel destructive); for the AABB-scanning sweep, the gentler terrain params
            // prevent moderate-mass structures from over-saturating effective mass and triggering
            // the BREAK_YIELD_THRESHOLD on every block they sit on at trivial speeds.
            double mass = Math.max(ElasticPairReaction.mass(sl), 1.0);
            double effectiveMass = Math.min((Double) TrueImpactConfig.TERRAIN_IMPACT_MAX_EFFECTIVE_MASS.get(),
                Math.pow(mass, (Double) TrueImpactConfig.TERRAIN_IMPACT_MASS_EXPONENT.get()));
            double velocityEnergy = Math.pow(speed, (Double) TrueImpactConfig.IMPACT_VELOCITY_EXPONENT.get());
            double kineticEnergy = 0.5 * effectiveMass * velocityEnergy
                * (Double) TrueImpactConfig.DAMAGE_SCALE.get()
                * (Double) TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
            // fork_8 block-aware: world AABB is rectangular, but the structure inside it usually
            // isn't. Use the AABB CENTER as the world reference; for each world block in the
            // AABB, compute its body-frame offset (block-center minus AABB-center), then look up
            // the sub-level block at that body-frame position. If the sub-level has no block
            // there (air pocket inside an irregular shape), skip — no real overlap, no
            // destruction. This gives craters that match the structure's footprint instead of
            // its bounding rectangle. Approximation: ignores rotation (rotated structures get
            // slightly off-axis block lookups, which is acceptable for landing/sliding cases).
            double centerX = (bounds.minX + bounds.maxX) * 0.5;
            double centerY = (bounds.minY + bounds.maxY) * 0.5;
            double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            int minBX = (int) Math.floor(bounds.minX);
            int minBY = (int) Math.floor(bounds.minY);
            int minBZ = (int) Math.floor(bounds.minZ);
            int maxBX = (int) Math.floor(bounds.maxX);
            int maxBY = (int) Math.floor(bounds.maxY);
            int maxBZ = (int) Math.floor(bounds.maxZ);
            int perSubLevelBudget = 50;
            outer:
            for (int by = minBY; by <= maxBY; by++) {
                for (int bx = minBX; bx <= maxBX; bx++) {
                    for (int bz = minBZ; bz <= maxBZ; bz++) {
                        if (perSubLevelBudget <= 0 || totalBudget <= 0) break outer;
                        cursor.set(bx, by, bz);
                        // 亡夐而堠 guard: skip if chunk not loaded — never force generation.
                        if (!level.hasChunkAt(cursor)) continue;
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir()) continue;
                        float destroySpeed = state.getDestroySpeed((BlockGetter) level, cursor);
                        if (destroySpeed < 0.0f) continue; // bedrock / barrier / etc.
                        // fork_8 block-aware: check the sub-level actually has a block at the
                        // corresponding body-frame position. localState adds plotCenter and
                        // queries the embedded sub-level block. AIR → air pocket → skip.
                        bodyOffset.set(bx + 0.5 - centerX, by + 0.5 - centerY, bz + 0.5 - centerZ);
                        BlockState slBlock = ElasticPairReaction.localState(sl, bodyOffset, bodyCheckPos);
                        if (slBlock.isAir()) continue;
                        double structuralIntegrity = MaterialImpactProperties.baseStrength((BlockGetter) level, cursor, state);
                        double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, structuralIntegrity), 1.0);
                        double yieldRatio = kineticEnergy / materialStrength;
                        if (yieldRatio > breakYield) {
                            final ServerLevel destroyLevel = level;
                            final BlockPos destroyPos = cursor.immutable();
                            level.getServer().execute(() -> destroyLevel.destroyBlock(destroyPos, PhysicsBreakPolicy.shouldDrop(destroyLevel.getBlockState(destroyPos), true)));
                            perSubLevelBudget--;
                            totalBudget--;
                        }
                    }
                }
            }
        }
    }

    // fork_17: convert a WORLD position → the sub-level's plot-local block coordinate, using the
    // inverse of SubLevel.globalBoundsTransform (plot-local→world). Rotation-correct. Returns
    // null if the matrix is unavailable. The result is plot-LOCAL — callers add plotCenter (or
    // pass it to findNearestNonAirSubLevelState, which does that) to reach the embedded block.
    // beta.14 — package-private for ClippingDamageScanner. Behavior unchanged.
    static Vector3d worldToPlotLocal(Object subLevel, double wx, double wy, double wz) {
        if (GLOBAL_BOUNDS_TRANSFORM_FIELD == null || subLevel == null) {
            return null;
        }
        try {
            Object m = GLOBAL_BOUNDS_TRANSFORM_FIELD.get(subLevel);
            if (!(m instanceof org.joml.Matrix4d mat)) {
                return null;
            }
            org.joml.Matrix4d inv = new org.joml.Matrix4d(mat).invert();
            Vector3d out = new Vector3d(wx, wy, wz);
            inv.transformPosition(out);
            if (!Double.isFinite(out.x) || !Double.isFinite(out.y) || !Double.isFinite(out.z)) {
                return null;
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // fork_22: the structure's rotation quaternion (Pose3d.orientation()), copied.
    private static org.joml.Quaterniond poseOrientation(Object subLevel) {
        if (LOGICAL_POSE_METHOD == null || ORIENTATION_METHOD == null || subLevel == null) {
            return null;
        }
        try {
            Object pose = LOGICAL_POSE_METHOD.invoke(subLevel);
            if (pose == null) {
                return null;
            }
            Object q = ORIENTATION_METHOD.invoke(pose);
            if (q instanceof org.joml.Quaterniondc qd) {
                return new org.joml.Quaterniond(qd);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // fork_7: read SubLevel.boundingBox() (world-space AABB) via cached reflection.
    // beta.14 — package-private for ClippingDamageScanner. Behavior unchanged.
    static AABB worldBounds(Object subLevel) {
        if (BOUNDING_BOX_METHOD == null || subLevel == null) return null;
        try {
            Object box = BOUNDING_BOX_METHOD.invoke(subLevel);
            if (box == null) return null;
            return new AABB(
                ElasticPairReaction.number(box, "minX"), ElasticPairReaction.number(box, "minY"), ElasticPairReaction.number(box, "minZ"),
                ElasticPairReaction.number(box, "maxX"), ElasticPairReaction.number(box, "maxY"), ElasticPairReaction.number(box, "maxZ")
            );
        } catch (Exception e) {
            return null;
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
                    level.destroyBlock(pos, PhysicsBreakPolicy.shouldDrop(state, true));
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
                        pos.set(x + center.getX(), y + center.getY(), z + center.getZ());
                        // 亡夐而堠 guard: plot-embedded coords are tens of millions out. If the
                        // chunk isn't already loaded, NEVER force generation — `getBlockState`
                        // would synchronously generate a chunk ~40M blocks from spawn, hitting
                        // vanilla Aquifer integer overflow → "Exception generating new chunk"
                        // crash + GB-scale junk region files.
                        if (!level.hasChunkAt(pos)) continue;
                        if (!(PhysicsBlockPropertyHelper.getRestitution((BlockState)level.getBlockState((BlockPos)pos)) >= (Double)TrueImpactConfig.BOUNCE_RESPONSE_THRESHOLD.get())) continue;
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
        // beta.6: rope-sub-level impulse exemption REMOVED — pair reaction is config-gated
        // (default off) and the anchor-block protection at the destruction sites is what
        // actually keeps things safe. Rope structures get impulses if pair reaction is on.
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

    // 1.1.5 — extract runtime id from an Object sub-level via cast; -1 on failure.
    static int runtimeIdOf(Object subLevel) {
        try {
            if (subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl) {
                return ssl.getRuntimeId();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    // beta.14 — package-private for ClippingDamageScanner. Behavior unchanged.
    static ServerLevel level(Object subLevel) {
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

    // beta.14 — package-private for ClippingDamageScanner. Behavior unchanged.
    static BlockPos plotCenter(Object subLevel) {
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
        // 亡夐而堠 guard: see isElasticSubLevel. plotCenter is at plot-embedded coords; never
        // force chunkgen here. Return AIR if the embedded chunk isn't already loaded.
        if (!level.hasChunkAt(blockPos)) {
            return Blocks.AIR.defaultBlockState();
        }
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

    // fork_18: back to config-gated (= OFF in normal play). fork_14-17 hard-coded this true for
    // diagnostics, but per-contact is hot — a forced WARN per contact per tick is thousands of
    // synchronous log writes/sec, which is itself a heavy sustained load (prime suspect for the
    // fork_17 "destruction degrades to nothing over minutes" report). Normal builds must NOT
    // force this on. To diagnose again, set enableDebugImpactLogging=true with presetMode=custom
    // (presetMode=auto resets the flag — the documented preset-override gotcha).
    private static boolean perContactDbg() {
        try {
            return ((Boolean) TrueImpactConfig.ENABLE_DEBUG_IMPACT_LOGGING.get()).booleanValue();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    // fork_13: a per-contact hit threw — log it (if debug on) but never let it abort apply().
    private static void logPerContactError(Throwable e) {
        if (ElasticPairReaction.perContactDbg()) {
            LogManager.getLogger().warn("[TrueImpact] per-contact hit threw (suppressed; apply loop continues)", e);
        }
    }

    // fork_6: Java-side per-contact terrain-hit decision — port of the HARDNESS_CALLBACK ladder
    // onto the apply() collision loop. Restores the responsive frontline that the
    // RapierVoxelColliderBakeryMixin removal (the rope-crash fix) gave up. Fires only for
    // sub-level vs world contacts (one of slA/slB is null); pair contacts stay on the cluster
    // path. Schema: terrain world position is on the OPPOSITE side from the sub-level — when
    // slA is the sub-level, localB (slots 12..14) is the world hit point; when slB is the
    // sub-level, localA (slots 9..11) is. Decisions match HardnessFragileCallback's ladder
    // (propagation / heavy break / break / crack). Defers destroyBlock to next tick.
    private static void applyPerContactTerrainHit(int sceneId, double[] collisions, int start,
                                                  Object subLevel, boolean slAIsSubLevel) {
        if (subLevel == null
            || !((Boolean) TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_BLOCK_BREAKING.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.MOVING_STRUCTURES_BREAK_BLOCKS.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_WORLD_DESTRUCTION.get()).booleanValue()) {
            return;
        }
        int off = slAIsSubLevel ? 12 : 9;
        double wx = collisions[start + off];
        double wy = collisions[start + off + 1];
        double wz = collisions[start + off + 2];
        if (!Double.isFinite(wx) || !Double.isFinite(wy) || !Double.isFinite(wz)) {
            return;
        }
        ServerLevel level = ElasticPairReaction.level(subLevel);
        if (level == null) {
            return;
        }
        Vector3d vel = ElasticPairReaction.latestLinearVelocity(subLevel);
        double speed = vel.length();
        if (!Double.isFinite(speed) || speed < (Double) TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
            return;
        }
        // fork_12: locate the real hit block via the existing findNearestNonAirState helper
        // (3×3×3 neighborhood scan). The contact (wx,wy,wz) lies on the block SURFACE, so a
        // plain floor() lands on the AIR block just outside it — the neighborhood scan absorbs
        // that off-by-one (and minor contact misalignment) without needing a normal-direction
        // guess. Replaces fork_10's hand-rolled normal-probe.
        BlockPos roughPos = BlockPos.containing(wx, wy, wz);
        // 亡夐而堠 guard: never force chunk generation.
        if (!level.hasChunkAt(roughPos)) {
            return;
        }
        BlockPos.MutableBlockPos hitPos = new BlockPos.MutableBlockPos();
        BlockState state = ElasticPairReaction.findNearestNonAirState(level, new Vector3d(wx, wy, wz), null, hitPos);
        if (state.isAir()) {
            if (ElasticPairReaction.perContactDbg()) {
                LogManager.getLogger().warn("[TrueImpact] perContact WORLD: no block near contact ({},{},{}) speed={}",
                    String.format("%.1f", wx), String.format("%.1f", wy), String.format("%.1f", wz),
                    String.format("%.2f", speed));
            }
            return;
        }
        BlockPos pos = hitPos.immutable();
        float destroySpeed = state.getDestroySpeed((BlockGetter) level, pos);
        if (destroySpeed < 0.0f) {
            return;
        }
        // Soil compaction: light impacts on grass/podzol/mycelium/farmland convert to dirt.
        if (((Boolean) TrueImpactConfig.ENABLE_SOIL_COMPACTION.get()).booleanValue()
            && COMPACTABLE_SOIL.contains(state.getBlock())
            && speed >= (Double) TrueImpactConfig.SOIL_COMPACTION_MIN_VELOCITY.get()
            && speed < (Double) TrueImpactConfig.SOIL_COMPACTION_MAX_VELOCITY.get()) {
            double soilChance = ((Double) TrueImpactConfig.SOIL_COMPACTION_PER_CONTACT_CHANCE.get()).doubleValue();
            if (soilChance >= 1.0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < soilChance) {
                final ServerLevel compactLevel = level;
                final BlockPos compactPos = pos;
                ImpactBreakQueue.enqueue(() -> compactLevel.setBlock(compactPos, Blocks.DIRT.defaultBlockState(), 3));
            }
            return;
        }
        // Block transforms: user-defined block → block conversions (checked after soil compaction).
        if (((Boolean) TrueImpactConfig.ENABLE_BLOCK_TRANSFORMS.get()).booleanValue()
            && speed >= (Double) TrueImpactConfig.BLOCK_TRANSFORM_MIN_VELOCITY.get()
            && speed < (Double) TrueImpactConfig.BLOCK_TRANSFORM_MAX_VELOCITY.get()) {
            BlockState transformed = BlockTransformRegistry.tryTransform(state);
            if (transformed != null) {
                double chance = ((Double) TrueImpactConfig.BLOCK_TRANSFORM_PER_CONTACT_CHANCE.get()).doubleValue();
                if (chance >= 1.0 || java.util.concurrent.ThreadLocalRandom.current().nextDouble() < chance) {
                    final ServerLevel tl = level;
                    final BlockPos tp = pos;
                    final BlockState ts = transformed;
                    ImpactBreakQueue.enqueue(() -> tl.setBlock(tp, ts, 3));
                }
                return;
            }
        }
        double structuralIntegrity = MaterialImpactProperties.baseStrength((BlockGetter) level, pos, state);
        double materialStrength = Math.max(MaterialImpactProperties.displayStrength(state, structuralIntegrity), 1.0);
        double materialToughness = Math.max(MaterialImpactProperties.displayToughness(state, structuralIntegrity), materialStrength);
        double crackResistance = Math.sqrt(materialStrength * materialToughness);
        double mass = Math.max(ElasticPairReaction.mass(subLevel), 1.0);
        double effectiveMass = Math.min((Double) TrueImpactConfig.MAX_EFFECTIVE_MASS.get(),
            Math.pow(mass, (Double) TrueImpactConfig.MASS_EXPONENT.get()));
        double velocityEnergy = Math.pow(speed, (Double) TrueImpactConfig.IMPACT_VELOCITY_EXPONENT.get());
        double verticalAngleProxy = Math.max(
            ((Double) TrueImpactConfig.IMPACT_ANGLE_FLOOR.get()).doubleValue(),
            speed > 1e-6 ? Math.abs(vel.y) / speed : 1.0);
        double kineticEnergy = 0.5 * effectiveMass * velocityEnergy
            * verticalAngleProxy
            * (Double) TrueImpactConfig.DAMAGE_SCALE.get()
            * (Double) TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
        // 1.1.5: contact-count normalization. Same as 1.1.2 HardnessFragileCallback fix.
        // The applyPerContactTerrainHit path was previously unnormalized — each tread contact
        // got the full sub-level KE attributed, so 30 treads × full-KE = 30 destroyBlocks
        // per tick on grass = trenches. Now we divide by smoothed contact count^exponent.
        double kineticEnergyPreNorm = kineticEnergy;
        double contactDivisor = 1.0;
        try {
            int sslId = ElasticPairReaction.runtimeIdOf(subLevel);
            if (sslId != -1) {
                contactDivisor = ContactPressureTracker.recordAndGetDivisor(
                    sslId, level.getGameTime(),
                    ((Double) TrueImpactConfig.CONTACT_PRESSURE_EXPONENT.get()).doubleValue());
                kineticEnergy /= contactDivisor;
            }
        } catch (Throwable ignored) {
            // Normalization must never disturb the destruction path.
        }
        double yieldRatio = kineticEnergy / materialStrength;
        double crackRatio = kineticEnergy / Math.max(crackResistance, 1.0);
        // 1.1.5 diag: per-(pos, second) rate-limited.
        TIDiag.terrainContact(hitPos, 0, 0, kineticEnergyPreNorm, kineticEnergy, contactDivisor);
        if (ElasticPairReaction.perContactDbg()) {
            LogManager.getLogger().warn("[TrueImpact] perContact WORLD: block={} speed={} KE={} yield={} (brkThr={}) crack={} (crackThr={})",
                state.getBlock(), String.format("%.2f", speed), String.format("%.1f", kineticEnergy),
                String.format("%.2f", yieldRatio), TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(),
                String.format("%.2f", crackRatio), TrueImpactConfig.CRACK_YIELD_THRESHOLD.get());
        }
        final ServerLevel destroyLevel = level;
        final BlockPos destroyPos = pos;
        // fork_15: speed gates removed from the per-contact ladder. The COM linear speed we use
        // here is NOT the per-contact impact velocity the old HARDNESS_CALLBACK got from Sable
        // (which included rotation), so MIN_PROPAGATION/BREAK_VELOCITY were filtering out
        // legitimate high-yield hits (e.g. yield 14.2 at COM-speed 10.2 only cracked). yieldRatio
        // already encodes velocity² via velocityEnergy — the separate speed gate was redundant
        // double-gating. Pure yield ladder now.
        if (yieldRatio >= (Double) TrueImpactConfig.PROPAGATION_YIELD_THRESHOLD.get()) {
            final net.minecraft.world.level.block.Block blockForPropagation = state.getBlock();
            final double propagEnergy = kineticEnergy * (Double) TrueImpactConfig.PROPAGATION_ENERGY_SCALE.get();
            final boolean doPropagation = ((Boolean) TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()).booleanValue();
            level.getServer().execute(() -> {
                destroyLevel.destroyBlock(destroyPos, false);
                if (doPropagation) {
                    CrackPropagationUtils.propagateCracks(destroyLevel, destroyPos, blockForPropagation, propagEnergy);
                }
            });
            return;
        }
        if (yieldRatio > (Double) TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get()) {
            level.getServer().execute(() -> destroyLevel.destroyBlock(destroyPos, PhysicsBreakPolicy.shouldDrop(destroyLevel.getBlockState(destroyPos), true)));
            return;
        }
        if (yieldRatio > (Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()) {
            double overstress = Math.max(0.0, kineticEnergy - materialStrength);
            BlockDamageAccumulator.apply(level, pos,
                MaterialImpactProperties.fatigueDamage(state, overstress * 0.65),
                materialToughness * (Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(),
                sceneId + pos.hashCode());
            return;
        }
        if (((Boolean) TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue()
            && crackRatio > (Double) TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
            double crackOverStress = Math.max(0.0, kineticEnergy - crackResistance);
            BlockDamageAccumulator.apply(level, pos,
                MaterialImpactProperties.fatigueDamage(state, crackOverStress * 0.4),
                crackResistance * (Double) TrueImpactConfig.CRACK_YIELD_THRESHOLD.get(),
                sceneId + pos.hashCode());
        }
    }

    // fork_21-diag: DECISIVE probe. For the first structure-side contact, dump every reference
    // coordinate AND try 5 candidate transforms — for each, look up the world block and report
    // what it found. Whichever candidate finds a non-air structure block IS the correct
    // body-frame → embedded transform. Fires once, ALWAYS. ~20 lines, no spam.
    private static volatile boolean POSE_PROBE_DONE = false;

    private static void probePoseTransform(Object subLevel, ServerLevel level, double slx, double sly, double slz) {
        if (POSE_PROBE_DONE || subLevel == null || level == null) {
            return;
        }
        POSE_PROBE_DONE = true;
        org.apache.logging.log4j.Logger log = LogManager.getLogger();
        try {
            log.warn("[TrueImpact] ===== POSE PROBE (fork_21) =====");
            Vector3d rp = ElasticPairReaction.rotationPoint(subLevel);
            BlockPos pc = ElasticPairReaction.plotCenter(subLevel);
            log.warn("[TrueImpact] PROBE: localA contact = ({}, {}, {})", slx, sly, slz);
            log.warn("[TrueImpact] PROBE: rotationPoint = {}", rp);
            log.warn("[TrueImpact] PROBE: plotCenter = {}", pc);
            org.joml.Matrix4d mat = null;
            if (GLOBAL_BOUNDS_TRANSFORM_FIELD != null) {
                Object m = GLOBAL_BOUNDS_TRANSFORM_FIELD.get(subLevel);
                if (m instanceof org.joml.Matrix4d gm) {
                    mat = new org.joml.Matrix4d(gm);
                    log.warn("[TrueImpact] PROBE: globalBoundsTransform =\n{}", mat);
                }
            }
            Object pose = LOGICAL_POSE_METHOD != null ? LOGICAL_POSE_METHOD.invoke(subLevel) : null;
            if (pose != null) {
                try {
                    Object ori = pose.getClass().getMethod("orientation").invoke(pose);
                    Object posn = pose.getClass().getMethod("position").invoke(pose);
                    log.warn("[TrueImpact] PROBE: pose.orientation = {}  pose.position = {}", ori, posn);
                } catch (Throwable t) { log.warn("[TrueImpact] PROBE: pose getters failed: {}", t.toString()); }
            }
            // Candidate embedded positions — for each, look up the block.
            java.util.Map<String, Vector3d> candidates = new java.util.LinkedHashMap<>();
            candidates.put("C1 localA+plotCenter", pc == null ? null
                : new Vector3d(slx + pc.getX(), sly + pc.getY(), slz + pc.getZ()));
            candidates.put("C2 localA+rotationPoint", rp == null ? null
                : new Vector3d(slx + rp.x, sly + rp.y, slz + rp.z));
            if (mat != null) {
                Vector3d c3 = new Vector3d(slx, sly, slz);
                mat.transformPosition(c3);
                candidates.put("C3 mat*localA", c3);
                Vector3d c4 = new Vector3d(slx, sly, slz);
                new org.joml.Matrix4d(mat).invert().transformPosition(c4);
                candidates.put("C4 inv(mat)*localA", c4);
                if (rp != null) {
                    Vector3d c5 = new Vector3d(slx + rp.x, sly + rp.y, slz + rp.z);
                    mat.transformPosition(c5);
                    candidates.put("C5 mat*(localA+rp)", c5);
                }
            }
            for (java.util.Map.Entry<String, Vector3d> e : candidates.entrySet()) {
                Vector3d v = e.getValue();
                if (v == null) { log.warn("[TrueImpact] PROBE: {} = <null>", e.getKey()); continue; }
                BlockPos bp = BlockPos.containing(v.x, v.y, v.z);
                String found = level.hasChunkAt(bp)
                    ? level.getBlockState(bp).getBlock().toString() : "<chunk not loaded>";
                log.warn("[TrueImpact] PROBE: {} -> ({},{},{}) block={}",
                    e.getKey(), bp.getX(), bp.getY(), bp.getZ(), found);
            }
            log.warn("[TrueImpact] ===== POSE PROBE END =====");
        } catch (Throwable t) {
            log.warn("[TrueImpact] POSE PROBE failed", t);
        }
    }

    // fork_9: structure-side per-contact destruction. HARDNESS_CALLBACK historically evaluated
    // BOTH sides of every block-hit; fork_6 only ported the world-terrain side, so structure
    // blocks survived ramming walls. This sibling routine reads the sub-level block at the
    // body-frame contact position via localState, applies the same yield ladder, and schedules
    // destroyBlock at the embedded coord. SAFE: rope/constraint sublevels are gated upstream in
    // apply(), so destroyBlock → handleBlockChange → rebake here cannot crash narrow_phase.
    private static void applyPerContactSubLevelSideHit(int sceneId, double[] collisions, int start,
                                                       Object subLevel, boolean slAIsSubLevel) {
        // fork_24: DISABLED for the 1.1.0 baseline. The structure-side block lookup needs the
        // Rapier body-frame contact → embedded-block transform, which is unsolved: forks 10-23
        // could not derive it (the structure is solid, so every candidate transform hits *some*
        // block — "first hit wins" cannot tell a correct lookup from a wrong one, and both
        // rotation directions produced mislocated "隔牛打山" damage). A clean known limitation
        // ("structures take no impact self-damage") ships better than visibly-wrong damage.
        // 固若金汤 / structure self-damage is a focused 1.1.x task — needs Sable-source study of
        // how Sable itself maps a contact to a sub-level block, or a round-trip-validating probe.
        if (true) {
            return;
        }
        if (subLevel == null
            || !((Boolean) TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_BLOCK_BREAKING.get()).booleanValue()
            || !((Boolean) TrueImpactConfig.ENABLE_PHYSICAL_DESTRUCTION.get()).booleanValue()) {
            return;
        }
        // Sub-level-side body-frame contact is on the SAME side as the sub-level (opposite of
        // the terrain side that applyPerContactTerrainHit uses).
        int slOff = slAIsSubLevel ? 9 : 12;
        double slx = collisions[start + slOff];
        double sly = collisions[start + slOff + 1];
        double slz = collisions[start + slOff + 2];
        if (!Double.isFinite(slx) || !Double.isFinite(sly) || !Double.isFinite(slz)) {
            return;
        }
        ServerLevel level = ElasticPairReaction.level(subLevel);
        if (level == null) {
            return;
        }
        Vector3d vel = ElasticPairReaction.latestLinearVelocity(subLevel);
        double speed = vel.length();
        if (!Double.isFinite(speed) || speed < (Double) TrueImpactConfig.MIN_EFFECT_VELOCITY.get()) {
            return;
        }
        // fork_22: 固若金汤 fix. localA (slx,sly,slz) is the contact in the structure's ROTATED
        // body frame. The fork_21 probe proved +plotCenter/+rotationPoint lands in the right
        // embedded REGION but on air — off by the rotation. Un-rotate localA with the pose
        // orientation quaternion. Exact reference/direction unknown → try 4 candidates
        // (inv-rotate vs rotate) × (plotCenter- vs rotationPoint-relative); the 3×3×3 scan in
        // findNearestNonAirSubLevelState absorbs surface off-by-one. First candidate landing on
        // a real block wins; winner logged once. Each candidate is chunk-loaded-checked first
        // (亡夐而堠 guard) so a wildly-off candidate can never force far-out worldgen.
        BlockPos.MutableBlockPos slCheckPos = new BlockPos.MutableBlockPos();
        BlockPos slPlotCenter = ElasticPairReaction.plotCenter(subLevel);
        if (slPlotCenter == null) {
            return;
        }
        org.joml.Quaterniond slOri = ElasticPairReaction.poseOrientation(subLevel);
        Vector3d slRotPoint = ElasticPairReaction.rotationPoint(subLevel);
        Vector3d rpMinusPc = slRotPoint == null ? new Vector3d()
            : new Vector3d(slRotPoint.x - slPlotCenter.getX(),
                           slRotPoint.y - slPlotCenter.getY(),
                           slRotPoint.z - slPlotCenter.getZ());
        java.util.List<Vector3d> slCandidates = new java.util.ArrayList<>();
        java.util.List<String> slCandNames = new java.util.ArrayList<>();
        if (slOri != null) {
            Vector3d invR = new org.joml.Quaterniond(slOri).conjugate().transform(new Vector3d(slx, sly, slz));
            Vector3d fwdR = new org.joml.Quaterniond(slOri).transform(new Vector3d(slx, sly, slz));
            // fork_23: rot@rp first. fork_22's winner invRot@rp produced point-MIRRORED damage
            // (bottom contact → top damage) — wrong rotation direction. The mirror-correct form
            // is the forward rotation on the same rotationPoint basis: q·localA + rotationPoint.
            // The structure is solid so several candidates hit *a* block; ordering decides which
            // wins, so the correct one must be tried first.
            slCandidates.add(new Vector3d(fwdR).add(rpMinusPc)); slCandNames.add("rot@rp");
            slCandidates.add(new Vector3d(invR).add(rpMinusPc)); slCandNames.add("invRot@rp");
            slCandidates.add(new Vector3d(fwdR));                slCandNames.add("rot@pc");
            slCandidates.add(new Vector3d(invR));                slCandNames.add("invRot@pc");
        }
        slCandidates.add(new Vector3d(slx, sly, slz));           slCandNames.add("raw");
        BlockState slState = null;
        for (int slCi = 0; slCi < slCandidates.size(); slCi++) {
            Vector3d cand = slCandidates.get(slCi);
            BlockPos rough = new BlockPos(
                slPlotCenter.getX() + (int) Math.floor(cand.x),
                slPlotCenter.getY() + (int) Math.floor(cand.y),
                slPlotCenter.getZ() + (int) Math.floor(cand.z));
            if (!level.hasChunkAt(rough)) {
                continue;
            }
            BlockState s = ElasticPairReaction.findNearestNonAirSubLevelState(subLevel, cand, null, slCheckPos);
            if (!s.isAir()) {
                slState = s;
                if (!SUBLEVEL_WINNER_LOGGED) {
                    SUBLEVEL_WINNER_LOGGED = true;
                    LogManager.getLogger().warn("[TrueImpact] SUBLEVEL lookup winner = {} (固若金汤 fix verified)", slCandNames.get(slCi));
                }
                break;
            }
        }
        if (slState == null) {
            return;
        }
        float slDestroySpeed = slState.getDestroySpeed((BlockGetter) level, slCheckPos);
        if (slDestroySpeed < 0.0f) {
            return;
        }
        double slStructInteg = MaterialImpactProperties.baseStrength((BlockGetter) level, slCheckPos, slState);
        double slStrength = Math.max(MaterialImpactProperties.displayStrength(slState, slStructInteg), 1.0);
        double slToughness = Math.max(MaterialImpactProperties.displayToughness(slState, slStructInteg), slStrength);
        double mass = Math.max(ElasticPairReaction.mass(subLevel), 1.0);
        double effectiveMass = Math.min((Double) TrueImpactConfig.MAX_EFFECTIVE_MASS.get(),
            Math.pow(mass, (Double) TrueImpactConfig.MASS_EXPONENT.get()));
        double velocityEnergy = Math.pow(speed, (Double) TrueImpactConfig.IMPACT_VELOCITY_EXPONENT.get());
        double kineticEnergy = 0.5 * effectiveMass * velocityEnergy
            * (Double) TrueImpactConfig.DAMAGE_SCALE.get()
            * (Double) TrueImpactConfig.GLOBAL_STRENGTH_SCALE.get();
        double slYield = kineticEnergy / slStrength;
        if (ElasticPairReaction.perContactDbg()) {
            double slCrackRes = Math.sqrt(slStrength * slToughness);
            LogManager.getLogger().warn("[TrueImpact] perContact SUBLEVEL: block={} speed={} KE={} yield={} (brkThr={}, heavyThr={}, minBrkVel={}) crack={} (crackThr={})",
                slState.getBlock(), String.format("%.2f", speed), String.format("%.1f", kineticEnergy),
                String.format("%.2f", slYield), TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(),
                TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get(), TrueImpactConfig.MIN_BREAK_VELOCITY.get(),
                String.format("%.2f", kineticEnergy / Math.max(slCrackRes, 1.0)),
                TrueImpactConfig.CRACK_YIELD_THRESHOLD.get());
        }
        // Heavy break: immediate destruction at embedded coord. fork_15: speed gate removed
        // (see applyPerContactTerrainHit) — pure yield ladder.
        if (slYield > (Double) TrueImpactConfig.HEAVY_BREAK_YIELD_THRESHOLD.get()) {
            final ServerLevel slDestroyLevel = level;
            final BlockPos slDestroyPos = slCheckPos.immutable();
            level.getServer().execute(() -> slDestroyLevel.destroyBlock(slDestroyPos, PhysicsBreakPolicy.shouldDrop(slDestroyLevel.getBlockState(slDestroyPos), true)));
            return;
        }
        // Break: fatigue accumulation (eventually breaks under repeated hits).
        if (slYield > (Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get()) {
            BlockPos slDestroyPos = slCheckPos.immutable();
            double slOverstress = Math.max(0.0, kineticEnergy - slStrength);
            BlockDamageAccumulator.apply(level, slDestroyPos,
                MaterialImpactProperties.fatigueDamage(slState, slOverstress * 0.65),
                slToughness * (Double) TrueImpactConfig.BREAK_YIELD_THRESHOLD.get(),
                sceneId + slDestroyPos.hashCode());
            return;
        }
        // fork_11: crack branch — I forgot this in fork_9. World-side has it; sub-level side was
        // silently missing it. Without this, medium impacts (speed 5–12, well below
        // MIN_BREAK_VELOCITY=12) did literally NOTHING to the structure even when their energy
        // easily passed CRACK_YIELD_THRESHOLD (1.15). Hence "结构碎和裂纹基本没有".
        double slCrackResistance = Math.sqrt(slStrength * slToughness);
        double slCrackRatio = kineticEnergy / Math.max(slCrackResistance, 1.0);
        if (((Boolean) TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue()
            && slCrackRatio > (Double) TrueImpactConfig.CRACK_YIELD_THRESHOLD.get()) {
            BlockPos slDestroyPos = slCheckPos.immutable();
            double slCrackOverStress = Math.max(0.0, kineticEnergy - slCrackResistance);
            BlockDamageAccumulator.apply(level, slDestroyPos,
                MaterialImpactProperties.fatigueDamage(slState, slCrackOverStress * 0.4),
                slCrackResistance * (Double) TrueImpactConfig.CRACK_YIELD_THRESHOLD.get(),
                sceneId + slDestroyPos.hashCode());
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
            // Grid scan suppressed for linear contact patterns (diagonal beam/rod):
            // if contacts are spread along one body-local axis but narrow in the perpendicular one,
            // scanning the bounding rectangle fills a wide area the structure never actually covered.
            boolean isLinear = Math.max(dx, dz) > 2.0 && Math.min(dx, dz) / Math.max(dx, dz) < 0.15;
            boolean bl = isFlat = this.points.size() > 3 && area > 1.5 && !isLinear;
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
            // fork_16 diagnostics: log the cluster's totalForce vs the gate so we can set the
            // threshold from data instead of guessing. The grid-scan footprint-fill (the
            // four-corners fix) lives PAST this gate — if a medium impact's totalForce is below
            // `threshold`, the whole method returns and the centre never fills.
            if (ElasticPairReaction.perContactDbg()) {
                LogManager.getLogger().warn("[TrueImpact] cluster TERRAIN: totalForce={} (gate={}, flatGate={}) isFlat={} points={} subLevelSpeed={}",
                    String.format("%.1f", this.totalForce), String.format("%.1f", threshold),
                    String.format("%.1f", threshold * 0.5), isFlat, this.points.size(),
                    String.format("%.2f", subLevelSpeed));
            }
            if (!isFlat && this.totalForce < threshold) {
                return;
            }
            if (isFlat && this.totalForce < threshold * 0.5) {
                return;
            }
            this.points.sort((a, b) -> Double.compare(b.force, a.force));
            int validContactCount = 0;
            for (PointData p : this.points) {
                if (p.force > 0.0 && Double.isFinite(p.force)) validContactCount++;
            }
            double terrainContactDivisor = Math.max(1.0, Math.pow(
                Math.max(1, validContactCount),
                ((Double)TrueImpactConfig.CONTACT_PRESSURE_EXPONENT.get()).doubleValue()));
            // Per-contact loop: each Rapier contact point independently targets the terrain-side world coord.
            // Static bodies (terrain) have body frame = world frame, so localB (slA = structure) is world space.
            double representativeEnergy = -1;
            Vector3d representativeNormal = this.points.get(0).normal;
            int diagContactIdx = 0;
            for (PointData p : this.points) {
                if (p.force <= 0.0 || !Double.isFinite(p.force)) continue;
                diagContactIdx++;
                Vector3d terrainWorldPoint = (p.slA != null) ? p.localB : p.localA;
                TerrainImpactSeed seed = ElasticPairReaction.buildTerrainImpactSeed(
                    this.subLevel, p.local, terrainWorldPoint, p.normal, p.force, explosions);
                if (seed != null) {
                    double normalizedEnergy = seed.energy() / terrainContactDivisor;
                    if (representativeEnergy < 0) representativeEnergy = normalizedEnergy;
                    TIDiag.terrainContact(seed.pos(), diagContactIdx, validContactCount,
                        seed.energy(), normalizedEnergy, terrainContactDivisor);
                    ElasticPairReaction.damageTerrain(seed.level(), seed.pos(), seed.normal(), normalizedEnergy);
                }
            }
            // Supplemental grid scan in body-local space.
            // Scan the contact extent in the structure's own coordinate frame, then transform
            // each sampled point to world space using a Y-axis rotation derived from contact
            // point correspondences. A 45°-rotated cube produces a diamond crater; a flat plate
            // aligned with axes produces a square one. No AABB bias in world space.
            if (representativeEnergy > 0 && isFlat) {
                // fork_19: 隔山打牛 guard. The grid scan below reconstructs the structure's
                // rotation+translation from contact-point correspondences — fragile when
                // contacts are poorly separated or single-cluster, which displaces the whole
                // footprint and craters terrain far from the structure. The structure's real
                // world AABB (boundingBox(), reliable post-step) is the ground truth: the
                // footprint MUST lie within it. Any grid cell that maps outside is a
                // reconstruction misfire → reject.
                AABB gridGuardAABB = ElasticPairReaction.worldBounds(this.subLevel);
                double contactY = 0.0; int contactCount = 0;
                Vector3d pLocalA = null, pWorldA = null, pLocalB = null, pWorldB = null;
                for (PointData p : this.points) {
                    if (p.force <= 0.0 || !Double.isFinite(p.force)) continue;
                    Vector3d tp = (p.slA != null) ? p.localB : p.localA;
                    if (!Double.isFinite(tp.x) || !Double.isFinite(tp.y) || !Double.isFinite(tp.z)) continue;
                    contactY += tp.y; contactCount++;
                    if (pLocalA == null) {
                        pLocalA = p.local; pWorldA = tp;
                    } else if (pLocalB == null) {
                        double dlx = p.local.x - pLocalA.x, dlz = p.local.z - pLocalA.z;
                        if (dlx * dlx + dlz * dlz > 0.25) { pLocalB = p.local; pWorldB = tp; }
                    }
                }
                if (contactCount == 0) return;
                contactY /= contactCount;
                // Derive Y-axis rotation: worldDiff = R * localDiff for two contact pairs.
                double cosTheta = 1.0, sinTheta = 0.0, transX, transZ;
                if (pLocalA != null && pLocalB != null) {
                    double dlx = pLocalB.x - pLocalA.x, dlz = pLocalB.z - pLocalA.z;
                    double dwx = pWorldB.x - pWorldA.x, dwz = pWorldB.z - pWorldA.z;
                    double len = Math.sqrt(dlx * dlx + dlz * dlz);
                    if (len > 0.01) {
                        dlx /= len; dlz /= len; dwx /= len; dwz /= len;
                        cosTheta = dlx * dwx + dlz * dwz;
                        sinTheta = dlx * dwz - dlz * dwx;
                    }
                }
                if (pLocalA != null) {
                    transX = pWorldA.x - (cosTheta * pLocalA.x - sinTheta * pLocalA.z);
                    transZ = pWorldA.z - (sinTheta * pLocalA.x + cosTheta * pLocalA.z);
                } else {
                    Vector3d rp = ElasticPairReaction.rotationPoint(this.subLevel);
                    if (rp == null) return;
                    transX = rp.x; transZ = rp.z;
                }
                // Scan body-local grid at 1-block resolution over the contact extent.
                int lxMin = (int)Math.floor(this.min.x);
                int lxMax = (int)Math.ceil(this.max.x);
                int lzMin = (int)Math.floor(this.min.z);
                int lzMax = (int)Math.ceil(this.max.z);
                int iy = (int)Math.floor(contactY - 0.5);
                int bodyArea = (lxMax - lxMin + 1) * (lzMax - lzMin + 1);
                if (bodyArea > 0 && bodyArea <= 4096) {
                    HashSet<BlockPos> seen = new HashSet<>();
                    ArrayList<BlockPos> validPositions = new ArrayList<>();
                    for (int lx = lxMin; lx <= lxMax; lx++) {
                        for (int lz = lzMin; lz <= lzMax; lz++) {
                            double wx = cosTheta * (lx + 0.5) - sinTheta * (lz + 0.5) + transX;
                            double wz = sinTheta * (lx + 0.5) + cosTheta * (lz + 0.5) + transZ;
                            if (gridGuardAABB != null
                                    && (wx < gridGuardAABB.minX - 1.0 || wx > gridGuardAABB.maxX + 1.0
                                     || wz < gridGuardAABB.minZ - 1.0 || wz > gridGuardAABB.maxZ + 1.0)) {
                                continue;
                            }
                            int bx = (int)Math.floor(wx);
                            int bz = (int)Math.floor(wz);
                            BlockPos gp = new BlockPos(bx, iy, bz);
                            BlockState gs = level.getBlockState(gp);
                            if (gs.isAir()) { gp = new BlockPos(bx, iy - 1, bz); gs = level.getBlockState(gp); }
                            if (gs.isAir()) { gp = new BlockPos(bx, iy + 1, bz); gs = level.getBlockState(gp); }
                            if (!gs.isAir() && !gs.is(Blocks.BEDROCK)
                                    && gs.getDestroySpeed((BlockGetter)level, gp) >= 0f) {
                                BlockPos imm = gp.immutable();
                                if (seen.add(imm)) {
                                    validPositions.add(imm);
                                }
                            }
                        }
                    }
                    if (!validPositions.isEmpty()) {
                        double gridDivisor = Math.max(1.0, Math.pow(validPositions.size(),
                            ((Double)TrueImpactConfig.CONTACT_PRESSURE_EXPONENT.get()).doubleValue()));
                        double gridEnergy = representativeEnergy / gridDivisor;
                        ArrayList<TerrainImpactSeed> gridSeeds = new ArrayList<>();
                        for (BlockPos gp : validPositions) {
                            gridSeeds.add(new TerrainImpactSeed(level, gp, representativeNormal, gridEnergy));
                        }
                        TIDiag.gridScan(gridSeeds.get(0).pos(), gridSeeds.size(),
                            representativeEnergy, gridEnergy, gridDivisor);
                        ElasticPairReaction.damageTerrain(level, gridSeeds,
                            Math.max((Integer)TrueImpactConfig.TERRAIN_IMPACT_MAX_BLOCKS.get(), gridSeeds.size()));
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
