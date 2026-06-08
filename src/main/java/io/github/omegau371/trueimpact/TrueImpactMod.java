package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.DiagnosticCommand;
import io.github.omegau371.trueimpact.command.StatusCommand;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import io.github.omegau371.trueimpact.platform.DistInfo;
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
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        if (DistInfo.isSableLoaded()) {
            LOGGER.info("True Impact: Sable detected -- diagnostic observation layer ready (mixins applied by plugin)");
        } else {
            LOGGER.info("True Impact: Sable not found -- mixin plugin skipped all Sable mixins");
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        DiagnosticCommand.register(event.getDispatcher(), DistInfo.isSableLoaded());
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

    private void onServerStopped(ServerStoppedEvent event) {
        // Clear all cross-world/cross-server transient state
        DiagnosticStateManager.clearAll();
        LOGGER.info("True Impact: diagnostic state cleared on server stop");
    }
}
