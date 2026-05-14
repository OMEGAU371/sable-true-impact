/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPhysicsSolver;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class ImpactCallbackDecider {
    private static final Set<Block> CALLBACK_BLOCKS = new HashSet<Block>();
    private static final Set<Block> CALLBACK_BLACKLIST = new HashSet<Block>();
    private static String mode = "blacklist";

    private ImpactCallbackDecider() {
    }

    public static void reload() {
        CALLBACK_BLOCKS.clear();
        CALLBACK_BLACKLIST.clear();
        mode = ((String)TrueImpactConfig.IMPACT_CALLBACK_MODE.get()).trim().toLowerCase(Locale.ROOT);
        ImpactCallbackDecider.loadBlocks((Iterable)TrueImpactConfig.IMPACT_CALLBACK_BLOCKS.get(), CALLBACK_BLOCKS);
        ImpactCallbackDecider.loadBlocks((Iterable)TrueImpactConfig.IMPACT_CALLBACK_BLOCK_BLACKLIST.get(), CALLBACK_BLACKLIST);
    }

    public static BlockSubLevelCollisionCallback callbackFor(BlockState state, BlockSubLevelCollisionCallback original) {
        if (original != null) {
            return original;
        }
        if (!(((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() && ((Boolean)TrueImpactConfig.ENABLE_UNIVERSAL_IMPACT_CALLBACK.get()).booleanValue() && ImpactCallbackDecider.allowsCallback(state))) {
            return null;
        }
        return TrueImpactPhysicsSolver.HARDNESS_CALLBACK;
    }

    private static boolean allowsCallback(BlockState state) {
        Block block = state.getBlock();
        if ("whitelist".equals(mode)) {
            return CALLBACK_BLOCKS.contains(block);
        }
        return !CALLBACK_BLACKLIST.contains(block);
    }

    private static void loadBlocks(Iterable<? extends String> entries, Set<Block> target) {
        for (String string : entries) {
            try {
                ResourceLocation id;
                Block block;
                String trimmed = string.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("tag:") || (block = (Block)BuiltInRegistries.BLOCK.get(id = ResourceLocation.parse((String)trimmed))) == Blocks.AIR && !id.getPath().equals("air")) continue;
                target.add(block);
            }
            catch (RuntimeException runtimeException) {}
        }
    }
}

