package com.cambers.auth.architecture;

import com.cambers.auth.AuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularArchitectureTests {

    private final ApplicationModules modules = ApplicationModules.of(AuthApplication.class);

    @Test
    void modular_structure_is_valid() {
        modules.verify();
    }

    @Test
    void only_explicitly_annotated_packages_are_modules_during_migration() {
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactly("observability");
    }
}
