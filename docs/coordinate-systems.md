# Coordinate Systems

Evidence levels: **[SP]** source-proven · **[IT]** inferred · **[UC]** unconfirmed · **[NE]** needs experiment

---

## 1. Five Coordinate Spaces

| Name | Definition | Unit | Notes |
|---|---|---|---|
| **WORLD** | Global Minecraft coordinates | block (float or int) | `BlockPos`, `Vec3`, world-side ops |
| **BODY_COM_LOCAL** | Rapier rigid body local frame, origin at center of mass, aligned with body orientation | block [IT] | `clearCollisions` localPoint/localNormal |
| **PLOT_RELATIVE** | Sub-level's own 0-origin grid, reference point at `logicalPose().rotationPoint()` | block | `logicalPose` COM offset, `toPlotPosition()` output |
| **EMBEDDED_LEVEL** | Absolute block address in embedded chunk (`plotRelative + plotCenter`) | block (int BlockPos) | ~±4×10⁷; never call `level.getBlockState` without `hasChunkAt` guard |
| **BLOCK_INDEX** | Per-chunk section `[0,16)³` offset | block (int) | Collider array, `SectionPos` |

---

## 2. Key Field/Method Space Table

| Field / Method | Class:line | Output space | Input space | Status |
|---|---|---|---|---|
| `logicalPose().position()` | SubLevel.java:84 | **WORLD** | — | **[SP]** `updatePose` line 245 |
| `logicalPose().orientation()` | SubLevel.java | world rotation quaternion | — | **[SP]** |
| `logicalPose().rotationPoint()` | RapierPhysicsPipeline.java:288 init | **PLOT_RELATIVE** | — | **[IT]** modified by MergedMassTracker.java:104 |
| `MassTracker.getCenterOfMass()` | MassTracker.java:200 | **PLOT_RELATIVE** | — | **[SP]** build() iterates blockPos |
| `clearCollisions[9..11]` localPointA | RapierPhysicsPipeline.java:229 | **BODY_COM_LOCAL** [IT] | — | **[UC: T-3]** native origin unconfirmed |
| `clearCollisions[3..5]` localNormalA | line 227 | **BODY_COM_LOCAL** direction [IT] | — | **[UC: T-6]** direction convention unconfirmed |
| `getInverseNormalMass(pos, dir)` | MassData.java:33 | scalar | PLOT_RELATIVE [IT] | **[UC]** |
| `pipeline.applyImpulse(body, pos, vec)` | RapierPhysicsPipeline.java:447 | — | PLOT_RELATIVE [IT] | **[IT]** line 448: `pos - COM`, COM is PLOT_RELATIVE |
| `pipeline.getLinearVelocity(body, dest)` | line 463 | WORLD [IT] | — | **[UC: T-7]** unit unconfirmed |
| `latestLinearVelocity` | SubLevelPhysicsSystem.java:247,255 | WORLD difference-approximation | — | **[SP]** = `(pos_now − pos_last) × 20`; NOT Rapier velocity |
| `globalBoundsTransform` | SubLevel.java:41 | WORLD from PLOT_RELATIVE | PLOT_RELATIVE | **[UC]** update timing not in scd_src |
| callback `(x,y,z)` | BlockSubLevelCollisionCallback.java:21 | ??? | — | **[UC: T-2]** |

---

## 3. Proven Transforms (source code)

### WORLD → PLOT_RELATIVE **[SP: SablePreSolverDamage.java:140–146]**
```java
dest.set(globalPoint).sub(pose.position());    // world − worldCOM
pose.orientation().transformInverse(dest);      // inverse rotation
dest.add(pose.rotationPoint());                 // + PLOT_RELATIVE COM offset
```
Formula: `plotRel = Q⁻¹ · (world − position) + rotationPoint`

### BODY_COM_LOCAL → WORLD **[SP: RapierPhysicsPipeline.java:254–260]**
```java
pose.orientation().transform(globalPoint).add(pose.position());
```
Formula: `world = Q · bodyComLocal + position`

### PLOT_RELATIVE → EMBEDDED_LEVEL **[IT]**
`embedded = plotRelative + plotCenter` where `plotCenter = LevelPlot.getCenterBlock()`  
**[UC]** whether plotCenter is fixed or mutable — not found in scd_src.

---

## 4. Forbidden operations

- ❌ `bodyComLocal + plotCenter` (skips rotation, wrong origin)
- ❌ `bodyComLocal + rotationPoint` (only valid when orientation ≈ identity)
- ❌ `latestLinearVelocity` as contact-point velocity (difference-approximation, no ω×r)
- ❌ `level.getBlockState(embeddedPos)` without `level.hasChunkAt(pos)` guard (forces chunkgen at ~4×10⁷, causes aquifer AIOOBE)

---

## 5. Unconfirmed / Pending

- **T-2** — callback `(x,y,z)` space
- **T-7** — `getLinearVelocity()` unit
- **Q-1** — `globalBoundsTransform` update timing
- **Q-2** — `plotCenter` immutability
- **Q-3** — `logicalPose().rotationPoint()` strict equality to current PLOT_RELATIVE COM
