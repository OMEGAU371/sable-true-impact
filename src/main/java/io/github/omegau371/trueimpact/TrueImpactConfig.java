package io.github.omegau371.trueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge SERVER config for True Impact.
 * Stored per-world under saves/<world>/serverconfig/true_impact-server.toml.
 *
 * Structure:
 *   [总体]                 — basic toggles for casual users (block breaking, crack overlay,
 *                            world/structure damage, structure-vs-structure damage, drop mode)
 *   [高级.材质]             — per-material break multipliers
 *   [高级.物理结构]         — penetration dynamics tuning
 *   [高级.平衡]             — damage preset + detection threshold
 *   [高级.受伤倍率]         — global/per-category damage multipliers
 *   [高级.兼容性.Create]    — Create mod integration toggles
 *   [高级.裂纹]             — crack overlay timing
 *   [高级.掘取]             — per-tool-tier impact energy thresholds (used when 掉落模式=BY_FORCE)
 */
public final class TrueImpactConfig {

    private TrueImpactConfig() {}

    public static final ModConfigSpec SPEC;

    // ── [总体] ────────────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_CRACK_OVERLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_BLOCK_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_PHYSICS_STRUCTURE_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_STRUCTURE_VS_STRUCTURE;
    public static final ModConfigSpec.EnumValue<DropMode> DROP_MODE;

    // ── [高级.掘取] ───────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue NETHERITE_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue DIAMOND_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue IRON_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue STONE_PICKAXE_MAX_J;
    public static final ModConfigSpec.DoubleValue WOODEN_PICKAXE_MAX_J;

    // ── [高级.材质] ───────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue BRITTLE_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WOOD_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue STONE_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue METAL_BREAK_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue HIGH_STRENGTH_BREAK_MULTIPLIER;

    // ── [高级.物理结构] ───────────────────────────────────────────────────────

    /** Master switch for penetration dynamics. */
    public static final ModConfigSpec.BooleanValue ENABLE_PENETRATION;
    /** 进入贯穿模式的单次接触能量下限（J）。 */
    public static final ModConfigSpec.DoubleValue PENETRATION_TRIGGER_J;
    /** 贯穿再注入速度上限（m/s），须低于引擎隧穿阈值。 */
    public static final ModConfigSpec.DoubleValue PENETRATION_MAX_SPEED_MS;
    /** 进入贯穿模式的最低接触速度（m/s）——与质量无关的动态/静态判据。 */
    public static final ModConfigSpec.DoubleValue PENETRATION_MIN_SPEED_MS;
    /** 每层压碎的能量损耗系数（>1 计入地形侧与热耗散）。 */
    public static final ModConfigSpec.DoubleValue PENETRATION_LOSS_FACTOR;
    /** 贯穿每层压碎的足印半径（格）；0=仅接触补丁。 */
    public static final ModConfigSpec.IntValue PENETRATION_FOOTPRINT_RADIUS;

    // ── [高级.平衡] ───────────────────────────────────────────────────────────

    public static final ModConfigSpec.EnumValue<DamagePreset> DAMAGE_PRESET;
    public static final ModConfigSpec.DoubleValue DETECTION_THRESHOLD_J;

    // ── [高级.受伤倍率] ───────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue TOTAL_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PHYSICS_STRUCTURE_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue WORLD_BLOCK_DAMAGE_MULTIPLIER;

    // ── [高级.兼容性] ─────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue ENABLE_CREATE_INTERACTION;
    public static final ModConfigSpec.BooleanValue DYNAMIC_STRUCTURE_DAMAGES_PHYSICS_STRUCTURE;
    public static final ModConfigSpec.BooleanValue DYNAMIC_STRUCTURE_DAMAGES_WORLD_BLOCK;
    public static final ModConfigSpec.DoubleValue CONTRAPTION_DAMAGE_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue TRAIN_DERAIL_THRESHOLD_J;
    public static final ModConfigSpec.DoubleValue MINECART_KILL_THRESHOLD_J;

    // ── [高级.裂纹] ───────────────────────────────────────────────────────────

    public static final ModConfigSpec.IntValue CRACK_UPDATE_COOLDOWN_TICKS;

    // ── [高级.调试] ───────────────────────────────────────────────────────────
    /** Verbose physics structure damage: full per-block calc path (hardness/threshold/confinement/ratio). Requires 物理结构损伤 also enabled. */
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
    /** SableImpactCapture.process() 内部：pairMap 条目数、sawWorldContact、phase3aSnapMap 大小。极高频。 */
    public static final ModConfigSpec.BooleanValue LOG_IMPACT_CAPTURE;
    /** 每次读取 BodySnapshot：pos、linVel、com、mass、rotation。高频。 */
    public static final ModConfigSpec.BooleanValue LOG_BODY_SNAPSHOT;
    /** 低频汇总：每次 tick 有碰撞时输出事件数量和 kImpact 总量。默认开启。 */
    public static final ModConfigSpec.BooleanValue LOG_ENERGY_SUMMARY;

