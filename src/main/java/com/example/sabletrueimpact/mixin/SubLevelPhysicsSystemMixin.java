/*
 *  dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer
 *  dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.sabletrueimpact.mixin;

import com.example.sabletrueimpact.ElasticPairReaction;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SubLevelPhysicsSystem.class}, remap=false)
public abstract class SubLevelPhysicsSystemMixin {

    // tickPipelinePhysics substep loop: physicsTick() (Rapier3D.step) → processSubLevelRemovals()
    // → updateAllPoses(). A rope-joint/contact divergence blows up inside physicsTick() and, the
    // moment it hits an extreme Y / NaN pose, processSubLevelRemovals() frees the collider while
    // Rapier's narrow phase still holds a stale contact pair → narrow_phase.rs:1115 non-unwinding
    // panic (hard JVM abort). The once-per-tick prePhysicsTicks clamp ran before this loop and
    // never gets another chance. Injecting right before processSubLevelRemovals() — every substep
    // — is the only window that caps the runaway while velocity is still finite (and thus
    // repairable via the add-delta API), removing the extreme-Y trigger before Sable can remove
    // the body.
    @Inject(method={"tickPipelinePhysics"},
            at=@At(value="INVOKE",
                   target="Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;processSubLevelRemovals()V",
                   remap=false),
            remap=false)
    private void sabletrueimpact$clampBeforeRemoval(ServerSubLevelContainer container, CallbackInfo ci) {
        ElasticPairReaction.clampRunawaySubLevels(container.getAllSubLevels());
        // fork_11: predictive substep terrain sweep DISABLED. The fork_8 block-aware check used
        // `worldPos - AABB-center` as a body-frame approximation, ignoring rotation; rotated /
        // tilted structures had AABB-corner positions that mapped to non-air sub-level blocks in
        // body-frame coords, triggering "destruction at a distance" (隔山打牛) — craters formed
        // under the AABB corners far from the structure's actual visual body. With fork_10's
        // off-by-one fix in per-contact, the post-step Java path handles destruction correctly
        // and the predictive sweep is no longer needed as a fallback. Keep the method available
        // (still defined) in case we add a proper pose-inverse-transformed version later.
        // ElasticPairReaction.predictiveSubLevelTerrainSweep(container.getAllSubLevels());
    }
}
