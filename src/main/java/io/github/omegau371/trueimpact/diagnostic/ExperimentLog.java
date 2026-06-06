package io.github.omegau371.trueimpact.diagnostic;

import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single rate-limited entry point for all diagnostic log output.
 *
 * [C5-log] The underlying Logger is private. All callers — including classes
 * in this package — MUST use info() or warn(). Direct LOG access is forbidden.
 * Dropped-count summary is also subject to the hard limit via tryLogDroppedSummary().
 */
public final class ExperimentLog {

    private static final Logger LOG = LoggerFactory.getLogger("true_impact/diag");

    private ExperimentLog() {}

    /** Log at INFO level, subject to global hard limit. @return true if logged. */
    public static boolean info(String msg) {
        if (!DiagnosticConfig.LIMITER.tryLog()) return false;
        LOG.info(msg);
        return true;
    }

    public static boolean info(String fmt, Object... args) {
        if (!DiagnosticConfig.LIMITER.tryLog()) return false;
        LOG.info(fmt, args);
        return true;
    }

    /** Log at WARN level (used for experiment validity warnings), subject to hard limit. */
    public static boolean warn(String fmt, Object... args) {
        if (!DiagnosticConfig.LIMITER.tryLog()) return false;
        LOG.warn(fmt, args);
        return true;
    }

    /**
     * Log the dropped-count summary if anything was dropped this tick.
     * [C9-codex] Summary also goes through the hard limit — it can itself be dropped.
     * @return number of dropped messages, or 0 if none or summary itself was dropped
     */
    public static int logDroppedSummaryIfNeeded() {
        int dropped = DiagnosticConfig.LIMITER.tryLogDroppedSummary();
        if (dropped > 0) {
            LOG.info("[TI diag] {} messages dropped this tick (hard limit reached)", dropped);
        }
        return dropped;
    }
}
