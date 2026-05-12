package com.example.sabletrueimpact;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MaterialImpactProperties {
    private static final Map<Block, Properties> CUSTOM_OVERRIDES = new HashMap<>();
    private static final Map<Block, Boolean> DESTRUCTIBLE_OVERRIDES = new HashMap<>();

    private MaterialImpactProperties() {
    }

    public static void reload() {
        CUSTOM_OVERRIDES.clear();
        DESTRUCTIBLE_OVERRIDES.clear();

        List<? extends String> customProps = TrueImpactConfig.CUSTOM_BLOCK_PROPERTIES.get();
        for (String entry : customProps) {
            try {
                String[] parts = entry.split(",");
                if (parts.length < 7) continue;

                ResourceLocation id = ResourceLocation.parse(parts[0].trim());
                Block block = BuiltInRegistries.BLOCK.get(id);
                if (block == Blocks.AIR && !id.getPath().equals("air")) continue;

                double strength = Double.parseDouble(parts[1].trim());
                double toughness = Double.parseDouble(parts[2].trim());
                double friction = Double.parseDouble(parts[3].trim());
                double elasticity = Double.parseDouble(parts[4].trim());
                double mass = Double.parseDouble(parts[5].trim());
                boolean destructible = Boolean.parseBoolean(parts[6].trim());

                CUSTOM_OVERRIDES.put(block, new Properties(strength, toughness, 1.0, friction, elasticity, mass, destructible));
            } catch (Exception ignored) {
            }
        }

        List<? extends String> destructOverrides = TrueImpactConfig.DESTRUCTIBLE_OVERRIDES.get();
        for (String entry : destructOverrides) {
            try {
                String[] parts = entry.split(",");
                if (parts.length < 2) continue;

                String target = parts[0].trim();
                boolean destructible = Boolean.parseBoolean(parts[1].trim());

                if (target.startsWith("tag:")) {
                    // Tag support could be added here if needed, but for now we focus on block IDs
                } else {
                    ResourceLocation id = ResourceLocation.parse(target);
                    Block block = BuiltInRegistries.BLOCK.get(id);
                    if (block != Blocks.AIR || id.getPath().equals("air")) {
                        DESTRUCTIBLE_OVERRIDES.put(block, destructible);
                    }
                }
            } catch (Exception ignored) {
            }
        }
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

    public static double getFriction(BlockState state, double original) {
        Properties props = CUSTOM_OVERRIDES.get(state.getBlock());
        double base = props != null ? props.friction() : original;
        return base * TrueImpactConfig.GLOBAL_FRICTION_SCALE.get();
    }

    public static double getRestitution(BlockState state, double original) {
        Properties props = CUSTOM_OVERRIDES.get(state.getBlock());
        double base = props != null ? props.elasticity() : original;
        return base * TrueImpactConfig.GLOBAL_RESTITUTION_SCALE.get();
    }

    public static double getMass(BlockState state, double original) {
        Properties props = CUSTOM_OVERRIDES.get(state.getBlock());
        double base = props != null ? props.mass() : original;
        return base * TrueImpactConfig.GLOBAL_MASS_SCALE.get();
    }

    public static double getDefenseValue(BlockState state, Level level, BlockPos pos) {
        float hardness = Math.max(0.05f, state.getDestroySpeed(level, pos));
        double baseStrength = TrueImpactConfig.BASE_STRENGTH.get()
                + hardness * TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get();
        return breakThreshold(state, baseStrength);
    }

    public static boolean isDestructible(BlockState state, boolean original) {
        if (DESTRUCTIBLE_OVERRIDES.containsKey(state.getBlock())) {
            return DESTRUCTIBLE_OVERRIDES.get(state.getBlock());
        }
        Properties props = CUSTOM_OVERRIDES.get(state.getBlock());
        return props != null ? props.destructible() : original;
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
        Properties custom = CUSTOM_OVERRIDES.get(state.getBlock());
        if (custom != null) {
            return custom;
        }

        double blast = state.getBlock().getExplosionResistance();

        // Universal Scaling Algorithm:
        // 1. Strength Multiplier: Base multiplier from config.
        double strengthMult = TrueImpactConfig.GLOBAL_BLOCK_STRENGTH_SCALE.get();

        // 2. Toughness Multiplier: Scales with Blast Resistance.
        double toughnessMult = TrueImpactConfig.GLOBAL_BLOCK_TOUGHNESS_SCALE.get()
                * (1.0 + blast * TrueImpactConfig.BLAST_TOUGHNESS_FACTOR.get());

        // 3. Brittleness: Inversely proportional to Blast Resistance.
        double brittleness = TrueImpactConfig.GLOBAL_BRITTLENESS_SCALE.get()
                / (1.0 + blast * TrueImpactConfig.BLAST_BRITTLENESS_DECAY.get());

        return new Properties(
                strengthMult,
                toughnessMult,
                brittleness,
                -1, -1, -1, true
        );
    }

    private record Properties(double strengthMultiplier, double toughnessMultiplier, double brittleness,
                              double friction, double elasticity, double mass, boolean destructible) {
    }
}
