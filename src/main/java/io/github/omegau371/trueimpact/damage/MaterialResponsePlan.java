package io.github.omegau371.trueimpact.damage;

/**
 * Phase 2E: immutable plan produced by MaterialResponsePlanner for one accumulated block.
 *
 * Pure data record: no side effects, no world access, no Minecraft imports.
 * TrueImpactMod reads this plan on ServerTickEvent.Post and decides what to execute.
 *
 * shouldDropDebris and futureBreakEligible are supplementary flags that may coexist
 * with a primary responseType (e.g. DROP_DEBRIS response also sets both flags).
 */
public record MaterialResponsePlan(
        MaterialResponseType responseType,
        MaterialThresholdProfile.MaterialClass materialClass,
        DamageState damageState,
        double rawImpactJ,
        double effectiveDamageJ,
        double thresholdJ,
        double ratio,
        boolean shouldDropDebris,
        boolean futureBreakEligible,
        String diagnosticNote
) {}
