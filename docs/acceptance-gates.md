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

Tested on dedicated server with Sable loaded, isolated free-floating structure (M=1 kpg).

| variant | input | ratio = |input|/(M·Δv) | angVelAfter | conclusion |
|---|---|---|---|---|
| `linear-only` | (10,0,0) | 1.0166 | ≈0.0003 | **Direct impulse confirmed** |
| `com-current` | (10,0,0) | 1.0165 | ≈0.0011 | COM application safe, no spurious torque |
| `at-pose-pos` | (10,0,0) | N/A (explosion) | ≈3.61e9 | **PERMANENTLY REMOVED** |

**Key finding:** `applyImpulse(body, COM, vector)` and `applyLinearAndAngularImpulse(body, vector, zero, true)`
both apply direct velocity change (Δv ≈ F/M). No `dt` factor. Ratio ≈ 1.0 in both cases.

**Safety finding:** `logicalPose().position()` as applyImpulse point produces astronomical lever arm
(≈ 2.15×10⁹ m/s Δv, server 21 s behind, Sable emergency sub-level removal).
Rule: any point argument to `applyImpulse` MUST satisfy |point − COM| ≪ 1e3 (plot-space).

**Code:** `apply-at-pose` command and `applyAtPoseExperiment` method permanently removed in commit `d69465d`.
Danger comment and coordinate-space rule recorded in `SableT4Command.java` and `T4ApplyForceExperiment.java`.

---

### T-3: `forceAmountRaw` dimension — NEXT TARGET

**Question:** Is `forceAmountRaw` (contact array offset 2) an impulse, a force, or dimensionless?
Answering this determines whether TI can use the contact array directly for damage, or must reconstruct impulse from Δv.

**Protocol (requires manual execution, no code changes needed):**

Prerequisites:
- Sable server config: `substepsPerTick = 1` (eliminates T-5 substep ambiguity)
- Two structures, equal mass M (use 1 kpg each); structure A can move freely, structure B at rest against terrain

Steps:
1. `/trueimpact debug bodies on` + `/trueimpact debug contacts on`
2. Inspect both IDs: `/trueimpact experiment t4 inspect <id_A>` — confirm `t4Ready=true`
3. Apply known impulse to A toward B: `/trueimpact experiment t4 apply-linear <id_A> 0 0 -50`
   (T-4 confirmed: this produces Δv ≈ 50 blocks/s in A immediately)
4. Wait for A to reach B and collide (1–3 ticks)
5. Collect from `latest.log`:
   - `[SNAP] phase=PRE_STEP id=A` and `id=B` → vBefore_A, vBefore_B
   - `[SNAP] phase=POST_STEP id=A` and `id=B` → vAfter_A, vAfter_B
   - `[CONTACT]` record for pair (A,B) → `forceAmountRaw`, `localNormalA`

Compute (offline):
- `J_A = M_A × |Δv_A|` (full 3D magnitude of velocity change)
- `J_B = M_B × |Δv_B|`
- `J_reconstructed` = average of J_A and J_B (Newton 3rd law check: they should be ≈ equal)
- `ratio_impulse = forceAmountRaw / J_reconstructed`
- `ratio_force = forceAmountRaw × dt / J_reconstructed` (where dt = substepDt ≈ 0.05 s at substeps=1)

Hypothesis decision:
- `ratio_impulse ≈ 1.0` → forceAmountRaw IS impulse magnitude
- `ratio_force ≈ 1.0` → forceAmountRaw is force (must multiply by dt to get impulse)
- Neither → investigate scaling (½mv²? relative velocity factor?)

**Gate passes when:** ratio is within 20% of either hypothesis for at least 2 independent collisions.
Record: M, v_A_before, v_A_after, v_B_before, v_B_after, forceAmountRaw, computed ratio.

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
