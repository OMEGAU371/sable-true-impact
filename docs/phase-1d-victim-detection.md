# Phase 1D -- Victim Block Detection (Diagnostics Only)

## Status: DIAGNOSTIC-ONLY (no damage, no destroyBlock)

Phase 1D adds the minimum infrastructure to detect WHICH block type was struck in a
collision event, and displays it in the `/trueimpact debug status` threshold line.

DamageResolver still returns NONE. No block is cracked, accumulated, or destroyed.

---

## Audit: What Contact Data Is Available

### clearCollisions() double[N*15] array

15 doubles per contact entry (source-proven: RapierPhysicsPipeline.java):

| Offset | Field | Notes |
|--------|-------|-------|
| 0-1    | idA, idB | rigid body runtime IDs |
| 2      | forceAmountRaw | per-substep force [T-3 confirmed] |
| 3-5    | localNormalA | BODY_COM_LOCAL direction [T-6 UC] |
| 6-8    | localNormalB | BODY_COM_LOCAL direction |
| 9-11   | localPointA | BODY_COM_LOCAL contact point [block] |
| 12-14  | localPointB | BODY_COM_LOCAL contact point [block] |

**Block position: NOT present.** Contact entries identify bodies by runtimeId only.
When one body's runtimeId is not in `lastPostSnaps` (active sub-levels), it is a
non-active body -- typically world/terrain, but not proven as such (C2-codex).

**Block type: NOT present.** No block registry ID or BlockState in the contact array.

### BlockSubLevelCollisionCallback

```java
// Fires during Rapier3D.step() for each block-vs-sublevel contact:
CollisionResult sable$onCollision(BlockPos pos, Vector3d hitPos, double impactVelocity)
```

The callback wrapper (`DiagnosticCallbackWrapperMixin`) already intercepts this.
Key data available per call:

| Field | Available | Notes |
|-------|-----------|-------|
| `state` (BlockState) | YES (bake time) | Block type captured when collider was baked |
| `pos` (BlockPos) | YES | Block position -- coordinate space UNCONFIRMED [T-2] |
| `hitPos` (Vector3d) | YES | Continuous hit position -- coordinate space UNCONFIRMED |
| `impactVelocity` | always 0.0 | Sable 1.2.2 known issue (Q-4) |

**This callback is the primary Phase 1D data source for victim block type.**

### Contact point reconstruction (not implemented in Phase 1D)

`localPointA/B` could theoretically be transformed to world space:
  `world = Q * localPoint + bodyPos`  (proven transform from coordinate-systems.md)

BUT:
- localPoint is on the SUB-LEVEL surface, not at the victim block
- The victim block is just outside the surface: requires offset by contact normal
- Contact normal convention is T-6 UNCONFIRMED
- 1-block positional error expected
- Classified as confidence=CONTACT_POINT_SAMPLE, not implemented in Phase 1D

---

## Phase 1D Implementation

### VictimInfo record (damage/VictimInfo.java)

Carries detected victim block information for one contact event:

```
kind:              ACTIVE_SUBLEVEL | WORLD_BLOCK | UNKNOWN
blockId:           registry id e.g. "minecraft:stone"; null if no block
posX/Y/Z:         block position (T-2 space unconfirmed)
hasPos:            false if pos excluded (embedded coords heuristic or unavailable)
confidence:        EXACT | APPROX | UNKNOWN
source:            CALLBACK_BLOCK_POS | CONTACT_POINT_SAMPLE | NONE
materialClass:     from MaterialThresholdProfile.classify(blockId)
materialThresholdJ: from MaterialThresholdProfile.threshold(materialClass)
```

**No Minecraft imports.** Factory methods: `activeSublevel()`, `unknown()`,
`worldBlock(blockId, x, y, z, confidence, source)`, `worldBlockNoPos(...)`.

### SableVictimCapture (sable/SableVictimCapture.java)

Static capture buffer:

```
clearForTick()            -- called by SableEventBridge.onPreStep() at substep 0
captureCallbackBlock(...) -- called by DiagnosticCallbackWrapperMixin when ENABLED
buildWorldVictimInfo()    -- called by SableImpactCapture.process() after all substeps
```

Does NOT import DiagnosticConfig (R13 preserved for SableImpactCapture callers).

### Lifecycle

