package io.github.omegau371.trueimpact.command;

import io.github.omegau371.trueimpact.damage.BlockDamageAccumulator;
import io.github.omegau371.trueimpact.damage.MaterialResponsePlan;

/**
 * MC-free formatter helpers for /trueimpact damage inspect output.
 * Extracted so unit tests can exercise formatting without loading MC classes.
 * DamageCommand calls these for actual chat output.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public final class DamageInspectFormatter {

    private DamageInspectFormatter() {}

    public static String formatNoEntry(String levelKey, int x, int y, int z, String currentBlockId) {
        return "[TI damage] no damage recorded"
                + " level=" + levelKey
                + " pos=(" + x + "," + y + "," + z + ")"
                + " block=" + currentBlockId;
    }

    public static String formatEntry(BlockDamageAccumulator.Snapshot snap) {
        return "[TI damage inspect]"
                + " block=" + snap.key().victimBlock()
                + " pos=(" + snap.key().posX() + "," + snap.key().posY()
                + "," + snap.key().posZ() + ")"
                + " class=" + snap.materialClass()
                + " threshold=" + fmt(snap.thresholdJ()) + "J"
                + " accumEff=" + fmt(snap.accumulatedEffectiveDamageJ()) + "J"
                + " rawLast=" + fmt(snap.lastRawImpactJ()) + "J"
                + " effLast=" + fmt(snap.lastEffectiveDamageJ()) + "J"
                + " ratio=" + (Double.isFinite(snap.ratio()) ? fmt(snap.ratio()) : "NaN")
                + " state=" + snap.damageState()
                + " hits=" + snap.hitCount()
                + " lastTick=" + snap.lastUpdatedTick();
    }

    /**
     * Formats the material response plan line shown below the main inspect line.
     *
     * @param plan          the plan produced by MaterialResponsePlanner.plan(snap)
     * @param debrisDropped whether debris has already been dropped for this block's key
     */
    public static String formatPlan(MaterialResponsePlan plan, boolean debrisDropped) {
        return "[TI damage plan]"
                + " response=" + plan.responseType()
                + " fbe=" + plan.futureBreakEligible()
                + " debris=" + debrisDropped
                + " note=" + plan.diagnosticNote();
    }

    private static String fmt(double v) {
        if (!Double.isFinite(v)) return String.valueOf(v);
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }
}
