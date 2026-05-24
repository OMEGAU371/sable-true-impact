/*
 *  dev.ryanhcode.sable.api.physics.object.rope.RopeHandle
 *  dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  org.joml.Vector3dc
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.RopeBindingRegistry;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle;
import dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.0.5-fork_1: mirror every rope attachment Sable establishes (START and END) into
// RopeBindingRegistry so it can answer "is this sub-level a rope endpoint" by object identity.
// The destruction sites use that to skip all block changes on rope-connected sub-levels (the
// far END subLevel is a plain Java arg here, so both ends are captured reliably).
// RapierRopeHandle is a record (int sceneId, long handle); shadow those component fields.
@Mixin(value={RapierRopeHandle.class}, remap=false)
public abstract class RapierRopeHandleMixin {
    @Shadow @Final private int sceneId;
    @Shadow @Final private long handle;

    @Inject(method={"setAttachment"}, at=@At(value="HEAD"), remap=false)
    private void sabletrueimpact$mirrorAttachment(RopeHandle.AttachmentPoint attachmentPoint,
                                                  Vector3dc location, ServerSubLevel subLevel,
                                                  CallbackInfo ci) {
        // fork_29: also pass the RopeHandle itself (this) so RopeBindingRegistry can later
        // cut the rope cleanly via RopeHandle.remove(). RapierRopeHandle implements RopeHandle.
        RopeBindingRegistry.record(this.sceneId, this.handle,
            attachmentPoint == RopeHandle.AttachmentPoint.END,
            (RopeHandle) (Object) this,
            subLevel, location.x(), location.y(), location.z());
    }

    @Inject(method={"remove"}, at=@At(value="HEAD"), remap=false)
    private void sabletrueimpact$forgetOnRemove(CallbackInfo ci) {
        RopeBindingRegistry.forget(this.handle);
    }
}
