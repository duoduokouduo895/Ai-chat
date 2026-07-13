package org.example.ai_agent.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void shouldFailClosedBeforeTheFirstProductionCutover() throws IOException {
        String productionEnv = Files.readString(Path.of("deploy/production.env.example"));
        String activation = Files.readString(Path.of("deploy/cloud-activate.sh"));
        String preflight = Files.readString(Path.of("deploy/cloud-preflight-production.sh"));

        assertTrue(productionEnv.contains("NACOS_SERVER_ADDR=nacos-v3:8848"));
        assertTrue(productionEnv.contains("ELASTICSEARCH_SERVER_ADDR=elasticsearch-v9:9200"));
        assertTrue(productionEnv.contains("AICHAT_PRODUCTION_CONFIRMED=false"));
        assertTrue(productionEnv.contains("AICHAT_CUTOVER_ID="));
        assertFalse(productionEnv.contains("NACOS_SERVER_ADDR=nacos:8848"));

        assertTrue(activation.contains("cloud-preflight-production.sh\" --ready"));
        assertTrue(preflight.contains("OLD_PRODUCTION_WRITERS_STOPPED=true"));
        assertTrue(preflight.contains("PRODUCTION_ACTIVATION_APPROVED=true"));
        assertTrue(preflight.contains("ai_agent_db.sql.gz"));
        assertTrue(preflight.contains("Production and candidate must not share the same Secret"));
        assertTrue(preflight.contains("AI-chat candidate must be stopped before production activation"));
        assertTrue(preflight.contains("export AICHAT_RELEASE=\"${release_name}\""));
        assertTrue(preflight.contains("Production Secret is missing, unsafe, or multiline"));
    }
}
