package com.example.sabletrueimpact;

import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

public final class GogglesBlockTooltipHandler {
    private static final ResourceLocation AERONAUTICS_GOGGLES = ResourceLocation.fromNamespaceAndPath("aeronautics", "aviators_goggles");

    private GogglesBlockTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!TrueImpactConfig.ENABLE_TRUE_IMPACT.get() || !TrueImpactClientConfig.ENABLE_GOGGLES_BLOCK_TOOLTIP.get()) {
            return;
        }
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player == null || !(stack.getItem() instanceof BlockItem blockItem) || !wearsAviatorGoggles(player)) {
            return;
        }

        BlockState state = blockItem.getBlock().defaultBlockState();
        double baseStrength = MaterialImpactProperties.baseStrength(player.level(), BlockPos.ZERO, state);

        double mass = safeMass(player, state);
        double friction = safeFriction(state);
        double restitution = safeRestitution(state);
        double strength = MaterialImpactProperties.displayStrength(state, baseStrength);
        double toughness = MaterialImpactProperties.displayToughness(state, baseStrength);
        double brittleness = MaterialImpactProperties.brittleness(state);

        event.getToolTip().add(Component.translatable("sabletrueimpact.title").withStyle(ChatFormatting.DARK_AQUA));
        event.getToolTip().add(Component.translatable("sabletrueimpact.mass", mass));
        event.getToolTip().add(Component.translatable("sabletrueimpact.friction", friction));
        event.getToolTip().add(Component.translatable("sabletrueimpact.restitution", restitution));
        event.getToolTip().add(Component.translatable("sabletrueimpact.strength", strength));
        event.getToolTip().add(Component.translatable("sabletrueimpact.toughness", toughness));
        event.getToolTip().add(Component.translatable("sabletrueimpact.brittleness", brittleness));
    }

    private static boolean wearsAviatorGoggles(Player player) {
        return isAviatorGoggles(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private static boolean isAviatorGoggles(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return AERONAUTICS_GOGGLES.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static double safeMass(Player player, BlockState state) {
        try {
            return Math.max(0.0, PhysicsBlockPropertyHelper.getMass(player.level(), BlockPos.ZERO, state));
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    private static double safeFriction(BlockState state) {
        try {
            return PhysicsBlockPropertyHelper.getFriction(state);
        } catch (RuntimeException e) {
            return 1.0;
        }
    }

    private static double safeRestitution(BlockState state) {
        try {
            return PhysicsBlockPropertyHelper.getRestitution(state);
        } catch (RuntimeException e) {
            return 0.0;
        }
    }
}
