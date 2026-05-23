/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CrackPropagationUtils {
    public static void propagateCracks(ServerLevel level, BlockPos originPos, Block originBlock, double initialEnergy) {
        if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_CRACKS.get()).booleanValue() || !((Boolean)TrueImpactConfig.ENABLE_CRACK_PROPAGATION.get()).booleanValue() || initialEnergy <= (Double)TrueImpactConfig.PROPAGATION_MIN_ENERGY.get()) {
            return;
        }
        ArrayDeque<PropagationNode> queue = new ArrayDeque<PropagationNode>();
        HashSet<BlockPos> visited = new HashSet<BlockPos>();
        queue.add(new PropagationNode(originPos, initialEnergy));
        visited.add(originPos);
        int processed = 0;
        while (!queue.isEmpty() && processed < (Integer)TrueImpactConfig.MAX_PROPAGATION_BLOCKS.get()) {
            PropagationNode current = (PropagationNode)queue.poll();
            BlockPos pos = current.pos;
            double energy = current.energy;
            if (!pos.equals(originPos)) {
                double structuralIntegrity;
                double yieldRatio;
                float hardness;
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || (hardness = state.getDestroySpeed((BlockGetter)level, pos)) < 0.0f || !((yieldRatio = energy / (structuralIntegrity = MaterialImpactProperties.baseStrength((BlockGetter)level, pos, state) + 4.0)) > (Double)TrueImpactConfig.PROPAGATION_CRACK_YIELD_THRESHOLD.get())) continue;
                int crackProgress = (int)Math.min(5.0, (yieldRatio - (Double)TrueImpactConfig.PROPAGATION_CRACK_YIELD_THRESHOLD.get()) / 3.25 * 6.0);
                level.destroyBlockProgress(pos.hashCode() * 31, pos, crackProgress);
                energy *= 0.55;
            }
            ++processed;
            for (Direction dir : Direction.values()) {
                double decay;
                double nextEnergy;
                BlockState neighborState;
                BlockPos neighborPos = pos.relative(dir);
                if (visited.contains(neighborPos) || (neighborState = level.getBlockState(neighborPos)).isAir() || !((nextEnergy = energy * (decay = (neighborState.getBlock() == originBlock ? (Double)TrueImpactConfig.SAME_BLOCK_DECAY.get() : (Double)TrueImpactConfig.DIFFERENT_BLOCK_DECAY.get()).doubleValue())) > 1.0)) continue;
                visited.add(neighborPos);
                queue.add(new PropagationNode(neighborPos, nextEnergy));
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

