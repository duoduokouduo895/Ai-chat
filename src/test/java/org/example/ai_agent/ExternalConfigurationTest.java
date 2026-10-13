package org.example.ai_agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Boots only Spring's config data loading against the packaged application.properties. */
class ExternalConfigurationTest {

    @Test
    void externalFileOverridesPackagedDefaultsAndSecretsLoadOneKeyPerFile(@TempDir Path dir) throws IOException {
        Path config = Files.createDirectories(dir.resolve("config"));
        Path secrets = Files.createDirectories(dir.resolve("secrets"));
        Files.writeString(config.resolve("application.properties"),
                "spring.r2dbc.url=r2dbc:mysql://mysql:3306/ai_chat\n"
                        + "knowledge-base.rag.vector-top-k=7\n"
                        + "external.config.encoding-probe=\\u4e2d\\u6587\\u914d\\u7f6e\n");
        Files.writeString(secrets.resolve("spring.r2dbc.password"), "中文密钥");

        try (ConfigurableApplicationContext context = boot(config, secrets)) {
            Environment environment = context.getEnvironment();
            assertEquals("r2dbc:mysql://mysql:3306/ai_chat", environment.getProperty("spring.r2dbc.url"));
            assertEquals("7", environment.getProperty("knowledge-base.rag.vector-top-k"));
            assertEquals("中文配置", environment.getProperty("external.config.encoding-probe"));
            assertEquals("中文密钥", environment.getProperty("spring.r2dbc.password"));
            assertEquals("prod", String.join(",", environment.getActiveProfiles()));
        }
    }

    @Test
    void missingDirectoriesFallBackToPackagedDefaults(@TempDir Path dir) {
        try (ConfigurableApplicationContext context = boot(dir.resolve("absent"), dir.resolve("absent-secrets"))) {
            Environment environment = context.getEnvironment();
            assertEquals("20", environment.getProperty("knowledge-base.rag.vector-top-k"));
            assertNull(environment.getProperty("spring.r2dbc.password"));
        }
    }

    @Test
    void productionImportRejectsMissingConfigFileOrSecretTree(@TempDir Path dir) throws IOException {
        Path config = Files.createDirectories(dir.resolve("config"));
        Path secrets = dir.resolve("secrets");
        assertThrows(RuntimeException.class, () -> bootRequired(config, secrets));

        Files.writeString(config.resolve("application.properties"), "external.config.ready=true\n");
        assertThrows(RuntimeException.class, () -> bootRequired(config, secrets));
    }

    private ConfigurableApplicationContext boot(Path config, Path secrets) {
        return new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run("--AICHAT_CONFIG_DIR=" + slashes(config), "--AICHAT_CONFIG_SECRETS_DIR=" + slashes(secrets),
                        "--spring.main.banner-mode=off");
    }

    private ConfigurableApplicationContext bootRequired(Path config, Path secrets) {
        return new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run("--spring.config.import=file:" + slashes(config.resolve("application.properties"))
                                + ",configtree:" + slashes(secrets) + "/",
                        "--spring.main.banner-mode=off");
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
