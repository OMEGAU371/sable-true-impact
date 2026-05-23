/*
 *  dev.ryanhcode.sable.api.SubLevelAssemblyHelper
 *  dev.ryanhcode.sable.api.physics.PhysicsPipeline
 *  dev.ryanhcode.sable.api.physics.PhysicsPipelineBody
 *  dev.ryanhcode.sable.companion.math.BoundingBox3i
 *  dev.ryanhcode.sable.companion.math.BoundingBox3ic
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 *  org.joml.Vector3d
 *  org.joml.Vector3dc
 */
package com.example.sabletrueimpact.detach;

import com.example.sabletrueimpact.PhysicsStepGate;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

// 1.2.0 SD-port — THE core of the detach mechanism.
//
// Given an impact-point and impact info, carves a connected cluster of blocks out of the
// parent sub-level and reassembles it as a free-flying debris sub-level via Sable's own
// SubLevelAssemblyHelper.assembleBlocks. **This is the whole point of the SD port**: it
// sidesteps Sable's heatmap-driven SUB_LEVEL_SPLITTING entirely, so a rope-CONSTRAINED
// parent can have a piece "snap off" without ever triggering the split-of-a-constrained-body
// code path that crashes the narrow phase.
//
// Mid-step rule: assembleBlocks MUST NOT be called during a Rapier step. Caller must invoke
// this from a known post-step context (ImpactBreakQueue drain, ServerTickEvent.Post, etc.).
// We re-check PhysicsStepGate.isMidStep() defensively and bail null if violated.
//
// Failure modes — detach() returns null on:
//   - mid-step (defensive bail)
//   - cluster plan returned null (invalid seed / no parent / etc.)
//   - assembleBlocks threw or returned null (cluster snapshot is then rolled back)
// Never throws across the API boundary.
public final class SubLevelDetacher {

    private SubLevelDetacher() {
    }

    // Tuning knobs for one detach call. Caller assembles from TrueImpactConfig in step 5.
    public record DetachParams(
        int targetClusterSize,
        int clusterRadius,
        double hardnessSimilarity,
        double kickScale,         // how much of `approachSpeed` to convert into linear kick
        double maxKickSpeed,      // hard cap on the linear kick magnitude
        double scatterStrength,   // random Δv added to the kick direction (organic spread)
        double spinStrength,      // angular velocity magnitude (random direction)
        int maxActiveDebris       // cap passed to TIDetachRegistry.register
    ) {
    }

    // Returns the newly assembled debris sub-level on success, null on any failure.
    //
    // `kick(X|Y|Z)` is the impact-direction vector (e.g. tangential or normal velocity); we
    // normalize and scale it. `approachSpeed` drives kick magnitude via `kickScale`.
    public static ServerSubLevel detach(ServerLevel level, BlockPos seed,
                                        double kickX, double kickY, double kickZ,
                                        double approachSpeed,
                                        DetachParams params) {
        if (level == null || seed == null || params == null) {
            return null;
        }
        if (level.getServer() == null || !level.getServer().isRunning()) {
            return null;
        }
        // Defensive mid-step gate. Caller should have ensured we're post-step, but the cost
        // of a stale check here is one nullcheck — the cost of being wrong is the crash.
        if (PhysicsStepGate.isMidStep()) {
            return null;
        }

        // Step 1: plan the cluster.
        ClusterCarver.Plan plan;
        try {
            plan = ClusterCarver.plan(level, seed,
                params.targetClusterSize(), params.clusterRadius(),
                params.hardnessSimilarity(), level.getRandom());
        } catch (Throwable t) {
            return null;
        }
        if (plan == null || plan.blocks().isEmpty()) {
            return null;
        }
        List<BlockPos> cluster = plan.blocks();

        // Step 2: snapshot block states so we can roll back if assembleBlocks fails partway.
        // SubLevelAssemblyHelper internally sets the cluster blocks to AIR while transferring
        // them into the new sub-level. If it throws or returns null mid-transfer, the world
        // could be left with holes — we restore from snapshot in that case.
        ArrayList<BlockState> snapshot = new ArrayList<>(cluster.size());
        try {
            for (BlockPos bp : cluster) {
                snapshot.add(level.getBlockState(bp));
            }
        } catch (Throwable t) {
            return null;
        }

        // Step 3: compute the cluster's integer AABB (Sable wants BoundingBox3ic; max is
        // exclusive in this API → +1 on each upper bound).
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos bp : cluster) {
            if (bp.getX() < minX) minX = bp.getX();
            if (bp.getY() < minY) minY = bp.getY();
            if (bp.getZ() < minZ) minZ = bp.getZ();
            if (bp.getX() > maxX) maxX = bp.getX();
            if (bp.getY() > maxY) maxY = bp.getY();
            if (bp.getZ() > maxZ) maxZ = bp.getZ();
        }
        BoundingBox3ic bb = new BoundingBox3i(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);

