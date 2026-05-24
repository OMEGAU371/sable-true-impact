/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback
 *  dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback
 *  net.minecraft.world.level.block.state.BlockState
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ImpactCallbackDecider;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets={"dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery"}, remap=false)
public abstract class RapierVoxelColliderBakeryMixin {
    @Redirect(method={"buildPhysicsDataForBlock"}, at=@At(value="INVOKE", target="Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;"))
    private BlockSubLevelCollisionCallback sabletrueimpact$wrapCollisionCallback(BlockState state) {
        BlockSubLevelCollisionCallback original = BlockWithSubLevelCollisionCallback.sable$getCallback((BlockState)state);
        BlockSubLevelCollisionCallback resolved = ImpactCallbackDecider.callbackFor(state, original);
        // beta.11-diag: log which callback ends up attached to belt/shaft.
        if (com.example.sabletrueimpact.BeltDiag.tracked(state)) {
            try {
                java.lang.String origName = original == null ? "null" : original.getClass().getName();
                java.lang.String finalName = resolved == null ? "null" : resolved.getClass().getName();
                java.lang.String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                org.apache.logging.log4j.LogManager.getLogger("BeltDiag").info(
                    "[bakery] {} : sable$orig={} -> resolved={}",
                    id, origName, finalName);
            } catch (Throwable ignored) {}
        }
        return resolved;
    }
}

