package com.cambers.auth.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.cambers.auth.email")
class DeliveryArchitectureTests {

    @ArchTest
    static final ArchRule outbox_must_not_depend_on_resend_adapter = noClasses()
            .that().resideInAPackage("..email.outbox..")
            .should().dependOnClassesThat().resideInAPackage("..email.resend..");
}
