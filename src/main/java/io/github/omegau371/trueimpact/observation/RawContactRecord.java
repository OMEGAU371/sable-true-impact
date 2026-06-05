package io.github.omegau371.trueimpact.observation;

/**
 * Immutable capture of one entry from Rapier3D.clearCollisions() — 15 doubles at offset i*15.
 *
 * [source-proven]: RapierPhysicsPipeline.java:219-230 — layout exactly as documented below.
 *
 * Coordinate spaces (see docs/coordinate-systems.md):
 *   pointAx/y/z, pointBx/y/z — [inferred] BODY_COM_LOCAL [block], unit: block
 *   normalAx/y/z, normalBx/y/z — [inferred] BODY_COM_LOCAL direction, unit: dimensionless
 *   forceAmountRaw — unit UNKNOWN; [C3-codex] not named force or impulse (T-3 pending)
 *
 * [C8-codex] Contact identity:
 *   Use (stepId, rawRecordIndex, orderedBodyPairKey) as raw identifier.
 *   substepIndex is NOT included — T-5 experiment required to determine if
 *   clearCollisions array contains substep attribution data.
 *   Cross-tick trackId is NOT provided; any matching is candidate-only.
 *
 * [C2-codex] idA or idB == 0, or not found in activeSubLevels, means
 *   BodyType.NON_ACTIVE_SUBLEVEL_BODY — NOT proven to be terrain.
 */
public record RawContactRecord(
        long serverTick,
        int rawRecordIndex,
        long orderedBodyPairKey,
        int idA,
        int idB,
        double forceAmountRaw,
        double normalAx, double normalAy, double normalAz,
        double normalBx, double normalBy, double normalBz,
        double pointAx, double pointAy, double pointAz,
        double pointBx, double pointBy, double pointBz
) {
    public static long bodyPairKey(int idA, int idB) {
        int lo = Math.min(idA, idB);
        int hi = Math.max(idA, idB);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }
}
