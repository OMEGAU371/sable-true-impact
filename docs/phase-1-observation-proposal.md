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
**MANUALLY PASSED — 2026-06-06**

Tested in-game with Sable loaded, M=1 kpg isolated free-floating structure.
(Log source consistent with integrated server / live game; dedicated server not separately confirmed.)

Two separate test runs:

**Run 1 — at-pose-pos explosion (pre-0.2.1):**

| variant | input | outcome |
|---|---|---|
| `at-pose-pos` | (100, 0, 0) | delta-v magnitude ≈2.15×10⁹, angular speed ≈3.61×10⁹ — server 21 s behind, Sable emergency removal |

**Run 2 — safety verification (0.2.1):**

| variant | input | ratio = inputMagnitude/(M·Δv) | angVelAfter | conclusion |
|---|---|---|---|---|
| `linear-only` | (10, 0, 0) | 1.0166 | ≈ 0.0003 | Direct impulse confirmed |
| `com-current` | (10, 0, 0) | 1.0165 | ≈ 0.0011 | COM application safe |

**Impulse semantics confirmed.** ratio ≈ 1.0, no dt factor.
**COM application is safe** for future force-transfer foundation.
**at-pose-pos permanently removed** — logicalPose().position() yields astronomical lever arm in plot-space.

Safety rule added to code and docs: |application_point − COM| ≪ 1e3 or reject.

Implementation: `SableT4Command.java`, `T4ApplyForceExperiment.java`, `SableEventBridge.java`.
Available commands: `t4 bodies`, `t4 inspect <id>`, `t4 apply <id> fx fy fz`, `t4 apply-linear <id> fx fy fz`.

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
