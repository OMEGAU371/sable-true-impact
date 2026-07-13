package io.github.omegau371.trueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge SERVER config for True Impact.
 * Stored per-world under saves/<world>/serverconfig/true_impact-server.toml.
 *
 * Structure:
 *   [general]                        — basic toggles for casual users (block breaking, damage accumulation,
 *                                      world/structure damage, structure-vs-structure damage, drop mode)
 *   [advanced.materials]             — per-material break multipliers
 *   [advanced.compaction]            — surface-layer compaction (grass -> dirt etc.), independent of worldBlockDamage
 *   [advanced.penetrationDynamics]   — penetration dynamics tuning
 *   [advanced.balance]               — damage preset + detection threshold
 *   [advanced.damageMultipliers]     — global/per-category damage multipliers
 *   [advanced.compatibility.create]  — Create mod integration toggles
 *   [advanced.crackOverlay]          — crack overlay timing
 *   [advanced.calibration]           — formula coefficients and base thresholds (advanced/expert tuning)
 *   [advanced.feedback]              — cosmetic particle/sound feedback rate limiting
 *   [advanced.mining]                — per-tool-tier impact energy thresholds (used when dropMode=BY_FORCE)
 *
 * Key names are English identifiers; display labels and tooltips for both English and Chinese
 * clients come from assets/true_impact/lang/en_us.json and zh_cn.json.
 */
public final class TrueImpactConfig {

    private TrueImpactConfig() {}

    public static final ModConfigSpec SPEC;

    // ── [general] ────────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_DAMAGE_ACCUMULATION;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_BLOCK_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_PHYSICS_STRUCTURE_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_STRUCTURE_VS_STRUCTURE;
    public static final ModConfigSpec.EnumValue<DropMode> DROP_MODE;
    public static final ModConfigSpec.DoubleValue DROP_CHANCE;

    // ── [advanced.mining] ───────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue NETHERITE_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue DIAMOND_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue IRON_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue STONE_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue WOODEN_PICKAXE_MAX_J;

    // ── [advanced.materials] ─────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue BRITTLE_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WOOD_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STONE_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue METAL_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HIGH_STRENGTH_BREAK_MULTIPLIER;

    // ── [advanced.compaction] ─────────────────────────────────────────────────

    /** Master switch for surface-layer compaction (grass -> dirt etc.); independent of worldBlockDamage. */
    public static final ModConfigSpec.BooleanValue ENABLE_COMPACTION;
    /** Player-configurable compaction rules; see CompactionRule for the "fromId;toId;thresholdJ;probability" format. */
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> COMPACTION_RULES;

    // ── [advanced.penetrationDynamics] ───────────────────────────────────────

    /** Master switch for penetration dynamics. */
    public static final ModConfigSpec.BooleanValue ENABLE_PENETRATION;
    /** Minimum single-contact impact energy (J) to enter penetration mode. */
    public static final ModConfigSpec.DoubleValue PENETRATION_TRIGGER_J;
    /** Cap on re-injected penetration speed (m/s); must stay below the engine's tunneling threshold. */
    public static final ModConfigSpec.DoubleValue PENETRATION_MAX_SPEED_MS;
    /** Minimum contact speed (m/s) to enter penetration mode — mass-independent dynamic/static criterion. */
    public static final ModConfigSpec.DoubleValue PENETRATION_MIN_SPEED_MS;
    /** Energy loss factor per crushed layer (>1 accounts for terrain-side crushing and heat dissipation). */
    public static final ModConfigSpec.DoubleValue PENETRATION_LOSS_FACTOR;
    /** Footprint radius (blocks) crushed per penetration layer; 0 = contact patch only. */
    public static final ModConfigSpec.IntValue PENETRATION_FOOTPRINT_RADIUS;

    // ── [advanced.balance] ───────────────────────────────────────────────────

    public static final ModConfigSpec.EnumValue<DamagePreset> DAMAGE_PRESET;
    public static final ModConfigSpec.DoubleValue DETECTION_THRESHOLD_J;

    // ── [advanced.damageMultipliers] ─────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue TOTAL_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PHYSICS_STRUCTURE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WORLD_BLOCK_DAMAGE_MULTIPLIER;

