/*
 *  dev.ryanhcode.sable.Sable
 *  dev.ryanhcode.sable.sublevel.SubLevel
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Vec3i
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact.detach;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// 1.2.0 SD-port — BFS cluster planner.
//
// Given an impact-point block on a sub-level, walk outward and collect a connected cluster
// of blocks that all (a) belong to the same parent sub-level, (b) are similar enough in
// hardness, (c) sit within a Chebyshev-distance cap of the seed. Returns the parent +
// the cluster list, to be handed to SubLevelDetacher which carves them out of the parent
// and reassembles them as a debris sub-level via SubLevelAssemblyHelper.assembleBlocks.
//
// READ-ONLY here — never mutates the world; the caller decides whether to actually carve
// based on the plan (e.g. impact energy gates).
//
// Architecture reference: Sable: Destructive's ClusterPlanner (MIT). Simplifications vs SD:
//   - hardness similarity uses Minecraft's getDestroySpeed directly (no separate toughness
//     table to maintain — TI already has MaterialImpactProperties for the heavy lifting and
//     we don't need a second one for cluster grouping)
//   - clearer cluster size / radius parameters expected from the caller (TrueImpactConfig
//     will source them in step 5)
//
// Caps:
//   - MAX_CLUSTER caps any caller's request at a sane value
//   - radius cap is Chebyshev (max(|dx|,|dy|,|dz|)) — prevents thin tendrils
//   - maxWalks caps the total frontier expansion — bounded work per call
public final class ClusterCarver {

    public static final int MIN_CLUSTER = 1;
    public static final int MAX_CLUSTER = 64;

    private ClusterCarver() {
    }

    // The result of a successful plan: the parent sub-level the cluster belongs to, and the
    // list of block positions (in the embedded world frame — same coords passed to setBlock).
    public record Plan(SubLevel parent, List<BlockPos> blocks) {
    }

    // Plan a cluster of up to `targetSize` blocks anchored at `seed`. Returns null if the
    // seed is invalid, has no parent sub-level, or no cluster could be formed.
    //
    // Parameters:
    //   - seed:               the impact-point block (must be inside `level.hasChunkAt`)
    //   - targetSize:         soft target size — clamped to [MIN_CLUSTER, MAX_CLUSTER]
    //   - clusterRadius:      hard Chebyshev cap; blocks farther than this from seed are excluded
    //   - hardnessSimilarity: in [0, 1]; 1.0 = only same-hardness blocks, 0.0 = any hardness.
    //                         A neighbor with destroy-speed `h` is accepted iff
    //                         seedH * sim <= h <= seedH / sim  (with sim>0 to avoid div-by-zero)
    //   - random:             used to randomize neighbor expansion order for organic shape
    public static Plan plan(ServerLevel level, BlockPos seed, int targetSize, int clusterRadius,
                            double hardnessSimilarity, RandomSource random) {
        if (level == null || seed == null || random == null) {
            return null;
        }
        org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger("TIDetach");
        BlockState seedState;
        try {
            seedState = level.getBlockState(seed);
        } catch (Throwable t) {
            log.info("[beta] plan null: getBlockState threw at {}", seed);
            return null;
        }
        if (seedState == null) {
            log.info("[beta] plan null: seedState null at {}", seed);
            return null;
        }
        if (seedState.isAir()) {
            log.info("[beta] plan null: seed is AIR at {} (block-moved-or-destroyed)", seed);
            return null;
        }
        if (seedState.hasBlockEntity()) {
            log.info("[beta] plan null: seed has block entity at {} (state={})", seed, seedState);
            return null;
        }
        SubLevel parent;
        try {
            parent = Sable.HELPER.getContaining((Level) level, (Vec3i) seed);
        } catch (Throwable t) {
            log.info("[beta] plan null: getContaining threw at {}: {}", seed, t.toString());
            return null;
        }
        if (parent == null) {
            log.info("[beta] plan null: getContaining returned null at {} (seedState={})", seed, seedState);
            return null;
        }
        if (parent.isRemoved()) {
            log.info("[beta] plan null: parent isRemoved at {}", seed);
            return null;
        }
        int target = Math.max(MIN_CLUSTER, Math.min(MAX_CLUSTER, targetSize));
        int radius = Math.max(1, clusterRadius);
        if (target == 1) {
            // Trivial cluster of just the seed; skip the BFS machinery.
            return new Plan(parent, List.of(seed.immutable()));
        }

        float seedHardness = safeDestroySpeed(level, seed, seedState);
        double similarity = Math.max(0.0, Math.min(1.0, hardnessSimilarity));
        double minMatch = seedHardness * similarity;
        // Avoid division by zero / blowup; >0.05 keeps the window sane.
        double maxMatch = (similarity > 0.05) ? (seedHardness / similarity) : Double.POSITIVE_INFINITY;

        // Frontier ordered by squared distance to seed → BFS grows outward roughly spherically.
        PriorityQueue<BlockPos> frontier = new PriorityQueue<>(target * 2,
            Comparator.comparingInt(p -> distSq(p, seed)));
        HashSet<Long> visited = new HashSet<>(target * 2);
        ArrayList<BlockPos> chosen = new ArrayList<>(target);

        visited.add(seed.asLong());
        chosen.add(seed.immutable());
        frontier.add(seed);

        int maxWalks = target * 8 + 32; // hard cap on neighbour expansions
        int walks = 0;
        Direction[] dirs = Direction.values().clone();

        outer:
        while (!frontier.isEmpty() && chosen.size() < target && walks < maxWalks) {
            BlockPos cur = frontier.poll();
            shuffleInPlace(dirs, random);
            for (Direction d : dirs) {
                if (chosen.size() >= target) break outer;
                ++walks;
                BlockPos next = cur.relative(d);
                // Chebyshev radius gate.
                if (Math.abs(next.getX() - seed.getX()) > radius
                        || Math.abs(next.getY() - seed.getY()) > radius
                        || Math.abs(next.getZ() - seed.getZ()) > radius) {
                    continue;
                }
                if (!visited.add(next.asLong())) continue;
                if (!level.hasChunkAt(next)) continue;
                BlockState ns;
                try {
                    ns = level.getBlockState(next);
                } catch (Throwable t) {
                    continue;
                }
                if (ns == null || ns.isAir() || ns.hasBlockEntity()) continue;
                // Same-parent constraint: don't bridge across sub-levels (a detached cluster
                // must belong to ONE original parent, not span two).
                try {
                    SubLevel owner = Sable.HELPER.getContaining((Level) level, (Vec3i) next);
                    if (owner != parent) continue;
                } catch (Throwable t) {
                    continue;
                }
                // Hardness similarity gate (loose by default — a wooden cluster shouldn't
                // pull in attached iron blocks, but stone bricks + cobble is fine).
                float h = safeDestroySpeed(level, next, ns);
                if (h < minMatch || h > maxMatch) continue;

                chosen.add(next.immutable());
                frontier.add(next);
            }
        }
        if (chosen.isEmpty()) {
            return null;
        }
        return new Plan(parent, chosen);
    }

    private static float safeDestroySpeed(ServerLevel level, BlockPos pos, BlockState state) {
        try {
            return state.getDestroySpeed((BlockGetter) level, pos);
        } catch (Throwable t) {
            // Any throw → treat as hardness 0 (matches softest blocks) so this isn't a hard
            // exclusion gate; the caller can post-filter if needed.
            return 0f;
        }
    }

    private static int distSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void shuffleInPlace(Direction[] arr, RandomSource random) {
        for (int i = arr.length - 1; i > 0; --i) {
            int j = random.nextInt(i + 1);
            Direction tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }
}
