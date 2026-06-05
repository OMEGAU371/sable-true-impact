package io.github.omegau371.trueimpact;

import io.github.omegau371.trueimpact.command.StatusCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TrueImpactVersion.MOD_ID)
public class TrueImpactMod {

    public static final Logger LOGGER = LoggerFactory.getLogger(TrueImpactVersion.MOD_ID);

    public TrueImpactMod(IEventBus modBus) {
        LOGGER.info("True Impact {} initializing", TrueImpactVersion.VERSION);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        StatusCommand.register(event.getDispatcher());
    }
}
