package io.github.omegau371.trueimpact.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DamageState.of() boundary classification.
 * No Minecraft runtime required.
 */
class DamageStateTest {

    @Test
    void ratio_zero_is_INTACT() {
        assertEquals(DamageState.INTACT, DamageState.of(0.0));
    }

    @Test
    void ratio_0_24_is_INTACT() {
        assertEquals(DamageState.INTACT, DamageState.of(0.24));
    }

    @Test
    void ratio_0_25_is_BRUISED() {
        assertEquals(DamageState.BRUISED, DamageState.of(0.25));
    }

    @Test
    void ratio_0_59_is_BRUISED() {
        assertEquals(DamageState.BRUISED, DamageState.of(0.59));
    }

    @Test
    void ratio_0_60_is_CRACKED() {
        assertEquals(DamageState.CRACKED, DamageState.of(0.60));
    }

    @Test
    void ratio_0_99_is_CRACKED() {
        assertEquals(DamageState.CRACKED, DamageState.of(0.99));
    }

    @Test
    void ratio_1_00_is_CRITICAL() {
        assertEquals(DamageState.CRITICAL, DamageState.of(1.00));
    }

    @Test
    void ratio_2_0_is_CRITICAL() {
        assertEquals(DamageState.CRITICAL, DamageState.of(2.0));
    }

    @Test
    void NaN_ratio_is_INTACT() {
        assertEquals(DamageState.INTACT, DamageState.of(Double.NaN),
                "NaN ratio should fall back to INTACT");
    }

    @Test
    void negative_infinity_is_INTACT() {
        assertEquals(DamageState.INTACT, DamageState.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    void positive_infinity_is_INTACT() {
        // Positive infinity is not finite; DamageState.of() returns INTACT as safe fallback.
        assertEquals(DamageState.INTACT, DamageState.of(Double.POSITIVE_INFINITY));
    }
}
