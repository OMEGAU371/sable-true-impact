package io.github.omegau371.trueimpact;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge SERVER config for True Impact.
 * Stored per-world under saves/<world>/serverconfig/true_impact-server.toml.
 *
 * Structure:
 *   [总体]           — basic toggles for casual users
 *   [高级.材质]       — per-material break multipliers
 *   [高级.物理结构]   — physics-structure-specific settings
 *   [高级.平衡]       — damage preset + detection threshold
 *   [高级.受伤倍率]   — global/per-category damage multipliers
 *   [高级.兼容性]     — Create mod integration toggles
 *   [高级.裂纹]       — crack overlay timing
 *   [高级.掘取]       — per-tool-tier impact energy thresholds (used when 掉落模式=BY_FORCE)
 */
public final class TrueImpactConfig {

    private TrueImpactConfig() {}

    public static final ModConfigSpec SPEC;

    // ── [总体] ────────────────────────────────────────────────────────────────

    public static final ModConfigSpec.BooleanValue ENABLE_BLOCK_BREAKING;
    public static final ModConfigSpec.BooleanValue ENABLE_CRACK_OVERLAY;
    public static final ModConfigSpec.BooleanValue ENABLE_PHYSICS_STRUCTURE_DAMAGE;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_BLOCK_DAMAGE;
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

    public static final ModConfigSpec.BooleanValue ENABLE_STRUCTURE_VS_STRUCTURE;
    /** Phase 3C 贯穿动力学总开关。 */
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
    /** Phase 3A 超详细：每个方块完整计算路径（硬度/阈值/围压/比率）。需同时开启物理结构损伤。 */
    public static final ModConfigSpec.BooleanValue LOG_PHASE3A_VERBOSE;
    /** Phase 3A 弹性下限：单次冲击低于「破坏阈值 × 此比例」时完全无损伤（疲劳极限）。 */
    public static final ModConfigSpec.DoubleValue SUBLEVEL_ELASTIC_FLOOR;
    /** Phase 3A 应力松弛半衰期（tick）：累积损伤每 N tick 减半；0 = 不衰减。 */
    public static final ModConfigSpec.IntValue SUBLEVEL_DAMAGE_HALF_LIFE_TICKS;
    /** Phase 3A 单次接触速度变化上限（m/s）：封杀抓取约束猛拽产生的非物理能量尖峰。 */
    public static final ModConfigSpec.DoubleValue SUBLEVEL_MAX_DELTA_V_MS;

    /** 每次 Sable sable$onCollision 触发：blockId、pos、speed、薄方块过滤结果。极高频。 */
    public static final ModConfigSpec.BooleanValue LOG_BLOCK_CALLBACK;
    /** Path 1 入队 + 应用：kImpact、confinement、blockId、pos、ratio、damageState、破坏结果。 */
    public static final ModConfigSpec.BooleanValue LOG_PATH1;
    /** Phase 3A 物理结构：入队（plotCp/bodyLocalCp/com）、tryBreak（blockId/local/ratio/state）、destroyBlock 结果。 */
    public static final ModConfigSpec.BooleanValue LOG_PHASE3A;
    /** Phase 4A 动态结构：contraption 搜索、锚点积累/破坏、列车脱轨、矿车摧毁。 */
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
        ENABLE_PHYSICS_STRUCTURE_DAMAGE = b
                .comment("Whether physics structures take damage when colliding with terrain.")
                .define("物理结构受损", true);
        ENABLE_WORLD_BLOCK_DAMAGE = b
                .comment("Whether world blocks take damage from impacts.")
                .define("世界方块受损", true);
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
        ENABLE_STRUCTURE_VS_STRUCTURE = b
                .comment("Whether physics structures take damage when colliding with each other.")
                .define("互相碰撞受损", true);
        ENABLE_PENETRATION = b
                .comment("Phase 3C penetration dynamics: when a high-energy impact crushes the",
                         "structure's leading face, leftover kinetic energy is re-injected as velocity",
                         "so heavy bodies smash THROUGH material layer by layer (meteor craters)",
                         "instead of stopping elastically at first contact.")
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
        b.pop();

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
                .comment("世界方块碰撞回调 — 每次 sable$onCollision 触发时输出：blockId、pos、speed、是否为薄方块。",
                         "警告: 极高频，每次物理步每个接触面都会输出一行。仅在短时间测试中开启。")
                .define("世界方块碰撞回调", false);
        LOG_PATH1 = b
                .comment("世界方块损伤 — Path 1 入队 + 应用全过程：kImpact、confinement 系数、blockId、pos、ratio、damageState、破坏结果。")
                .define("世界方块损伤", false);
        LOG_PHASE3A = b
                .comment("物理结构损伤 — Phase 3A 完整流水线：入队（plotCp/bodyLocalCp/com/plotCenter）、",
                         "匹配 sublevel、tryBreak（blockId/localXYZ/ratio/damageState）、destroyBlock 结果。")
                .define("物理结构损伤", false);
        LOG_PHASE4A = b
                .comment("动态结构损伤 — Phase 4A 完整流水线：contraption 搜索、锚点积累/破坏阈值对比、",
                         "列车脱轨（kImpact/threshold/脚下轨道）、矿车摧毁（vehicle类型/kImpact）。")
                .define("动态结构损伤", false);
        LOG_IMPACT_CAPTURE = b
                .comment("物理捕获详情 — SableImpactCapture.process() 内部：pairMap 条目数、sawWorldContact、",
                         "phase3aSnapMap 大小、每对接触的 kImpact。警告: 极高频，每次 Rapier 物理步触发。")
                .define("物理捕获详情", false);
        LOG_BODY_SNAPSHOT = b
                .comment("刚体快照 — 每次采样 BodySnapshot 时输出：position、linVel、angVel、mass、com、rotation 四元数。",
                         "警告: 高频，每次物理步每个活动刚体都会输出。")
                .define("刚体快照", false);
        LOG_ENERGY_SUMMARY = b
                .comment("能量汇总 — 每次 tick 有碰撞事件时输出汇总一行：事件总数、kImpact 范围（min/max/sum）、",
                         "被激活的路径（Path1/3A/4A）。低频，默认开启。")
                .define("能量汇总", true);
        LOG_PHASE3A_VERBOSE = b
                .comment("超详细物理结构损伤 — 在「物理结构损伤」之上输出完整计算路径：",
                         "massKpg、质量归一化系数、硬度、爆炸抗性、单块裂缝/破坏阈值、围压系数、",
                         "调整后阈值、当前累积 kImpact、比率、损伤状态。",
                         "需同时开启「物理结构损伤」。默认关闭。")
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
