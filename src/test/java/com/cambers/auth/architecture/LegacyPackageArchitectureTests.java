package com.cambers.auth.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LegacyPackageArchitectureTests {

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter().importPackages("com.cambers.auth");

    @Test
    void retiredLayeredAndTransversalRootPackagesStayRetired() {
        noClasses()
                .should().resideInAnyPackage(
                        "com.cambers.auth.controller..",
                        "com.cambers.auth.dto..",
                        "com.cambers.auth.entity..",
                        "com.cambers.auth.repository..",
                        "com.cambers.auth.service..",
                        "com.cambers.auth.config..",
                        "com.cambers.auth.exception..",
                        "com.cambers.auth.security..",
                        "com.cambers.auth.validation.."
                )
                .check(APPLICATION_CLASSES);
    }
}
