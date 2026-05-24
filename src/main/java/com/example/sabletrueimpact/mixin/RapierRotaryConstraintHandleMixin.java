/*
 *  dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration
 *  dev.ryanhcode.sable.physics.impl.rapier.Rapier3D
 *  dev.ryanhcode.sable.physics.impl.rapier.constraint.rotary.RapierRotaryConstraintHandle
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  net.minecraft.server.level.ServerLevel
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ConstraintHandleAccess;
import com.example.sabletrueimpact.RopeBindingRegistry;
import dev.ryanhcode.sable.api.physics.constraint.rotary.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.rotary.RapierRotaryConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// beta.7 — auto-detect Sable rotary constraint anchors (cardan shafts, etc.) and protect
// the blocks at their world positions from any destruction. This is what catches modlists
// like b1rbb's where `SableKardanwelleMod` was creating rotary constraints whose anchor
// blocks (cardan_shaft) were getting destroyed → extreme-Y crash family.
//
// Hook point: `RapierRotaryConstraintHandle.create(...)` at RETURN. By then the native
// constraint has been created (so the handle ID is final) and we can record both anchor
// world positions (pos1, pos2 from the config) keyed by that handle. The base class's
// remove() mixin will clean up later.
@Mixin(value = {RapierRotaryConstraintHandle.class}, remap = false)
public abstract class RapierRotaryConstraintHandleMixin {

    @Inject(method = {"create"}, at = @At(value = "RETURN"), remap = false)
    private static void sabletrueimpact$recordAnchor(
            ServerLevel serverLevel,
            ServerSubLevel sublevelA,
            ServerSubLevel sublevelB,
            RotaryConstraintConfiguration config,
            CallbackInfoReturnable<RapierRotaryConstraintHandle> cir) {
        try {
            RapierRotaryConstraintHandle ret = cir.getReturnValue();
            if (ret == null || serverLevel == null || config == null) {
                return;
            }
            long handle = ConstraintHandleAccess.getHandle(ret);
            int sceneId = Rapier3D.getID(serverLevel);
            RopeBindingRegistry.recordConstraint(sceneId, handle,
                config.pos1().x(), config.pos1().y(), config.pos1().z(),
                config.pos2().x(), config.pos2().y(), config.pos2().z());
        } catch (Throwable t) {
            // Recording is best-effort — never let it crash constraint creation.
        }
    }
}
