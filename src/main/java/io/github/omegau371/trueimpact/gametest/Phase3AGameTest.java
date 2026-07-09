package io.github.omegau371.trueimpact.gametest;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.neoforge.gametest.SableTestHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.omegau371.trueimpact.TrueImpactMod;
import io.github.omegau371.trueimpact.damage.CrackOverlayTracker;
import io.github.omegau371.trueimpact.damage.DamageState;
import io.github.omegau371.trueimpact.damage.ImpactRuntimeConfig;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.SublevelDamageAccumulator;
import io.github.omegau371.trueimpact.sable.SableImpactCapture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * Phase 3A integration tests: sublevel block damage when colliding with terrain.
 *
 * Run with: .\gradlew.bat runGameTestServer
 *
 * Template: sable:physicstest.gravity — Sable's built-in flat platform (stone floor + open space).
 *
 * Basic correctness:
 *   highImpact_sublevelBlockBreaks   — v=-120 m/s stone → block destroyed
 *   highImpact_glassSublevelBreaks   — v=-120 m/s glass → block destroyed
 *   lowImpact_sublevelBlockSurvives  — v=-0.5 m/s  stone → block survives
 *
 * Material threshold differentiation:
 *   materialBoundary_glassBrakes_stoneDoesNot  — v=-20 m/s: glass breaks (BRITTLE ~50J, mass 1.0kpg), stone survives (STONE ~490J, mass 2.0kpg)
 *   materialBoundary_ironBreaks_obsidianDoesNot — v=-60 m/s: iron_block breaks (METAL 1800J), obsidian survives (HIGH_STRENGTH 7500J)
 *
 * Per-sublevel tracking (proves the multi-sublevel fix):
 *   multiSublevel_allBreakSimultaneously       — 3 stones at v=-120 m/s, all must break independently
 *
 * High-strength survival:
 *   highStrength_obsidianSurvivesModerateImpact — v=-60 m/s: stone (control) breaks, obsidian survives (HIGH_STRENGTH 7500J)
 */
@GameTestHolder("true_impact")
@PrefixGameTestTemplate(false)
public final class Phase3AGameTest {

    private static final String TEMPLATE_NS = "sable";
    private static final String TEMPLATE    = "physicstest.gravity";
    private static final int    TIMEOUT     = 120; // ticks (90 logic + safety margin)

    // ── Shared helper ────────────────────────────────────────────────────────

    private static boolean hasBroken(ServerSubLevel sl) {
        return TrueImpactMod.PHASE3A_BROKEN_SUBLEVELS.contains(sl);
    }

private static void applyVelocity(ServerSubLevel sl, double vy) {
        RigidBodyHandle handle = RigidBodyHandle.of(sl);
        if (handle != null && handle.isValid()) {
            handle.addLinearAndAngularVelocity(
                    new Vector3d(0.0, vy, 0.0),
                    new Vector3d(0.0, 0.0, 0.0));
        } else {
            TrueImpactMod.LOGGER.warn("[TI3A-TEST] applyVelocity: handle null/invalid for sl={}", sl);
        }
    }

