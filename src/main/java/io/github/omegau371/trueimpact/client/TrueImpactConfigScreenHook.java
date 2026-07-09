package io.github.omegau371.trueimpact.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers NeoForge's built-in ConfigurationScreen as this mod's config UI.
 *
 * Without an explicit factory, third-party generic config screens (Catnip/Ponder)
 * may take over editing our SERVER-type config. Catnip's screen saves through a
 * stale LoadedConfig reference when the world is unloading, which crashes with
 * a NightConfig WritingException (observed 2026-07-03). The built-in screen
 * handles server-config lifecycle correctly.
 */
@EventBusSubscriber(modid = "true_impact", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TrueImpactConfigScreenHook {

    private TrueImpactConfigScreenHook() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById("true_impact").ifPresent(container ->
                container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
    }
}
