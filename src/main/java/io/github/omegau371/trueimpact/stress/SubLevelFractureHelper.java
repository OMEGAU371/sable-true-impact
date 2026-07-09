package io.github.omegau371.trueimpact.stress;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.GatherResult;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor;
import io.github.omegau371.trueimpact.TrueImpactMod;
import io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.SublevelDamageAccumulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Executes stress-driven secondary fracture after a sublevel block is destroyed by Phase 3A.
 *
 * Flow:
 *   1. BFS stress propagation from the destroyed block (sublevel-local coords)
 *   2. Destroy blocks whose received stress exceeds their material's failure limit
 *   3. Find disconnected components in the remaining sublevel via gatherConnectedBlocks
 *   4. Assemble each smaller component as a new sublevel that splits from the parent
 *
 * Must only be called on the server thread (ServerTickEvent.Post context).
 * All Sable API calls are wrapped in try-catch — crashes are logged, never re-thrown.
 */
public final class SubLevelFractureHelper {

    private SubLevelFractureHelper() {}

    private static final int MAX_GATHER_BLOCKS = 4096;
    private static final int[][] DIRS = {
        {1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}
    };

    /**
     * Entry point. Call after accessor.destroyBlock() confirms destruction of (localX,Y,Z).
     *
     * @param parent     the sublevel the block was in
     * @param accessor   accessor for the sublevel's embedded plot
     * @param hostLevel  host ServerLevel (where the embedded plot lives at ~4×10⁷)
     * @param plotCenter plot.getCenterBlock() — local-to-world translation anchor
     * @param localX/Y/Z local coordinates of the block that was destroyed
     * @param kImpact    kinetic energy of the impact (J)
     * @param cpX/Y/Z    contact point in sublevel-local space (body-COM-local from Sable event);
     *                   used to compute the impact direction for directional stress propagation
     */
    public static void execute(
            ServerSubLevel parent,
            EmbeddedPlotLevelAccessor accessor,
            ServerLevel hostLevel,
            BlockPos plotCenter,
            int localX, int localY, int localZ,
            double kImpact,
            double cpX, double cpY, double cpZ) {
        try {
            executeInternal(parent, accessor, hostLevel, plotCenter,
                    localX, localY, localZ, kImpact, cpX, cpY, cpZ);
        } catch (Throwable t) {
            TrueImpactMod.LOGGER.warn("[TI-STRESS] SubLevelFractureHelper failed: {} {}",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    private static void executeInternal(
            ServerSubLevel parent,
            EmbeddedPlotLevelAccessor accessor,
            ServerLevel hostLevel,
            BlockPos plotCenter,
            int localX, int localY, int localZ,
            double kImpact,
            double cpX, double cpY, double cpZ) {

        // Impact direction: from contact point toward block center = force propagates INTO structure.
        // cpX/Y/Z is the surface point where the impactor touched; the block center is at localXYZ+0.5.
        double dirX = (localX + 0.5) - cpX;
        double dirY = (localY + 0.5) - cpY;
        double dirZ = (localZ + 0.5) - cpZ;
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len > 0.001) { dirX /= len; dirY /= len; dirZ /= len; }
        else             { dirX = 0;    dirY = 0;    dirZ = 0;    } // fallback: uniform spread

        // ── Step 1: Stress propagation ────────────────────────────────────────
        StressPropagator.BlockSampler sampler = (lx, ly, lz) -> {
            try {
                BlockState s = accessor.getBlockState(new BlockPos(lx, ly, lz));
                if (s == null || s.isAir()) return null;
                return MaterialThresholdProfile.classify(
                        BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
            } catch (Throwable ignored) { return null; }
        };

        List<StressPropagator.FractureCandidate> candidates = StressPropagator.propagate(
                localX, localY, localZ, kImpact, sampler, StressPropagator.DEFAULT_MAX_RADIUS,
                dirX, dirY, dirZ);

        if (candidates.isEmpty()) return;

        if (ImpactRuntimeConfig.LOG_STRESS)
            TrueImpactMod.LOGGER.info(
                    "[TI-STRESS] propagate from ({},{},{}) kImpact={}J → {} candidates",
                    localX, localY, localZ, String.format("%.1f", kImpact), candidates.size());

        // ── Step 2: Destroy secondary fracture points ─────────────────────────
        // Set of abs-positions already removed (destroyed block + fracture points).
        Set<BlockPos> removedAbsPos = new HashSet<>();
        removedAbsPos.add(plotCenter.offset(localX, localY, localZ));

        for (StressPropagator.FractureCandidate fc : candidates) {
            BlockPos localPos = new BlockPos(fc.lx(), fc.ly(), fc.lz());
            BlockState state;
            try { state = accessor.getBlockState(localPos); }
            catch (Throwable ignored) { continue; }
            if (state == null || state.isAir()) continue;

            BlockPos absPos = plotCenter.offset(fc.lx(), fc.ly(), fc.lz());
            if (!hostLevel.hasChunkAt(absPos)) continue;

            Block.dropResources(state, hostLevel, absPos, null, null, ItemStack.EMPTY);
            boolean broke;
            try { broke = accessor.destroyBlock(localPos, false, null, 512); }
            catch (Throwable ignored) { continue; }

            if (broke) {
                removedAbsPos.add(absPos);
                MaterialThresholdProfile.MaterialClass mc = MaterialThresholdProfile.classify(
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                SublevelDamageAccumulator.removeEntry(new SublevelDamageAccumulator.AccKey(
                        parent.getRuntimeId(), fc.lx(), fc.ly(), fc.lz(), mc));
                if (ImpactRuntimeConfig.LOG_STRESS)
                    TrueImpactMod.LOGGER.info("[TI-STRESS] secondary fracture at local=({},{},{}) abs={}",
                            fc.lx(), fc.ly(), fc.lz(), absPos);
            }
        }

        if (removedAbsPos.size() <= 1) return; // only the original block was destroyed → nothing more to do

        // ── Step 3: Find connected components via gatherConnectedBlocks ────────
        SubLevelAssemblyHelper.FrontierPredicate predicate =
                (fromPos, fromState, toPos, toState, dir) -> !removedAbsPos.contains(toPos);

        Set<BlockPos> alreadyAssigned = new HashSet<>(removedAbsPos);
        List<GatherResult> components = new ArrayList<>();

        for (BlockPos removed : removedAbsPos) {
            for (int[] d : DIRS) {
                BlockPos seed = removed.offset(d[0], d[1], d[2]);
                if (alreadyAssigned.contains(seed)) continue;
                if (!hostLevel.hasChunkAt(seed)) continue;
                if (hostLevel.getBlockState(seed).isAir()) { alreadyAssigned.add(seed); continue; }

                GatherResult result = SubLevelAssemblyHelper.gatherConnectedBlocks(
                        seed, hostLevel, MAX_GATHER_BLOCKS, predicate);

                if (result.assemblyState() == GatherResult.State.SUCCESS
                        && !result.blocks().isEmpty()) {
                    components.add(result);
                    alreadyAssigned.addAll(result.blocks());
                }
            }
        }

        if (components.size() <= 1) return; // still one connected body — no split

        if (ImpactRuntimeConfig.LOG_STRESS)
            TrueImpactMod.LOGGER.info("[TI-STRESS] split detected: {} components from sl={}",
                    components.size(), parent.getRuntimeId());

        // ── Step 4: Assemble each smaller component as a new sublevel ──────────
        // Largest component stays in parent (no assembleBlocks call for it).
        // Smaller components are kicked out via assembleBlocks which internally:
        //   • allocates a new sublevel
        //   • calls kickFromContainingSubLevel (inherits velocity from parent)
        //   • moves blocks from parent's plot to new sublevel's plot
        int largestIdx = 0;
        for (int i = 1; i < components.size(); i++) {
            if (components.get(i).blocks().size() > components.get(largestIdx).blocks().size()) {
                largestIdx = i;
            }
        }

        for (int i = 0; i < components.size(); i++) {
            if (i == largestIdx) continue;

            GatherResult comp = components.get(i);
            BlockPos origin = comp.blocks().iterator().next();

            try {
                ServerSubLevel newSublevel = SubLevelAssemblyHelper.assembleBlocks(
                        hostLevel, origin, comp.blocks(), comp.boundingBox());
                if (newSublevel != null) {
                    // lastNetworkedPose is the latest pose sent to clients (public API).
                    // Sets splitFromPose used by ClientboundRecentlySplitSubLevelPacket.
                    newSublevel.setSplitFrom(parent, parent.lastNetworkedPose());
                    if (ImpactRuntimeConfig.LOG_STRESS)
                        TrueImpactMod.LOGGER.info(
                                "[TI-STRESS] assembled new sl={} ({} blocks) splitFrom sl={}",
                                newSublevel.getRuntimeId(), comp.blocks().size(), parent.getRuntimeId());
                }
            } catch (Throwable t) {
                TrueImpactMod.LOGGER.warn("[TI-STRESS] assembleBlocks failed: {} {}",
                        t.getClass().getSimpleName(), t.getMessage());
            }
        }
    }
}
