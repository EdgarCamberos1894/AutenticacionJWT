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
    }
}
