package org.example.ai_agent.common.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ElasticsearchConfigTest {

    private ElasticsearchConfig config;

    @BeforeEach
    void setUp() {
        config = new ElasticsearchConfig();
        ReflectionTestUtils.setField(config, "elasticsearchUri", "http://elasticsearch-v9-candidate:9200");
        ReflectionTestUtils.setField(config, "connectionTimeout", 1_000);
        ReflectionTestUtils.setField(config, "socketTimeout", 2_000);
        ReflectionTestUtils.setField(config, "username", "");
        ReflectionTestUtils.setField(config, "password", "");
    }

    @Test
    void shouldBuildAuthenticatedClientConfiguration() {
        ReflectionTestUtils.setField(config, "username", "aichat_candidate");
        ReflectionTestUtils.setField(config, "password", "not-a-real-secret");

        assertDoesNotThrow(config::clientConfiguration);
    }

    @Test
    void shouldRejectPartialCredentials() {
        ReflectionTestUtils.setField(config, "username", "aichat_candidate");

        assertThrows(IllegalStateException.class, config::clientConfiguration);
    }

    @Test
    void shouldRejectCredentialsEmbeddedInUri() {
        ReflectionTestUtils.setField(config, "elasticsearchUri", "http://user:password@localhost:9200");

        assertThrows(IllegalStateException.class, config::clientConfiguration);
    }

    @Test
    void shouldRejectEndpointWithPathOrQuery() {
        ReflectionTestUtils.setField(config, "elasticsearchUri", "http://localhost:9200/private?pretty=true");

        assertThrows(IllegalStateException.class, config::clientConfiguration);
    }
}
