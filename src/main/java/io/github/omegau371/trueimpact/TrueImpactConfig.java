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
 *   [advanced.penetrationDynamics]   — penetration dynamics tuning
 *   [advanced.balance]               — damage preset + detection threshold
 *   [advanced.damageMultipliers]     — global/per-category damage multipliers
 *   [advanced.compatibility.create]  — Create mod integration toggles
 *   [advanced.crackOverlay]          — crack overlay timing
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

        b.push("general");
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
                .comment("Master switch for whether world blocks are affected by impacts at all",
                         "(damage accumulation, crack overlay, destruction, and surface compaction",
                         "such as grass -> dirt). Mirrors physicsStructureDamage for physics structures.")
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
        b.pop();

        b.push("advanced");

        b.push("materials");
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

        b.push("penetrationDynamics");
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
                .comment("Cap on re-injected speed (m/s). Keep below ~50 to avoid engine tunneling",
                         "through thin obstacles; excess energy is lost to crushing/heat. Default 30.")
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

        b.push("balance");
        DAMAGE_PRESET = b
                .comment("Damage intensity preset. Multiplied with damageMultipliers.totalDamageMultiplier.",
                         "MILD=0.3x  CONSERVATIVE=0.6x  DEFAULT=1.0x  INTENSE=2.5x  DRAMATIC=6.0x")
                .defineEnum("damagePreset", DamagePreset.DEFAULT);
        DETECTION_THRESHOLD_J = b
                .comment("Minimum impact energy (J) for any damage to be registered. Impacts below this are ignored entirely.")
                .defineInRange("detectionThreshold", 40.0, 1.0, 500.0);
        b.pop();

        b.push("damageMultipliers");
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

        b.push("compatibility");
        b.push("create");
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

        b.push("crackOverlay");
        CRACK_UPDATE_COOLDOWN_TICKS = b
                .comment("Minimum ticks between crack overlay updates for the same block. Lower = more responsive but more packet traffic.")
                .defineInRange("crackUpdateCooldown", 10, 1, 100);
        b.pop();

        b.push("mining");
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

        b.push("debug");
        LOG_BLOCK_CALLBACK = b
                .comment("World block collision callback — logs blockId, pos, speed, and thin-block filter",
                         "result on every collision. WARNING: extremely high frequency (one line per contact",
                         "face, every physics step). Only enable for short test sessions.")
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
                         "size, whether a world contact was seen, snapshot map size. WARNING: extremely",
                         "high frequency, fires every Rapier physics step.")
                .define("impactCaptureDetail", false);
        LOG_BODY_SNAPSHOT = b
                .comment("Rigid body snapshots — logs position, linear/angular velocity, mass, center of",
                         "mass, and rotation quaternion every time a body is sampled. WARNING: high",
                         "frequency, one line per active body per physics step.")
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

    public enum DamagePreset {
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
    }

    public enum DropMode {
        DISABLED,  // no drops from impacts
        BY_FORCE,  // tool tier scales with impact energy (see [advanced.mining])
        ALL        // always drop as if mined by netherite pickaxe
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
