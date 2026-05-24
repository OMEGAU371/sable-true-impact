/*
 *  dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration
 *  dev.ryanhcode.sable.physics.impl.rapier.Rapier3D
 *  dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  net.minecraft.server.level.ServerLevel
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ConstraintHandleAccess;
import com.example.sabletrueimpact.RopeBindingRegistry;
import dev.ryanhcode.sable.api.physics.constraint.generic.GenericConstraintConfiguration;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// beta.7 — auto-detect Sable generic (per-axis configurable) constraint anchors.
@Mixin(value = {RapierGenericConstraintHandle.class}, remap = false)
public abstract class RapierGenericConstraintHandleMixin {

    @Inject(method = {"create"}, at = @At(value = "RETURN"), remap = false)
    private static void sabletrueimpact$recordAnchor(
            ServerLevel serverLevel,
            ServerSubLevel sublevelA,
            ServerSubLevel sublevelB,
            GenericConstraintConfiguration config,
            CallbackInfoReturnable<RapierGenericConstraintHandle> cir) {
        try {
            RapierGenericConstraintHandle ret = cir.getReturnValue();
            if (ret == null || serverLevel == null || config == null) {
                return;
            }
            long handle = ConstraintHandleAccess.getHandle(ret);
            int sceneId = Rapier3D.getID(serverLevel);
            RopeBindingRegistry.recordConstraint(sceneId, handle,
                config.pos1().x(), config.pos1().y(), config.pos1().z(),
                config.pos2().x(), config.pos2().y(), config.pos2().z());
        } catch (Throwable t) {
            // best-effort
        }
    }
}
