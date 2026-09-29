package org.example.ai_agent.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentComposeContractTest {

    @Test
    void shouldUseTheReviewedModeSpecificNameForSpringMetricsAndTraces() throws IOException {
        String compose = Files.readString(rootFile("compose.cloud.yaml"));

        assertTrue(compose.contains(
                "SPRING_APPLICATION_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
        assertTrue(compose.contains(
                "AIDIAG_SERVICE_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
        assertTrue(compose.contains(
                "OTEL_SERVICE_NAME: ${AICHAT_SERVICE_NAME:?Set a mode-specific service name}"));
    }

    @Test
    void shouldLoadConfigurationFromAReadOnlyMountInsteadOfNacos() throws IOException {
        String compose = Files.readString(rootFile("compose.cloud.yaml"));

        assertTrue(compose.contains("SPRING_PROFILES_ACTIVE: prod\n"));
        assertTrue(compose.contains("AICHAT_CONFIG_DIR: /opt/aichat/config\n"));
        assertTrue(compose.contains("AICHAT_CONFIG_SECRETS_DIR: /opt/aichat/config/secrets\n"));
        assertTrue(compose.contains("SPRING_CONFIG_IMPORT: file:/opt/aichat/config/application.properties,configtree:/opt/aichat/config/secrets/"));
        assertTrue(compose.contains("target: /opt/aichat/config"));
        assertTrue(compose.contains("read_only: true"));
        assertTrue(compose.contains("create_host_path: false"));
        assertFalse(compose.toLowerCase().contains("nacos"));
    }

    @Test
    void shouldFailClosedBeforeTheFirstProductionCutover() throws IOException {
        String productionEnv = Files.readString(rootFile("deploy/production.env.example"));
        String activation = Files.readString(rootFile("deploy/cloud-activate.sh"));
        String preflight = Files.readString(rootFile("deploy/cloud-preflight-production.sh"));

        assertTrue(productionEnv.contains("AICHAT_CONFIG_HOST_DIR=/opt/ai-chat/runtime/config/production"));
        assertTrue(productionEnv.contains("ELASTICSEARCH_SERVER_ADDR=elasticsearch-v9:9200"));
        assertTrue(productionEnv.contains("AICHAT_PRODUCTION_CONFIRMED=false"));
        assertTrue(productionEnv.contains("AICHAT_CUTOVER_ID="));
        assertFalse(productionEnv.contains("NACOS"));

        assertTrue(activation.contains("cloud-preflight-production.sh\" --ready"));
        assertTrue(preflight.contains("OLD_PRODUCTION_WRITERS_STOPPED=true"));
        assertTrue(preflight.contains("PRODUCTION_ACTIVATION_APPROVED=true"));
        assertTrue(preflight.contains("ai_agent_db.sql.gz"));
        assertTrue(preflight.contains("Production and candidate must not share the same Secret"));
        assertTrue(preflight.contains("AI-chat candidate must be stopped before production activation"));
        assertTrue(preflight.contains("export AICHAT_RELEASE=\"${release_name}\""));
        assertTrue(preflight.contains("Production Secret is missing, unsafe, or multiline"));

        String approval = Files.readString(rootFile("deploy/cloud-approve-production-cutover.sh"));
        assertTrue(approval.contains("legacy-old-writer-stopped.env"));
        assertTrue(approval.contains("LEGACY_MAIN_PID_RUNNING=false"));
        assertTrue(approval.contains("LEGACY_APP_PORT=8080"));
        assertTrue(approval.contains("LEGACY_APP_PORT_LISTENING=false"));
        assertTrue(approval.contains("LEGACY_STOP_EVIDENCE_SHA256="));
    }

    @Test
    void shouldKeepNginxAndDnsCutoverExplicitAndRollbackSafe() throws IOException {
        String template = Files.readString(rootFile("deploy/nginx/api.dduo.chat.conf.template"));
        String activation = Files.readString(rootFile("deploy/cloud-activate-production-nginx.sh"));
        String rollback = Files.readString(rootFile("deploy/cloud-rollback-production-nginx.sh"));

        assertTrue(template.contains("proxy_pass http://127.0.0.1:__APP_PORT__"));
        assertTrue(template.contains("location = /healthz"));
        assertTrue(template.contains("proxy_pass http://127.0.0.1:__MANAGEMENT_PORT__/actuator/health"));
        assertTrue(template.contains("proxy_buffering off"));
        assertTrue(template.contains("client_max_body_size 16m"));
        assertFalse(template.contains("listen 0.0.0.0"));

        assertTrue(activation.contains("ACTIVATE_AI_CHAT_PRODUCTION_NGINX"));
        assertTrue(activation.contains("AI-chat production must be healthy with zero restarts"));
        assertTrue(activation.contains("openssl x509 -in \"${cert_file}\" -noout -checkend 604800"));
        assertTrue(activation.contains("TLS certificate and private key do not match"));
        assertTrue(activation.contains("--resolve api.dduo.chat:443:127.0.0.1"));
        assertTrue(activation.contains("health_status"));
        assertTrue(activation.contains("https://api.dduo.chat/api/ai/models"));
        assertTrue(activation.contains("\"${protected_api_status}\" == 401"));
        assertFalse(activation.contains("https://api.dduo.chat/api/users/guest"));
        assertTrue(activation.contains("local deadline=$((SECONDS + 20))"));
        assertTrue(activation.contains("while (( SECONDS < deadline ))"));
        assertTrue(activation.contains("sleep 0.25"));
        assertTrue(activation.contains("dns_mutation=none"));

        assertTrue(rollback.contains("DNS_POINTS_AWAY_FROM_GCP"));
        assertTrue(rollback.contains("Public DNS still resolves to GCP"));
        assertTrue(rollback.contains("managed config was restored"));
    }

    @Test
    void shouldRequireARecoverableLegacyWriterBeforeProductionCutover() throws IOException {
        String control = Files.readString(rootFile("deploy/cloud-control-legacy-writer.sh"));
        String lifecycleTest = Files.readString(rootFile("deploy/tests/legacy-writer-lifecycle-test.sh"));

        assertTrue(control.contains("prepare <cutover-id>"));
        assertTrue(control.contains("STOP_LEGACY_WRITER"));
        assertTrue(control.contains("GCP_PRODUCTION_IS_STOPPED"));
        assertTrue(control.contains("/proc/${pid}/cmdline"));
        assertTrue(control.contains("/proc/${pid}/environ"));
        assertTrue(control.contains("Legacy JVM did not stop after SIGTERM; no SIGKILL was issued"));
        assertTrue(control.contains("Legacy application port changed after recovery preparation"));
        assertTrue(control.contains("OLD_PRODUCTION_WRITERS_STOPPED=true"));
        assertTrue(control.contains("LEGACY_WRITER_RESTORED=true"));
        assertTrue(lifecycleTest.contains("pid_drift_rejected=true"));
        assertTrue(lifecycleTest.contains("unconfirmed_stop_rejected=true"));
        assertTrue(lifecycleTest.contains("unconfirmed_restore_rejected=true"));
        assertTrue(lifecycleTest.contains("graceful_stop_and_restore=true"));
    }

    @Test
    void shouldKeepNoSchemaProductionUpgradeRollbackSafe() throws IOException {
        String upgrade = Files.readString(rootFile("deploy/cloud-upgrade-production.sh"));

        assertTrue(upgrade.contains("--confirm-no-schema-production-upgrade"));
        assertTrue(upgrade.contains("Database migrations changed; use the full maintenance-window cutover workflow"));
        assertTrue(upgrade.contains("--strip-trailing-cr"));
        assertTrue(upgrade.contains("Current production container must be healthy with zero restarts"));
        assertTrue(upgrade.contains("compose_for \"${release}\" build"));
        assertTrue(upgrade.contains("switch_link \"${previous_release}\""));
        assertTrue(upgrade.contains("--force-recreate"));
        assertTrue(upgrade.contains("database_migrations=unchanged"));
        assertTrue(upgrade.contains("nginx_mutation=none"));
        assertTrue(upgrade.contains("dns_mutation=none"));
    }

    private Path rootFile(String relativePath) {
        return Path.of(System.getProperty("project.root.dir"), relativePath);
    }
}
