package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.sable.SableImpactCapture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Diagnostic sub-commands under /trueimpact.
 *
 * All debug commands require operator permission (level 2).
 * T-4 experiment commands require operator permission (level 4).
 *
 *   /trueimpact debug contacts [on|off]              -- T-3/T-5/T-6 raw contact logging
 *   /trueimpact debug callbacks [on|off]             -- T-1/T-2 callback logging
 *   /trueimpact debug bodies [on|off]                -- body snapshot + T-7 logging
 *   /trueimpact debug status                         -- print all current flags
 *   /trueimpact debug all off                        -- disable everything + clear state (op 2)
 *   /trueimpact experiment t4 bodies                 -- list sub-levels (op 4, Sable only)
 *   /trueimpact experiment t4 inspect <id>           -- detailed body readout (op 4, Sable only)
 *   /trueimpact experiment t4 apply <id> fx fy fz    -- T-4 com-current variant (op 4, Sable only)
 *   /trueimpact experiment t4 apply-linear <id> ...  -- T-4 linear-only variant (op 4, Sable only)
 *
 * [PERMANENTLY REMOVED] apply-at-pose: produced |dv|~2.15e9 and |omega|~3.61e9 in live test,
 *   causing server 21 s behind and Sable emergency sub-level removal.
 *   logicalPose().position() is not a valid application point for applyImpulse --
 *   coordinate space mismatch yields astronomical lever arm.
 *   Do NOT re-add without full coordinate-space audit.
 */
public final class DiagnosticCommand {

