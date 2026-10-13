package org.example.ai_agent.chat.repository;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class ConversationSearchMySqlIntegrationTest {
    @Test
    @Timeout(60)
    void registeredConversationExistenceMapsMysqlNumericResultToBoolean() {
        String database = System.getenv("AI_CHAT_MYSQL_IT_DATABASE");
        if (database == null || !database.matches("ai_chat_it_[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("An isolated integration database is required");
        }
        String host = System.getenv("AI_CHAT_MYSQL_IT_HOST");
        int port = Integer.parseInt(System.getenv("AI_CHAT_MYSQL_IT_PORT"));
        String username = System.getenv("AI_CHAT_MYSQL_IT_USERNAME");
        String password = System.getenv("AI_CHAT_MYSQL_IT_PASSWORD");
        Flyway.configure().dataSource("jdbc:mysql://" + host + ":" + port + "/" + database,
                username, password).locations("classpath:db/migration").load().migrate();
        var factory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder()
                .host(host).port(port).database(database).username(username).password(password).build());
        var client = DatabaseClient.create(factory);
        var repository = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(factory))
                .getRepository(ConversationRepository.class);
        Duration timeout = Duration.ofSeconds(10);
        assertFalse(repository.existsRegisteredConversationById(1L).block(timeout));
        client.sql("INSERT INTO users (user_id, username, password) VALUES (1, 'search-it', 'unused')")
                .fetch().rowsUpdated().block(timeout);
        client.sql("INSERT INTO conversation (id, title, user_id) VALUES (1, 'search-it', 1)")
                .fetch().rowsUpdated().block(timeout);
        assertTrue(repository.existsRegisteredConversationById(1L).block(timeout));
        assertFalse(repository.existsRegisteredConversationById(2L).block(timeout));
    }
}
