package io.github.omegau371.trueimpact.damage;

/**
 * Material classification and threshold profiles for the TI damage pipeline.
 *
 * Each MaterialClass has two values:
 *   threshold(mc)      -- detection / accumulation denominator in J (unit: kpg·block²/s²)
 *   breakMultiplier(mc)-- multiply threshold to get the break threshold:
 *                         breakThreshold = threshold(mc) * breakMultiplier(mc)
 *
 * Example (STONE): detection at 40J global, break threshold = 50 * 10 = 500J.
 * Example (BRITTLE/glass): detection at 40J global, break threshold = 15 * 3 = 45J.
 * This means glass can be broken in a single moderate hit; stone takes ~7 hits.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class MaterialThresholdProfile {

    private MaterialThresholdProfile() {}

    // -------------------------------------------------------------------------
    // Material classes
    // -------------------------------------------------------------------------

    public enum MaterialClass {
        SOFT_SOIL,      // dirt, grass, gravel, sand -- compacts, does not shatter
        BRITTLE,        // glass, ice, terracotta -- cracks and shatters quickly
        WOOD,           // planks, logs -- medium toughness
        STONE,          // stone, cobblestone, deepslate, concrete -- standard hardness
        METAL,          // iron, gold, copper, chains -- tough, slow to crack
        HIGH_STRENGTH,  // obsidian, netherite, ancient_debris -- very hard to break
        GENERIC         // anything else -- treated like STONE but slightly easier
    }

    // -------------------------------------------------------------------------
    // Threshold: detection / accumulation denominator
    // -------------------------------------------------------------------------

    /**
     * Accumulation denominator for this material class.
     * ratio = accumulatedJ / (threshold * breakMultiplier).
     * Ratio >= 1.0 -> CRITICAL.
     */
    public static double threshold(MaterialClass mat) {
        return switch (mat) {
            case SOFT_SOIL     ->   5.0;
            case BRITTLE       ->  15.0;
            case WOOD          ->  20.0;
            case STONE         ->  50.0;
            case METAL         -> 120.0;
            case HIGH_STRENGTH -> 300.0;
            case GENERIC       ->  40.0;
        };
    }

    // -------------------------------------------------------------------------
    // Break multiplier: controls brittleness / toughness
    // -------------------------------------------------------------------------

    /**
     * Multiplier applied to threshold() when constructing DeferredDamageEvents.
     * breakThreshold = threshold(mc) * breakMultiplier(mc).
     *
     * Low multiplier  = brittle  (few hits to break).
     * High multiplier = tough    (many hits to break).
     *
     *   BRITTLE: 15 * 3  =  45 J -- glass shatters in 1-2 moderate hits
     *   WOOD:    20 * 5  = 100 J -- planks break in ~3 moderate hits
     *   STONE:   50 * 10 = 500 J -- stone takes ~7 moderate hits
     *   METAL:  120 * 15 = 1800J -- iron blocks resist all but heavy impacts
     *   GENERIC: 40 * 7  = 280 J -- unclassified blocks: easier than stone
     */
    public static double breakMultiplier(MaterialClass mat) {
        double base = switch (mat) {
            case SOFT_SOIL     ->  1.0;  // N/A -- walks the COMPACT path, not BREAK
            case BRITTLE       ->  3.0;
            case WOOD          ->  5.0;
            case STONE         -> 10.0;
            case METAL         -> 15.0;
            case HIGH_STRENGTH -> 25.0;
            case GENERIC       ->  7.0;
        };
        try {
            return base * io.github.omegau371.trueimpact.TrueImpactConfig.materialBreakMultiplier(mat);
        } catch (Throwable t) {
            // Config not loaded (unit-test environment without MC runtime) — use base value.
            return base;
        }
    }

    // -------------------------------------------------------------------------
    // Block ID classification
    // -------------------------------------------------------------------------

    /**
     * Classifies a block registry ID into a MaterialClass.
     * Unknown or null IDs return GENERIC.
     */
    public static MaterialClass classify(String blockId) {
        if (blockId == null) return MaterialClass.GENERIC;
        String id = blockId.startsWith("minecraft:")
                ? blockId.substring("minecraft:".length())
                : blockId;
        return switch (id) {

            // ── Soft soil ──────────────────────────────────────────────────
            case "dirt", "coarse_dirt", "grass_block", "gravel", "sand", "red_sand",
                 "farmland", "rooted_dirt", "podzol",
                 "mycelium", "soul_sand", "soul_soil",
                 "mud", "muddy_mangrove_roots",
                 "suspicious_sand", "suspicious_gravel"
                    -> MaterialClass.SOFT_SOIL;

            // ── Brittle ────────────────────────────────────────────────────
            // Glass
            case "glass", "glass_pane",
                 "white_stained_glass",   "orange_stained_glass",
                 "magenta_stained_glass", "light_blue_stained_glass",
                 "yellow_stained_glass",  "lime_stained_glass",
                 "pink_stained_glass",    "gray_stained_glass",
                 "light_gray_stained_glass", "cyan_stained_glass",
                 "purple_stained_glass",  "blue_stained_glass",
                 "brown_stained_glass",   "green_stained_glass",
                 "red_stained_glass",     "black_stained_glass",
                 "white_stained_glass_pane",   "orange_stained_glass_pane",
                 "magenta_stained_glass_pane", "light_blue_stained_glass_pane",
                 "yellow_stained_glass_pane",  "lime_stained_glass_pane",
                 "pink_stained_glass_pane",    "gray_stained_glass_pane",
                 "light_gray_stained_glass_pane", "cyan_stained_glass_pane",
                 "purple_stained_glass_pane",  "blue_stained_glass_pane",
                 "brown_stained_glass_pane",   "green_stained_glass_pane",
                 "red_stained_glass_pane",     "black_stained_glass_pane",
                 "tinted_glass",
                 // Ice
                 "ice", "frosted_ice", "packed_ice", "blue_ice",
                 // Terracotta (base + glazed variants)
                 "terracotta",
                 "white_terracotta",   "orange_terracotta",
                 "magenta_terracotta", "light_blue_terracotta",
                 "yellow_terracotta",  "lime_terracotta",
                 "pink_terracotta",    "gray_terracotta",
                 "light_gray_terracotta", "cyan_terracotta",
                 "purple_terracotta",  "blue_terracotta",
                 "brown_terracotta",   "green_terracotta",
                 "red_terracotta",     "black_terracotta",
                 "white_glazed_terracotta",   "orange_glazed_terracotta",
                 "magenta_glazed_terracotta", "light_blue_glazed_terracotta",
                 "yellow_glazed_terracotta",  "lime_glazed_terracotta",
                 "pink_glazed_terracotta",    "gray_glazed_terracotta",
                 "light_gray_glazed_terracotta", "cyan_glazed_terracotta",
                 "purple_glazed_terracotta",  "blue_glazed_terracotta",
                 "brown_glazed_terracotta",   "green_glazed_terracotta",
                 "red_glazed_terracotta",     "black_glazed_terracotta"
                    -> MaterialClass.BRITTLE;

            // ── Wood ───────────────────────────────────────────────────────
            // Planks
            case "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
                 "acacia_planks", "dark_oak_planks", "mangrove_planks",
                 "cherry_planks", "bamboo_planks",
                 "crimson_planks", "warped_planks",
                 // Logs / stripped logs
                 "oak_log", "spruce_log", "birch_log", "jungle_log",
                 "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log",
                 "bamboo_block",
                 "crimson_stem", "warped_stem",
                 "stripped_oak_log", "stripped_spruce_log", "stripped_birch_log",
                 "stripped_jungle_log", "stripped_acacia_log", "stripped_dark_oak_log",
                 "stripped_mangrove_log", "stripped_cherry_log",
                 "stripped_crimson_stem", "stripped_warped_stem",
                 // Wood blocks
                 "oak_wood", "spruce_wood", "birch_wood", "jungle_wood",
                 "acacia_wood", "dark_oak_wood", "mangrove_wood", "cherry_wood",
                 // Fences (structural but weaker)
                 "oak_fence", "spruce_fence", "birch_fence", "jungle_fence",
                 "acacia_fence", "dark_oak_fence", "mangrove_fence", "cherry_fence",
                 "crimson_fence", "warped_fence"
                    -> MaterialClass.WOOD;

            // ── Stone ──────────────────────────────────────────────────────
            case "stone", "cobblestone", "granite", "diorite", "andesite",
                 "polished_granite", "polished_diorite", "polished_andesite",
                 "deepslate", "cobbled_deepslate", "polished_deepslate",
                 "chiseled_deepslate", "deepslate_bricks", "deepslate_tiles",
                 "cracked_deepslate_bricks", "cracked_deepslate_tiles",
                 "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                 "chiseled_stone_bricks", "mossy_cobblestone", "bricks",
                 "sandstone", "chiseled_sandstone", "cut_sandstone", "smooth_sandstone",
                 "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "smooth_red_sandstone",
                 "smooth_stone",
                 "nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks",
                 "red_nether_bricks",
                 "blackstone", "polished_blackstone", "chiseled_polished_blackstone",
                 "polished_blackstone_bricks", "cracked_polished_blackstone_bricks",
                 "basalt", "polished_basalt", "smooth_basalt",
                 "quartz_block", "chiseled_quartz_block", "quartz_pillar", "smooth_quartz",
                 "purpur_block", "purpur_pillar",
                 "end_stone", "end_stone_bricks",
                 "calcite", "tuff", "dripstone_block",
                 // Concrete (all 16 colors)
                 "white_concrete",   "orange_concrete",
                 "magenta_concrete", "light_blue_concrete",
                 "yellow_concrete",  "lime_concrete",
                 "pink_concrete",    "gray_concrete",
                 "light_gray_concrete", "cyan_concrete",
                 "purple_concrete",  "blue_concrete",
                 "brown_concrete",   "green_concrete",
                 "red_concrete",     "black_concrete",
                 // Concrete powder (slightly weaker but same class)
                 "white_concrete_powder",   "orange_concrete_powder",
                 "magenta_concrete_powder", "light_blue_concrete_powder",
                 "yellow_concrete_powder",  "lime_concrete_powder",
                 "pink_concrete_powder",    "gray_concrete_powder",
                 "light_gray_concrete_powder", "cyan_concrete_powder",
                 "purple_concrete_powder",  "blue_concrete_powder",
                 "brown_concrete_powder",   "green_concrete_powder",
                 "red_concrete_powder",     "black_concrete_powder"
                    -> MaterialClass.STONE;

            // ── Metal ──────────────────────────────────────────────────────
            case "iron_block", "gold_block",
                 "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
                 "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
                 "waxed_copper_block", "waxed_exposed_copper",
                 "waxed_weathered_copper", "waxed_oxidized_copper",
                 "raw_iron_block", "raw_gold_block", "raw_copper_block",
                 "iron_bars", "chain",
                 "diamond_block", "emerald_block",
                 "amethyst_block"
                    -> MaterialClass.METAL;

            // ── High-strength ──────────────────────────────────────────────
            case "obsidian", "crying_obsidian",
                 "reinforced_deepslate",
                 "ancient_debris",
                 "netherite_block",
                 "bedrock"            // effectively immune at this multiplier (300*25=7500J)
                    -> MaterialClass.HIGH_STRENGTH;

            default -> MaterialClass.GENERIC;
        };
    }

    // -------------------------------------------------------------------------
    // Legacy helper (Phase 1C; kept for diagnostic command compatibility)
    // -------------------------------------------------------------------------

    public static boolean wouldExceed(double kImpact, MaterialClass mat) {
        return Double.isFinite(kImpact) && kImpact > threshold(mat);
    }
}
