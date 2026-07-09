package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import io.github.omegau371.trueimpact.sable.SableVictimCapture;
import io.github.omegau371.trueimpact.sable.TrueImpactBlockCallbackRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts BlockWithSubLevelCollisionCallback.sable$getCallback during terrain baking
 * and returns a TrueImpactWrapper for EVERY block (not just FRAGILE ones).
 *
 * Previously this mixin only wrapped non-null callbacks (FRAGILE blocks). With this change,
 * we inject a callback for all blocks so that sable$onCollision fires for each block that
 * a physics body touches. The per-block BlockPos from onCollision is fed into
 * TrueImpactBlockCallbackRegistry, which SableImpactCapture drains after clearCollisions().
 *
 * Physics behavior is unchanged: TrueImpactWrapper returns CollisionResult.NONE for
 * non-FRAGILE blocks (same as if no callback were present). For FRAGILE blocks, the
 * original callback (FragileBlockCallback) is still invoked and its result returned.
 *
 * T-1 and T-2 diagnostics are preserved inside the wrapper.
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
            require = 0,
            remap = false
    )
    private BlockSubLevelCollisionCallback wrapCallback(BlockState state) {
        BlockSubLevelCollisionCallback original = BlockWithSubLevelCollisionCallback.sable$getCallback(state);
        // Always wrap: for non-FRAGILE blocks original==null; the wrapper still fires
        // sable$onCollision and records the block position, but returns NONE so physics is unchanged.
        return new TrueImpactWrapper(original, state);
    }

    private static final class TrueImpactWrapper implements BlockSubLevelCollisionCallback {

        private final BlockSubLevelCollisionCallback delegate; // null for non-FRAGILE blocks
        private final BlockState state;

        TrueImpactWrapper(BlockSubLevelCollisionCallback delegate, BlockState state) {
            this.delegate = delegate;
            this.state    = state;
        }

        @Override
        public CollisionResult sable$onCollision(BlockPos pos, BlockPos otherPos,
                                                  Vector3d velocity, double speed) {
            ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String blockId = (loc != null) ? loc.toString() : "unknown";

            // ── Damage pipeline: record to registry (unconditional, no flag gate) ──
            // pos is world-space for terrain blocks (T-2 confirmed).
            // Embedded-level coords are filtered inside the registry.
            //
            // Skip thin blocks (collisionShape maxY < 0.5): Sable fires onCollision for
            // the entire block-grid cell, so low-profile blocks (rails, carpets, slabs at
            // bottom) produce false positives when a body passes over their empty upper half.
            // EmptyBlockGetter is sufficient for single-block shapes that don't depend on
            // neighbours; fall back to recording on any exception.
            boolean isThinBlock = false;
            double shapeMaxY = Double.NaN;
            boolean shapeEmpty = false;
            try {
                net.minecraft.world.phys.shapes.VoxelShape shape =
                        state.getCollisionShape(
                                net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                                pos,
                                net.minecraft.world.phys.shapes.CollisionContext.empty());
                shapeEmpty = shape.isEmpty();
                shapeMaxY  = shapeEmpty ? Double.NaN : shape.bounds().maxY;
                // create:track* are intentionally allowed through even though maxY < 0.5,
                // so that Path 1 can accumulate damage on them from physical impacts.
                boolean isTrackBlock = blockId.startsWith("create:track");
                isThinBlock = !shapeEmpty && shapeMaxY < 0.5 && !isTrackBlock;
            } catch (Throwable ignored) {}
            if (!isThinBlock) {
                TrueImpactBlockCallbackRegistry.record(
                        pos.getX(), pos.getY(), pos.getZ(), blockId, speed);
            }
            if (io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig.LOG_BLOCK_CALLBACK) {
                org.apache.logging.log4j.LogManager.getLogger("true_impact")
                        .info("[TI-CB] block={} pos=({},{},{}) speed={} shapeEmpty={} shapeMaxY={} isThin={} recorded={}",
                                blockId, pos.getX(), pos.getY(), pos.getZ(),
                                String.format("%.4f", speed),
                                shapeEmpty,
                                Double.isNaN(shapeMaxY) ? "N/A" : String.format("%.3f", shapeMaxY),
                                isThinBlock, !isThinBlock);
            }

            // ── Legacy victim capture for T-1/T-2 diagnostics ─────────────────────
            boolean posLooksWorld = Math.abs(pos.getX()) <= 1_000_000
                    && Math.abs(pos.getZ()) <= 1_000_000;
            SableVictimCapture.captureCallbackBlock(blockId,
                    pos.getX(), pos.getY(), pos.getZ(), posLooksWorld);

            // ── T-1: callback thread vs server thread ──────────────────────────────
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T1_CALLBACK_THREAD)) {
                Thread cb  = Thread.currentThread();
                Thread srv = SableEventBridge.capturedServerThread;
                boolean same = (srv != null) && (cb == srv);
                ExperimentLog.info("[T-1] callbackThread=\"{}\" id={} isDaemon={}" +
                        " serverThread=\"{}\" sameThread={} speed={}",
                        cb.getName(), cb.getId(), cb.isDaemon(),
                        srv != null ? srv.getName() : "unknown(not_yet_captured)",
                        same, String.format("%.4f", speed));
            }

            // ── T-2: raw callback coordinates ──────────────────────────────────────
            if (DiagnosticConfig.is(DiagnosticConfig.LOG_T2_CALLBACK_COORD)) {
                boolean mayBeEmbedded = Math.abs(pos.getX()) > 1_000_000
                        || Math.abs(pos.getZ()) > 1_000_000;
                ExperimentLog.info("[T-2] pos_raw=({},{},{}) velocity=({},{},{}) speed={}" +
                        " mayBeEmbedded={} blockState={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.3f", velocity.x),
                        String.format("%.3f", velocity.y),
                        String.format("%.3f", velocity.z),
                        String.format("%.4f", speed),
                        mayBeEmbedded, state);
            }

            // ── Delegate to original callback (FRAGILE blocks: FragileBlockCallback) ─
            if (delegate != null) {
                return delegate.sable$onCollision(pos, otherPos, velocity, speed);
            }
            return CollisionResult.NONE;
        }
    }
}
