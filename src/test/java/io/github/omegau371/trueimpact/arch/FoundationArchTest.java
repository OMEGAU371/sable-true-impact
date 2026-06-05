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
 * Phase 0 rules (R1-R4): layer boundaries and no-client-class constraints.
 * Phase 1A rules (R5-R8): observation/diagnostic must not depend on future damage layer.
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
}