```
substep 0 start:
  SableEventBridge.onPreStep(substep=0, ENABLED) -> SableVictimCapture.clearForTick()

during each substep:
  Rapier3D.step() -> BlockSubLevelCollisionCallback.sable$onCollision() fires
    -> DiagnosticCallbackWrapperMixin (when ENABLED):
       blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()
       posLooksWorld = |posX| <= 1_000_000 && |posZ| <= 1_000_000  [embedded-coord heuristic]
       SableVictimCapture.captureCallbackBlock(blockId, pos, posLooksWorld)
       [last-write-wins if multiple blocks hit per tick]

after all substeps:
  Rapier3D.clearCollisions() -> DiagnosticContactCaptureMixin.captureContactData()
    -> SableImpactCapture.process():
       - both bodies missing -> skip (unchanged)
       - one body missing -> sawWorldContact = true (Phase 1D: detect before discard)
       - both bodies in lastPostSnaps -> active-vs-active processing (unchanged)
       
       after loop:
         if sawWorldContact -> tickVictim = SableVictimCapture.buildWorldVictimInfo()
         else if active-vs-active pairs found -> tickVictim = VictimInfo.activeSublevel()
         store in SableImpactCapture.lastVictimInfo (static)

query:
  /trueimpact debug status -> DiagnosticCommand.status()
    -> SableImpactCapture.stats().lastVictimInfo()
    -> [TI capture threshold] line: victimKind, victimBlock, confidence, materialClass, threshold
```

### VictimInfo update policy

`lastVictimInfo` in `SableImpactCapture.RuntimeStats` is updated when any contact event
is detected (world or active-vs-active). Not overwritten by zero-contact ticks (preserves
last known value, similar to `lastNonZeroRecordTick` behavior).

On `resetCounters()`: set to null.

---

## Status Output (Line 10 updated)

After Phase 1D, `[TI capture threshold]` shows:

```
[TI capture threshold] victimKind=WORLD_BLOCK victimBlock=minecraft:stone
  victimPos=(5,64,5) confidence=APPROX source=CALLBACK_BLOCK_POS
  materialClass=STONE threshold=50.000
  kImpact=13.900 kBand=MEDIUM wouldExceed=false note=diagnostic-only
```

Or for active-vs-active:

```
[TI capture threshold] victimKind=ACTIVE_SUBLEVEL victimBlock=none victimPos=unknown
  confidence=EXACT source=NONE materialClass=GENERIC threshold=50.000
  kImpact=13.900 kBand=MEDIUM wouldExceed=false note=diagnostic-only
```

Or when no data:

```
[TI capture threshold] none
```

---

## Confidence Classification

| Situation | confidence | source | Notes |
|-----------|-----------|--------|-------|
| Active-vs-active | EXACT | NONE | Kind known definitively; no world block |
| World contact + callback + posLooksWorld=true | APPROX | CALLBACK_BLOCK_POS | pos space T-2 unconfirmed |
| World contact + callback + posLooksWorld=false | APPROX | CALLBACK_BLOCK_POS | Excluded embedded coords; hasPos=false |
| World contact but no callback (callbacks off) | UNKNOWN | NONE | No block data captured |

**Upgrade path**: When T-2 experiment confirms callback pos space = WORLD,
change `Confidence.APPROX` to `Confidence.EXACT` in `SableVictimCapture.buildWorldVictimInfo()`.

---

## Why Victim Block Cannot Be Exactly Located (Phase 1D Limitations)

1. **clearCollisions() has no block coords**: The 15-double contact array identifies bodies by
   runtimeId only. No block position or type is encoded.

2. **BlockSubLevelCollisionCallback pos is T-2 UNCONFIRMED**: The `(x,y,z)` argument's
   coordinate space has not been experimentally verified. It could be world coords, plot-relative,
   or embedded-level coords (~4e7 range). The `posLooksWorld` heuristic filters out likely
   embedded-level values (|coord| > 1M) but is not a substitute for T-2 confirmation.

3. **Contact point transform produces surface point, not victim block**: `localPointA/B`
   transformed to world space lands on the sub-level's surface, not at the struck block.
   An additional offset by the contact normal direction would be needed, but T-6 (normal
   convention) is unconfirmed. This method deferred to Phase 1D+ as CONTACT_POINT_SAMPLE.

4. **Multiple blocks per tick; last-write-wins**: If a sub-level touches multiple different
   block types in one tick, only the last-fired callback is retained. The association between
   a specific clearCollisions body-pair entry and a specific block callback is not tracked.

**Next steps required for EXACT confidence:**

| Experiment | Purpose |
|------------|---------|
| T-2 | Confirm callback pos coordinate space (world? plot-relative? embedded?) |
| T-6 | Confirm localNormalA convention (direction, sign, frame) |

Once T-2 passes: change APPROX -> EXACT in `SableVictimCapture.buildWorldVictimInfo()`.
Once T-6 passes: implement CONTACT_POINT_SAMPLE fallback for blocks without callbacks.

---

## What Phase 1D Does NOT Do

- No destroyBlock calls.
- No crack overlay.
- No DamageAccumulator per-block state.
- DamageResolver.resolve() always returns DamageEvent.NONE.
- contactCount NOT used in any formula.
- wouldExceed does NOT trigger any game state change.
- VictimInfo is display-only; it never flows into DamageResolver.
- No BlockHardnessProfile (vanilla hardness reads) -- that is Phase 1E+.
