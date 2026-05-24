/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.ElasticSubLevelDetector;
import com.example.sabletrueimpact.ImpactDamageAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class ImpactResolver {
    private ImpactResolver() {
    }

    public static boolean shouldSuppressCallbackDamage(ServerLevel level, BlockPos pos, BlockState state) {
        return ImpactDamageAllocator.isProtectedHardMaterialNearSoftTarget(level, pos, state, 3);
    }
}
