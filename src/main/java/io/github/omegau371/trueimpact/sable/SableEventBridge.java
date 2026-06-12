package io.github.omegau371.trueimpact.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.damage.DeferredDamageQueue;
import io.github.omegau371.trueimpact.diagnostic.ExperimentLog;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import io.github.omegau371.trueimpact.observation.BodySnapshot;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import io.github.omegau371.trueimpact.observation.SnapshotPhase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static handler for PRE/POST body snapshot logic and T-4 post-step measurement.
 * Called from DiagnosticPhysicsStepMixin (mixin-based hooks, no NeoForge Sable events needed).
 *
 * [C10-inferred] Called on server thread. T-1 experiment verifies.
 *
 * T-4 is INDEPENDENT of LOG_BODY_SNAPSHOTS -- onPostStep() always checks T-4 pending map.
 */
public final class SableEventBridge {

    /** Previous PRE_STEP positions for T-7 comparison. Cleared by clearState(). */
    private static final Map<Integer, double[]> prevPrePos = new HashMap<>();

    /**
     * Most recent POST_STEP snapshots, keyed by runtimeId.
     * Rebuilt from scratch at the start of every onPostStep() call -- the map is
     * cleared before iterating sub-levels, so stale entries from removed or absent
     * bodies never persist to the next clearCollisions() call.
     * SableImpactCapture reads this to identify which bodies are currently active.
     * ContactLogger (T-3/T-6) also reads from this map.
     * Additionally cleared by clearState() on session reset.
     */
    private static final Map<Integer, BodySnapshot> lastPostSnaps = new HashMap<>();

    /**
     * Tick-start linear velocities (substep 0 PRE_STEP), keyed by runtimeId.
     * Populated when LOG_RAW_CONTACTS is enabled. Used by T-3-MISS pairwise dv scan.
     * Cleared at the start of each substep-0 PRE_STEP pass.
     */
    private static final Map<Integer, double[]> tickStartVelById = new HashMap<>();

    /**
     * Returns an unmodifiable view of the last POST_STEP snapshots for contact correlation.
     * Always populated each substep regardless of diagnostic flags.
     * Empty before the first POST_STEP fires or after clearState().
     */
    public static Map<Integer, BodySnapshot> getLastPostSnapshots() {
        return java.util.Collections.unmodifiableMap(lastPostSnaps);
    }

    /**
     * Returns an unmodifiable view of tick-start velocities (substep 0 PRE_STEP).
     * Used by ContactLogger for T-3-MISS pairwise dv scan.
     * Empty if LOG_RAW_CONTACTS was off, or if no substep-0 PRE_STEP has fired yet.
     */
    public static Map<Integer, double[]> getTickStartVels() {
        return java.util.Collections.unmodifiableMap(tickStartVelById);
    }

    /** Captured server thread identity (set on first server tick, used by T-1). */
    public static volatile Thread capturedServerThread = null;

    /** Last game-tick at which a T-4 "no-match" diagnostic was emitted (once per tick). */
    private static volatile long lastT4DiagLogTick = -1L;

    private SableEventBridge() {}

    static {
        // Register cleanup hooks so DiagnosticStateManager.clearAll() flushes all state
        DiagnosticStateManager.registerFlushHook(SableEventBridge::clearState);
        DiagnosticStateManager.registerFlushHook(T4ApplyForceExperiment::clearAll);
        DiagnosticStateManager.registerFlushHook(DeferredDamageQueue::clear); // Phase 1E
    }

    public static void clearState() {
        prevPrePos.clear();
        lastPostSnaps.clear();
        tickStartVelById.clear();
        SableImpactCapture.resetCounters();
        capturedServerThread = null;
        lastT4DiagLogTick = -1L;
    }

    /** Called from TrueImpactMod.onServerTick to capture server thread identity for T-1. */
    public static void captureServerThread() {
        if (capturedServerThread == null) {
            capturedServerThread = Thread.currentThread();
        }
    }

    // -- PRE_STEP ----------------------------------------------------------------

    /**
     * Called from DiagnosticPhysicsStepMixin before Rapier3D.step().
     *
     * Unconditional path (substep 0 only, when ENABLED):
     *   SableVictimCapture.clearForTick() -- clears previous tick's callback capture
     *   before this tick's BlockSubLevelCollisionCallbacks fire during physicsTick().
     *
     * Two gated paths:
     *   needSnapshots  = ENABLED + LOG_BODY_SNAPSHOTS -> body snapshot + T-7 tracking
     *   needTickStart  = LOG_RAW_CONTACTS, substep 0 only -> tick-start vels for T-3-MISS
     */
    public static void onPreStep(SubLevelPhysicsSystem system) {
        int substep = SableBodyReader.substepIndex(system);

        // Phase 1D: clear victim capture buffer at tick start so stale block data from
        // the previous tick is not carried forward. Runs before callbacks fire.
        if (substep == 0 && DiagnosticConfig.ENABLED) {
            SableVictimCapture.clearForTick();
        }

        boolean needSnapshots = DiagnosticConfig.ENABLED && DiagnosticConfig.LOG_BODY_SNAPSHOTS;
        boolean needTickStart = DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS);
        if (!needSnapshots && !needTickStart) return;

