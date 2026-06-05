package io.github.omegau371.trueimpact.observation;

public enum SnapshotPhase {
    /** Captured immediately before Rapier3D.step() for this substep. */
    PRE_STEP,
    /** Captured immediately after Rapier3D.step() + updateAllPoses() for this substep. */
    POST_STEP
}
