/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.sabletrueimpact.mixin;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SubLevelPhysicsSystem.class}, remap=false)
public abstract class SableCollisionMixin {
    @Inject(method={"tickPipelinePhysics"}, at={@At(value="HEAD")})
    private void sabletrueimpact$onPhysicsTick(CallbackInfo ci) {
    }
}

