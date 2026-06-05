package io.github.omegau371.trueimpact.observation;

public enum BodyType {
    /**
     * Body ID was found in activeSubLevels at the time of capture.
     * [source-proven]: RapierPhysicsPipeline.java:231-232
     */
    ACTIVE_SUBLEVEL,
    /**
     * Body ID was NOT found in activeSubLevels.
     * [C2-from-review]: This does NOT prove the body is terrain —
     * only that it is not currently an active ServerSubLevel.
     */
    NON_ACTIVE_SUBLEVEL_BODY
}
