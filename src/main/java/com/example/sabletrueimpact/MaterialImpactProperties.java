package com.example.sabletrueimpact;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialImpactProperties {
    private MaterialImpactProperties() {
    }

    public static double strengthMultiplier(BlockState state) {
        if (!TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()) {
            return 1.0;
        }
        return properties(state).strengthMultiplier();
    }

    public static double toughnessMultiplier(BlockState state) {
        if (!TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()) {
            return 1.0;
        }
        return properties(state).toughnessMultiplier();
    }

    public static double brittleness(BlockState state) {
        if (!TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()) {
            return 1.0;
        }
        return properties(state).brittleness();
    }

    public static double breakThreshold(BlockState state, double baseStrength) {
        return baseStrength * strengthMultiplier(state) * toughnessMultiplier(state);
    }

    public static double displayStrength(BlockState state, double baseStrength) {
        return baseStrength * strengthMultiplier(state);
    }

    public static double displayToughness(BlockState state, double baseStrength) {
        return baseStrength * toughnessMultiplier(state);
    }

    public static double fatigueDamage(BlockState state, double damage) {
        return damage * brittleness(state);
    }

    private static Properties properties(BlockState state) {
        if (state.is(Blocks.NETHERITE_BLOCK)) {
            return new Properties(
                    TrueImpactConfig.NETHERITE_STRENGTH_MULTIPLIER.get(),
                    TrueImpactConfig.NETHERITE_TOUGHNESS_MULTIPLIER.get(),
                    TrueImpactConfig.NETHERITE_BRITTLENESS.get()
            );
        }
        if (state.is(Blocks.ANCIENT_DEBRIS)) {
            return new Properties(5.0, 10.0, 0.15);
        }
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.RESPAWN_ANCHOR)) {
            return new Properties(6.0, 8.0, 0.25);
        }
        return new Properties(
                TrueImpactConfig.DEFAULT_MATERIAL_STRENGTH_MULTIPLIER.get(),
                TrueImpactConfig.DEFAULT_MATERIAL_TOUGHNESS_MULTIPLIER.get(),
                TrueImpactConfig.DEFAULT_MATERIAL_BRITTLENESS.get()
        );
    }

    private record Properties(double strengthMultiplier, double toughnessMultiplier, double brittleness) {
    }
}
