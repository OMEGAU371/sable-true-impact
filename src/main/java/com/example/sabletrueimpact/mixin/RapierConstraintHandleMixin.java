/*
 *  dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.RopeBindingRegistry;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// beta.7 — clean up the constraint-anchor registry when a constraint is removed. Catches all
// 4 subclasses (Rotary/Fixed/Free/Generic) in a single mixin since they all inherit `remove()`
// from this base class.
@Mixin(value = {RapierConstraintHandle.class}, remap = false)
public abstract class RapierConstraintHandleMixin {

    @Shadow @Final protected long handle;

    @Inject(method = {"remove"}, at = @At(value = "HEAD"), remap = false)
    private void sabletrueimpact$forgetAnchor(CallbackInfo ci) {
        try {
            RopeBindingRegistry.forgetConstraint(this.handle);
        } catch (Throwable t) {
            // best-effort
        }
    }
}
