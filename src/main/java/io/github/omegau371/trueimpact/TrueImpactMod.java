package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.DamageCommand;
import io.github.omegau371.trueimpact.command.DiagnosticCommand;
import io.github.omegau371.trueimpact.command.StatusCommand;
import io.github.omegau371.trueimpact.damage.ApplyOutcome;
import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.BlockHardnessProfile;
import io.github.omegau371.trueimpact.damage.ConfinementFactor;
import io.github.omegau371.trueimpact.damage.BlockView;
import io.github.omegau371.trueimpact.damage.CrackOverlayTracker;
import io.github.omegau371.trueimpact.damage.DamageFeedbackTracker;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.damage.DeferredContraptionDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredContraptionDamageQueue;
import io.github.omegau371.trueimpact.damage.DeferredSublevelDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredSublevelDamageQueue;
import io.github.omegau371.trueimpact.damage.SublevelDamageAccumulator;
import io.github.omegau371.trueimpact.damage.ImpactBlockApplicator;
import io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlan;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlanner;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import io.github.omegau371.trueimpact.platform.DistInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TrueImpactVersion.MOD_ID)
public class TrueImpactMod {

    public static final Logger LOGGER = LoggerFactory.getLogger(TrueImpactVersion.MOD_ID);

    /**
     * Tracks which sublevel objects had at least one block destroyed by Phase 3A.
     * Keyed by object identity (ServerSubLevel instance) so cross-test contamination
     * is impossible even if Sable reuses runtimeIds between test runs.
     * getAllSubLevels() returns the canonical ServerSubLevel instance so identity matches.
     */
    public static final java.util.Set<Object> PHASE3A_BROKEN_SUBLEVELS =
            java.util.Collections.synchronizedSet(
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    /**
     * Ghost-contact rate limiter: (runtimeId, localPos) → last serverTick a stale-collider
     * contact at that destroyed position was processed. Sable never rebakes the sublevel
     * voxel collider after destroyBlock, so Rapier emits contacts at destroyed positions
     * every tick indefinitely. Bounded: fully cleared when it exceeds 8192 entries.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Long> GHOST_CONTACT_LAST_HANDLED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int GHOST_REDIRECT_COOLDOWN_TICKS = 20;

    /**
     * Striker-face hardness registry (rebuilt each tick): world contact position →
     * break threshold of the SUBLEVEL block face striking there. Populated during
     * applyDeferredSublevelDamage (which runs before the Path 1 world drain in the
     * same tick); consumed by the Path 1 loop to enforce material yield: the contact
     * stress a striker can transmit is limited by its own crushing strength — a
     * wooden face cannot smash stone no matter how fast it is swung; it crushes
     * itself first. Key = levelKey + ":" + BlockPos.asLong(floor(vis)).
     */
    private static final java.util.Map<String, Double> STRIKER_FACE_BREAK_J = new java.util.HashMap<>();

    /** Victim thresholds above strikerBreakJ × this are immune to that striker (harder-breaks-softer). */
    private static final double STRIKER_YIELD_TOLERANCE = 1.5;

    /**
     * breakJ at or above this is "indestructible" (bedrock returns Double.MAX_VALUE —
     * which IS finite, so isFinite() does NOT guard against it). Indestructible blocks
     * are never destroyed or charged, but as strikers they crush anything softer.
     */
    private static final double INDESTRUCTIBLE_BREAK_J = 1.0e9;

    private static void recordStrikerFace(String levelKey, double visX, double visY, double visZ,
                                          double strikerBreakJ) {
        if (!Double.isFinite(visX) || !Double.isFinite(strikerBreakJ)) return;
        String key = levelKey + ":" + net.minecraft.core.BlockPos.asLong(
                (int) Math.floor(visX), (int) Math.floor(visY), (int) Math.floor(visZ));
        STRIKER_FACE_BREAK_J.merge(key, strikerBreakJ, Math::max);
    }

    /**
     * Max striker breakJ recorded near the given world block this tick, or 0 when none.
     * Radius 2: contact-cluster world events (grid scans, multi-point contacts) land up
     * to 2 blocks from the striker's registered vis position.
     */
    private static double lookupStrikerFaceBreakJ(String levelKey, int bx, int by, int bz) {
        if (STRIKER_FACE_BREAK_J.isEmpty()) return 0.0;
        double best = 0.0;
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    Double v = STRIKER_FACE_BREAK_J.get(
                            levelKey + ":" + net.minecraft.core.BlockPos.asLong(bx + dx, by + dy, bz + dz));
                    if (v != null && v > best) best = v;
                }
        return best;
    }

    // D-6.2: Per-anchor accumulated kImpact. Key = (levelKey, anchorPos, blockId).
    // Parallel to BlockDamageAccumulator but only for contraption anchor blocks.
    // Stale entries (anchor block replaced externally) linger until server stop; acceptable
    // given the small number of contraption anchors in a typical world.
    private static final java.util.Map<BlockDamageAccumulator.AccKey, Double> ANCHOR_DAMAGE =
            new java.util.HashMap<>();

    // D-6.2: Cached contraption block layout. Entity ID → double[][] where each row is
    // [localX, localY, localZ, breakThresholdJ]. Built on first contact; cleared on flush.
    // Layout is fixed after assembly, so one computation per entity is correct.
    private static final java.util.Map<Integer, double[][]> CONTRAPTION_BLOCK_CACHE =
            new java.util.HashMap<>();

    public TrueImpactMod(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, TrueImpactConfig.SPEC);
        LOGGER.info("True Impact {} initializing", TrueImpactVersion.VERSION);
        modBus.addListener(TrueImpactMod::onRegisterGameTests);
        modBus.addListener(TrueImpactMod::onConfigLoad);
        modBus.addListener(TrueImpactMod::onConfigReload);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        DiagnosticStateManager.registerFlushHook(BlockDamageAccumulator::clear);
        DiagnosticStateManager.registerFlushHook(DamageFeedbackTracker::clear);
        DiagnosticStateManager.registerFlushHook(MaterialResponsePlanner::clear);
        DiagnosticStateManager.registerFlushHook(CrackOverlayTracker::clear);
        DiagnosticStateManager.registerFlushHook(SublevelDamageAccumulator::clear);
        DiagnosticStateManager.registerFlushHook(DeferredContraptionDamageQueue::clear);
        DiagnosticStateManager.registerFlushHook(ANCHOR_DAMAGE::clear);
        DiagnosticStateManager.registerFlushHook(CONTRAPTION_BLOCK_CACHE::clear);

