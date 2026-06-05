package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wraps the BlockSubLevelCollisionCallback returned by RapierVoxelColliderBakery
 * to support T-1 (callback thread identity) and T-2 (callback coordinate identification).
 *
 * Observation-only: returns the original CollisionResult unchanged.
 * [safety] No block modifications, no impulse applications inside this wrapper.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery",
       remap = false)
public abstract class DiagnosticCallbackWrapperMixin {

    @Redirect(
            method = "buildPhysicsDataForBlock",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;" +
                              "sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)" +
                              "Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;",
                     remap = false),
            remap = false
    )
    private BlockSubLevelCollisionCallback wrapCallbackForDiagnostics(BlockState state) {
        BlockSubLevelCollisionCallback original = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_T1_CALLBACK_THREAD)
                && !DiagnosticConfig.is(DiagnosticConfig.LOG_T2_CALLBACK_COORD)) {
            return original;
        }
        return new DiagnosticCallbackWrapper(original, state);
    }

    /**
     * Inner wrapper: logs T-1/T-2 data before delegating to original.
     * Thread identity recorded here — matches the thread on which Rapier3D.step() fires callbacks.
     */
    private static final class DiagnosticCallbackWrapper implements BlockSubLevelCollisionCallback {

        private final BlockSubLevelCollisionCallback delegate;
        private final BlockState state;

        DiagnosticCallbackWrapper(BlockSubLevelCollisionCallback delegate, BlockState state) {
            this.delegate = delegate;
            this.state = state;
        }

        @Override
        public CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity) {
            // T-1: record callback thread identity
            // [C10-inferred] expected to be server thread; needs runtime verification
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T1_CALLBACK_THREAD)) {
                Thread current = Thread.currentThread();
                ExperimentLog.info("[T-1] callbackThread=\"{}\" id={} isDaemon={} " +
                                   "impactVelocity={} pos=({},{},{}) hitPos=({},{},{})",
                        current.getName(), current.getId(), current.isDaemon(),
                        String.format("%.4f", impactVelocity),
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.3f", hitPos.x),
                        String.format("%.3f", hitPos.y),
                        String.format("%.3f", hitPos.z));
            }

            // T-2: log raw (x,y,z) for coordinate space identification
            // [C5][T-2 pending] space of (pos) is UNCONFIRMED — could be plot-local, world, or embedded
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T2_CALLBACK_COORD)) {
                ExperimentLog.info("[T-2] pos_raw=({},{},{}) hitPos_raw=({},{},{}) " +
                                   "impactVelocity={} blockState={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.3f", hitPos.x),
                        String.format("%.3f", hitPos.y),
                        String.format("%.3f", hitPos.z),
                        String.format("%.4f", impactVelocity),
                        state);
            }

            // Delegate to original — observation only
            if (delegate != null) return delegate.sable$onCollision(pos, hitPos, impactVelocity);
            return CollisionResult.NONE;
        }
    }
}