        long tick = system.getLevel().getGameTime();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
        if (container == null) return;

        List<ServerSubLevel> subLevels = container.getAllSubLevels();

        // Capture tick-start velocity at substep 0 for T-3-MISS pairwise scan.
        // Cleared each substep-0 pass to avoid stale data from the previous tick.
        if (needTickStart && substep == 0) {
            tickStartVelById.clear();
            for (ServerSubLevel sl : subLevels) {
                if (sl.isRemoved()) continue;
                try {
                    RigidBodyHandle handle = system.getPhysicsHandle(sl);
                    if (handle != null && handle.isValid()) {
                        var lv = handle.getLinearVelocity(new org.joml.Vector3d());
                        tickStartVelById.put(sl.getRuntimeId(), new double[]{lv.x, lv.y, lv.z});
                    }
                } catch (Exception ignored) {}
            }
        }

        // Body snapshots for SNAP log and T-7.
        if (needSnapshots) {
            for (ServerSubLevel sl : subLevels) {
                if (sl.isRemoved()) continue;
                BodySnapshot snap = SableBodyReader.snapshot(sl, system, tick, substep, SnapshotPhase.PRE_STEP);
                prevPrePos.put(sl.getRuntimeId(), new double[]{snap.posX(), snap.posY(), snap.posZ()});
                logSnap(snap);
            }
        }
    }

    // -- POST_STEP ---------------------------------------------------------------

    /**
     * Called from DiagnosticPhysicsStepMixin after Rapier3D.step() + updateAllPoses().
     *
     * Two unconditional paths:
     *   1. Post-step snapshot collection into lastPostSnaps -- always runs.
     *      Required by damage pipeline (SableImpactCapture). No diagnostic flag gate.
     *   2. T-4 pending measurement -- always runs when a pending entry exists.
     *
     * One gated path:
     *   3. Diagnostic log output (SNAP, T-7) -- only when ENABLED + LOG_BODY_SNAPSHOTS.
     */
    public static void onPostStep(SubLevelPhysicsSystem system) {
        boolean hasPending = !T4ApplyForceExperiment.pendingByKey.isEmpty();
        // Always run: lastPostSnaps collection is required by the damage pipeline
        // (SableImpactCapture must identify active bodies unconditionally).
        // T-4 measurement also runs unconditionally when pending.
        // Only log output (SNAP, T-7) is gated on DiagnosticConfig flags below.

        // Rebuild lastPostSnaps from scratch every substep.
        // Clearing before iteration ensures removed or absent bodies never persist
        // as stale entries to the next clearCollisions() call in captureContactData().
        lastPostSnaps.clear();

        long tick = system.getLevel().getGameTime();
        int substep = SableBodyReader.substepIndex(system);
        double dt = SableBodyReader.substepDt(system);
        int substepCount = system.getConfig().substepsPerTick;
        String levelKey = system.getLevel().dimension().location().toString();

        ServerSubLevelContainer container = SubLevelContainer.getContainer(system.getLevel());
        if (container == null) {
            // lastPostSnaps already cleared above -- no active bodies if no container.
            if (hasPending) {
                ExperimentLog.warn("[T-4] DIAG tick={} levelKey='{}' container=null -- cannot iterate sublevels. pendingKeys={}",
                        tick, levelKey, T4ApplyForceExperiment.pendingByKey.keySet());
            }
            return;
        }

        // Phase 1C: skip expensive sub-level iteration when all diagnostics are off
        // and no T-4 measurement is pending. lastPostSnaps stays empty (already cleared);
        // SableImpactCapture.captureGate is also false (mixin sets it from ENABLED),
        // so process() skips contact array parsing too -- no wasted CPU after all-off.
        // REMOVE this gate in Phase 2 when DamageResolver produces real effects.
        if (!DiagnosticConfig.ENABLED && !hasPending) return;

        List<ServerSubLevel> subLevels = container.getAllSubLevels();
        boolean t4MatchedThisPass = false;
        StringBuilder visitedIds = hasPending ? new StringBuilder() : null;

        for (ServerSubLevel sl : subLevels) {
            if (sl.isRemoved()) continue;
            int rid = sl.getRuntimeId();
            if (visitedIds != null) visitedIds.append(rid).append(',');

            // T-4: always run regardless of ENABLED or LOG_BODY_SNAPSHOTS
            String t4Key = T4ApplyForceExperiment.key(levelKey, rid);
            T4ApplyForceExperiment.Pending t4 = T4ApplyForceExperiment.pendingByKey.get(t4Key);
            if (t4 != null) {
                t4MatchedThisPass = true;
                handleT4PostStep(sl, system, t4, t4Key, dt, tick);
            }

            // Always collect post-step snapshot -- required by damage pipeline.
            // SableImpactCapture uses lastPostSnaps to identify active bodies
            // regardless of whether diagnostic logging is enabled.
            BodySnapshot snap = SableBodyReader.snapshot(sl, system, tick, substep, SnapshotPhase.POST_STEP);
            lastPostSnaps.put(rid, snap);

            // Diagnostic log output only -- gated on ENABLED + LOG_BODY_SNAPSHOTS.
            if (DiagnosticConfig.ENABLED && DiagnosticConfig.LOG_BODY_SNAPSHOTS) {
                logSnap(snap);
                if (DiagnosticConfig.LOG_T7_VELOCITY_RATIO) {
                    logT7(rid, snap, dt, tick, substep, substepCount);
                }
            }
        }

        // -- T-4 diagnostics: log when pending exists but no match was found ------
        if (hasPending && !t4MatchedThisPass && tick != lastT4DiagLogTick) {
            lastT4DiagLogTick = tick;
            String ids = (visitedIds != null && visitedIds.length() > 0)
                    ? visitedIds.substring(0, visitedIds.length() - 1) : "none";
            ExperimentLog.warn("[T-4] DIAG tick={} levelKey='{}' pending NOT consumed this pass. " +
                    "pendingKeys={} visibleRuntimeIds=[{}] " +
                    "(key mismatch? runtimeId changed after apply? body removed?)",
                    tick, levelKey, T4ApplyForceExperiment.pendingByKey.keySet(), ids);
        }

        // -- T-4 timeout: clear stuck pending after PENDING_TIMEOUT_TICKS ---------
        if (!T4ApplyForceExperiment.pendingByKey.isEmpty()) {
            T4ApplyForceExperiment.pendingByKey.entrySet().removeIf(entry -> {
                long age = tick - entry.getValue().tickApplied();
                if (age > T4ApplyForceExperiment.PENDING_TIMEOUT_TICKS) {
                    ExperimentLog.warn("[T-4] TIMEOUT key={} applied=tick{} age={}ticks (>{}). " +
                            "Auto-cleared. Re-run after checking /trueimpact debug status.",
                            entry.getKey(), entry.getValue().tickApplied(), age,
                            T4ApplyForceExperiment.PENDING_TIMEOUT_TICKS);
                    return true;
                }
                return false;
            });
        }
    }

    // -- T-4 post-step measurement -----------------------------------------------

    private static void handleT4PostStep(
            ServerSubLevel sl, SubLevelPhysicsSystem system,
            T4ApplyForceExperiment.Pending p, String t4Key,
            double dt, long tick
    ) {
        // Independently read vAfter and angular velocity
        double vax = 0, vay = 0, vaz = 0;
        double avx = 0, avy = 0, avz = 0;
        boolean velValid = false;
        try {
            RigidBodyHandle handle = system.getPhysicsHandle(sl);
            if (handle != null && handle.isValid()) {
                var lv = handle.getLinearVelocity(new org.joml.Vector3d());
                vax = lv.x; vay = lv.y; vaz = lv.z;
                velValid = true;
                try {
                    var av = handle.getAngularVelocity(new org.joml.Vector3d());
                    avx = av.x; avy = av.y; avz = av.z;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Clear BEFORE logging (one-shot)
        T4ApplyForceExperiment.pendingByKey.remove(t4Key);

        if (!velValid) {
            ExperimentLog.warn("[T-4] id={} variant={} tick={} ABORTED: vAfter read failed (velocityReadValid=false)",
                    p.runtimeId(), p.variant(), tick);
            return;
        }

        double dvx = vax - p.vbx(), dvy = vay - p.vby(), dvz = vaz - p.vbz();
        double dvMag = Math.sqrt(dvx*dvx + dvy*dvy + dvz*dvz);

        // Normalize input for projection
        double inMag = Math.sqrt(p.fx()*p.fx() + p.fy()*p.fy() + p.fz()*p.fz());
        double nx = p.fx()/inMag, ny = p.fy()/inMag, nz = p.fz()/inMag;

        // [C1-audit] Vector projection: deltaVAlongInput = dv * normalize(input)
        double deltaVAlongInput = dvx*nx + dvy*ny + dvz*nz;
        double measuredMomentumAlongInput = p.massKpg() * deltaVAlongInput;

        // ratio = inMag / (M * deltaVAlongInput); ~1.0 if impulse, ~1/dt if force
        double ratio = (Math.abs(measuredMomentumAlongInput) > 1e-12)
                ? inMag / measuredMomentumAlongInput : Double.NaN;

        // Gravity error estimate (kpg*block/s per substep, g ~= 0.08 block/s^2 default)
        double gravityErrorEst = p.massKpg() * 0.08 * dt;
        double avMag = Math.sqrt(avx*avx + avy*avy + avz*avz);

        ExperimentLog.info("[T-4] RESULT id={} variant={} M={}kpg input=({},{},{}) |input|={} dt={}s" +
                " vBefore=({},{},{}) vAfter=({},{},{})" +
                " dv=({},{},{}) |dv|={}" +
                " deltaVAlongInput={} measuredMomentumAlongInput={}" +
                " input/(M*deltaVAlongInput)={}" +
                " [~1=impulse, ~1/dt=force -- NOT AUTO-CONCLUDED]" +
                " angVelAfter=({},{},{}) |omega|={}" +
                " gravityErrorEst={}kpg*block/s [contact/isolation NOT confirmed]",
                p.runtimeId(),
                p.variant(),
                fmt(p.massKpg()),
                fmt(p.fx()), fmt(p.fy()), fmt(p.fz()), fmt(inMag),
                fmt5(dt),
                fmt(p.vbx()), fmt(p.vby()), fmt(p.vbz()),
                fmt(vax), fmt(vay), fmt(vaz),
                fmt(dvx), fmt(dvy), fmt(dvz), fmt(dvMag),
                fmt(deltaVAlongInput), fmt(measuredMomentumAlongInput),
                Double.isNaN(ratio) ? "NaN(~0)" : fmt(ratio),
                fmt(avx), fmt(avy), fmt(avz), fmt(avMag),
                fmt(gravityErrorEst));
    }

    // -- T-7 ---------------------------------------------------------------------

    private static void logT7(int id, BodySnapshot post, double dt,
                               long tick, int substep, int substepCount) {
        double[] prev = prevPrePos.get(id);
        if (prev == null) return;
        if (!post.velocityReadValid()) {
            ExperimentLog.info("[T-7] tick={} sub={} id={} SKIPPED: velocityReadValid=false",
                    tick, substep, id);
            return;
        }
        double dx = post.posX()-prev[0], dy = post.posY()-prev[1], dz = post.posZ()-prev[2];
        double posDelta = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double linMag = Math.sqrt(post.linVelX()*post.linVelX()
                + post.linVelY()*post.linVelY() + post.linVelZ()*post.linVelZ());
        double ratio = (linMag > 1e-9) ? posDelta / (linMag * dt) : Double.NaN;
        ExperimentLog.info("[T-7] tick={} sub={} id={} substeps={} dt={}" +
                " posDelta={} linVelMag={} ratio=posDelta/(linVelMag*dt)={} [unit UNCONFIRMED]",
                tick, substep, id, substepCount,
                fmt5(dt), fmt6(posDelta), fmt6(linMag),
                Double.isNaN(ratio) ? "NaN(vel~0)" : fmt4(ratio));
    }

    // -- snapshot log ------------------------------------------------------------

    private static void logSnap(BodySnapshot s) {
        String comStr = s.comValid() ? fmt(s.comX())+","+fmt(s.comY())+","+fmt(s.comZ()) : "invalid";
        String velStr = s.velocityReadValid()
                ? fmt(s.linVelX())+","+fmt(s.linVelY())+","+fmt(s.linVelZ()) : "invalid";
        // ori=(x,y,z,w) is required by T-3: rotate localNormalA from BODY_COM_LOCAL to WORLD
        ExperimentLog.info("[SNAP] tick={} sub={} phase={} id={} mass={}kpg pos=({},{},{}) com=({}) linVel=({}) ori=({},{},{},{})",
                s.serverTick(), s.substepIndex(), s.phase(), s.runtimeId(), fmt(s.massKpg()),
                fmt(s.posX()), fmt(s.posY()), fmt(s.posZ()), comStr, velStr,
                fmt(s.oriX()), fmt(s.oriY()), fmt(s.oriZ()), fmt(s.oriW()));
    }

    // -- format helpers ----------------------------------------------------------

    private static String fmt(double v)  { return String.format("%.4f", v); }
    private static String fmt4(double v) { return String.format("%.4f", v); }
    private static String fmt5(double v) { return String.format("%.5f", v); }
    private static String fmt6(double v) { return String.format("%.6f", v); }
}
