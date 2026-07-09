package io.github.omegau371.trueimpact.damage;

/**
 * Derives MaterialProperties from vanilla block data (hardness, blastResistance, MaterialClass).
 *
 * Philosophy: formulas provide physically motivated defaults for any block. Precise
 * per-block overrides will come later via DisguisedMaterialResolver (§2 of TIPlan).
 *
 * No Minecraft imports — safe for unit testing.
 */
public final class MaterialPropertiesProfile {
    private MaterialPropertiesProfile() {}

    /**
     * Derives full MaterialProperties from vanilla block data.
     *
     * @param hardness    from BlockState.getDestroySpeed(); negative = indestructible
     * @param blastResist from Block.getExplosionResistance()
     * @param mc          material classification
     */
    public static MaterialProperties of(float hardness, float blastResist,
                                        MaterialThresholdProfile.MaterialClass mc) {
        double crackJ = BlockHardnessProfile.crackThresholdJ(hardness, blastResist);
        boolean indestructible = (crackJ == Double.MAX_VALUE);

        return new MaterialProperties(
                toughnessFor(mc, blastResist),
                ductilityFor(mc),
                brittlenessFor(mc),
                elasticityFor(mc),
                frictionFor(mc),
                densityFor(mc, blastResist),
                indestructible ? Double.MAX_VALUE : yieldThresholdFor(mc, crackJ),
                indestructible ? Double.MAX_VALUE : compactionThresholdFor(mc, crackJ)
        );
    }

    // ---- toughness (0–1) --------------------------------------------------------
    // Ability to absorb impact energy without fracturing.
    // Primary driver: MaterialClass. Secondary: blastResist within class.

    static double toughnessFor(MaterialThresholdProfile.MaterialClass mc, float blastResist) {
        double base = switch (mc) {
            case SOFT_SOIL     -> 0.20;
            case BRITTLE       -> 0.05;
            case WOOD          -> 0.35;
            case STONE         -> 0.25;
            case METAL         -> 0.70;
            case HIGH_STRENGTH -> 0.85;
            case GENERIC       -> 0.30;
        };
        // Higher blast resistance within a class → denser/harder variant → marginally tougher
        double bonus = Math.min(Math.max(0, blastResist), 200.0) / 200.0 * 0.10;
        return Math.min(1.0, base + bonus);
    }

    // ---- ductility (0–1) --------------------------------------------------------
    // Capacity for permanent deformation before fracture.

    static double ductilityFor(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 0.45;   // dirt/clay/sand flow and compact
            case BRITTLE       -> 0.01;   // glass, ice — essentially zero ductility
            case WOOD          -> 0.35;   // fibrous, can bend and splinter
            case STONE         -> 0.05;   // minimal; small chip at fracture surface
            case METAL         -> 0.75;   // metals deform significantly before fracture
            case HIGH_STRENGTH -> 0.80;
            case GENERIC       -> 0.15;
        };
    }

    // ---- brittleness (0–1) ------------------------------------------------------
    // Propensity for crystalline/sharp fracture and crack branching.
    // Note: NOT simply (1 - toughness). SOFT_SOIL is low-toughness but also low-brittle
    // (it crumbles/flows rather than snapping cleanly).

    static double brittlenessFor(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 0.10;   // crumbles, not crystalline fracture
            case BRITTLE       -> 0.95;   // glass/ice/terracotta snap cleanly
            case WOOD          -> 0.35;   // fibrous fracture, moderate branching
            case STONE         -> 0.70;   // typical rocky fracture patterns
            case METAL         -> 0.10;   // ductile failure, no crack branching
            case HIGH_STRENGTH -> 0.05;
            case GENERIC       -> 0.50;
        };
    }

    // ---- elasticity / restitution (0–1) -----------------------------------------
    // How much kinetic energy is returned on impact vs absorbed.

    static double elasticityFor(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 0.05;   // soil/sand dead-stops impactors
            case BRITTLE       -> 0.10;   // glass: absorbs then shatters
            case WOOD          -> 0.35;
            case STONE         -> 0.25;
            case METAL         -> 0.55;   // metals spring back noticeably
            case HIGH_STRENGTH -> 0.60;
            case GENERIC       -> 0.25;
        };
    }

    // ---- friction (0–1) ---------------------------------------------------------
    // Kinetic friction coefficient; used for §6 wear damage and tangential force.

    static double frictionFor(MaterialThresholdProfile.MaterialClass mc) {
        return switch (mc) {
            case SOFT_SOIL     -> 0.65;   // granular, high friction
            case BRITTLE       -> 0.35;   // smooth glass/ice
            case WOOD          -> 0.55;
            case STONE         -> 0.65;
            case METAL         -> 0.40;   // smooth machined surface
            case HIGH_STRENGTH -> 0.45;
            case GENERIC       -> 0.50;
        };
    }

    // ---- density (kg/m³) --------------------------------------------------------
    // Mass of a 1 m³ block; used for self-weight stress and belly-flop calculations.

    static double densityFor(MaterialThresholdProfile.MaterialClass mc, float blastResist) {
        double base = switch (mc) {
            case SOFT_SOIL     -> 1_600;
            case BRITTLE       -> 2_500;  // soda-lime glass
            case WOOD          ->   700;
            case STONE         -> 2_400;
            case METAL         -> 7_800;  // iron; gold/lead need per-block overrides
            case HIGH_STRENGTH -> 9_500;
            case GENERIC       -> 1_500;
        };
        // Small intra-class variation: denser/more resistant variants weigh slightly more
        double scale = 1.0 + Math.min(Math.max(0, blastResist), 100.0) / 1000.0;
        return base * scale;
    }

    // ---- yield threshold (J) ----------------------------------------------------
    // Energy at which plastic deformation onset occurs (< crackThresholdJ for ductile
    // materials; ≈ crackThresholdJ for brittle ones — they fracture before yielding).

    static double yieldThresholdFor(MaterialThresholdProfile.MaterialClass mc,
                                    double crackThresholdJ) {
        double fraction = switch (mc) {
            case SOFT_SOIL     -> 0.40;   // flows/compacts at low energy
            case BRITTLE       -> 0.95;   // fractures almost immediately, barely yields
            case WOOD          -> 0.60;
            case STONE         -> 0.85;
            case METAL         -> 0.40;   // metals yield well before fracture
            case HIGH_STRENGTH -> 0.35;
            case GENERIC       -> 0.70;
        };
        return crackThresholdJ * fraction;
    }

    // ---- compaction threshold (J) -----------------------------------------------
    // Only meaningful for SOFT_SOIL: energy triggering block-state compaction.
    // All other materials return MAX_VALUE (compaction path never activates).

    static double compactionThresholdFor(MaterialThresholdProfile.MaterialClass mc,
                                         double crackThresholdJ) {
        if (mc != MaterialThresholdProfile.MaterialClass.SOFT_SOIL) return Double.MAX_VALUE;
        return crackThresholdJ * 0.30;   // compaction triggers before cracking
    }
}
