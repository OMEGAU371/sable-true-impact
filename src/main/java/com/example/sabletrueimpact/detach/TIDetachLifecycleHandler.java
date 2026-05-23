/*
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.LevelAccessor
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.level.LevelEvent$Unload
 *  net.neoforged.neoforge.event.server.ServerStartedEvent
 *  net.neoforged.neoforge.event.server.ServerStoppedEvent
 *  net.neoforged.neoforge.event.server.ServerStoppingEvent
 *  net.neoforged.neoforge.event.tick.ServerTickEvent$Post
 */
package com.example.sabletrueimpact.detach;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 1.2.0 SD-port — drives the TIDetachRegistry lifecycle. Registered on NeoForge.EVENT_BUS by
// TrueImpactMod. Mirrors Sable: Destructive's LifecycleHandler.
//
// Responsibilities:
//   - tick() each ServerTickEvent.Post → evicts debris older than DEFAULT_LIFETIME_TICKS,
//     drops GC'd weak refs. Mid-step gating is inside TIDetachRegistry.tick() itself.
//   - clear() on server start AND stop AND stopped → no leftover entries across save reloads
//   - clearForLevel() on LevelEvent.Unload → drop entries for unloaded dimensions
//
// Default lifetime is 600 ticks (30 s at 20 TPS) — long enough to see a flying debris land
// and react, short enough to keep simultaneous physics-load bounded. Not user-configurable in
// v1; can be promoted to TrueImpactConfig later if needed.
public final class TIDetachLifecycleHandler {

    private static final int DEFAULT_LIFETIME_TICKS = 600;

    private TIDetachLifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        TIDetachRegistry.tick(DEFAULT_LIFETIME_TICKS);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TIDetachRegistry.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TIDetachRegistry.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TIDetachRegistry.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        LevelAccessor accessor = event.getLevel();
        if (accessor instanceof ServerLevel sl) {
            TIDetachRegistry.clearForLevel(sl);
        }
    }
}
