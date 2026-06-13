package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /trueimpact damage subcommands (Phase 2D).
 *
 *   /trueimpact damage inspect <x> <y> <z>
 *       Looks up current block at that position in the source's dimension.
 *       Read-only: never mutates the world.
 *
 *   /trueimpact damage inspect last
 *       Shows BlockDamageAccumulator.lastUpdatedSnapshot() -- the most recent impact
 *       regardless of position or material. Useful without knowing exact coordinates.
 *
 *   /trueimpact damage inspect here
 *       Inspects the block targeted by the player's crosshair (4.5-block ray cast).
 *       Requires a player source; shows an error if console or no block targeted.
 *       Read-only: never mutates the world.
 *
 *   /trueimpact damage clear
 *       Clears BlockDamageAccumulator only. Queue counters are preserved.
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
                                                                .executes(DamageCommand::inspect))))
                                        .then(Commands.literal("last")
                                                .executes(DamageCommand::inspectLast))
                                        .then(Commands.literal("here")
                                                .executes(DamageCommand::inspectHere)))
                                .then(Commands.literal("clear")
                                        .executes(DamageCommand::clear)))
        );
    }

    // -- inspect <x> <y> <z> -----------------------------------------------------

    private static int inspect(CommandContext<CommandSourceStack> ctx) {
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");

        ServerLevel level = ctx.getSource().getLevel();
        String levelKey   = level.dimension().location().toString();
        BlockPos pos      = new BlockPos(x, y, z);

        String currentBlockId = resolveBlockId(level, pos);
        sendInspectResult(ctx.getSource(), levelKey, pos, currentBlockId);
        return 1;
    }

    // -- inspect last -------------------------------------------------------------

    private static int inspectLast(CommandContext<CommandSourceStack> ctx) {
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.lastUpdatedSnapshot();
        if (snap == null) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[TI damage] no damage recorded yet")
                            .withStyle(ChatFormatting.GRAY), false);
        } else {
            ChatFormatting color = colorForState(snap.damageState());
            final String text = DamageInspectFormatter.formatEntry(snap);
            ctx.getSource().sendSuccess(
                    () -> Component.literal(text).withStyle(color), false);
        }
        return 1;
    }

    // -- inspect here -------------------------------------------------------------

    private static int inspectHere(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal(
                    "[TI damage] inspect here requires a player (not console)"));
            return 0;
        }

        HitResult hit = player.pick(4.5, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal(
                    "[TI damage] inspect here: no block targeted (aim at a block within 4.5 blocks)"));
            return 0;
        }

        BlockPos targetPos = ((BlockHitResult) hit).getBlockPos();
        ServerLevel level  = source.getLevel();
        String levelKey    = level.dimension().location().toString();
        String blockId     = resolveBlockId(level, targetPos);
        sendInspectResult(source, levelKey, targetPos, blockId);
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

    // -- shared helpers -----------------------------------------------------------

    private static String resolveBlockId(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return "minecraft:air";
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return (loc != null) ? loc.toString() : "minecraft:air";
    }

    private static void sendInspectResult(CommandSourceStack source,
                                          String levelKey, BlockPos pos, String blockId) {
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                levelKey, pos.getX(), pos.getY(), pos.getZ(), blockId);
        if (snap == null) {
            final String text = DamageInspectFormatter.formatNoEntry(
                    levelKey, pos.getX(), pos.getY(), pos.getZ(), blockId);
            source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
        } else {
            ChatFormatting color = colorForState(snap.damageState());
            final String text = DamageInspectFormatter.formatEntry(snap);
            source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
        }
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
