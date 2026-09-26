package org.example.ai_agent.service.memory;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.example.ai_agent.conversation.CompletedContextTurn;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real MySQL contract for versioned memory writes, full-text recall and archive eviction.
 * Opt-in like the other MySQL suites; the database name must start with {@code ai_chat_it_}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class ConversationMemoryStoreMySqlIntegrationTest {
    private DatabaseClient database;
    private ConversationMemoryStore store;
    private long conversationId;

    @BeforeAll
    void setUpDatabase() {
        String host = required("AI_CHAT_MYSQL_IT_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("AI_CHAT_MYSQL_IT_PORT", "3306"));
        String name = required("AI_CHAT_MYSQL_IT_DATABASE");
        if (!name.matches("ai_chat_it_[A-Za-z0-9_]+")) {
            throw new IllegalStateException("Integration test database must match ai_chat_it_[A-Za-z0-9_]+");
        }
        String user = required("AI_CHAT_MYSQL_IT_USERNAME");
        String password = required("AI_CHAT_MYSQL_IT_PASSWORD");
        Flyway.configure()
                .dataSource("jdbc:mysql://" + host + ":" + port + "/" + name
                        + "?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false", user, password)
                .locations("classpath:db/migration").encoding("UTF-8").outOfOrder(true).load().migrate();
        ConnectionFactory factory = MySqlConnectionFactory.from(MySqlConnectionConfiguration.builder()
                .host(host).port(port).database(name).username(user).password(password)
                .sessionVariables("character_set_client=utf8mb4", "character_set_connection=utf8mb4",
                        "character_set_results=utf8mb4")
                .build());
        database = DatabaseClient.create(factory);
        store = new ConversationMemoryStore(database,
                TransactionalOperator.create(new R2dbcTransactionManager(factory)));
    }

    @BeforeEach
    void createConversation() {
        conversationId = database.sql("INSERT INTO conversation (title, user_id) VALUES ('memory-it', 9200001)")
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map((row, meta) -> row.get("id", Long.class)).one().block();
    }

    @Test
    void commitPublishesOneVersionAndRejectsAStaleLease() {
        store.requestCompaction(conversationId, "chat").block();
        var lease = store.claim(conversationId, Duration.ofMinutes(5)).block();
        assertNotNull(lease);
        assertNull(store.claim(conversationId, Duration.ofMinutes(5)).block(), "an active lease is exclusive");

        var turns = List.of(turn(101, "服务端口定为 8443，数据库选 PostgreSQL", "好的"), turn(102, "再确认一下超时时间 30 秒", "已确认"));
        var result = new CompactionResult("端口 8443；PostgreSQL；超时 30 秒。", 20,
                List.of(new CompactionResult.NewConstraint("DECISION", "数据库使用 PostgreSQL", 101)), List.of());
        assertTrue(store.commit(lease, result, turns, 102, false, true, 5).block());
        assertFalse(store.commit(lease, result, turns, 102, false, true, 5).block(), "the lease was consumed");

        var memory = store.load(conversationId).block();
        assertEquals(1, memory.version());
        assertEquals(102, memory.coveredThroughSeq());
        assertEquals("端口 8443；PostgreSQL；超时 30 秒。", memory.summary());
        assertEquals(List.of("数据库使用 PostgreSQL"),
                memory.constraints().stream().map(ConversationMemory.MemoryConstraint::content).toList());

        var hits = store.search(conversationId, "端口", 3).collectList().block();
        assertEquals(101, hits.getFirst().seq());

        store.requestCompaction(conversationId, "chat").block();
        var next = store.claim(conversationId, Duration.ofMinutes(5)).block();
        var revoke = new CompactionResult("改用 MySQL。", 5, List.of(),
                List.of(memory.constraints().getFirst().id()));
        assertTrue(store.commit(next, revoke, List.of(), 103, false, true, 5).block());
        assertTrue(store.load(conversationId).block().constraints().isEmpty());
    }

    @Test
    void evictsOldestArchiveUntilUnderTheCapAndDeletesWithTheConversation() {
        store.requestCompaction(conversationId, "chat").block();
        var lease = store.claim(conversationId, Duration.ofMinutes(5)).block();
        var turns = List.of(turn(201, "a".repeat(1000), "b"), turn(202, "c".repeat(1000), "d"));
        store.commit(lease, new CompactionResult("s", 1, List.of(), List.of()), turns, 202, false, true, 5).block();

        long before = sumArchiveBytes();
        long freed = store.evictOldestArchive(before - 1, 1).block();

        assertTrue(freed > 0, "the globally oldest archived turn is evicted");
        assertEquals(before - freed, sumArchiveBytes());
        assertEquals(0L, store.evictOldestArchive(Long.MAX_VALUE, 1).block(), "nothing is evicted under the cap");
        database.sql("DELETE FROM conversation WHERE id = :id").bind("id", conversationId).fetch().rowsUpdated().block();
        assertEquals(0L, database.sql("SELECT COUNT(*) FROM conversation_archive_turn WHERE conversation_id = :id")
                .bind("id", conversationId).map((row, meta) -> row.get(0, Long.class)).one().block());
    }

    private long sumArchiveBytes() {
        return database.sql("SELECT COALESCE(SUM(archive_bytes), 0) FROM conversation_memory")
                .map((row, meta) -> row.get(0, Number.class).longValue()).one().block();
    }

    private static CompletedContextTurn turn(long seq, String user, String assistant) {
        return new CompletedContextTurn("t" + seq, user, assistant, LocalDateTime.now(), seq);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing environment variable: " + name);
        return value;
    }
}
