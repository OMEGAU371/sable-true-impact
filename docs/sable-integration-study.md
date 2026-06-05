# Sable Integration Study

Evidence levels: **[SP]** source-proven · **[IT]** inferred from source · **[PC]** physics theory · **[UC]** unconfirmed · **[NE]** needs experiment

---

## 1. Physics Step Lifecycle

### 1.1 Call tree **[SP: SubLevelPhysicsSystem.java:168–224, RapierPhysicsPipeline.java:157–222]**

```
MinecraftServer tick (server thread [IT: C10-pending T-1])
  SubLevelPhysicsSystem.tick(container)
    tickPunchCooldowns(); ticketManager.update(...); updateLastPose()
    pipeline.tick()                               — clears LevelAccelerator cache

    currentlySteppingSystem = this               ← non-null from here…
    tickPipelinePhysics(container)
      pipeline.prePhysicsTicks()                 ← Rapier3D.tick(sceneId, 0.05)
      loop substep 0..N-1  [N=2 default, PhysicsConfigData:13]
        prePhysicsTickBegin(); updateMergedMassData(partial)
        prePhysicsTick(system, handle, dt)
        SableEventPublish.prePhysicsTick()        → ForgeSablePrePhysicsTickEvent  [TI hook A]
        applyQueuedForces(...)
        pipeline.physicsTick(substepDt)           → Rapier3D.step(sceneId, 0.025)
          [native: narrow-phase, collision callbacks fire HERE — thread [IT]]
        container.processSubLevelRemovals()
        updateAllPoses()                          ← logicalPose updated
        SableEventPublish.postPhysicsTick()       → ForgeSablePostPhysicsTickEvent [TI hook B]
        ++currentSubstep
      pipeline.postPhysicsTicks()
        processCollisionEffects()
          Rapier3D.clearCollisions(sceneId)       ← contact array returned HERE
    currentlySteppingSystem = null               ← …null after full return [SP:line 191]
```

### 1.2 Danger window table **[SP + IT]**

| Period | `currentlySteppingSystem` | Safe | Forbidden | Unconfirmed |
|---|---|---|---|---|
| Before `prePhysicsTicks` | **null** | reads, impulses | — | — |
| `prePhysicsTicks` (Rapier3D.tick) | **non-null** | reads | `destroyBlock` | impulse safety |
| `physicsTick` inner (Rapier3D.step, callbacks) | **non-null** | read + queue | **`destroyBlock`; direct impulse** | callback thread identity [T-1] |
| `postPhysicsTick` event per substep | **non-null** | reads | `destroyBlock` | impulse safety [UC] |
| `postPhysicsTicks` / `clearCollisions` | **non-null** | read contact array | `destroyBlock` | impulse safety |
| After `tickPipelinePhysics` return | **null** | all | — | — |

### 1.3 narrow_phase crash hypothesis **[IT: highly credible hypothesis; native cause unconfirmed]**

**Proven call chain:** `destroyBlock` → `handleBlockChange` [SP:319] → `pipeline.handleBlockChange` → `Rapier3D.changeBlock` [native, line 152].

**Hypothesis:** `changeBlock` during `Rapier3D.step` triggers voxel collider rebake → stales narrow-phase contact pair index → `unwrap()` panic in Rust.

**Missing evidence:** Rust source for `changeBlock`; whether rebake is immediate or lazy.

**Operational evidence:** Legacy True Impact consistently crashed without `ImpactBreakQueue`; crashes stopped after deferring all `destroyBlock` to `ServerTickEvent.Post`.

---

## 2. Rigid Body and Mass

### 2.1 Data structure **[SP: multiple files]**

```
ServerSubLevel (implements PhysicsPipelineBody)
  getRuntimeId()           → Rapier3D body ID (countingObjectID++, Rapier3D.java:98)
  getMassTracker()         → MergedMassTracker  (self + KinematicContraption)
  getSelfMassTracker()     → MassTracker        (this sub-level's own blocks)
  logicalPose()            → Pose3d             (world position + rotation)
  latestLinearVelocity     → Vector3d [public]  (difference-derived, see §3.2)
```

### 2.2 Mass unit **[SP: PhysicsBlockPropertyTypes.java:28]**

Default: **1.0 Sable mass unit (kpg)** per solid block.  
"kpg" is community labelling; source code has no such string.  
**[C4-codex] NOT assumed equal to 1 kg. SI mapping unconfirmed.**

