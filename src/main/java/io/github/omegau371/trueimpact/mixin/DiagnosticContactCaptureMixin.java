package io.github.omegau371.trueimpact.mixin;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import io.github.omegau371.trueimpact.TrueImpactMod;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Intercepts Rapier3D.clearCollisions() to feed the damage pipeline and diagnostic logs.
 *
 * Dual-path injection for Sable 1.x / 2.0.x compatibility.
 * Both paths redirect the clearCollisions() call inside processCollisionEffects();
 * only the method signature differs between versions.
 *
 *   SABLE 1.x PATH (@Redirect, require=0):
 *     clearCollisions(int sceneId) -- static, takes scene integer ID.
 *     Target: Rapier3D.clearCollisions(I)[D
 *
 *   SABLE 2.0.x PATH (@Redirect, require=0):
 *     clearCollisions(long sceneHandle) -- static, takes native scene handle (long).
 *     Target: Rapier3D.clearCollisions(J)[D
 *     NOTE: recentCollisions in 2.0.x is Long2LongOpenHashMap (NOT double[]).
 *     The actual double[] collision data is still obtained from clearCollisions().
 *
 * Both paths call TI$doProcess(data) and return the data to Sable unchanged.
 * A tick-scoped guard prevents double execution (defensive only; at runtime exactly
 * one path fires because each version has only one of the two INVOKE instructions).
 *
 * PATH A (damage pipeline): runs unconditionally, no diagnostic gate.
 * PATH B (ContactLogger):   gated on LOG_RAW_CONTACTS.
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline",
       remap = false)
public abstract class DiagnosticContactCaptureMixin {

    @Shadow @Final private ServerLevel level;

    // Note: tick-scoped dedup was removed. processCollisionEffects fires once per substep;
    // with multiple substeps per tick, contacts in substep K>0 would have been silently dropped
    // when substep-0 already set TI$lastProcessedTick (e.g. Sable's own testgravity bodies).
    // DeferredSublevelDamageQueue has its own per-(tick,sublevel,block) dedup, so processing
    // the same sublevel's contact in multiple substeps is safe — first enqueue wins.

    // ── Sable 1.x: Rapier3D.clearCollisions(int sceneId) static method ────────────
    @Unique private static volatile Method  TI$clearCollisions1xMethod;
    @Unique private static volatile boolean TI$clearCollisions1xSearchDone;

    @Unique
    private double[] TI$callClearCollisions1x(int sceneId) {
        if (!TI$clearCollisions1xSearchDone) {
            Method found = null;
            try {
                Class<?> cls = Class.forName(
                        "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
                found = cls.getDeclaredMethod("clearCollisions", int.class);
                found.setAccessible(true);
            } catch (Throwable ignored) {}
            TI$clearCollisions1xMethod  = found;
            TI$clearCollisions1xSearchDone = true;
        }
        Method m = TI$clearCollisions1xMethod;
        if (m == null) return null;
        try { return (double[]) m.invoke(null, sceneId); } catch (Throwable ignored) { return null; }
    }

    // ── Sable 2.0.x: Rapier3D.clearCollisions(long sceneHandle) static method ─────
    @Unique private static volatile Method  TI$clearCollisions2xMethod;
    @Unique private static volatile boolean TI$clearCollisions2xSearchDone;

    @Unique
    private double[] TI$callClearCollisions2x(long sceneHandle) {
        if (!TI$clearCollisions2xSearchDone) {
            Method found = null;
            try {
                Class<?> cls = Class.forName(
                        "dev.ryanhcode.sable.physics.impl.rapier.Rapier3D");
                found = cls.getDeclaredMethod("clearCollisions", long.class);
                found.setAccessible(true);
            } catch (Throwable ignored) {}
            TI$clearCollisions2xMethod  = found;
            TI$clearCollisions2xSearchDone = true;
        }
        Method m = TI$clearCollisions2xMethod;
        if (m == null) return null;
        try { return (double[]) m.invoke(null, sceneHandle); } catch (Throwable ignored) { return null; }
    }

    // ── SABLE 1.x INJECTION ────────────────────────────────────────────────────────
    // Intercepts clearCollisions(int sceneId) inside processCollisionEffects.
    // require=0: silently skips on Sable 2.0.x (signature changed to (J)[D there).
    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(I)[D",
                     remap = false),
            require = 0,
            remap = false
    )
    private double[] captureVia1x(int sceneId) {
        double[] data = TI$callClearCollisions1x(sceneId);
        TI$doProcess(data);
        return (data != null) ? data : new double[0];
    }

    // ── SABLE 2.0.x INJECTION ──────────────────────────────────────────────────────
    // Intercepts clearCollisions(long sceneHandle) inside processCollisionEffects.
    // require=0: silently skips on Sable 1.x (signature there is (I)[D).
    // NOTE: recentCollisions in 2.0.x is Long2LongOpenHashMap, NOT the double[] data.
    //       The actual collision data still comes from clearCollisions() as double[N*15].
    @Redirect(
            method = "processCollisionEffects",
            at = @At(value = "INVOKE",
                     target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;clearCollisions(J)[D",
                     remap = false),
            require = 0,
            remap = false
    )
    private double[] captureVia2x(long sceneHandle) {
        double[] data = TI$callClearCollisions2x(sceneHandle);
        TI$doProcess(data);
        return (data != null) ? data : new double[0];
    }

    // ── shared processing (called by both redirect paths) ──────────────────────────
    // This runs INSIDE a @Redirect on Rapier3D.clearCollisions(), i.e. synchronously
    // in the middle of native physics-step processing. An uncaught exception here does
    // NOT behave like a normal Java crash: it unwinds back through the JNI boundary into
    // Rust, where a panic in a context that cannot unwind aborts the whole process
    // (observed upstream: "panicked... on an Err value: JavaException... thread caused
    // non-unwinding panic. aborting." -- a dedicated-server crash on any impact, with no
    // usable Java-side crash report). The damage pipeline below does a lot of unguarded
    // map/array access across many contact records; a single malformed contact must not
    // take down the whole server. Catch broadly and keep going -- Sable's native step
    // must always return cleanly to Rust regardless of what happens on the Java side.
    @Unique
    private void TI$doProcess(double[] data) {
        try {
            TI$doProcessUnsafe(data);
        } catch (Throwable t) {
            TrueImpactMod.LOGGER.warn("[TI] TI$doProcess failed (native callback context -- swallowed to avoid a JNI unwind abort): {} {}",
                    t.getClass().getSimpleName(), t.getMessage());
        }
    }

    @Unique
    private void TI$doProcessUnsafe(double[] data) {
        if (data == null || data.length == 0) return;
        long tick = level.getGameTime();

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
        int substepCount = (system != null) ? system.getConfig().substepsPerTick : -1;
        Map<Integer, BodySnapshot> snaps        = SableEventBridge.getLastPostSnapshots();
        Map<Integer, double[]>     tickStartVels = SableEventBridge.getTickStartVels();
        // Use SubLevelPhysicsSystem.getLevel() for the dimension key.
        // RapierPhysicsPipeline.level can be a virtual physics level whose dimension()
        // is not registered in the Minecraft level registry (returns "minecraft:missing").
        String levelKey = (system != null)
                ? system.getLevel().dimension().location().toString()
                : level.dimension().location().toString();

        // Contact-point block detection (PATH A, unconditional).
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

            BodySnapshot activeSnap = (snapA != null) ? snapA : snapB;
            double lpX, lpY, lpZ;
            if (snapA != null) {
                lpX = data[base + 9];
                lpY = data[base + 10];
                lpZ = data[base + 11];
            } else {
                lpX = data[base + 12];
                lpY = data[base + 13];
                lpZ = data[base + 14];
            }

            double[] wcp = transformLocalToWorld(activeSnap, lpX, lpY, lpZ);
            BlockPos candidate = sampleNearestSolidBlock(wcp);
            if (candidate != null) {
                ResourceLocation loc = BuiltInRegistries.BLOCK
                        .getKey(level.getBlockState(candidate).getBlock());
                if (loc != null) {
                    SableVictimCapture.captureContactPointBlock(loc.toString(),
                            candidate.getX(), candidate.getY(), candidate.getZ());
                    return;
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
