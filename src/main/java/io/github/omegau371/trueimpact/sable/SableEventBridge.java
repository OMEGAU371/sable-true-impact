package io.github.omegau371.trueimpact.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;

import java.util.HashMap;
import java.util.Map;

/**
 * Static handler for PRE/POST body snapshot logic.
 * Called from DiagnosticPhysicsStepMixin (replaces NeoForge event listeners).
 *
 * This class references Sable API directly — only loaded when Sable is present.
 * No NeoForge Sable events used (those are in sable-neoforge, not sable-common).
 *
 * [C10-inferred] Called from server thread; verified by T-1.
 */
public final class SableEventBridge {

    // Store previous PRE positions for T-7 velocity unit comparison
    private static final Map<Integer, double[]> prevPrePos = new HashMap<>();

    private SableEventBridge() {}

    /**
     * Called from DiagnosticPhysicsStepMixin before Rapier3D.step().
     */
    public static void onPreStep(SubLevelPhysicsSystem system) {
        if (!DiagnosticConfig.ENABLED) return;
        if (!DiagnosticConfig.LOG_BODY_SNAPSHOTS) return;

        long tick = system.getLevel().getGameTime();
        int substep = SableBodyReader.substepIndex(system);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
        if (container == null) return;

        for (ServerSubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;
            BodySnapshot snap = SableBodyReader.snapshot(sl, system, tick, substep, SnapshotPhase.PRE_STEP);
            prevPrePos.put(sl.getRuntimeId(), new double[]{snap.posX(), snap.posY(), snap.posZ()});
            logBodySnapshot(snap);
        }
    }

    /**
     * Called from DiagnosticPhysicsStepMixin after Rapier3D.step() + updateAllPoses().
     */
    public static void onPostStep(SubLevelPhysicsSystem system) {
        if (!DiagnosticConfig.ENABLED) return;

        long tick = system.getLevel().getGameTime();
        int substep = SableBodyReader.substepIndex(system);
        double dt = SableBodyReader.substepDt(system);
        int substepCount = system.getConfig().substepsPerTick;

        ServerSubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
        if (container == null) return;

        for (ServerSubLevel sl : container.getAllSubLevels()) {
            if (sl.isRemoved()) continue;

            if (DiagnosticConfig.LOG_BODY_SNAPSHOTS) {
                BodySnapshot snap = SableBodyReader.snapshot(sl, system, tick, substep, SnapshotPhase.POST_STEP);
                logBodySnapshot(snap);

                if (DiagnosticConfig.LOG_T7_VELOCITY_RATIO) {
                    logT7(sl.getRuntimeId(), snap, dt, tick, substep, substepCount);
                }

                // T-4 result capture
                T4ApplyForceExperiment.Pending p = T4ApplyForceExperiment.pending;
                if (p != null && p.runtimeId() == sl.getRuntimeId()) {
                    logT4Result(p, snap, dt);
                    T4ApplyForceExperiment.clear();
                }
            }
        }
    }

    private static void logBodySnapshot(BodySnapshot s) {
        ExperimentLog.info(
                "[SNAP] tick={} sub={} phase={} id={} mass={} " +
                "pos=({},{},{}) linVel=({},{},{}) angVel=({},{},{})",
                s.serverTick(), s.substepIndex(), s.phase(), s.runtimeId(),
                String.format("%.3f", s.massKpg()),
                String.format("%.3f", s.posX()), String.format("%.3f", s.posY()), String.format("%.3f", s.posZ()),
                String.format("%.4f", s.linVelX()), String.format("%.4f", s.linVelY()), String.format("%.4f", s.linVelZ()),
                String.format("%.4f", s.angVelX()), String.format("%.4f", s.angVelY()), String.format("%.4f", s.angVelZ())
        );
    }

    private static void logT7(int runtimeId, BodySnapshot post, double dt,
                               long tick, int substep, int substepCount) {
        double[] prev = prevPrePos.get(runtimeId);
        if (prev == null) return;
        double dx = post.posX() - prev[0], dy = post.posY() - prev[1], dz = post.posZ() - prev[2];
        double posDelta = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double linMag = Math.sqrt(post.linVelX()*post.linVelX()
                + post.linVelY()*post.linVelY() + post.linVelZ()*post.linVelZ());
        double ratio = (linMag > 1e-9) ? posDelta / (linMag * dt) : Double.NaN;
        ExperimentLog.info("[T-7] tick={} sub={} id={} substeps={} dt={} " +
                "posDelta={} linVelMag={} ratio=posDelta/(linVelMag*dt)={} [unit UNCONFIRMED]",
                tick, substep, runtimeId, substepCount,
                String.format("%.5f", dt), String.format("%.6f", posDelta),
                String.format("%.6f", linMag),
                Double.isNaN(ratio) ? "NaN(vel~0)" : String.format("%.4f", ratio));
    }

    private static void logT4Result(T4ApplyForceExperiment.Pending p, BodySnapshot post, double dt) {
        double dvx = post.linVelX() - p.vbx(), dvy = post.linVelY() - p.vby(), dvz = post.linVelZ() - p.vbz();
        double dvMag = Math.sqrt(dvx*dvx + dvy*dvy + dvz*dvz);
        double inputMag = Math.sqrt(p.fx()*p.fx() + p.fy()*p.fy() + p.fz()*p.fz());
        double mDv = p.massKpg() * dvMag;
        double ratio = (mDv > 1e-12) ? inputMag / mDv : Double.NaN;
        ExperimentLog.LOG.info("[T-4] RESULT id={} M={} input=({},{},{}) inputMag={} dt={} " +
                "vBefore=({},{},{}) vAfter=({},{},{}) Δv=({},{},{}) |Δv|={} M·|Δv|={} " +
                "input/(M·|Δv|)={} [~1=impulse, ~1/dt=force, UNCONFIRMED]",
                p.runtimeId(), String.format("%.4f", p.massKpg()),
                String.format("%.4f", p.fx()), String.format("%.4f", p.fy()), String.format("%.4f", p.fz()),
                String.format("%.6f", inputMag), String.format("%.5f", dt),
                String.format("%.4f", p.vbx()), String.format("%.4f", p.vby()), String.format("%.4f", p.vbz()),
                String.format("%.4f", post.linVelX()), String.format("%.4f", post.linVelY()), String.format("%.4f", post.linVelZ()),
                String.format("%.4f", dvx), String.format("%.4f", dvy), String.format("%.4f", dvz),
                String.format("%.6f", dvMag), String.format("%.6f", mDv),
                Double.isNaN(ratio) ? "NaN(Δv~0)" : String.format("%.4f", ratio));
    }
}