### 2.3 MassTracker.build algorithm **[SP: MassTracker.java:85–141]**

Two-pass scan: (1) accumulate mass + weighted CoM, (2) parallel-axis inertia tensor per block.  
Default block self-inertia: `I_self = (mass/6) * Identity` [line 145].

### 2.4 getInverseNormalMass **[SP: MassData.java:33–38]**

`K = 1/m + (r × n̂)ᵀ · I⁻¹ · (r × n̂)` where `r = position − COM`.  
Includes rotational inertia effect. Input coordinates: **PLOT_RELATIVE [UC: §3.2 note 2]**.

---

## 3. Collision Array Format **[SP: RapierPhysicsPipeline.java:219–230]**

15 doubles per contact record:

| Offset | Field | Inferred space | Unit |
|---|---|---|---|
| 0 | idA (int) | — | — |
| 1 | idB (int) | — | — |
| 2 | forceAmountRaw | — | **UNKNOWN [T-3]** |
| 3–5 | localNormalA | BODY_COM_LOCAL [IT] | dimensionless [C4-codex] |
| 6–8 | localNormalB | BODY_COM_LOCAL [IT] | dimensionless |
| 9–11 | localPointA | BODY_COM_LOCAL [IT] | block |
| 12–14 | localPointB | BODY_COM_LOCAL [IT] | block |

**Filter:** `forceAmountRaw > 25.0 * min(massA, massB)` [SP:line 234].

`idA/B not in activeSubLevels` → `NON_ACTIVE_SUBLEVEL_BODY` [C2-codex]; **not proven terrain**.

---

## 4. BlockSubLevelCollisionCallback **[SP: BlockSubLevelCollisionCallback.java, Rapier3D.java:155]**

```java
// Fires during Rapier3D.step() native call:
double[] onCollision(int x, int y, int z, double x1, double y1, double z1, double impactVelocity)
// (x,y,z) coordinate space: UNCONFIRMED [T-2]
// impactVelocity: reported as 0.0 in Sable 1.2.2 [IT from legacy TI; Q-4]
// Returns: [tangentX, tangentY, tangentZ, removeCollision(0|1)]
```

---

## 5. applyForce naming analysis **[SP + IT; native unconfirmed T-4]**

**Upper API contract = impulse [SP]:**  
`PhysicsPipeline.applyImpulse` → `Rapier3D.applyForce` [RapierPhysicsPipeline.java:447].  
`SablePreSolverDamage` uses `J = m * Δv` formula with no `dt` [SablePreSolverDamage.java:118–120].  
`ServerboundPunchSubLevelPacket` uses `strengthScalar ∝ punchCurve(mass)` with no `dt` [line 190–196].

**Native semantics: UNCONFIRMED [T-4].**  
Rapier distinguishes `apply_force` (F·dt) from `apply_impulse` (direct Δp). JNI bridge uses `applyForce` naming; actual native call unknown without Rust source.

---

## 6. T-5 substep attribution **[UC: clearCollisions is post-all-substeps]**

`clearCollisions()` is called once in `postPhysicsTicks()` AFTER all substeps complete.  
Whether the array contains per-substep metadata is UNKNOWN [T-5].  
`RawContactRecord` intentionally omits `substepIndex` until T-5 provides evidence.

---

## 7. Thread identity **[IT: C10-pending T-1]**

`SubLevelPhysicsSystem.tick()` is called from the server tick loop.  
`Rapier3D.step()` blocks on native JNI; callbacks fire from the same thread in standard JNI.  
**Not runtime-verified.** T-1 experiment uses `Thread.currentThread()` comparison.

---

## 8. Pending experiments

| ID | Question | Method |
|---|---|---|
| T-1 | Callback thread = server thread? | `Thread.currentThread()` in callback |
| T-2 | (x,y,z) in callback: which space? | Print raw + candidate transforms |
| T-3 | `forceAmountRaw` dimension? | Known-mass collision; compare to mv, ½mv², etc. |
| T-4 | `applyForce` = force or impulse? | Apply known vector; measure Δv; compute ratio |
| T-5 | clearCollisions substep attribution? | Run substeps=1,2,4; compare record counts |
| T-6 | normalA direction convention? | dot(normalWorld, pointB−pointA); dot(normal, relVel) |
| T-7 | linVel unit? | Compare getLinearVelocity() to pos delta/dt |
