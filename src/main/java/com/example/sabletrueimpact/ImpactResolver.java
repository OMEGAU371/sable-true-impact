package com.example.sabletrueimpact;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class ImpactResolver {
    private ImpactResolver() {
    }

    public static boolean shouldSuppressCallbackDamage(ServerLevel level, BlockPos pos, BlockState state) {
        return ElasticSubLevelDetector.isInsideSubLevelPlot(level, pos)
                || ImpactDamageAllocator.isProtectedHardMaterialNearSoftTarget(level, pos, state, 3);
    }
}
