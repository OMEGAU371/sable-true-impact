package io.github.omegau371.trueimpact.diagnostic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class T4ExperimentTest {

    @BeforeEach
    void setUp() { T4ApplyForceExperiment.clearAll(); }

    @AfterEach
    void tearDown() { T4ApplyForceExperiment.clearAll(); }

    @Test
    void pending_is_per_level_and_runtimeId() {
        String key1 = T4ApplyForceExperiment.key("minecraft:overworld", 42);
        String key2 = T4ApplyForceExperiment.key("minecraft:overworld", 99);
        String key3 = T4ApplyForceExperiment.key("minecraft:the_nether", 42);

        T4ApplyForceExperiment.pendingByKey.put(key1, pending(42, 1, 0, 0));
        T4ApplyForceExperiment.pendingByKey.put(key2, pending(99, 0, 1, 0));
        T4ApplyForceExperiment.pendingByKey.put(key3, pending(42, 0, 0, 1));

        assertEquals(3, T4ApplyForceExperiment.pendingByKey.size(),
                "Three different keys must coexist without overwriting each other");
    }

    @Test
    void two_experiments_for_same_id_same_level_have_same_key() {
        // Key uniqueness: same level + same id → same key
        String k1 = T4ApplyForceExperiment.key("minecraft:overworld", 42);
        String k2 = T4ApplyForceExperiment.key("minecraft:overworld", 42);
        assertEquals(k1, k2, "Same level + same runtimeId must produce the same key");
    }

    @Test
    void different_levels_same_id_have_different_keys() {
        String k1 = T4ApplyForceExperiment.key("minecraft:overworld", 42);
        String k2 = T4ApplyForceExperiment.key("minecraft:the_nether", 42);
        assertNotEquals(k1, k2, "Same runtimeId in different dimensions must NOT collide");
    }

    @Test
    void input_magnitude_ceiling_is_conservative() {
        assertTrue(T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE > 0,
                "Hard ceiling must be positive");
        assertTrue(T4ApplyForceExperiment.MAX_INPUT_MAGNITUDE <= 500.0,
                "Hard ceiling must be conservative (≤ 500)");
    }

    @Test
    void pre_velocity_threshold_is_positive() {
        assertTrue(T4ApplyForceExperiment.MAX_PRE_VELOCITY_THRESHOLD > 0,
                "Pre-velocity threshold must be > 0");
    }

    @Test
    void clearAll_removes_all_pending() {
        T4ApplyForceExperiment.pendingByKey.put(
                T4ApplyForceExperiment.key("dim:a", 1), pending(1, 1, 0, 0));
        T4ApplyForceExperiment.pendingByKey.put(
                T4ApplyForceExperiment.key("dim:b", 2), pending(2, 0, 1, 0));
        T4ApplyForceExperiment.clearAll();
        assertTrue(T4ApplyForceExperiment.pendingByKey.isEmpty(),
                "clearAll must remove all pending entries");
    }

    private static T4ApplyForceExperiment.Pending pending(int id, double fx, double fy, double fz) {
        return new T4ApplyForceExperiment.Pending(
                "minecraft:overworld", id, fx, fy, fz,
                0, 0, 0, 1.0, 0.025, 0L);
    }
}
