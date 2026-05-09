package com.example.sabletrueimpact;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TrueImpactMod.MODID)
public class TrueImpactMod {
    public static final String MODID = "sabletrueimpact";

    public TrueImpactMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, TrueImpactConfig.SPEC);
        NeoForge.EVENT_BUS.register(ExplosionImpactHandler.class);
        NeoForge.EVENT_BUS.register(EntityImpactHandler.class);
        NeoForge.EVENT_BUS.register(SubLevelFracture.class);
        NeoForge.EVENT_BUS.register(TrueImpactPerformance.class);
    }
}
