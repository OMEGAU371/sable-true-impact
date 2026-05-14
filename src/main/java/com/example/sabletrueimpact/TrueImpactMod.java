/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.neoforged.api.distmarker.Dist
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.fml.ModContainer
 *  net.neoforged.fml.common.Mod
 *  net.neoforged.fml.config.IConfigSpec
 *  net.neoforged.fml.config.ModConfig$Type
 *  net.neoforged.fml.event.config.ModConfigEvent$Loading
 *  net.neoforged.fml.event.config.ModConfigEvent$Reloading
 *  net.neoforged.fml.loading.FMLEnvironment
 *  net.neoforged.neoforge.common.NeoForge
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.EntityImpactHandler;
import com.example.sabletrueimpact.ExplosionImpactHandler;
import com.example.sabletrueimpact.GogglesBlockTooltipHandler;
import com.example.sabletrueimpact.ImpactCallbackDecider;
import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.SubLevelFracture;
import com.example.sabletrueimpact.TrueImpactConfig;
import com.example.sabletrueimpact.TrueImpactPerformance;
import com.example.sabletrueimpact.TrueImpactPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value="sabletrueimpact")
public class TrueImpactMod {
    public static final String MODID = "sabletrueimpact";

    public TrueImpactMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, (IConfigSpec)TrueImpactConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, (IConfigSpec)TrueImpactClientConfig.SPEC);
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
        if (event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        TrueImpactPresets.apply();
        MaterialImpactProperties.reload();
        ImpactCallbackDecider.reload();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        TrueImpactPresets.apply();
        MaterialImpactProperties.reload();
        ImpactCallbackDecider.reload();
    }
}
