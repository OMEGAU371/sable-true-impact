package io.github.omegau371.trueimpact.diagnostic;

import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.RawContactRecord;

/**
 * Processes the raw double[] from Rapier3D.clearCollisions() and logs T-3/T-5/T-6 data.
 * No Sable API references — called from DiagnosticContactCaptureMixin.
 */
public final class ContactLogger {

    private ContactLogger() {}

    /**
     * Called from DiagnosticContactCaptureMixin after clearCollisions() returns.
     *
     * @param data            raw double array from Rapier3D.clearCollisions()
     * @param serverTick      level.getGameTime() at time of capture
     * @param substepCount    PhysicsConfigData.substepsPerTick (for T-5 annotation only)
     */
    public static void onClearCollisions(double[] data, long serverTick, int substepCount) {
        if (!DiagnosticConfig.is(DiagnosticConfig.LOG_RAW_CONTACTS)) return;

        int count = data.length / 15;

        // T-5: record the total contact count and configured substep count
        // [T-5] substepIndex NOT derivable from this array without native evidence
        ExperimentLog.info("[T-5] tick={} contacts={} substepCount={} (substep attribution UNCONFIRMED)",
                serverTick, count, substepCount);

        for (int i = 0; i < count; i++) {
            if (!DiagnosticConfig.LIMITER.tryLog()) {
                ExperimentLog.LOG.debug("[T-3/T-6] tick={} dropped {} remaining contacts (rate limit)",
                        serverTick, count - i);
                break;
            }

            int base = i * 15;
            int idA = (int) data[base];
            int idB = (int) data[base + 1];
            double forceAmountRaw = data[base + 2];

            // T-3: log forceAmountRaw alongside body IDs for dimension analysis
            // [C3] unit is UNKNOWN — field is NOT named "force" or "impulse"
            ExperimentLog.LOG.info(
                    "[T-3] tick={} idx={} idA={} idB={} forceAmountRaw={} " +
                    "nAx={} nAy={} nAz={} nBx={} nBy={} nBz={} " +
                    "pAx={} pAy={} pAz={} pBx={} pBy={} pBz={}",
                    serverTick, i, idA, idB,
                    String.format("%.6f", forceAmountRaw),
                    String.format("%.4f", data[base + 3]),
                    String.format("%.4f", data[base + 4]),
                    String.format("%.4f", data[base + 5]),
                    String.format("%.4f", data[base + 6]),
                    String.format("%.4f", data[base + 7]),
                    String.format("%.4f", data[base + 8]),
                    String.format("%.4f", data[base + 9]),
                    String.format("%.4f", data[base + 10]),
                    String.format("%.4f", data[base + 11]),
                    String.format("%.4f", data[base + 12]),
                    String.format("%.4f", data[base + 13]),
                    String.format("%.4f", data[base + 14])
            );
        }
    }
}
