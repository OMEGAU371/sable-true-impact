package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ContactLogger;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.sable.SableEventBridge;
import io.github.omegau371.trueimpact.sable.SableImpactCapture;
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
 *   PATH A -- damage pipeline (SableImpactCapture):
 *     Runs UNCONDITIONALLY on every physics tick.
 *     MUST NOT be placed inside any DiagnosticConfig gate.
 *     Phase 1B: DamageResolver always returns NONE (no block damage).
 *     Phase 1C+: this path produces real DamageEvents; gating it on a debug flag
 *     would make production damage depend on whether debug logging is enabled,
 *     violating the principle that diagnostic state must not influence game behavior.
 *
 *   PATH B -- diagnostic logging (ContactLogger):
 *     Runs only when LOG_RAW_CONTACTS is enabled.
 *     Safe to gate; logs have no game-side effects.
 *
 * Dependency note (Phase 1C+ readiness):
 *   snaps (lastPostSnaps) is currently populated only when LOG_BODY_SNAPSHOTS is on.
 *   tickStartVels is currently populated only when LOG_RAW_CONTACTS is on.
 *   SableImpactCapture handles empty maps gracefully (produces zero records).
 *   Phase 1C+ will need these populated unconditionally for reliable damage output.
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

        // PATH A: damage pipeline -- MUST remain outside any diagnostic gate.
        SableImpactCapture.process(data, tick, substepCount, snaps, tickStartVels);

        // PATH B: diagnostic logging -- gated on LOG_RAW_CONTACTS.
        if (DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) {
            ContactLogger.onClearCollisions(data, tick, substepCount, snaps, tickStartVels);
        }

        return data;
    }
}
