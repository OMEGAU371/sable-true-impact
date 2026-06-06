package io.github.omegau371.trueimpact.sable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputVectorGuardTest {

    @Test
    void rejects_nan_x() {
        assertFalse(InputVectorGuard.isFiniteInput(Double.NaN, 0, 0),
                "NaN in x must be rejected");
    }

    @Test
    void rejects_nan_y() {
        assertFalse(InputVectorGuard.isFiniteInput(0, Double.NaN, 0),
                "NaN in y must be rejected");
    }

    @Test
    void rejects_nan_z() {
        assertFalse(InputVectorGuard.isFiniteInput(0, 0, Double.NaN),
                "NaN in z must be rejected");
    }

    @Test
    void rejects_positive_infinity_x() {
        assertFalse(InputVectorGuard.isFiniteInput(Double.POSITIVE_INFINITY, 0, 0),
                "+Infinity in x must be rejected");
    }

    @Test
    void rejects_negative_infinity_y() {
        assertFalse(InputVectorGuard.isFiniteInput(0, Double.NEGATIVE_INFINITY, 0),
                "-Infinity in y must be rejected");
    }

    @Test
    void rejects_infinity_z() {
        assertFalse(InputVectorGuard.isFiniteInput(0, 0, Double.POSITIVE_INFINITY),
                "+Infinity in z must be rejected");
    }

    @Test
    void accepts_normal_vector() {
        assertTrue(InputVectorGuard.isFiniteInput(1.0, 2.0, -3.0),
                "Normal finite vector must be accepted");
    }

    @Test
    void accepts_zero_vector() {
        assertTrue(InputVectorGuard.isFiniteInput(0, 0, 0),
                "Zero vector must be accepted (magnitude check does not reject it)");
    }

    @Test
    void accepts_max_practical_magnitude() {
        assertTrue(InputVectorGuard.isFiniteInput(200.0, 0, 0),
                "Practical-maximum input (200, 0, 0) must be accepted");
    }
}