        // Step 4: ask Sable to assemble the cluster into a new sub-level.
        ServerSubLevel debris;
        try {
            debris = SubLevelAssemblyHelper.assembleBlocks(level, seed, cluster, bb);
        } catch (Throwable t) {
            rollback(level, cluster, snapshot);
            return null;
        }
        if (debris == null) {
            rollback(level, cluster, snapshot);
            return null;
        }

        // Step 5: kick + spin the debris (capped magnitudes). This is what makes it look
        // "knocked off" rather than just appearing in place. Failures here are NOT fatal —
        // worst case the debris just sits still, which is fine.
        try {
            applyKick(level, debris, kickX, kickY, kickZ, approachSpeed, params);
        } catch (Throwable t) {
            // ignored — debris is alive, kick is cosmetic
        }

        // Step 6: register with TIDetachRegistry for lifetime management.
        try {
            TIDetachRegistry.register(level, debris, params.maxActiveDebris());
        } catch (Throwable t) {
            // ignored — debris is still alive in physics; we just lose the timed despawn
        }

        return debris;
    }

    // Restore the snapshot at every cluster position. Best-effort; a failed restore on one
    // block doesn't stop us trying the rest.
    private static void rollback(ServerLevel level, List<BlockPos> cluster, List<BlockState> snapshot) {
        for (int i = 0; i < cluster.size() && i < snapshot.size(); ++i) {
            try {
                BlockState s = snapshot.get(i);
                if (s != null) {
                    level.setBlock(cluster.get(i), s, 11);
                }
            } catch (Throwable t) {
                // continue with the rest
            }
        }
    }

    // Compute and apply linear + angular velocity. All NaN/Inf guarded; magnitude capped.
    private static void applyKick(ServerLevel level, ServerSubLevel debris,
                                  double kickX, double kickY, double kickZ,
                                  double approachSpeed, DetachParams params) {
        SubLevelPhysicsSystem sys = SubLevelPhysicsSystem.get((Level) level);
        if (sys == null) return;
        PhysicsPipeline pipeline = sys.getPipeline();
        if (pipeline == null) return;

        // Sanitize inputs.
        if (Double.isNaN(kickX) || Double.isInfinite(kickX)) kickX = 0.0;
        if (Double.isNaN(kickY) || Double.isInfinite(kickY)) kickY = 0.0;
        if (Double.isNaN(kickZ) || Double.isInfinite(kickZ)) kickZ = 0.0;
        if (Double.isNaN(approachSpeed) || Double.isInfinite(approachSpeed) || approachSpeed < 0) {
            approachSpeed = 0.0;
        }

        // Base kick: take the impact direction (normalized), scale by approachSpeed * kickScale.
        double mag = Math.sqrt(kickX * kickX + kickY * kickY + kickZ * kickZ);
        double targetMag = Math.max(0.0, approachSpeed) * Math.max(0.0, params.kickScale());
        double linX = 0.0, linY = 0.25 * targetMag, linZ = 0.0; // small upward bias if no direction
        if (mag > 1.0e-6) {
            double inv = targetMag / mag;
            linX = kickX * inv;
            linY = kickY * inv * 0.5 + 0.25 * targetMag; // dampened Y, plus upward bias for "knocked off"
            linZ = kickZ * inv;
        }

        // Scatter: small random Δ added to make multiple debris bodies fan out organically.
        double scatter = Math.max(0.0, params.scatterStrength());
        if (scatter > 0.0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            linX += (r.nextDouble() * 2.0 - 1.0) * scatter;
            linY += r.nextDouble() * scatter * 0.6;
            linZ += (r.nextDouble() * 2.0 - 1.0) * scatter;
        }

        // Hard cap the linear magnitude.
        double cap = Math.max(0.5, params.maxKickSpeed());
        double vmag = Math.sqrt(linX * linX + linY * linY + linZ * linZ);
        if (Double.isNaN(vmag) || Double.isInfinite(vmag)) {
            linX = 0.0; linY = 0.0; linZ = 0.0;
        } else if (vmag > cap) {
            double s = cap / vmag;
            linX *= s; linY *= s; linZ *= s;
        }

        // Angular velocity: random axis, capped magnitude.
        double spinMag = Math.max(0.0, params.spinStrength());
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        Vector3d angular = new Vector3d(
            (tlr.nextDouble() - 0.5) * 2.0 * spinMag,
            (tlr.nextDouble() - 0.5) * 2.0 * spinMag,
            (tlr.nextDouble() - 0.5) * 2.0 * spinMag);
        Vector3d linear = new Vector3d(linX, linY, linZ);

        pipeline.addLinearAndAngularVelocity((PhysicsPipelineBody) debris, (Vector3dc) linear, (Vector3dc) angular);
    }
}
