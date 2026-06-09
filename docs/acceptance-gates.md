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

### T-3: `forceAmountRaw` dimension — MANUALLY PASSED (2026-06-09)

**Conclusion: `forceAmountRaw` is a per-substep force value, NOT an impulse.**

Canonical conversion: `J = forceAmountRaw * substepDt`

**Evidence from live test (tick=31404, substepCount=2, substepDt=0.025s):**

| field | value |
|---|---|
| activeVsActive contacts | 4 |
| sumForceAmountRaw | 3923.88 |
| sumReconJ (from delta-v) | 98.39 |
| estimatedImpulseJ (= sumForce * substepDt) | 98.10 |
| ratioRawOverReconJ | 39.88 |
| 1 / substepDt | 40.00 |

`ratioRawOverReconJ ~= 40 ~= 1/substepDt` confirms force-per-substep semantics.
`estimatedImpulseJ` and `sumReconJ` differ by < 0.3% -- excellent agreement.

**Demoted: `ratioRawOverNomMom`** -- computed from post-step closing velocity, which is near zero
after the solver resolves the collision. Reflects residual kinematic state, not the peak impact impulse.
Do NOT use for damage formula. Kept in logs as diagnostic-only.

**Contact phase classification** (added in 0.2.7):

| phase | condition | use for damage? |
|---|---|---|
| `IMPACT_CANDIDATE` | activeVsActive > 0 AND estimatedImpulseJ/contact > 5.0 | **Yes -- primary damage input** |
| `SUSTAINED_CONTACT` | activeVsActive > 0, low impulse/contact | No -- resting/sliding support force |
| `WORLD_VS_ACTIVE` | activeVsActive = 0, terrain vs sub-level | Out of scope for Phase 1A |
| `NO_ACTIVE_CONTACT` | no active bodies on either side | Skip |

**Next phase foundation:**
`J = forceAmountRaw * substepDt` is the root formula for structure-vs-structure impulse.
Phase 1B will use `estimatedImpulseJ` from `[T-3-SUMMARY]` as input to the damage resolver.

---

## Gate 1B -- Phase 1B Damage Pipeline Skeleton -- MANUALLY PASSED (2026-06-10)

**Objective:** ImpactRecord data contract and SableImpactCapture pipeline running
unconditionally, independent of all diagnostic flags. DamageResolver always NONE.

### Build verification (automated)

| check | result |
|---|---|
| `.\gradlew.bat build` | PASS -- all tests pass, ArchUnit 0 violations |
| ArchUnit R9: physics/ no TI internal deps | PASS |
| ArchUnit R11: damage/ no diagnostic/observation deps | PASS |
| ArchUnit R13: SableImpactCapture no DiagnosticConfig dep | PASS |
| DamageResolverTest: resolver always NONE | PASS |
| SableImpactCaptureTest: 25+ cases | PASS |

### In-game verification (2026-06-10, version 0.3.6-phase1b, debug ALL OFF)

Test setup: dedicated server, two active sub-levels, all TI diagnostic flags disabled.

```
/trueimpact debug all off
/trueimpact debug status   (before collision)
```
```
[TI diag] enabled=false bodies=false contacts=false callbacks_t1t2=false t7=false t4Pending=0
[TI capture] calls=195 rawContacts=0 records=0 lastTick=-1 lastRecords=0 ...
[TI capture last-hit] tick=-1 records=0 activeImpact=0 sustained=0
```

After active-vs-active collision (bodies allowed to collide freely):

```
[TI capture] calls=2978 rawContacts=19156 records=59 ...
[TI capture last-hit] tick=77296 records=1 activeImpact=1 sustained=0
```

| check | result |
|---|---|
| pipeline runs without debug flags | `calls` increases each tick with all flags off -- PASS |
| active-vs-active contact detected | `records=59` (cumulative), `activeImpact=1` at last-hit tick -- PASS |
| last-hit counters correct | `tick=77296` preserved after bodies separate -- PASS |
| no block damage | zero block breaks observed; DamageResolver returned NONE for all inputs -- PASS |
| lastPostSnaps populated unconditionally | ImpactRecords generated with `ENABLED=false` -- PASS |

### Phase 1B architectural guarantees established

- `lastPostSnaps` rebuilt from scratch each substep; no stale body entries
- `SableImpactCapture.process()` runs before diagnostic gate in mixin (PATH A always active)
- ArchUnit R13 enforces SableImpactCapture never reads DiagnosticConfig
- World-vs-active and unknown pairs discarded at capture layer; ImpactRecord = active-vs-active only
- `contactCount` stored as diagnostic metadata only; not used in any formula

---

## Gate 1C -- Phase 1C Damage Calculation (Diagnostics Only) *(in progress)*

See `docs/phase-1c-damage-model.md` for design.
DamageResolver remains NONE throughout Phase 1C.

- [x] `ImpactMetrics` record defined in physics/ with all five calculation outputs
- [x] SableImpactCapture computes ImpactMetrics for every active-vs-active ImpactRecord
- [x] `/trueimpact debug status` outputs last-record and last-impact ImpactMetrics (no gameplay effect)
- [x] `/trueimpact debug status` outputs T-8 rolling ratio stats (n/last/min/avg/p50/max)
- [ ] T-8: impactEnergyJ = J^2/(2*m_eff) validated against 3D relative kinetic delta
- [ ] materialThresholdJ placeholder calibrated against one known-fragile block type
- [x] DamageResolver still returns NONE; exceedsThreshold is logged only

---

## Gate 1 -- Impact Event Model *(future)*

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
