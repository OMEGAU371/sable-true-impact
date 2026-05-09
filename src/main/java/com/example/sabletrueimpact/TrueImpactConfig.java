package com.example.sabletrueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrueImpactConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_TRUE_IMPACT;
    public static final ModConfigSpec.DoubleValue MIN_EFFECT_VELOCITY;
    public static final ModConfigSpec.DoubleValue MIN_BREAK_VELOCITY;
    public static final ModConfigSpec.DoubleValue MIN_PROPAGATION_VELOCITY;
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE;
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
    public static final ModConfigSpec.BooleanValue ENABLE_TERRAIN_IMPACT_DAMAGE;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue TERRAIN_IMPACT_FORCE_THRESHOLD;
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
    public static final ModConfigSpec.BooleanValue ENABLE_SUBLEVEL_FRACTURE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FORCE_THRESHOLD;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_FORCE_SCALE;
    public static final ModConfigSpec.DoubleValue SUBLEVEL_FRACTURE_RADIUS;
    public static final ModConfigSpec.IntValue SUBLEVEL_FRACTURE_MAX_BLOCKS;
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
    public static final ModConfigSpec.BooleanValue ENABLE_CUMULATIVE_BLOCK_DAMAGE;
    public static final ModConfigSpec.DoubleValue CUMULATIVE_BLOCK_DAMAGE_SCALE;
    public static final ModConfigSpec.IntValue CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS;
    public static final ModConfigSpec.IntValue CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES;

    public static final ModConfigSpec SPEC;

    static {
        ENABLE_TRUE_IMPACT = BUILDER.comment("Master switch for all Sable True Impact behavior. If false, the mod keeps loading but adds no impact damage, cracks, fracture, entity damage, or reaction effects.")
                .define("enableTrueImpact", true);

        BUILDER.push("impact");
        MIN_EFFECT_VELOCITY = BUILDER.comment("Impacts below this speed do nothing. Raise this if tiny falls still leave marks.")
                .defineInRange("minEffectVelocity", 3.0, 0.0, 1000.0);
        MIN_BREAK_VELOCITY = BUILDER.comment("Impacts below this speed cannot directly break the hit block.")
                .defineInRange("minBreakVelocity", 12.0, 0.0, 1000.0);
        MIN_PROPAGATION_VELOCITY = BUILDER.comment("Impacts below this speed cannot spread cracks to nearby blocks.")
                .defineInRange("minPropagationVelocity", 18.0, 0.0, 1000.0);
        DAMAGE_SCALE = BUILDER.comment("Global damage multiplier. Lower is more realistic/conservative, higher is more cinematic.")
                .defineInRange("damageScale", 0.055, 0.0, 1000.0);
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
        MOVING_STRUCTURES_BREAK_BLOCKS = BUILDER.comment("If false, moving physical structures cannot break or cumulatively damage normal world blocks. Internal sublevel fracture can still split the moving structure.")
                .define("movingStructuresBreakBlocks", false);
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
        HARDNESS_STRENGTH_FACTOR = BUILDER.defineInRange("hardnessStrengthFactor", 18.0, 0.0, 1000.0);
        BLAST_STRENGTH_FACTOR = BUILDER.defineInRange("blastStrengthFactor", 4.5, 0.0, 1000.0);
        BASE_STRENGTH = BUILDER.comment("Flat base strength. Keep this low enough that material hardness still matters.")
                .defineInRange("baseStrength", 7.0, 0.0, 1000.0);
        CRACK_YIELD_THRESHOLD = BUILDER.defineInRange("crackYieldThreshold", 0.85, 0.0, 1000.0);
        BREAK_YIELD_THRESHOLD = BUILDER.defineInRange("breakYieldThreshold", 6.5, 0.0, 1000.0);
        HEAVY_BREAK_YIELD_THRESHOLD = BUILDER.defineInRange("heavyBreakYieldThreshold", 11.0, 0.0, 1000.0);
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
        BUILDER.pop();

        BUILDER.push("terrainImpact");
        ENABLE_TERRAIN_IMPACT_DAMAGE = BUILDER.comment("Experimental: when a Sable sublevel hits normal terrain, damage the terrain using the sublevel mass even if the callback fires on the sublevel block side.")
                .define("enableTerrainImpactDamage", true);
        TERRAIN_IMPACT_DAMAGE_SCALE = BUILDER.defineInRange("terrainImpactDamageScale", 0.00016, 0.0, 1000.0);
        TERRAIN_IMPACT_FORCE_THRESHOLD = BUILDER.comment("Terrain impacts below this force are treated as rolling/sliding contact and do not damage terrain.")
                .defineInRange("terrainImpactForceThreshold", 900.0, 0.0, 1000000000.0);
        TERRAIN_IMPACT_MASS_EXPONENT = BUILDER.comment("Separate mass exponent for terrain damage. Lower than global massExponent so normal vehicles crack terrain before breaking it.")
                .defineInRange("terrainImpactMassExponent", 0.7, 0.0, 2.0);
        TERRAIN_IMPACT_MAX_EFFECTIVE_MASS = BUILDER.comment("Separate effective mass cap for terrain damage.")
                .defineInRange("terrainImpactMaxEffectiveMass", 35.0, 1.0, 100000.0);
        TERRAIN_STEP_CONTACT_FORGIVENESS = BUILDER.comment("Horizontal impacts within this distance below a block top are treated as step-up contact, not terrain damage.")
                .defineInRange("terrainStepContactForgiveness", 0.22, 0.0, 1.0);
        TERRAIN_STEP_SIDE_NORMAL_THRESHOLD = BUILDER.comment("Contacts with vertical normal component below this are considered side contacts for step forgiveness.")
                .defineInRange("terrainStepSideNormalThreshold", 0.35, 0.0, 1.0);
        TERRAIN_IMPACT_BREAK_YIELD = BUILDER.defineInRange("terrainImpactBreakYield", 3.25, 0.0, 1000.0);
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
        BUILDER.pop();

        BUILDER.push("subLevelFracture");
        ENABLE_SUBLEVEL_FRACTURE = BUILDER.comment("Experimental: very strong impacts can cut weak internal connections so Sable's native splitter creates new sublevels.")
                .define("enableSubLevelFracture", true);
        SUBLEVEL_FRACTURE_FORCE_THRESHOLD = BUILDER.comment("Minimum collision force before internal sublevel fracture is considered. Raise this if vehicles split too easily.")
                .defineInRange("subLevelFractureForceThreshold", 420.0, 0.0, 1000000000.0);
        SUBLEVEL_FRACTURE_FORCE_SCALE = BUILDER.comment("Scales raw collision force into fracture damage.")
                .defineInRange("subLevelFractureForceScale", 0.052, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_RADIUS = BUILDER.comment("Maximum radius around the impact point scanned for weak connections.")
                .defineInRange("subLevelFractureRadius", 4.0, 0.0, 32.0);
        SUBLEVEL_FRACTURE_MAX_BLOCKS = BUILDER.comment("Maximum internal blocks removed by one fracture event.")
                .defineInRange("subLevelFractureMaxBlocks", 18, 0, 10000);
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
                .defineInRange("subLevelFractureFatigueScale", 0.35, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_INTERLOCK_STRENGTH = BUILDER.comment("Strength multiplier from checkerboard/interlocked mixed-material neighbors.")
                .defineInRange("subLevelFractureInterlockStrength", 1.8, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_BEAM_STRENGTH = BUILDER.comment("Strength multiplier from beam/girder/frame/support blocks near a candidate fracture.")
                .defineInRange("subLevelFractureBeamStrength", 2.4, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_CONTINUOUS_SEAM_WEAKNESS = BUILDER.comment("Extra weakness for straight continuous seams. Interlocked seams resist this.")
                .defineInRange("subLevelFractureContinuousSeamWeakness", 1.7, 0.0, 1000.0);
        SUBLEVEL_FRACTURE_WEAK_PLANE_SPREAD = BUILDER.comment("How much likely fracture planes increase fracture chance in nearby aligned blocks.")
                .defineInRange("subLevelFractureWeakPlaneSpread", 0.55, 0.0, 1000.0);
        BUILDER.pop();

        BUILDER.push("cumulativeDamage");
        ENABLE_CUMULATIVE_BLOCK_DAMAGE = BUILDER.comment("Accumulates repeated crack-level hits until the block breaks.")
                .define("enableCumulativeBlockDamage", true);
        CUMULATIVE_BLOCK_DAMAGE_SCALE = BUILDER.defineInRange("cumulativeBlockDamageScale", 1.0, 0.0, 1000.0);
        CUMULATIVE_BLOCK_DAMAGE_DECAY_TICKS = BUILDER.comment("Ticks before stored block damage expires if the block is not hit again.")
                .defineInRange("cumulativeBlockDamageDecayTicks", 600, 20, 72000);
        CUMULATIVE_BLOCK_DAMAGE_MAX_ENTRIES = BUILDER.comment("Safety cap for remembered damaged blocks.")
                .defineInRange("cumulativeBlockDamageMaxEntries", 4096, 128, 1000000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TrueImpactConfig() {
    }
}
