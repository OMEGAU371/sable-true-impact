package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.DamageCommand;
import io.github.omegau371.trueimpact.command.DiagnosticCommand;
import io.github.omegau371.trueimpact.command.StatusCommand;
import io.github.omegau371.trueimpact.damage.ApplyOutcome;
import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.BlockView;
import io.github.omegau371.trueimpact.damage.CrackOverlayTracker;
import io.github.omegau371.trueimpact.damage.DamageFeedbackTracker;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TrueImpactVersion.MOD_ID)
public class TrueImpactMod {

    public static final Logger LOGGER = LoggerFactory.getLogger(TrueImpactVersion.MOD_ID);

    public TrueImpactMod(IEventBus modBus) {
        LOGGER.info("True Impact {} initializing", TrueImpactVersion.VERSION);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        DiagnosticStateManager.registerFlushHook(BlockDamageAccumulator::clear);
        DiagnosticStateManager.registerFlushHook(DamageFeedbackTracker::clear);
        DiagnosticStateManager.registerFlushHook(MaterialResponsePlanner::clear);
        DiagnosticStateManager.registerFlushHook(CrackOverlayTracker::clear);

        if (DistInfo.isSableLoaded()) {
            LOGGER.info("True Impact: Sable detected -- diagnostic observation layer ready (mixins applied by plugin)");
        } else {
            LOGGER.info("True Impact: Sable not found -- mixin plugin skipped all Sable mixins");
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        DiagnosticCommand.register(event.getDispatcher(), DistInfo.isSableLoaded());
        DamageCommand.register(event.getDispatcher());
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
        // Drain deferred damage queue -- safe world-access window (after all physics ticks).
        java.util.List<DeferredDamageEvent> events = DeferredDamageQueue.drainAll();
        if (events.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (DeferredDamageEvent e : events) {
            // Phase 2C: accumulate effective damage.
            BlockDamageAccumulator.accumulate(e);

            // Phase 2A: SOFT_SOIL compaction (grass_block -> dirt) via ImpactBlockApplicator.
            ServerLevel level = findLevel(server, e.levelKey());
            ApplyOutcome outcome = (level == null)
                    ? ApplyOutcome.SKIP_CHUNK_UNLOADED
                    : ImpactBlockApplicator.tryApply(new ServerLevelBlockView(level), e);
            DeferredDamageQueue.recordApplyResult(e, outcome);

            // Phase 2E: material response planning + execution (one snapshot read covers 2D+2E).
            BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                    e.levelKey(), e.posX(), e.posY(), e.posZ(), e.victimBlock());
            if (snap != null) {
                MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);

                // Debris drop: disabled by default (ENABLE_DEBRIS_DROPS = false).
                // Code preserved for Phase 2F; markDebrisDropped tracks dedup state.
                if (plan.shouldDropDebris()
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

                // Phase 2E hotfix: vanilla crack overlay for CRACKED/CRITICAL blocks.
                // Skipped for SOFT_SOIL (compaction is the primary response).
                if (level != null
                        && snap.materialClass() != MaterialThresholdProfile.MaterialClass.SOFT_SOIL) {
                    int crackProgress = CrackOverlayTracker.tryUpdate(
                            snap.key(), snap.damageState(), snap.ratio(), server.getTickCount());
                    if (crackProgress >= 0) {
                        applyCrackOverlay(level, snap.key(), crackProgress);
                    }
                }
            }
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

    private void onServerStopped(ServerStoppedEvent event) {
        // Clear all cross-world/cross-server transient state
        DiagnosticStateManager.clearAll();
        LOGGER.info("True Impact: diagnostic state cleared on server stop");
    }
}
