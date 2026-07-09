package io.github.omegau371.trueimpact.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.omegau371.trueimpact.TrueImpactMod;
import io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * Path 1 integration tests: world block damage when a Sable sublevel impacts terrain.
 *
 * Pipeline: SableImpactCapture.enqueueWorldBlockContact (via process() or
 * tryEnqueueVelocityDelta) → DeferredWorldContactQueue → applyDeferredWorldContacts
 * → DeferredDamageQueue → onServerTickPost → BlockDamageAccumulator → destroyBlock.
 *
 * Template: sable:physicstest.gravity — Sable's built-in flat platform.
 * Floor layer is at template-relative y=1. Sublevel spawns at relative (0.5, N, 0.5),
 * falls straight down to x=0/z=0 → floor BlockPos is (0, 1, 0) in template-local coords.
 */
@GameTestHolder("true_impact")
@PrefixGameTestTemplate(false)
public final class Phase1GameTest {

    private static final String TEMPLATE_NS = "sable";
    private static final String TEMPLATE    = "physicstest.gravity";
    private static final int    TIMEOUT     = 120;

    // Floor block directly below the sublevel spawn point (relative coords).
    private static final BlockPos FLOOR_POS = new BlockPos(0, 1, 0);

    // ── High-impact: glass world block should be destroyed ──────────────────
    //
    // Stone sublevel at -120 m/s produces kImpact ≈ 80 J (empirical from live logs).
    // Neighbors are cleared in the test so confinement=0 and the base break threshold
    // (~51.6 J from BlockHardnessProfile) applies; effective damage cap = 75 J (BRITTLE
    // class: 15×5). One hit (75 J) exceeds 51.6 J → CRITICAL → destroyBlock.
    // Proves the full Path 1 pipeline: capture → queue → apply → destroyBlock.

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void highImpact_worldGlassBreaks(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        // Clear all 5 non-air neighbors so confinement=0 → effective break threshold = base
        // only (~51.6 J from BlockHardnessProfile). With confinement the surrounding stone
        // floor would raise it to ~154 J, requiring 3 hits (cap 75 J/hit). Isolated glass
        // breaks in one hit: effective = min(80 J, cap=75 J) = 75 J > 51.6 J → CRITICAL.
        helper.setBlock(FLOOR_POS.below(), Blocks.AIR.defaultBlockState());
        helper.setBlock(FLOOR_POS.north(), Blocks.AIR.defaultBlockState());
        helper.setBlock(FLOOR_POS.south(), Blocks.AIR.defaultBlockState());
        helper.setBlock(FLOOR_POS.east(),  Blocks.AIR.defaultBlockState());
        helper.setBlock(FLOOR_POS.west(),  Blocks.AIR.defaultBlockState());
        helper.setBlock(FLOOR_POS, Blocks.GLASS.defaultBlockState());

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        // Stone sublevel 6 blocks above floor; -120 m/s → kImpact >> 45 J.
        Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5));
        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPos, Blocks.STONE.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                RigidBodyHandle handle = RigidBodyHandle.of(sl);
                if (handle != null && handle.isValid()) {
                    handle.addLinearAndAngularVelocity(
                            new Vector3d(0.0, -120.0, 0.0),
                            new Vector3d(0.0,   0.0, 0.0));
                } else {
                    TrueImpactMod.LOGGER.warn("[TI-P1-TEST] handle null/invalid at tick 20");
                }
            })
            .thenExecuteAfter(70, () -> {
                BlockState state = helper.getBlockState(FLOOR_POS);
                TrueImpactMod.LOGGER.info("[TI-P1-TEST] highImpact_worldGlassBreaks floor={}", state.getBlock());
                if (!state.isAir()) {
                    helper.fail(
                        "Path 1: stone sublevel at -120 m/s should break floor glass "
                        + "(BRITTLE threshold 45 J), but block at "
                        + FLOOR_POS + " is still " + state.getBlock());
                }
            })
            .thenSucceed();
    }

    // ── Low-impact: gentle contact should NOT break glass ───────────────────
    //
    // Nudge at -0.5 m/s: kImpact << 40 J global detection gate → no damage recorded.

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void lowImpact_worldGlassSurvives(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.GLASS.defaultBlockState());

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        // Spawn closer to floor so it settles before the gentle nudge.
        Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 3.5, 0.5));
        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPos, Blocks.STONE.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(2, () -> {
                RigidBodyHandle handle = RigidBodyHandle.of(sl);
                if (handle != null && handle.isValid()) {
                    handle.addLinearAndAngularVelocity(
                            new Vector3d(0.0, -0.5, 0.0),
                            new Vector3d(0.0,  0.0, 0.0));
                }
            })
            .thenExecuteAfter(80, () -> {
                BlockState state = helper.getBlockState(FLOOR_POS);
                TrueImpactMod.LOGGER.info("[TI-P1-TEST] lowImpact_worldGlassSurvives floor={}", state.getBlock());
                if (state.isAir()) {
                    helper.fail("Path 1: gentle contact (-0.5 m/s) should NOT break floor glass, but block is gone");
                }
            })
            .thenSucceed();
    }
}
