package io.github.omegau371.trueimpact.damage;

/**
 * A single surface-compaction rule: an impact on {@code fromBlockId} exceeding
 * {@code thresholdJ} has a {@code probability} (0.0-1.0) chance of transforming the block
 * into {@code toBlockId}.
 *
 * Configured as a list of "fromId;toId;thresholdJ;probability" strings in
 * [advanced.compaction] compactionRules -- NeoForge's built-in config-list GUI only supports
 * flat string lists (no per-field editing for compound entries), so each rule is one
 * semicolon-delimited line the player can add/edit/remove directly in the config screen.
 *
 * No Minecraft imports -- safe to unit-test without the game runtime.
 */
public record CompactionRule(String fromBlockId, String toBlockId, double thresholdJ, double probability) {

    /**
     * Parses one config-list entry ("fromId;toId;thresholdJ;probability").
     * Returns null for malformed lines (wrong field count, unparseable numbers, threshold
     * < 0, or probability outside [0,1]) -- callers should skip and log malformed entries
     * rather than fail the whole list.
     */
    public static CompactionRule parse(String line) {
        if (line == null) return null;
        String[] parts = line.split(";", -1);
        if (parts.length != 4) return null;

        String from = parts[0].trim();
        String to = parts[1].trim();
        if (from.isEmpty() || to.isEmpty()) return null;

        double threshold;
        double probability;
        try {
            threshold = Double.parseDouble(parts[2].trim());
            probability = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (!Double.isFinite(threshold) || threshold < 0) return null;
        if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) return null;

        return new CompactionRule(from, to, threshold, probability);
    }

    /** Serializes back to the "fromId;toId;thresholdJ;probability" config-list format. */
    public String toConfigLine() {
        return fromBlockId + ";" + toBlockId + ";" + thresholdJ + ";" + probability;
    }
}
