package com.cambers.auth.architecture;

import com.cambers.auth.AuthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ModularArchitectureTests {

    private static final String DETECTION_STRATEGY_PROPERTY = "spring.modulith.detection-strategy";
    private static final String EXPLICITLY_ANNOTATED = "explicitly-annotated";

    @Test
    void declaredModulesRespectTheirBoundaries() {
        String previousStrategy = System.getProperty(DETECTION_STRATEGY_PROPERTY);

        try {
            System.setProperty(DETECTION_STRATEGY_PROPERTY, EXPLICITLY_ANNOTATED);
            ApplicationModules modules = ApplicationModules.of(AuthApplication.class);

            modules.verify();

            Set<String> identifiers = modules.stream()
                    .map(module -> module.getIdentifier().toString())
                    .collect(Collectors.toSet());

            assertThat(identifiers).containsExactlyInAnyOrder(
                    "account",
                    "authentication",
                    "delivery",
                    "observability",
                    "abuse"
            );
        } finally {
            restoreDetectionStrategy(previousStrategy);
        }
    }

    private void restoreDetectionStrategy(String previousStrategy) {
        if (previousStrategy == null) {
            System.clearProperty(DETECTION_STRATEGY_PROPERTY);
            return;
        }
        System.setProperty(DETECTION_STRATEGY_PROPERTY, previousStrategy);
    }
}
