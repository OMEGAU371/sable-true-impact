/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.state.BlockState
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.TrueImpactConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MaterialImpactProperties {
    private static final Map<Block, Properties> CUSTOM_OVERRIDES = new HashMap<Block, Properties>();
    private static final Map<TagKey<Block>, Properties> CUSTOM_TAG_OVERRIDES = new HashMap<TagKey<Block>, Properties>();
    private static final Map<Block, Boolean> DESTRUCTIBLE_OVERRIDES = new HashMap<Block, Boolean>();
    private static final Map<TagKey<Block>, Boolean> DESTRUCTIBLE_TAG_OVERRIDES = new HashMap<TagKey<Block>, Boolean>();

    private MaterialImpactProperties() {
    }

    public static void reload() {
        CUSTOM_OVERRIDES.clear();
        CUSTOM_TAG_OVERRIDES.clear();
        DESTRUCTIBLE_OVERRIDES.clear();
        DESTRUCTIBLE_TAG_OVERRIDES.clear();
        List<? extends String> customProps = TrueImpactConfig.CUSTOM_BLOCK_PROPERTIES.get();
        for (String entry : customProps) {
            try {
                String[] parts = entry.split(",");
                if (parts.length < 7) continue;
                String target = parts[0].trim();
                double strength = Double.parseDouble(parts[1].trim());
                double toughness = Double.parseDouble(parts[2].trim());
                double friction = Double.parseDouble(parts[3].trim());
                double elasticity = Double.parseDouble(parts[4].trim());
                double mass = Double.parseDouble(parts[5].trim());
                boolean destructible = Boolean.parseBoolean(parts[6].trim());
                Properties properties = new Properties(strength, toughness, 1.0, friction, elasticity, mass, destructible, Source.BLOCK_OVERRIDE);
                if (target.startsWith("tag:")) {
                    CUSTOM_TAG_OVERRIDES.put(MaterialImpactProperties.blockTag(target), properties.withSource(Source.TAG_OVERRIDE));
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse((String)target);
                Block block = (Block)BuiltInRegistries.BLOCK.get(id);
                if (block == Blocks.AIR && !id.getPath().equals("air")) continue;
                CUSTOM_OVERRIDES.put(block, properties);
            }
            catch (Exception parts) {}
        }
        List<? extends String> destructOverrides = TrueImpactConfig.DESTRUCTIBLE_OVERRIDES.get();
        for (String entry : destructOverrides) {
            try {
                String[] parts = entry.split(",");
                if (parts.length < 2) continue;
                String target = parts[0].trim();
                boolean destructible = Boolean.parseBoolean(parts[1].trim());
                if (target.startsWith("tag:")) {
                    DESTRUCTIBLE_TAG_OVERRIDES.put(MaterialImpactProperties.blockTag(target), destructible);
                    continue;
                }
                ResourceLocation id = ResourceLocation.parse((String)target);
                Block block = (Block)BuiltInRegistries.BLOCK.get(id);
                if (block == Blocks.AIR && !id.getPath().equals("air")) continue;
                DESTRUCTIBLE_OVERRIDES.put(block, destructible);
            }
            catch (Exception exception) {}
        }
    }

    public static double strengthMultiplier(BlockState state) {
        if (!((Boolean)TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()).booleanValue()) {
            return 1.0;
        }
        return MaterialImpactProperties.properties(state).strengthMultiplier();
    }

    public static double toughnessMultiplier(BlockState state) {
        if (!((Boolean)TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()).booleanValue()) {
            return 1.0;
        }
        return MaterialImpactProperties.properties(state).toughnessMultiplier();
    }

    public static double brittleness(BlockState state) {
        if (!((Boolean)TrueImpactConfig.ENABLE_MATERIAL_TOUGHNESS.get()).booleanValue()) {
            return 1.0;
        }
        return MaterialImpactProperties.properties(state).brittleness();
    }

    public static double getFriction(BlockState state, double original) {
        Properties props = MaterialImpactProperties.customProperties(state);
        double base = props != null ? props.friction() : original;
        return base * (Double)TrueImpactConfig.GLOBAL_FRICTION_SCALE.get();
    }

    public static double getRestitution(BlockState state, double original) {
        Properties props = MaterialImpactProperties.customProperties(state);
        double base = props != null ? props.elasticity() : original;
        return base * (Double)TrueImpactConfig.GLOBAL_RESTITUTION_SCALE.get();
    }

    public static double getMass(BlockState state, double original) {
        Properties props = MaterialImpactProperties.customProperties(state);
        double base = props != null ? props.mass() : original;
        return base * (Double)TrueImpactConfig.GLOBAL_MASS_SCALE.get();
    }

    public static boolean isDestructible(BlockState state, boolean original) {
        if (DESTRUCTIBLE_OVERRIDES.containsKey(state.getBlock())) {
            return DESTRUCTIBLE_OVERRIDES.get(state.getBlock());
        }
        for (Map.Entry<TagKey<Block>, Boolean> entry : DESTRUCTIBLE_TAG_OVERRIDES.entrySet()) {
            if (!state.is(entry.getKey())) continue;
            return entry.getValue();
        }
        Properties props = MaterialImpactProperties.customProperties(state);
        return props != null ? props.destructible() : original;
    }

    public static double breakThreshold(BlockState state, double baseStrength) {
        return baseStrength * MaterialImpactProperties.strengthMultiplier(state) * MaterialImpactProperties.toughnessMultiplier(state);
    }

    public static double baseStrength(BlockGetter level, BlockPos pos, BlockState state) {
        double hardness = Math.max(0.05, (double)state.getDestroySpeed(level, pos));
        double blast = Math.max(0.0, (double)state.getBlock().getExplosionResistance());
        return MaterialImpactProperties.baseStrength(hardness, blast);
    }

    public static double baseStrength(double hardness, double blast) {
        hardness = Math.max(0.05, hardness);
        blast = Math.max(0.0, blast);
        double blastResistance = blast > 0.0 ? Math.pow(blast, (Double)TrueImpactConfig.BLAST_STRENGTH_EXPONENT.get()) : 0.0;
        double hardnessContrib = Math.pow(hardness, (Double)TrueImpactConfig.HARDNESS_STRENGTH_EXPONENT.get()) * (Double)TrueImpactConfig.HARDNESS_STRENGTH_FACTOR.get();
        double base = (Double)TrueImpactConfig.BASE_STRENGTH.get() + hardnessContrib + blastResistance * (Double)TrueImpactConfig.BLAST_STRENGTH_FACTOR.get();
        if (hardness < 1.0) {
            base *= ((Double)TrueImpactConfig.SOFT_BLOCK_STRENGTH_MULTIPLIER.get()).doubleValue();
        }
        return base;
    }

    public static double displayStrength(BlockState state, double baseStrength) {
        return baseStrength * MaterialImpactProperties.strengthMultiplier(state);
    }

    public static double displayToughness(BlockState state, double baseStrength) {
        return baseStrength * MaterialImpactProperties.toughnessMultiplier(state);
    }

    public static double fatigueDamage(BlockState state, double damage) {
        return damage * MaterialImpactProperties.brittleness(state);
    }

    public static String sourceLabel(BlockState state) {
        return switch (MaterialImpactProperties.properties(state).source().ordinal()) {
            default -> throw new MatchException(null, null);
            case 1 -> "STI block override";
            case 2 -> "STI tag override";
            case 0 -> "Vanilla/Sable derived";
        };
    }

    private static TagKey<Block> blockTag(String target) {
        return TagKey.create((ResourceKey)Registries.BLOCK, (ResourceLocation)ResourceLocation.parse((String)target.substring("tag:".length())));
    }

    private static Properties customProperties(BlockState state) {
        Properties custom = CUSTOM_OVERRIDES.get(state.getBlock());
        if (custom != null) {
            return custom;
        }
        for (Map.Entry<TagKey<Block>, Properties> entry : CUSTOM_TAG_OVERRIDES.entrySet()) {
            if (!state.is(entry.getKey())) continue;
            return entry.getValue();
        }
        return null;
    }

    private static Properties properties(BlockState state) {
        Properties custom = MaterialImpactProperties.customProperties(state);
        if (custom != null) {
            return custom;
        }
        double blast = state.getBlock().getExplosionResistance();
        double strengthMult = (Double)TrueImpactConfig.GLOBAL_BLOCK_STRENGTH_SCALE.get();
        double toughnessMult = (Double)TrueImpactConfig.GLOBAL_BLOCK_TOUGHNESS_SCALE.get() * (1.0 + Math.sqrt(Math.max(blast, 0.0)) * (Double)TrueImpactConfig.BLAST_TOUGHNESS_FACTOR.get());
        double brittleness = (Double)TrueImpactConfig.GLOBAL_BRITTLENESS_SCALE.get() / (1.0 + blast * (Double)TrueImpactConfig.BLAST_BRITTLENESS_DECAY.get());
        return new Properties(strengthMult, toughnessMult, brittleness, -1.0, -1.0, -1.0, true, Source.DERIVED);
    }

    private record Properties(double strengthMultiplier, double toughnessMultiplier, double brittleness, double friction, double elasticity, double mass, boolean destructible, Source source) {
        private Properties withSource(Source source) {
            return new Properties(this.strengthMultiplier, this.toughnessMultiplier, this.brittleness, this.friction, this.elasticity, this.mass, this.destructible, source);
        }
    }

    private static enum Source {
        DERIVED,
        BLOCK_OVERRIDE,
        TAG_OVERRIDE;

    }
}
