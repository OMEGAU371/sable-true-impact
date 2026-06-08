package io.github.omegau371.trueimpact.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture guards for all phases.
 * Build fails on any violation.
 *
 * Phase 0  rules (R1-R4):  layer boundaries and no-client-class constraints.
 * Phase 1A rules (R5-R8):  observation/diagnostic must not depend on future damage layer.
 * Phase 1B rules (R9-R12): physics/ and damage/ layer isolation.
 */
@AnalyzeClasses(
        packages = "io.github.omegau371.trueimpact",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class FoundationArchTest {

    // ── Phase 0 rules ─────────────────────────────────────────────────────────

    @ArchTest
    static final ArchRule platform_must_not_depend_on_command =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..command..");

    @ArchTest
    static final ArchRule command_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..command..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    @ArchTest
    static final ArchRule platform_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    @ArchTest
    static final ArchRule mod_root_must_not_use_client_classes =
            noClasses().that().resideInAPackage("io.github.omegau371.trueimpact")
                    .and().areNotAnnotatedWith("net.neoforged.api.distmarker.OnlyIn")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    // ── Phase 1A rules ────────────────────────────────────────────────────────
    // Decree: observation and diagnostic layers must not depend on the future damage layer.
    // Production logic must not read diagnostic state to influence game behavior.

    @ArchTest
    static final ArchRule observation_must_not_depend_on_damage =
            noClasses().that().resideInAPackage("..observation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..damage..");

    @ArchTest
    static final ArchRule diagnostic_must_not_depend_on_damage =
            noClasses().that().resideInAPackage("..diagnostic..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..damage..");

    @ArchTest
    static final ArchRule observation_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..observation..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    @ArchTest
    static final ArchRule diagnostic_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..diagnostic..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    // -- Phase 1B rules ----------------------------------------------------------

    // R9: physics/ is the pure data contract layer -- no upstream TI dependencies.
    // It must not depend on observation, diagnostic, damage, command, platform, or sable.
    @ArchTest
    static final ArchRule physics_must_not_depend_on_damage =
            noClasses().that().resideInAPackage("..physics..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..damage..");

    @ArchTest
    static final ArchRule physics_must_not_depend_on_diagnostic_or_observation =
            noClasses().that().resideInAPackage("..physics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..diagnostic..", "..observation..");

    // R10: damage/ resolver reads only physics/ -- never diagnostic or observation state.
    // This ensures the resolver cannot read DiagnosticConfig or GlobalRateLimiter.
    @ArchTest
    static final ArchRule damage_must_not_depend_on_diagnostic_or_observation =
            noClasses().that().resideInAPackage("..damage..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..diagnostic..", "..observation..");

    // R11: damage/ must not use client-only classes.
    @ArchTest
    static final ArchRule damage_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..damage..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    // R12: physics/ must not use client-only classes.
    @ArchTest
    static final ArchRule physics_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..physics..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");
}
