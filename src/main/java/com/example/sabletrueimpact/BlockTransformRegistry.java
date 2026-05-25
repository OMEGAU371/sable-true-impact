package com.example.sabletrueimpact;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parses and evaluates user-defined block transform rules from {@link TrueImpactConfig#BLOCK_TRANSFORM_RULES}.
 *
 * <p>Rule format: {@code "source:block -> target1:block[,weight1];target2:block[,weight2]"}
 * <ul>
 *   <li>Single target (100%): {@code "minecraft:gravel -> minecraft:sand"}</li>
 *   <li>Weighted random: {@code "minecraft:gravel -> minecraft:sand,0.7;minecraft:flint,0.3"}</li>
 * </ul>
 *
 * <p>Call {@link #reload()} on config load/reload. Threadsafe reads via volatile list swap.
 */
public final class BlockTransformRegistry {

    private record Target(Block block, double weight) {}
    private record Rule(Block source, List<Target> targets, double totalWeight) {}

    private static volatile List<Rule> RULES = List.of();

    private BlockTransformRegistry() {}

    public static void reload() {
        List<? extends String> entries;
        try {
            entries = TrueImpactConfig.BLOCK_TRANSFORM_RULES.get();
        } catch (Exception e) {
            RULES = List.of();
            return;
        }
        List<Rule> rules = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            try {
                String[] sides = entry.split("\\s*->\\s*", 2);
                if (sides.length != 2) continue;
                Block source = parseBlock(sides[0].trim());
                if (source == null) continue;
                List<Target> targets = parseTargets(sides[1].trim());
                if (targets.isEmpty()) continue;
                double total = 0.0;
                for (Target t : targets) total += t.weight();
                rules.add(new Rule(source, List.copyOf(targets), total));
            } catch (Exception ignored) {}
        }
        RULES = List.copyOf(rules);
    }

    /**
     * Returns a randomly-chosen target BlockState for the given source, or null if no rule matches.
     */
    @Nullable
    public static BlockState tryTransform(BlockState source) {
        for (Rule rule : RULES) {
            if (!source.is(rule.source())) continue;
            List<Target> targets = rule.targets();
            if (targets.size() == 1) return targets.get(0).block().defaultBlockState();
            double roll = ThreadLocalRandom.current().nextDouble() * rule.totalWeight();
            double acc = 0.0;
            for (Target t : targets) {
                acc += t.weight();
                if (roll < acc) return t.block().defaultBlockState();
            }
            return targets.get(targets.size() - 1).block().defaultBlockState();
        }
        return null;
    }

    @Nullable
    private static Block parseBlock(String id) {
        try {
            ResourceLocation loc = ResourceLocation.parse(id);
            Block b = (Block) BuiltInRegistries.BLOCK.get(loc);
            if (b == Blocks.AIR && !loc.getPath().equals("air")) return null;
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Target> parseTargets(String spec) {
        List<Target> out = new ArrayList<>();
        for (String part : spec.split(";")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] kv = part.split(",", 2);
            Block b = parseBlock(kv[0].trim());
            if (b == null) continue;
            double w = 1.0;
            if (kv.length > 1) {
                try { w = Double.parseDouble(kv[1].trim()); } catch (NumberFormatException ignored) {}
            }
            if (w <= 0.0) continue;
            out.add(new Target(b, w));
        }
        return out;
    }
}
