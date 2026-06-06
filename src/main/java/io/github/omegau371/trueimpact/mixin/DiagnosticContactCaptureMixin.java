package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ContactLogger;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.Map;

/**
 * Captures raw contact array from Rapier3D.clearCollisions() for T-3, T-5, T-6.
 * Passes the most recent POST_STEP body snapshots from SableEventBridge for correlation.
 *
 * [T-5] clearCollisions is called once after all substeps — array covers ALL substeps.
 *        substepIndex is NOT available here. ContactLogger outputs this explicitly.
 * [C2]  Bodies not found in activeSubLevels → NON_ACTIVE_SUBLEVEL_BODY, NOT "terrain".
 *
 * Observation-only: original double[] passed through unchanged.
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
        if (DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) {
            SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
            int substepCount = (system != null) ? system.getConfig().substepsPerTick : -1;
            Map<Integer, BodySnapshot> snaps = SableEventBridge.getLastPostSnapshots();
            // tickStartVels: populated at substep 0 PRE_STEP; used by T-3-MISS pairwise scan
            Map<Integer, double[]> tickStartVels = SableEventBridge.getTickStartVels();
            ContactLogger.onClearCollisions(data, level.getGameTime(), substepCount, snaps, tickStartVels);
        }
        return data;
    }
}
