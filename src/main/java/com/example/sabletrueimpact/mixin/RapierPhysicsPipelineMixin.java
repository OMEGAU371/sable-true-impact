package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ElasticPairReaction;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RapierPhysicsPipeline.class, remap = false)
public abstract class RapierPhysicsPipelineMixin {
    @Shadow
    @Final
    private Int2ObjectMap<?> activeSubLevels;

    @Redirect(method = "processCollisionEffects", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(I)[D"))
    private double[] sabletrueimpact$applyElasticPairReaction(int sceneId) {
        double[] collisions = Rapier3D.clearCollisions(sceneId);
        ElasticPairReaction.apply(sceneId, activeSubLevels, collisions);
        return collisions;
    }
}
