package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Diagnostic sub-commands under /trueimpact.
 *
 * All debug commands require operator permission (level 2).
 * T-4 experiment commands require operator permission (level 4).
 *
 *   /trueimpact debug contacts [on|off]   — T-3/T-5/T-6 raw contact logging
 *   /trueimpact debug callbacks [on|off]  — T-1/T-2 callback logging
 *   /trueimpact debug bodies [on|off]     — body snapshot + T-7 logging
 *   /trueimpact debug status              — print all current flags
 *   /trueimpact debug all off             — disable everything + clear state (op 2)
 *   /trueimpact experiment t4 bodies      — list sub-levels (op 4, Sable only)
 *   /trueimpact experiment t4 apply <id> <fx> <fy> <fz>  — T-4 command (op 4, Sable only)
 */
public final class DiagnosticCommand {

    private DiagnosticCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean sableLoaded) {
        var debug = Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("contacts")
                        .then(Commands.literal("on").executes(ctx -> setContacts(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setContacts(ctx, false))))
                .then(Commands.literal("callbacks")
                        .then(Commands.literal("on").executes(ctx -> setCallbacks(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setCallbacks(ctx, false))))
                .then(Commands.literal("bodies")
                        .then(Commands.literal("on").executes(ctx -> setBodies(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setBodies(ctx, false))))
                .then(Commands.literal("status").executes(DiagnosticCommand::status))
                .then(Commands.literal("all")
                        .then(Commands.literal("off").executes(DiagnosticCommand::allOff)));

        var experiment = Commands.literal("experiment").requires(src -> src.hasPermission(4));
        if (sableLoaded) {
            experiment = experiment.then(
                    Commands.literal("t4")
                            .then(Commands.literal("bodies").executes(DiagnosticCommand::t4Bodies))
                            .then(Commands.literal("inspect")
                                    .then(Commands.argument("runtimeId", IntegerArgumentType.integer(0))
                                            .executes(DiagnosticCommand::t4Inspect)))
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

    // ── debug subcommands ────────────────────────────────────────────────────

    private static int setContacts(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_RAW_CONTACTS = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] contacts (T-3/T-5/T-6): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setCallbacks(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_T1_CALLBACK_THREAD = on;
        DiagnosticConfig.LOG_T2_CALLBACK_COORD = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] callbacks (T-1/T-2): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setBodies(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_BODY_SNAPSHOTS = on;
        DiagnosticConfig.LOG_T7_VELOCITY_RATIO = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] bodies + T-7: " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        int t4Pending = T4ApplyForceExperiment.pendingByKey.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] enabled=" + DiagnosticConfig.ENABLED
                + " bodies=" + DiagnosticConfig.LOG_BODY_SNAPSHOTS
                + " contacts=" + DiagnosticConfig.LOG_RAW_CONTACTS
                + " callbacks_t1t2=" + DiagnosticConfig.LOG_T1_CALLBACK_THREAD
                + " t7=" + DiagnosticConfig.LOG_T7_VELOCITY_RATIO
                + " t4Pending=" + t4Pending), false);
        return 1;
    }

    private static int allOff(CommandContext<CommandSourceStack> ctx) {
        DiagnosticStateManager.clearAll();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] ALL diagnostics OFF. State cleared (prevPrePos, T-4 pending, snapshots)."), false);
        return 1;
    }

    // ── T-4 (Sable-only) ─────────────────────────────────────────────────────

    private static int t4Bodies(CommandContext<CommandSourceStack> ctx) {
        return io.github.omegau371.trueimpact.sable.SableT4Command.listBodies(ctx.getSource());
    }

    private static int t4Inspect(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "runtimeId");
        return io.github.omegau371.trueimpact.sable.SableT4Command.inspectBody(ctx.getSource(), id);
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
