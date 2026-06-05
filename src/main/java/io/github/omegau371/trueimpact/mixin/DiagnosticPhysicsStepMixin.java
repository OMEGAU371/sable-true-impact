package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects PRE_STEP and POST_STEP snapshot capture into the substep loop.
 *
 * PRE_STEP fires just before Rapier3D.step() — position is the pre-step pose.
 * POST_STEP fires just after updateAllPoses() — logicalPose() reflects post-step position.
 *
 * Observation-only: no block modifications, no impulse applications.
 * [C10-inferred] Runs on server thread; T-1 experiment verifies.
 */
@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class DiagnosticPhysicsStepMixin {

    /**
     * PRE_STEP: inject before PhysicsPipeline.physicsTick() is called.
     * At this point logicalPose() still holds the pre-step positions.
     * [SP: SubLevelPhysicsSystem.java:216]
     */
    @Inject(
            method = "tickPipelinePhysics",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;physicsTick(D)V",
                     shift = At.Shift.BEFORE,
                     remap = false),
            remap = false
    )
    private void capturePreStep(ServerSubLevelContainer container, CallbackInfo ci) {
        SableEventBridge.onPreStep((SubLevelPhysicsSystem) (Object) this);
    }

    /**
     * POST_STEP: inject after updateAllPoses() is called.
     * logicalPose() now reflects the post-step positions from Rapier.
     * getLinearVelocity() returns the post-step Rapier velocity.
     * [SP: SubLevelPhysicsSystem.java:218, updateAllPoses calls readPose]
     */
    @Inject(
            method = "tickPipelinePhysics",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;updateAllPoses(Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;)V",
                     shift = At.Shift.AFTER,
                     remap = false),
            remap = false
    )
    private void capturePostStep(ServerSubLevelContainer container, CallbackInfo ci) {
        SableEventBridge.onPostStep((SubLevelPhysicsSystem) (Object) this);
    }
}
