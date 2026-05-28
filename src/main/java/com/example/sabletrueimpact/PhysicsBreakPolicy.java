package com.example.sabletrueimpact;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Centralised drop policy for physics-broken blocks.
 *
 * <p>Two layers:
 * <ul>
 *   <li>Global toggle {@link TrueImpactConfig#ENABLE_PHYSICS_BREAK_DROPS} — when false,
 *       no impact-broken block drops items.</li>
 *   <li>Per-block blacklist {@link TrueImpactConfig#NO_DROP_BLOCKS} — listed blocks never
 *       drop when broken by physics, even with the global toggle on.</li>
 * </ul>
 *
 * <p>Call {@link #shouldDrop(BlockState, boolean)} at every {@code destroyBlock} site that
 * would otherwise pass {@code dropItems=true}. The {@code defaultDrop} arg lets propagation
 * /chain-reaction paths (which pass {@code false}) opt out of the policy entirely.
 */
public final class PhysicsBreakPolicy {
    private static volatile Set<String> NO_DROP = Set.of();

    private PhysicsBreakPolicy() {}

    public static void reload() {
        try {
            Set<String> set = new HashSet<>();
            for (Object o : TrueImpactConfig.NO_DROP_BLOCKS.get()) {
                if (o instanceof String s && !s.isBlank()) set.add(s.trim());
            }
            NO_DROP = Set.copyOf(set);
        } catch (Throwable t) {
            NO_DROP = Set.of();
        }
    }

    /** Returns the {@code dropItems} flag to pass to {@code Level.destroyBlock}. */
    public static boolean shouldDrop(BlockState state, boolean defaultDrop) {
        if (!defaultDrop) return false;
        try {
            if (!((Boolean) TrueImpactConfig.ENABLE_PHYSICS_BREAK_DROPS.get()).booleanValue()) return false;
        } catch (Throwable t) {
            return defaultDrop;
        }
        if (state == null) return true;
        Set<String> blacklist = NO_DROP;
        if (blacklist.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return true;
        return !blacklist.contains(id.toString());
    }
}
