package io.github.omegau371.trueimpact.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.omegau371.trueimpact.damage.MaterialThresholdProfile;
import io.github.omegau371.trueimpact.damage.VictimInfo;
import io.github.omegau371.trueimpact.diagnostic.T4ApplyForceExperiment;
import io.github.omegau371.trueimpact.observation.DiagnosticConfig;
import io.github.omegau371.trueimpact.observation.DiagnosticStateManager;
import io.github.omegau371.trueimpact.physics.ContactType;
import io.github.omegau371.trueimpact.physics.ImpactMetrics;
import io.github.omegau371.trueimpact.sable.SableImpactCapture;
import net.minecraft.ChatFormatting;
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
        boolean captureActive = stats.captureActive();

        // Line 1: [TI diag] flag summary.
        // GRAY when enabled; DARK_GRAY when disabled (nothing is logging).
        ChatFormatting diagColor = DiagnosticConfig.ENABLED ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
        String diagText = "[TI diag] enabled=" + DiagnosticConfig.ENABLED
                + " bodies=" + DiagnosticConfig.LOG_BODY_SNAPSHOTS
                + " contacts=" + DiagnosticConfig.LOG_RAW_CONTACTS
                + " callbacks_t1t2=" + DiagnosticConfig.LOG_T1_CALLBACK_THREAD
                + " t7=" + DiagnosticConfig.LOG_T7_VELOCITY_RATIO
                + " t4Pending=" + t4Pending;
        ctx.getSource().sendSuccess(() -> Component.literal(diagText).withStyle(diagColor), false);

        // Lines 2-3: capture counters.
        // DARK_GRAY when capture is paused (all diagnostics off -- enable any flag to resume).
        // AQUA when capture is active.
        if (!captureActive) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture] PAUSED -- enable any /trueimpact debug flag to resume capture")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-hit] PAUSED")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            String captureLine = "[TI capture] calls=" + stats.totalProcessCalls()
                    + " rawContacts=" + stats.totalRawContactsSeen()
                    + " records=" + stats.totalImpactRecordsCreated()
                    + " lastTick=" + stats.lastTick()
                    + " lastRecords=" + stats.lastRecordCount()
                    + " lastActiveImpact=" + stats.lastActiveImpactCount()
                    + " lastSustained=" + stats.lastSustainedCount();
            ctx.getSource().sendSuccess(() -> Component.literal(captureLine)
                    .withStyle(ChatFormatting.AQUA), false);
            String lastHitLine = "[TI capture last-hit] tick=" + stats.lastNonZeroRecordTick()
                    + " records=" + stats.lastNonZeroRecordCount()
                    + " activeImpact=" + stats.lastNonZeroActiveImpactCount()
                    + " sustained=" + stats.lastNonZeroSustainedCount();
            ctx.getSource().sendSuccess(() -> Component.literal(lastHitLine)
                    .withStyle(ChatFormatting.AQUA), false);
        }
        // Line 4: most recent record of any ContactType (ACTIVE_IMPACT or ACTIVE_SUSTAINED).
        // GOLD for ACTIVE_IMPACT, YELLOW for ACTIVE_SUSTAINED, DARK_GRAY for none.
        ImpactMetrics rec = stats.lastRecordMetrics();
        if (rec == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-record-metrics] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            ChatFormatting recColor = rec.contactType() == ContactType.ACTIVE_IMPACT
                    ? ChatFormatting.GOLD : ChatFormatting.YELLOW;
            String recText = "[TI capture last-record-metrics] tick=" + rec.serverTick()
                    + " type=" + rec.contactType()
                    + " energyJ=" + fmt(rec.impactEnergyJ())
                    + " normalJ=" + fmt(rec.normalImpulseJ())
                    + " pressureProxy=" + fmt(rec.contactPressureProxy())
                    + " stress=" + fmt(rec.candidateStressEstimate())
                    + " thresholdJ=" + fmt(rec.materialThresholdJ())
                    + " exceeds=" + rec.exceedsThreshold();
            ctx.getSource().sendSuccess(() -> Component.literal(recText).withStyle(recColor), false);
        }

        // Line 5: most recent ACTIVE_IMPACT record only (null if none since last reset).
        // GOLD when present (always ACTIVE_IMPACT), DARK_GRAY for none.
        ImpactMetrics impact = stats.lastActiveImpactMetrics();
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture last-impact-metrics] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            String impText = "[TI capture last-impact-metrics] tick=" + impact.serverTick()
                    + " type=" + impact.contactType()
                    + " energyJ=" + fmt(impact.impactEnergyJ())
                    + " normalJ=" + fmt(impact.normalImpulseJ()) + "(T6-UC)"
                    + " pressureProxy=" + fmt(impact.contactPressureProxy()) + "(areaUC)"
                    + " stress=" + fmt(impact.candidateStressEstimate())
                    + " thresholdJ=" + fmt(impact.materialThresholdJ())
                    + " exceeds=" + impact.exceedsThreshold();
            ctx.getSource().sendSuccess(() -> Component.literal(impText).withStyle(ChatFormatting.GOLD), false);
        }

        // Line 6: T-8 rolling calibration stats.
        // LIGHT_PURPLE when samples present, DARK_GRAY when empty.
        SableImpactCapture.T8Stats t8 = stats.t8Stats();
        if (!t8.hasSamples()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-stats] n=0 [no valid T-8 samples yet;"
                    + " need ACTIVE_IMPACT with finite kineticDelta"
                    + " -- enable 'debug contacts on']").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            String t8StatText = "[TI capture T8-stats] n=" + t8.sampleCount()
                    + " last=" + fmt(t8.lastRatio())
                    + " min="  + fmt(t8.minRatio())
                    + " avg="  + fmt(t8.averageRatio())
                    + " p50="  + fmt(t8.p50Ratio())
                    + " max="  + fmt(t8.maxRatio());
            ctx.getSource().sendSuccess(() -> Component.literal(t8StatText)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        // Line 7: T-8 kinetic validation for the last ACTIVE_IMPACT.
        // LIGHT_PURPLE when present, DARK_GRAY for none.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-impact] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
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
            ctx.getSource().sendSuccess(() -> Component.literal(t8Full)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        // Line 8: Unit audit (solver diagnostic -- forceAmount-based; NOT canonical).
        // GRAY -- audit/reference data, not primary calibration signal.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture T8-audit] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            double rawSum   = impact.rawSumForce();
            double subDt    = impact.substepDtUsed();
            double mEff8    = impact.effectiveMassKpg();
            double kDelta8  = impact.kineticDeltaMagnitudeJ();
            double eCurrent = impact.impactEnergyJ();
            double eNoDt    = (Double.isFinite(rawSum) && Double.isFinite(mEff8) && mEff8 > 0)
                    ? (rawSum * rawSum) / (2.0 * mEff8) : Double.NaN;
            final String auditLine = "[TI capture T8-audit] tick=" + impact.serverTick()
                    + " rawSum=" + fmt(rawSum)
                    + " substepDt=" + fmt(subDt)
                    + " contactCount=" + impact.contactCount()
                    + " J=rawSum*substepDt=" + fmt(impact.totalImpulseJ())
                    + " E_current=J^2/(2mEff)=" + fmt(eCurrent)
                    + " E_noDt=rawSum^2/(2mEff)=" + fmt(eNoDt)
                    + " kDelta=" + (Double.isNaN(kDelta8) ? "NaN" : fmt(kDelta8))
                    + " ratio_current=" + fmtRatio(kDelta8, eCurrent)
                    + " ratio_noDt=" + fmtRatio(kDelta8, eNoDt)
                    + " [target: ratio~1.0 for correct formula]";
            ctx.getSource().sendSuccess(() -> Component.literal(auditLine)
                    .withStyle(ChatFormatting.GRAY), false);
        }

        // Line 9: Phase 1C canonical -- velocity-derived kinetic energy.
        // LIGHT_PURPLE for velocity-full source, YELLOW for partial, DARK_GRAY for none/unavailable.
        if (impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture canonical] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            boolean velFull    = impact.hasStartVelA() && impact.hasStartVelB()
                    && impact.hasPostVelA() && impact.hasPostVelB();
            boolean velPartial = !velFull && (impact.hasPostVelA() || impact.hasPostVelB());
            ChatFormatting canonColor = velFull    ? ChatFormatting.LIGHT_PURPLE
                    : velPartial ? ChatFormatting.YELLOW
                    : ChatFormatting.DARK_GRAY;
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
            ctx.getSource().sendSuccess(() -> Component.literal(canonLine).withStyle(canonColor), false);
        }

        // Line 10: Phase 1D victim material threshold.
        // Shows detected victim block (contact-point sampling or callback) or ACTIVE_SUBLEVEL.
        // Source=NO_CALLBACK: world contact seen but block identification failed
        //   (callback not fired for that block type AND contact-point sampling found no solid block).
        // kImpact from last ACTIVE_IMPACT; threshold from detected victim's material class.
        // RED when wouldExceed=true, YELLOW for NO_CALLBACK, GRAY otherwise, DARK_GRAY when no data.
        VictimInfo victim = stats.lastVictimInfo();
        if (victim == null && impact == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TI capture threshold] none").withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            VictimInfo displayVictim = (victim != null) ? victim : VictimInfo.unknown();
            // Use kImpact from last ACTIVE_IMPACT if available; fall back to last record.
            double kImpact = (impact != null)
                    ? impact.kineticImpactEnergyJ()
                    : (rec != null ? rec.kineticImpactEnergyJ() : Double.NaN);
            double matThreshold = displayVictim.materialThresholdJ();
            boolean wouldExceed = Double.isFinite(kImpact) && kImpact > matThreshold;
            boolean noCallback  = displayVictim.source() == VictimInfo.Source.NO_CALLBACK;
            ChatFormatting threshColor = wouldExceed ? ChatFormatting.RED
                    : noCallback ? ChatFormatting.YELLOW
                    : ChatFormatting.GRAY;
            String blockStr = (displayVictim.blockId() != null) ? displayVictim.blockId() : "none";
            String posStr = displayVictim.hasPos()
                    ? ("(" + displayVictim.posX() + "," + displayVictim.posY() + ","
                       + displayVictim.posZ() + ")")
                    : "unknown";
            String noteStr = noCallback
                    ? "world contact seen; no block data (no BlockSubLevelCollisionCallback + sampling failed)"
                    : "diagnostic-only";
            final String threshLine = "[TI capture threshold]"
                    + " victimKind=" + displayVictim.kind()
                    + " victimBlock=" + blockStr
                    + " victimPos=" + posStr
                    + " confidence=" + displayVictim.confidence()
                    + " source=" + displayVictim.source()
                    + " materialClass=" + displayVictim.materialClass()
                    + " threshold=" + fmt(matThreshold)
                    + " kImpact=" + (Double.isNaN(kImpact) ? "NaN" : fmt(kImpact))
                    + " kBand=" + KImpactBand.of(kImpact)
                    + " wouldExceed=" + wouldExceed
                    + " note=" + noteStr;
            ctx.getSource().sendSuccess(() -> Component.literal(threshLine).withStyle(threshColor), false);
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
