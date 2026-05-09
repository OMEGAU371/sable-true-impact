package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class CrackPropagationUtils {

    public static void propagateCracks(ServerLevel level, BlockPos originPos, Block originBlock, double initialEnergy) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_CRACKS.get()
                || !TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()
                || initialEnergy <= TrueImpactConfig.PROPAGATION_MIN_ENERGY.get()) return; // Not enough energy to propagate
        
        Queue<PropagationNode> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        
        queue.add(new PropagationNode(originPos, initialEnergy));
        visited.add(originPos);
        
        int processed = 0;

        while (!queue.isEmpty() && processed < TrueImpactConfig.MAX_PROPAGATION_BLOCKS.get()) {
            PropagationNode current = queue.poll();
            BlockPos pos = current.pos;
            double energy = current.energy;
            
            // Skip origin as it's handled by the main collision callback
            if (!pos.equals(originPos)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;

                float hardness = state.getDestroySpeed(level, pos);
                float blastResist = state.getBlock().getExplosionResistance();
                if (hardness < 0.0f) continue; // Unbreakable
                
                double structuralIntegrity = (hardness * (TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get() + 2.0))
                        + (blastResist * (TrueImpactConfig.BLAST_STRENGTH_FACTOR.get() + 0.5))
                        + (TrueImpactConfig.BASE_STRENGTH.get() + 4.0);
                if (hardness < 1.0f) {
                    structuralIntegrity *= TrueImpactConfig.SOFT_BLOCK_STRENGTH_MULTIPLIER.get();
                }
                double yieldRatio = energy / structuralIntegrity;
                
                if (yieldRatio > TrueImpactConfig.PROPAGATION_CRACK_YIELD_THRESHOLD.get()) {
                    int crackProgress = (int) Math.min(5, ((yieldRatio - TrueImpactConfig.PROPAGATION_CRACK_YIELD_THRESHOLD.get()) / 3.25) * 6);
                    level.destroyBlockProgress(pos.hashCode() * 31, pos, crackProgress);
                    energy *= 0.55; // Cracking consumes energy. Propagation should mark stress, not excavate.
                } else {
                    // Not enough energy to affect this block, stop propagation along this path
                    continue; 
                }
            }

            processed++;

            // Propagate to neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pos.relative(dir);
                if (!visited.contains(neighborPos)) {
                    BlockState neighborState = level.getBlockState(neighborPos);
                    if (neighborState.isAir()) continue;
                    
                    double decay = (neighborState.getBlock() == originBlock)
                            ? TrueImpactConfig.SAME_BLOCK_DECAY.get()
                            : TrueImpactConfig.DIFFERENT_BLOCK_DECAY.get();
                    double nextEnergy = energy * decay;
                    
                    if (nextEnergy > 1.0) { // Only add if energy is still meaningful
                        visited.add(neighborPos);
                        queue.add(new PropagationNode(neighborPos, nextEnergy));
                    }
                }
            }
        }
    }

    private static class PropagationNode {
        BlockPos pos;
        double energy;

        PropagationNode(BlockPos pos, double energy) {
            this.pos = pos;
            this.energy = energy;
        }
    }
}
