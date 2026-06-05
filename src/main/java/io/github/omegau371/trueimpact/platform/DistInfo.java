package io.github.omegau371.trueimpact.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Environment queries safe to call from any thread after mod loading.
 * No client-only classes referenced here; safe on dedicated server.
 */
public final class DistInfo {

    private DistInfo() {}

    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }

    public static boolean isSableLoaded() {
        return ModList.get().isLoaded("sable");
    }

    /** Version string for the given mod ID, or {@code null} if not loaded. */
    public static String modVersion(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse(null);
    }

    public static String distLabel() {
        return isDedicatedServer() ? "Dedicated Server" : "Client / Integrated Server";
    }
}
