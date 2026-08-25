package com.cambers.auth.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.cambers.auth")
class LayeredArchitectureTests {

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule repositories_must_not_depend_on_upper_layers = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule entities_must_not_depend_on_application_or_http_layers = noClasses()
            .that().resideInAPackage("..entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..",
                    "..service..",
                    "..repository..",
                    "..security..",
                    "..abuse..",
                    "..email.."
            );

    @ArchTest
    static final ArchRule email_outbox_must_not_depend_on_resend_adapter = noClasses()
            .that().resideInAPackage("..email.outbox..")
            .should().dependOnClassesThat().resideInAPackage("..email.resend..");
}
