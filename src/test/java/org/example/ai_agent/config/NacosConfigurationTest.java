package org.example.ai_agent.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosConfigurationTest {

    @Test
    void defaultConfiguration_shouldUseCloudNacosProfileWithoutLocalFallback() throws IOException {
        Properties defaults = load("application.properties");

        assertEquals("prod,nacos", defaults.getProperty("spring.profiles.active"));
        assertNull(defaults.getProperty("spring.cloud.nacos.config.enabled"));
        assertNull(defaults.getProperty("spring.cloud.nacos.discovery.enabled"));
        assertNull(defaults.getProperty("spring.config.import"));
        assertNull(resource("bootstrap.properties"));
    }

    @Test
    void nacosProfile_shouldExplicitlyEnableRemoteConfiguration() throws IOException {
        Properties nacos = load("application-nacos.properties");

        assertEquals("true", nacos.getProperty("spring.cloud.nacos.config.enabled"));
        assertEquals("true", nacos.getProperty("spring.cloud.nacos.discovery.enabled"));
        assertEquals("${NACOS_SERVER_ADDR:34.150.80.173:8848}",
                nacos.getProperty("spring.cloud.nacos.config.server-addr"));
        assertEquals("${NACOS_SERVER_ADDR:34.150.80.173:8848}",
                nacos.getProperty("spring.cloud.nacos.discovery.server-addr"));
        assertEquals("${NACOS_CONFIG_TIMEOUT_MS:3000}",
                nacos.getProperty("spring.cloud.nacos.config.timeout"));
        assertEquals("${NACOS_CONFIG_MAX_RETRY:3}",
                nacos.getProperty("spring.cloud.nacos.config.max-retry"));
        String configImport = nacos.getProperty("spring.config.import");
        assertTrue(configImport.contains("nacos:databaseR2DBC-config.properties"));
        assertFalse(configImport.contains("optional:nacos:"));
        assertFalse(configImport.contains("optional:file:./DEFAULT_GROUP/"));
    }

    private Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = resource(name)) {
            assertNotNull(inputStream, name + " 应存在");
            properties.load(inputStream);
        }
        return properties;
    }

    private InputStream resource(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }
}
