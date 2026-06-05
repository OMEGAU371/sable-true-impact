package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BodySnapshotTest {

    private static final BodySnapshot SAMPLE = new BodySnapshot(
            100L, 0, SnapshotPhase.PRE_STEP, BodyType.ACTIVE_SUBLEVEL, 42,
            10.5, 1.0, 2.0, 3.0,
            4.0, 5.0, 6.0,
            0.0, 0.0, 0.0, 1.0,
            7.0, 8.0, 9.0,
            1.5, 0.0, 0.0,
            0.0, 0.5, 0.0
    );

    @Test
    void fields_are_accessible_and_correct() {
        assertEquals(100L, SAMPLE.serverTick());
        assertEquals(0, SAMPLE.substepIndex());
        assertEquals(SnapshotPhase.PRE_STEP, SAMPLE.phase());
        assertEquals(BodyType.ACTIVE_SUBLEVEL, SAMPLE.bodyType());
        assertEquals(42, SAMPLE.runtimeId());
        assertEquals(10.5, SAMPLE.massKpg(), 1e-9);
        assertEquals(4.0, SAMPLE.posX(), 1e-9);
        assertEquals(1.5, SAMPLE.linVelX(), 1e-9);
    }

    @Test
    void record_has_no_setter_methods() {
        boolean hasSetters = Arrays.stream(BodySnapshot.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(hasSetters, "BodySnapshot is a record and must have no setter methods");
    }

    @Test
    void record_is_final() {
        assertTrue(java.lang.reflect.Modifier.isFinal(BodySnapshot.class.getModifiers()),
                "BodySnapshot must be final (all records are)");
    }

    @Test
    void two_equal_snapshots_have_same_hashcode() {
        BodySnapshot copy = new BodySnapshot(
                100L, 0, SnapshotPhase.PRE_STEP, BodyType.ACTIVE_SUBLEVEL, 42,
                10.5, 1.0, 2.0, 3.0,
                4.0, 5.0, 6.0,
                0.0, 0.0, 0.0, 1.0,
                7.0, 8.0, 9.0,
                1.5, 0.0, 0.0,
                0.0, 0.5, 0.0
        );
        assertEquals(SAMPLE, copy);
        assertEquals(SAMPLE.hashCode(), copy.hashCode());
    }
}
