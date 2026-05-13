package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ImpactCallbackDecider;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery", remap = false)
public abstract class RapierVoxelColliderBakeryMixin {
    @Redirect(
            method = "buildPhysicsDataForBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;"
            )
    )
    private BlockSubLevelCollisionCallback sabletrueimpact$wrapCollisionCallback(BlockState state) {
        BlockSubLevelCollisionCallback original = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        return ImpactCallbackDecider.callbackFor(state, original);
    }
}
