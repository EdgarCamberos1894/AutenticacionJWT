package com.cambers.auth.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packages = "com.cambers.auth.email")
class DeliveryArchitectureTests {

    @ArchTest
    static final ArchRule outbox_must_not_depend_on_resend_adapter = noClasses()
            .that().resideInAPackage("..email.outbox..")
            .should().dependOnClassesThat().resideInAPackage("..email.resend..");

    @Test
    void deliveryBasePackageExposesOnlyApplicationContract() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.cambers.auth.email");

        Set<String> publicBaseTypes = classes.stream()
                .filter(javaClass -> javaClass.getPackageName().equals("com.cambers.auth.email"))
                .filter(javaClass -> javaClass.getModifiers().contains(JavaModifier.PUBLIC))
                .map(JavaClass::getSimpleName)
                .collect(Collectors.toSet());

        assertThat(publicBaseTypes).containsExactly("AuthenticationEmailDelivery");
    }
}