    private DiagnosticCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean sableLoaded) {
        var debug = Commands.literal("debug")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("contacts")
                        .then(Commands.literal("on").executes(ctx -> setContacts(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setContacts(ctx, false))))
                .then(Commands.literal("callbacks")
                        .then(Commands.literal("on").executes(ctx -> setCallbacks(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setCallbacks(ctx, false))))
                .then(Commands.literal("bodies")
                        .then(Commands.literal("on").executes(ctx -> setBodies(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setBodies(ctx, false))))
                .then(Commands.literal("status").executes(DiagnosticCommand::status))
                .then(Commands.literal("all")
                        .then(Commands.literal("off").executes(DiagnosticCommand::allOff)));

        var experiment = Commands.literal("experiment").requires(src -> src.hasPermission(4));
        if (sableLoaded) {
            experiment = experiment.then(
                    Commands.literal("t4")
                            .then(Commands.literal("bodies").executes(DiagnosticCommand::t4Bodies))
                            .then(Commands.literal("inspect")
                                    .then(Commands.argument("runtimeId", IntegerArgumentType.integer(0))
                                            .executes(DiagnosticCommand::t4Inspect)))
                            .then(Commands.literal("apply")
                                    .then(Commands.argument("runtimeId", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("fx", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("fy", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("fz", DoubleArgumentType.doubleArg())
                                                                    .executes(DiagnosticCommand::t4Apply))))))
                            .then(Commands.literal("apply-linear")
                                    .then(Commands.argument("runtimeId", IntegerArgumentType.integer(0))
                                            .then(Commands.argument("fx", DoubleArgumentType.doubleArg())
                                                    .then(Commands.argument("fy", DoubleArgumentType.doubleArg())
                                                            .then(Commands.argument("fz", DoubleArgumentType.doubleArg())
                                                                    .executes(DiagnosticCommand::t4ApplyLinear)))))));
                            // apply-at-pose PERMANENTLY REMOVED: live test caused |dv|~2.15e9, |omega|~3.61e9,
                            // server 21 s behind, Sable emergency sub-level removal.
                            // logicalPose().position() yields astronomical lever arm -- unsafe application point.
        }

        dispatcher.register(
                Commands.literal("trueimpact")
                        .then(debug)
                        .then(experiment)
        );
    }

    // -- debug subcommands --------------------------------------------------------

    private static int setContacts(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_RAW_CONTACTS = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] contacts (T-3/T-5/T-6): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setCallbacks(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_T1_CALLBACK_THREAD = on;
        DiagnosticConfig.LOG_T2_CALLBACK_COORD = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] callbacks (T-1/T-2): " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int setBodies(CommandContext<CommandSourceStack> ctx, boolean on) {
        DiagnosticConfig.ENABLED = on || DiagnosticConfig.ENABLED;
        DiagnosticConfig.LOG_BODY_SNAPSHOTS = on;
        DiagnosticConfig.LOG_T7_VELOCITY_RATIO = on;
        ctx.getSource().sendSuccess(() -> Component.literal("[TI diag] bodies + T-7: " + (on ? "ON" : "OFF")), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        int t4Pending = T4ApplyForceExperiment.pendingByKey.size();
        SableImpactCapture.RuntimeStats stats = SableImpactCapture.stats();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] enabled=" + DiagnosticConfig.ENABLED
                + " bodies=" + DiagnosticConfig.LOG_BODY_SNAPSHOTS
                + " contacts=" + DiagnosticConfig.LOG_RAW_CONTACTS
                + " callbacks_t1t2=" + DiagnosticConfig.LOG_T1_CALLBACK_THREAD
                + " t7=" + DiagnosticConfig.LOG_T7_VELOCITY_RATIO
                + " t4Pending=" + t4Pending), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI capture] calls=" + stats.totalProcessCalls()
                + " rawContacts=" + stats.totalRawContactsSeen()
                + " records=" + stats.totalImpactRecordsCreated()
                + " lastTick=" + stats.lastTick()
                + " lastRecords=" + stats.lastRecordCount()
                + " lastActiveImpact=" + stats.lastActiveImpactCount()
                + " lastSustained=" + stats.lastSustainedCount()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI capture last-hit] tick=" + stats.lastNonZeroRecordTick()
                + " records=" + stats.lastNonZeroRecordCount()
                + " activeImpact=" + stats.lastNonZeroActiveImpactCount()
                + " sustained=" + stats.lastNonZeroSustainedCount()), false);
        // Line 4: most recent record of any ContactType (ACTIVE_IMPACT or ACTIVE_SUSTAINED).
        ImpactMetrics rec = stats.lastRecordMetrics();
        if (rec == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-record-metrics] none"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-record-metrics] tick=" + rec.serverTick()
                    + " type=" + rec.contactType()
                    + " energyJ=" + fmt(rec.impactEnergyJ())
                    + " normalJ=" + fmt(rec.normalImpulseJ())
                    + " pressureProxy=" + fmt(rec.contactPressureProxy())
                    + " stress=" + fmt(rec.candidateStressEstimate())
                    + " thresholdJ=" + fmt(rec.materialThresholdJ())
                    + " exceeds=" + rec.exceedsThreshold()), false);
        }

        // Line 5: most recent ACTIVE_IMPACT record only (null if none since last reset).
        ImpactMetrics impact = stats.lastActiveImpactMetrics();
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-impact-metrics] none"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-impact-metrics] tick=" + impact.serverTick()
                    + " type=" + impact.contactType()
                    + " energyJ=" + fmt(impact.impactEnergyJ())
                    + " normalJ=" + fmt(impact.normalImpulseJ()) + "(T6-UC)"
                    + " pressureProxy=" + fmt(impact.contactPressureProxy()) + "(areaUC)"
                    + " stress=" + fmt(impact.candidateStressEstimate())
                    + " thresholdJ=" + fmt(impact.materialThresholdJ())
                    + " exceeds=" + impact.exceedsThreshold()), false);
        }

        // Line 6: T-8 rolling calibration stats (kineticDelta/impulseEnergy ratio)
        SableImpactCapture.T8Stats t8 = stats.t8Stats();
        if (!t8.hasSamples()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-stats] n=0 [no valid T-8 samples yet;"
                    + " need ACTIVE_IMPACT with finite kineticDelta"
                    + " -- enable 'debug contacts on']"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-stats] n=" + t8.sampleCount()
                    + " last=" + fmt(t8.lastRatio())
                    + " min="  + fmt(t8.minRatio())
                    + " avg="  + fmt(t8.averageRatio())
                    + " p50="  + fmt(t8.p50Ratio())
                    + " max="  + fmt(t8.maxRatio())), false);
        }

        // Line 7: T-8 kinetic validation for the last ACTIVE_IMPACT.
        // Shows formula trace (J, mEff, mA, mB, E) and 3D kinetic energy comparison.
        // velAvail shows per-body availability; NaN fields indicate missing data.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-impact] none"), false);
        } else {
            // Availability summary: explicit per-body flags
            String velAvail = "startA:" + b(impact.hasStartVelA())
                    + " startB:" + b(impact.hasStartVelB())
                    + " postA:"  + b(impact.hasPostVelA())
                    + " postB:"  + b(impact.hasPostVelB());
            boolean kineticAvail = !Double.isNaN(impact.kineticDeltaMagnitudeJ());
            String kineticLine = kineticAvail
                    ? (" kBefore=" + fmt(impact.kineticBeforeJ())
                       + " kAfter="  + fmt(impact.kineticAfterJ())
                       + " kDelta="  + fmt(impact.kineticDeltaMagnitudeJ())
                       + " ratio=kDelta/E=" + fmtRatio(impact.kineticDeltaMagnitudeJ(), impact.impactEnergyJ()))
                    : " kDelta=NaN [start or post vels missing]";
            if (!impact.hasStartVelA() || !impact.hasStartVelB()) {
                kineticLine += " [enable 'debug contacts on' for start vels]";
            }
            final String t8Full = "[TI capture T8-impact] tick=" + impact.serverTick()
                    + " J=" + fmt(impact.totalImpulseJ())
                    + " mEff=" + fmt(impact.effectiveMassKpg())
                    + " mA=" + fmt(impact.massAKpg())
                    + " mB=" + fmt(impact.massBKpg())
                    + " E=J^2/(2mEff)=" + fmt(impact.impactEnergyJ())
                    + " velAvail=[" + velAvail + "]"
                    + kineticLine;
            ctx.getSource().sendSuccess(() -> Component.literal(t8Full), false);
        }

        // Line 8: Unit audit -- candidate energy formulas vs kDelta.
        // Exposes rawSumForce so we can test whether:
        //   E_current = (rawSum*substepDt)^2/(2mEff)  [current: forceAmountRaw is force]
        //   E_noDt    = rawSum^2/(2mEff)              [candidate: rawSum is already impulse]
        // The formula whose E is closest to kDelta identifies the correct unit interpretation.
        // See docs/phase-1c-damage-model.md T-8 unit audit section.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-audit] none"), false);
        } else {
            double rawSum  = impact.rawSumForce();
            double subDt   = impact.substepDtUsed();
            double mEff    = impact.effectiveMassKpg();
            double kDelta  = impact.kineticDeltaMagnitudeJ();
            // E_current = J^2/(2mEff) where J = rawSum*substepDt (same as impactEnergyJ)
            double eCurrent = impact.impactEnergyJ();
            // E_noDt = rawSum^2/(2mEff) -- candidate if rawSum is already the impulse
            double eNoDt   = (Double.isFinite(rawSum) && Double.isFinite(mEff) && mEff > 0)
                    ? (rawSum * rawSum) / (2.0 * mEff) : Double.NaN;
            final String auditLine = "[TI capture T8-audit] tick=" + impact.serverTick()
                    + " rawSum=" + fmt(rawSum)
                    + " substepDt=" + fmt(subDt)
                    + " contactCount=" + impact.contactCount()
                    + " J=rawSum*substepDt=" + fmt(impact.totalImpulseJ())
                    + " E_current=J^2/(2mEff)=" + fmt(eCurrent)
                    + " E_noDt=rawSum^2/(2mEff)=" + fmt(eNoDt)
                    + " kDelta=" + (Double.isNaN(kDelta) ? "NaN" : fmt(kDelta))
                    + " ratio_current=" + fmtRatio(kDelta, eCurrent)
                    + " ratio_noDt=" + fmtRatio(kDelta, eNoDt)
                    + " [target: ratio~1.0 for correct formula]";
            ctx.getSource().sendSuccess(() -> Component.literal(auditLine), false);
        }

        // Line 9: Phase 1C canonical -- velocity-derived kinetic energy.
        // This is the primary damage energy metric after the T-8 unit audit showed
        // forceAmount-derived energy is ~1000x off.  Source=velocity requires
        // 'debug contacts on' for tick-start vels; post vels always available.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture canonical] none"), false);
        } else {
            boolean velFull = impact.hasStartVelA() && impact.hasStartVelB()
                    && impact.hasPostVelA()  && impact.hasPostVelB();
            boolean velPartial = !velFull && (impact.hasPostVelA() || impact.hasPostVelB());
            String sourceTag = velFull ? "velocity-full"
                    : velPartial ? "velocity-partial [enable 'debug contacts on' for start vels]"
                    : "unavailable [enable 'debug contacts on']";
            String kImpactStr = Double.isNaN(impact.kineticImpactEnergyJ())
                    ? "NaN" : fmt(impact.kineticImpactEnergyJ());
            String velImpStr  = Double.isNaN(impact.velocityDerivedImpulseJ())
                    ? "NaN [all 4 vels required]" : fmt(impact.velocityDerivedImpulseJ());
            final String canonLine = "[TI capture canonical] tick=" + impact.serverTick()
                    + " source=" + sourceTag
                    + " kImpact=abs(kBefore-kAfter)=" + kImpactStr
                    + " kBand=" + KImpactBand.of(impact.kineticImpactEnergyJ())
                    + " kBefore=" + (Double.isNaN(impact.kineticBeforeJ())  ? "NaN" : fmt(impact.kineticBeforeJ()))
                    + " kAfter="  + (Double.isNaN(impact.kineticAfterJ())   ? "NaN" : fmt(impact.kineticAfterJ()))
                    + " kDelta="  + (Double.isNaN(impact.kineticDeltaMagnitudeJ()) ? "NaN" : fmt(impact.kineticDeltaMagnitudeJ()))
                    + " dVRel3D=" + (Double.isNaN(impact.deltaRelativeSpeedMagnitude()) ? "NaN" : fmt(impact.deltaRelativeSpeedMagnitude()))
                    + " velImpulse=mEff*dVRel3D=" + velImpStr
                    + " exceeds=" + b(impact.exceedsThreshold())
                    + " threshold=" + fmt(impact.materialThresholdJ());
            ctx.getSource().sendSuccess(() -> Component.literal(canonLine), false);
        }
        return 1;
    }

    private static String b(boolean v) { return v ? "T" : "F"; }


    private static String fmtRatio(double num, double den) {
        if (!Double.isFinite(num) || !Double.isFinite(den) || den == 0) return "NaN";
        return String.format(java.util.Locale.ROOT, "%.4f", num / den);
    }

    private static String fmt(double value) {
        if (!Double.isFinite(value)) return String.valueOf(value);
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static int allOff(CommandContext<CommandSourceStack> ctx) {
        DiagnosticStateManager.clearAll();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[TI diag] ALL diagnostics OFF. State cleared (prevPrePos, T-4 pending, snapshots)."), false);
        return 1;
    }

    // -- T-4 (Sable-only) ---------------------------------------------------------

    private static int t4Bodies(CommandContext<CommandSourceStack> ctx) {
        return io.github.omegau371.trueimpact.sable.SableT4Command.listBodies(ctx.getSource());
    }

    private static int t4Inspect(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "runtimeId");
        return io.github.omegau371.trueimpact.sable.SableT4Command.inspectBody(ctx.getSource(), id);
    }

    private static int t4ApplyLinear(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "runtimeId");
        double fx = DoubleArgumentType.getDouble(ctx, "fx");
        double fy = DoubleArgumentType.getDouble(ctx, "fy");
        double fz = DoubleArgumentType.getDouble(ctx, "fz");
        return io.github.omegau371.trueimpact.sable.SableT4Command.applyLinearExperiment(
                ctx.getSource(), id, fx, fy, fz);
    }

    private static int t4Apply(CommandContext<CommandSourceStack> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "runtimeId");
        double fx = DoubleArgumentType.getDouble(ctx, "fx");
        double fy = DoubleArgumentType.getDouble(ctx, "fy");
        double fz = DoubleArgumentType.getDouble(ctx, "fz");
        return io.github.omegau371.trueimpact.sable.SableT4Command.applyForExperiment(
                ctx.getSource(), id, fx, fy, fz);
    }
}
