package io.github.omegau371.trueimpact.sable;

/**
 * Input vector validation for T-4 experiment commands.
 * Pure Java — no Sable or Minecraft imports — so this class is unit-testable without mocks.
 */
final class InputVectorGuard {

    private InputVectorGuard() {}

    /**
     * Returns true iff fx, fy, fz are all finite AND their Euclidean magnitude is finite.
     * Rejects NaN, ±Infinity in any component or in the resulting magnitude.
     */
    static boolean isFiniteInput(double fx, double fy, double fz) {
        if (!Double.isFinite(fx) || !Double.isFinite(fy) || !Double.isFinite(fz)) return false;
        return Double.isFinite(Math.sqrt(fx * fx + fy * fy + fz * fz));
    }
}