    // ── [advanced.compatibility] ─────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue ENABLE_CREATE_INTERACTION;
    public static final ModConfigSpec.DoubleValue CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TRAIN_DERAIL_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue MINECART_KILL_THRESHOLD_J;

    // ── [advanced.crackOverlay] ───────────────────────────────────────────────

    public static final ModConfigSpec.IntValue CRACK_UPDATE_COOLDOWN_TICKS;

    // ── [advanced.calibration] ────────────────────────────────────────────────
    // Formula coefficients and base thresholds for the damage calculation system
    // (see BlockHardnessProfile, ConfinementFactor, MaterialThresholdProfile).

    public static final ModConfigSpec.DoubleValue CRACK_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue CRACK_EXPONENT;
    public static final ModConfigSpec.DoubleValue CRACK_MIN_J;
    public static final ModConfigSpec.DoubleValue CRACK_MAX_J;
    public static final ModConfigSpec.DoubleValue BREAK_BASE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BREAK_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue BREAK_EXPONENT;
    public static final ModConfigSpec.DoubleValue STRIKER_YIELD_TOLERANCE;
    public static final ModConfigSpec.DoubleValue STRUCTURE_VS_STRUCTURE_SPLIT_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue CONFINEMENT_PER_FACE_CAP;
    public static final ModConfigSpec.DoubleValue CONFINEMENT_DIRECTION_BASE;
    public static final ModConfigSpec.DoubleValue CONFINEMENT_DIRECTION_AMPLITUDE;
    public static final ModConfigSpec.DoubleValue OVERBURDEN_EFFICIENCY;
    public static final ModConfigSpec.DoubleValue SOFT_SOIL_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue BRITTLE_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue WOOD_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue STONE_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue METAL_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue HIGH_STRENGTH_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue GENERIC_THRESHOLD_J;

    // ── [advanced.feedback] ───────────────────────────────────────────────────
    // Cosmetic particle/sound feedback rate limiting.

    public static final ModConfigSpec.IntValue FEEDBACK_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue FEEDBACK_BUDGET_PER_TICK;

    // ── [advanced.debug] ──────────────────────────────────────────────────────
    /** Verbose physics structure damage: full per-block calc path (hardness/threshold/confinement/ratio). Requires physicsStructureDamageLog also enabled. */
    public static final ModConfigSpec.BooleanValue LOG_PHASE3A_VERBOSE;
    /** Elastic floor: a hit below "break threshold × this ratio" causes zero damage (fatigue limit). */
    public static final ModConfigSpec.DoubleValue SUBLEVEL_ELASTIC_FLOOR;
    /** Stress-relaxation half-life (ticks): accumulated damage halves every N ticks; 0 = no decay. */
    public static final ModConfigSpec.IntValue SUBLEVEL_DAMAGE_HALF_LIFE_TICKS;
    /** Cap on per-contact velocity change (m/s): guards against non-physical energy spikes from grab-constraint yanks. */
    public static final ModConfigSpec.DoubleValue SUBLEVEL_MAX_DELTA_V_MS;

    /** Fires on every Sable sable$onCollision callback: blockId, pos, speed, thin-block filter result. Extremely high frequency. */
    public static final ModConfigSpec.BooleanValue LOG_BLOCK_CALLBACK;
    /** World block damage enqueue + apply: kImpact, confinement, blockId, pos, ratio, damage state, break result. */
    public static final ModConfigSpec.BooleanValue LOG_PATH1;
    /** Physics structure damage: enqueue (plotCp/bodyLocalCp/com), break attempt (blockId/local/ratio/state), destroyBlock result. */
    public static final ModConfigSpec.BooleanValue LOG_PHASE3A;
    /** Dynamic structure damage: contraption search, anchor accumulation/break, train derailment, minecart destruction. */
    public static final ModConfigSpec.BooleanValue LOG_PHASE4A;
    /** SableImpactCapture.process() internals: pairMap entry count, sawWorldContact, phase3aSnapMap size. Extremely high frequency. */
    public static final ModConfigSpec.BooleanValue LOG_IMPACT_CAPTURE;
    /** Every BodySnapshot read: pos, linVel, com, mass, rotation. High frequency. */
    public static final ModConfigSpec.BooleanValue LOG_BODY_SNAPSHOT;
    /** Low-frequency summary: event count and total kImpact per tick with a collision. Enabled by default. */
    public static final ModConfigSpec.BooleanValue LOG_ENERGY_SUMMARY;

