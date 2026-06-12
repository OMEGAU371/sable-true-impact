package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import io.github.omegau371.trueimpact.sable.SableVictimCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wraps non-null BlockSubLevelCollisionCallbacks to support T-1 and T-2.
 *
 * Transparency rules:
 *   - The wrapper is applied at bake time UNCONDITIONALLY for non-null callbacks.
 *     Runtime flags (LOG_T1_CALLBACK_THREAD, LOG_T2_CALLBACK_COORD) only control logging.
 *     This avoids needing re-bake when diagnostics are enabled after startup.
 *   - When original callback is null → return null (preserve Rapier's null = no-callback behavior).
 *     [C2-audit] MUST NOT replace null with a NONE-returning wrapper.
 *   - The wrapper always returns the original callback's result unchanged.
 *
 * T-1 records: callbackThread name/id, captured server thread name/id, sameThread boolean.
 * T-2 records: raw pos(x,y,z), hitPos(x,y,z); coordinate space transformations
 *              to WORLD/PLOT_RELATIVE/BODY_COM_LOCAL candidates where context available,
 *              otherwise labeled "unavailable".
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
    private BlockSubLevelCollisionCallback wrapNonNullCallback(BlockState state) {
        BlockSubLevelCollisionCallback original = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        // [C2-audit] KEEP null as null — do not substitute NONE-returning wrapper
        if (original == null) return null;
        // Always wrap non-null; runtime flags only control logging inside the wrapper
        return new DiagnosticCallbackWrapper(original, state);
    }

    private static final class DiagnosticCallbackWrapper implements BlockSubLevelCollisionCallback {

        private final BlockSubLevelCollisionCallback delegate;
        private final BlockState state;

        DiagnosticCallbackWrapper(BlockSubLevelCollisionCallback delegate, BlockState state) {
            this.delegate = delegate;
            this.state = state;
        }

        @Override
        public CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity) {
            // Victim capture: record block type for world-vs-active detection (unconditional).
            // Fires during Rapier3D.step() callbacks. Read-only; no world mutation.
            // pos space is UNCONFIRMED (T-2 pending); posLooksWorld heuristic excludes
            // embedded-level coords (~4e7 range).
            boolean posLooksWorld = Math.abs(pos.getX()) <= 1_000_000
                    && Math.abs(pos.getZ()) <= 1_000_000;
            ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String blockId = (loc != null) ? loc.toString() : "unknown";
            SableVictimCapture.captureCallbackBlock(blockId,
                    pos.getX(), pos.getY(), pos.getZ(), posLooksWorld);

            // T-1: callback thread vs captured server thread
            // Logging gate: LOG_T1_CALLBACK_THREAD; wrapper presence is unconditional
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T1_CALLBACK_THREAD)) {
                Thread cb = Thread.currentThread();
                Thread srv = SableEventBridge.capturedServerThread;
                boolean same = (srv != null) && (cb == srv);
                ExperimentLog.info("[T-1] callbackThread=\"{}\" id={} isDaemon={}" +
                        " serverThread=\"{}\" sameThread={}" +
                        " impactVelocity={} [note: Sable 1.2.2 passes 0.0]",
                        cb.getName(), cb.getId(), cb.isDaemon(),
                        srv != null ? srv.getName() : "unknown(not_yet_captured)",
                        same,
                        String.format("%.4f", impactVelocity));
            }

            // T-2: raw callback coordinates for coordinate space identification
            // [C5][T-2 pending] space of pos is UNCONFIRMED — candidates below
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T2_CALLBACK_COORD)) {
                // Candidate transforms:
                // WORLD candidate: raw pos.x/y/z directly — only valid if coords are world-scale
                // PLOT_RELATIVE candidate: unavailable without SubLevel context mid-callback
                // BODY_COM_LOCAL candidate: unavailable without SubLevel pose mid-callback
                // EMBEDDED_LEVEL: would be ~4e7 range — checkable from raw value
                boolean mayBeEmbedded = Math.abs(pos.getX()) > 1_000_000
                        || Math.abs(pos.getZ()) > 1_000_000;
                ExperimentLog.info("[T-2] pos_raw=({},{},{}) hitPos_raw=({},{},{})" +
                        " mayBeEmbedded={} impactVelocity={}" +
                        " WORLD_candidate=pos_raw PLOT_RELATIVE_candidate=unavailable" +
                        " BODY_COM_LOCAL_candidate=unavailable blockState={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.3f", hitPos.x),
                        String.format("%.3f", hitPos.y),
                        String.format("%.3f", hitPos.z),
                        mayBeEmbedded,
                        String.format("%.4f", impactVelocity),
                        state);
            }

            // Observation-only: delegate and return original result unchanged
            return delegate.sable$onCollision(pos, hitPos, impactVelocity);
        }
    }
}
