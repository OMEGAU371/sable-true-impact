package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.TrueImpactVersion;
import io.github.omegau371.trueimpact.platform.DistInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class StatusCommand {

    private StatusCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("trueimpact")
                        .then(Commands.literal("status")
                                .executes(StatusCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        // Version read from live ModContainer -- single source of truth at runtime
        String tiVersion  = nvl(DistInfo.modVersion(TrueImpactVersion.MOD_ID), "unknown");
        String mcVersion  = nvl(DistInfo.modVersion("minecraft"), "1.21.1");
        String neoVersion = nvl(DistInfo.modVersion("neoforge"), "?");

        String sableStatus;
        if (DistInfo.isSableLoaded()) {
            String sv = DistInfo.modVersion("sable");
            sableStatus = "detected" + (sv != null ? " (" + sv + ")" : "");
        } else {
            sableStatus = "not loaded";
        }

        send(src, "=== True Impact Status ===");
        send(src, "Mod Version  : " + tiVersion);
        send(src, "MC / NeoForge: " + mcVersion + " / " + neoVersion);
        send(src, "Sable        : " + sableStatus);
        send(src, "Environment  : " + DistInfo.distLabel());

        return 1;
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static String nvl(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
