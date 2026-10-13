package org.example.ai_agent.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlywayConfigConditionTest {

    @Test
    void passiveCandidateCanDisableMigrationsWithoutDatabaseProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(FlywayConfig.class)
                .withPropertyValues("app.flyway.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Flyway.class));
    }
}
