package com.example.sabletrueimpact.mixin;

import org.spongepowered.asm.mixin.Mixin;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {SubLevelPhysicsSystem.class}, remap = false)
public abstract class SableCollisionMixin {

    @Inject(method = "tickPipelinePhysics", at = @At("HEAD"))
    private void sabletrueimpact$onPhysicsTick(CallbackInfo ci) {
        // Placeholder for hooking into collision resolution
    }
}
