package com.cambers.auth.architecture;

import com.cambers.auth.AuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModularArchitectureTests {

    @Test
    void declaredModulesRespectTheirBoundaries() {
        ApplicationModules modules = ApplicationModules.of(AuthApplication.class);

        modules.verify();

        Set<String> identifiers = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());

        assertThat(identifiers).containsExactlyInAnyOrder(
                "delivery",
                "observability",
                "abuse"
        );
    }
}