    // ── High-impact test: sublevel block should be destroyed ─────────────────

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void highImpact_sublevelBlockBreaks(GameTestHelper helper) {
        // ENABLE_BLOCK_BREAKING defaults to false for safety; must be true for Phase 3A tests.
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        // Spawn a single stone block as sublevel, 6 blocks above the floor.
        Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5));
        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPos, Blocks.STONE.defaultBlockState());
        TrueImpactMod.LOGGER.info("[TI3A-TEST] spawned sl={}", sl);

        // Wait 20 ticks for Sable to fully initialize the sublevel's rigid body,
        // then fire it downward at 120 m/s.
        // mass ≈ 0.078 kpg → kImpact = 0.5 * 0.078 * 120² ≈ 562 J > 500 J STONE threshold.
        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                RigidBodyHandle handle = RigidBodyHandle.of(sl);
                boolean valid = handle != null && handle.isValid();
                TrueImpactMod.LOGGER.info("[TI3A-TEST] @tick20 handle={} valid={}", handle != null, valid);
                if (valid) {
                    handle.addLinearAndAngularVelocity(
                            new Vector3d(0.0, -120.0, 0.0),
                            new Vector3d(0.0,   0.0, 0.0));
                    TrueImpactMod.LOGGER.info("[TI3A-TEST] velocity -120 m/s applied");
                } else {
                    TrueImpactMod.LOGGER.warn("[TI3A-TEST] handle null/invalid -- velocity NOT applied");
                }
            })
            // Check stats shortly after expected collision (≈2-3 ticks after velocity applied)
            .thenExecuteAfter(5, () -> {
                SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
                TrueImpactMod.LOGGER.info(
                        "[TI3A-TEST] @tick25 processCalls={} worldSeen={} worldStatus={} worldKImpact={}",
                        s.totalProcessCalls(), s.worldContactSeenLastTick(),
                        s.worldKImpactStatusLastTick(), s.worldKImpactLastTick());
            })
            // Wait for physics + TrueImpact deferred-damage tick to process
            .thenExecuteAfter(70, () -> {
                SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
                boolean broke = hasBroken(sl);
                TrueImpactMod.LOGGER.info(
                        "[TI3A-TEST] @tick95 processCalls={} worldStatus={} worldKImpact={} broke={}",
                        s.totalProcessCalls(), s.worldKImpactStatusLastTick(),
                        s.worldKImpactLastTick(), broke);
                if (!broke) {
                    helper.fail("Phase 3A: expected sublevel to be destroyed on high-speed impact, "
                            + "but sl=" + sl + " not in PHASE3A_BROKEN_SUBLEVELS. "
                            + "processCalls=" + s.totalProcessCalls()
                            + " worldStatus=" + s.worldKImpactStatusLastTick()
                            + " worldKImpact=" + s.worldKImpactLastTick());
                }
            })
            .thenSucceed();
    }

    // ── Low-impact test: gentle contact should NOT destroy block ─────────────

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void lowImpact_sublevelBlockSurvives(GameTestHelper helper) {
        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 3.5, 0.5));
        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPos, Blocks.STONE.defaultBlockState());

        // A very slow downward nudge: kImpact << 40 J detection threshold
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
                if (hasBroken(sl)) {
                    helper.fail("Phase 3A: gentle contact should NOT destroy sublevel block, "
                            + "but sl=" + sl + " found in PHASE3A_BROKEN_SUBLEVELS");
                }
            })
            .thenSucceed();
    }

    // ── Material boundary: glass breaks, stone survives ──────────────────────
    //
    // Verified empirically (dev server, LOG_PHASE3A_VERBOSE) against the real
    // BlockHardnessProfile formula, single-block sublevel masses glass=1.0 kpg,
    // stone=2.0 kpg, and the face-spread halving (single solid cell -> totalW
    // floor 2.0 -> cellK = kImpact/2):
    //
    // At -20 m/s:
    //   glass raw kImpact = 0.5*1.0*20^2 = 200 J -> cellK=100 J vs breakJ=49.9 J -> breaks
    //   stone raw kImpact = 0.5*2.0*20^2 = 400 J -> cellK=200 J vs breakJ=489.8 J -> survives (BRUISED)
    //
    // (The previous -10 m/s velocity put glass exactly on the break boundary --
    // accumulated ratio measured at 0.986, just under 1.0 -- a coin-flip pass/fail
    // rather than a real material-threshold demonstration.)
    //
    // Threshold ratio check: iron_block is ~4.25× heavier than stone in Sable (real density),
    // so "stone breaks, iron survives" is physically impossible at any single velocity
    // (iron kImpact would exceed its 1800 J threshold before stone exceeds 500 J).
    // The pair glass/stone works because the threshold ratio (500/45 = 11×) exceeds
    // the mass ratio (stone/glass ≈ 1.8×).

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void materialBoundary_glassBrakes_stoneDoesNot(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        ServerSubLevel slGlass = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5)),
                Blocks.GLASS.defaultBlockState());
        ServerSubLevel slStone = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(3.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                applyVelocity(slGlass, -20.0);
                applyVelocity(slStone, -20.0);
            })
            .thenExecuteAfter(70, () -> {
                boolean glassBroke = hasBroken(slGlass);
                boolean stoneBroke = hasBroken(slStone);
                TrueImpactMod.LOGGER.info("[TI3A-BOUNDARY] glass={} stone={}", glassBroke, stoneBroke);
                if (!glassBroke) helper.fail("glass should break at -20 m/s (BRITTLE ~50 J, mass 1.0 kpg)"
                        + " but didn't");
                if (stoneBroke)  helper.fail("stone should NOT break at -20 m/s (STONE ~490 J, mass 2.0 kpg)"
                        + " but did");
            })
            .thenSucceed();
    }

    // ── Material boundary: iron_block breaks, obsidian survives ──────────────
    //
    // At -60 m/s (total ≈ -70 m/s after gravity):
    //   iron_block kImpact ≈ 4500 J  > 1800 J (METAL)        → should break
    //   obsidian   kImpact ≈ 1060 J  < 7500 J (HIGH_STRENGTH) → should survive
    //
    // Iron is ~4.25× heavier than stone in Sable (real density model), so
    // iron kImpact at this velocity far exceeds the METAL 1800 J threshold.
    // Obsidian (stone-like density) stays well below the HIGH_STRENGTH 7500 J limit.
    // This proves the METAL → HIGH_STRENGTH threshold ladder is enforced correctly.

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void materialBoundary_ironBreaks_obsidianDoesNot(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        ServerSubLevel slIron = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5)),
                Blocks.IRON_BLOCK.defaultBlockState());
        ServerSubLevel slObsidian = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(3.5, 6.5, 0.5)),
                Blocks.OBSIDIAN.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                applyVelocity(slIron,     -60.0);
                applyVelocity(slObsidian, -60.0);
            })
            .thenExecuteAfter(70, () -> {
                boolean ironBroke     = hasBroken(slIron);
                boolean obsidianBroke = hasBroken(slObsidian);
                TrueImpactMod.LOGGER.info("[TI3A-BOUNDARY] iron={} obsidian={}", ironBroke, obsidianBroke);
                if (!ironBroke)     helper.fail("iron_block should break at -60 m/s (METAL 1800 J) but didn't");
                if (obsidianBroke)  helper.fail("obsidian should NOT break at -60 m/s (HIGH_STRENGTH 7500 J) but did");
            })
            .thenSucceed();
    }

    // ── Multi-sublevel: all 3 break simultaneously ────────────────────────────
    //
    // Three stone sublevels fall and hit the floor in the same physics substep.
    // Proves that the per-sublevel tracking (phase3aCpMap/phase3aSnapMap/phase3aKMap)
    // correctly enqueues independent damage events for each sublevel.

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void multiSublevel_allBreakSimultaneously(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        ServerSubLevel slA = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());
        ServerSubLevel slB = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(2.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());
        ServerSubLevel slC = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(4.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                applyVelocity(slA, -120.0);
                applyVelocity(slB, -120.0);
                applyVelocity(slC, -120.0);
            })
            .thenExecuteAfter(70, () -> {
                boolean brokeA = hasBroken(slA);
                boolean brokeB = hasBroken(slB);
                boolean brokeC = hasBroken(slC);
                TrueImpactMod.LOGGER.info("[TI3A-MULTI] A={} B={} C={}", brokeA, brokeB, brokeC);
                if (!brokeA) helper.fail("sublevel A should break at -120 m/s");
                if (!brokeB) helper.fail("sublevel B should break at -120 m/s");
                if (!brokeC) helper.fail("sublevel C should break at -120 m/s");
            })
            .thenSucceed();
    }

    // ── High-strength: obsidian survives where stone would break ──────────────
    //
    // At -60 m/s (total ≈ -70 m/s after gravity):
    //   stone kImpact ≈ 4000-5000 J  > 500 J  (STONE)        → should break (control)
    //   obsidian kImpact expected < 7500 J (HIGH_STRENGTH)    → should survive

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void highStrength_obsidianSurvivesModerateImpact(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        ServerSubLevel slStone   = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());
        ServerSubLevel slObsidian = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(3.5, 6.5, 0.5)),
                Blocks.OBSIDIAN.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                applyVelocity(slStone,    -60.0);
                applyVelocity(slObsidian, -60.0);
            })
            .thenExecuteAfter(70, () -> {
                boolean stoneBroke    = hasBroken(slStone);
                boolean obsidianBroke = hasBroken(slObsidian);
                TrueImpactMod.LOGGER.info("[TI3A-HIGHSTR] stone={} obsidian={}", stoneBroke, obsidianBroke);
                if (!stoneBroke)    helper.fail("stone control should break at -60 m/s (STONE 500 J)");
                if (obsidianBroke)  helper.fail("obsidian should NOT break at -60 m/s (HIGH_STRENGTH 7500 J)");
            })
            .thenSucceed();
    }

    // ── Crack overlay: sub-threshold hit must produce BRUISED/CRACKED state ────
    //
    // Verified empirically (dev server, LOG_PHASE3A_VERBOSE) against the real
    // BlockHardnessProfile formula: stone single-block sublevel, mass 2.0 kpg,
    // breakJ=489.8 J. At -20 m/s: raw kImpact=0.5*2.0*20^2=400 J, halved by the
    // single-cell face-spread floor (totalW>=2.0) to cellK=200 J -> ratio=0.408
    // -> DamageState.BRUISED, comfortably inside [0.25, 0.60) with margin from
    // both boundaries (the previous -15 m/s measured ratio=0.245, landing right
    // on the INTACT/BRUISED edge -- a coin-flip pass/fail, not a real margin).
    // This is the canonical test that SublevelDamageAccumulator feeds crack packets correctly.

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void crackOverlay_stoneShowsCrackBeforeBreaking(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(container,
                SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5)),
                Blocks.STONE.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> applyVelocity(sl, -20.0))
            .thenExecuteAfter(70, () -> {
                boolean broke = hasBroken(sl);
                // Single block sublevel: local coords are always (0, 0, 0).
                SublevelDamageAccumulator.Snapshot snap =
                        SublevelDamageAccumulator.getSnapshot(sl.getRuntimeId(), 0, 0, 0,
                                MaterialThresholdProfile.MaterialClass.STONE);
                DamageState state  = snap != null ? snap.damageState() : null;
                double ratio       = snap != null ? snap.ratio()       : Double.NaN;
                int crackProgress  = snap != null
                        ? CrackOverlayTracker.ratioToProgress(state, ratio) : -1;

                TrueImpactMod.LOGGER.info(
                        "[TI3A-CRACK] broke={} state={} ratio={} crackProgress={}",
                        broke, state, String.format("%.3f", ratio), crackProgress);

                if (broke) {
                    helper.fail("stone at -20 m/s should NOT break (STONE threshold ~490 J)");
                }
                if (snap == null) {
                    helper.fail("no damage accumulated — impact not detected (below 40 J detection gate?)");
                }
                if (state == DamageState.INTACT || state == null) {
                    helper.fail("expected BRUISED or CRACKED state, got " + state
                            + " (ratio=" + String.format("%.3f", ratio) + ")");
                }
                if (crackProgress < 0) {
                    helper.fail("expected crack progress >= 0 for state=" + state
                            + " ratio=" + String.format("%.3f", ratio)
                            + " but ratioToProgress returned " + crackProgress);
                }
                // Clean up so this sublevel's runtimeId can be safely reused by other tests.
                SublevelDamageAccumulator.clearForRuntimeId(sl.getRuntimeId());
            })
            .thenSucceed();
    }

    // ── Material variety: glass sublevel should break at lower energy ─────────

    @GameTest(templateNamespace = TEMPLATE_NS, template = TEMPLATE, timeoutTicks = TIMEOUT)
    public static void highImpact_glassSublevelBreaks(GameTestHelper helper) {
        ImpactRuntimeConfig.ENABLE_BLOCK_BREAKING = true;

        ServerSubLevelContainer container = (ServerSubLevelContainer)
                SubLevelContainer.getContainer(helper.getLevel());

        Vector3d spawnPos = SableTestHelper.absolutePosition(helper, new Vector3d(0.5, 6.5, 0.5));
        ServerSubLevel sl = SableTestHelper.spawnSingleBlockSubLevel(
                container, spawnPos, Blocks.GLASS.defaultBlockState());

        helper.startSequence()
            .thenExecuteAfter(20, () -> {
                RigidBodyHandle handle = RigidBodyHandle.of(sl);
                if (handle != null && handle.isValid()) {
                    handle.addLinearAndAngularVelocity(
                            new Vector3d(0.0, -120.0, 0.0),
                            new Vector3d(0.0,   0.0, 0.0));
                }
            })
            .thenExecuteAfter(70, () -> {
                if (!hasBroken(sl)) {
                    SableImpactCapture.RuntimeStats s = SableImpactCapture.stats();
                    helper.fail("Phase 3A: glass sublevel should break on 120 m/s impact, "
                            + "but sl=" + sl + " not in PHASE3A_BROKEN_SUBLEVELS. "
                            + "processCalls=" + s.totalProcessCalls()
                            + " worldStatus=" + s.worldKImpactStatusLastTick()
                            + " worldKImpact=" + s.worldKImpactLastTick());
                }
            })
            .thenSucceed();
    }
}