    // ── Build ─────────────────────────────────────────────────────────────────

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("总体");
        ENABLE_BLOCK_BREAKING = b
                .comment("Whether physical impacts can destroy blocks.")
                .define("方块破坏", true);
        ENABLE_CRACK_OVERLAY = b
                .comment("Whether crack overlays are shown on damaged blocks.")
                .define("裂纹贴图", true);
        ENABLE_WORLD_BLOCK_DAMAGE = b
                .comment("Whether world blocks take damage from impacts.")
                .define("世界方块受损", true);
        ENABLE_PHYSICS_STRUCTURE_DAMAGE = b
                .comment("Whether physics structures take damage when colliding with terrain.")
                .define("物理结构受损", true);
        ENABLE_STRUCTURE_VS_STRUCTURE = b
                .comment("Whether physics structures take damage when colliding with each other.")
                .define("互相碰撞受损", true);
        DROP_MODE = b
                .comment("Block drop mode: DISABLED=no drops, BY_FORCE=drops depend on impact energy (see [高级.掘取]), ALL=always drop as if mined by netherite pickaxe.")
                .defineEnum("掉落模式", DropMode.BY_FORCE);
        b.pop();

        b.push("高级");

        b.push("材质");
        BRITTLE_BREAK_MULTIPLIER = b
                .comment("Break multiplier for brittle materials (glass, ice, terracotta). Base threshold: 45 J.")
                .defineInRange("易碎材质破坏倍率", 1.0, 0.1, 20.0);
        WOOD_BREAK_MULTIPLIER = b
                .comment("Break multiplier for wood materials (planks, logs). Base threshold: 100 J.")
                .defineInRange("木质材质破坏倍率", 1.0, 0.1, 20.0);
        STONE_BREAK_MULTIPLIER = b
                .comment("Break multiplier for stone materials (stone, cobblestone, concrete). Base threshold: 500 J.")
                .defineInRange("石质材质破坏倍率", 1.0, 0.1, 20.0);
        METAL_BREAK_MULTIPLIER = b
                .comment("Break multiplier for metal materials (iron, gold, copper). Base threshold: 1800 J.")
                .defineInRange("金属材质破坏倍率", 1.0, 0.1, 20.0);
        HIGH_STRENGTH_BREAK_MULTIPLIER = b
                .comment("Break multiplier for high-strength materials (obsidian, netherite). Base threshold: 7500 J.")
                .defineInRange("高强度材质破坏倍率", 1.0, 0.1, 20.0);
        b.pop();

        b.push("物理结构");
        ENABLE_PENETRATION = b
                .comment("Penetration dynamics: when a high-energy impact crushes the structure's",
                         "leading face, leftover kinetic energy is re-injected as velocity so heavy",
                         "bodies smash THROUGH material layer by layer (meteor craters) instead of",
                         "stopping elastically at first contact.")
                .define("启用贯穿动力学", true);
        PENETRATION_TRIGGER_J = b
                .comment("Minimum per-contact impact energy (J) to enter penetration mode.",
                         "Ordinary falls stay in the elastic/crack regime. Default 1200.")
                .defineInRange("贯穿触发能量", 1200.0, 100.0, 1000000.0);
        PENETRATION_MAX_SPEED_MS = b
                .comment("Cap on re-injected speed (m/s). Keep below ~50 to avoid engine tunneling",
                         "through thin obstacles; excess energy is lost to crushing/heat. Default 30.")
                .defineInRange("贯穿再注入速度上限", 30.0, 1.0, 50.0);
        PENETRATION_MIN_SPEED_MS = b
                .comment("Minimum contact speed (m/s) to enter penetration mode — the mass-independent",
                         "dynamic/static criterion. A heavy body settling 1 block falls at ~4.4 m/s;",
                         "without this gate its m·g·h energy alone re-triggers penetration every drop,",
                         "so massive bodies ratchet-sink into soft ground forever. Below this speed",
                         "the contact is STATIC bearing: Rapier supports the body, nothing is dug.",
                         "Default 8 (kills ≤2-block settle re-triggers, keeps 20-30 m/s real slams).")
                .defineInRange("贯穿最低触发速度", 8.0, 0.0, 30.0);
        PENETRATION_LOSS_FACTOR = b
                .comment("Energy loss multiplier per crushed layer: E_left = E − Σ(breakJ) × factor.",
                         "Above 1.0 accounts for terrain-side crushing, deformation and heat.",
                         "Higher = shallower penetration. Default 2.0.")
                .defineInRange("贯穿能量损耗系数", 2.0, 1.0, 20.0);
        PENETRATION_FOOTPRINT_RADIUS = b
                .comment("Footprint radius (blocks) crushed per penetration layer. Large structures",
                         "need their WHOLE leading face and the terrain under it cleared, or they",
                         "rest on intact ground around the single-contact hole and stop dead.",
                         "Each crushed cell charges its breakJ to the energy budget. 0 = patch only.")
                .defineInRange("贯穿足印半径", 8, 0, 16);
        b.pop();

