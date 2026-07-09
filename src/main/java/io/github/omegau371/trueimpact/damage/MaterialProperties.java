package io.github.omegau371.trueimpact.damage;

/**
 * Comprehensive physical material properties for a block.
 *
 * Obtained via MaterialPropertiesProfile.of(hardness, blastResist, materialClass).
 * All ratio fields are in [0.0, 1.0]; threshold fields are in Joules.
 *
 * This is the shared data contract for all future TI systems that need material
 * behaviour beyond crack/break thresholds (D-1 plasticity, §3 stress, §6 friction, etc.).
 */
public record MaterialProperties(

        // -- fracture character ---------------------------------------------------

        /** 0 = shatters/crumbles immediately; 1 = absorbs energy and deforms. */
        double toughness,

        /** 0 = no permanent deformation; 1 = flows freely (clay, metal). */
        double ductility,

        /** 0 = ductile failure mode; 1 = pure brittle fracture (glass, stone). */
        double brittleness,

        // -- dynamic response -----------------------------------------------------

        /** Coefficient of restitution: 0 = dead stop / absorbs; 1 = perfect bounce. */
        double elasticity,

        /** Kinetic friction coefficient 0–1 (used for §6 wear and future friction). */
        double friction,

        // -- mass -----------------------------------------------------------------

        /** Mass of a full 1 m³ block in kg (used for self-weight and belly-flop). */
        double densityKgM3,

        // -- damage thresholds ----------------------------------------------------

        /**
         * Energy (J) at which plastic deformation begins — below crackThresholdJ for
         * ductile materials, at or above crackThresholdJ for brittle ones (no yield zone).
         * Double.MAX_VALUE for indestructible blocks.
         */
        double yieldThresholdJ,

        /**
         * Energy (J) at which SOFT_SOIL compaction is triggered.
         * Double.MAX_VALUE for all non-SOFT_SOIL materials.
         */
        double compactionThresholdJ

) {}
