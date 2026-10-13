package org.example.ai_agent.integration;

import com.rabbitmq.client.ConnectionFactory;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(60)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_STACK_IT_ENABLED", matches = "true")
class ReliabilityStackIntegrationTest {

    @Test
    @Order(1)
    void mysqlRollbackDoesNotLeakPartialState() throws Exception {
        String database = required("AI_CHAT_STACK_MYSQL_DATABASE");
        assertTrue(database.matches("ai_chat_it_[A-Za-z0-9_]+"));
        String url = "jdbc:mysql://" + required("AI_CHAT_STACK_MYSQL_HOST") + ":"
                + required("AI_CHAT_STACK_MYSQL_PORT") + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (var connection = DriverManager.getConnection(url,
                required("AI_CHAT_STACK_MYSQL_USERNAME"), required("AI_CHAT_STACK_MYSQL_PASSWORD"));
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE reliability_probe (id VARCHAR(64) PRIMARY KEY, value_text VARCHAR(64))");
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO reliability_probe VALUES ('rollback', 'must disappear')");
            connection.rollback();
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM reliability_probe")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    @Order(2)
    void redisAclRestrictsWritesToThisRunPrefix() {
        String prefix = required("AI_CHAT_STACK_REDIS_PREFIX");
        assertTrue(prefix.matches("aichat:it:[A-Za-z0-9_]+:"));
        String uri = "redis://" + required("AI_CHAT_STACK_REDIS_USERNAME") + ":"
                + required("AI_CHAT_STACK_REDIS_PASSWORD") + "@"
                + required("AI_CHAT_STACK_REDIS_HOST") + ":" + required("AI_CHAT_STACK_REDIS_PORT");
        RedisClient client = RedisClient.create(uri);
        try (var connection = client.connect()) {
            var commands = connection.sync();
            assertEquals("OK", commands.set(prefix + "probe", "isolated"));
            assertEquals("isolated", commands.get(prefix + "probe"));
            assertThrows(RuntimeException.class, () -> commands.set("outside:" + UUID.randomUUID(), "blocked"));
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Order(3)
    void rabbitMqRecoversAfterAChannelLevelPublishFailure() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(required("AI_CHAT_STACK_RABBIT_HOST"));
        factory.setPort(Integer.parseInt(required("AI_CHAT_STACK_RABBIT_PORT")));
        factory.setUsername(required("AI_CHAT_STACK_RABBIT_USERNAME"));
        factory.setPassword(required("AI_CHAT_STACK_RABBIT_PASSWORD"));
        factory.setVirtualHost(required("AI_CHAT_STACK_RABBIT_VHOST"));
        factory.setConnectionTimeout(5000);
        String exchange = required("AI_CHAT_STACK_RABBIT_EXCHANGE");
        String queue = exchange + ".queue";

        try (var connection = factory.newConnection()) {
            var failingChannel = connection.createChannel();
            try {
                failingChannel.confirmSelect();
                failingChannel.basicPublish(exchange + ".missing", "probe", null,
                        "must fail".getBytes(StandardCharsets.UTF_8));
                assertThrows(Exception.class, () -> failingChannel.waitForConfirmsOrDie(3000));
            } finally {
                if (failingChannel.isOpen()) {
                    failingChannel.close();
                }
            }
            try (var recoveredChannel = connection.createChannel()) {
                recoveredChannel.exchangeDeclare(exchange, "direct", true);
                recoveredChannel.queueDeclare(queue, true, false, false, null);
                recoveredChannel.queueBind(queue, exchange, "probe");
                recoveredChannel.basicPublish(exchange, "probe", null,
                        "recovered".getBytes(StandardCharsets.UTF_8));
                var delivery = recoveredChannel.basicGet(queue, true);
                assertEquals("recovered", new String(delivery.getBody(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    @Order(4)
    void elasticsearchIndexCanBeRecreatedAfterAnIsolatedFailure() throws Exception {
        String index = required("AI_CHAT_STACK_ES_INDEX");
        assertTrue(index.matches("ai-chat-it-[a-z0-9_-]+"));
        HttpClient client = HttpClient.newBuilder().authenticator(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(required("AI_CHAT_STACK_ES_USERNAME"),
                        required("AI_CHAT_STACK_ES_PASSWORD").toCharArray());
            }
        }).build();
        String base = "http://" + required("AI_CHAT_STACK_ES_HOST") + ":" + required("AI_CHAT_STACK_ES_PORT");

        assertTrue(send(client, "PUT", base + "/" + index + "/_doc/probe?refresh=true",
                "{\"message\":\"before recovery\"}").statusCode() < 300);
        assertTrue(send(client, "DELETE", base + "/" + index, null).statusCode() < 300);
        assertEquals(404, send(client, "GET", base + "/" + index, null).statusCode());
        assertTrue(send(client, "PUT", base + "/" + index,
                "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}").statusCode() < 300);
        assertTrue(send(client, "PUT", base + "/" + index + "/_doc/probe?refresh=true",
                "{\"message\":\"after recovery\"}").statusCode() < 300);
        var search = send(client, "GET", base + "/" + index + "/_search?q=message:recovery", null);
        assertEquals(200, search.statusCode());
        assertFalse(search.body().isBlank());
    }

    private HttpResponse<String> send(HttpClient client, String method, String uri, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return client.send(HttpRequest.newBuilder(URI.create(uri))
                        .header("Content-Type", "application/json")
                        .method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration setting: " + name);
        }
        return value;
    }
}
