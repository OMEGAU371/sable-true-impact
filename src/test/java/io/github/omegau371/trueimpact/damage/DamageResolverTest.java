package io.github.omegau371.trueimpact.damage;

import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1B contract: DamageResolver.resolve() must return NONE for every input.
 * This test will fail as soon as any non-NONE value is returned, enforcing
 * the Phase 1B "no block damage" constraint at the test layer.
 */
class DamageResolverTest {

    private static ImpactRecord impact(ContactType type, double impulseJ) {
        return new ImpactRecord(
                1L,
                (long) 10 << 32 | (20 & 0xFFFFFFFFL),
                10, 20,
                5.0, 10.0,
                1.0 / (1.0 / 5.0 + 1.0 / 10.0),
                1,
                impulseJ,
                0.0,
                0.025,
                type);
    }

    @Test
    void resolve_returns_NONE_for_ACTIVE_IMPACT() {
        ImpactRecord record = impact(ContactType.ACTIVE_IMPACT, 50.0);
        assertEquals(DamageResolver.DamageEvent.NONE, DamageResolver.resolve(record),
                "Phase 1B: resolve must always return NONE");
    }

    @Test
    void resolve_returns_NONE_for_ACTIVE_SUSTAINED() {
        ImpactRecord record = impact(ContactType.ACTIVE_SUSTAINED, 1.0);
        assertEquals(DamageResolver.DamageEvent.NONE, DamageResolver.resolve(record),
                "Phase 1B: resolve must always return NONE");
    }

    @Test
    void resolve_returns_NONE_for_zero_impulse() {
        ImpactRecord record = impact(ContactType.ACTIVE_IMPACT, 0.0);
        assertEquals(DamageResolver.DamageEvent.NONE, DamageResolver.resolve(record));
    }

    @Test
    void resolve_returns_NONE_for_very_high_impulse() {
        ImpactRecord record = impact(ContactType.ACTIVE_IMPACT, 1_000_000.0);
        assertEquals(DamageResolver.DamageEvent.NONE, DamageResolver.resolve(record),
                "Phase 1B: must return NONE regardless of impulse magnitude");
    }

    @Test
    void damage_event_enum_has_only_NONE_in_phase_1b() {
        DamageResolver.DamageEvent[] values = DamageResolver.DamageEvent.values();
        assertEquals(1, values.length,
                "Phase 1B DamageEvent must have exactly one value (NONE)");
        assertEquals(DamageResolver.DamageEvent.NONE, values[0]);
    }
}
