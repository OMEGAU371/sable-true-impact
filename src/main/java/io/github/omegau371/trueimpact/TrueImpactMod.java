package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.DiagnosticCommand;
import io.github.omegau371.trueimpact.command.StatusCommand;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.platform.DistInfo;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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

        // PRE/POST body snapshots are captured via DiagnosticPhysicsStepMixin (mixin-based).
        // No NeoForge Sable events needed — avoids dependency on sable-neoforge.jar.
        if (DistInfo.isSableLoaded()) {
            LOGGER.info("True Impact: Sable detected — diagnostic observation layer ready");
        } else {
            LOGGER.info("True Impact: Sable not found — mixin hooks will be no-ops");
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
        DiagnosticCommand.register(event.getDispatcher(), DistInfo.isSableLoaded());
    }

    private void onServerTick(ServerTickEvent.Pre event) {
        DiagnosticConfig.LIMITER.resetTick();
        long tick = event.getServer().getTickCount();
        if (tick % 20 == 0) {
            DiagnosticConfig.LIMITER.resetSecond();
        }
    }
}
