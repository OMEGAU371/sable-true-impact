package io.github.omegau371.trueimpact.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.omegau371.trueimpact.TrueImpactMod;
import io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.lang.reflect.Field;

/**
 * Phase 4A integration test: Create contraption anchor destruction on high-speed sublevel impact.
 *
 * Run with: .\gradlew.bat runGameTestServer   (requires Create in run/mods/)
 *
 * Template: sable:physicstest.gravity — flat stone floor + open space.
 *
 * Test flow:
 *   1. Place create:mechanical_bearing (FACING=UP) at relative (2, 0, 0).
 *   2. Place stone block at (2, 1, 0) — the structure to assemble into the contraption.
 *   3. Place create:creative_motor (FACING=UP) at (2, -1, 0) — directly below bearing.
 *      The motor provides live kinetic power so the bearing's speed stays non-zero
 *      when Create's tick() processes assembleNextTick (avoids speed-reset race condition).
 *   4. Wait 5 ticks for the kinetic network to propagate motor → bearing.
 *   5. Call assemble() on the bearing; SPAWN a stone sublevel at (0.5, 6.5, 0.5).
 *      Sublevel is spawned here (not at test start) so it is still airborne at the kick.
 *      Kicking a sleeping floor body does NOT reflect in getLinearVelocity() at the next
 *      PRE_STEP; kicking an airborne body does.
 *   6. Re-assert assembleNextTick=true at tick 6 in case Create reset it during the same tick.
 *   7. Tick 9: kick the sublevel (velocity ADDED via addLinearAndAngularVelocity, not set)
 *      while still airborne. The AABB search in Phase 4A finds the contraption entity
 *      within ±2.5 blocks of the landing position.
 *   8. Verified empirically (2026-07-09, dev server): -30 m/s reliably destroys the anchor
 *      on the first contact. The ORIGINAL -120 m/s kick tunneled straight through the
 *      bearing and the floor without ever generating a real contact — the sublevel just
 *      free-fell into the void forever (observed: AABB search position falling thousands
 *      of blocks below the bearing, 0 entities found every tick, test always timing out).
 *      -30 m/s stays far below Rapier's collision-detection velocity ceiling and still
 *      clears the anchor's structural threshold with margin.
 *   9. Verify bearing position is now air (tick 79).
 *
 * Note: Create must be in run/mods/ for this test to have any effect.
 * Without Create, the bearing lookup returns Blocks.AIR and the test is skipped.
 */
@GameTestHolder("true_impact")
@PrefixGameTestTemplate(false)
public final class Phase4AGameTest {