        if (DistInfo.isSableLoaded()) {
            // Pre/post-physics-step hooks via Sable 2.0.x NeoForge events.
            // Replaces DiagnosticPhysicsStepMixin; fires once per substep on the server thread.
            NeoForge.EVENT_BUS.addListener(
                    io.github.omegau371.trueimpact.sable.SableEventBridge::onForgePrePhysics);
            NeoForge.EVENT_BUS.addListener(
                    io.github.omegau371.trueimpact.sable.SableEventBridge::onForgePostPhysics);
            LOGGER.info("True Impact: Sable detected -- physics events + collision mixins active");
        } else {
            LOGGER.info("True Impact: Sable not found -- all Sable hooks skipped");
        }
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(io.github.omegau371.trueimpact.gametest.Phase3AGameTest.class);
        event.register(io.github.omegau371.trueimpact.gametest.Phase4AGameTest.class);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        DiagnosticCommand.register(event.getDispatcher(), DistInfo.isSableLoaded());
        DamageCommand.register(event.getDispatcher());
    }

    private void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        String levelKey = level.dimension().location().toString();
        String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
        BlockDamageAccumulator.AccKey key = new BlockDamageAccumulator.AccKey(
                levelKey, pos.getX(), pos.getY(), pos.getZ(), blockId);
        int breakerId = CrackOverlayTracker.removeEntry(key);
        if (breakerId != Integer.MIN_VALUE) level.destroyBlockProgress(breakerId, pos, -1);
        BlockDamageAccumulator.removeEntry(key);
    }

    private void onServerTick(ServerTickEvent.Pre event) {
        // Capture server thread identity once (for T-1 comparison)
        if (DistInfo.isSableLoaded()) {
            io.github.omegau371.trueimpact.sable.SableEventBridge.captureServerThread();
        }
        // Rate limiter reset
        DiagnosticConfig.LIMITER.resetTick();
        long tick = event.getServer().getTickCount();
        if (tick % 20 == 0) {
            DiagnosticConfig.LIMITER.resetSecond();
        }
        // Log dropped summary if any (also subject to hard limit)
        if (DiagnosticConfig.ENABLED) {
            int dropped = ExperimentLog.logDroppedSummaryIfNeeded();
        }
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        // Striker-face registry is per-tick: sublevel apply below repopulates it, then
        // the Path 1 world drain consumes it for the material-yield clamp.
        STRIKER_FACE_BREAK_J.clear();
        // Phase 3A: sublevel-block damage (Newton's 3rd law).
        // Runs first so sublevel blocks can be queued independently of world-block gate.
        if (DistInfo.isSableLoaded()) {
            applyDeferredSublevelDamage(event.getServer());
            applyDeferredContraptionDamage(event.getServer());
            applyDeferredWorldContacts(event.getServer());
        }

        // Drain deferred damage queue -- safe world-access window (after all physics ticks).
        java.util.List<DeferredDamageEvent> events = DeferredDamageQueue.drainAll();
        if (events.isEmpty()) return;
        // Master switch for world blocks -- mirrors ENABLE_PHYSICS_STRUCTURE_DAMAGE's early
        // return in applyDeferredSublevelDamage. Must gate everything (accumulation, crack
        // overlay, destruction, compaction), not just one narrow sub-effect.
        if (!ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS) return;
        MinecraftServer server = event.getServer();

        // LOG_ENERGY_SUMMARY: low-frequency one-liner per tick showing kImpact range + paths.
        if (ImpactRuntimeConfig.LOG_ENERGY_SUMMARY && !events.isEmpty()) {
            double kMin = Double.MAX_VALUE, kMax = 0, kSum = 0;
            for (DeferredDamageEvent ev : events) { kSum += ev.kImpact(); if (ev.kImpact() < kMin) kMin = ev.kImpact(); if (ev.kImpact() > kMax) kMax = ev.kImpact(); }
            LOGGER.info("[TI-ENERGY] Path1 events={} kImpact min={} max={} sum={} (J)",
                    events.size(), String.format("%.1f", kMin), String.format("%.1f", kMax), String.format("%.1f", kSum));
        }
        for (DeferredDamageEvent raw : events) {
            // Stale-entry guard + BlockHardnessProfile dynamic threshold override.
            // If the block at this position was replaced or destroyed by non-TI means
            // (player mining, explosion, command, another mod) between the physics tick
            // and now, discard this event and clean up any lingering crack overlay.
            // For confirmed blocks, replace the static break threshold from enqueue time
            // with a value derived from the block's actual hardness and blast resistance.
            DeferredDamageEvent e = raw;
            // Victim's UNCONFINED break threshold, for the material-yield eligibility
            // test — confinement must not enter that comparison (it made stone-vs-stone
            // self-immune: buried victim 816J vs bare striker 490×1.5). Falls back to the
            // enqueue-time static threshold when world state is unavailable.
            double victimBaseBreakJ = raw.threshold();
            ServerLevel level = findLevel(server, e.levelKey());
            if (level != null) {
                BlockPos checkPos = new BlockPos(e.posX(), e.posY(), e.posZ());
                if (level.hasChunkAt(checkPos)) {
                    BlockState current = level.getBlockState(checkPos);
                    String currentId = BuiltInRegistries.BLOCK
                            .getKey(current.getBlock()).toString();
                    if (!currentId.equals(e.victimBlock())) {
                        // Block was replaced externally -- purge TI state for this key.
                        BlockDamageAccumulator.AccKey staleKey = new BlockDamageAccumulator.AccKey(
                                e.levelKey(), e.posX(), e.posY(), e.posZ(), e.victimBlock());
                        BlockDamageAccumulator.removeEntry(staleKey);
                        int staleBreakerId = CrackOverlayTracker.removeEntry(staleKey);
                        if (staleBreakerId != Integer.MIN_VALUE) {
                            level.destroyBlockProgress(staleBreakerId, checkPos, -1);
                        }
                        continue;
                    }
                    // Block confirmed -- apply vanilla-data-driven break threshold
                    // scaled by D-3 confinement factor (surrounding block pressure).
                    float hardness    = current.getDestroySpeed(level, checkPos);
                    float blastResist = current.getBlock().getExplosionResistance();
                    double baseThreshold  = BlockHardnessProfile.breakThresholdJ(hardness, blastResist);
                    double victimCrack    = BlockHardnessProfile.crackThresholdJ(hardness, blastResist);
                    int confinementRadius = ConfinementFactor.dynamicRadius(e.kImpact());
                    double[] neighborCracks = sampleNeighborCrackThresholds(level, checkPos, confinementRadius);
                    double confinement = ConfinementFactor.compute(neighborCracks, victimCrack,
                            e.impactDirX(), e.impactDirY(), e.impactDirZ());
                    victimBaseBreakJ = baseThreshold;
                    e = e.withThreshold(baseThreshold * (1.0 + confinement));
                }
            }

            // Material yield clamp: contact stress is limited by the striker's own
            // crushing strength. When the sublevel face striking this block is SOFTER
            // than the victim (victim BASE threshold > striker breakJ × tolerance —
            // both unconfined, material-vs-material), the striker crushes itself
            // without damaging the victim: a swung wooden skeleton can no longer smash
            // stone, while stone-vs-stone stays symmetric. When materials are
            // comparable, the transmitted energy is still capped at the striker's
            // breakJ. Unknown striker (no sublevel contact nearby this tick) → no clamp.
            double strikerBreakJ = lookupStrikerFaceBreakJ(e.levelKey(), e.posX(), e.posY(), e.posZ());
            if (strikerBreakJ > 0) {
                if (victimBaseBreakJ > strikerBreakJ * STRIKER_YIELD_TOLERANCE) {
                    if (ImpactRuntimeConfig.LOG_PATH1)
                        LOGGER.info("[TI-P1] yieldSkip block={} pos=({},{},{}) victimBase={} > striker={}×{}",
                                e.victimBlock(), e.posX(), e.posY(), e.posZ(),
                                String.format("%.1f", victimBaseBreakJ),
                                String.format("%.1f", strikerBreakJ),
                                STRIKER_YIELD_TOLERANCE);
                    continue;
                }
                if (e.kImpact() > strikerBreakJ) {
                    e = e.withKImpact(strikerBreakJ);
                }
            }

            // LOG_PATH1: log each event after confinement scaling (threshold already includes confinement).
            if (ImpactRuntimeConfig.LOG_PATH1) {
                LOGGER.info("[TI-P1] event block={} pos=({},{},{}) kImpact={}J threshold={}J",
                        e.victimBlock(), e.posX(), e.posY(), e.posZ(),
                        String.format("%.2f", e.kImpact()),
                        String.format("%.2f", e.threshold()));
            }

            // Phase 2C: accumulate effective damage (or, when 裂纹积累 is off, judge this
            // hit alone -- see BlockDamageAccumulator.accumulate for the single-hit branch).
            BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.accumulate(e);

            // Phase 2A: surface-layer transformation (grass→dirt etc.).
            // Skipped when CRITICAL so the block breaks directly without transforming first.
            boolean criticalSoil = snap != null
                    && snap.materialClass() == MaterialThresholdProfile.MaterialClass.SOFT_SOIL
                    && snap.damageState() == DamageState.CRITICAL;
            ApplyOutcome outcome;
            if (level == null) {
                outcome = ApplyOutcome.SKIP_CHUNK_UNLOADED;
            } else if (criticalSoil) {
                outcome = ApplyOutcome.APPLIED_NO_OP; // break takes priority over transform
            } else {
                outcome = ImpactBlockApplicator.tryApply(new ServerLevelBlockView(level), e);
            }
            DeferredDamageQueue.recordApplyResult(e, outcome);
            if (ImpactRuntimeConfig.LOG_PATH1 && snap != null) {
                LOGGER.info("[TI-P1] snap block={} pos=({},{},{}) accumulated={}J ratio={} state={} hitCount={}",
                        snap.key().victimBlock(), e.posX(), e.posY(), e.posZ(),
                        String.format("%.2f", snap.accumulatedEffectiveDamageJ()),
                        String.format("%.3f", snap.ratio()),
                        snap.damageState(), snap.hitCount());
            }
            if (snap != null) {
                MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);

                // Phase 2F: actual block destruction for CRITICAL blocks.
                // Fires when ENABLE_BLOCK_BREAKING=true and plan.futureBreakEligible()
                // (STONE, WOOD, METAL, HIGH_STRENGTH, GENERIC at CRITICAL).
                // First-hit-only dedup via markBreakScheduled.
                // destroyBlock(pos, true) drops loot naturally; no separate debris needed.
                boolean blockBroken = false;
                if (level != null
                        && ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING
                        && plan.futureBreakEligible()
                        && outcome != ApplyOutcome.APPLIED  // compaction already changed the block
                        && MaterialResponsePlanner.markBreakScheduled(snap.key())) {
                    BlockPos breakPos = new BlockPos(e.posX(), e.posY(), e.posZ());
                    if (level.hasChunkAt(breakPos) && !level.getBlockState(breakPos).isAir()) {
                        ItemStack dropTool = resolveDropTool(e.kImpact());
                        if (dropTool != null) {
                            BlockState bs = level.getBlockState(breakPos);
                            BlockEntity be = bs.hasBlockEntity() ? level.getBlockEntity(breakPos) : null;
                            Block.dropResources(bs, level, breakPos, be, null, dropTool);
                        }
                        level.destroyBlock(breakPos, false);
                        // Clear crack overlay from clients (block is gone)
                        level.destroyBlockProgress(
                                CrackOverlayTracker.fakeBreakerIdFor(snap.key()), breakPos, -1);
                        // Remove stale accumulator entry so the position starts fresh
                        BlockDamageAccumulator.removeEntry(snap.key());
                        blockBroken = true;
                        if (ImpactRuntimeConfig.LOG_PATH1)
                            LOGGER.info("[TI-P1] BROKEN block={} pos=({},{},{}) accumulated={}J",
                                    snap.key().victimBlock(), breakPos.getX(), breakPos.getY(), breakPos.getZ(),
                                    String.format("%.2f", snap.accumulatedEffectiveDamageJ()));
                    }
                }

                // Debris drop: only when block was NOT just broken (avoids double loot).
                // Disabled by default (ENABLE_DEBRIS_DROPS = false).
                if (!blockBroken
                        && plan.shouldDropDebris()
                        && level != null
                        && ImpactRuntimeConfig.ENABLE_DEBRIS_DROPS
                        && MaterialResponsePlanner.markDebrisDropped(snap.key())) {
                    dropDebris(level, e.posX(), e.posY(), e.posZ());
                }
                MaterialResponsePlanner.recordExecuted(plan);

                // Phase 2D: cosmetic particle feedback (rate-limited, read-only).
                if (level != null && DamageFeedbackTracker.shouldEmit(
                        e.posX(), e.posY(), e.posZ(), snap.damageState(), server.getTickCount())) {
                    emitDamageFeedback(level, e.posX(), e.posY(), e.posZ(), snap.damageState());
                }

                // Phase 2E: vanilla crack overlay for BRUISED/CRACKED/CRITICAL blocks.
                // Progress driven purely by accumulated ratio (0.10 per step).
                // Skipped for SOFT_SOIL (compaction is the primary response).
                if (level != null) {
                    int crackProgress = CrackOverlayTracker.tryUpdate(
                            snap.key(), snap.damageState(), snap.ratio(), server.getTickCount());
                    if (crackProgress >= 0) {
                        applyCrackOverlay(level, snap.key(), crackProgress);
                    }
                }
            }
        }
    }

    // Phase 3A: apply deferred sublevel-block damage (Newton's 3rd law).
    // Called after world-block damage so both sides of the collision are resolved in the same tick.
    private static void applyDeferredSublevelDamage(MinecraftServer server) {
        java.util.List<DeferredSublevelDamageEvent> events = DeferredSublevelDamageQueue.drainAll();
        if (events.isEmpty()) return;
        if (ImpactRuntimeConfig.LOG_PHASE3A)
            LOGGER.info("[TI3A-D] applyDeferred: {} events, ENABLE_BLOCK_BREAKING={}",
                    events.size(), ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING);
        // NOTE: ENABLE_BLOCK_BREAKING is intentionally NOT checked here -- it must only
        // gate the final destroyBlock call (see applySublevelCellDamage), mirroring the
        // world-block path where accumulation/crack-display run independent of it.
        if (!ImpactRuntimeConfig.ENABLE_PHYSICS_STRUCTURE_DAMAGE) return;

        try {
            for (ServerLevel level : server.getAllLevels()) {
                String lk = level.dimension().location().toString();
                dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                        (dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer)
                        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                if (container == null) continue;
                java.util.List<dev.ryanhcode.sable.sublevel.ServerSubLevel> allSubs =
                        container.getAllSubLevels();

                for (DeferredSublevelDamageEvent event : events) {
                    if (!lk.equals(event.levelKey())) continue;
                    boolean found = false;
                    for (dev.ryanhcode.sable.sublevel.ServerSubLevel sl : allSubs) {
                        if (sl.getRuntimeId() != event.sublevelRuntimeId()) continue;
                        found = true;
                        if (ImpactRuntimeConfig.LOG_PHASE3A)
                            LOGGER.info("[TI3A-D] matched sl={} removed={} kImpact={}",
                                    sl, sl.isRemoved(), String.format("%.2f", event.kImpact()));
                        if (sl.isRemoved()) break;
                        tryBreakSublevelBlock(level, sl, event);
                        break;
                    }
                    if (!found && ImpactRuntimeConfig.LOG_PHASE3A) {
                        LOGGER.info("[TI3A-D] no sl found for runtimeId={} levelKey={}",
                                event.sublevelRuntimeId(), event.levelKey());
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[TI] Phase 3A sublevel damage failed: {}", t.getMessage());
        }
    }

    // Phase 4A: apply deferred contraption anchor damage.
    // Runs after Phase 3A so both sublevel-block and contraption damage resolve in the same tick.
    // The event carries the body's Minecraft world-space position at the time of contact
    // (bodyWorldX/Y/Z), so we do NOT need the ServerSubLevel instance for the AABB search —
    // avoiding a false-negative when Phase 3A already marked the sublevel as removed.
    private static void applyDeferredContraptionDamage(MinecraftServer server) {
        java.util.List<DeferredContraptionDamageEvent> events = DeferredContraptionDamageQueue.drainAll();
        if (events.isEmpty()) return;
        if (!ImpactRuntimeConfig.ENABLE_CREATE_INTERACTION) return;

        // Merge all events from the same sublevel this tick: kImpact sums, position from first.
        // Rapier manifolds can produce multiple contact points per collision, causing the same
        // sublevel to be enqueued 2-4 times per substep. Without merging, anchor damage
        // accumulates at 2-4x the real physical rate.
        java.util.Map<String, DeferredContraptionDamageEvent> merged = new java.util.LinkedHashMap<>();
        for (DeferredContraptionDamageEvent e : events) {
            String key = e.levelKey() + ":" + e.sublevelRuntimeId();
            DeferredContraptionDamageEvent prev = merged.get(key);
            if (prev == null) {
                merged.put(key, e);
            } else {
                merged.put(key, new DeferredContraptionDamageEvent(
                        prev.levelKey(),
                        prev.cpLocalX(), prev.cpLocalY(), prev.cpLocalZ(),
                        prev.bodyWorldX(), prev.bodyWorldY(), prev.bodyWorldZ(),
                        prev.kImpact() + e.kImpact(),
                        prev.sublevelRuntimeId()));
            }
        }
        if (ImpactRuntimeConfig.LOG_PHASE4A)
            LOGGER.info("[TI4A] applyDeferred: {} raw → {} merged, ENABLE_CREATE_INTERACTION=true",
                    events.size(), merged.size());

        try {
            for (ServerLevel level : server.getAllLevels()) {
                String lk = level.dimension().location().toString();
                for (DeferredContraptionDamageEvent event : merged.values()) {
                    if (!lk.equals(event.levelKey())) continue;
                    tryDamageContraption(level, event);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[TI4A] applyDeferredContraptionDamage failed: {}", t.getMessage());
        }
    }

    // Phase 4A/4B: search for KinematicContraption entities near the sublevel's world-space
    // contact position and apply contraption-type-specific damage.
    //
    // Type dispatch (D-6):
    //   CarriageContraptionEntity  → tryDerailTrain       (D-6.3)
    //   OrientedContraptionEntity  → tryKillMinecartVehicle (D-6.4)
    //   everything else (bearing)  → anchor crack accumulation + destroy (D-6.1/D-6.2)
    private static void tryDamageContraption(ServerLevel level, DeferredContraptionDamageEvent event) {
        try {
            double wx = event.bodyWorldX(), wy = event.bodyWorldY(), wz = event.bodyWorldZ();
            if (!Double.isFinite(wx) || !Double.isFinite(wy) || !Double.isFinite(wz)) return;

            // Bias downward: snap is captured POST_STEP after bounce, body may be above floor.
            AABB searchBox = new AABB(wx - 3.0, wy - 10.0, wz - 3.0, wx + 3.0, wy + 2.0, wz + 3.0);
            java.util.List<net.minecraft.world.entity.Entity> nearby = level.getEntities(
                    (net.minecraft.world.entity.Entity) null, searchBox,
                    e -> e instanceof dev.ryanhcode.sable.api.sublevel.KinematicContraption kc
                         && kc.sable$isValid());
            if (ImpactRuntimeConfig.LOG_PHASE4A)
                LOGGER.info("[TI4A] AABB search at ({},{},{}) → {} KinematicContraption entities",
                        String.format("%.1f", wx), String.format("%.1f", wy), String.format("%.1f", wz),
                        nearby.size());

            for (net.minecraft.world.entity.Entity entity : nearby) {
                // --- D-6.3: 列车 CarriageContraptionEntity → 轨道蓄积 + 超阈值脱轨 ---
                if (isClassNamed(entity, "com.simibubi.create.content.trains.entity.CarriageContraptionEntity")) {
                    tryDerailTrain(level, entity, event.kImpact(), ImpactRuntimeConfig.TRAIN_DERAIL_THRESHOLD_J);
                    break;
                }
                // --- D-6.4: 矿车 OrientedContraptionEntity → kill vehicle ---
                if (isClassNamed(entity, "com.simibubi.create.content.contraptions.OrientedContraptionEntity")) {
                    tryKillMinecartVehicle(level, entity, event.kImpact(), ImpactRuntimeConfig.MINECART_KILL_THRESHOLD_J);
                    break;
                }
                // --- D-6.1/D-6.2: 轴承等 → anchor 裂纹积累 + 摧毁 ---
                BlockPos anchor = getContraptionAnchor(entity);
                if (anchor == null) continue;
                BlockState anchorState = level.getBlockState(anchor);
                if (!level.hasChunkAt(anchor) || anchorState.isAir()) continue;

                // Threshold = local structural strength + anchor block strength.
                // Local strength = Σ breakThresholdJ for contraption blocks within R=1.5
                // of the contact point (approximated as sublevel COM in contraption-local space).
                // §13 结构应力将在此公式中叠加（预留位置）。
                double[][] contraptionBlocks = getContraptionBlocks(level, entity);
                double localCX = event.bodyWorldX() - entity.getX();
                double localCY = event.bodyWorldY() - entity.getY();
                double localCZ = event.bodyWorldZ() - entity.getZ();
                // Guard: enqueueContraptionContact fires on ALL world contacts (including ground).
                // If the nearest contraption block is > 3 blocks from the sublevel COM, this is
                // a false positive — sublevel hit the ground near the contraption, not the contraption.
                double minDistSq = Double.MAX_VALUE;
                double structureStrength = 0.0;
                for (double[] b : contraptionBlocks) {
                    double dx = b[0] - localCX, dy = b[1] - localCY, dz = b[2] - localCZ;
                    double dSq = dx * dx + dy * dy + dz * dz;
                    if (dSq < minDistSq) minDistSq = dSq;
                    structureStrength += b[3]; // sum over all blocks — no contact-area scaling
                }
                if (contraptionBlocks.length > 0 && minDistSq > 9.0) {
                    if (ImpactRuntimeConfig.LOG_PHASE4A)
                        LOGGER.info("[TI4A] false contact: nearest block {:.2f} blocks away, skipping",
                                Math.sqrt(minDistSq));
                    break;
                }
                float anchorHardness    = anchorState.getDestroySpeed(level, anchor);
                float anchorBlastResist = anchorState.getBlock().getExplosionResistance();
                double anchorBreakJ     = BlockHardnessProfile.breakThresholdJ(anchorHardness, anchorBlastResist);
                double anchorThreshold  = (structureStrength + anchorBreakJ)
                        * ImpactRuntimeConfig.CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER;
                if (!Double.isFinite(anchorThreshold)) {
                    if (ImpactRuntimeConfig.LOG_PHASE4A)
                        LOGGER.info("[TI4A] anchor at {} is indestructible (hardness={}), skipping",
                                anchor, anchorHardness);
                    break;
                }
                if (ImpactRuntimeConfig.LOG_PHASE4A)
                    LOGGER.info("[TI4A] localContact=({},{},{}) structStr={}J anchor={}J threshold={}J",
                            String.format("%.2f", localCX), String.format("%.2f", localCY), String.format("%.2f", localCZ),
                            String.format("%.1f", structureStrength),
                            String.format("%.1f", anchorBreakJ), String.format("%.1f", anchorThreshold));

                String blockId = BuiltInRegistries.BLOCK
                        .getKey(anchorState.getBlock()).toString();
                BlockDamageAccumulator.AccKey anchorKey = new BlockDamageAccumulator.AccKey(
                        event.levelKey(), anchor.getX(), anchor.getY(), anchor.getZ(), blockId);

                double accumulated = ANCHOR_DAMAGE.merge(anchorKey, event.kImpact(), Double::sum);
                double ratio = accumulated / anchorThreshold;
                DamageState state = DamageState.of(ratio);

                // Send crack overlay (rate-limited via CrackOverlayTracker cooldown).
                int progress = CrackOverlayTracker.tryUpdate(anchorKey, state, ratio, level.getGameTime());
                if (progress >= 0) {
                    level.destroyBlockProgress(CrackOverlayTracker.fakeBreakerIdFor(anchorKey), anchor, progress);
                }

                if (ImpactRuntimeConfig.LOG_PHASE4A)
                    LOGGER.info("[TI4A] anchor kImpact={} accumulated={}/{}J ratio={} state={} pos={}",
                            String.format("%.1f", event.kImpact()),
                            String.format("%.1f", accumulated), String.format("%.1f", anchorThreshold),
                            String.format("%.2f", ratio), state, anchor);

                if (ratio >= 1.0) {
                    level.destroyBlockProgress(CrackOverlayTracker.fakeBreakerIdFor(anchorKey), anchor, -1);
                    level.destroyBlock(anchor, false);
                    ANCHOR_DAMAGE.remove(anchorKey);
                    if (ImpactRuntimeConfig.LOG_PHASE4A)
                        LOGGER.info("[TI4A] anchor destroyed (accumulated={}J / threshold={}J) at {}",
                                String.format("%.1f", accumulated), String.format("%.1f", anchorThreshold), anchor);
                }
                break;
            }
        } catch (Throwable t) {
            LOGGER.warn("[TI4A] tryDamageContraption failed: {} {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /**
     * Path 1 (world block damage) — applies contacts collected from Rapier clearCollisions()
     * world-body contact points.  Runs after applyDeferredContraptionDamage but before
     * DeferredDamageQueue.drainAll() so the enqueued DeferredDamageEvents are processed
     * in the same server tick.
     */
    private static void applyDeferredWorldContacts(MinecraftServer server) {
        java.util.List<io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent> contacts =
                io.github.omegau371.trueimpact.damage.DeferredWorldContactQueue.drainAll();
        if (contacts.isEmpty()) return;

        // Merge contacts at the same (levelKey, BlockPos) within a single tick.
        java.util.Map<String, io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent> merged =
                new java.util.LinkedHashMap<>();
        for (io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent c : contacts) {
            int bx = (int) Math.floor(c.worldCpX());
            int by = (int) Math.floor(c.worldCpY() - 0.1);
            int bz = (int) Math.floor(c.worldCpZ());
            String key = c.levelKey() + ":" + net.minecraft.core.BlockPos.asLong(bx, by, bz);
            io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent prev = merged.get(key);
            if (prev == null) {
                merged.put(key, c);
            } else {
                merged.put(key, new io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent(
                    prev.levelKey(), prev.worldCpX(), prev.worldCpY(), prev.worldCpZ(),
                    prev.kImpact() + c.kImpact()));
            }
        }

        for (io.github.omegau371.trueimpact.damage.DeferredWorldContactEvent c : merged.values()) {
            int bx = (int) Math.floor(c.worldCpX());
            int by = (int) Math.floor(c.worldCpY() - 0.1);
            int bz = (int) Math.floor(c.worldCpZ());
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                if (!c.levelKey().equals(level.dimension().location().toString())) continue;
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, by, bz);
                if (!level.hasChunkAt(pos)) break;
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (state.isAir()) break;
                String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .getKey(state.getBlock()).toString();
                io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass mc =
                        io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.classify(blockId);
                double threshold = io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.threshold(mc)
                        * io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.breakMultiplier(mc);
                double worldMult = 1.0;
                try { worldMult = TrueImpactConfig.effectiveWorldBlockMultiplier(); } catch (Throwable ignored) {}
                io.github.omegau371.trueimpact.damage.DeferredDamageQueue.enqueue(
                    new io.github.omegau371.trueimpact.damage.DeferredDamageEvent(
                        server.getTickCount(), c.levelKey(),
                        blockId, bx, by, bz,
                        mc, c.kImpact() * worldMult, threshold,
                        io.github.omegau371.trueimpact.damage.VictimInfo.Source.CONTACT_POINT_SAMPLE,
                        io.github.omegau371.trueimpact.damage.VictimInfo.Confidence.APPROX));
                break;
            }
        }
    }

    // D-6.3: Accumulate damage on the track block under the carriage every hit.
    // When accumulated ratio >= 1.0 (CRITICAL) or kImpact >= threshold, destroy the track.
    // Only set CarriageContraptionEntity.derailed=true when kImpact >= threshold.
    private static void tryDerailTrain(ServerLevel level,
                                       net.minecraft.world.entity.Entity entity,
                                       double kImpact, double threshold) {
        try {
            if (ImpactRuntimeConfig.LOG_PHASE4A)
                LOGGER.info("[TI4A-TRAIN] CarriageContraptionEntity hit kImpact={} threshold={}",
                        String.format("%.1f", kImpact), threshold);

            // Search up to 3 blocks down for a thin/track block.
            // Accumulate damage unconditionally (regardless of kImpact vs threshold).
            BlockPos foot = entity.blockPosition();
            boolean trackDestroyed = false;
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos candidate = foot.offset(0, dy, 0);
                BlockState trackState = level.getBlockState(candidate);
                if (trackState.isAir()) continue;
                try {
                    net.minecraft.world.phys.shapes.VoxelShape shape =
                            trackState.getCollisionShape(level, candidate,
                                    net.minecraft.world.phys.shapes.CollisionContext.empty());
                    boolean isThinOrEmpty = shape.isEmpty() || shape.bounds().maxY < 0.5;
                    if (!isThinOrEmpty) break; // solid block → stop searching

                    // Accumulate damage on the track block (mirrors Path 1 world-block logic).
                    String trackId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(trackState.getBlock()).toString();
                    String lk = level.dimension().location().toString();
                    float hardness   = trackState.getDestroySpeed(level, candidate);
                    float blastRes   = trackState.getBlock().getExplosionResistance();
                    double crackJ    = BlockHardnessProfile.crackThresholdJ(hardness, blastRes);
                    double baseBreakJ = BlockHardnessProfile.breakThresholdJ(hardness, blastRes);
                    if (!Double.isFinite(baseBreakJ)) break; // indestructible

                    double[] neighborCracks = sampleNeighborCrackThresholds(level, candidate);
                    double confinement = ConfinementFactor.compute(neighborCracks, crackJ);
                    double adjBreakJ = baseBreakJ * (1.0 + confinement);

                    io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass mc =
                            io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.classify(trackId);
                    DeferredDamageEvent fakeEv = new DeferredDamageEvent(
                            level.getGameTime(), lk, trackId,
                            candidate.getX(), candidate.getY(), candidate.getZ(),
                            mc, kImpact, adjBreakJ,
                            io.github.omegau371.trueimpact.damage.VictimInfo.Source.CONTACT_POINT_SAMPLE,
                            io.github.omegau371.trueimpact.damage.VictimInfo.Confidence.EXACT);
                    BlockDamageAccumulator.accumulate(fakeEv);

                    BlockDamageAccumulator.Snapshot trackSnap = BlockDamageAccumulator.getSnapshot(
                            lk, candidate.getX(), candidate.getY(), candidate.getZ(), trackId);
                    if (trackSnap != null) {
                        BlockDamageAccumulator.AccKey trackKey = new BlockDamageAccumulator.AccKey(
                                lk, candidate.getX(), candidate.getY(), candidate.getZ(), trackId);
                        int progress = CrackOverlayTracker.tryUpdate(
                                trackKey, trackSnap.damageState(), trackSnap.ratio(), level.getGameTime());
                        if (progress >= 0)
                            level.destroyBlockProgress(CrackOverlayTracker.fakeBreakerIdFor(trackKey), candidate, progress);

                        if (ImpactRuntimeConfig.LOG_PHASE4A)
                            LOGGER.info("[TI4A-TRAIN] track={} pos={} kImpact={} accumulated={}J ratio={} state={}",
                                    trackId, candidate, String.format("%.1f", kImpact),
                                    String.format("%.1f", trackSnap.accumulatedEffectiveDamageJ()),
                                    String.format("%.3f", trackSnap.ratio()), trackSnap.damageState());

                        if (kImpact >= threshold || trackSnap.damageState() == DamageState.CRITICAL) {
                            level.destroyBlockProgress(CrackOverlayTracker.fakeBreakerIdFor(trackKey), candidate, -1);
                            Block.dropResources(trackState, level, candidate, null, null,
                                    net.minecraft.world.item.ItemStack.EMPTY);
                            level.destroyBlock(candidate, false);
                            BlockDamageAccumulator.removeEntry(trackKey);
                            trackDestroyed = true;
                            if (ImpactRuntimeConfig.LOG_PHASE4A)
                                LOGGER.info("[TI4A-TRAIN] track DESTROYED at {}", candidate);
                        }
                    }
                    break;
                } catch (Throwable ignored) {}
            }

            // Only trigger derail when kImpact exceeds threshold.
            if (kImpact < threshold) return;

            // 'derailed' lives on the Carriage object, not on the entity itself.
            // Path: CarriageContraptionEntity.carriage → Carriage.derailed
            java.lang.reflect.Field carriageField = findField(entity.getClass(), "carriage");
            if (carriageField == null) {
                LOGGER.warn("[TI4A-TRAIN] no 'carriage' field on {}", entity.getClass().getSimpleName());
                return;
            }
            carriageField.setAccessible(true);
            Object carriage = carriageField.get(entity);
            if (carriage == null) {
                LOGGER.warn("[TI4A-TRAIN] 'carriage' field is null");
                return;
            }
            // Try Carriage.derailed first, then Carriage.train → Train.derailed.
            Object derailTarget = carriage;
            java.lang.reflect.Field derailedField = findField(carriage.getClass(), "derailed");
            if (derailedField == null
                    || (derailedField.getType() != boolean.class && derailedField.getType() != Boolean.class)) {
                java.lang.reflect.Field trainField = findField(carriage.getClass(), "train");
                if (trainField != null) {
                    trainField.setAccessible(true);
                    Object train = trainField.get(carriage);
                    if (train != null) {
                        derailTarget = train;
                        derailedField = findField(train.getClass(), "derailed");
                        if (derailedField != null
                                && derailedField.getType() != boolean.class
                                && derailedField.getType() != Boolean.class) {
                            derailedField = null;
                        }
                    }
                }
            }
            if (derailedField == null) {
                // Still not found — enumerate Train fields for the next investigation.
                java.util.StringJoiner fields = new java.util.StringJoiner(", ");
                Class<?> c = derailTarget.getClass();
                while (c != null && c != Object.class) {
                    for (java.lang.reflect.Field f : c.getDeclaredFields())
                        fields.add(f.getType().getSimpleName() + " " + f.getName());
                    c = c.getSuperclass();
                }
                LOGGER.warn("[TI4A-TRAIN] no boolean 'derailed' on {} — fields: {}",
                        derailTarget.getClass().getSimpleName(), fields);
                return;
            }
            derailedField.setAccessible(true);
            derailedField.set(derailTarget, true);
            if (ImpactRuntimeConfig.LOG_PHASE4A)
                LOGGER.info("[TI4A-TRAIN] train derailed (field on {}) at {}",
                        derailTarget.getClass().getSimpleName(), entity.position());
        } catch (Throwable t) {
            LOGGER.warn("[TI4A-TRAIN] tryDerailTrain failed: {} {}",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    // D-6.4: Kill the minecart vehicle under OrientedContraptionEntity.
    // Standard MC riding: minecart is the vehicle, OrientedContraptionEntity is a passenger.
    // Killing the vehicle causes the contraption entity to dismount and drop as blocks.
    private static void tryKillMinecartVehicle(ServerLevel level,
                                               net.minecraft.world.entity.Entity entity,
                                               double kImpact, double threshold) {
        try {
            if (ImpactRuntimeConfig.LOG_PHASE4A)
                LOGGER.info("[TI4A-CART] OrientedContraptionEntity hit kImpact={} threshold={}",
                        String.format("%.1f", kImpact), threshold);
            if (kImpact < threshold) return;
            net.minecraft.world.entity.Entity vehicle = entity.getVehicle();
            if (vehicle == null) {
                LOGGER.warn("[TI4A-CART] no vehicle on OrientedContraptionEntity at {}",
                        entity.position());
                return;
            }
            // hurt() triggers the normal death flow (loot table → item drops).
            // kill() bypasses loot tables and leaves no drops.
            vehicle.hurt(level.damageSources().generic(), Float.MAX_VALUE);
            if (ImpactRuntimeConfig.LOG_PHASE4A)
                LOGGER.info("[TI4A-CART] vehicle {} destroyed at {}",
                        vehicle.getClass().getSimpleName(), vehicle.position());
        } catch (Throwable t) {
            LOGGER.warn("[TI4A-CART] tryKillMinecartVehicle failed: {} {}",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    // Walk the class hierarchy to check if obj is an instance of the named class.
    // Avoids a hard compile-time dependency on Create classes.
    private static boolean isClassNamed(Object obj, String targetClassName) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            if (c.getName().equals(targetClassName)) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    private static BlockPos getContraptionAnchor(net.minecraft.world.entity.Entity entity) {
        try {
            // Create 6.x ControlledContraptionEntity (bearing) stores the controller block pos in
            // "controllerPos". Other contraption types may use "anchor". Try both.
            for (String name : new String[]{"controllerPos", "anchor"}) {
                java.lang.reflect.Field f = findField(entity.getClass(), name);
                if (f != null && f.getType() == BlockPos.class) {
                    f.setAccessible(true);
                    Object val = f.get(entity);
                    if (val instanceof BlockPos bp) return bp;
                }
            }
            LOGGER.warn("[TI4A] getContraptionAnchor: no anchor field found on {}",
                    entity.getClass().getSimpleName());
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[TI4A] getContraptionAnchor failed: {} {}", t.getClass().getSimpleName(), t.getMessage());
            return null;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try { return clazz.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try { return clazz.getDeclaredMethod(name); } catch (NoSuchMethodException ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    // Build and cache the contraption's block layout as double[][4]: [localX, localY, localZ, breakJ].
    // Reads via reflection (contraption.getBlocks() or .blocks field; StructureBlockInfo.state).
    // Returns empty array on failure so callers fall back to anchor-only threshold.
    private static double[][] getContraptionBlocks(ServerLevel level,
                                                    net.minecraft.world.entity.Entity entity) {
        return CONTRAPTION_BLOCK_CACHE.computeIfAbsent(entity.getId(), id -> {
            try {
                java.lang.reflect.Field contraptionField = findField(entity.getClass(), "contraption");
                if (contraptionField == null) return new double[0][0];
                contraptionField.setAccessible(true);
                Object contraption = contraptionField.get(entity);
                if (contraption == null) return new double[0][0];

                java.util.Map<?, ?> blocks = null;
                java.lang.reflect.Method getBlocks = findMethod(contraption.getClass(), "getBlocks");
                if (getBlocks != null) {
                    getBlocks.setAccessible(true);
                    Object result = getBlocks.invoke(contraption);
                    if (result instanceof java.util.Map<?, ?> m) blocks = m;
                }
                if (blocks == null) {
                    java.lang.reflect.Field bf = findField(contraption.getClass(), "blocks");
                    if (bf != null) {
                        bf.setAccessible(true);
                        Object result = bf.get(contraption);
                        if (result instanceof java.util.Map<?, ?> m) blocks = m;
                    }
                }
                if (blocks == null || blocks.isEmpty()) return new double[0][0];

                double[][] rows = new double[blocks.size()][4];
                int i = 0;
                for (java.util.Map.Entry<?, ?> entry : blocks.entrySet()) {
                    if (!(entry.getKey() instanceof BlockPos bp)) continue;
                    java.lang.reflect.Field stateField = findField(entry.getValue().getClass(), "state");
                    if (stateField == null) continue;
                    stateField.setAccessible(true);
                    Object stateObj = stateField.get(entry.getValue());
                    if (!(stateObj instanceof BlockState bs)) continue;
                    float hardness    = bs.getDestroySpeed(level, BlockPos.ZERO);
                    float blastResist = bs.getBlock().getExplosionResistance();
                    double breakJ     = BlockHardnessProfile.breakThresholdJ(hardness, blastResist);
                    rows[i][0] = bp.getX();
                    rows[i][1] = bp.getY();
                    rows[i][2] = bp.getZ();
                    rows[i][3] = Double.isFinite(breakJ) ? breakJ : 0.0;
                    i++;
                }
                double[][] trimmed = java.util.Arrays.copyOf(rows, i);
                if (ImpactRuntimeConfig.LOG_PHASE4A)
                    LOGGER.info("[TI4A] block cache built: {} blocks for entity {}", i, id);
                return trimmed;
            } catch (Throwable t) {
                LOGGER.warn("[TI4A] getContraptionBlocks failed: {} {}",
                        t.getClass().getSimpleName(), t.getMessage());
                return new double[0][0];
            }
        });
    }

    private static void tryBreakSublevelBlock(
            ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel sl,
            DeferredSublevelDamageEvent event) {
        try {
            // Spurious-contact filter (designed in DeferredSublevelDamageEvent.visX/Y/Z docs,
            // never implemented until now): vis is the claimed Minecraft-world contact position.
            // Real Rapier world contacts always have solid terrain there. Velocity-delta events
            // synthesized from grab-constraint deceleration in MID-AIR do not — log analysis of
            // the 0.1.15 session showed 97 of 110 damage events were such phantoms (up to 534 kJ),
            // the root cause of "waving a structure shatters it". NaN vis = unknown → skip filter.
            if (Double.isFinite(event.visX()) && Double.isFinite(event.visY())
                    && Double.isFinite(event.visZ())
                    && !hasSolidTerrainAlongImpact(level,
                            event.visX(), event.visY(), event.visZ(),
                            event.impactDirX(), event.impactDirY(), event.impactDirZ())) {
                if (ImpactRuntimeConfig.LOG_PHASE3A)
                    LOGGER.info("[TI3A-D] tryBreak: phantom dropped (no terrain near vis=({},{},{})) k={} sl={}",
                            String.format("%.1f", event.visX()), String.format("%.1f", event.visY()),
                            String.format("%.1f", event.visZ()),
                            String.format("%.1f", event.kImpact()), sl);
                return;
            }

            dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot = sl.getPlot();
            dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor =
                    plot.getEmbeddedLevelAccessor();

            int localX = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.cpX());
            int localY = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.cpY());
            int localZ = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.cpZ());

            net.minecraft.world.level.block.state.BlockState state =
                    accessor.getBlockState(new net.minecraft.core.BlockPos(localX, localY, localZ));
            // Effective kImpact — reduced when redirecting through a stale-collider fallback.
            double effectiveKImpact = event.kImpact();
            if (state.isAir()) {
                // Ghost-contact suppression: Sable never rebakes the voxel collider after
                // destroyBlock, so Rapier reports contacts at destroyed positions EVERY tick
                // forever. Each ghost costs a full pipeline pass (fallback search, logs) and
                // its redirect grinds down low-threshold edge/corner neighbours. Handle at
                // most one ghost per position per second; drop the rest before any work.
                long ghostKey = (((long) sl.getRuntimeId()) << 36)
                        ^ (((long) (localX & 0xFFF)) << 24)
                        ^ (((long) (localY & 0xFFF)) << 12)
                        ^ (localZ & 0xFFF);
                Long lastHandled = GHOST_CONTACT_LAST_HANDLED.get(ghostKey);
                if (lastHandled != null
                        && event.serverTick() - lastHandled < GHOST_REDIRECT_COOLDOWN_TICKS) {
                    return;
                }
                if (GHOST_CONTACT_LAST_HANDLED.size() > 8192) GHOST_CONTACT_LAST_HANDLED.clear();
                GHOST_CONTACT_LAST_HANDLED.put(ghostKey, event.serverTick());
                // Sable's voxel collider is stale: this block was already destroyed but
                // Rapier still reports contact here (dynamic voxel not rebuilt after destroyBlock).
                // Redirect to the immediate (1-step) inward neighbour only; deeper cascade would
                // over-destroy the structure from accumulated stale contacts.
                int[] fb = findInwardFallbackLocal(accessor, localX, localY, localZ,
                        event.gridDirX(), event.gridDirY(), event.gridDirZ());
                if (fb == null) {
                    if (ImpactRuntimeConfig.LOG_PHASE3A)
                        LOGGER.info("[TI3A-D] tryBreak: AIR (no fallback) local=({},{},{}) sl={}",
                                localX, localY, localZ, sl);
                    return;
                }
                if (ImpactRuntimeConfig.LOG_PHASE3A)
                    LOGGER.info("[TI3A-D] tryBreak: AIR→fallback ({},{},{})→({},{},{}) sl={}",
                            localX, localY, localZ, fb[0], fb[1], fb[2], sl);
                localX = fb[0]; localY = fb[1]; localZ = fb[2];
                state = accessor.getBlockState(new net.minecraft.core.BlockPos(localX, localY, localZ));
                if (state.isAir()) return;
                // Energy attenuates when propagating through a destroyed layer.
                effectiveKImpact *= 0.25;
            }

            // GRID-space direction: the plot grid does not rotate with the body, so the
            // surface snap and face-spread plane below must use R⁻¹(worldDir), not the
            // world direction (world axes on grid coordinates put corner-landing damage
            // on the opposite corner of tumbling bodies).
            double idx = event.gridDirX(), idy = event.gridDirY(), idz = event.gridDirZ();
            boolean hasDir = Double.isFinite(idx) && Double.isFinite(idy) && Double.isFinite(idz)
                    && (idx != 0.0 || idy != 0.0 || idz != 0.0);

            // Surface snap: damage belongs on the body's LEADING face (the surface facing
            // the direction of travel). Real Rapier contacts already resolve there, so the
            // walk below exits immediately (next block along dir is outside the body = AIR).
            // Velocity-delta synthetic contacts assume a single-block body (COM ± 0.5) and
            // land on a near-COM block of multi-block structures — observed breaking an
            // interior vertical column instead of the bottom face on a falling 2×2×2 cube.
            // Walking along the dominant impact axis to the last solid block relocates them
            // to the true contact face.
            if (hasDir) {
                double ax = Math.abs(idx), ay = Math.abs(idy), az = Math.abs(idz);
                int sx = 0, sy = 0, sz = 0;
                if (ay >= ax && ay >= az)  sy = idy > 0 ? 1 : -1;
                else if (ax >= az)         sx = idx > 0 ? 1 : -1;
                else                       sz = idz > 0 ? 1 : -1;
                for (int step = 0; step < 16; step++) {
                    net.minecraft.world.level.block.state.BlockState next =
                            accessor.getBlockState(new net.minecraft.core.BlockPos(
                                    localX + sx, localY + sy, localZ + sz));
                    if (next == null || next.isAir()) break;
                    localX += sx; localY += sy; localZ += sz;
                    state = next;
                }
            }

            // Active-vs-active realism (port of the vs-world material rules): the event
            // carries the OTHER body's contact cell, so the opposing face's hardness can
            // drive both the yield clamp and the energy split. Each side of the pair was
            // enqueued with the FULL pair dissipation eDiss; scaling by 2 × hardness-share
            // keeps eDiss for symmetric materials and shifts the crush energy into the
            // softer face for asymmetric ones (wood-vs-stone: wood takes ~2×eDiss·5/6,
            // stone side is immune outright — same 1.5× tolerance as the vs-world clamp).
            if (event.otherRuntimeId() >= 0) {
                double otherBreakJ = resolveOtherFaceBreakJ(level, event);
                if (Double.isFinite(otherBreakJ) && otherBreakJ > 0) {
                    float myH = state.getDestroySpeed(
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                            net.minecraft.core.BlockPos.ZERO);
                    float myB = state.getBlock().getExplosionResistance();
                    double myBreakJ = BlockHardnessProfile.breakThresholdJ(myH, myB);
                    if (Double.isFinite(myBreakJ) && myBreakJ > 0) {
                        if (myBreakJ > otherBreakJ * STRIKER_YIELD_TOLERANCE) {
                            if (ImpactRuntimeConfig.LOG_PHASE3A)
                                LOGGER.info("[TI3A-AA] victim immune: myBreakJ={} > otherBreakJ={} × {} sl={}",
                                        String.format("%.0f", myBreakJ),
                                        String.format("%.0f", otherBreakJ),
                                        STRIKER_YIELD_TOLERANCE, sl.getRuntimeId());
                            return;
                        }
                        double share = 2.0 * otherBreakJ / (myBreakJ + otherBreakJ);
                        effectiveKImpact *= share;
                        if (ImpactRuntimeConfig.LOG_PHASE3A)
                            LOGGER.info("[TI3A-AA] hardness split: myBreakJ={} otherBreakJ={} share={} eff={} sl={}",
                                    String.format("%.0f", myBreakJ),
                                    String.format("%.0f", otherBreakJ),
                                    String.format("%.2f", share),
                                    String.format("%.1f", effectiveKImpact), sl.getRuntimeId());
                    }
                }
            }

            // Face energy distribution: kImpact is the whole body's ΔKE and is shared by
            // the blocks forming the contact face. Spread it over a 3×3 patch of SOLID
            // cells in the plane perpendicular to the dominant impact axis, weighted by
            // distance (center 1.0, edge 0.5, corner 0.25) and renormalized so the total
            // equals effectiveKImpact. A single-block face still receives 100% — energy
            // is conserved regardless of structure size.
            int f1x = 0, f1y = 0, f1z = 0, f2x = 0, f2y = 0, f2z = 0;
            if (hasDir) {
                double ax = Math.abs(idx), ay = Math.abs(idy), az = Math.abs(idz);
                if (ay >= ax && ay >= az)  { f1x = 1; f2z = 1; } // Y-dominant → XZ face plane
                else if (ax >= az)         { f1y = 1; f2z = 1; } // X-dominant → YZ face plane
                else                       { f1x = 1; f2y = 1; } // Z-dominant → XY face plane
            }
            int[][] cells = new int[9][3];
            double[] cellWeights = new double[9];
            net.minecraft.world.level.block.state.BlockState[] cellStates =
                    new net.minecraft.world.level.block.state.BlockState[9];
            int cellCount = 0;
            double totalW = 0.0;
            if (hasDir) {
                for (int u = -1; u <= 1; u++) {
                    for (int v = -1; v <= 1; v++) {
                        int cx = localX + u * f1x + v * f2x;
                        int cy = localY + u * f1y + v * f2y;
                        int cz = localZ + u * f1z + v * f2z;
                        net.minecraft.world.level.block.state.BlockState cs = (u == 0 && v == 0)
                                ? state
                                : accessor.getBlockState(new net.minecraft.core.BlockPos(cx, cy, cz));
                        if (cs == null || cs.isAir()) continue;
                        int d2 = u * u + v * v;
                        double w = d2 == 0 ? 1.0 : (d2 == 1 ? 0.5 : 0.25);
                        cells[cellCount][0] = cx; cells[cellCount][1] = cy; cells[cellCount][2] = cz;
                        cellWeights[cellCount] = w;
                        cellStates[cellCount] = cs;
                        cellCount++;
                        totalW += w;
                    }
                }
            }
            if (cellCount > 0) {
                // Renormalize over SOLID cells (energy conservation), floored at 2.0.
                // Full renormalization is required: with a fixed 3×3 divisor a 1×1 contact
                // face received only 25% of the impact energy, which combined with the
                // elastic floor made partially-destroyed skeletons effectively INVINCIBLE
                // (while Path 1 still hammered world blocks with full energy — a wooden
                // skeleton could smash stone unharmed). The corner-grinding the fixed
                // divisor once guarded against was driven by ghost/synthetic events, all
                // now fixed at the source (first-wins dedup, surface snap, phantom filter,
                // ghost cooldown). The 2.0 floor keeps a lone corner cell from absorbing
                // 100% of the body's energy — some rebound at a tiny contact patch remains.
                totalW = Math.max(totalW, 2.0);
            } else { // direction unknown: all energy to the contact block
                cells[0][0] = localX; cells[0][1] = localY; cells[0][2] = localZ;
                cellWeights[0] = 1.0; cellStates[0] = state;
                cellCount = 1; totalW = 1.0;
            }
            if (ImpactRuntimeConfig.LOG_PHASE3A_VERBOSE)
                LOGGER.info("[TI3A-V] faceSpread: center=({},{},{}) solidCells={} totalW={} kTotal={}",
                        localX, localY, localZ, cellCount,
                        String.format("%.2f", totalW), String.format("%.2f", effectiveKImpact));
            double absorbedJ = 0.0;
            for (int c = 0; c < cellCount; c++) {
                absorbedJ += applySublevelCellDamage(level, sl, plot, accessor, event,
                        cells[c][0], cells[c][1], cells[c][2], cellStates[c],
                        effectiveKImpact * (cellWeights[c] / totalW));
            }

            // ── Phase 3C: penetration dynamics ────────────────────────────────
            // Rapier resolved this contact as rigid-vs-rigid and consumed the body's
            // velocity — but the leading face just CRUSHED (absorbedJ > 0), so the stop
            // was not elastic. Re-inject the energy that crushing didn't absorb as
            // velocity along the original travel direction: the body smashes through
            // layer by layer, each layer paying its breakJ toll, until the budget runs
            // out and the impact degenerates into the normal elastic stop. The speed cap
            // stays below the engine tunneling threshold, so the solver always sees
            // solid colliders — tunneling remains structurally impossible.
            // Contact-face striker strength — yield reference for penetration decisions.
            float csH = state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                    net.minecraft.core.BlockPos.ZERO);
            float csB = state.getBlock().getExplosionResistance();
            double contactStrikerBreakJ = BlockHardnessProfile.breakThresholdJ(csH, csB);

            // Penetration justification: the impact was NOT elastic if EITHER side yields.
            // absorbedJ > 0 covers a crushing striker face; the terrain probe covers the
            // opposite (and physically dominant) case — a striker HARDER than the ground,
            // whose own face never crushes (bedrock: breakJ = MAX_VALUE → absorbedJ stays
            // 0 → old gate never fired → super-hard impactors bounced off elastically
            // instead of drilling in, the exact inverse of real penetrator physics).
            // terrainYieldable: the striker is harder than the ground ahead — penetration
            // should fire even when the striker face didn't self-crush (absorbedJ==0).
            // Confinement in the footprint budget ensures depth-limited termination:
            // each deeper layer costs more J, so the budget naturally runs out before
            // infinite drilling occurs even for very hard impactors like obsidian.
            boolean terrainYieldable = absorbedJ <= 0
                    && isTerrainYieldableInFront(level, event, contactStrikerBreakJ);

            if (ImpactRuntimeConfig.ENABLE_PENETRATION
                    && Double.isFinite(event.visX()) // NaN vis = body-vs-body: Rapier already
                    // resolved the momentum exchange; re-injection would double-launch the victim
                    && (absorbedJ > 0 || terrainYieldable)
                    && effectiveKImpact >= ImpactRuntimeConfig.PENETRATION_TRIGGER_J
                    && event.massKpg() > 0
                    // Dynamic/static regime: kImpact ≥ 0.5·m·v_min² ⇔ contact speed ≥ v_min.
                    // The absolute J trigger alone lets heavy bodies re-trigger from 1-block
                    // settling (m·g·h scales with mass, speed doesn't) and ratchet-sink forever.
                    && effectiveKImpact >= 0.5 * event.massKpg()
                            * ImpactRuntimeConfig.PENETRATION_MIN_SPEED_MS
                            * ImpactRuntimeConfig.PENETRATION_MIN_SPEED_MS
                    && Double.isFinite(event.impactDirX()) && Double.isFinite(event.impactDirY())
                    && Double.isFinite(event.impactDirZ())) {
                // Footprint layer crush: Rapier reports ONE contact point per pair, so the
                // 3×3 patch above clears a hole far smaller than a large structure's base —
                // the body then RESTS on the intact terrain around the hole and penetration
                // stalls (giant slab leaving only a scratch). Crush the striker's whole
                // leading layer and the terrain in front of it, each cell charging its
                // breakJ to the same energy budget, so depth scales as E / footprint-cost.
                absorbedJ += crushPenetrationFootprint(level, sl, plot, accessor, event,
                        localX, localY, localZ, contactStrikerBreakJ,
                        effectiveKImpact / ImpactRuntimeConfig.PENETRATION_LOSS_FACTOR - absorbedJ);
                double eRem = effectiveKImpact - absorbedJ * ImpactRuntimeConfig.PENETRATION_LOSS_FACTOR;
                if (eRem > 50.0) {
                    double vTarget = Math.min(Math.sqrt(2.0 * eRem / event.massKpg()),
                            ImpactRuntimeConfig.PENETRATION_MAX_SPEED_MS);
                    try {
                        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle =
                                dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(sl);
                        if (handle != null && handle.isValid()) {
                            org.joml.Vector3d cur = handle.getLinearVelocity(new org.joml.Vector3d());
                            org.joml.Vector3d delta = new org.joml.Vector3d(
                                    event.impactDirX() * vTarget - cur.x,
                                    event.impactDirY() * vTarget - cur.y,
                                    event.impactDirZ() * vTarget - cur.z);
                            handle.addLinearAndAngularVelocity(delta, new org.joml.Vector3d());
                            if (ImpactRuntimeConfig.LOG_PHASE3A)
                                LOGGER.info("[TI3C] penetrate: sl={} eIn={} absorbed={} eRem={} vInject={} dir=({},{},{})",
                                        sl.getRuntimeId(),
                                        String.format("%.1f", effectiveKImpact),
                                        String.format("%.1f", absorbedJ),
                                        String.format("%.1f", eRem),
                                        String.format("%.2f", vTarget),
                                        String.format("%.2f", event.impactDirX()),
                                        String.format("%.2f", event.impactDirY()),
                                        String.format("%.2f", event.impactDirZ()));
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("[TI3C] velocity reinjection failed: {} {}",
                                t.getClass().getSimpleName(), t.getMessage());
                    }
                }
            }

        } catch (Throwable t) {
            LOGGER.warn("[TI] tryBreakSublevelBlock failed: {} {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /**
     * Phase 3C footprint crush: destroys the striker's leading-layer cells and the world
     * blocks in front of them across a (2R+1)² face-plane footprint around the contact
     * cell, charging each destroyed cell's breakJ against the penetration energy budget.
     *
     * Only runs for near-axis-aligned bodies (gridDir·impactDir > 0.9): the world-side
     * footprint is mapped by applying grid-plane offsets in world axes, which is only
     * valid when the grid is not rotated relative to the world. Tumbling bodies keep the
     * patch-only behavior. Skips the inner 3×3 (already processed by the face spread),
     * skips Phase 3B stress propagation per cell (the footprint IS the mass destruction),
     * and stops as soon as the budget or the safety cell cap is exhausted.
     *
     * @param budgetJ remaining crushable energy (already divided by the loss factor)
     * @return total breakJ charged (both sides)
     */
    private static double crushPenetrationFootprint(
            ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel sl,
            dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot,
            dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
            DeferredSublevelDamageEvent event,
            int cx, int cy, int cz,
            double contactStrikerBreakJ,
            double budgetJ) {
        int radius = ImpactRuntimeConfig.PENETRATION_FOOTPRINT_RADIUS;
        if (radius <= 0 || budgetJ <= 0) return 0.0;
        if (!Double.isFinite(event.gridDirX()) || !Double.isFinite(event.visX())) return 0.0;
        double align = event.gridDirX() * event.impactDirX()
                + event.gridDirY() * event.impactDirY()
                + event.gridDirZ() * event.impactDirZ();
        if (align < 0.9) return 0.0;

        // Face-plane axes perpendicular to the dominant impact axis (same as face spread).
        double ax = Math.abs(event.gridDirX()), ay = Math.abs(event.gridDirY()), az = Math.abs(event.gridDirZ());
        int f1x = 0, f1y = 0, f1z = 0, f2x = 0, f2y = 0, f2z = 0;
        if (ay >= ax && ay >= az)  { f1x = 1; f2z = 1; }
        else if (ax >= az)         { f1y = 1; f2z = 1; }
        else                       { f1x = 1; f2y = 1; }

        net.minecraft.core.BlockPos plotCenter = plot.getCenterBlock();
        int wbx = (int) Math.floor(event.visX());
        int wby = (int) Math.floor(event.visY() - 0.1);
        int wbz = (int) Math.floor(event.visZ());
        ItemStack dropTool = resolveDropTool(event.kImpact());

        // Dominant WORLD axis of travel — the world-side probe walks this way to find the
        // actual terrain surface. The vis anchor can sit 1-2 blocks inside the striker's
        // own volume (synthetic events assume a single-block body), so each world column
        // steps forward until the first solid block instead of trusting vis.y − 0.1
        // (observed: a 3-layer slab crushed 97 of its own blocks while worldBroken=0 —
        // the whole footprint probed the slab's own airspace one block above the desert).
        double wax = Math.abs(event.impactDirX()), way = Math.abs(event.impactDirY()),
               waz = Math.abs(event.impactDirZ());
        int dwx = 0, dwy = 0, dwz = 0;
        if (way >= wax && way >= waz)  dwy = event.impactDirY() > 0 ? 1 : -1;
        else if (wax >= waz)           dwx = event.impactDirX() > 0 ? 1 : -1;
        else                           dwz = event.impactDirZ() > 0 ? 1 : -1;

        double charged = 0.0;
        int processed = 0;
        int ownBroken = 0, worldBroken = 0;
        outer:
        for (int r = 0; r <= radius; r++) {
            for (int u = -r; u <= r; u++) {
                for (int v = -r; v <= r; v++) {
                    if (Math.max(Math.abs(u), Math.abs(v)) != r) continue; // ring only
                    if (charged >= budgetJ || ++processed > 512) break outer;

                    // ── striker's own leading-layer cell (r ≤ 1 handled by the patch) ──
                    int gx = cx + u * f1x + v * f2x;
                    int gy = cy + u * f1y + v * f2y;
                    int gz = cz + u * f1z + v * f2z;
                    double columnStrikerBreakJ = contactStrikerBreakJ;
                    net.minecraft.core.BlockPos localPos = new net.minecraft.core.BlockPos(gx, gy, gz);
                    net.minecraft.world.level.block.state.BlockState os;
                    try { os = accessor.getBlockState(localPos); } catch (Throwable ignored) { os = null; }
                    if (r >= 2 && os != null && !os.isAir()) {
                        float oh = os.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, localPos);
                        float ob = os.getBlock().getExplosionResistance();
                        double ownBreakJ = BlockHardnessProfile.breakThresholdJ(oh, ob);
                        // Indestructible striker cells (bedrock: MAX_VALUE, which IS finite)
                        // are never destroyed or charged — but they still crush the terrain
                        // below with their full strength as the column's yield reference.
                        columnStrikerBreakJ = ownBreakJ;
                        if (ownBreakJ < INDESTRUCTIBLE_BREAK_J) {
                            BlockPos absPos = new BlockPos(plotCenter.getX() + gx,
                                    plotCenter.getY() + gy, plotCenter.getZ() + gz);
                            if (dropTool != null) {
                                ServerLevel hostLevel = accessor.getLevel();
                                BlockEntity be = os.hasBlockEntity() ? accessor.getBlockEntity(localPos) : null;
                                Block.dropResources(os, hostLevel, absPos, be, null, dropTool);
                            }
                            boolean broke = false;
                            try { broke = accessor.destroyBlock(localPos, false, null, 512); }
                            catch (Throwable ignored) {}
                            if (broke) {
                                charged += ownBreakJ;
                                ownBroken++;
                                PHASE3A_BROKEN_SUBLEVELS.add(sl);
                                io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass omc =
                                        io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.classify(
                                                net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                                        .getKey(os.getBlock()).toString());
                                SublevelDamageAccumulator.AccKey key = new SublevelDamageAccumulator.AccKey(
                                        sl.getRuntimeId(), gx, gy, gz, omc);
                                level.destroyBlockProgress(SublevelDamageAccumulator.fakeBreakerIdFor(key), absPos, -1);
                                SublevelDamageAccumulator.removeEntry(key);
                            }
                        }
                    }

                    // ── world block in front of this column (directional probe) ──
                    // Only columns where the striker actually has material (or the contact
                    // cell itself) press on the terrain. r == 0 (not r <= 1): unconditional
                    // r ≤ 1 let a sinking body's footprint eat the sand one block outside its
                    // own hull — the foundation of a structure standing NEXT to it — which
                    // then fell, re-triggered its own penetration, and co-sank ("entanglement").
                    boolean columnLoaded = (r == 0) || (os != null && !os.isAir());
                    if (!columnLoaded) continue;
                    for (int step = 0; step <= 4; step++) {
                        BlockPos wp = new BlockPos(
                                wbx + u * f1x + v * f2x + step * dwx,
                                wby + u * f1y + v * f2y + step * dwy,
                                wbz + u * f1z + v * f2z + step * dwz);
                        if (!level.hasChunkAt(wp)) break;
                        net.minecraft.world.level.block.state.BlockState ws = level.getBlockState(wp);
                        if (ws.isAir()) continue;
                        float wh = ws.getDestroySpeed(level, wp);
                        float wb = ws.getBlock().getExplosionResistance();
                        double victimBreakJ = BlockHardnessProfile.breakThresholdJ(wh, wb);
                        if (!Double.isFinite(victimBreakJ)) break;
                        // Confinement: blocks deeper or more surrounded cost more per unit.
                        // R=1 (6 face neighbors only) — performance-safe at up to 512 columns.
                        // This makes each deeper layer progressively more expensive, so the
                        // energy budget depletes naturally before infinite drilling occurs.
                        double victimCrackJ = BlockHardnessProfile.crackThresholdJ(wh, wb);
                        double[] nCracks = sampleNeighborCrackThresholds(level, wp, 1);
                        double conf = ConfinementFactor.compute(nCracks, victimCrackJ,
                                event.impactDirX(), event.impactDirY(), event.impactDirZ());
                        double effectiveBreakJ = victimBreakJ * (1.0 + conf);
                        // Material yield: confined effective threshold vs striker strength.
                        if (effectiveBreakJ <= columnStrikerBreakJ * STRIKER_YIELD_TOLERANCE) {
                            if (dropTool != null) {
                                Block.dropResources(ws, level, wp, null, null, dropTool);
                            }
                            if (level.destroyBlock(wp, false)) {
                                charged += effectiveBreakJ; // deeper blocks drain budget faster
                                worldBroken++;
                            }
                        }
                        break; // first solid block ends the probe (broken or immune)
                    }
                }
            }
        }
        if (ImpactRuntimeConfig.LOG_PHASE3A && (ownBroken > 0 || worldBroken > 0))
            LOGGER.info("[TI3C] footprint: sl={} radius={} ownBroken={} worldBroken={} charged={} budget={}",
                    sl.getRuntimeId(), radius, ownBroken, worldBroken,
                    String.format("%.1f", charged), String.format("%.1f", budgetJ));
        return charged;
    }

    /**
     * Applies one face-cell's energy share to a single sublevel block: material thresholds
     * → confinement → tick-aware accumulation (elastic floor + stress relaxation) →
     * crack overlay or destruction. Extracted from tryBreakSublevelBlock so the face
     * energy distribution can drive multiple cells per contact.
     *
     * @return the energy (J) absorbed by DESTROYING this cell (its adjusted break
     *         threshold), or 0 when the cell survived — Phase 3C penetration budget input.
     */
    private static double applySublevelCellDamage(
            ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel sl,
            dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot plot,
            dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
            DeferredSublevelDamageEvent event,
            int localX, int localY, int localZ,
            net.minecraft.world.level.block.state.BlockState state,
            double cellK) {

            String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(state.getBlock()).toString();
            io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.MaterialClass mc =
                    io.github.omegau371.trueimpact.damage.MaterialThresholdProfile.classify(blockId);

            // Derive crack/break thresholds from vanilla hardness + blast resistance
            // (same data source as Path 1 world blocks and neighbor sampling).
            // capMultiplier still comes from mc (韧性/脆性 characteristic).
            net.minecraft.core.BlockPos localBlockPosForHardness =
                    new net.minecraft.core.BlockPos(localX, localY, localZ);
            float hardness   = state.getDestroySpeed(
                    net.minecraft.world.level.EmptyBlockGetter.INSTANCE, localBlockPosForHardness);
            float blastResist = state.getBlock().getExplosionResistance();
            double victimCrackJ = BlockHardnessProfile.crackThresholdJ(hardness, blastResist);
            double baseBreakJ   = BlockHardnessProfile.breakThresholdJ(hardness, blastResist);

            // Register this striker face for the Path 1 material-yield clamp — BEFORE the
            // elastic reject: even when the striker face itself takes no damage, its
            // crushing strength still limits what it can transmit to the world block.
            recordStrikerFace(event.levelKey(), event.visX(), event.visY(), event.visZ(), baseBreakJ);

            // Fast elastic reject: ConfinementFactor.compute() ≥ 0, so adjustedBreakJ ≥ baseBreakJ.
            // A hit below floor × baseBreakJ can never cause damage — skip the expensive
            // neighbor confinement sampling (up to hundreds of block reads) entirely.
            if (cellK < ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR * baseBreakJ) {
                if (ImpactRuntimeConfig.LOG_PHASE3A_VERBOSE)
                    LOGGER.info("[TI3A-V] elasticSkip: local=({},{},{}) cellK={} < floor {}×{}",
                            localX, localY, localZ, String.format("%.1f", cellK),
                            String.format("%.2f", ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR),
                            String.format("%.1f", baseBreakJ));
                return 0.0;
            }

            // Confinement: dynamic radius (matches Path 1) + direction-aware weighting.
            int confinementRadius = io.github.omegau371.trueimpact.damage.ConfinementFactor
                    .dynamicRadius(cellK);
            double[] neighborCracks = sampleSublevelNeighborCrackThresholds(
                    accessor, localX, localY, localZ, confinementRadius);
            double confinement = io.github.omegau371.trueimpact.damage.ConfinementFactor.compute(
                    neighborCracks, victimCrackJ,
                    // Grid-space direction: neighbor sampling walks the plot grid.
                    event.gridDirX(), event.gridDirY(), event.gridDirZ());
            double adjustedBreakJ = baseBreakJ * (1.0 + confinement);

            SublevelDamageAccumulator.AccKey accKey =
                    new SublevelDamageAccumulator.AccKey(sl.getRuntimeId(), localX, localY, localZ, mc);
            SublevelDamageAccumulator.Snapshot snap =
                    SublevelDamageAccumulator.accumulate(
                            sl.getRuntimeId(), localX, localY, localZ, mc, cellK,
                            adjustedBreakJ, event.serverTick());

            if (ImpactRuntimeConfig.LOG_PHASE3A)
                LOGGER.info("[TI3A-D] tryBreak sl={} kImpact={} plotCp=({},{},{}) local=({},{},{}) block={} mc={} confinement={} breakJ={} ratio={} state={}",
                        sl, String.format("%.2f", cellK),
                        String.format("%.3f", event.cpX()),
                        String.format("%.3f", event.cpY()),
                        String.format("%.3f", event.cpZ()),
                        localX, localY, localZ, blockId, mc,
                        String.format("%.3f", confinement),
                        String.format("%.1f", adjustedBreakJ),
                        String.format("%.3f", snap.ratio()), snap.damageState());
            if (ImpactRuntimeConfig.LOG_PHASE3A_VERBOSE)
                LOGGER.info("[TI3A-V] calcPath: local=({},{},{}) block={} mc={} hardness={} blast={} crackJ={} baseBreakJ={} confinement={} adjBreakJ={} cellK={} elasticFloorJ={} accRatio={} state={}",
                        localX, localY, localZ, blockId, mc,
                        String.format("%.2f", hardness), String.format("%.2f", blastResist),
                        String.format("%.1f", victimCrackJ), String.format("%.1f", baseBreakJ),
                        String.format("%.4f", confinement), String.format("%.1f", adjustedBreakJ),
                        String.format("%.2f", cellK),
                        String.format("%.1f", ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR * adjustedBreakJ),
                        String.format("%.4f", snap.ratio()), snap.damageState());

            // Absolute block position in the embedded world (plotCenter + localOffset).
            net.minecraft.core.BlockPos center = plot.getCenterBlock();
            BlockPos absPos = new BlockPos(
                    center.getX() + localX, center.getY() + localY, center.getZ() + localZ);
            int fakeBreakerId = SublevelDamageAccumulator.fakeBreakerIdFor(accKey);

            // ENABLE_BLOCK_BREAKING gates ONLY the final destruction, mirroring the world-block
            // path -- accumulation and crack display above already ran unconditionally.
            if (snap.damageState() == DamageState.CRITICAL && ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING) {
                net.minecraft.core.BlockPos localBlockPos = new net.minecraft.core.BlockPos(localX, localY, localZ);
                ItemStack dropTool = resolveDropTool(cellK);
                if (dropTool != null) {
                    ServerLevel hostLevel = accessor.getLevel();
                    BlockEntity be = state.hasBlockEntity() ? accessor.getBlockEntity(localBlockPos) : null;
                    Block.dropResources(state, hostLevel, absPos, be, null, dropTool);
                }
                boolean broke = accessor.destroyBlock(localBlockPos, false, null, 512);
                if (ImpactRuntimeConfig.LOG_PHASE3A)
                    LOGGER.info("[TI3A-D] destroyBlock local=({},{},{}) result={}", localX, localY, localZ, broke);
                if (broke) {
                    PHASE3A_BROKEN_SUBLEVELS.add(sl);
                    level.destroyBlockProgress(fakeBreakerId, absPos, -1);
                    SublevelDamageAccumulator.removeEntry(accKey);
                    // Phase 3B: stress propagation → secondary fracture → sublevel split
                    if (ImpactRuntimeConfig.ENABLE_STRESS_FRACTURE) {
                        io.github.omegau371.trueimpact.stress.SubLevelFractureHelper.execute(
                                sl, accessor, level, center, localX, localY, localZ, cellK,
                                event.cpX(), event.cpY(), event.cpZ());
                    }
                    return adjustedBreakJ;
                }
            } else if (ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION) {
                // Crack overlay only makes sense when damage persists across hits (single-hit
                // mode has nothing intermediate to show — see SublevelDamageAccumulator).
                int progress = CrackOverlayTracker.ratioToProgress(snap.damageState(), snap.ratio());
                // Monotonic display: hold the highest crack reached, so stress relaxation
                // does not visually heal the block between hits ("crack reversal").
                progress = SublevelDamageAccumulator.monotonicProgress(accKey, progress);
                if (progress >= 0) {
                    level.destroyBlockProgress(fakeBreakerId, absPos, progress);
                }
            }
            return 0.0;
    }

    /**
     * Phantom-contact filter: returns true when solid world terrain exists near the
     * claimed contact position, searching a 3×3×3 neighborhood at vis and then at up to
     * 4 one-block steps ALONG the impact direction. The directional walk is required
     * because velocity-delta synthetic events compute vis with a single-block assumption
     * (COM ± 0.5) — on a 2-block-tall falling body vis lands INSIDE the body volume,
     * ~1.5 blocks above the terrain actually being struck (observed dropping a genuine
     * 2×2×2 landing on the dev server). Terrain being hit always lies along the direction
     * of travel; a genuinely airborne body has nothing solid there either.
     * Unloaded chunks are skipped (hasChunkAt guard — never forces chunkgen), so events
     * claiming contact at embedded-plot coordinates (~4×10⁷) are also dropped here.
     */
    /**
     * Active-vs-active: base (unconfined) break threshold of the OTHER body's contact-face
     * cell, resolved via its runtimeId + plot-local contact point carried in the event.
     * NaN when the other body is gone (removed/split this tick) or its cell is stale AIR —
     * callers fall back to the symmetric no-split behavior.
     */
    private static double resolveOtherFaceBreakJ(ServerLevel level, DeferredSublevelDamageEvent event) {
        try {
            if (Double.isNaN(event.otherCpX())) return Double.NaN;
            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                    (dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer)
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) return Double.NaN;
            for (dev.ryanhcode.sable.sublevel.ServerSubLevel osl : container.getAllSubLevels()) {
                if (osl.getRuntimeId() != event.otherRuntimeId()) continue;
                if (osl.isRemoved()) return Double.NaN;
                dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor oAcc =
                        osl.getPlot().getEmbeddedLevelAccessor();
                int ox = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.otherCpX());
                int oy = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.otherCpY());
                int oz = io.github.omegau371.trueimpact.damage.Phase3ADamage.faceAwareRound(event.otherCpZ());
                net.minecraft.world.level.block.state.BlockState os =
                        oAcc.getBlockState(new net.minecraft.core.BlockPos(ox, oy, oz));
                if (os == null || os.isAir()) return Double.NaN;
                float oh = os.getDestroySpeed(
                        net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                        net.minecraft.core.BlockPos.ZERO);
                float ob = os.getBlock().getExplosionResistance();
                return BlockHardnessProfile.breakThresholdJ(oh, ob);
            }
        } catch (Throwable ignored) {}
        return Double.NaN;
    }

    /**
     * Phase 3C: returns true when there is crushable world terrain along the impact direction
     * from the vis position — the striker is harder than the ground. Used to trigger
     * penetration even when absorbedJ==0 (indestructible striker face never self-crushes).
     */
    private static boolean isTerrainYieldableInFront(
            ServerLevel level, DeferredSublevelDamageEvent event, double strikerBreakJ) {
        double vx = event.visX(), vy = event.visY(), vz = event.visZ();
        double dx = event.impactDirX(), dy = event.impactDirY(), dz = event.impactDirZ();
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(vz)) return false;
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return false;
        for (int step = 0; step <= 4; step++) {
            net.minecraft.core.BlockPos wp = new net.minecraft.core.BlockPos(
                    (int) Math.floor(vx + step * dx),
                    (int) Math.floor(vy + step * dy),
                    (int) Math.floor(vz + step * dz));
            if (!level.hasChunkAt(wp)) return false;
            net.minecraft.world.level.block.state.BlockState ws = level.getBlockState(wp);
            if (ws.isAir()) continue;
            float wh = ws.getDestroySpeed(level, wp);
            float wb = ws.getBlock().getExplosionResistance();
            double victimBreakJ = BlockHardnessProfile.breakThresholdJ(wh, wb);
            if (!Double.isFinite(victimBreakJ)) return false;
            // Apply confinement: deep/surrounded blocks are harder — use confined threshold
            // for the yield check so only genuinely crushable terrain opens the gate.
            double victimCrackJ = BlockHardnessProfile.crackThresholdJ(wh, wb);
            double[] nCracks = sampleNeighborCrackThresholds(level, wp, 1);
            double conf = ConfinementFactor.compute(nCracks, victimCrackJ,
                    event.impactDirX(), event.impactDirY(), event.impactDirZ());
            double effectiveBreakJ = victimBreakJ * (1.0 + conf);
            return effectiveBreakJ <= strikerBreakJ * STRIKER_YIELD_TOLERANCE;
        }
        return false;
    }

    private static boolean hasSolidTerrainAlongImpact(ServerLevel level,
            double vx, double vy, double vz, double dirX, double dirY, double dirZ) {
        boolean hasDir = Double.isFinite(dirX) && Double.isFinite(dirY) && Double.isFinite(dirZ)
                && (dirX != 0.0 || dirY != 0.0 || dirZ != 0.0);
        int maxSteps = hasDir ? 4 : 0;
        for (int step = 0; step <= maxSteps; step++) {
            int bx = (int) Math.floor(vx + step * dirX);
            int by = (int) Math.floor(vy + step * dirY);
            int bz = (int) Math.floor(vz + step * dirZ);
            for (int dy = -1; dy <= 1; dy++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = new BlockPos(bx + dx, by + dy, bz + dz);
                        if (!level.hasChunkAt(p)) continue;
                        if (!level.getBlockState(p).isAir()) return true;
                    }
        }
        return false;
    }

    /**
     * Stale-collider fallback: Sable's voxel shape is not rebuilt after destroyBlock, so
     * Rapier keeps reporting contacts at already-destroyed positions. This method walks
     * inward from (lx, ly, lz) along the dominant axis of -impactDir to find the nearest
     * non-AIR block, falling back to any face neighbor when direction is unavailable.
     */
    private static int[] findInwardFallbackLocal(
            dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
            int lx, int ly, int lz,
            double impDirX, double impDirY, double impDirZ) {

        boolean hasDir = Double.isFinite(impDirX) && Double.isFinite(impDirY) && Double.isFinite(impDirZ);
        if (hasDir) {
            // Inward = -impactDir; dominant axis determines step direction.
            double ax = Math.abs(impDirX), ay = Math.abs(impDirY), az = Math.abs(impDirZ);
            int dx = 0, dy = 0, dz = 0;
            if (ay >= ax && ay >= az)       { dy = impDirY > 0 ? -1 : 1; }
            else if (ax >= az)              { dx = impDirX > 0 ? -1 : 1; }
            else                            { dz = impDirZ > 0 ? -1 : 1; }
            // Only 1 step: prevent cascade from consuming multiple inner layers via stale contacts.
            net.minecraft.world.level.block.state.BlockState s1 =
                    accessor.getBlockState(new net.minecraft.core.BlockPos(lx + dx, ly + dy, lz + dz));
            if (s1 != null && !s1.isAir())
                return new int[]{lx + dx, ly + dy, lz + dz};
        }
        // Direction unavailable or inward walk found nothing: try all 6 face neighbors.
        int[][] nb = {{0,1,0},{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}};
        for (int[] n : nb) {
            net.minecraft.world.level.block.state.BlockState s =
                    accessor.getBlockState(new net.minecraft.core.BlockPos(lx+n[0], ly+n[1], lz+n[2]));
            if (s != null && !s.isAir()) return new int[]{lx+n[0], ly+n[1], lz+n[2]};
        }
        return null;
    }

    private static ItemStack resolveDropTool(double kImpact) {
        try {
            TrueImpactConfig.DropMode mode = TrueImpactConfig.DROP_MODE.get();
            return switch (mode) {
                case DISABLED -> null;
                case ALL      -> new ItemStack(Items.NETHERITE_PICKAXE);
                case BY_FORCE -> {
                    if (kImpact <= TrueImpactConfig.NETHERITE_PICKAXE_MAX_J.get()) yield new ItemStack(Items.NETHERITE_PICKAXE);
                    if (kImpact <= TrueImpactConfig.DIAMOND_PICKAXE_MAX_J.get())   yield new ItemStack(Items.DIAMOND_PICKAXE);
                    if (kImpact <= TrueImpactConfig.IRON_PICKAXE_MAX_J.get())      yield new ItemStack(Items.IRON_PICKAXE);
                    if (kImpact <= TrueImpactConfig.STONE_PICKAXE_MAX_J.get())     yield new ItemStack(Items.STONE_PICKAXE);
                    if (kImpact <= TrueImpactConfig.WOODEN_PICKAXE_MAX_J.get())    yield new ItemStack(Items.WOODEN_PICKAXE);
                    yield null; // above wooden threshold → bare hand → no drops
                }
            };
        } catch (Throwable t) {
            return new ItemStack(Items.NETHERITE_PICKAXE); // fallback when config not loaded
        }
    }

    // Drops a single block-item at the impact position (debris effect, no block removal).
    private static void dropDebris(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) return;
        net.minecraft.world.item.Item item = level.getBlockState(pos).getBlock().asItem();
        if (item == Items.AIR) return;
        ItemEntity entity = new ItemEntity(level, x + 0.5, y + 0.5, z + 0.5,
                new ItemStack(item, 1));
        entity.setDeltaMovement(
                (level.random.nextDouble() - 0.5) * 0.1,
                level.random.nextDouble() * 0.05 + 0.05,
                (level.random.nextDouble() - 0.5) * 0.1);
        level.addFreshEntity(entity);
    }

    // Sends a vanilla block-breaking progress packet (0-9) to nearby players.
    // No block is removed. fakeBreakerId is stable and negative per block key.
    private static void applyCrackOverlay(ServerLevel level, BlockDamageAccumulator.AccKey key, int progress) {
        BlockPos pos = new BlockPos(key.posX(), key.posY(), key.posZ());
        if (!level.hasChunkAt(pos)) return;
        level.destroyBlockProgress(CrackOverlayTracker.fakeBreakerIdFor(key), pos, progress);
    }

    private static void emitDamageFeedback(ServerLevel level, int x, int y, int z, DamageState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!level.hasChunkAt(pos)) return;
        BlockState blockState = level.getBlockState(pos);
        int count = (state == DamageState.CRITICAL) ? 5 : 2;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                x + 0.5, y + 0.5, z + 0.5, count, 0.25, 0.25, 0.25, 0.0);
    }

    // Samples the crack thresholds of face neighbors for D-3 confinement.
    // radius=1: direct face neighbors only. radius>1: walks each ray up to R steps,
    // summing contiguous solid thresholds; an air gap or unloaded chunk stops the chain.
    // Matching ConfinementFactor.FACE_NORMALS order: above, below, north, south, east, west.
    private static double[] sampleNeighborCrackThresholds(ServerLevel level, BlockPos center, int radius) {
        int[][] dirs = {{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{1,0,0},{-1,0,0}};
        double[] cracks = new double[6];
        for (int i = 0; i < 6; i++) {
            double sum = 0.0;
            for (int d = 1; d <= radius; d++) {
                BlockPos n = center.offset(dirs[i][0] * d, dirs[i][1] * d, dirs[i][2] * d);
                if (!level.hasChunkAt(n)) break;
                BlockState state = level.getBlockState(n);
                if (state.isAir()) break;
                float h  = state.getDestroySpeed(level, n);
                float br = state.getBlock().getExplosionResistance();
                sum += BlockHardnessProfile.crackThresholdJ(h, br);
            }
            cracks[i] = sum;
        }
        return cracks;
    }

    private static double[] sampleNeighborCrackThresholds(ServerLevel level, BlockPos center) {
        return sampleNeighborCrackThresholds(level, center, 1);
    }

    /**
     * Phase 3A: same logic as sampleNeighborCrackThresholds but reads from a sublevel accessor.
     * radius=1: 6 direct face neighbors. radius>1: walks each ray up to R steps, summing
     * contiguous solid thresholds; null state or air stops the chain (mirrors Path 1 behavior).
     */
    private static double[] sampleSublevelNeighborCrackThresholds(
            dev.ryanhcode.sable.sublevel.plot.EmbeddedPlotLevelAccessor accessor,
            int localX, int localY, int localZ, int radius) {
        int[][] dirs = {{0,1,0},{0,-1,0},{0,0,-1},{0,0,1},{1,0,0},{-1,0,0}};
        double[] cracks = new double[6];
        for (int i = 0; i < 6; i++) {
            double sum = 0.0;
            for (int d = 1; d <= radius; d++) {
                try {
                    BlockPos np = new BlockPos(
                            localX + dirs[i][0] * d,
                            localY + dirs[i][1] * d,
                            localZ + dirs[i][2] * d);
                    BlockState ns = accessor.getBlockState(np);
                    if (ns == null || ns.isAir()) break;
                    float h  = ns.getDestroySpeed(
                            net.minecraft.world.level.EmptyBlockGetter.INSTANCE, np);
                    float br = ns.getBlock().getExplosionResistance();
                    sum += BlockHardnessProfile.crackThresholdJ(h, br);
                } catch (Throwable ignored) { break; }
            }
            cracks[i] = sum;
        }
        return cracks;
    }

    private static ServerLevel findLevel(MinecraftServer server, String levelKey) {
        for (ServerLevel l : server.getAllLevels()) {
            if (l.dimension().location().toString().equals(levelKey)) return l;
        }
        return null;
    }

    /** MC-dependent implementation of BlockView backed by a real ServerLevel. */
    private static final class ServerLevelBlockView implements BlockView {
        private final ServerLevel level;

        ServerLevelBlockView(ServerLevel level) { this.level = level; }

        @Override
        public boolean hasChunkAt(int x, int y, int z) {
            return level.hasChunkAt(new BlockPos(x, y, z));
        }

        @Override
        public String getBlockId(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            ResourceLocation loc =
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            return loc != null ? loc.toString() : "minecraft:air";
        }

        @Override
        public boolean setBlock(int x, int y, int z, String targetBlockId) {
            ResourceLocation loc = ResourceLocation.tryParse(targetBlockId);
            if (loc == null) return false;
            java.util.Optional<net.minecraft.world.level.block.Block> blockOpt =
                    BuiltInRegistries.BLOCK.getOptional(loc);
            if (blockOpt.isEmpty()) return false;
            return level.setBlockAndUpdate(
                    new BlockPos(x, y, z), blockOpt.get().defaultBlockState());
        }
    }

    private static void onConfigLoad(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == TrueImpactConfig.SPEC) applyConfig();
    }

    private static void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == TrueImpactConfig.SPEC) applyConfig();
    }

    private static void applyConfig() {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING           = TrueImpactConfig.ENABLE_BLOCK_BREAKING.get();
        ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION      = TrueImpactConfig.ENABLE_DAMAGE_ACCUMULATION.get();
        ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS             = TrueImpactConfig.ENABLE_WORLD_BLOCK_DAMAGE.get();
        ImpactRuntimeConfig.ENABLE_PHYSICS_STRUCTURE_DAMAGE = TrueImpactConfig.ENABLE_PHYSICS_STRUCTURE_DAMAGE.get();
        ImpactRuntimeConfig.ENABLE_STRUCTURE_VS_STRUCTURE   = TrueImpactConfig.ENABLE_STRUCTURE_VS_STRUCTURE.get();
        ImpactRuntimeConfig.ENABLE_CREATE_INTERACTION               = TrueImpactConfig.ENABLE_CREATE_INTERACTION.get();
        ImpactRuntimeConfig.CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER = TrueImpactConfig.CONTRAPTION_ANCHOR_STRENGTH_MULTIPLIER.get();
        ImpactRuntimeConfig.TRAIN_DERAIL_THRESHOLD_J               = TrueImpactConfig.TRAIN_DERAIL_THRESHOLD_J.get();
        ImpactRuntimeConfig.MINECART_KILL_THRESHOLD_J              = TrueImpactConfig.MINECART_KILL_THRESHOLD_J.get();
        io.github.omegau371.trueimpact.sable.SableImpactCapture.GLOBAL_DETECTION_THRESHOLD_J =
                TrueImpactConfig.DETECTION_THRESHOLD_J.get();
        io.github.omegau371.trueimpact.damage.CrackOverlayTracker.PER_BLOCK_UPDATE_COOLDOWN_TICKS =
                TrueImpactConfig.CRACK_UPDATE_COOLDOWN_TICKS.get();
        // Debug logging flags
        try {
            ImpactRuntimeConfig.LOG_BLOCK_CALLBACK  = TrueImpactConfig.LOG_BLOCK_CALLBACK.get();
            ImpactRuntimeConfig.LOG_PATH1           = TrueImpactConfig.LOG_PATH1.get();
            ImpactRuntimeConfig.LOG_PHASE3A         = TrueImpactConfig.LOG_PHASE3A.get();
            ImpactRuntimeConfig.LOG_PHASE4A         = TrueImpactConfig.LOG_PHASE4A.get();
            ImpactRuntimeConfig.LOG_IMPACT_CAPTURE  = TrueImpactConfig.LOG_IMPACT_CAPTURE.get();
            ImpactRuntimeConfig.LOG_BODY_SNAPSHOT   = TrueImpactConfig.LOG_BODY_SNAPSHOT.get();
            ImpactRuntimeConfig.LOG_ENERGY_SUMMARY   = TrueImpactConfig.LOG_ENERGY_SUMMARY.get();
            ImpactRuntimeConfig.LOG_PHASE3A_VERBOSE  = TrueImpactConfig.LOG_PHASE3A_VERBOSE.get();
        } catch (Throwable ignored) {} // GameTest env: config not loaded
        try {
            ImpactRuntimeConfig.SUBLEVEL_ELASTIC_FLOOR           = TrueImpactConfig.SUBLEVEL_ELASTIC_FLOOR.get();
            ImpactRuntimeConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS  = TrueImpactConfig.SUBLEVEL_DAMAGE_HALF_LIFE_TICKS.get();
            ImpactRuntimeConfig.SUBLEVEL_MAX_DELTA_V_MS          = TrueImpactConfig.SUBLEVEL_MAX_DELTA_V_MS.get();
            ImpactRuntimeConfig.ENABLE_PENETRATION               = TrueImpactConfig.ENABLE_PENETRATION.get();
            ImpactRuntimeConfig.PENETRATION_TRIGGER_J            = TrueImpactConfig.PENETRATION_TRIGGER_J.get();
            ImpactRuntimeConfig.PENETRATION_MAX_SPEED_MS         = TrueImpactConfig.PENETRATION_MAX_SPEED_MS.get();
            ImpactRuntimeConfig.PENETRATION_MIN_SPEED_MS         = TrueImpactConfig.PENETRATION_MIN_SPEED_MS.get();
            ImpactRuntimeConfig.PENETRATION_LOSS_FACTOR          = TrueImpactConfig.PENETRATION_LOSS_FACTOR.get();
            ImpactRuntimeConfig.PENETRATION_FOOTPRINT_RADIUS     = TrueImpactConfig.PENETRATION_FOOTPRINT_RADIUS.get();
        } catch (Throwable ignored) {}
        LOGGER.info("[TI] Config applied: breaking={} accumulation={} worldDamage={} threshold={}J",
                ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING,
                ImpactRuntimeConfig.ENABLE_DAMAGE_ACCUMULATION,
                ImpactRuntimeConfig.APPLY_BLOCK_EFFECTS,
                io.github.omegau371.trueimpact.sable.SableImpactCapture.GLOBAL_DETECTION_THRESHOLD_J);
    }

    private void onServerStopped(ServerStoppedEvent event) {
        // Clear all cross-world/cross-server transient state
        DiagnosticStateManager.clearAll();
        LOGGER.info("True Impact: diagnostic state cleared on server stop");
    }
}
