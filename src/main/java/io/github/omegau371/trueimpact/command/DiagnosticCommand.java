package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Extends /trueimpact with debug and experiment sub-commands.
 *
 * Structure:
 *   /trueimpact debug contacts [on|off]   — T-3/T-5/T-6 raw contact logging
 *   /trueimpact debug callbacks [on|off]  — T-1/T-2 callback logging
 *   /trueimpact debug bodies [on|off]     — body snapshot logging (T-7 included)
 *   /trueimpact debug status              — print current flags
 *   /trueimpact experiment t4 bodies      — list sub-levels with runtimeIds (Sable only, op 4)
 *   /trueimpact experiment t4 apply <id> <fx> <fy> <fz>  — T-4 impulse experiment (op 4)
 *
 * All diagnostics default to OFF. Production logic MUST NOT read diagnostic state.
 */
public final class DiagnosticCommand {

    private DiagnosticCommand() {}

    /**
     * @param sableLoaded whether Sable is present at runtime; if false, T-4 nodes are omitted
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean sableLoaded) {
        var debug = Commands.literal("debug")
                .then(Commands.literal("contacts")
                        .then(Commands.literal("on").executes(ctx -> setContactLog(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setContactLog(ctx, false))))
                .then(Commands.literal("callbacks")
                        .then(Commands.literal("on").executes(ctx -> setCallbackLog(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setCallbackLog(ctx, false))))
                .then(Commands.literal("bodies")
                        .then(Commands.literal("on").executes(ctx -> setBodyLog(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setBodyLog(ctx, false))))
                .then(Commands.literal("status").executes(DiagnosticCommand::printStatus));

        var experiment = Commands.literal("experiment").requires(src -> src.hasPermission(4));

        if (sableLoaded) {
            experiment = experiment
                    .then(Commands.literal("t4")
                            .then(Commands.literal("bodies").executes(DiagnosticCommand::t4ListBodies))
                            .then(Commands.literal("apply")
                                    .then(Commands.argument("runtimeId", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("fx", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("fy", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("fz", DoubleArgumentType.doubleArg())
                                                                    .executes(DiagnosticCommand::t4Apply)))))));
        }

        dispatcher.register(
                Commands.literal("trueimpact")
                        .then(debug)
                        .then(experiment)
        );
    }

    private static int setContactLog(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = true;
        DiagnosticConfig.LOG_RAW_CONTACTS = on;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] raw contacts logging: " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setCallbackLog(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = true;
        DiagnosticConfig.LOG_T1_CALLBACK_THREAD = on;
        DiagnosticConfig.LOG_T2_CALLBACK_COORD = on;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] callback logging (T-1/T-2): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setBodyLog(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = true;
        DiagnosticConfig.LOG_BODY_SNAPSHOTS = on;
        DiagnosticConfig.LOG_T7_VELOCITY_RATIO = on;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] body snapshots (T-7 included): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int printStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] enabled=" + DiagnosticConfig.ENABLED
                + " bodies=" + DiagnosticConfig.LOG_BODY_SNAPSHOTS
                + " contacts=" + DiagnosticConfig.LOG_RAW_CONTACTS
                + " callbacks=" + DiagnosticConfig.LOG_T1_CALLBACK_THREAD
                + " t7=" + DiagnosticConfig.LOG_T7_VELOCITY_RATIO
        ), false);
        return 1;
    }

    // T-4 methods — only registered when Sable is loaded

    private static int t4ListBodies(CommandContext<CommandSourceStack> ctx) {
        return io.github.omegau371.trueimpact.sable.SableT4Command.listBodies(ctx.getSource());
    }

    private static int t4Apply(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "runtimeId");
        double fx = DoubleArgumentType.getDouble(ctx, "fx");
        double fy = DoubleArgumentType.getDouble(ctx, "fy");
        double fz = DoubleArgumentType.getDouble(ctx, "fz");
        return io.github.omegau371.trueimpact.sable.SableT4Command.applyForExperiment(
                ctx.getSource(), id, fx, fy, fz);
    }
}
