package io.github.omegau371.trueimpact.diagnostic;

import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rate-limited logger wrapper for all experiment output. */
public final class ExperimentLog {

    public static final Logger LOG = LoggerFactory.getLogger("true_impact/diag");

    private ExperimentLog() {}

    /** Log an info message, subject to global hard limit. Returns true if the message was logged. */
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
}
