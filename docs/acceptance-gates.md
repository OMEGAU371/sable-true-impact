# Acceptance Gates

Each phase must pass its gate before phase N+1 work begins.

## Gate 0 — Foundation — AUTOMATED PASSED / MANUAL PENDING (2026-06-05)

**Automated (CI / local build):**

- [x] `.\gradlew.bat clean test build` exits 0
- [x] 20 tests passed, 0 failed: 4 ArchUnit + 12 DeployFilter safety + 4 unit
- [x] `build/libs/true_impact-0.1.0-foundation.jar` exists (6.6 KB)
- [x] `build/libs/true_impact-0.1.0-foundation-sources.jar` exists (4.2 KB)
- [x] Main JAR MANIFEST.MF contains `TI-Project-Line: rewrite2`
- [x] ArchUnit: 0 violations in `FoundationArchTest`
- [x] `TrueImpactVersion.java` is auto-generated from template; no hardcoded version

**Deploy safety (verified by DeployFilterTest — 12 assertions):**

- [x] JAR with `TI-Project-Line: rewrite2` identified as own → eligible for deletion
- [x] Legacy `true_impact-1.x.x.jar` without marker → NOT deleted
- [x] `sable-neoforge-*.jar` / `create-*.jar` → NOT touched
- [x] `-sources.jar` / `-javadoc.jar` → NOT deleted, NOT copied to mods dir
- [x] Corrupt/empty JAR → returns false safely (no exception)
- [x] Main JAR selected by explicit name (`true_impact-${mod_version}.jar`), not listFiles()[0]

**Server runtime (automated via runServer):**

- [x] `runServer` starts without crash
- [x] Log: `True Impact 0.1.0-foundation initializing` (from mod constructor)
- [x] Log: `Done (12.352s)!` — server fully started
- [x] No `RuntimeDistCleaner` exception
- [x] No `ClassNotFoundException` or `NoClassDefFoundError`
- [x] Server process stopped cleanly, no orphaned background processes
- [x] Version `0.1.0-foundation` visible in mod discovery log from ModContainer

**Manual verification still required (cannot automate headless):**

- [ ] `/trueimpact status` command output verified in-game via client
- [ ] Sable detection line shows `detected (X.Y.Z)` when Sable is loaded
- [ ] Environment line shows `Client / Integrated Server` on client
- [ ] Environment line shows `Dedicated Server` on dedicated server

**Git:**

- [x] Repository initialized (`git init`)
- [x] Initial commit: `82bdddd` (22 files, Phase 0 scaffold)

---

---

## Gate 1A — Phase 1A Observation Layer (Sable Impulse Study) — IN PROGRESS

### T-4: applyForce/applyImpulse semantics — MANUALLY PASSED (2026-06-06)

Tested in-game with Sable loaded, isolated free-floating structure (M=1 kpg).
(Log source: consistent with integrated server / live game; dedicated server not separately confirmed.)

Two separate test runs:

**Run 1 — at-pose-pos explosion (pre-0.2.1 build):**

| variant | input | delta-v magnitude | angular speed | outcome |
|---|---|---|---|---|
| `at-pose-pos` | (100, 0, 0) | ≈ 2.15×10⁹ | ≈ 3.61×10⁹ | **EXPLOSION — server 21 s behind, Sable emergency removal** |

**Run 2 — safety verification after at-pose-pos removed (0.2.1 build):**

| variant | input | ratio = inputMagnitude/(M·Δv) | angVelAfter | conclusion |
|---|---|---|---|---|
| `linear-only` | (10, 0, 0) | 1.0166 | ≈ 0.0003 | **Direct impulse confirmed** |
| `com-current` | (10, 0, 0) | 1.0165 | ≈ 0.0011 | COM application safe, no spurious torque |

**Key finding:** `applyImpulse(body, COM, vector)` and `applyLinearAndAngularImpulse(body, vector, zero, true)`
both apply direct velocity change (Δv ≈ F/M). No `dt` factor. Ratio ≈ 1.0 in both cases.

**Safety finding:** `logicalPose().position()` as applyImpulse point produces astronomical lever arm
because both position and COM are in embedded/plot space (~204810xx). The difference `(pose_pos − COM)`
is in plot-space units, yielding an astronomically large lever arm for any real structure.
Rule: any point argument to `applyImpulse` MUST satisfy |point − COM| ≪ 1e3 (plot-space).

**Code:** `apply-at-pose` command and `applyAtPoseExperiment` method permanently removed in commit `d69465d`.
Danger comment and coordinate-space rule recorded in `SableT4Command.java` and `T4ApplyForceExperiment.java`.

---

### T-3: `forceAmountRaw` dimension — NEXT TARGET

**Question:** Is `forceAmountRaw` (contact array offset 2) an impulse, a force, or something else?
Answering this determines whether TI can use the contact array directly for damage, or must reconstruct impulse from Δv.

