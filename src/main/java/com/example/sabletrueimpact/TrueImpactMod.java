package com.example.sabletrueimpact;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TrueImpactMod.MODID)
public class TrueImpactMod {
    public static final String MODID = "sabletrueimpact";

    public TrueImpactMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, TrueImpactConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, TrueImpactClientConfig.SPEC);
        }

        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        NeoForge.EVENT_BUS.register(ExplosionImpactHandler.class);
        NeoForge.EVENT_BUS.register(EntityImpactHandler.class);
        NeoForge.EVENT_BUS.register(SubLevelFracture.class);
        NeoForge.EVENT_BUS.register(TrueImpactPerformance.class);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(GogglesBlockTooltipHandler.class);
        }
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        TrueImpactPresets.apply();
        MaterialImpactProperties.reload();
        ImpactCallbackDecider.reload();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        TrueImpactPresets.apply();
        MaterialImpactProperties.reload();
        ImpactCallbackDecider.reload();
    }
}
