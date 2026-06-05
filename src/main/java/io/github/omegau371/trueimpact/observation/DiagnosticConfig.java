package io.github.omegau371.trueimpact.observation;

/**
 * Runtime diagnostic flags. ALL default to false — diagnostics are off unless explicitly enabled.
 *
 * Production logic MUST NOT read any of these fields to influence game behavior.
 * These flags control logging only.
 */
public final class DiagnosticConfig {

    private DiagnosticConfig() {}

    // Master switch — must be true for any sub-flag to take effect
    public static volatile boolean ENABLED = false;

    // Body snapshot logging (PRE_STEP / POST_STEP per substep)
    public static volatile boolean LOG_BODY_SNAPSHOTS = false;

    // Raw contact record logging from clearCollisions() (T-3, T-5, T-6)
    public static volatile boolean LOG_RAW_CONTACTS = false;

    // T-1: callback thread identity logging
    public static volatile boolean LOG_T1_CALLBACK_THREAD = false;

    // T-2: callback coordinate identification logging
    public static volatile boolean LOG_T2_CALLBACK_COORD = false;

    // T-7: velocity units comparison (logged when LOG_BODY_SNAPSHOTS is true)
    public static volatile boolean LOG_T7_VELOCITY_RATIO = false;

    /** @return true only if master switch is on AND the given sub-flag is on. */
    public static boolean is(boolean subFlag) {
        return ENABLED && subFlag;
    }

    public static final GlobalRateLimiter LIMITER = new GlobalRateLimiter();
}
