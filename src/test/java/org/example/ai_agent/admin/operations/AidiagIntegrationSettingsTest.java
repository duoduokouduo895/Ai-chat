package org.example.ai_agent.admin.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AidiagIntegrationSettingsTest {

    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void enablesOnlyWithAValidServerSideSecret() throws Exception {
        var secret = tempDir.resolve("reader-secret");
        Files.writeString(secret, "0123456789abcdef0123456789abcdef");

        var settings = new AidiagIntegrationSettings(true, "http://aidiag-backend:8080/", "reader", secret.toString(),
                "ai-chat-candidate", Duration.ofSeconds(3));

        assertThat(settings.available()).isTrue();
        assertThat(settings.baseUrl()).isEqualTo("http://aidiag-backend:8080");
        assertThat(settings.password()).hasSize(32);
    }

    @Test
    void missingSecretDisablesOnlyTheIntegration() {
        var settings = new AidiagIntegrationSettings(true, "not-a-url", "reader", tempDir.resolve("missing").toString(),
                "invalid service", Duration.ofSeconds(30));

        assertThat(settings.available()).isFalse();
        assertThat(settings.baseUrl()).isEqualTo("http://127.0.0.1");
    }
}
