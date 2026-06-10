package io.github.omegau371.trueimpact.command;

/**
 * Calibration band classifier for kineticImpactEnergyJ.
 *
 * DISPLAY-ONLY: these bands are temporary human-readable labels for manual
 * in-game testing. They are NOT material thresholds and must NOT enter any
 * damage formula or resolver branch.
 *
 * See docs/phase-1c-damage-model.md "kBand calibration labels" section.
 *
 * Band boundaries (inclusive lower, exclusive upper):
 *   NONE   NaN / non-finite
 *   TOUCH  [0, 1)
 *   LIGHT  [1, 5)
 *   MEDIUM [5, 20)
 *   HEAVY  [20, 80)
 *   SEVERE [80, inf)
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
final class KImpactBand {

    private KImpactBand() {}

    static String of(double kImpact) {
        if (!Double.isFinite(kImpact)) return "NONE";
        if (kImpact <  1.0) return "TOUCH";
        if (kImpact <  5.0) return "LIGHT";
        if (kImpact < 20.0) return "MEDIUM";
        if (kImpact < 80.0) return "HEAVY";
        return "SEVERE";
    }
}