    // ── Build ─────────────────────────────────────────────────────────────────

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Basic on/off toggles for casual players: whether blocks break at all,",
                  "whether damage accumulates over multiple hits, and drop behavior.")
                .push("general");
        ENABLE_BLOCK_BREAKING = b
                .comment("Whether damaged blocks are actually destroyed. Does NOT gate damage",
                         "accumulation or crack display -- those are controlled by damageAccumulation below.")
                .define("enableBlockBreaking", true);
        ENABLE_DAMAGE_ACCUMULATION = b
                .comment("Whether damage persists and accumulates across multiple hits (with crack",
                         "overlays showing intermediate progress). When OFF, there is no accumulated",
                         "state at all: each hit is judged alone -- if it alone reaches the break",
                         "threshold the block is destroyed outright (subject to enableBlockBreaking), otherwise",
                         "nothing happens and no crack is shown.")
                .define("damageAccumulation", true);
        ENABLE_WORLD_BLOCK_DAMAGE = b
                .comment("Master switch for whether world blocks take damage from impacts",
                         "(damage accumulation, crack overlay, destruction). Mirrors physicsStructureDamage",
                         "for physics structures. Does NOT gate surface compaction (grass -> dirt etc.) --",
                         "see [advanced.compaction], which is independent.")
                .define("worldBlockDamage", true);
        ENABLE_PHYSICS_STRUCTURE_DAMAGE = b
                .comment("Whether physics structures take damage when colliding with terrain.")
                .define("physicsStructureDamage", true);
        ENABLE_STRUCTURE_VS_STRUCTURE = b
                .comment("Whether physics structures take damage when colliding with each other.")
                .define("structureVsStructureDamage", true);
        DROP_MODE = b
                .comment("Block drop mode: DISABLED=no drops, BY_FORCE=drops depend on impact energy (see [advanced.mining]), ALL=always drop as if mined by netherite pickaxe.")
                .defineEnum("dropMode", DropMode.BY_FORCE);
        DROP_CHANCE = b
                .comment("Chance (0.0-1.0) that a destroyed block actually drops its item, independent of dropMode.",
                         "Lower this to reduce item-entity and particle load when a lot of blocks break at once.")
                .defineInRange("dropChance", 1.0, 0.0, 1.0);
        b.pop();

        b.comment("Fine-tuning for players who want more control over damage formulas,",
                  "thresholds, logging, and Create integration.")
                .push("advanced");

        b.comment("Per-material-class break multipliers (brittle/wood/stone/metal/high-strength).")
                .push("materials");
        BRITTLE_BREAK_MULTIPLIER = b
                .comment("Break multiplier for brittle materials (glass, ice, terracotta). Base threshold: 45 J.")
                .defineInRange("brittleBreakMultiplier", 1.0, 0.1, 20.0);
        WOOD_BREAK_MULTIPLIER = b
                .comment("Break multiplier for wood materials (planks, logs). Base threshold: 100 J.")
                .defineInRange("woodBreakMultiplier", 1.0, 0.1, 20.0);
        STONE_BREAK_MULTIPLIER = b
                .comment("Break multiplier for stone materials (stone, cobblestone, concrete). Base threshold: 500 J.")
                .defineInRange("stoneBreakMultiplier", 1.0, 0.1, 20.0);
        METAL_BREAK_MULTIPLIER = b
                .comment("Break multiplier for metal materials (iron, gold, copper). Base threshold: 1800 J.")
                .defineInRange("metalBreakMultiplier", 1.0, 0.1, 20.0);
        HIGH_STRENGTH_BREAK_MULTIPLIER = b
                .comment("Break multiplier for high-strength materials (obsidian, netherite). Base threshold: 7500 J.")
                .defineInRange("highStrengthBreakMultiplier", 1.0, 0.1, 20.0);
        b.pop();

        b.comment("Surface-layer compaction (grass -> dirt, farmland -> dirt, etc.) -- a transformation,",
                  "not damage, so it runs independently of worldBlockDamage.")
                .push("compaction");
        ENABLE_COMPACTION = b
                .comment("Whether impacts compact soft-soil surface blocks into their denser form",
                         "according to the rules below. Independent of worldBlockDamage: can stay on",
                         "even with world block damage disabled, and vice versa.")
                .define("enableCompaction", true);
        COMPACTION_RULES = b
                .comment("Compaction rules, one per line: \"fromBlockId;toBlockId;thresholdJ;probability\".",
                         "An impact on fromBlockId exceeding thresholdJ has probability (0.0-1.0) chance",
                         "of transforming it into toBlockId. Add/edit/remove lines to add your own rules;",
                         "a block with no matching rule is left alone by compaction (still accumulates",
                         "damage and breaks normally). Malformed lines are ignored.")
                .defineList("compactionRules", java.util.List.of(
                        "minecraft:grass_block;minecraft:dirt;5.0;1.0",
                        "minecraft:farmland;minecraft:dirt;5.0;1.0",
                        "minecraft:podzol;minecraft:dirt;5.0;1.0",
                        "minecraft:mycelium;minecraft:dirt;5.0;1.0",
                        "minecraft:suspicious_sand;minecraft:sand;5.0;1.0",
                        "minecraft:suspicious_gravel;minecraft:gravel;5.0;1.0"
                ), () -> "minecraft:sand;minecraft:sandstone;500.0;0.5", obj -> obj instanceof String);
        b.pop();

        b.comment("Tuning for high-energy impacts that punch through terrain layer by layer",
                  "instead of stopping elastically at first contact.")
                .push("penetrationDynamics");
        ENABLE_PENETRATION = b
                .comment("Penetration dynamics: when a high-energy impact crushes the structure's",
                         "leading face, leftover kinetic energy is re-injected as velocity so heavy",
                         "bodies smash THROUGH material layer by layer (meteor craters) instead of",
                         "stopping elastically at first contact.")
                .define("enablePenetration", true);
        PENETRATION_TRIGGER_J = b
                .comment("Minimum per-contact impact energy (J) to enter penetration mode.",
                         "Ordinary falls stay in the elastic/crack regime. Default 1200.")
                .defineInRange("penetrationTriggerEnergy", 1200.0, 100.0, 1000000.0);
        PENETRATION_MAX_SPEED_MS = b
                .comment("Cap on re-injected speed (m/s). Above ~50 the engine can tunnel through thin",
                         "obstacles; excess energy is lost to crushing/heat. Default 30.")
                .defineInRange("penetrationMaxSpeed", 30.0, 1.0, 50.0);
        PENETRATION_MIN_SPEED_MS = b
                .comment("Minimum contact speed (m/s) to enter penetration mode — the mass-independent",
                         "dynamic/static criterion. A heavy body settling 1 block falls at ~4.4 m/s;",
                         "without this gate its m·g·h energy alone re-triggers penetration every drop,",
                         "so massive bodies ratchet-sink into soft ground forever. Below this speed",
                         "the contact is STATIC bearing: Rapier supports the body, nothing is dug.",
                         "Default 8 (kills ≤2-block settle re-triggers, keeps 20-30 m/s real slams).")
                .defineInRange("penetrationMinSpeed", 8.0, 0.0, 30.0);
        PENETRATION_LOSS_FACTOR = b
                .comment("Energy loss multiplier per crushed layer: E_left = E − Σ(breakJ) × factor.",
                         "Above 1.0 accounts for terrain-side crushing, deformation and heat.",
                         "Higher = shallower penetration. Default 2.0.")
                .defineInRange("penetrationLossFactor", 2.0, 1.0, 20.0);
        PENETRATION_FOOTPRINT_RADIUS = b
                .comment("Footprint radius (blocks) crushed per penetration layer. Large structures",
                         "need their WHOLE leading face and the terrain under it cleared, or they",
                         "rest on intact ground around the single-contact hole and stop dead.",
                         "Each crushed cell charges its breakJ to the energy budget. 0 = patch only.")
                .defineInRange("penetrationFootprintRadius", 8, 0, 16);
        b.pop();

        b.comment("Overall damage intensity preset and the minimum energy needed for any",
                  "damage to be registered at all.")
                .push("balance");
        DAMAGE_PRESET = b
                .comment("Damage intensity preset. Multiplied with damageMultipliers.totalDamageMultiplier.",
                         "MILD=0.3x  CONSERVATIVE=0.6x  DEFAULT=1.0x  INTENSE=2.5x  DRAMATIC=6.0x")
                .defineEnum("damagePreset", DamagePreset.DEFAULT);
        DETECTION_THRESHOLD_J = b
                .comment("Minimum impact energy (J) for any damage to be registered. Impacts below this are ignored entirely.")
                .defineInRange("detectionThreshold", 40.0, 1.0, 500.0);
        b.pop();

        b.comment("Global and per-category damage multipliers, plus physics-structure",
                  "elastic floor, stress-relaxation decay, and impact-speed cap tuning.")
                .push("damageMultipliers");
        TOTAL_DAMAGE_MULTIPLIER = b
                .comment("Global damage multiplier applied to all impacts. Stacks with damagePreset.")
                .defineInRange("totalDamageMultiplier", 1.0, 0.1, 10.0);
        PHYSICS_STRUCTURE_DAMAGE_MULTIPLIER = b
                .comment("Additional damage multiplier applied only to physics structure blocks.")
                .defineInRange("physicsStructureDamageMultiplier", 1.0, 0.1, 10.0);
        WORLD_BLOCK_DAMAGE_MULTIPLIER = b
                .comment("Additional damage multiplier applied only to world blocks.")
                .defineInRange("worldBlockDamageMultiplier", 1.0, 0.1, 10.0);
        SUBLEVEL_ELASTIC_FLOOR = b
                .comment("Elastic floor (fatigue limit): a single hit below this fraction of a block's",
                         "adjusted break threshold causes ZERO damage — purely elastic contact.",
                         "Prevents gentle repeated contacts (waving a structure) from grinding blocks",
                         "to destruction via unbounded accumulation. Default 0.2 (20%).")
                .defineInRange("physicsStructureElasticFloor", 0.2, 0.0, 1.0);
        SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = b
                .comment("Stress relaxation half-life in server ticks: accumulated block damage",
                         "halves every N ticks between hits. Real crashes land within a few ticks",
                         "(unaffected); scattered sub-critical hits fade instead of stacking forever.",
                         "0 disables decay. Default 60 (3 seconds).")
                .defineInRange("physicsStructureDamageHalfLife", 60, 0, 20 * 60 * 10);
        SUBLEVEL_MAX_DELTA_V_MS = b
                .comment("Sanity cap on per-contact velocity change (m/s) for physics structure kImpact.",
                         "Grab-tool constraint yanks and post-break angular whip can report 100+ m/s,",
                         "producing absurd energies (400 000+ J observed). kImpact is clamped to",
                         "0.5 × bodyMass × cap². 0 disables. Default 40 m/s.")
                .defineInRange("physicsStructureMaxImpactSpeed", 40.0, 0.0, 1000.0);
        b.pop();

        b.comment("Integration toggles for other mods.").push("compatibility");
        b.comment("Create mod dynamic structure (contraption) impact interactions:",
                  "anchor damage, train derailment, minecart destruction.")
                .push("create");
        ENABLE_CREATE_INTERACTION = b
                .comment("Whether impacts involving Create mod dynamic structures are processed.",
                         "When enabled, damage is transferred to the anchor block of the dynamic structure",
                         "(the block/entity that, when destroyed, collapses the structure — e.g. bearings, rails).")
                .define("enableContraptionInteraction", true);
        CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER = b
                .comment("Multiplier applied to the auto-computed anchor break threshold.",
                         "Threshold = (Σ block breakThresholdJ + anchor breakThresholdJ) × this value.",
                         "Higher = more durable contraption anchors. Default 1.0 = no scaling.")
                .defineInRange("anchorStrengthMultiplier", 1.0, 0.1, 20.0);
        TRAIN_DERAIL_THRESHOLD_J = b
                .comment("Minimum impact energy (J) required to derail a Create train.",
                         "Default 800 J ≈ a serious collision. Lower = easier to derail.")
                .defineInRange("trainDerailThreshold", 800.0, 1.0, 100000.0);
        MINECART_KILL_THRESHOLD_J = b
                .comment("Minimum impact energy (J) required to destroy a minecart contraption.",
                         "Default 500 J ≈ a moderate-to-heavy collision. Lower = more fragile minecarts.")
                .defineInRange("minecartKillThreshold", 500.0, 1.0, 100000.0);
        b.pop(); // create
        b.pop(); // compatibility

        b.comment("Crack overlay network update timing.").push("crackOverlay");
        CRACK_UPDATE_COOLDOWN_TICKS = b
                .comment("Minimum ticks between crack overlay updates for the same block. Lower = more responsive but more packet traffic.")
                .defineInRange("crackUpdateCooldown", 10, 1, 100);
        b.pop();

        b.comment("Formula coefficients and base thresholds for the damage calculation system.")
                .push("calibration");
        CRACK_COEFFICIENT = b
                .comment("Coefficient in crackThresholdJ = clamp(coefficient × blastResist^exponent, min, max).")
                .defineInRange("crackCoefficient", 15.0, 0.1, 1000.0);
        CRACK_EXPONENT = b
                .comment("Exponent in the crackThresholdJ formula. Higher = threshold grows faster with blast resistance.")
                .defineInRange("crackExponent", 0.6, 0.01, 2.0);
        CRACK_MIN_J = b
                .comment("Floor on crackThresholdJ (J) -- fragile blocks (blastResist<=0) use this directly.")
                .defineInRange("crackMinJ", 3.0, 0.1, 1000.0);
        CRACK_MAX_J = b
                .comment("Ceiling on crackThresholdJ (J) -- very blast-resistant blocks (obsidian, etc.) clamp here.")
                .defineInRange("crackMaxJ", 500.0, 1.0, 100000.0);
        BREAK_BASE_MULTIPLIER = b
                .comment("Base term in breakMult = base + blastResist^exponent × coefficient;",
                         "breakThresholdJ = crackThresholdJ × breakMult.")
                .defineInRange("breakBaseMultiplier", 5.0, 0.1, 100.0);
        BREAK_COEFFICIENT = b
                .comment("Coefficient in the breakMult formula (see breakBaseMultiplier).")
                .defineInRange("breakCoefficient", 3.0, 0.1, 100.0);
        BREAK_EXPONENT = b
                .comment("Exponent in the breakMult formula (see breakBaseMultiplier).")
                .defineInRange("breakExponent", 0.4, 0.01, 2.0);
        STRIKER_YIELD_TOLERANCE = b
                .comment("Material-yield immunity ratio: a victim survives when its own break threshold",
                         "exceeds the striker's break threshold × this value. Used across world block",
                         "damage, physics structure damage, penetration, and structure-vs-structure collisions.")
                .defineInRange("strikerYieldTolerance", 1.5, 1.0, 10.0);
        STRUCTURE_VS_STRUCTURE_SPLIT_COEFFICIENT = b
                .comment("Structure-vs-structure hardness-split coefficient: the softer body's energy share",
                         "is coefficient × otherBreakJ / (myBreakJ + otherBreakJ).")
                .defineInRange("structureVsStructureSplitCoefficient", 2.0, 0.1, 10.0);
        CONFINEMENT_PER_FACE_CAP = b
                .comment("Max contribution of a single neighbor block, relative to the victim's crack threshold.")
                .defineInRange("confinementPerFaceCap", 3.0, 0.1, 20.0);
        CONFINEMENT_DIRECTION_BASE = b
                .comment("Baseline directional confinement weight (0-1) before the impact-direction bonus.")
                .defineInRange("confinementDirectionBase", 0.5, 0.0, 1.0);
        CONFINEMENT_DIRECTION_AMPLITUDE = b
                .comment("Amplitude of the impact-direction bonus added to confinementDirectionBase.")
                .defineInRange("confinementDirectionAmplitude", 0.5, 0.0, 1.0);
        OVERBURDEN_EFFICIENCY = b
                .comment("Fraction of raw hydrostatic overburden pressure (density × gravity × depth)",
                         "that becomes penetration resistance energy. Higher = terrain resists deep",
                         "drilling sooner. Default 0.15 (the undamped physical value of 1.0 stops",
                         "penetration within a few blocks for almost any striker).")
                .defineInRange("overburdenEfficiency", 0.15, 0.01, 1.0);
        SOFT_SOIL_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for SOFT_SOIL blocks (dirt, sand, gravel, etc.),",
                         "before the material break multiplier is applied.")
                .defineInRange("softSoilThresholdJ", 5.0, 0.1, 10000.0);
        BRITTLE_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for BRITTLE blocks (glass, ice, terracotta).")
                .defineInRange("brittleThresholdJ", 15.0, 0.1, 10000.0);
        WOOD_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for WOOD blocks (planks, logs).")
                .defineInRange("woodThresholdJ", 20.0, 0.1, 10000.0);
        STONE_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for STONE blocks (stone, cobblestone, concrete).")
                .defineInRange("stoneThresholdJ", 50.0, 0.1, 10000.0);
        METAL_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for METAL blocks (iron, gold, copper).")
                .defineInRange("metalThresholdJ", 120.0, 0.1, 10000.0);
        HIGH_STRENGTH_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for HIGH_STRENGTH blocks (obsidian, netherite).")
                .defineInRange("highStrengthThresholdJ", 300.0, 0.1, 10000.0);
        GENERIC_THRESHOLD_J = b
                .comment("Base accumulation threshold (J) for unclassified (GENERIC) blocks.")
                .defineInRange("genericThresholdJ", 40.0, 0.1, 10000.0);
        b.pop();

        b.comment("Cosmetic particle/sound feedback rate limiting (CRACKED/CRITICAL states only).")
                .push("feedback");
        FEEDBACK_COOLDOWN_TICKS = b
                .comment("Minimum ticks between feedback events for the same block position.")
                .defineInRange("feedbackCooldownTicks", 10, 0, 200);
        FEEDBACK_BUDGET_PER_TICK = b
                .comment("Max feedback events emitted per server tick across all blocks (prevents spam during chain impacts).")
                .defineInRange("feedbackBudgetPerTick", 16, 1, 1000);
        b.pop();

        b.comment("Per-tool-tier impact energy thresholds, used when Drop Mode is set to By Force.")
                .push("mining");
        NETHERITE_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) that still drops as netherite-pickaxe tier. Only used when dropMode=BY_FORCE.")
                .defineInRange("netheritePickaxeMax", 100.0, 0.0, 100000.0);
        DIAMOND_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for diamond-pickaxe-tier drops.")
                .defineInRange("diamondPickaxeMax", 300.0, 0.0, 100000.0);
        IRON_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for iron-pickaxe-tier drops.")
                .defineInRange("ironPickaxeMax", 800.0, 0.0, 100000.0);
        STONE_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for stone-pickaxe-tier drops.")
                .defineInRange("stonePickaxeMax", 2000.0, 0.0, 100000.0);
        WOODEN_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for wooden-pickaxe-tier drops. Impacts above this yield no drops (bare hand).")
                .defineInRange("woodenPickaxeMax", 5000.0, 0.0, 100000.0);
        b.pop();

        b.comment("Diagnostic logging toggles. High-frequency options generate heavy console output;",
                  "useful when troubleshooting.")
                .push("debug");
        LOG_BLOCK_CALLBACK = b
                .comment("World block collision callback — logs blockId, pos, speed, and thin-block filter",
                         "result on every collision. Extremely high frequency (one line per contact",
                         "face, every physics step); suited to short test sessions.")
                .define("worldBlockCollisionCallback", false);
        LOG_PATH1 = b
                .comment("World block damage — full enqueue + apply pipeline: kImpact, confinement factor,",
                         "blockId, pos, ratio, damage state, and destruction result.")
                .define("worldBlockDamageLog", false);
        LOG_PHASE3A = b
                .comment("Physics structure damage — full pipeline: enqueue (plotCp/bodyLocalCp/com/",
                         "plotCenter), sublevel matching, break attempt (blockId/local coords/ratio/state),",
                         "and destroy-block result.")
                .define("physicsStructureDamageLog", false);
        LOG_PHASE4A = b
                .comment("Dynamic structure damage — full pipeline: contraption search, anchor",
                         "accumulation/break threshold comparison, train derailment (kImpact/threshold/",
                         "track beneath), minecart destruction (vehicle type/kImpact).")
                .define("dynamicStructureDamageLog", false);
        LOG_IMPACT_CAPTURE = b
                .comment("Raw physics capture — internals of the collision-processing pipeline: pair map",
                         "size, whether a world contact was seen, snapshot map size. Extremely high",
                         "frequency, fires every Rapier physics step.")
                .define("impactCaptureDetail", false);
        LOG_BODY_SNAPSHOT = b
                .comment("Rigid body snapshots — logs position, linear/angular velocity, mass, center of",
                         "mass, and rotation quaternion every time a body is sampled. High frequency,",
                         "one line per active body per physics step.")
                .define("rigidBodySnapshot", false);
        LOG_ENERGY_SUMMARY = b
                .comment("Energy summary — one summary line per tick with any collision: event count,",
                         "kImpact range (min/max/sum), and which damage paths were active. Low frequency,",
                         "enabled by default.")
                .define("energySummary", true);
        LOG_PHASE3A_VERBOSE = b
                .comment("Verbose physics structure damage — adds the full per-block calculation path on",
                         "top of physicsStructureDamageLog: mass, mass-normalization factor, hardness, blast resistance,",
                         "per-block crack/break threshold, confinement factor, adjusted threshold, current",
                         "accumulated kImpact, ratio, and damage state. Requires physicsStructureDamageLog also enabled.",
                         "Disabled by default.")
                .define("physicsStructureDamageVerbose", false);
        b.pop(); // debug

        b.pop(); // advanced

        SPEC = b.build();
    }

    // ── Preset ────────────────────────────────────────────────────────────────

    public enum DamagePreset implements net.neoforged.neoforge.common.TranslatableEnum {
        MILD(0.3),
        CONSERVATIVE(0.6),
        DEFAULT(1.0),
        INTENSE(2.5),
        DRAMATIC(6.0);

        private final double multiplier;

        DamagePreset(double multiplier) {
            this.multiplier = multiplier;
        }

        public double multiplier() {
            return multiplier;
        }

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable(
                    "true_impact.damagePreset." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public enum DropMode implements net.neoforged.neoforge.common.TranslatableEnum {
        DISABLED,  // no drops from impacts
        BY_FORCE,  // tool tier scales with impact energy (see [advanced.mining])
        ALL;       // always drop as if mined by netherite pickaxe

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable(
                    "true_impact.dropMode." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    // ── Runtime helpers ───────────────────────────────────────────────────────

    /**
     * Effective damage multiplier = preset × total multiplier.
     * Apply this to all kImpact values before threshold comparison.
     */
    public static double effectiveMultiplier() {
        return DAMAGE_PRESET.get().multiplier() * TOTAL_DAMAGE_MULTIPLIER.get();
    }

    /** Effective multiplier for physics structure damage. */
    public static double effectivePhysicsStructureMultiplier() {
        return effectiveMultiplier() * PHYSICS_STRUCTURE_DAMAGE_MULTIPLIER.get();
    }

    /** Effective multiplier for world block damage. */
    public static double effectiveWorldBlockMultiplier() {
        return effectiveMultiplier() * WORLD_BLOCK_DAMAGE_MULTIPLIER.get();
    }

    /**
     * Per-material break multiplier from config.
     * Returns 1.0 for SOFT_SOIL and GENERIC (no config override for those).
     */
    public static double materialBreakMultiplier(
            io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case BRITTLE       -> BRITTLE_BREAK_MULTIPLIER.get();
            case WOOD          -> WOOD_BREAK_MULTIPLIER.get();
            case STONE         -> STONE_BREAK_MULTIPLIER.get();
            case METAL         -> METAL_BREAK_MULTIPLIER.get();
            case HIGH_STRENGTH -> HIGH_STRENGTH_BREAK_MULTIPLIER.get();
            default            -> 1.0;
        };
    }
}
