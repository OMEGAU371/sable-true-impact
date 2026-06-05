package io.github.omegau371.trueimpact.unit;

import io.github.omegau371.trueimpact.TrueImpactVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrueImpactVersionTest {

    @Test
    void modId_is_true_impact() {
        assertEquals("true_impact", TrueImpactVersion.MOD_ID);
    }

    @Test
    void version_starts_with_zero_for_rewrite() {
        assertTrue(TrueImpactVersion.VERSION.startsWith("0."),
                "Rewrite version line must start with 0.x");
    }

    @Test
    void version_contains_phase_marker() {
        assertTrue(
                TrueImpactVersion.VERSION.contains("foundation")
                || TrueImpactVersion.VERSION.contains("sable-study")
                || TrueImpactVersion.VERSION.contains("alpha")
                || TrueImpactVersion.VERSION.contains("beta"),
                "Version must contain a phase marker (foundation, sable-study, alpha, beta, ...)");
    }

    @Test
    void modId_matches_neoforge_pattern() {
        assertTrue(TrueImpactVersion.MOD_ID.matches("[a-z][a-z0-9_]{1,63}"),
                "mod_id must match NeoForge lowercase pattern");
    }
}
