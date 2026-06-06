package io.github.omegau371.trueimpact.observation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RawContactRecordTest {

    private static final RawContactRecord SAMPLE = new RawContactRecord(
            50L, 3, RawContactRecord.bodyPairKey(5, 0),
            5, 0, 999.5,
            1.0, 0.0, 0.0, -1.0, 0.0, 0.0,
            2.5, 0.0, 0.0, -2.5, 0.0, 0.0
    );

    @Test
    void forceAmountRaw_stored_without_conversion() {
        assertEquals(999.5, SAMPLE.forceAmountRaw(), 1e-9);
    }

    @Test
    void idZero_not_labeled_terrain() {
        // [C2-audit] id=0 or not-in-activeSubLevels → NON_ACTIVE_SUBLEVEL_BODY only
        boolean hasTerrainMethod = Arrays.stream(RawContactRecord.class.getMethods())
                .anyMatch(m -> m.getName().toLowerCase().contains("terrain"));
        assertFalse(hasTerrainMethod, "RawContactRecord must not have any 'terrain' methods [C2-audit]");
    }

    @Test
    void substepIndex_not_included() {
        boolean hasSubstepIndex = Arrays.stream(RawContactRecord.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("substepIndex"));
        assertFalse(hasSubstepIndex, "substepIndex must be absent until T-5 confirms substep attribution [C8]");
    }

    @Test
    void no_setter_methods() {
        assertFalse(Arrays.stream(RawContactRecord.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set")));
    }

    @Test
    void ordered_body_pair_key_is_symmetric() {
        assertEquals(RawContactRecord.bodyPairKey(5, 12), RawContactRecord.bodyPairKey(12, 5));
    }

    @Test
    void field_not_named_force_or_impulse() {
        boolean badName = Arrays.stream(RawContactRecord.class.getRecordComponents())
                .anyMatch(c -> c.getName().equals("force") || c.getName().equals("impulse")
                        || c.getName().equals("forceAmount") || c.getName().equals("impulseAmount"));
        assertFalse(badName, "forceAmountRaw must not be named 'force' or 'impulse' — unit unconfirmed");
    }
}
