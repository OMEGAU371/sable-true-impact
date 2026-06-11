package io.github.omegau371.trueimpact.damage;

/**
 * T-9 diagnostic material threshold profiles for Phase 1C calibration.
 *
 * Maps block registry IDs (e.g. "minecraft:stone") to material classes
 * and assigns a temporary diagnostic threshold per class.
 *
 * CONTRACT (Phase 1C):
 *   - All thresholds are TEMPORARY DIAGNOSTIC VALUES for manual calibration only.
 *   - They are NOT the final material threshold system.
 *   - Phase 1D will replace this with BlockHardnessProfile (derived from vanilla hardness).
 *   - wouldExceed() is diagnostic-only; it MUST NOT trigger any game effect.
 *   - contactCount, forceAmount, rawSumForce MUST NOT appear in wouldExceed() or threshold().
 *   - DamageResolver must still return NONE regardless of wouldExceed().
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 *
 * See docs/phase-1c-damage-model.md "T-9: materialThresholdJ calibration" section.
 */
public final class MaterialThresholdProfile {

    private MaterialThresholdProfile() {}

    // -------------------------------------------------------------------------
    // Material classes
    // -------------------------------------------------------------------------

    /**
     * Material class assigned to a block for threshold lookup.
     *
     * GENERIC is the fallback for any block not explicitly mapped.
     * All thresholds are Phase 1C diagnostic placeholders; calibrate in T-9.
     */
    public enum MaterialClass {
        SOFT_SOIL,      // dirt, grass, gravel, sand -- low threshold
        WOOD,           // planks -- medium-low threshold
        STONE,          // stone, cobblestone, deepslate variants -- medium threshold
        METAL,          // iron_block, gold_block, copper_block -- high threshold
        HIGH_STRENGTH,  // obsidian, ancient_debris, netherite_block -- very high threshold
        GENERIC         // fallback for unmapped blocks -- same as STONE placeholder
    }

    // -------------------------------------------------------------------------
    // Threshold lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the temporary Phase 1C diagnostic threshold for a material class.
     *
     * Units: kpg*block^2/s^2 (same as kineticImpactEnergyJ).
     * DIAGNOSTIC ONLY -- these values are initial calibration guesses, not final thresholds.
     */
    public static double threshold(MaterialClass mat) {
        return switch (mat) {
            case SOFT_SOIL     ->   5.0;
            case WOOD          ->  20.0;
            case STONE         ->  50.0;
            case METAL         -> 120.0;
            case HIGH_STRENGTH -> 300.0;
            case GENERIC       ->  50.0;
        };
    }

    // -------------------------------------------------------------------------
    // Block ID classification
    // -------------------------------------------------------------------------

    /**
     * Classifies a block by its registry ID to a MaterialClass.
     *
     * Accepts IDs with or without the "minecraft:" namespace prefix.
     * Unknown or null IDs return GENERIC.
     *
     * Coverage is intentionally narrow (Phase 1C calibration set only).
     * Expand in Phase 1D when BlockHardnessProfile is implemented.
     */
    public static MaterialClass classify(String blockId) {
        if (blockId == null) return MaterialClass.GENERIC;
        String id = blockId.startsWith("minecraft:")
                ? blockId.substring("minecraft:".length())
                : blockId;
        return switch (id) {
            // Soft soil
            case "dirt", "grass_block", "gravel", "sand",
                 "farmland", "rooted_dirt", "podzol",
                 "mycelium", "soul_sand", "soul_soil" -> MaterialClass.SOFT_SOIL;

            // Wood (all vanilla plank variants)
            case "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
                 "acacia_planks", "dark_oak_planks", "mangrove_planks",
                 "cherry_planks", "bamboo_planks",
                 "crimson_planks", "warped_planks" -> MaterialClass.WOOD;

            // Stone (common hard blocks)
            case "stone", "cobblestone", "granite", "diorite", "andesite",
                 "deepslate", "cobbled_deepslate", "polished_deepslate",
                 "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
                 "mossy_cobblestone", "bricks",
                 "sandstone", "chiseled_sandstone", "cut_sandstone",
                 "red_sandstone" -> MaterialClass.STONE;

            // Metal blocks
            case "iron_block", "gold_block",
                 "copper_block", "exposed_copper", "weathered_copper", "oxidized_copper",
                 "raw_iron_block", "raw_gold_block",
                 "raw_copper_block" -> MaterialClass.METAL;

            // High-strength blocks
            case "obsidian", "crying_obsidian",
                 "reinforced_deepslate",
                 "ancient_debris",
                 "netherite_block" -> MaterialClass.HIGH_STRENGTH;

            default -> MaterialClass.GENERIC;
        };
    }

    // -------------------------------------------------------------------------
    // Threshold comparison
    // -------------------------------------------------------------------------

    /**
     * Returns true when kImpact exceeds the threshold for the given material class.
     *
     * Returns false when kImpact is NaN or non-finite (no velocity data available).
     * DIAGNOSTIC ONLY: this result MUST NOT trigger any game effect.
     *
     * Uses kineticImpactEnergyJ (velocity-derived canonical). contactCount,
     * forceAmount, and rawSumForce must NOT be passed here.
     */
    public static boolean wouldExceed(double kImpact, MaterialClass mat) {
        return Double.isFinite(kImpact) && kImpact > threshold(mat);
    }
}