    private static final String TEMPLATE_NS = "sable";
    private static final String TEMPLATE    = "physicstest.gravity";
    private static final int    TIMEOUT     = 200;

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void contraption_anchorDestroyed_onHighImpact(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        net.minecraft.server.level.ServerLevel level = helper.getLevel();

        // ── Block placement ────────────────────────────────────────────────
        // Y=0: floor level (stone floor from the gravity template).
        // The bearing at (2,0,0) replaces one floor stone; structure stone goes at (2,1,0).
        // The creative motor at (2,-1,0) is directly below the bearing (below the floor).
        BlockPos bearingRelPos   = new BlockPos(2, 0, 0);
        BlockPos structureRelPos = new BlockPos(2, 1, 0);
        BlockPos motorRelPos     = new BlockPos(2, -1, 0);

        // ── Look up Create blocks; skip if Create not loaded ───────────────
        net.minecraft.world.level.block.Block bearingBlock =
            BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "mechanical_bearing"));
        if (bearingBlock == Blocks.AIR) {
            TrueImpactMod.LOGGER.warn("[TI4A-TEST] create:mechanical_bearing not found — Create not loaded, skipping");
            helper.succeed();
            return;
        }
        net.minecraft.world.level.block.Block motorBlock =
            BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));

        // ── Place bearing (FACING=UP) ──────────────────────────────────────
        BlockState bearingState = bearingBlock.defaultBlockState();
        for (Property<?> prop : bearingState.getProperties()) {
            if ("facing".equals(prop.getName()) && prop instanceof DirectionProperty dp) {
                bearingState = bearingState.setValue(dp, Direction.UP);
                break;
            }
        }
        helper.setBlock(bearingRelPos, bearingState);
        helper.setBlock(structureRelPos, Blocks.STONE.defaultBlockState());

        // ── Place creative motor below bearing (FACING=UP → shaft points up) ─
        // Motor's shaft connects to bearing's kinetic input face (bottom of bearing).
        // This keeps the bearing's speed non-zero through Create's kinetic network,
        // so assemble() doesn't abort when Create's tick checks speed.
        if (motorBlock != Blocks.AIR) {
            BlockState motorState = motorBlock.defaultBlockState();
            for (Property<?> prop : motorState.getProperties()) {
                if ("facing".equals(prop.getName()) && prop instanceof DirectionProperty dp) {
                    motorState = motorState.setValue(dp, Direction.UP);
                    break;
                }
            }
            helper.setBlock(motorRelPos, motorState);
            TrueImpactMod.LOGGER.info("[TI4A-TEST] creative_motor placed at {}", helper.absolutePos(motorRelPos));
        } else {
            TrueImpactMod.LOGGER.warn("[TI4A-TEST] create:creative_motor not found — will rely on manual speed set");
        }

        BlockPos bearingAbsPos = helper.absolutePos(bearingRelPos);

        // Sublevel is spawned inside the tick-5 callback (after assemble) so it has only
        // ~4 ticks of free-fall before the tick-9 kick — still airborne, not sleeping.
        java.util.concurrent.atomic.AtomicReference<ServerSubLevel> slRef =
            new java.util.concurrent.atomic.AtomicReference<>();

        helper.startSequence()
            // ── Tick 5: call assemble() after kinetic network has propagated ──
            .thenExecuteAfter(5, () -> {
                net.minecraft.world.level.block.entity.BlockEntity be =
                    level.getBlockEntity(bearingAbsPos);
                if (be == null) {
                    TrueImpactMod.LOGGER.error("[TI4A-TEST] No block entity at {} — aborting", bearingAbsPos);
                    helper.fail("[TI4A-TEST] Bearing block entity missing at tick 5");
                    return;
                }
                try {
                    // Log the speed the kinetic network delivered (should be > 0 if motor connected).
                    Field speedField = findField(be.getClass(), "speed");
                    float currentSpeed = 0f;
                    if (speedField != null) {
                        speedField.setAccessible(true);
                        currentSpeed = (float) speedField.get(be);
                        TrueImpactMod.LOGGER.info("[TI4A-TEST] bearing speed at tick 5: {}", currentSpeed);
                        if (currentSpeed == 0f) {
                            // Motor either not connected or defaulting to 0 — force manually.
                            speedField.set(be, 32.0f);
                            TrueImpactMod.LOGGER.warn("[TI4A-TEST] speed was 0, forced to 32");
                        }
                    }

                    // Call assemble() — public method, no setAccessible needed.
                    java.lang.reflect.Method assembleMethod = be.getClass().getMethod("assemble");
                    assembleMethod.invoke(be);
                    TrueImpactMod.LOGGER.info("[TI4A-TEST] assemble() invoked on {}, speed={}",
                        be.getClass().getSimpleName(), currentSpeed);

                    // Inspect internal state immediately after calling assemble().
                    Field atnField = findField(be.getClass(), "assembleNextTick");
                    if (atnField != null) {
                        atnField.setAccessible(true);
                        TrueImpactMod.LOGGER.info("[TI4A-TEST] assembleNextTick after call: {}", atnField.get(be));
                    }
                    Field mcField = findField(be.getClass(), "movedContraption");
                    if (mcField != null) {
                        mcField.setAccessible(true);
                        TrueImpactMod.LOGGER.info("[TI4A-TEST] movedContraption right after assemble(): {}", mcField.get(be));
                    }
                } catch (Throwable t) {
                    TrueImpactMod.LOGGER.warn("[TI4A-TEST] tick-5 assemble threw: {} {}",
                        t.getClass().getSimpleName(), t.getMessage());
                }
                // Spawn sublevel now so it's only ~4 ticks old (still airborne) when kicked at tick 9.
                ServerSubLevelContainer container = (ServerSubLevelContainer)
                    SubLevelContainer.getContainer(level);
                Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5));
                ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                    container, spawnPos, Blocks.STONE.defaultBlockState());
                slRef.set(sl);
                TrueImpactMod.LOGGER.info("[TI4A-TEST] sublevel spawned at tick 5 sl={}", sl);
            })
            // ── Tick 6: re-assert assembleNextTick=true and speed in case Create reset them ──
            // Create's tick() may have run in the same tick as our tick-5 callback and found
            // assembleNextTick=true but speed=0, aborting and setting assembleNextTick=false.
            // Re-asserting both here gives the bearing one more chance to assemble at tick 6-7.
            .thenExecuteAfter(1, () -> {
                net.minecraft.world.level.block.entity.BlockEntity be =
                    level.getBlockEntity(bearingAbsPos);
                if (be == null) return;
                try {
                    Field mcField  = findField(be.getClass(), "movedContraption");
                    Field atnField = findField(be.getClass(), "assembleNextTick");
                    Field speedF   = findField(be.getClass(), "speed");
                    if (mcField != null) {
                        mcField.setAccessible(true);
                        if (mcField.get(be) != null) {
                            TrueImpactMod.LOGGER.info("[TI4A-TEST] movedContraption set at tick 6 — assembly succeeded!");
                            return; // already assembled, nothing to do
                        }
                    }
                    // Assembly not yet complete — re-assert
                    if (speedF != null) { speedF.setAccessible(true); speedF.set(be, 32.0f); }
                    if (atnField != null) { atnField.setAccessible(true); atnField.set(be, true); }
                    TrueImpactMod.LOGGER.info("[TI4A-TEST] tick 6 re-assert: speed=32, assembleNextTick=true");
                } catch (Throwable t) {
                    TrueImpactMod.LOGGER.warn("[TI4A-TEST] tick-6 re-assert threw: {}", t.getMessage());
                }
            })
            // ── Tick 9: log entity status, fire sublevel ──────────────────
            // Sublevel was spawned at tick 5; after 4 ticks of fall it is ~4.2 blocks
            // above the floor (g≈116 m/s², fallen≈2.3 blocks). Kicking an airborne body
            // guarantees the kick velocity shows up in getLinearVelocity() at the next
            // PRE_STEP. Kicking a sleeping floor body does NOT — root cause of prior failure.
            .thenExecuteAfter(3, () -> {
                AABB entityCheckBox = new AABB(bearingAbsPos).inflate(5.0);
                java.util.List<net.minecraft.world.entity.Entity> allNearby =
                    level.getEntities((net.minecraft.world.entity.Entity) null, entityCheckBox, e -> true);
                TrueImpactMod.LOGGER.info("[TI4A-TEST] entities within 5 blocks of bearing ({}): {}",
                    bearingAbsPos, allNearby.size());
                allNearby.forEach(e -> TrueImpactMod.LOGGER.info("[TI4A-TEST]   {} @ {}",
                    e.getClass().getSimpleName(), e.position()));

                java.util.List<net.minecraft.world.entity.Entity> contraptions =
                    level.getEntities((net.minecraft.world.entity.Entity) null, entityCheckBox,
                        e -> e instanceof dev.ryanhcode.sable.api.sublevel.KinematicContraption kc
                             && kc.sable$isValid());
                TrueImpactMod.LOGGER.info("[TI4A-TEST] valid KinematicContraption entities nearby: {}",
                    contraptions.size());

                ServerSubLevel sl = slRef.get();
                RigidBodyHandle handle = sl != null ? RigidBodyHandle.of(sl) : null;
                if (handle != null && handle.isValid()) {
                    handle.addLinearAndAngularVelocity(
                        new Vector3d(0.0, -30.0, 0.0),
                        new Vector3d(0.0,   0.0,  0.0));
                    TrueImpactMod.LOGGER.info("[TI4A-TEST] sublevel velocity -30 m/s applied (airborne kick at tick 9)");
                } else {
                    TrueImpactMod.LOGGER.warn("[TI4A-TEST] sublevel handle null/invalid at tick 9");
                }
            })
            // ── Tick 79: verify the bearing (anchor block) was destroyed ──
            .thenExecuteAfter(70, () -> {
                BlockState blockAtAnchor = level.getBlockState(bearingAbsPos);
                boolean anchorDestroyed = blockAtAnchor.isAir();
                TrueImpactMod.LOGGER.info("[TI4A-TEST] anchor={} state={} destroyed={}",
                    bearingAbsPos, blockAtAnchor, anchorDestroyed);
                if (!anchorDestroyed) {
                    helper.fail("Phase 4A: expected bearing anchor to be destroyed on -30 m/s impact, "
                        + "but block at " + bearingAbsPos + " is still " + blockAtAnchor
                        + ". Check TI4A logs: entities nearby / assembleNextTick / movedContraption "
                        + "/ AABB search position (a runaway fall past the anchor position "
                        + "usually means the kick tunneled through without a real contact).");
                }
            })
            .thenSucceed();
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try { return clazz.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
