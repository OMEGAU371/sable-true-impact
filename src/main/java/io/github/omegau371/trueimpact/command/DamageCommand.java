package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlan;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlanner;
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
 * /trueimpact damage subcommands (Phase 2D/2E).
 *
 *   /trueimpact damage inspect <x> <y> <z>
 *       Looks up current block at that position in the source's dimension.
 *
 *   /trueimpact damage inspect last
 *       Shows BlockDamageAccumulator.lastUpdatedSnapshot().
 *
 *   /trueimpact damage inspect here
 *       Inspects the block targeted by the player's crosshair (4.5-block ray cast).
 *
 *   All inspect variants show two output lines:
 *     [TI damage inspect] -- accumulated damage fields
 *     [TI damage plan]    -- MaterialResponsePlan (Phase 2E)
 *
 *   /trueimpact damage clear
 *       Clears BlockDamageAccumulator only. Queue counters are preserved.
 *
 * All subcommands require operator permission (level 2).
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
        sendInspectResult(ctx.getSource(), level, new BlockPos(x, y, z));
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
            sendInspectResultFromSnapshot(ctx.getSource(), snap);
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
        sendInspectResult(source, source.getLevel(), targetPos);
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

    private static void sendInspectResult(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        String levelKey  = level.dimension().location().toString();
        String blockId   = resolveBlockId(level, pos);
        BlockDamageAccumulator.Snapshot snap = BlockDamageAccumulator.getSnapshot(
                levelKey, pos.getX(), pos.getY(), pos.getZ(), blockId);
        if (snap == null) {
            final String text = DamageInspectFormatter.formatNoEntry(
                    levelKey, pos.getX(), pos.getY(), pos.getZ(), blockId);
            source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GRAY), false);
        } else {
            sendInspectResultFromSnapshot(source, snap);
        }
    }

    private static void sendInspectResultFromSnapshot(CommandSourceStack source,
                                                      BlockDamageAccumulator.Snapshot snap) {
        MaterialResponsePlan plan = MaterialResponsePlanner.plan(snap);
        boolean debrisDropped = MaterialResponsePlanner.hasDroppedDebris(snap.key());
        ChatFormatting color = colorForState(snap.damageState());
        final String entryText = DamageInspectFormatter.formatEntry(snap);
        final String planText  = DamageInspectFormatter.formatPlan(plan, debrisDropped);
        source.sendSuccess(() -> Component.literal(entryText).withStyle(color), false);
        source.sendSuccess(() -> Component.literal(planText).withStyle(ChatFormatting.GRAY), false);
    }

    private static String resolveBlockId(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return "minecraft:air";
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return (loc != null) ? loc.toString() : "minecraft:air";
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
