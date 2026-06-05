package io.github.omegau371.trueimpact.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Mechanically enforces the foundation-phase architecture rules.
 * Build fails on any violation — add a rule here before adding code that would violate it.
 *
 * Layer model (lowest → highest dependency direction):
 *   platform  ←  command  ←  TrueImpactMod
 *
 * Rules:
 *   R1  platform must not depend on command (no upward dependency)
 *   R2  command must not import Minecraft client-only classes
 *   R3  platform must not import Minecraft client-only classes
 */
@AnalyzeClasses(
        packages = "io.github.omegau371.trueimpact",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class FoundationArchTest {

    // R1 — platform is the lowest layer; it must not depend on command
    @ArchTest
    static final ArchRule platform_must_not_depend_on_command =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..command..");

    // R2 — command layer must never reference client-only Minecraft classes
    @ArchTest
    static final ArchRule command_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..command..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    // R3 — platform layer must never reference client-only Minecraft classes
    @ArchTest
    static final ArchRule platform_must_not_use_client_classes =
            noClasses().that().resideInAPackage("..platform..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");

    // R4 — mod entry point must not bypass layers and directly reference client classes
    @ArchTest
    static final ArchRule mod_root_must_not_use_client_classes =
            noClasses().that().resideInAPackage("io.github.omegau371.trueimpact")
                    .and().areNotAnnotatedWith("net.neoforged.api.distmarker.OnlyIn")
                    .should().dependOnClassesThat()
                    .resideInAPackage("net.minecraft.client..");
}
