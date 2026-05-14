/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.block.state.BlockState
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
 */
package com.example.sabletrueimpact;

import com.example.sabletrueimpact.MaterialImpactProperties;
import com.example.sabletrueimpact.TrueImpactClientConfig;
import com.example.sabletrueimpact.TrueImpactConfig;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class GogglesBlockTooltipHandler {
    private static final ResourceLocation AERONAUTICS_GOGGLES = ResourceLocation.fromNamespaceAndPath((String)"aeronautics", (String)"aviators_goggles");

    private GogglesBlockTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        BlockItem blockItem;
        Player player;
        block5: {
            block4: {
                Item item;
                if (!((Boolean)TrueImpactConfig.ENABLE_TRUE_IMPACT.get()).booleanValue() || !((Boolean)TrueImpactClientConfig.ENABLE_GOGGLES_BLOCK_TOOLTIP.get()).booleanValue()) {
                    return;
                }
                player = event.getEntity();
                ItemStack stack = event.getItemStack();
                if (player == null || !((item = stack.getItem()) instanceof BlockItem)) break block4;
                blockItem = (BlockItem)item;
                if (GogglesBlockTooltipHandler.wearsAviatorGoggles(player)) break block5;
            }
            return;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        double baseStrength = MaterialImpactProperties.baseStrength((BlockGetter)player.level(), BlockPos.ZERO, state);
        double mass = GogglesBlockTooltipHandler.safeMass(player, state);
        double friction = GogglesBlockTooltipHandler.safeFriction(state);
        double restitution = GogglesBlockTooltipHandler.safeRestitution(state);
        double strength = MaterialImpactProperties.displayStrength(state, baseStrength);
        double toughness = MaterialImpactProperties.displayToughness(state, baseStrength);
        double brittleness = MaterialImpactProperties.brittleness(state);
        event.getToolTip().add(Component.literal((String)"Sable True Impact").withStyle(ChatFormatting.DARK_AQUA));
        event.getToolTip().add(Component.literal((String)"  Source: ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)MaterialImpactProperties.sourceLabel(state)).withStyle(ChatFormatting.DARK_AQUA)));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Mass", "%.2f kpg", mass));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Friction", "%.2f", friction));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Restitution", "%.2f", restitution));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Strength", "%.1f pN", strength));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Toughness", "%.1f pN*s", toughness));
        event.getToolTip().add(GogglesBlockTooltipHandler.line("Brittleness", "%.2f", brittleness));
    }

    private static Component line(String label, String format, Object ... values) {
        return Component.literal((String)("  " + label + ": ")).withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)String.format(Locale.ROOT, format, values)).withStyle(ChatFormatting.AQUA));
    }

    private static boolean wearsAviatorGoggles(Player player) {
        return GogglesBlockTooltipHandler.isAviatorGoggles(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private static boolean isAviatorGoggles(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return AERONAUTICS_GOGGLES.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static double safeMass(Player player, BlockState state) {
        try {
            double original = PhysicsBlockPropertyHelper.getMass((BlockGetter)player.level(), (BlockPos)BlockPos.ZERO, (BlockState)state);
            return Math.max(0.0, MaterialImpactProperties.getMass(state, original));
        }
        catch (RuntimeException e) {
            return 0.0;
        }
    }

    private static double safeFriction(BlockState state) {
        try {
            return MaterialImpactProperties.getFriction(state, PhysicsBlockPropertyHelper.getFriction((BlockState)state));
        }
        catch (RuntimeException e) {
            return 1.0;
        }
    }

    private static double safeRestitution(BlockState state) {
        try {
            return MaterialImpactProperties.getRestitution(state, PhysicsBlockPropertyHelper.getRestitution((BlockState)state));
        }
        catch (RuntimeException e) {
            return 0.0;
        }
    }
}
