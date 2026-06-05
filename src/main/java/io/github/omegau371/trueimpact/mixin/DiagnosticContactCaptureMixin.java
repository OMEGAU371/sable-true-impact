package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ContactLogger;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Captures the raw contact array from Rapier3D.clearCollisions() for T-3, T-5, T-6.
 *
 * [source-proven] clearCollisions called in RapierPhysicsPipeline.processCollisionEffects()
 * which is called from postPhysicsTicks() after all substeps complete.
 *
 * [C5-T-5] The returned array covers ALL substeps — substep attribution is UNCONFIRMED.
 * [C3] forceAmountRaw unit is UNKNOWN; ContactLogger does not rename or reinterpret it.
 *
 * Observation-only: passes the original double[] through unchanged.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",
       remap = false)
public abstract class DiagnosticContactCaptureMixin {

    @Shadow @Final
    private ServerLevel level;

    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(I)[D",
                     remap = false),
            remap = false
    )
    private double[] captureContactsForDiagnostics(int sceneId) {
        double[] data = Rapier3D.clearCollisions(sceneId);
        if (DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) {
            SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
            int substepCount = (system != null) ? system.getConfig().substepsPerTick : -1;
            ContactLogger.onClearCollisions(data, level.getGameTime(), substepCount);
        }
        return data;
    }
}
