package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.DamageCommand;
import io.github.omegau371.trueimpact.command.DiagnosticCommand;
import io.github.omegau371.trueimpact.command.StatusCommand;
import io.github.omegau371.trueimpact.damage.ApplyOutcome;
import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.BlockView;
import io.github.omegau371.trueimpact.damage.DamageFeedbackTracker;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.DeferredDamageEvent;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.damage.ImpactBlockApplicator;
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
        // Phase 2A: drain deferred damage queue and apply real block effects.
        // Fires after all world ticks (safe world-access window; physics step complete).
        // ImpactBlockApplicator handles revalidation and SOFT_SOIL compaction.
        java.util.List<DeferredDamageEvent> events = DeferredDamageQueue.drainAll();
        if (events.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (DeferredDamageEvent e : events) {
            BlockDamageAccumulator.accumulate(e);
            ServerLevel level = findLevel(server, e.levelKey());
            ApplyOutcome outcome;
            if (level == null) {
                outcome = ApplyOutcome.SKIP_CHUNK_UNLOADED;
            } else {
                outcome = ImpactBlockApplicator.tryApply(new ServerLevelBlockView(level), e);
            }
            DeferredDamageQueue.recordApplyResult(e, outcome);
            // Phase 2D: cosmetic feedback (particles) for CRACKED/CRITICAL blocks.
            // Rate-limited per block and globally per tick. Read-only -- no world mutation.
            if (level != null) {
                BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                        e.levelKey(), e.posX(), e.posY(), e.posZ(), e.victimBlock());
                if (snap != null && DamageFeedbackTracker.shouldEmit(
                        e.posX(), e.posY(), e.posZ(), snap.damageState(), server.getTickCount())) {
                    emitDamageFeedback(level, e.posX(), e.posY(), e.posZ(), snap.damageState());
                }
            }
        }
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
