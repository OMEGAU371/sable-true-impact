# Acceptance Gates

Each phase must pass its gate before phase N+1 work begins.

## Gate 0 — Foundation  ✅ PASSED 2026-06-05

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
