# Phase 1A — Observation Layer Design

## Goal

Minimum read-only observation layer to support the seven diagnostic experiments.
**No damage, cracks, destruction, collision response, or automatic force application.**

---

## Data Structures

### BodySnapshot (immutable record)

| Field | Unit | Space | Source | When sampled |
|---|---|---|---|---|
| serverTick | tick | — | level.getGameTime() | PRE/POST event |
| substepIndex | 0-based int | — | round(partial × N) − 1 | PRE/POST event |
| phase | PRE_STEP / POST_STEP | — | event type | PRE/POST event |
| bodyType | ACTIVE_SUBLEVEL / NON_ACTIVE | — | in activeSubLevels? [C2] | PRE/POST |
| runtimeId | int | — | getRuntimeId() | PRE/POST |
| massKpg | kpg | — | getMassTracker().getMass() | PRE/POST |
| comX/Y/Z | block | PLOT_RELATIVE | getMassTracker().getCenterOfMass() | PRE/POST |
| posX/Y/Z | block | WORLD | logicalPose().position() | PRE/POST |
| oriX/Y/Z/W | — | WORLD rotation | logicalPose().orientation() | PRE/POST |
| rotPtX/Y/Z | block | PLOT_RELATIVE [IT] | logicalPose().rotationPoint() | PRE/POST |
| linVelX/Y/Z | **UNKNOWN [T-7]** | WORLD [IT] | getLinearVelocity() | PRE/POST |
| angVelX/Y/Z | **UNKNOWN [T-7]** | WORLD [IT] | getAngularVelocity() | PRE/POST |

### RawContactRecord (immutable record)

| Field | Notes |
|---|---|
| serverTick | level.getGameTime() at clearCollisions() call |
| rawRecordIndex | index i in clearCollisions array |
| orderedBodyPairKey | min(idA,idB)<<32 \| max(idA,idB) |
| idA, idB | raw Rapier body IDs; id not in activeSubLevels → NON_ACTIVE [C2] |
| forceAmountRaw | NOT named force/impulse; unit UNKNOWN [T-3] |
| localNormal A/B | BODY_COM_LOCAL [IT]; direction convention UNKNOWN [T-6] |
| localPoint A/B | BODY_COM_LOCAL [IT]; unit: block [IT] |
| **substepIndex** | **INTENTIONALLY ABSENT** until T-5 provides evidence |

---

## Contact Identity **[C8-codex]**

- Primary key: `(serverTick, rawRecordIndex, orderedBodyPairKey)`
- No cross-tick trackId provided
- No `round(point, 0.5)` or BlockPos used as identity
- Any cross-tick matching is candidate-only, not physical fact

---

## Logging Rate Limits **[C9-codex]**

All limits are hard — no exceptions for "high-energy" events.

| Limit | Value |
|---|---|
| `MAX_LOGS_PER_TICK` | 20 |
| `MAX_LOGS_PER_SECOND` | 100 |

Dropped events increment `droppedThisTick` counter; reported at end of tick if > 0.

---

## Experiment Implementations

### T-1: Callback thread identity
- Fires inside `DiagnosticCallbackWrapperMixin` on each `sable$onCollision` call
- Logs: `callbackThread.getName()`, `callbackThread.getId()`, server thread comparison
- Expected: same thread; **not yet verified**

### T-2: Callback coordinate identification
- Same wrapper; logs raw `(pos.x, pos.y, pos.z)` and `(hitPos.x, hitPos.y, hitPos.z)`
- Manual comparison against known sub-level's WORLD/PLOT_RELATIVE/EMBEDDED positions

### T-3: forceAmountRaw dimension
- Fires in `ContactLogger.onClearCollisions()`
- Logs `idA`, `idB`, `forceAmountRaw` per contact record
- Manual: correlate with body masses and pre/post velocities from BodySnapshots

### T-4: applyForce semantics (admin-only, manual execution only)
Command: `/trueimpact experiment t4 apply <runtimeId> <fx> <fy> <fz>`
- Requires op level 4; hard input ceiling MAX_INPUT_MAGNITUDE=200
- Rejects if pre-velocity |v| > MAX_PRE_VELOCITY_THRESHOLD=2.0
- Issues ISOLATION WARNING (cannot programmatically confirm no contacts)
- Pending is per-level+runtimeId (ConcurrentHashMap); no global volatile override
- Independent of LOG_BODY_SNAPSHOTS — onPostStep() always checks pending map
- Records: M, inputRaw, dt, vBefore, vAfter, Δv, **deltaVAlongInput**, **measuredMomentumAlongInput**,
  input/(M·deltaVAlongInput), gravityErrorEst
- [NOT AUTO-CONCLUDED] ratio annotated with gravity/contact error sources; human analysis required
- **Implementation status: IMPLEMENTED, not yet executed — awaits project-manager manual run**

### T-5: clearCollisions substep attribution
- `ContactLogger` logs contact count and `substepCount` per `clearCollisions` call
- Manual: run same scenario with substeps=1, 2, 4; compare record counts
- **RawContactRecord keeps no substepIndex until this experiment concludes**

### T-6: Normal direction convention
- `ContactLogger` logs raw `localNormalA/B` values
- Manual analysis: convert to WORLD, compute `dot(normalWorld, pointB−pointA)` and `dot(normalWorld, relativeContactVelocity)`
- No automatic reversal or response based on result

### T-7: Velocity units
- `SableEventBridge` compares `getLinearVelocity()` magnitude vs `|Δpos|/dt` per substep
- Logged as ratio; interpretation requires manual analysis of multiple data points

---

## Architecture Constraints

- `observation/` and `diagnostic/` must NOT depend on `damage/` (enforced by ArchUnit)
- Production logic must NOT read `DiagnosticConfig.*` to influence game behavior
- All diagnostics default `false`; no logging unless explicitly enabled via command
- `SableEventBridge`, `SableBodyReader`, `SableT4Command` in `sable/` package: only loaded when Sable present
- Mixins apply only to Sable internal classes; if Sable absent, mixins are harmlessly skipped