**Protocol (requires manual execution; no code changes needed):**

**Prerequisites:**
- Sable server config: `substepsPerTick = 1` (eliminates T-5 substep ambiguity; substepDt ≈ 0.05 s)
- Two **free-floating** sub-levels A and B — neither touching terrain — equal mass M = 1 kpg each
- A and B start at rest, separated by ≈ 1–2 blocks

**Steps:**
1. `/trueimpact debug bodies on` + `/trueimpact debug contacts on`
2. Inspect both: `/trueimpact experiment t4 inspect <id_A>` and `<id_B>` — confirm `t4Ready=true` for both
3. Apply known impulse to A toward B: `/trueimpact experiment t4 apply-linear <id_A> 0 0 -50`
   (T-4 confirmed: produces Δv ≈ 50 block/s in A, direct impulse, no dt factor)
4. Wait for A to collide with B (1–3 ticks)
5. Collect from `latest.log` for the collision tick:
   - `[SNAP] phase=PRE_STEP  id=A` → **v_before_A**
   - `[SNAP] phase=PRE_STEP  id=B` → **v_before_B**
   - `[SNAP] phase=POST_STEP id=A` → **v_after_A**
   - `[SNAP] phase=POST_STEP id=B` → **v_after_B**
   - All `[CONTACT]` entries where pair = (A, B) → aggregate: **sum_F = Σ forceAmountRaw**;
     take `localNormalA` from the first record as representative contact normal

**Offline computation:**

```
# Convert localNormalA to world space using body A's orientation quaternion
# (use oriX/Y/Z/W from body A's PRE_STEP snapshot)
n_world = rotate(localNormalA, quat_A)
# T-6 normal direction convention not yet confirmed → use abs(dot) to absorb sign ambiguity

Δv_A = v_after_A - v_before_A
Δv_B = v_after_B - v_before_B

J_A = M_A * abs(dot(Δv_A, n_world))   # impulse of A projected along contact normal
J_B = M_B * abs(dot(Δv_B, n_world))   # impulse of B projected along contact normal

J_reconstructed = (J_A + J_B) / 2     # Newton 3rd law: should be ≈ equal
contaminated = abs(J_A - J_B) / J_reconstructed > 0.30   # > 30% divergence → external force, mark invalid

ratio_impulse = sum_F / J_reconstructed           # ≈ 1.0 → forceAmountRaw is impulse
ratio_force   = sum_F * substepDt / J_reconstructed  # ≈ 1.0 → forceAmountRaw is force (needs ×dt)
```

**Hypothesis decision:**
- `ratio_impulse ≈ 1.0` (±20%) → `forceAmountRaw` IS impulse; can use directly
- `ratio_force ≈ 1.0` (±20%) → `forceAmountRaw` is force; multiply by `substepDt` for impulse
- Neither → record raw data; investigate other relationships (½mv², relative velocity scaling, etc.)

**Cautions:**
- Sable filter: records with `forceAmountRaw ≤ 25 × min(M_A, M_B)` are dropped before logging.
  At M = 1 kpg, any record that appears already passed `forceAmountRaw > 25`.
  If no `[CONTACT]` entries appear, increase A's launch speed (try ≥ 50 block/s).
- Multiple `[CONTACT]` entries may exist for the same pair in one tick → **must sum** `forceAmountRaw`; a single entry is not the total.
- T-6 normal direction unconfirmed → always use `abs(dot(..., n_world))`; note sign uncertainty in result.

**Gate passes when:** at least 2 independent free-floating collisions, both non-contaminated, with ratio stable within ±20% of the same hypothesis.

---

## Gate 1 — Impact Event Model *(future)*

- [ ] Build passes with new `physics/` package ArchUnit rules enforced
- [ ] With Sable: at least one impact event logged per collision at DEBUG level
- [ ] No `NullPointerException` on empty `ImpactReducer` flush
- [ ] Dedicated server: no client-class loading exceptions

---

## Gate 2 — Material Profiles *(future)*

- [ ] `BlockHardnessProfile.of(level, pos, state)` returns non-null for all vanilla blocks tested
- [ ] `BlockHardnessProfile` is the ONLY class calling `BlockState.getDestroySpeed`
- [ ] ArchUnit: rule R-hardness-single-source passes

---

## Gate 3 — Crack Accumulation *(future)*

- [ ] Crack overlay visible after repeated impacts in singleplayer
- [ ] Dedicated server: no `ClientLevel` reference loaded on server classpath
- [ ] Crack progress resets correctly on block change

---

## Gate 4 — Destruction Pipeline *(future)*

- [ ] Block breaks after sustained impact
- [ ] No regression of `narrow_phase.rs "No element at index"` crash
- [ ] `enablePhysicalDestruction=false` produces zero block breaks under any impact
- [ ] `ImpactBreakQueue` defers: `destroyBlock` never called inside a Rapier physics step
