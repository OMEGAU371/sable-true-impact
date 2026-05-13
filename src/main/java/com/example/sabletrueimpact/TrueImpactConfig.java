package com.example.sabletrueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrueImpactConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TRUE_IMPACT;
    public static final ModConfigSpec.ConfigValue<String> PRESET_MODE;
    public static final ModConfigSpec.ConfigValue<String> PERFORMANCE_PRESET;
    public static final ModConfigSpec.ConfigValue<String> DESTRUCTION_PRESET;
    public static final ModConfigSpec.DoubleValue GLOBAL_STRENGTH_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_BLOCK_STRENGTH_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_BLOCK_TOUGHNESS_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_BRITTLENESS_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_FRICTION_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_RESTITUTION_SCALE;
    public static final ModConfigSpec.DoubleValue GLOBAL_MASS_SCALE;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_IMPACT_LOGGING;

    public static final ModConfigSpec.BooleanValue ENABLE_MATERIAL_MATCHUP_DAMAGE;
    public static final ModConfigSpec.DoubleValue MATERIAL_MATCHUP_EXPONENT;
    public static final ModConfigSpec.DoubleValue TOUGHNESS_MATCHUP_EXPONENT;
    public static final ModConfigSpec.DoubleValue SELF_DAMAGE_IMMUNITY_RATIO;
    public static final ModConfigSpec.DoubleValue MIN_SELF_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue MAX_SELF_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue MIN_TARGET_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue MAX_TARGET_DAMAGE_SCALE;

    public static final ModConfigSpec.BooleanValue ENABLE_SOIL_COMPACTION;
    public static final ModConfigSpec.DoubleValue SOIL_COMPACTION_MIN_VELOCITY;
    public static final ModConfigSpec.DoubleValue SOIL_COMPACTION_MAX_VELOCITY;
    public static final ModConfigSpec.DoubleValue MIN_EFFECT_VELOCITY;
    public static final ModConfigSpec.DoubleValue MIN_BREAK_VELOCITY;
    public static final ModConfigSpec.DoubleValue MIN_PROPAGATION_VELOCITY;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue IMPACT_VELOCITY_EXPONENT;
    public static final ModConfigSpec.DoubleValue RESTITUTION_DAMAGE_REDUCTION;
    public static final ModConfigSpec.DoubleValue RESTITUTION_BREAK_VELOCITY_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue ELASTIC_SHATTER_VELOCITY;
    public static final ModConfigSpec.BooleanValue ELASTIC_BLOCKS_BREAK_BLOCKS;
    public static final ModConfigSpec.DoubleValue ELASTIC_SUBLEVEL_DETECTION_RANGE;
    public static final ModConfigSpec.IntValue ELASTIC_SUBLEVEL_SCAN_LIMIT;
    public static final ModConfigSpec.BooleanValue PROTECT_NEARBY_SUBLEVEL_IMPACTS;
    public static final ModConfigSpec.DoubleValue BOUNCE_RESPONSE_SCALE;
    public static final ModConfigSpec.DoubleValue BOUNCE_RESPONSE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue REACTION_RESPONSE_SCALE;
    public static final ModConfigSpec.DoubleValue REACTION_YIELD_LIMIT;
    public static final ModConfigSpec.BooleanValue MOVING_STRUCTURES_BREAK_BLOCKS;
    public static final ModConfigSpec.DoubleValue LOW_FRICTION_DAMAGE_REDUCTION;
    public static final ModConfigSpec.DoubleValue FRAGILE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MASS_EXPONENT;
    public static final ModConfigSpec.DoubleValue FALLBACK_IMPACT_MASS;
    public static final ModConfigSpec.DoubleValue MAX_EFFECTIVE_MASS;
    public static final ModConfigSpec.DoubleValue SOFT_BLOCK_STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HARDNESS_STRENGTH_FACTOR;
    public static final ModConfigSpec.DoubleValue BLAST_STRENGTH_FACTOR;
    public static final ModConfigSpec.DoubleValue BASE_STRENGTH;
    public static final ModConfigSpec.DoubleValue CRACK_YIELD_THRESHOLD;
    public static final ModConfigSpec.DoubleValue BREAK_YIELD_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HEAVY_BREAK_YIELD_THRESHOLD;
    public static final ModConfigSpec.DoubleValue PROPAGATION_YIELD_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_CRACKS;
    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_CRACK_PROPAGATION;
    public static final ModConfigSpec.IntValue MAX_PROPAGATION_BLOCKS;
    public static final ModConfigSpec.DoubleValue PROPAGATION_MIN_ENERGY;
    public static final ModConfigSpec.DoubleValue PROPAGATION_ENERGY_SCALE;
    public static final ModConfigSpec.DoubleValue SAME_BLOCK_DECAY;
    public static final ModConfigSpec.DoubleValue DIFFERENT_BLOCK_DECAY;
    public static final ModConfigSpec.DoubleValue PROPAGATION_CRACK_YIELD_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_PAIR_REACTION;
    public static final ModConfigSpec.DoubleValue PAIR_REACTION_SCALE;
    public static final ModConfigSpec.DoubleValue PAIR_REACTION_MAX_IMPULSE;
    public static final ModConfigSpec.DoubleValue PAIR_REACTION_MAX_VELOCITY_CHANGE;
    public static final ModConfigSpec.DoubleValue PAIR_REACTION_FORCE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_TERRAIN_IMPACT_DAMAGE;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_FORCE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_FORCE_EXPONENT;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_MASS_EXPONENT;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_MAX_EFFECTIVE_MASS;
    public static final ModConfigSpec.DoubleValue TERRAIN_STEP_CONTACT_FORGIVENESS;
    public static final ModConfigSpec.DoubleValue TERRAIN_STEP_SIDE_NORMAL_THRESHOLD;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_BREAK_YIELD;
    public static final ModConfigSpec.IntValue TERRAIN_IMPACT_MAX_BLOCKS;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_IMPACT_DAMAGE;
    public static final ModConfigSpec.DoubleValue ENTITY_IMPACT_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_IMPACT_MIN_DAMAGE;
    public static final ModConfigSpec.DoubleValue ENTITY_IMPACT_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue ENTITY_IMPACT_RADIUS;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVING_IMPACT_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVING_IMPACT_MAX_DAMAGE;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVING_IMPACT_CONTACT_MARGIN;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVING_IMPACT_MIN_RELATIVE_SPEED;
    public static final ModConfigSpec.DoubleValue ENTITY_MOVING_IMPACT_MIN_CLOSING_SPEED;
    public static final ModConfigSpec.DoubleValue ENTITY_STANDING_SUPPORT_TOLERANCE;
    public static final ModConfigSpec.DoubleValue ENTITY_STANDING_MAX_UPWARD_SPEED;
    public static final ModConfigSpec.DoubleValue ENTITY_IMPACT_MIN_SPEED;
    public static final ModConfigSpec.IntValue ENTITY_IMPACT_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue ENTITY_IMPACT_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue ENTITY_IMPACT_MAX_SUBLEVELS_PER_SCAN;
    public static final ModConfigSpec.BooleanValue ENABLE_SUBLEVEL_FRACTURE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FORCE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FORCE_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FORCE_EXPONENT;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_RADIUS;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_MAX_BLOCKS;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_MAX_CANDIDATES;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_SNAPSHOT_PADDING;
    public static final ModConfigSpec.BooleanValue ENABLE_ASYNC_FRACTURE_ANALYSIS;
    public static final ModConfigSpec.IntValue ASYNC_FRACTURE_MAX_QUEUED_JOBS;
    public static final ModConfigSpec.IntValue ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FRICTION_RESISTANCE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_SAME_BLOCK_RESISTANCE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_STICKY_RESISTANCE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_MASS_REFERENCE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_MASS_BONUS_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_IMBALANCE_BONUS_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_IMBALANCE_MAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_CHANCE_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FATIGUE_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_INTERLOCK_STRENGTH;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_BEAM_STRENGTH;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_CONTINUOUS_SEAM_WEAKNESS;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD;
    public static final ModConfigSpec.BooleanValue ENABLE_EXPLOSION_IMPACT_FRACTURE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_IMPACT_FORCE_SCALE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_IMPACT_RADIUS_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue EXPLOSION_IMPACT_CONFINEMENT_SCALE;
    public static final ModConfigSpec.IntValue EXPLOSION_IMPACT_MAX_SUBLEVELS;
    public static final ModConfigSpec.IntValue EXPLOSION_IMPACT_RAY_SAMPLES;
    public static final ModConfigSpec.DoubleValue EXPLOSION_IMPACT_RAY_STEP;
    public static final ModConfigSpec.BooleanValue ENABLE_EXPLOSION_IMPULSE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_IMPULSE_SCALE;
    public static final ModConfigSpec.DoubleValue EXPLOSION_MAX_IMPULSE;
    public static final ModConfigSpec.BooleanValue ENABLE_IMPACT_EXPLOSIONS;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_FORCE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_MASS_THRESHOLD;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_SCALE;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_MAX_RADIUS;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_FIRE_CHANCE;
    public static final ModConfigSpec.IntValue IMPACT_EXPLOSION_MAX_PER_BATCH;
    public static final ModConfigSpec.DoubleValue IMPACT_EXPLOSION_COALESCE_RADIUS;
    public static final ModConfigSpec.BooleanValue ENABLE_MATERIAL_TOUGHNESS;
    public static final ModConfigSpec.DoubleValue BLAST_TOUGHNESS_FACTOR;
    public static final ModConfigSpec.DoubleValue BLAST_BRITTLENESS_DECAY;
    public static final ModConfigSpec.BooleanValue ENABLE_CUMULATIVE_BLOCK_DAMAGE;
    public static final ModConfigSpec.DoubleValue CUMULATIVE_BLOCK_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS;
    public static final ModConfigSpec.IntValue CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES;
    public static final ModConfigSpec.IntValue PERFORMANCE_LOG_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_PERFORMANCE_LOGGING;
    public static final ModConfigSpec.BooleanValue ENABLE_UNIVERSAL_IMPACT_CALLBACK;
    public static final ModConfigSpec.ConfigValue<String> IMPACT_CALLBACK_MODE;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> IMPACT_CALLBACK_BLOCKS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> IMPACT_CALLBACK_BLOCK_BLACKLIST;
    public static final ModConfigSpec.BooleanValue ENABLE_CREATE_CONTRAPTION_ANCHOR_DAMAGE;
    public static final ModConfigSpec.DoubleValue CREATE_CONTRAPTION_ANCHOR_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue CREATE_CONTRAPTION_ANCHOR_DAMAGE_RADIUS;
    
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CUSTOM_BLOCK_PROPERTIES;
    public static final ModConfigSpec.BooleanValue ENABLE_PHYSICAL_DESTRUCTION;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_DESTRUCTION;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> DESTRUCTIBLE_OVERRIDES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("general");
        ENABLE_TRUE_IMPACT = BUILDER.comment("Master switch for all Sable True Impact behavior.")
                .define("enableTrueImpact", true);
        PRESET_MODE = BUILDER.comment("Preset mode. 'auto' applies performancePreset and destructionPreset to the main tuning values at config load. 'custom' leaves every advanced value untouched.")
                .define("presetMode", "auto");
        PERFORMANCE_PRESET = BUILDER.comment("Performance budget preset: potato, very_low, low, medium, high, very_high, destructive. This has priority over destructionPreset when both affect cost.")
                .define("performancePreset", "medium");
        DESTRUCTION_PRESET = BUILDER.comment("Destruction detail preset: off, low, medium, high, cinematic. Higher values enable more detailed cracks, fracture, explosions, and material response.")
                .define("destructionPreset", "medium");
        GLOBAL_STRENGTH_SCALE = BUILDER.comment("Global multiplier for impact damage and fracture power.")
                .defineInRange("globalStrengthScale", 1.0, 0.0, 1000.0);
        GLOBAL_BLOCK_STRENGTH_SCALE = BUILDER.comment("Global multiplier for block strength (how much force is needed to start damage).")
                .defineInRange("globalBlockStrengthScale", 1.0, 0.0, 1000000.0);
        GLOBAL_BLOCK_TOUGHNESS_SCALE = BUILDER.comment("Global multiplier for block toughness (how much energy is absorbed before breaking).")
                .defineInRange("globalBlockToughnessScale", 1.0, 0.0, 1000000.0);
        GLOBAL_BRITTLENESS_SCALE = BUILDER.comment("Global multiplier for block brittleness (how easily cracks spread).")
                .defineInRange("globalBrittlenessScale", 1.0, 0.0, 100.0);
        GLOBAL_FRICTION_SCALE = BUILDER.comment("Global multiplier for all block friction values.")
                .defineInRange("globalFrictionScale", 1.0, 0.0, 10.0);
        GLOBAL_RESTITUTION_SCALE = BUILDER.comment("Global multiplier for all block restitution (bounciness) values.")
                .defineInRange("globalRestitutionScale", 1.0, 0.0, 2.0);
        GLOBAL_MASS_SCALE = BUILDER.comment("Global multiplier for all block mass values.")
                .defineInRange("globalMassScale", 1.0, 0.0, 100.0);
        ENABLE_DEBUG_IMPACT_LOGGING = BUILDER.comment("If true, logs detailed material-matchup damage split calculations to the server console.")
                .define("enableDebugImpactLogging", false);

        BUILDER.push("materialMatchup");
        ENABLE_MATERIAL_MATCHUP_DAMAGE = BUILDER.comment("If true, damage split between colliding materials is determined by their relative impact resistance.")
                .define("enableMaterialMatchupDamage", true);
        MATERIAL_MATCHUP_EXPONENT = BUILDER.comment("Controls the severity of the damage split. 1.0 is linear; higher values make strong materials much more resistant to weak materials.")
                .defineInRange("materialMatchupExponent", 0.85, 0.0, 4.0);
        TOUGHNESS_MATCHUP_EXPONENT = BUILDER.comment("Extra damage split from toughness ratio. Higher values make tough materials chip less and brittle materials crack more.")
                .defineInRange("toughnessMatchupExponent", 0.45, 0.0, 4.0);
        SELF_DAMAGE_IMMUNITY_RATIO = BUILDER.comment("If target impact resistance is below this fraction of the moving material's resistance, the moving material takes no fracture damage.")
                .defineInRange("selfDamageImmunityRatio", 0.25, 0.0, 1.0);
        MIN_SELF_DAMAGE_SCALE = BUILDER.comment("The minimum possible damage multiplier for a strong material hitting a weak material.")
                .defineInRange("minSelfDamageScale", 0.0, 0.0, 1.0);
        MAX_SELF_DAMAGE_SCALE = BUILDER.comment("The maximum possible damage multiplier for a weak material hitting a strong material.")
                .defineInRange("maxSelfDamageScale", 3.0, 1.0, 100.0);
        MIN_TARGET_DAMAGE_SCALE = BUILDER.comment("The minimum possible damage multiplier applied to a target hit by a much weaker structure.")
                .defineInRange("minTargetDamageScale", 0.25, 0.0, 1.0);
        MAX_TARGET_DAMAGE_SCALE = BUILDER.comment("The maximum possible damage multiplier applied to a weak target hit by a much stronger/tougher structure.")
                .defineInRange("maxTargetDamageScale", 8.0, 1.0, 100.0);
        BUILDER.pop();
        
        HARDNESS_STRENGTH_FACTOR = BUILDER.comment("Strength added per 1.0 of vanilla block hardness. Hardness mainly resists the start of damage (Yield Point).")
                .defineInRange("hardnessStrengthFactor", 2.0, 0.0, 1000.0);
        BLAST_STRENGTH_FACTOR = BUILDER.comment("Strength added per 1.0 of vanilla explosion resistance. This keeps dense blast-proof materials from chipping before soft targets.")
                .defineInRange("blastStrengthFactor", 5.5, 0.0, 1000.0);
        BLAST_TOUGHNESS_FACTOR = BUILDER.comment("How much a block's vanilla explosion resistance contributes to its physical toughness (impact absorption).")
                .defineInRange("blastToughnessFactor", 0.65, 0.0, 1000.0);
        BUILDER.pop();

        ENABLE_SOIL_COMPACTION = BUILDER.comment("If true, light impacts on grass/podzol/mycelium compress the soil into dirt instead of breaking or ignoring the block. Heavy impacts still break the block normally.")
                .define("enableSoilCompaction", true);
        SOIL_COMPACTION_MIN_VELOCITY = BUILDER.comment("Minimum impact velocity to trigger soil compaction (grass → dirt). Below this, impacts are too gentle to do anything.")
                .defineInRange("soilCompactionMinVelocity", 3.0, 0.0, 1000.0);
        SOIL_COMPACTION_MAX_VELOCITY = BUILDER.comment("Impact velocity above which soil compaction is skipped and the block is treated like any other (broken normally). Below this threshold, grass is pressed into dirt instead.")
                .defineInRange("soilCompactionMaxVelocity", 14.0, 0.0, 1000.0);

        BUILDER.push("impact");
        MIN_EFFECT_VELOCITY = BUILDER.comment("Impacts below this speed do nothing. Raise this if tiny falls still leave marks.")
                .defineInRange("minEffectVelocity", 3.0, 0.0, 1000.0);
        MIN_BREAK_VELOCITY = BUILDER.comment("Impacts below this speed cannot directly break the hit block.")
                .defineInRange("minBreakVelocity", 12.0, 0.0, 1000.0);
        MIN_PROPAGATION_VELOCITY = BUILDER.comment("Impacts below this speed cannot spread cracks to nearby blocks.")
                .defineInRange("minPropagationVelocity", 18.0, 0.0, 1000.0);
        DAMAGE_SCALE = BUILDER.comment("Global damage multiplier. Lower is more realistic/conservative, higher is more cinematic.")
                .defineInRange("damageScale", 0.042, 0.0, 1000.0);
        IMPACT_VELOCITY_EXPONENT = BUILDER.comment("Velocity exponent for normal block-vs-sublevel impact energy. 2.0 is classic kinetic-energy scaling; higher values make high-speed impacts much more destructive.")
                .defineInRange("impactVelocityExponent", 2.0, 0.0, 8.0);
        RESTITUTION_DAMAGE_REDUCTION = BUILDER.comment("How strongly Sable restitution reduces impact damage. 1.0 means restitution 0.5 roughly halves damage.")
                .defineInRange("restitutionDamageReduction", 1.2, 0.0, 10.0);
        RESTITUTION_BREAK_VELOCITY_MULTIPLIER = BUILDER.comment("Elastic blocks require higher speed before they can break or strongly damage other blocks.")
                .defineInRange("restitutionBreakVelocityMultiplier", 6.0, 0.0, 20.0);
        ELASTIC_SHATTER_VELOCITY = BUILDER.comment("Elastic blocks below this speed convert impact into reaction motion instead of breaking or damaging terrain.")
                .defineInRange("elasticShatterVelocity", 22.0, 0.0, 1000.0);
        ELASTIC_BLOCKS_BREAK_BLOCKS = BUILDER.comment("If false, blocks with Sable restitution never break terrain through True Impact.")
                .define("elasticBlocksBreakBlocks", false);
        ELASTIC_SUBLEVEL_DETECTION_RANGE = BUILDER.comment("When terrain is hit, nearby elastic sublevels in this range suppress terrain damage.")
                .defineInRange("elasticSubLevelDetectionRange", 8.0, 0.0, 128.0);
        ELASTIC_SUBLEVEL_SCAN_LIMIT = BUILDER.comment("Maximum blocks scanned per nearby sublevel to detect elastic materials.")
                .defineInRange("elasticSubLevelScanLimit", 4096, 16, 1000000);
        PROTECT_NEARBY_SUBLEVEL_IMPACTS = BUILDER.comment("Safety fallback: if true and elastic blocks cannot break blocks, any nearby sublevel impact is converted to reaction motion instead of block damage. This is broad and disables most sublevel impact damage.")
                .define("protectNearbySubLevelImpacts", false);
        BOUNCE_RESPONSE_SCALE = BUILDER.comment("How much velocity is returned through Sable's collision callback for elastic blocks.")
                .defineInRange("bounceResponseScale", 1.15, 0.0, 10.0);
        BOUNCE_RESPONSE_THRESHOLD = BUILDER.comment("Restitution above this value gets a bounce response instead of normal damage at moderate speeds.")
                .defineInRange("bounceResponseThreshold", 0.15, 0.0, 1.0);
        REACTION_RESPONSE_SCALE = BUILDER.comment("Converts moderate would-be block damage into a counter-motion instead of breaking blocks.")
                .defineInRange("reactionResponseScale", 0.75, 0.0, 10.0);
        REACTION_YIELD_LIMIT = BUILDER.comment("Yield below this value prefers reaction force over block breaking. Raise for less terrain damage.")
                .defineInRange("reactionYieldLimit", 18.0, 0.0, 1000.0);
        MOVING_STRUCTURES_BREAK_BLOCKS = BUILDER.comment("If true, moving Sable physical structures can break or cumulatively damage normal world blocks. Internal sublevel fracture can still split the moving structure either way.")
                .define("movingStructuresBreakBlocks", true);
        LOW_FRICTION_DAMAGE_REDUCTION = BUILDER.comment("How strongly low-friction/glancing materials reduce damage.")
                .defineInRange("lowFrictionDamageReduction", 0.35, 0.0, 1.0);
        FRAGILE_DAMAGE_MULTIPLIER = BUILDER.comment("Extra damage multiplier for blocks tagged fragile by Sable.")
                .defineInRange("fragileDamageMultiplier", 1.75, 0.0, 100.0);
        MASS_EXPONENT = BUILDER.comment("How strongly Sable structure mass affects damage. 1.0 means linear effective mass before the cap.")
                .defineInRange("massExponent", 1.0, 0.0, 2.0);
        FALLBACK_IMPACT_MASS = BUILDER.comment("Mass used only when True Impact cannot identify the colliding Sable sublevel. Keep low to avoid tiny objects causing extreme damage.")
                .defineInRange("fallbackImpactMass", 1.0, 0.01, 100000.0);
        MAX_EFFECTIVE_MASS = BUILDER.comment("Caps effective mass so huge structures do not erase terrain too easily.")
                .defineInRange("maxEffectiveMass", 100.0, 1.0, 100000.0);
        SOFT_BLOCK_STRENGTH_MULTIPLIER = BUILDER.comment("Extra strength multiplier for soft blocks like dirt/grass. 1.0 keeps soil softer than wood and stone.")
                .defineInRange("softBlockStrengthMultiplier", 1.0, 1.0, 1000.0);
        BASE_STRENGTH = BUILDER.comment("Flat base strength. Keep this low enough that material hardness still matters.")
                .defineInRange("baseStrength", 12.0, 0.0, 1000.0);
        CRACK_YIELD_THRESHOLD = BUILDER.defineInRange("crackYieldThreshold", 1.15, 0.0, 1000.0);
        BREAK_YIELD_THRESHOLD = BUILDER.defineInRange("breakYieldThreshold", 9.5, 0.0, 1000.0);
        HEAVY_BREAK_YIELD_THRESHOLD = BUILDER.defineInRange("heavyBreakYieldThreshold", 16.0, 0.0, 1000.0);
        PROPAGATION_YIELD_THRESHOLD = BUILDER.defineInRange("propagationYieldThreshold", 22.0, 0.0, 1000.0);
        ENABLE_CRACKS = BUILDER.define("enableCracks", true);
        ENABLE_BLOCK_BREAKING = BUILDER.define("enableBlockBreaking", true);
        ENABLE_CRACK_PROPAGATION = BUILDER.define("enableCrackPropagation", true);
        BUILDER.pop();

        BUILDER.push("propagation");
        MAX_PROPAGATION_BLOCKS = BUILDER.comment("Maximum nearby blocks that can receive crack marks from one catastrophic impact.")
                .defineInRange("maxPropagationBlocks", 24, 0, 10000);
        PROPAGATION_MIN_ENERGY = BUILDER.defineInRange("propagationMinEnergy", 42.0, 0.0, 1000000.0);
        PROPAGATION_ENERGY_SCALE = BUILDER.defineInRange("propagationEnergyScale", 0.026, 0.0, 1000.0);
        SAME_BLOCK_DECAY = BUILDER.defineInRange("sameBlockDecay", 0.45, 0.0, 1.0);
        DIFFERENT_BLOCK_DECAY = BUILDER.defineInRange("differentBlockDecay", 0.20, 0.0, 1.0);
        PROPAGATION_CRACK_YIELD_THRESHOLD = BUILDER.defineInRange("propagationCrackYieldThreshold", 0.75, 0.0, 1000.0);
        BUILDER.pop();

        BUILDER.push("pairReaction");
        ENABLE_PAIR_REACTION = BUILDER.comment("Experimental: apply equal-ish counter impulses when two Sable physical sublevels collide and either side is elastic.")
                .define("enablePairReaction", true);
        PAIR_REACTION_SCALE = BUILDER.comment("Scales pair collision counter-impulses. Lower if elastic structures launch too hard.")
                .defineInRange("pairReactionScale", 0.025, 0.0, 10.0);
        PAIR_REACTION_MAX_IMPULSE = BUILDER.comment("Caps raw collision force used by pair reaction.")
                .defineInRange("pairReactionMaxImpulse", 250.0, 0.0, 1000000.0);
        PAIR_REACTION_MAX_VELOCITY_CHANGE = BUILDER.comment("Caps approximate velocity change from one pair reaction impulse.")
                .defineInRange("pairReactionMaxVelocityChange", 3.0, 0.0, 1000.0);
        PAIR_REACTION_FORCE_THRESHOLD = BUILDER.comment("Minimum collision force required to trigger a pair reaction impulse. Prevents micro-jitter and 'bumping' on light touches.")
                .defineInRange("pairReactionForceThreshold", 50.0, 0.0, 1000000.0);
        BUILDER.pop();

        BUILDER.push("terrainImpact");
        ENABLE_TERRAIN_IMPACT_DAMAGE = BUILDER.comment("Experimental: when a Sable sublevel hits normal terrain, damage the terrain using the sublevel mass even if the callback fires on the sublevel block side.")
                .define("enableTerrainImpactDamage", true);
        TERRAIN_IMPACT_DAMAGE_SCALE = BUILDER.defineInRange("terrainImpactDamageScale", 0.00016, 0.0, 1000.0);
        TERRAIN_IMPACT_FORCE_THRESHOLD = BUILDER.comment("Terrain impacts below this force are treated as rolling/sliding contact and do not damage terrain.")
                .defineInRange("terrainImpactForceThreshold", 2200.0, 0.0, 1000000000.0);
        TERRAIN_IMPACT_FORCE_EXPONENT = BUILDER.comment("Exponent applied to terrain collision force above the threshold. 1.0 keeps current linear force scaling; higher values make violent impacts dig much harder.")
                .defineInRange("terrainImpactForceExponent", 1.0, 0.0, 8.0);
        TERRAIN_IMPACT_MASS_EXPONENT = BUILDER.comment("Separate mass exponent for terrain damage. Lower than global massExponent so normal vehicles crack terrain before breaking it.")
                .defineInRange("terrainImpactMassExponent", 0.7, 0.0, 2.0);
        TERRAIN_IMPACT_MAX_EFFECTIVE_MASS = BUILDER.comment("Separate effective mass cap for terrain damage.")
                .defineInRange("terrainImpactMaxEffectiveMass", 35.0, 1.0, 100000.0);
        TERRAIN_STEP_CONTACT_FORGIVENESS = BUILDER.comment("Horizontal impacts within this distance below a block top are treated as step-up contact, not terrain damage.")
                .defineInRange("terrainStepContactForgiveness", 0.22, 0.0, 1.0);
        TERRAIN_STEP_SIDE_NORMAL_THRESHOLD = BUILDER.comment("Contacts with vertical normal component below this are considered side contacts for step forgiveness.")
                .defineInRange("terrainStepSideNormalThreshold", 0.35, 0.0, 1.0);
        TERRAIN_IMPACT_BREAK_YIELD = BUILDER.defineInRange("terrainImpactBreakYield", 8.5, 0.0, 1000.0);
        TERRAIN_IMPACT_MAX_BLOCKS = BUILDER.comment("Maximum terrain blocks broken by one terrain impact collision.")
                .defineInRange("terrainImpactMaxBlocks", 4, 0, 10000);
        BUILDER.pop();

        BUILDER.push("entityImpact");
        ENABLE_ENTITY_IMPACT_DAMAGE = BUILDER.comment("Damages living entities near a Sable sublevel impact point.")
                .define("enableEntityImpactDamage", true);
        ENTITY_IMPACT_DAMAGE_SCALE = BUILDER.defineInRange("entityImpactDamageScale", 0.18, 0.0, 1000.0);
        ENTITY_IMPACT_MIN_DAMAGE = BUILDER.defineInRange("entityImpactMinDamage", 1.0, 0.0, 1000.0);
        ENTITY_IMPACT_MAX_DAMAGE = BUILDER.comment("Maximum damage per entity impact. Set to 0 for unlimited.")
                .defineInRange("entityImpactMaxDamage", 18.0, 0.0, 1000000.0);
        ENTITY_IMPACT_RADIUS = BUILDER.comment("Radius around the impact point that can damage entities.")
                .defineInRange("entityImpactRadius", 1.75, 0.0, 32.0);
        ENTITY_MOVING_IMPACT_DAMAGE_SCALE = BUILDER.comment("Damage scale for direct moving sublevel vs living entity impacts.")
                .defineInRange("entityMovingImpactDamageScale", 0.006, 0.0, 1000.0);
        ENTITY_MOVING_IMPACT_MAX_DAMAGE = BUILDER.comment("Maximum damage for direct moving sublevel vs living entity impacts. Set to 0 for unlimited.")
                .defineInRange("entityMovingImpactMaxDamage", 6.0, 0.0, 1000000.0);
        ENTITY_MOVING_IMPACT_CONTACT_MARGIN = BUILDER.comment("Small contact margin for direct moving sublevel entity impacts. This replaces broad radius damage for direct hits.")
                .defineInRange("entityMovingImpactContactMargin", 0.18, 0.0, 4.0);
        ENTITY_MOVING_IMPACT_MIN_RELATIVE_SPEED = BUILDER.comment("Direct entity impact needs this much relative speed between sublevel and entity.")
                .defineInRange("entityMovingImpactMinRelativeSpeed", 3.0, 0.0, 1000.0);
        ENTITY_MOVING_IMPACT_MIN_CLOSING_SPEED = BUILDER.comment("Direct entity impact needs this much speed toward the entity, not merely shared acceleration.")
                .defineInRange("entityMovingImpactMinClosingSpeed", 1.75, 0.0, 1000.0);
        ENTITY_STANDING_SUPPORT_TOLERANCE = BUILDER.comment("Entities whose feet are within this distance above a sublevel top are treated as standing on it, not being hit by it.")
                .defineInRange("entityStandingSupportTolerance", 0.35, 0.0, 4.0);
        ENTITY_STANDING_MAX_UPWARD_SPEED = BUILDER.comment("Standing support is ignored if the sublevel is moving upward faster than this.")
                .defineInRange("entityStandingMaxUpwardSpeed", 1.25, 0.0, 1000.0);
        ENTITY_IMPACT_MIN_SPEED = BUILDER.comment("Sublevel speed below this value will not directly damage entities.")
                .defineInRange("entityImpactMinSpeed", 4.0, 0.0, 1000.0);
        ENTITY_IMPACT_COOLDOWN_TICKS = BUILDER.comment("Per entity/sublevel hit cooldown to avoid damage every tick while pinned.")
                .defineInRange("entityImpactCooldownTicks", 20, 0, 200);
        ENTITY_IMPACT_SCAN_INTERVAL_TICKS = BUILDER.comment("How often direct moving sublevel entity damage scans run. 1 = every tick, 2 = every other tick. Raise for better server performance.")
                .defineInRange("entityImpactScanIntervalTicks", 2, 1, 200);
        ENTITY_IMPACT_MAX_SUBLEVELS_PER_SCAN = BUILDER.comment("Maximum moving Sable sublevels checked for direct entity damage per scan. Lower this if worlds with many physical structures stutter.")
                .defineInRange("entityImpactMaxSubLevelsPerScan", 96, 1, 1000000);
        BUILDER.pop();

        BUILDER.push("subLevelFracture");
        ENABLE_SUBLEVEL_FRACTURE = BUILDER.comment("Experimental: very strong impacts can cut weak internal connections so Sable's native splitter creates new sublevels.")
                .define("enableSubLevelFracture", true);
        SUBLEVEL_FRACTURE_FORCE_THRESHOLD = BUILDER.comment("Minimum collision force before internal sublevel fracture is considered. Raise this if vehicles split too easily.")
                .defineInRange("subLevelFractureForceThreshold", 620.0, 0.0, 1000000000.0);
        SUBLEVEL_FRACTURE_FORCE_SCALE = BUILDER.comment("Scales raw collision force into fracture damage.")
                .defineInRange("subLevelFractureForceScale", 0.068, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_FORCE_EXPONENT = BUILDER.comment("Exponent applied to collision force above the fracture threshold. 1.0 keeps current linear behavior; higher values make high-speed/high-force crashes split structures much more aggressively.")
                .defineInRange("subLevelFractureForceExponent", 1.0, 0.0, 8.0);
        SUBLEVEL_FRACTURE_RADIUS = BUILDER.comment("Maximum radius around the impact point scanned for weak connections.")
                .defineInRange("subLevelFractureRadius", 4.0, 0.0, 32.0);
        SUBLEVEL_FRACTURE_MAX_BLOCKS = BUILDER.comment("Maximum internal blocks removed by one fracture event.")
                .defineInRange("subLevelFractureMaxBlocks", 18, 0, 10000);
        SUBLEVEL_FRACTURE_MAX_CANDIDATE_CHECKS = BUILDER.comment("Maximum block positions inspected by one fracture scan. Lower this to cap worst-case CPU cost.")
                .defineInRange("subLevelFractureMaxCandidateChecks", 384, 1, 1000000);
        SUBLEVEL_FRACTURE_MAX_CANDIDATES = BUILDER.comment("Maximum fracture candidates kept for sorting and chance checks. Lower this if impacts stutter when many blocks are nearby.")
                .defineInRange("subLevelFractureMaxCandidates", 96, 1, 1000000);
        SUBLEVEL_FRACTURE_COOLDOWN_TICKS = BUILDER.comment("Minimum ticks between expensive fracture scans for the same Sable physical structure. Raise this for large-structure performance.")
                .defineInRange("subLevelFractureCooldownTicks", 4, 0, 200);
        SUBLEVEL_FRACTURE_MAX_ATTEMPTS_PER_TICK = BUILDER.comment("Global cap for fracture scans started per server tick per dimension. Extra collision points skip fracture to protect TPS.")
                .defineInRange("subLevelFractureMaxAttemptsPerTick", 8, 1, 1000000);
        SUBLEVEL_FRACTURE_SNAPSHOT_PADDING = BUILDER.comment("Extra block padding captured around fracture candidates for structural analysis. 1 is faster, 2 is more detailed.")
                .defineInRange("subLevelFractureSnapshotPadding", 1, 0, 4);
        ENABLE_ASYNC_FRACTURE_ANALYSIS = BUILDER.comment("Experimental: compute fracture candidates on a background thread after the world snapshot is captured on the server thread. Final block changes still run on the server thread.")
                .define("enableAsyncFractureAnalysis", false);
        ASYNC_FRACTURE_MAX_QUEUED_JOBS = BUILDER.comment("Maximum pending async fracture jobs. New async jobs are skipped when the queue is full to protect server TPS.")
                .defineInRange("asyncFractureMaxQueuedJobs", 64, 1, 1000000);
        ASYNC_FRACTURE_MAX_APPLIED_JOBS_PER_TICK = BUILDER.comment("Maximum completed async fracture jobs applied to the world per tick.")
                .defineInRange("asyncFractureMaxAppliedJobsPerTick", 4, 1, 10000);
        SUBLEVEL_FRACTURE_FRICTION_RESISTANCE = BUILDER.comment("High-friction blocks resist fracture by this multiplier per friction point.")
                .defineInRange("subLevelFractureFrictionResistance", 1.6, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_SAME_BLOCK_RESISTANCE = BUILDER.comment("Same-material connections resist fracture more than mixed-material seams.")
                .defineInRange("subLevelFractureSameBlockResistance", 1.6, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_STICKY_RESISTANCE = BUILDER.comment("Resistance multiplier for sticky/glue-like blocks. Large values make them almost never fracture.")
                .defineInRange("subLevelFractureStickyResistance", 10000.0, 1.0, 1000000000.0);
        SUBLEVEL_FRACTURE_MASS_REFERENCE = BUILDER.comment("Mass at which a structure starts receiving noticeable extra fracture power.")
                .defineInRange("subLevelFractureMassReference", 14.0, 1.0, 1000000.0);
        SUBLEVEL_FRACTURE_MASS_BONUS_SCALE = BUILDER.comment("How much large structures amplify fracture power.")
                .defineInRange("subLevelFractureMassBonusScale", 0.8, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_IMBALANCE_BONUS_SCALE = BUILDER.comment("How much off-center impacts amplify fracture power.")
                .defineInRange("subLevelFractureImbalanceBonusScale", 0.85, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_IMBALANCE_MAX_MULTIPLIER = BUILDER.comment("Maximum multiplier from off-center mass imbalance.")
                .defineInRange("subLevelFractureImbalanceMaxMultiplier", 4.0, 1.0, 1000.0);
        SUBLEVEL_FRACTURE_CHANCE_SCALE = BUILDER.comment("Converts fracture score into actual break chance. Raise for more random/local breakage.")
                .defineInRange("subLevelFractureChanceScale", 1.25, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_IMPACT_FOCUS_EXPONENT = BUILDER.comment("Higher values concentrate fracture probability near the impact point.")
                .defineInRange("subLevelFractureImpactFocusExponent", 1.8, 0.1, 10.0);
        SUBLEVEL_FRACTURE_CRACK_BONUS_SCALE = BUILDER.comment("How much existing cumulative crack damage increases fracture chance.")
                .defineInRange("subLevelFractureCrackBonusScale", 3.0, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_FATIGUE_SCALE = BUILDER.comment("How much near-miss fracture force is stored as cumulative block damage/cracks.")
                .defineInRange("subLevelFractureFatigueScale", 0.22, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_INTERLOCK_STRENGTH = BUILDER.comment("Strength multiplier from checkerboard/interlocked mixed-material neighbors.")
                .defineInRange("subLevelFractureInterlockStrength", 1.8, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_BEAM_STRENGTH = BUILDER.comment("Strength multiplier from beam/girder/frame/support blocks near a candidate fracture.")
                .defineInRange("subLevelFractureBeamStrength", 2.4, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_CONTINUOUS_SEAM_WEAKNESS = BUILDER.comment("Extra weakness for straight continuous seams. Interlocked seams resist this.")
                .defineInRange("subLevelFractureContinuousSeamWeakness", 1.7, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD = BUILDER.comment("How much likely fracture planes increase fracture chance in nearby aligned blocks.")
                .defineInRange("subLevelFractureWeakPlaneSpread", 0.55, 0.0, 1000.0);
        BUILDER.pop();

        BUILDER.push("explosionImpact");
        ENABLE_EXPLOSION_IMPACT_FRACTURE = BUILDER.comment("If true, explosions send pressure waves into nearby Sable physical structures, causing cracks and internal fracture.")
                .define("enableExplosionImpactFracture", true);
        EXPLOSION_IMPACT_FORCE_SCALE = BUILDER.comment("Scales explosion radius and distance into fracture force. Raise for more dramatic blast separation.")
                .defineInRange("explosionImpactForceScale", 225.0, 0.0, 1000000.0);
        EXPLOSION_IMPACT_RADIUS_MULTIPLIER = BUILDER.comment("How far explosions search for Sable physical structures, as a multiplier of explosion radius.")
                .defineInRange("explosionImpactRadiusMultiplier", 1.65, 0.0, 32.0);
        EXPLOSION_IMPACT_CONFINEMENT_SCALE = BUILDER.comment("Extra blast pressure in enclosed spaces (blockedRatio^2). Higher values make indoor explosions much more lethal than outdoor ones.")
                .defineInRange("explosionImpactConfinementScale", 4.5, 0.0, 100.0);
        EXPLOSION_IMPACT_MAX_SUBLEVELS = BUILDER.comment("Maximum nearby Sable physical structures processed by one explosion.")
                .defineInRange("explosionImpactMaxSubLevels", 32, 1, 1000000);
        EXPLOSION_IMPACT_RAY_SAMPLES = BUILDER.comment("Invisible shockwave ray count used to detect enclosed blasts and impacted Sable structures. Higher is more accurate but costs more CPU.")
                .defineInRange("explosionImpactRaySamples", 48, 6, 512);
        EXPLOSION_IMPACT_RAY_STEP = BUILDER.comment("Distance advanced per shockwave ray sample step. Lower is more precise but costs more CPU.")
                .defineInRange("explosionImpactRayStep", 0.75, 0.1, 4.0);
        ENABLE_EXPLOSION_IMPULSE = BUILDER.comment("If true, shockwave hits push Sable physical structures away from the explosion center.")
                .define("enableExplosionImpulse", true);
        EXPLOSION_IMPULSE_SCALE = BUILDER.comment("Converts shockwave pressure into outward impulse. Raise for more dramatic flying debris.")
                .defineInRange("explosionImpulseScale", 0.018, 0.0, 1000.0);
        EXPLOSION_MAX_IMPULSE = BUILDER.comment("Caps outward impulse from one explosion hit per Sable physical structure.")
                .defineInRange("explosionMaxImpulse", 80.0, 0.0, 1000000.0);
        BUILDER.pop();

        BUILDER.push("impactExplosion");
        ENABLE_IMPACT_EXPLOSIONS = BUILDER.comment("If true, massive high-energy crashes can trigger standard Minecraft explosions at the impact point.")
                .define("enableImpactExplosions", false);
        IMPACT_EXPLOSION_FORCE_THRESHOLD = BUILDER.comment("Minimum collision force (before mass scaling) to trigger an impact explosion.")
                .defineInRange("impactExplosionForceThreshold", 5000.0, 0.0, 1000000000.0);
        IMPACT_EXPLOSION_MASS_THRESHOLD = BUILDER.comment("Minimum Sable structure mass required to trigger an impact explosion.")
                .defineInRange("impactExplosionMassThreshold", 15.0, 0.0, 1000000.0);
        IMPACT_EXPLOSION_SCALE = BUILDER.comment("Scales total impact energy (force * mass) into explosion radius.")
                .defineInRange("impactExplosionScale", 0.000018, 0.0, 100.0);
        IMPACT_EXPLOSION_MAX_RADIUS = BUILDER.comment("Caps the radius of impact-triggered explosions.")
                .defineInRange("impactExplosionMaxRadius", 12.0, 0.0, 64.0);
        IMPACT_EXPLOSION_FIRE_CHANCE = BUILDER.comment("Chance (0.0 to 1.0) that an impact explosion creates fire. 0.0 disables fire entirely.")
                .defineInRange("impactExplosionFireChance", 0.0, 0.0, 1.0);
        IMPACT_EXPLOSION_MAX_PER_BATCH = BUILDER.comment("Maximum number of explosions that can fire from a single collision batch. Prevents a crash from spawning dozens of simultaneous explosion sound sources and freezing physics-audio mods.")
                .defineInRange("impactExplosionMaxPerBatch", 1, 1, 64);
        IMPACT_EXPLOSION_COALESCE_RADIUS = BUILDER.comment("Explosions within this distance (blocks) of a stronger explosion in the same batch are suppressed. Keeps one big boom instead of many small ones.")
                .defineInRange("impactExplosionCoalesceRadius", 16.0, 0.0, 256.0);
        BUILDER.pop();

        BUILDER.push("materialToughness");
        ENABLE_MATERIAL_TOUGHNESS = BUILDER.comment("If true, selected materials receive separate strength, toughness, and brittleness multipliers.")
                .define("enableMaterialToughness", true);
        BLAST_BRITTLENESS_DECAY = BUILDER.comment("Scale for how much explosion resistance reduces a material's brittleness. High blast resistance makes materials more 'ductile'.")
                .defineInRange("blastBrittlenessDecay", 0.01, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("cumulativeDamage");
        ENABLE_CUMULATIVE_BLOCK_DAMAGE = BUILDER.comment("Accumulates repeated crack-level hits until the block breaks.")
                .define("enableCumulativeBlockDamage", true);
        CUMULATIVE_BLOCK_DAMAGE_SCALE = BUILDER.defineInRange("cumulativeBlockDamageScale", 0.65, 0.0, 1000.0);
        CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS = BUILDER.comment("Ticks before stored block damage expires if the block is not hit again.")
                .defineInRange("cumulativeBlockDamageDecayTicks", 600, 20, 72000);
        CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES = BUILDER.comment("Safety cap for remembered damaged blocks.")
                .defineInRange("cumulativeBlockDamageMaxEntries", 4096, 128, 1000000);
        BUILDER.pop();

        BUILDER.push("performance");
        ENABLE_PERFORMANCE_LOGGING = BUILDER.comment("If true, logs periodic Sable True Impact performance counters. Keep false for normal gameplay.")
                .define("enablePerformanceLogging", false);
        PERFORMANCE_LOG_INTERVAL_TICKS = BUILDER.comment("How often performance counters are logged when enablePerformanceLogging is true.")
                .defineInRange("performanceLogIntervalTicks", 200, 20, 72000);
        BUILDER.pop();

        BUILDER.push("customization");
        ENABLE_UNIVERSAL_IMPACT_CALLBACK = BUILDER.comment("If true, Sable True Impact attaches its collision callback while Sable builds voxel collider data. This avoids making every Block implement Sable's callback interface.")
                .define("enableUniversalImpactCallback", true);
        IMPACT_CALLBACK_MODE = BUILDER.comment("Controls callback selection. 'blacklist' applies True Impact to all blocks except impactCallbackBlockBlacklist. 'whitelist' applies only to impactCallbackBlocks.")
                .define("impactCallbackMode", "blacklist");
        IMPACT_CALLBACK_BLOCKS = BUILDER.comment("Blocks that receive the True Impact callback when impactCallbackMode is 'whitelist'.")
                .defineListAllowEmpty(java.util.List.of("impactCallbackBlocks"), java.util.ArrayList::new, o -> o instanceof String);
        IMPACT_CALLBACK_BLOCK_BLACKLIST = BUILDER.comment("Blocks that never receive the universal True Impact callback. Sable's own callbacks are still preserved. Keep Create functional blocks here if another mod changes their interaction behavior.")
                .defineListAllowEmpty(java.util.List.of("impactCallbackBlockBlacklist"), () -> java.util.List.of("create:blaze_burner"), o -> o instanceof String);
        CUSTOM_BLOCK_PROPERTIES = BUILDER.comment("List of custom block physical properties overrides. Format: 'modid:blockid,strength,toughness,friction,elasticity,mass,destructible'. Example: 'minecraft:obsidian,6.0,8.0,0.5,0.2,50.0,true'")
                .defineListAllowEmpty(java.util.List.of("customBlockProperties"), java.util.ArrayList::new, o -> o instanceof String);
        ENABLE_PHYSICAL_DESTRUCTION = BUILDER.comment("Global toggle for internal physical structure destruction (SubLevel Fracture).")
                .define("enablePhysicalDestruction", true);
        ENABLE_WORLD_DESTRUCTION = BUILDER.comment("Global toggle for world block destruction (Terrain/Blocks hit by physical structures).")
                .define("enableWorldDestruction", true);
        DESTRUCTIBLE_OVERRIDES = BUILDER.comment("Explicit list of block IDs or tags to mark as destructible (true) or indestructible (false). Format: 'modid:blockid,true' or 'tag:modid:tagname,false'. Overrides other settings.")
                .defineListAllowEmpty(java.util.List.of("destructibleOverrides"), java.util.ArrayList::new, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("createContraptionAnchors");
        ENABLE_CREATE_CONTRAPTION_ANCHOR_DAMAGE = BUILDER.comment("If true, impacts near Create contraption anchors transfer part of the load to bearings, pistons, pulleys, car assemblers, and nearby support blocks.")
                .define("enableCreateContraptionAnchorDamage", true);
        CREATE_CONTRAPTION_ANCHOR_DAMAGE_SCALE = BUILDER.comment("Damage scale for Create contraption anchor load transfer.")
                .defineInRange("createContraptionAnchorDamageScale", 1.0, 0.0, 1000.0);
        CREATE_CONTRAPTION_ANCHOR_DAMAGE_RADIUS = BUILDER.comment("Radius around an impact point to search for Create contraption anchors that should receive transferred impact load.")
                .defineInRange("createContraptionAnchorDamageRadius", 4, 0, 16);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TrueImpactConfig() {
    }
}
