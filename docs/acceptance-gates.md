# Acceptance Gates

Each phase must pass its gate before phase N+1 work begins.

## Gate 0 — Foundation

**Automated (CI / local build):**

- [ ] `.\gradlew.bat build` exits 0
- [ ] `.\gradlew.bat test` exits 0 and reports ≥ 4 tests passed, 0 failed
- [ ] `build/libs/true_impact-0.1.0-foundation.jar` exists
- [ ] No compiler warnings about unchecked casts or raw types
- [ ] ArchUnit: 0 violations in `FoundationArchTest`

**Manual in-game (client with Sable loaded):**

- [ ] Mod loads without error in `logs/latest.log`
- [ ] `/trueimpact status` prints 5 lines (header + 4 fields)
- [ ] Version line shows `0.1.0-foundation`
- [ ] Sable line shows `detected (1.2.2)` (or actual version)
- [ ] Environment line shows `Client / Integrated Server`

**Manual in-game (dedicated server, no client):**

- [ ] Server starts without errors
- [ ] `/trueimpact status` accessible via RCON or operator console
- [ ] Environment line shows `Dedicated Server`
- [ ] No `RuntimeException` from `RuntimeDistCleaner`

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
- [ ] No regression of `narrow_phase.rs "No element at index"` crash (legacy memory [[narrow-phase-crash]])
- [ ] `enablePhysicalDestruction=false` produces zero block breaks under any impact
- [ ] `ImpactBreakQueue` defers: `destroyBlock` never called inside a Rapier physics step
