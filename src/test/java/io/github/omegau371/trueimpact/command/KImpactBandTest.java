package io.github.omegau371.trueimpact.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for KImpactBand.of() -- the kineticImpactEnergyJ calibration band classifier.
 *
 * Bands are TEMPORARY CALIBRATION LABELS for manual in-game testing only.
 * They are NOT material thresholds; they must NOT enter any damage formula.
 *
 * Band boundaries (inclusive lower, exclusive upper):
 *   NONE   = NaN or non-finite
 *   TOUCH  = [0, 1)
 *   LIGHT  = [1, 5)
 *   MEDIUM = [5, 20)
 *   HEAVY  = [20, 50)
 *   SEVERE = [50, inf)
 */
class KImpactBandTest {

    @Test
    void nan_produces_NONE() {
        assertEquals("NONE", KImpactBand.of(Double.NaN));
    }

    @Test
    void positive_infinity_produces_NONE() {
        // Infinity is not a valid energy measurement; treated same as NaN.
        // kineticImpactEnergyJ = abs(kBefore-kAfter) cannot be infinite in practice.
        assertEquals("NONE", KImpactBand.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void negative_infinity_produces_NONE() {
        assertEquals("NONE", KImpactBand.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    void zero_produces_TOUCH() {
        assertEquals("TOUCH", KImpactBand.of(0.0));
    }

    @Test
    void point_five_produces_TOUCH() {
        assertEquals("TOUCH", KImpactBand.of(0.5));
    }

    @Test
    void just_below_1_produces_TOUCH() {
        assertEquals("TOUCH", KImpactBand.of(0.9999));
    }

    @Test
    void exactly_1_produces_LIGHT() {
        assertEquals("LIGHT", KImpactBand.of(1.0));
    }

    @Test
    void three_produces_LIGHT() {
        assertEquals("LIGHT", KImpactBand.of(3.0));
    }

    @Test
    void just_below_5_produces_LIGHT() {
        assertEquals("LIGHT", KImpactBand.of(4.9999));
    }

    @Test
    void exactly_5_produces_MEDIUM() {
        assertEquals("MEDIUM", KImpactBand.of(5.0));
    }

    @Test
    void ten_produces_MEDIUM() {
        assertEquals("MEDIUM", KImpactBand.of(10.0));
    }

    @Test
    void just_below_20_produces_MEDIUM() {
        assertEquals("MEDIUM", KImpactBand.of(19.9999));
    }

    @Test
    void exactly_20_produces_HEAVY() {
        assertEquals("HEAVY", KImpactBand.of(20.0));
    }

    @Test
    void thirty_produces_HEAVY() {
        assertEquals("HEAVY", KImpactBand.of(30.0));
    }

    @Test
    void just_below_50_produces_HEAVY() {
        assertEquals("HEAVY", KImpactBand.of(49.9999));
    }

    @Test
    void exactly_50_produces_SEVERE() {
        assertEquals("SEVERE", KImpactBand.of(50.0));
    }

    @Test
    void eighty_produces_SEVERE() {
        assertEquals("SEVERE", KImpactBand.of(80.0));
    }
}