        b.push("平衡");
        DAMAGE_PRESET = b
                .comment("Damage intensity preset. Multiplied with 受伤倍率.总受伤倍率.",
                         "MILD=0.3x  CONSERVATIVE=0.6x  DEFAULT=1.0x  INTENSE=2.5x  DRAMATIC=6.0x")
                .defineEnum("伤害预设", DamagePreset.DEFAULT);
        DETECTION_THRESHOLD_J = b
                .comment("Minimum impact energy (J) for any damage to be registered. Impacts below this are ignored entirely.")
                .defineInRange("最低检测阈值", 40.0, 1.0, 500.0);
        b.pop();

        b.push("受伤倍率");
        TOTAL_DAMAGE_MULTIPLIER = b
                .comment("Global damage multiplier applied to all impacts. Stacks with 伤害预设.")
                .defineInRange("总受伤倍率", 1.0, 0.1, 10.0);
        PHYSICS_STRUCTURE_DAMAGE_MULTIPLIER = b
                .comment("Additional damage multiplier applied only to physics structure blocks.")
                .defineInRange("物理结构受伤倍率", 1.0, 0.1, 10.0);
        WORLD_BLOCK_DAMAGE_MULTIPLIER = b
                .comment("Additional damage multiplier applied only to world blocks.")
                .defineInRange("世界方块受伤倍率", 1.0, 0.1, 10.0);
        SUBLEVEL_ELASTIC_FLOOR = b
                .comment("Elastic floor (fatigue limit): a single hit below this fraction of a block's",
                         "adjusted break threshold causes ZERO damage — purely elastic contact.",
                         "Prevents gentle repeated contacts (waving a structure) from grinding blocks",
                         "to destruction via unbounded accumulation. Default 0.2 (20%).")
                .defineInRange("物理结构弹性下限", 0.2, 0.0, 1.0);
        SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = b
                .comment("Stress relaxation half-life in server ticks: accumulated block damage",
                         "halves every N ticks between hits. Real crashes land within a few ticks",
                         "(unaffected); scattered sub-critical hits fade instead of stacking forever.",
                         "0 disables decay. Default 60 (3 seconds).")
                .defineInRange("物理结构损伤半衰期", 60, 0, 20 * 60 * 10);
        SUBLEVEL_MAX_DELTA_V_MS = b
                .comment("Sanity cap on per-contact velocity change (m/s) for physics structure kImpact.",
                         "Grab-tool constraint yanks and post-break angular whip can report 100+ m/s,",
                         "producing absurd energies (400 000+ J observed). kImpact is clamped to",
                         "0.5 × bodyMass × cap². 0 disables. Default 40 m/s.")
                .defineInRange("物理结构冲击速度上限", 40.0, 0.0, 1000.0);
        b.pop();

        b.push("兼容性");
        b.push("Create");
        ENABLE_CREATE_INTERACTION = b
                .comment("Whether impacts involving Create mod dynamic structures are processed.",
                         "When enabled, damage is transferred to the anchor block of the dynamic structure",
                         "(the block/entity that, when destroyed, collapses the structure — e.g. bearings, rails).")
                .define("对动态结构生效", true);
        DYNAMIC_STRUCTURE_DAMAGES_PHYSICS_STRUCTURE = b
                .comment("Whether a Create dynamic structure can damage a physics structure it collides with.")
                .define("动态结构损坏物理结构", true);
        DYNAMIC_STRUCTURE_DAMAGES_WORLD_BLOCK = b
                .comment("Whether a Create dynamic structure can damage world blocks it collides with.")
                .define("动态结构损坏世界方块", true);
        CONTRAPTION_DAMAGE_THRESHOLD_J = b
                .comment("Minimum impact energy (J) required to destroy a dynamic structure's anchor block.",
                         "Default 200 J ≈ a moderate collision. Lower = more fragile contraptions.")
                .defineInRange("动态结构锚点破坏阈值", 200.0, 1.0, 100000.0);
        CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER = b
                .comment("Multiplier applied to the auto-computed anchor break threshold.",
                         "Threshold = (Σ block breakThresholdJ + anchor breakThresholdJ) × this value.",
                         "Higher = more durable contraption anchors. Default 1.0 = no scaling.")
                .defineInRange("锚点强度倍率", 1.0, 0.1, 20.0);
        TRAIN_DERAIL_THRESHOLD_J = b
                .comment("Minimum impact energy (J) required to derail a Create train.",
                         "Default 800 J ≈ a serious collision. Lower = easier to derail.")
                .defineInRange("列车脱轨阈值", 800.0, 1.0, 100000.0);
        MINECART_KILL_THRESHOLD_J = b
                .comment("Minimum impact energy (J) required to destroy a minecart contraption.",
                         "Default 500 J ≈ a moderate-to-heavy collision. Lower = more fragile minecarts.")
                .defineInRange("矿车摧毁阈值", 500.0, 1.0, 100000.0);
        b.pop(); // Create
        b.pop(); // 兼容性

