package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ContactLogger;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import io.github.omegau371.trueimpact.sable.SableImpactCapture;
import io.github.omegau371.trueimpact.sable.SableVictimCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;

/**
 * Intercepts Rapier3D.clearCollisions() to feed the damage pipeline and diagnostic logs.
 *
 * Two independent paths run after clearCollisions() returns:
 *
 *   PATH A -- damage pipeline (SableImpactCapture + victim detection):
 *     Runs UNCONDITIONALLY on every physics tick regardless of diagnostic flags.
 *     MUST NOT be placed inside any DiagnosticConfig gate.
 *     Diagnostic flags control only log/chat output, never whether capture runs.
 *
 *   PATH B -- diagnostic logging (ContactLogger):
 *     Runs only when LOG_RAW_CONTACTS is enabled.
 *     Safe to gate; logs have no game-side effects.
 *
 * snaps (lastPostSnaps) and tickStartVels are always populated unconditionally
 * by SableEventBridge regardless of diagnostic flags.
 *
 * [T-5] clearCollisions is called once after all substeps -- array covers ALL substeps.
 * [C2]  Bodies not found in activeSubLevels -> NON_ACTIVE_SUBLEVEL_BODY, NOT "terrain".
 *
 * Original double[] is passed through unchanged.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",
       remap = false)
public abstract class DiagnosticContactCaptureMixin {

    @Shadow @Final private ServerLevel level;

    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(I)[D",
                     remap = false),
            remap = false
    )
    private double[] captureContactData(int sceneId) {
        double[] data = Rapier3D.clearCollisions(sceneId);

        SubLevelPhysicsSystem system   = SubLevelPhysicsSystem.get(level);
        int substepCount               = (system != null) ? system.getConfig().substepsPerTick : -1;
        Map<Integer, BodySnapshot> snaps        = SableEventBridge.getLastPostSnapshots();
        Map<Integer, double[]>    tickStartVels = SableEventBridge.getTickStartVels();
        long tick                      = level.getGameTime();
        String levelKey                = level.dimension().location().toString();

        // Contact-point block detection for world-vs-active contacts (PATH A, unconditional).
        // BlockSubLevelCollisionCallback fires only for blocks implementing
        // BlockWithSubLevelCollisionCallback. Most vanilla blocks have null callbacks.
        // Falls back to transforming the contact point to world space and sampling
        // level.getBlockState() at and near that position.
        // Runs BEFORE process() so SableImpactCapture can read SableVictimCapture.
        // Skip only if callback path already captured data this tick (callback is preferred).
        if (!SableVictimCapture.hasCaptureThisTick()) {
            tryContactPointBlockDetection(data, snaps);
        }

        // PATH A: damage pipeline -- always runs, no diagnostic gate.
        SableImpactCapture.process(data, tick, substepCount, snaps, tickStartVels, levelKey);

        // PATH B: diagnostic logging -- gated on LOG_RAW_CONTACTS.
        if (DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) {
            ContactLogger.onClearCollisions(data, tick, substepCount, snaps, tickStartVels);
        }

        return data;
    }

    // -- Phase 1D: contact-point block detection -----------------------------------

    /**
     * Scans clearCollisions data for world-vs-active contacts. For each found pair,
     * transforms the active body's contact point (BODY_COM_LOCAL) to WORLD space,
     * then samples level.getBlockState() at and around that position.
     *
     * Fills SableVictimCapture with the first detected block, source=CONTACT_POINT_SAMPLE.
     * Only called when no callback data already exists (callback is preferred when available).
     *
     * posX/posY/posZ: BodySnapshot.posX/Y/Z = logicalPose().position() = WORLD [SP].
     * Transform: world = Q * bodyComLocal + position [SP: coordinate-systems.md section 3].
     * hasChunkAt guard prevents forced chunkgen at embedded-level coords (~4e7).
     */
    private void tryContactPointBlockDetection(double[] data, Map<Integer, BodySnapshot> snaps) {
        if (data == null) return;
        int count = data.length / 15;
        for (int i = 0; i < count; i++) {
            int base = i * 15;
            int idA = (int) data[base];
            int idB = (int) data[base + 1];
            BodySnapshot snapA = snaps.get(idA);
            BodySnapshot snapB = snaps.get(idB);

            if (snapA == null && snapB == null) continue; // both unknown
            if (snapA != null && snapB != null) continue; // active-vs-active

            // Exactly one active body; use ITS contact point in ITS local frame
            BodySnapshot activeSnap = (snapA != null) ? snapA : snapB;
            double lpX, lpY, lpZ;
            if (snapA != null) {
                // A is active; localPointA in offsets 9-11
                lpX = data[base + 9];
                lpY = data[base + 10];
                lpZ = data[base + 11];
            } else {
                // B is active; localPointB in offsets 12-14
                lpX = data[base + 12];
                lpY = data[base + 13];
                lpZ = data[base + 14];
            }

            // Transform body-COM-local -> world
            double[] wcp = transformLocalToWorld(activeSnap, lpX, lpY, lpZ);

            // Sample block at contact point and adjacent positions
            BlockPos candidate = sampleNearestSolidBlock(wcp);
            if (candidate != null) {
                ResourceLocation loc = BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(candidate).getBlock());
                if (loc != null) {
                    SableVictimCapture.captureContactPointBlock(loc.toString(),
                            candidate.getX(), candidate.getY(), candidate.getZ());
                    return; // first successful detection is sufficient for diagnostics
                }
            }
        }
    }

    /**
     * Rotates body-COM-local vector to world space and adds body world position.
     * Formula: world = Q * local + pos  [SP: RapierPhysicsPipeline.java:254-260]
     */
    private static double[] transformLocalToWorld(BodySnapshot s, double lx, double ly, double lz) {
        double qx = s.oriX(), qy = s.oriY(), qz = s.oriZ(), qw = s.oriW();
        double tx = 2.0 * (qy * lz - qz * ly);
        double ty = 2.0 * (qz * lx - qx * lz);
        double tz = 2.0 * (qx * ly - qy * lx);
        return new double[]{
            lx + qw * tx + qy * tz - qz * ty + s.posX(),
            ly + qw * ty + qz * tx - qx * tz + s.posY(),
            lz + qw * tz + qx * ty - qy * tx + s.posZ()
        };
    }

    /**
     * Tries to find a non-air block at or adjacent to the given world contact point.
     * Contact point is on the active body's surface; the victim block is just outside.
     * Returns null if no solid block found or if hasChunkAt fails for all candidates.
     */
    private BlockPos sampleNearestSolidBlock(double[] wcp) {
        BlockPos center = BlockPos.containing(wcp[0], wcp[1], wcp[2]);
        BlockPos[] candidates = {
            center,
            center.below(),
            center.north(), center.south(),
            center.west(), center.east(),
            center.above()
        };
        for (BlockPos pos : candidates) {
            if (!level.hasChunkAt(pos)) continue;
            if (!level.getBlockState(pos).isAir()) return pos;
        }
        return null;
    }
}
