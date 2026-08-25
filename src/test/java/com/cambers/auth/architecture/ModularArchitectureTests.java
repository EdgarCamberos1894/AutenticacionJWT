package com.cambers.auth.architecture;

import com.cambers.auth.AuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularArchitectureTests {

    static {
        System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    }

    @Test
    void explicitlyDeclaredModulesRespectTheirBoundaries() {
        ApplicationModules modules = ApplicationModules.of(AuthApplication.class);

        modules.verify();

        assertThat(modules.getModuleByName("observability")).isPresent();
        var abuse = modules.getModuleByName("abuse").orElseThrow();
        assertThat(abuse.getDirectDependencies(modules).containsModuleNamed("observability")).isTrue();
        var account = modules.getModuleByName("account").orElseThrow();
        assertThat(account.getDirectDependencies(modules).containsModuleNamed("observability")).isTrue();
    }
}
