package org.example.ai_agent.service.impl.chat;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.repository.chat.ChatTurnRepository;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.chat.RedisChatMemoryStore;
import org.example.ai_agent.service.impl.search.ConversationSearchIndexQueue;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.CacheManager;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Real MySQL/R2DBC contract tests for the ChatTurn request claim.
 *
 * <p>The suite is opt-in because it requires an isolated MySQL database. The
 * database name is rejected unless it starts with {@code ai_chat_it_}; this
 * prevents cleanup statements from ever targeting a production schema.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "AI_CHAT_MYSQL_IT_ENABLED", matches = "true")
class ChatTurnMySqlIntegrationTest {

    private DatabaseClient databaseClient;
    private ChatTurnLifecycle lifecycle;
    private MessageRepository contextMessages;
    private ConversationServiceImpl conversationService;
    private ConversationSearchIndexQueue searchIndexQueue;
    private CacheManager cacheManager;
    private TransactionalOperator transactionalOperator;

    @BeforeAll
    void setUpDatabase() {
        MySqlTestSettings settings = MySqlTestSettings.fromEnvironment();
        settings.requireIsolatedDatabase();

        Flyway.configure()
                .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .target("6")
                .outOfOrder(true)
                .load()
                .migrate();
        insertV6CompatibilityProbe(settings);

        Flyway.configure()
                .dataSource(settings.jdbcUrl(), settings.username(), settings.password())
                .locations("classpath:db/migration")
                .encoding("UTF-8")
                .outOfOrder(true)
                .load()
                .migrate();
        assertV7UpgradeContract(settings);

        ConnectionFactory connectionFactory = MySqlConnectionFactory.from(
                MySqlConnectionConfiguration.builder()
                        .host(settings.host())
                        .port(settings.port())
                        .database(settings.database())
                        .username(settings.username())
                        .password(settings.password())
                        .sessionVariables(
                                "character_set_client=utf8mb4",
                                "character_set_connection=utf8mb4",
                                "character_set_results=utf8mb4")
                        .build());
        databaseClient = DatabaseClient.create(connectionFactory);

        R2dbcEntityTemplate entityTemplate = new R2dbcEntityTemplate(connectionFactory);
        R2dbcRepositoryFactory repositoryFactory = new R2dbcRepositoryFactory(entityTemplate);
        ChatTurnRepository chatTurnRepository = repositoryFactory.getRepository(ChatTurnRepository.class);
        ConversationRepository conversationRepository = repositoryFactory.getRepository(ConversationRepository.class);
        MessageRepository messageRepository = repositoryFactory.getRepository(MessageRepository.class);
        contextMessages = messageRepository;

        searchIndexQueue = mock(ConversationSearchIndexQueue.class);
        cacheManager = mock(CacheManager.class);
        conversationService = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                searchIndexQueue,
                cacheManager,
                mock(RedisChatMemoryStore.class), new ChatRetrievalReader(databaseClient, new com.fasterxml.jackson.databind.ObjectMapper()));
        transactionalOperator = TransactionalOperator.create(
                new R2dbcTransactionManager(connectionFactory));
        lifecycle = new ChatTurnLifecycle(
                chatTurnRepository,
                messageRepository,
                new AssistantConversationTurn(conversationService),
                transactionalOperator);
    }

    private void insertV6CompatibilityProbe(MySqlTestSettings settings) {
        try (Connection connection = DriverManager.getConnection(
                settings.jdbcUrl(), settings.username(), settings.password());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO conversation (id, title, created_at, updated_at, user_id)
                    VALUES (9900001, 'V6 compatibility probe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 9100001)
                    """);
            statement.executeUpdate("""
                    INSERT INTO chat_turn (
                        id, actor_type, actor_id, conversation_id, client_request_id,
                        model_id, status, attempt, started_at, updated_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000006', 'USER', 9100001, 9900001,
                        'v6-compatibility-probe', 'mysql-it-model', 'STARTED', 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    """);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to insert the V6 compatibility probe", error);
        }
    }

    private void assertV7UpgradeContract(MySqlTestSettings settings) {
        try (Connection connection = DriverManager.getConnection(
                settings.jdbcUrl(), settings.username(), settings.password());
             Statement statement = connection.createStatement()) {
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM chat_turn
                    WHERE client_request_id = 'v6-compatibility-probe'
                      AND request_fingerprint IS NULL
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'chat_turn'
                      AND index_name = 'uk_chat_turn_actor_request'
                    """));
            assertEquals(1L, singleLong(statement, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = 'chat_turn'
                      AND column_name = 'conversation_id'
                      AND is_nullable = 'YES'
                    """));
        } catch (Exception error) {
            throw new IllegalStateException("V7 upgrade contract validation failed", error);
        }
    }

    private long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new IllegalStateException("Expected one aggregate row");
            }
            return resultSet.getLong(1);
        }
    }

    @BeforeEach
    void cleanBeforeTest() {
        reset(searchIndexQueue, cacheManager);
        cleanup().block();
    }

    @AfterAll
    void cleanAfterSuite() {
        if (databaseClient != null) {
            cleanup().block();
        }
    }

    @Test
    void retrievalHistoryRechecksPermissionAndCompletedContextWorksAcrossModels() {
        databaseClient.sql("INSERT INTO knowledge_bases (id, name, creator_id, status, is_public) VALUES (9900101, 'source test', 9100001, 'ACTIVE', FALSE)")
                .fetch().rowsUpdated()
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_documents (id, knowledge_base_id, title, file_name, file_type, file_size, oss_path, oss_url, uploader_id)
                        VALUES (9900102, 9900101, 'source title', 'source.txt', 'txt', 4, 'isolated/source.txt', 'https://example.invalid/source', 9100001)
                        """).fetch().rowsUpdated())
                .then(databaseClient.sql("INSERT INTO document_versions (id, document_id, generation, activation_status) VALUES (9900103, 9900102, 1, 'ACTIVE')").fetch().rowsUpdated())
                .then(databaseClient.sql("""
                        INSERT INTO knowledge_document_chunks (id, document_id, knowledge_base_id, version_id, generation, chunk_index, content)
                        VALUES (9900104, 9900102, 9900101, 9900103, 1, 0, 'private evidence')
                        """).fetch().rowsUpdated()).block();
        var request = request("sources-" + UUID.randomUUID(), "original question");
        var match = new org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto.KnowledgeSearchMatch();
        match.setKnowledgeBaseId(9900101L); match.setDocumentId(9900102L);
        match.setVersionId(9900103L); match.setChunkId(9900104L); match.setContent("private evidence");
        request.setRetrievalContext(new org.example.ai_agent.service.RagService.RetrievalContext(
                "original question", List.of(match), "private evidence", 1, 1L));
        var claim = lifecycle.claim(request).flatMap(lifecycle::markStreaming).block();
        lifecycle.completeWithAiMessage(claim, request, "final answer").block();
        var message = conversationService.getMessagesByConversationId(request.getConversationId(), 9100001L)
                .filter(value -> "ai".equals(value.getSender())).single().block();
        assertTrue(((ChatRetrievalReader.View) message.getRetrieval()).sources().getFirst().available());
        databaseClient.sql("UPDATE knowledge_bases SET creator_id = 9100002 WHERE id = 9900101").fetch().rowsUpdated().block();
        var refreshed = conversationService.getMessagesByConversationId(request.getConversationId(), 9100001L)
                .filter(value -> "ai".equals(value.getSender())).single().block();
        var unavailable = ((ChatRetrievalReader.View) refreshed.getRetrieval()).sources().getFirst();
        assertFalse(unavailable.available());
        assertEquals(null, unavailable.documentTitle());
        var next = request("cross-model-" + UUID.randomUUID(), "next question");
        next.setConversationId(request.getConversationId()); next.setModel("different-model");
        new ConversationContextLoader(contextMessages, new org.example.ai_agent.config.ai.ModelCatalogProperties())
                .prepare(next, "next question").block();
        assertEquals(1, next.getContextTurns().size());
        assertEquals("original question", next.getContextTurns().getFirst().userText());
        assertEquals("final answer", next.getContextTurns().getFirst().assistantText());
    }

    @Test
    void concurrentNewConversationClaimsCreateOneTurnOneConversationAndOneUserMessage() {
        String clientRequestId = "it-" + UUID.randomUUID();
        ChatRequest firstRequest = request(clientRequestId, "并发请求");
        ChatRequest secondRequest = request(clientRequestId, "并发请求");

        Mono<List<ChatTurnLifecycle.Claim>> concurrentClaims = Flux.merge(
                        lifecycle.claim(firstRequest).subscribeOn(Schedulers.parallel()),
                        lifecycle.claim(secondRequest).subscribeOn(Schedulers.parallel()))
                .collectList();

        StepVerifier.create(concurrentClaims)
                .assertNext(claims -> {
                    assertEquals(2, claims.size());
                    assertEquals(1, claims.stream().filter(ChatTurnLifecycle.Claim::isOwned).count());
                    assertEquals(1, claims.stream()
                            .filter(claim -> claim.kind() == ChatTurnLifecycle.ClaimKind.IN_PROGRESS)
                            .count());
                    assertNotNull(firstRequest.getConversationId());
                    assertEquals(firstRequest.getConversationId(), secondRequest.getConversationId());
                    assertEquals(firstRequest.getConversationId(), claims.getFirst().row().getConversationId());
                    assertEquals(firstRequest.getConversationId(), claims.getLast().row().getConversationId());
                })
                .verifyComplete();

        assertEquals(1L, count("SELECT COUNT(*) FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block());
        assertEquals(1L, count("SELECT COUNT(*) FROM conversation WHERE user_id = 9100001", null).block());
        assertEquals(1L, count("SELECT COUNT(*) FROM message WHERE sender = 'user'", null).block());
    }

    @Test
    void failedUserMessageRollsBackRequestClaimAndNewConversation() {
        String clientRequestId = "it-rollback-" + UUID.randomUUID();

        StepVerifier.create(lifecycle.claim(request(clientRequestId, "   ")))
                .expectError(IllegalArgumentException.class)
                .verify();

        assertEquals(0L, count("SELECT COUNT(*) FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block());
        assertEquals(0L, count("SELECT COUNT(*) FROM conversation WHERE user_id = 9100001", null).block());
        assertEquals(0L, count("SELECT COUNT(*) FROM message", null).block());
    }

    @Test
    void reusedClientRequestIdWithDifferentPromptIsRejectedWithoutCreatingAnotherConversation() {
        String clientRequestId = "it-conflict-" + UUID.randomUUID();
        ChatRequest firstRequest = request(clientRequestId, "问题 A");

        StepVerifier.create(lifecycle.claim(firstRequest))
                .assertNext(claim -> assertEquals(ChatTurnLifecycle.ClaimKind.OWNED, claim.kind()))
                .verifyComplete();

        ChatRequest conflictingRequest = request(clientRequestId, "问题 B");
        StepVerifier.create(lifecycle.claim(conflictingRequest))
                .expectErrorSatisfies(error -> {
                    assertEquals(ChatStreamClientException.class, error.getClass());
                    assertEquals("CHAT_REQUEST_CONFLICT", ((ChatStreamClientException) error).getErrorCode());
                })
                .verify();

        assertEquals(1L, count("SELECT COUNT(*) FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block());
        assertEquals(1L, count("SELECT COUNT(*) FROM conversation WHERE user_id = 9100001", null).block());
        assertEquals(1L, count("SELECT COUNT(*) FROM message WHERE sender = 'user'", null).block());
    }

    @Test
    void assistantMessageAndCompletedTurnCommitAtomicallyAndCanBeReplayed() {
        String clientRequestId = "it-complete-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "需要完整回答的问题");

        ChatTurnLifecycle.Claim owned = lifecycle.claim(request).block();
        assertNotNull(owned);

        StepVerifier.create(lifecycle.markStreaming(owned)
                        .flatMap(claim -> lifecycle.completeWithAiMessage(claim, request, "完整回答")))
                .assertNext(message -> {
                    assertNotNull(message.getId());
                    assertEquals("完整回答", message.getContent());
                })
                .verifyComplete();

        StepVerifier.create(lifecycle.completeWithAiMessage(owned, request, "完整回答"))
                .assertNext(message -> assertNotNull(message.getId()))
                .verifyComplete();

        assertEquals(1L, count("""
                SELECT COUNT(*)
                FROM chat_turn turn_row
                INNER JOIN message assistant_message
                    ON assistant_message.id = turn_row.assistant_message_id
                WHERE turn_row.client_request_id = :requestId
                  AND turn_row.status = 'COMPLETED'
                  AND assistant_message.sender = 'ai'
                  AND assistant_message.content = '完整回答'
                """, clientRequestId).block());

        ChatRequest retry = request(clientRequestId, "需要完整回答的问题");
        StepVerifier.create(lifecycle.claim(retry))
                .assertNext(claim -> {
                    assertEquals(ChatTurnLifecycle.ClaimKind.REPLAY, claim.kind());
                    assertEquals("完整回答", claim.assistantMessage().getContent());
                })
                .verifyComplete();
    }

    @Test
    void rejectedCompletionRollsBackAssistantMessageBeforeTurnIsFailed() {
        String clientRequestId = "it-complete-rollback-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "触发完成回滚");
        ChatTurnLifecycle.Claim owned = lifecycle.claim(request).block();
        assertNotNull(owned);

        RuntimeException completionFailure = new RuntimeException("completion rejected");
        StepVerifier.create(lifecycle.completeWithAiMessage(owned, request, "不应残留的回答")
                        .onErrorResume(error -> lifecycle.markFailed(owned, completionFailure)
                                .then(Mono.error(error))))
                .expectErrorSatisfies(error -> assertEquals(
                        "ChatTurn 完成状态迁移被拒绝", error.getMessage()))
                .verify();

        assertEquals(0L, count("SELECT COUNT(*) FROM message WHERE sender = 'ai'", null).block());
        assertEquals(1L, count("""
                SELECT COUNT(*) FROM chat_turn
                WHERE client_request_id = :requestId
                  AND status = 'FAILED'
                  AND assistant_message_id IS NULL
                """, clientRequestId).block());
    }

    @Test
    void completionAndCancellationRaceProduceExactlyOneConsistentTerminalState() {
        String clientRequestId = "it-complete-cancel-race-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "完成与取消竞争");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request)
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(claim);

        Mono<Signal<org.example.ai_agent.entity.Message>> completion = lifecycle
                .completeWithAiMessage(claim, request, "竞争回答")
                .materialize()
                .subscribeOn(Schedulers.parallel());
        Mono<Signal<Void>> cancellation = lifecycle.markCancelled(claim)
                .materialize()
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(Mono.zip(completion, cancellation))
                .assertNext(signals -> {
                    int successfulTransitions = (signals.getT1().isOnNext() ? 1 : 0)
                            + (signals.getT2().isOnComplete() ? 1 : 0);
                    assertEquals(1, successfulTransitions);
                    assertTrue(signals.getT1().isOnError() || signals.getT2().isOnError());
                })
                .verifyComplete();

        String finalStatus = text("SELECT status FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block();
        assertTrue("COMPLETED".equals(finalStatus) || "CANCELLED".equals(finalStatus));
        if ("COMPLETED".equals(finalStatus)) {
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM chat_turn turn_row
                    INNER JOIN message assistant_message ON assistant_message.id = turn_row.assistant_message_id
                    WHERE turn_row.client_request_id = :requestId
                      AND assistant_message.sender = 'ai'
                      AND turn_row.error_code IS NULL
                      AND turn_row.completed_at IS NOT NULL
                      AND turn_row.updated_at >= turn_row.started_at
                    """, clientRequestId).block());
        } else {
            assertEquals(0L, count("SELECT COUNT(*) FROM message WHERE sender = 'ai'", null).block());
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM chat_turn
                    WHERE client_request_id = :requestId
                      AND assistant_message_id IS NULL
                      AND error_code = 'CHAT_CANCELLED'
                      AND completed_at IS NOT NULL
                      AND updated_at >= started_at
                    """, clientRequestId).block());
        }
    }

    @Test
    void completionAndFailureRaceProduceExactlyOneConsistentTerminalState() {
        String clientRequestId = "it-complete-fail-race-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "完成与失败竞争");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request)
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(claim);

        Mono<Signal<org.example.ai_agent.entity.Message>> completion = lifecycle
                .completeWithAiMessage(claim, request, "竞争回答")
                .materialize()
                .subscribeOn(Schedulers.parallel());
        Mono<Signal<Void>> failure = lifecycle.markFailed(claim, new RuntimeException("model failed"))
                .materialize()
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(Mono.zip(completion, failure))
                .assertNext(signals -> assertEquals(1,
                        (signals.getT1().isOnNext() ? 1 : 0)
                                + (signals.getT2().isOnComplete() ? 1 : 0)))
                .verifyComplete();

        String finalStatus = text("SELECT status FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block();
        if ("COMPLETED".equals(finalStatus)) {
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM chat_turn turn_row
                    INNER JOIN message assistant_message ON assistant_message.id = turn_row.assistant_message_id
                    WHERE turn_row.client_request_id = :requestId
                      AND turn_row.error_code IS NULL
                      AND turn_row.completed_at IS NOT NULL
                    """, clientRequestId).block());
        } else {
            assertEquals("FAILED", finalStatus);
            assertEquals(0L, count("SELECT COUNT(*) FROM message WHERE sender = 'ai'", null).block());
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM chat_turn
                    WHERE client_request_id = :requestId
                      AND assistant_message_id IS NULL
                      AND error_code = 'CHAT_ERROR'
                      AND completed_at IS NOT NULL
                    """, clientRequestId).block());
        }
    }

    @Test
    void failureAndCancellationRaceProduceExactlyOneTerminalState() {
        String clientRequestId = "it-fail-cancel-race-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "失败与取消竞争");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request)
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(claim);

        Mono<Signal<Void>> failure = lifecycle.markFailed(claim, new RuntimeException("model failed"))
                .materialize()
                .subscribeOn(Schedulers.parallel());
        Mono<Signal<Void>> cancellation = lifecycle.markCancelled(claim)
                .materialize()
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(Mono.zip(failure, cancellation))
                .assertNext(signals -> {
                    int successfulTransitions = (signals.getT1().isOnComplete() ? 1 : 0)
                            + (signals.getT2().isOnComplete() ? 1 : 0);
                    assertEquals(1, successfulTransitions);
                    assertTrue(signals.getT1().isOnError() || signals.getT2().isOnError());
                })
                .verifyComplete();

        assertEquals(1L, count("""
                SELECT COUNT(*) FROM chat_turn
                WHERE client_request_id = :requestId
                  AND ((status = 'FAILED' AND error_code = 'CHAT_ERROR')
                    OR (status = 'CANCELLED' AND error_code = 'CHAT_CANCELLED'))
                """, clientRequestId).block());
    }

    @Test
    void staleRecoveryTerminatesOnlyExpiredActiveTurns() {
        ChatTurnLifecycle.Claim staleStarted = lifecycle.claim(request(
                "it-stale-started-" + UUID.randomUUID(), "过期 STARTED")).block();
        ChatTurnLifecycle.Claim staleStreaming = lifecycle.claim(request(
                "it-stale-streaming-" + UUID.randomUUID(), "过期 STREAMING"))
                .flatMap(lifecycle::markStreaming)
                .block();
        ChatTurnLifecycle.Claim recentStreaming = lifecycle.claim(request(
                "it-recent-streaming-" + UUID.randomUUID(), "当前 STREAMING"))
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(staleStarted);
        assertNotNull(staleStreaming);
        assertNotNull(recentStreaming);

        ageTurn(staleStarted.turnId()).block();
        ageTurn(staleStreaming.turnId()).block();

        StepVerifier.create(lifecycle.recoverStaleTurns(java.time.Duration.ofMinutes(10), 200))
                .expectNext(2)
                .verifyComplete();

        assertEquals(2L, count("""
                SELECT COUNT(*) FROM chat_turn
                WHERE status = 'FAILED' AND error_code = 'CHAT_STALE_TIMEOUT'
                """, null).block());
        assertEquals(1L, count("""
                SELECT COUNT(*) FROM chat_turn
                WHERE id = :requestId AND status = 'STREAMING'
                """, recentStreaming.turnId()).block());
    }

    @Test
    void staleRecoveryAndCompletionRacePreserveExactlyOneTerminalFact() {
        String clientRequestId = "it-stale-complete-race-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "过期恢复与完成竞争");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request)
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(claim);
        ageTurn(claim.turnId()).block();

        Mono<Signal<org.example.ai_agent.entity.Message>> completion = lifecycle
                .completeWithAiMessage(claim, request, "竞争回答")
                .materialize()
                .subscribeOn(Schedulers.parallel());
        Mono<Integer> recovery = lifecycle.recoverStaleTurns(java.time.Duration.ofMinutes(10), 200)
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(Mono.zip(completion, recovery))
                .assertNext(result -> assertEquals(1,
                        (result.getT1().isOnNext() ? 1 : 0) + result.getT2()))
                .verifyComplete();

        String finalStatus = text("SELECT status FROM chat_turn WHERE client_request_id = :requestId",
                clientRequestId).block();
        if ("COMPLETED".equals(finalStatus)) {
            assertEquals(1L, count("SELECT COUNT(*) FROM message WHERE sender = 'ai'", null).block());
        } else {
            assertEquals("FAILED", finalStatus);
            assertEquals(0L, count("SELECT COUNT(*) FROM message WHERE sender = 'ai'", null).block());
            assertEquals(1L, count("""
                    SELECT COUNT(*) FROM chat_turn
                    WHERE client_request_id = :requestId
                      AND assistant_message_id IS NULL
                      AND error_code = 'CHAT_STALE_TIMEOUT'
                    """, clientRequestId).block());
        }
    }

    @Test
    void staleRecoveryAndCancellationRacePreserveExactlyOneTerminalFact() {
        String clientRequestId = "it-stale-cancel-race-" + UUID.randomUUID();
        ChatRequest request = request(clientRequestId, "过期恢复与取消竞争");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request)
                .flatMap(lifecycle::markStreaming)
                .block();
        assertNotNull(claim);
        ageTurn(claim.turnId()).block();

        Mono<Signal<Void>> cancellation = lifecycle.markCancelled(claim)
                .materialize()
                .subscribeOn(Schedulers.parallel());
        Mono<Integer> recovery = lifecycle.recoverStaleTurns(java.time.Duration.ofMinutes(10), 200)
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(Mono.zip(cancellation, recovery))
                .assertNext(result -> assertEquals(1,
                        (result.getT1().isOnComplete() ? 1 : 0) + result.getT2()))
                .verifyComplete();

        assertEquals(1L, count("""
                SELECT COUNT(*) FROM chat_turn
                WHERE client_request_id = :requestId
                  AND assistant_message_id IS NULL
                  AND completed_at IS NOT NULL
                  AND error_code IN ('CHAT_CANCELLED', 'CHAT_STALE_TIMEOUT')
                """, clientRequestId).block());
    }

    @Test
    void rolledBackMessageDoesNotEnqueueProjectionButCommittedMessageDoes() {
        ChatRequest request = request("it-projection-" + UUID.randomUUID(), "建立会话");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request).block();
        assertNotNull(claim);
        reset(searchIndexQueue);

        org.example.ai_agent.entity.Message rolledBackMessage = message(
                request.getConversationId(), "projection-rollback-" + UUID.randomUUID());
        RuntimeException rollback = new RuntimeException("force rollback");
        StepVerifier.create(transactionalOperator.transactional(
                        conversationService.addMessageToConversation(rolledBackMessage)
                                .then(Mono.error(rollback))))
                .expectErrorSatisfies(error -> assertEquals(rollback, error))
                .verify();

        verify(searchIndexQueue, never()).enqueueUpsert(request.getConversationId());
        assertEquals(0L, count("""
                SELECT COUNT(*) FROM message WHERE deduplication_key = :requestId
                """, rolledBackMessage.getDeduplicationKey()).block());

        org.example.ai_agent.entity.Message committedMessage = message(
                request.getConversationId(), "projection-commit-" + UUID.randomUUID());
        StepVerifier.create(transactionalOperator.transactional(
                        conversationService.addMessageToConversation(committedMessage)))
                .assertNext(saved -> assertNotNull(saved.getId()))
                .verifyComplete();

        verify(searchIndexQueue).enqueueUpsert(request.getConversationId());
        assertEquals(1L, count("""
                SELECT COUNT(*) FROM message WHERE deduplication_key = :requestId
                """, committedMessage.getDeduplicationKey()).block());
    }

    @Test
    void derivedAdapterFailuresDoNotReverseACommittedMessageFact() {
        ChatRequest request = request("it-derived-failure-" + UUID.randomUUID(), "建立会话");
        ChatTurnLifecycle.Claim claim = lifecycle.claim(request).block();
        assertNotNull(claim);
        reset(searchIndexQueue, cacheManager);
        doThrow(new IllegalStateException("cache unavailable"))
                .when(cacheManager).getCache("conversations");
        doThrow(new IllegalStateException("projection queue unavailable"))
                .when(searchIndexQueue).enqueueUpsert(request.getConversationId());

        org.example.ai_agent.entity.Message committedMessage = message(
                request.getConversationId(), "derived-failure-" + UUID.randomUUID());
        StepVerifier.create(transactionalOperator.transactional(
                        conversationService.addMessageToConversation(committedMessage)))
                .assertNext(saved -> assertNotNull(saved.getId()))
                .verifyComplete();

        verify(cacheManager).getCache("conversations");
        verify(searchIndexQueue).enqueueUpsert(request.getConversationId());
        assertEquals(1L, count("""
                SELECT COUNT(*) FROM message WHERE deduplication_key = :requestId
                """, committedMessage.getDeduplicationKey()).block());
    }

    private ChatRequest request(String clientRequestId, String prompt) {
        return ChatRequest.builder()
                .userId(9100001L)
                .conversationId(null)
                .prompt(prompt)
                .model("mysql-it-model")
                .clientMessageId(clientRequestId)
                .actorType("USER")
                .build();
    }

    private Mono<Long> count(String sql, String requestId) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        if (requestId != null) {
            spec = spec.bind("requestId", requestId);
        }
        return spec.map((row, metadata) -> row.get(0, Long.class)).one();
    }

    private Mono<String> text(String sql, String requestId) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        if (requestId != null) {
            spec = spec.bind("requestId", requestId);
        }
        return spec.map((row, metadata) -> row.get(0, String.class)).one();
    }

    private Mono<Void> ageTurn(String turnId) {
        return databaseClient.sql("""
                        UPDATE chat_turn SET updated_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE)
                        WHERE id = :turnId
                        """)
                .bind("turnId", turnId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private org.example.ai_agent.entity.Message message(Long conversationId, String deduplicationKey) {
        org.example.ai_agent.entity.Message message = new org.example.ai_agent.entity.Message();
        message.setConversationId(conversationId);
        message.setSender("ai");
        message.setContent("投影事务测试");
        message.setModel("mysql-it-model");
        message.setDeduplicationKey(deduplicationKey);
        return message;
    }

    private Mono<Void> cleanup() {
        return databaseClient.sql("DELETE FROM conversation_active_turn").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM chat_turn").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM knowledge_bases WHERE id = 9900101").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM message").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM conversation").fetch().rowsUpdated())
                .then();
    }

    private record MySqlTestSettings(String host, int port, String database, String username, String password) {

        static MySqlTestSettings fromEnvironment() {
            return new MySqlTestSettings(
                    required("AI_CHAT_MYSQL_IT_HOST"),
                    Integer.parseInt(optional("AI_CHAT_MYSQL_IT_PORT", "3306")),
                    required("AI_CHAT_MYSQL_IT_DATABASE"),
                    required("AI_CHAT_MYSQL_IT_USERNAME"),
                    required("AI_CHAT_MYSQL_IT_PASSWORD"));
        }

        void requireIsolatedDatabase() {
            if (!database.matches("ai_chat_it_[A-Za-z0-9_]+")) {
                throw new IllegalStateException(
                        "Integration test database must match ai_chat_it_[A-Za-z0-9_]+");
            }
        }

        String jdbcUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing environment variable: " + name);
            }
            return value;
        }

        private static String optional(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }
}
