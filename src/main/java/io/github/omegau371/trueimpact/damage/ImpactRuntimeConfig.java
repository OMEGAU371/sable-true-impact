package io.github.omegau371.trueimpact.damage;

/**
 * Production runtime configuration for the TrueImpact damage pipeline.
 *
 * These flags control whether real game effects are applied.
 * They are INDEPENDENT of DiagnosticConfig -- debug logging state must never
 * determine whether block damage occurs.
 *
 * All fields default to true (effects enabled). Set to false to disable
 * specific effect categories for testing or compatibility.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class ImpactRuntimeConfig {

    private ImpactRuntimeConfig() {}

    /**
     * Master switch for all block mutation effects.
     * When false: no block states are changed; all flush events are SKIP_EFFECTS_DISABLED.
     * Default: true.
     */
    public static volatile boolean APPLY_BLOCK_EFFECTS = true;

    /**
     * Controls cosmetic damage feedback (particles/sounds) for CRACKED/CRITICAL blocks.
     * When false: DamageFeedbackTracker.shouldEmit() always returns false.
     * Rate limiting and state classification still occur; only the MC-side emission is suppressed.
     * Default: true.
     */
    public static volatile boolean ENABLE_DAMAGE_FEEDBACK = true;

    /**
     * Controls whether debris items are spawned for STONE/GENERIC blocks at CRITICAL state.
     * When false: no ItemEntity is spawned; MaterialResponsePlanner plans still computed.
     * Debris deduplication (one drop per block) applies regardless of this flag.
     * Default: false (disabled in Phase 2E hotfix; reserved for Phase 2F).
     */
    public static volatile boolean ENABLE_DEBRIS_DROPS = false;

    /**
     * Whether damage persists and accumulates across multiple hits, with crack overlays
     * (progress 0..9, via CrackOverlayTracker) showing intermediate progress.
     * When false: no accumulator state is kept. Each hit is judged in isolation -- if it
     * alone reaches the break threshold the block is destroyed outright (subject to
     * ENABLE_BLOCK_BREAKING), otherwise nothing happens and no crack is ever shown.
     * Consumed directly by BlockDamageAccumulator/SublevelDamageAccumulator (single-hit
     * branch) and by the Phase 3A crack-overlay call site; CrackOverlayTracker.tryUpdate
     * (Path 1 + Phase 4A) also checks it as a backstop.
     * Default: true.
     */
    public static volatile boolean ENABLE_DAMAGE_ACCUMULATION = true;

    /**
     * Phase 2F: controls actual block destruction for CRITICAL blocks.
     * When true: futureBreakEligible plans (STONE, WOOD, METAL, HIGH_STRENGTH, GENERIC at CRITICAL)
     * result in level.destroyBlock(pos, true) -- block is removed and drops its loot.
     * Destruction is deferred to ServerTickEvent.Post (never inside a Rapier substep).
     * First-hit-only dedup via MaterialResponsePlanner.markBreakScheduled().
     * When ENABLE_BLOCK_BREAKING is true, ENABLE_DEBRIS_DROPS is ignored for those blocks
     * (destroyBlock already drops loot; separate debris item would be double-drop).
     * Default: false (safe; enable to activate block destruction).
     */
    public static volatile boolean ENABLE_BLOCK_BREAKING = true;

    /**
     * Controls whether physics structures (Sable sublevels) take damage from impacts.
     * When false: DeferredSublevelDamageQueue is drained but no damage is applied.
     * Default: true.
     */
    public static volatile boolean ENABLE_PHYSICS_STRUCTURE_DAMAGE = true;

    /**
     * Controls mutual damage between two physics structures (active-vs-active pairs).
     * When true: each ACTIVE_IMPACT pair enqueues sublevel damage for BOTH bodies with
     * the pair's dissipated energy 0.5·μ·v_rel². Synced from TrueImpactConfig
     * [高级.物理结构] 互相碰撞受损. Default: true.
     */
    public static volatile boolean ENABLE_STRUCTURE_VS_STRUCTURE = true;

    /**
     * Phase 4A: controls whether Create contraption anchors are destroyed on high-speed impact.
     * Synced from TrueImpactConfig.ENABLE_CREATE_INTERACTION on config load/reload.
     * Static field avoids config-not-loaded exceptions in GameTest environment.
     * Default: true.
     */
    public static volatile boolean ENABLE_CREATE_INTERACTION = true;

    /**
     * Phase 4A: multiplier applied to the auto-computed anchor break threshold.
     * Synced from TrueImpactConfig.CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER.
     * Default: 1.0 (no scaling).
     */
    public static volatile double CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER = 1.0;
    public static volatile double TRAIN_DERAIL_THRESHOLD_J  = 800.0;
    public static volatile double MINECART_KILL_THRESHOLD_J = 500.0;

    // ── Debug logging flags ───────────────────────────────────────────────────
    // Synced from TrueImpactConfig.[高级.调试] on load/reload.
    // All default false (silent) except LOG_ENERGY_SUMMARY which defaults true (low-frequency).

    /** sable$onCollision 每次触发：blockId、pos、speed、薄方块过滤。极高频。 */
    public static volatile boolean LOG_BLOCK_CALLBACK   = false;
    /** Path 1 世界方块入队 + 应用：kImpact、confinement、ratio、damageState、破坏结果。 */
    public static volatile boolean LOG_PATH1            = false;
    /** Phase 3A 物理结构：enqueue/tryBreak/destroyBlock 全流水线。 */
    public static volatile boolean LOG_PHASE3A          = false;
    /** Phase 4A 动态结构（含列车/矿车）：AABB 搜索、锚点积累、脱轨、摧毁。 */
    public static volatile boolean LOG_PHASE4A          = false;
    /** SableImpactCapture.process() 内部：pairMap 大小、sawWorldContact、phase3aSnapMap。极高频。 */
    public static volatile boolean LOG_IMPACT_CAPTURE   = false;
    /** BodySnapshot 采样：pos、vel、mass、com、rotation。高频。 */
    public static volatile boolean LOG_BODY_SNAPSHOT    = false;
    /** 每 tick 有碰撞时输出汇总一行（事件数/kImpact range/活动路径）。默认开。 */
    public static volatile boolean LOG_ENERGY_SUMMARY   = true;

    // ── Phase 3B: stress fracture ─────────────────────────────────────────────

    /**
     * Phase 3B: enables BFS stress propagation after a sublevel block is destroyed.
     * When true: secondary blocks that exceed their stress limit are also destroyed,
     * and the sublevel is split into disconnected components via SubLevelAssemblyHelper.
     * Default: true (stress fracture active when ENABLE_PHYSICS_STRUCTURE_DAMAGE is also true).
     */
    public static volatile boolean ENABLE_STRESS_FRACTURE = true;

    /** Phase 3B: log stress propagation details (candidates, secondary breaks, splits). */
    public static volatile boolean LOG_STRESS = false;

    /**
     * Phase 3A verbose: full damage calculation path per block.
     * Shows hardness, blast resist, crackJ, baseBreakJ, confinement, adjBreakJ, ratio, state.
     * Requires LOG_PHASE3A = true to be useful.
     */
    public static volatile boolean LOG_PHASE3A_VERBOSE = false;

    /**
     * Elastic floor (fatigue limit) for sublevel block damage, as a fraction of the
     * block's adjusted break threshold. A single hit below floor × breakJ causes ZERO
     * damage — purely elastic contact. Prevents gentle repeated contacts (waving a
     * structure around) from grinding blocks to CRITICAL via unbounded accumulation.
     * Default 0.2 (hits under 20% of the break threshold are harmless).
     */
    public static volatile double SUBLEVEL_ELASTIC_FLOOR = 0.2;

    /**
     * Stress-relaxation half-life for accumulated sublevel block damage, in server ticks.
     * Accumulated damage halves every N ticks between hits. A real crash delivers its
     * energy within a few ticks (negligible decay); scattered sub-critical hits fade.
     * 0 disables decay. Default 60 (3 seconds).
     */
    public static volatile int SUBLEVEL_DAMAGE_HALF_LIFE_TICKS = 60;

    /**
     * Sanity cap on the per-contact velocity change (m/s) used in sublevel kImpact.
     * Constraint yanks (grab tool) and post-break angular whip can report Δv of 100+ m/s,
     * producing absurd energies (400 000+ J). kImpact is clamped to 0.5 × M × cap².
     * 0 disables the cap. Default 40 m/s.
     */
    public static volatile double SUBLEVEL_MAX_DELTA_V_MS = 40.0;

    // ── Phase 3C: penetration dynamics ────────────────────────────────────────

    /**
     * Master switch for penetration: when a high-energy impact crushes the structure's
     * leading face, the leftover kinetic energy is re-injected as velocity along the
     * original travel direction, letting heavy bodies smash THROUGH material layer by
     * layer instead of stopping elastically at the first contact.
     */
    public static volatile boolean ENABLE_PENETRATION = true;

    /**
     * Minimum per-contact kImpact (J) to consider penetration. Ordinary falls and bumps
     * stay in the elastic/crack regime. Default 1200 J.
     */
    public static volatile double PENETRATION_TRIGGER_J = 1200.0;

    /**
     * Cap on the re-injected speed (m/s). MUST stay below the engine tunneling threshold
     * (~60+ m/s vs 1-block-thin obstacles, observed on the dev server); the excess energy
     * is treated as lost to crushing/heat. Default 30 m/s (1.5 blocks/tick).
     */
    public static volatile double PENETRATION_MAX_SPEED_MS = 30.0;

    /**
     * Minimum contact speed (m/s) to enter penetration mode — the mass-independent
     * dynamic/static criterion, checked as kImpact ≥ 0.5·m·v². The absolute energy
     * trigger alone let heavy bodies re-trigger from 1-block settling (m·g·h ≈ 10 kJ
     * for a 1000 kpg body, 8× the 1200 J trigger) and ratchet-sink into soft ground
     * forever. Below this speed the contact is static bearing — nothing is dug.
     * Default 8 m/s (a 1-block drop lands at ~4.4 m/s, real slams at 20-30 m/s).
     */
    public static volatile double PENETRATION_MIN_SPEED_MS = 8.0;

    /**
     * Energy loss multiplier per crushed layer: E_remaining = E − Σ(breakJ) × factor.
     * The factor above 1.0 accounts for the terrain side, deformation and heat that the
     * explicit block bookkeeping doesn't capture. Default 2.0.
     */
    public static volatile double PENETRATION_LOSS_FACTOR = 2.0;

    /**
     * Footprint radius (blocks) for penetration-mode layer crushing. Rapier reports ONE
     * contact point per body pair, so patch-only damage clears a 3×3 hole that a large
     * structure cannot descend into — it rests on the intact terrain around the hole and
     * penetration stalls (giant sandstone slab leaving only a scratch on a desert).
     * When penetration triggers, the striker's whole leading layer AND the terrain in
     * front of it are crushed cell-by-cell across (2R+1)², each cell charging its breakJ
     * to the energy budget. 0 disables footprint mode (patch-only). Default 8.
     */
    public static volatile int PENETRATION_FOOTPRINT_RADIUS = 8;
}
