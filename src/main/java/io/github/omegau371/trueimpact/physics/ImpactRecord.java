package io.github.omegau371.trueimpact.physics;

/**
 * Immutable data contract for one active-vs-active contact event in a single physics tick.
 *
 * Scope constraint: ImpactRecord represents ONLY active sub-level vs active sub-level pairs.
 * World-vs-active and unknown-body contacts are discarded by the capture layer before
 * this record is constructed. The type system enforces this: ContactType has no
 * WORLD_VS_ACTIVE or UNKNOWN values.
 *
 * Field calibration status:
 *
 *   totalImpulseJ         T-3 CONFIRMED (2026-06-09). Canonical damage input.
 *                         = sumForceAmountRaw * substepDt
 *                         Use this as the primary formula input.
 *
 *   impulseAlongNormalJ   T-6 UNCONFIRMED. Normal direction convention not verified.
 *                         abs() is applied at construction to absorb sign ambiguity.
 *                         Do NOT use as primary formula input until T-6 passes.
 *                         Retained for directional diagnostic analysis only.
 *
 *   effectiveMassKpg      Derived from confirmed T-3 snapshot masses. Ready for use.
 *
 *   contactCount          [DIAGNOSTIC METADATA -- UNCONFIRMED as contact area proxy]
 *                         = number of Rapier contact records for this pair in this tick.
 *                         Whether contactCount correlates with contact surface area has NOT
 *                         been experimentally verified. A dedicated experiment is required
 *                         before contactCount may appear in any damage root formula.
 *                         It is included here so the capture layer does not have to re-derive
 *                         it later, not because it is ready for formula use.
 *
 *   contactType           Diagnostic filter tag only.
 *                         Used by the resolver for a single early-return guard.
 *                         Must NOT appear in formula branches or as a formula parameter.
 *
 *   substepDt             Reference value. Already baked into totalImpulseJ; do not
 *                         multiply again.
 */
public record ImpactRecord(
        long        serverTick,
        long        bodyPairKey,         // min(idA,idB)<<32 | max(idA,idB)
        int         idA,
        int         idB,
        double      massAKpg,
        double      massBKpg,
        double      effectiveMassKpg,    // 1/(1/mA + 1/mB); NaN if unavailable
        int         contactCount,        // DIAGNOSTIC METADATA -- UNCONFIRMED as area proxy
        double      totalImpulseJ,       // sumForce * substepDt [CANONICAL T-3]
        double      impulseAlongNormalJ, // dv reconstruction [T-6 UNCONFIRMED; abs applied]
        double      substepDt,           // reference only; already baked into totalImpulseJ
        ContactType contactType
) {}
