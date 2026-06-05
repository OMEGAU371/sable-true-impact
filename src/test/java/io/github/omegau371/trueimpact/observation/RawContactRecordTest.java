package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RawContactRecordTest {

    private static final RawContactRecord SAMPLE = new RawContactRecord(
            50L, 3, RawContactRecord.bodyPairKey(5, 0),
            5, 0, 999.5,
            1.0, 0.0, 0.0,
            -1.0, 0.0, 0.0,
            2.5, 0.0, 0.0,
            -2.5, 0.0, 0.0
    );

    @Test
    void forceAmountRaw_is_stored_without_conversion() {
        // [C3-codex] forceAmountRaw must be stored as-is; no unit interpretation
        assertEquals(999.5, SAMPLE.forceAmountRaw(), 1e-9,
                "forceAmountRaw must be the raw Rapier value with no conversion");
    }

    @Test
    void idZero_is_not_interpreted_as_terrain() {
        // [C2-codex] id not in activeSubLevels → NON_ACTIVE_SUBLEVEL_BODY, not proven terrain
        assertEquals(0, SAMPLE.idB());
        // Verify there is no isTerrain method
        boolean hasTerrain = Arrays.stream(RawContactRecord.class.getMethods())
                .anyMatch(m -> m.getName().toLowerCase().contains("terrain"));
        assertFalse(hasTerrain,
                "RawContactRecord must have no 'terrain' interpretation methods — [C2-codex]");
    }

    @Test
    void substepIndex_is_not_included() {
        // [C8-codex][T-5 pending] substepIndex intentionally omitted
        boolean hasSubstepIndex = Arrays.stream(RawContactRecord.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("substepIndex"));
        assertFalse(hasSubstepIndex,
                "substepIndex must NOT be in RawContactRecord until T-5 provides evidence");
    }

    @Test
    void record_has_no_setter_methods() {
        boolean hasSetters = Arrays.stream(RawContactRecord.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertFalse(hasSetters, "RawContactRecord is a record and must have no setters");
    }

    @Test
    void orderedBodyPairKey_is_symmetric() {
        long k1 = RawContactRecord.bodyPairKey(5, 12);
        long k2 = RawContactRecord.bodyPairKey(12, 5);
        assertEquals(k1, k2, "bodyPairKey(a,b) must equal bodyPairKey(b,a)");
    }

    @Test
    void forceAmountRaw_field_is_not_named_force_or_impulse() {
        boolean badName = Arrays.stream(RawContactRecord.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("force") || c.getName().equals("impulse")
                        || c.getName().equals("forceAmount") || c.getName().equals("impulseAmount"));
        assertFalse(badName,
                "forceAmountRaw must be named 'forceAmountRaw', not 'force' or 'impulse' — unit unconfirmed");
    }
}