        b.push("裂纹");
        CRACK_UPDATE_COOLDOWN_TICKS = b
                .comment("Minimum ticks between crack overlay updates for the same block. Lower = more responsive but more packet traffic.")
                .defineInRange("裂纹更新间隔", 10, 1, 100);
        b.pop();

        b.push("掘取");
        NETHERITE_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) that still drops as netherite-pickaxe tier. Only used when 掉落模式=BY_FORCE.")
                .defineInRange("下界合金镐上限", 100.0, 0.0, 100000.0);
        DIAMOND_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for diamond-pickaxe-tier drops.")
                .defineInRange("钻石镐上限", 300.0, 0.0, 100000.0);
        IRON_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for iron-pickaxe-tier drops.")
                .defineInRange("铁镐上限", 800.0, 0.0, 100000.0);
        STONE_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for stone-pickaxe-tier drops.")
                .defineInRange("石镐上限", 2000.0, 0.0, 100000.0);
        WOODEN_PICKAXE_MAX_J = b
                .comment("Max impact energy (J) for wooden-pickaxe-tier drops. Impacts above this yield no drops (bare hand).")
                .defineInRange("木镐上限", 5000.0, 0.0, 100000.0);
        b.pop();

        b.push("调试");
        LOG_BLOCK_CALLBACK = b
                .comment("World block collision callback — logs blockId, pos, speed, and thin-block filter",
                         "result on every collision. WARNING: extremely high frequency (one line per contact",
                         "face, every physics step). Only enable for short test sessions.")
                .define("世界方块碰撞回调", false);
        LOG_PATH1 = b
                .comment("World block damage — full enqueue + apply pipeline: kImpact, confinement factor,",
                         "blockId, pos, ratio, damage state, and destruction result.")
                .define("世界方块损伤", false);
        LOG_PHASE3A = b
                .comment("Physics structure damage — full pipeline: enqueue (plotCp/bodyLocalCp/com/",
                         "plotCenter), sublevel matching, break attempt (blockId/local coords/ratio/state),",
                         "and destroy-block result.")
                .define("物理结构损伤", false);
        LOG_PHASE4A = b
                .comment("Dynamic structure damage — full pipeline: contraption search, anchor",
                         "accumulation/break threshold comparison, train derailment (kImpact/threshold/",
                         "track beneath), minecart destruction (vehicle type/kImpact).")
                .define("动态结构损伤", false);
        LOG_IMPACT_CAPTURE = b
                .comment("Raw physics capture — internals of the collision-processing pipeline: pair map",
                         "size, whether a world contact was seen, snapshot map size. WARNING: extremely",
                         "high frequency, fires every Rapier physics step.")
                .define("物理捕获详情", false);
        LOG_BODY_SNAPSHOT = b
                .comment("Rigid body snapshots — logs position, linear/angular velocity, mass, center of",
                         "mass, and rotation quaternion every time a body is sampled. WARNING: high",
                         "frequency, one line per active body per physics step.")
                .define("刚体快照", false);
        LOG_ENERGY_SUMMARY = b
                .comment("Energy summary — one summary line per tick with any collision: event count,",
                         "kImpact range (min/max/sum), and which damage paths were active. Low frequency,",
                         "enabled by default.")
                .define("能量汇总", true);
        LOG_PHASE3A_VERBOSE = b
                .comment("Verbose physics structure damage — adds the full per-block calculation path on",
                         "top of 物理结构损伤: mass, mass-normalization factor, hardness, blast resistance,",
                         "per-block crack/break threshold, confinement factor, adjusted threshold, current",
                         "accumulated kImpact, ratio, and damage state. Requires 物理结构损伤 also enabled.",
                         "Disabled by default.")
                .define("超详细物理结构损伤", false);
        b.pop(); // 调试

        b.pop(); // 高级

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
        DISABLED,  // 关闭 — no drops from impacts
        BY_FORCE,  // 因力而掘 — tool tier scales with impact energy (see [高级.掘取])
        ALL        // 全部开启 — always drop as if mined by netherite pickaxe
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
