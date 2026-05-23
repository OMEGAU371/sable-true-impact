/*
 *  dev.ryanhcode.sable.sublevel.ServerSubLevel
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ElasticPairReaction;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.0.5-fork_28: the definitive fix for the narrow_phase crash family (唯絷是惶 / sable#950).
//
// ServerSubLevel.tick() force-removes any sub-level whose bounding box leaves the configured
// Y range, via:  Sable.LOGGER.info("... extreme Y coordinate range, removing", this);
//                this.markRemoved();
// That markRemoved() frees the Rapier collider while a stale narrow-phase contact pair still
// references it → narrow_phase.rs:1115 "No element at index" non-unwinding panic (uncatchable
// JVM abort). Every crash report the user has filed shows this exact "extreme Y ... removing"
// line moments before the panic.
//
// The velocity clamp could only ever REDUCE the runaway frequency — a violated rope joint
// flings a body past the Y limit inside a single solver step, before any post-step clamp
// runs. So we attack the removal itself: this mixin injects at the LOGGER.info call (the
// unambiguous anchor inside tick()'s removal branch) and hands the sub-level to
// ElasticPairReaction.rescueRunawaySubLevel(), which teleports the body back into a sane Y
// (Rapier teleport repositions the EXISTING collider — it never frees it) and zeroes its
// velocity. If the rescue succeeds we cancel tick() so the log line and markRemoved() never
// run: the body survives, Sable never frees a collider, the crash trigger is eliminated.
@Mixin(value = {ServerSubLevel.class}, remap = false)
public abstract class ServerSubLevelMixin {

    @Inject(method = {"tick"},
            at = @At(value = "INVOKE",
                     target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V",
                     remap = false),
            cancellable = true,
            remap = false)
    private void sabletrueimpact$rescueBeforeRemoval(CallbackInfo ci) {
        if (ElasticPairReaction.rescueRunawaySubLevel((ServerSubLevel) (Object) this)) {
            ci.cancel();
        }
    }
}
