/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.ryanhcode.sable.physics.impl.rapier.Rapier3D
 *  dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline
 *  it.unimi.dsi.fastutil.ints.Int2ObjectMap
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ElasticPairReaction;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={RapierPhysicsPipeline.class}, remap=false)
public abstract class RapierPhysicsPipelineMixin {
    @Shadow
    @Final
    private Int2ObjectMap<?> activeSubLevels;

    // Bug 2 fix: flush queued pair-reaction impulses at the start of pre-step (before Rapier runs)
    // so they are applied in a clean physics state, avoiding the "island should be awake" panic.
    @Inject(method={"prePhysicsTicks"}, at=@At(value="HEAD"))
    private void sabletrueimpact$flushPendingImpulses(CallbackInfo ci) {
        ElasticPairReaction.flushPendingImpulses();
    }

    @Redirect(method={"processCollisionEffects"}, at=@At(value="INVOKE", target="Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(I)[D"))
    private double[] sabletrueimpact$applyElasticPairReaction(int sceneId) {
        double[] collisions = Rapier3D.clearCollisions((int)sceneId);
        ElasticPairReaction.apply(sceneId, this.activeSubLevels, collisions);
        return collisions;
    }
}

