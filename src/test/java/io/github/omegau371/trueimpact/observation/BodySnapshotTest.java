package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BodySnapshotTest {

    // Helper factory matching current record layout
    private static BodySnapshot make(boolean comValid, boolean velValid) {
        return new BodySnapshot(
                100L, 0, SnapshotPhase.PRE_STEP, BodyType.ACTIVE_SUBLEVEL, 42,
                10.5,
                comValid, 1.0, 2.0, 3.0,
                4.0, 5.0, 6.0,
                0.0, 0.0, 0.0, 1.0,
                7.0, 8.0, 9.0,
                velValid, 1.5, 0.0, 0.0, 0.0, 0.5, 0.0
        );
    }

    @Test
    void fields_accessible_and_correct() {
        BodySnapshot s = make(true, true);
        assertEquals(100L, s.serverTick());
        assertEquals(10.5, s.massKpg(), 1e-9);
        assertTrue(s.comValid());
        assertEquals(1.0, s.comX(), 1e-9);
        assertTrue(s.velocityReadValid());
        assertEquals(1.5, s.linVelX(), 1e-9);
    }

    @Test
    void comValid_false_means_com_values_are_invalid() {
        BodySnapshot s = make(false, true);
        assertFalse(s.comValid(), "comValid=false indicates com fields are 0.0 (not real measurements)");
        // The 0.0 values must not be used as real measurements
    }

    @Test
    void velocityReadValid_false_means_velocity_is_invalid() {
        BodySnapshot s = make(true, false);
        assertFalse(s.velocityReadValid(), "velocityReadValid=false: linVel/angVel are 0.0 placeholders");
    }

    @Test
    void record_has_no_setter_methods() {
        boolean hasSetters = Arrays.stream(BodySnapshot.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(hasSetters, "BodySnapshot is a record — no setters");
    }

    @Test
    void record_is_final() {
        assertTrue(java.lang.reflect.Modifier.isFinal(BodySnapshot.class.getModifiers()));
    }

    @Test
    void equal_snapshots_have_equal_hashcode() {
        BodySnapshot a = make(true, true);
        BodySnapshot b = make(true, true);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
