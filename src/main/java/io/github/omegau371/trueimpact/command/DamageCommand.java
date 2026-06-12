package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.DamageState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * /trueimpact damage subcommands (Phase 2D).
 *
 *   /trueimpact damage inspect <x> <y> <z>
 *       Reads the BlockDamageAccumulator for the executing player's current dimension
 *       and the block currently at that position. Shows all accumulated damage fields.
 *       Read-only: never mutates the world.
 *
 *   /trueimpact damage clear
 *       Clears BlockDamageAccumulator only.
 *       Does NOT reset DeferredDamageQueue counters or any other diagnostic state.
 *
 * All damage subcommands require operator permission (level 2).
 */
public final class DamageCommand {

    private DamageCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("trueimpact")
                        .then(Commands.literal("damage")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                                .executes(DamageCommand::inspect)))))
                                .then(Commands.literal("clear")
                                        .executes(DamageCommand::clear)))
        );
    }

    // -- inspect ------------------------------------------------------------------

    private static int inspect(CommandContext<CommandSourceStack> ctx) {
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        ServerLevel level   = ctx.getSource().getLevel();
        String levelKey     = level.dimension().location().toString();
        BlockPos pos        = new BlockPos(x, y, z);

        String currentBlockId;
        if (level.hasChunkAt(pos)) {
            ResourceLocation loc = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(pos).getBlock());
            currentBlockId = (loc != null) ? loc.toString() : "minecraft:air";
        } else {
            currentBlockId = "minecraft:air";
        }

        BlockDamageAccumulator.Snapshot snap =
                BlockDamageAccumulator.getSnapshot(levelKey, x, y, z, currentBlockId);

        if (snap == null) {
            final String noEntryText = DamageInspectFormatter.formatNoEntry(levelKey, x, y, z, currentBlockId);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(noEntryText).withStyle(ChatFormatting.GRAY), false);
        } else {
            ChatFormatting color = colorForState(snap.damageState());
            final String entryText = DamageInspectFormatter.formatEntry(snap);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(entryText).withStyle(color), false);
        }
        return 1;
    }

    // -- clear --------------------------------------------------------------------

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int before = BlockDamageAccumulator.entryCount();
        BlockDamageAccumulator.clear();
        final int count = before;
        ctx.getSource().sendSuccess(
                () -> Component.literal("[TI damage] accumulator cleared (" + count + " entries removed)")
                        .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    // Formatting delegated to DamageInspectFormatter (MC-free, unit-testable).

    static ChatFormatting colorForState(DamageState state) {
        return switch (state) {
            case INTACT   -> ChatFormatting.GREEN;
            case BRUISED  -> ChatFormatting.YELLOW;
            case CRACKED  -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
        };
    }
}
