package com.example.sabletrueimpact;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ImpactCallbackDecider {
    private static final Set<Block> CALLBACK_BLOCKS = new HashSet<>();
    private static final Set<Block> CALLBACK_BLACKLIST = new HashSet<>();
    private static String mode = "blacklist";

    private ImpactCallbackDecider() {
    }

    public static void reload() {
        CALLBACK_BLOCKS.clear();
        CALLBACK_BLACKLIST.clear();
        mode = TrueImpactConfig.IMPACT_CALLBACK_MODE.get().trim().toLowerCase(Locale.ROOT);
        loadBlocks(TrueImpactConfig.IMPACT_CALLBACK_BLOCKS.get(), CALLBACK_BLOCKS);
        loadBlocks(TrueImpactConfig.IMPACT_CALLBACK_BLOCK_BLACKLIST.get(), CALLBACK_BLACKLIST);
    }

    public static BlockSubLevelCollisionCallback callbackFor(BlockState state, BlockSubLevelCollisionCallback original) {
        if (original != null) {
            return original;
        }
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get()
                || !TrueImpactConfig.ENABLE_UNIVERSAL_IMPACT_CALLBACK.get()
                || !allowsCallback(state)) {
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
        for (String entry : entries) {
            try {
                String trimmed = entry.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("tag:")) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse(trimmed);
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block != Blocks.AIR || id.getPath().equals("air")) {
                    target.add(block);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }
}
