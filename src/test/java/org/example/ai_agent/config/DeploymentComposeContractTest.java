package org.example.ai_agent.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentComposeContractTest {

    @Test
    void shouldUseTheReviewedModeSpecificNameForSpringNacosMetricsAndTraces() throws IOException {
        String compose = Files.readString(Path.of("compose.cloud.yaml"));

        assertTrue(compose.contains(
                "SPRING_APPLICATION_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
        assertTrue(compose.contains(
                "AIDIAG_SERVICE_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
        assertTrue(compose.contains(
                "OTEL_SERVICE_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
    }
}
