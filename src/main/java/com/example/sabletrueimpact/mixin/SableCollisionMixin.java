package com.example.sabletrueimpact.mixin;

import org.spongepowered.asm.mixin.Mixin;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import com.example.sabletrueimpact.ElasticPairReaction;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {SubLevelPhysicsSystem.class}, remap = false)
public abstract class SableCollisionMixin {

    @Inject(method = "tickPipelinePhysics", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;step(I[D)I", remap = false), remap = false)
    private void sabletrueimpact$onPhysicsStep(CallbackInfo ci) {
        try {
            SubLevelPhysicsSystem system = (SubLevelPhysicsSystem)(Object)this;
            
            // Debug: This should appear in logs every physics tick
            // System.out.println("[TrueImpact] Physics step intercepted!");

            java.lang.reflect.Field activeField = null;
            try {
                activeField = SubLevelPhysicsSystem.class.getDeclaredField("activeSubLevels");
            } catch (NoSuchFieldException e) {
                // Fallback for possible obfuscation or renames
                for (java.lang.reflect.Field f : SubLevelPhysicsSystem.class.getDeclaredFields()) {
                    if (it.unimi.dsi.fastutil.ints.Int2ObjectMap.class.isAssignableFrom(f.getType())) {
                        activeField = f;
                        break;
                    }
                }
            }
            
            if (activeField == null) return;
            activeField.setAccessible(true);
            it.unimi.dsi.fastutil.ints.Int2ObjectMap<?> activeSubLevels = (it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>) activeField.get(system);
            
            java.lang.reflect.Field collisionsField = null;
            try {
                collisionsField = SubLevelPhysicsSystem.class.getDeclaredField("collisions");
            } catch (NoSuchFieldException e) {
                for (java.lang.reflect.Field f : SubLevelPhysicsSystem.class.getDeclaredFields()) {
                    if (f.getType() == double[].class) {
                        collisionsField = f;
                        break;
                    }
                }
            }
            
            if (collisionsField == null) return;
            collisionsField.setAccessible(true);
            double[] collisions = (double[]) collisionsField.get(system);
            
            if (collisions != null && collisions.length > 0) {
                ElasticPairReaction.apply(0, activeSubLevels, collisions);
            }
        } catch (Exception e) {
            // Log the error so we can see it in latest.log
            System.err.println("[TrueImpact] Critical failure in physics mixin: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
