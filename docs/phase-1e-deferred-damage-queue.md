# Phase 1E -- Deferred Damage Queue (Diagnostics Only)

## Status: DIAGNOSTIC-ONLY (no destroyBlock, no crack, no accumulator)

Phase 1E is the last diagnostic-only phase before Phase 2A (first real damage effect).
It introduces the deferred damage queue: candidate damage events are enqueued during
the physics tick (clearCollisions) and flushed on ServerTickEvent.Post (safe window).

Phase 1E flush is count/log only. DamageResolver still returns NONE.

---

## Architecture

```
During physics tick (clearCollisions processing, server thread):
  SableImpactCapture.process()
    -> detects world-vs-active contact
    -> computes kImpact (single-body velocity estimate)
    -> if WORLD_BLOCK AND finite kImpact > materialThreshold:
         DeferredDamageQueue.enqueue(DeferredDamageEvent)

After physics tick (ServerTickEvent.Post, safe window, same server thread):
  TrueImpactMod.onServerTickPost()
    -> DeferredDamageQueue.flush()
       Phase 1E: count + retain lastFlushed, no world mutations
       Phase 2A: will call DamageResolver.resolve() per event
```

---

## Data flow

### DeferredDamageEvent (damage/)

```java
public record DeferredDamageEvent(
    long   serverTick,
    String victimBlock,      // e.g. "minecraft:stone"
    int    posX, posY, posZ,
    MaterialThresholdProfile.MaterialClass materialClass,
    double kImpact,          // Phase 1C velocity-derived canonical
    double threshold,        // material threshold at enqueue time
    VictimInfo.Source     source,
    VictimInfo.Confidence confidence
)
```

victimKind is implicitly WORLD_BLOCK (only WORLD_BLOCK events are enqueued).
No Minecraft imports -- unit-testable without game runtime.

### DeferredDamageQueue (damage/)

- `enqueue(event)` -- guards: finite kImpact, dedup, cap
- `flush()` -- called by TrueImpactMod.onServerTickPost()
- `stats()` -- returns QueueStats(pending, totalEnqueued, totalFlushed, lastFlushed)
- `clear()` -- full reset (registered as DiagnosticStateManager flush hook for allOff)

Dedup: same (posX, posY, posZ, victimBlock) per serverTick is enqueued at most once.
Cap: MAX_PENDING = 64. Prevents unbounded growth on rapid impacts.

---

## Enqueue conditions

Events are enqueued in SableImpactCapture.process() when:
1. victimKind == WORLD_BLOCK (contact-point sampling found a block)
2. kImpact is finite (requires 'debug contacts on' for start velocities)
3. kImpact > materialThresholdJ (velocity-derived energy exceeds material threshold)

NOT enqueued when:
- victimKind == ACTIVE_SUBLEVEL (active-vs-active, no world block)
- victimKind == UNKNOWN or source == NO_CALLBACK (block not identified)
- kImpact is NaN (no velocity data; contacts off)
- kImpact <= threshold (below damage threshold)

### kImpact source for enqueue

Two paths depending on what contacts exist in the tick:

**Path A (world-only tick, pairMap empty):**
  Single-body estimate from active body's velocity change:
    kImpact = 0.5 * massActive * |vBefore^2 - vAfter^2|
  vBefore from tickStartVels (requires 'debug contacts on').
  If vBefore unavailable, kImpact = NaN -> no enqueue.

**Path B (active-vs-active + world in same tick):**
  Prefer active-vs-active kImpact (latestRecordMetrics.kineticImpactEnergyJ()),
  fall back to single-body estimate (worldKImpact) if metrics not available.

---

## Status output (Line 11)

```
[TI damage queue] pending=0 totalEnqueued=5 totalFlushed=5
  last=t7432 minecraft:stone (5,63,5) kImpact=240.000 class=STONE src=CONTACT_POINT_SAMPLE
  note=diagnostic-only-no-destroyBlock
```

Colors: YELLOW when pending > 0, AQUA when totalEnqueued > 0 and pending = 0, DARK_GRAY when never triggered.

---

## Server tick safety

The flush hook runs on `ServerTickEvent.Post`, which fires after all world ticks
including SubLevelPhysicsSystem.tick(). Per the Sable danger table:
  - currentlySteppingSystem = null at this point
  - All world mutations are safe (destroyBlock, setBlock allowed)

Phase 1E does NOT exercise this safety window for world mutations. Phase 2A will.

---

## What Phase 1E does NOT do

- No destroyBlock calls
- No crack overlay
- No DamageAccumulator per-block state
- DamageResolver.resolve() still returns DamageEvent.NONE
- The queue flush does not modify any game state
- Flushed events are diagnostic counters only

---

## Upgrade path to Phase 2A

Phase 2A will:
1. Replace `lastFlushed = pending.poll(); totalFlushed++` with a call to DamageResolver
2. DamageResolver will return CRACK / BREAK based on block hardness and accumulated energy
3. The result will be routed to DamageAccumulator (per-block crack state)
4. destroyBlock will be scheduled via ImpactBreakQueue (never inside Rapier step)
5. Remove the Phase 1C/1D/1E gate comments throughout the pipeline
