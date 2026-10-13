package org.example.ai_agent.chat.repository;

import org.example.ai_agent.chat.entity.ChatTurnRow;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface ChatTurnRepository extends R2dbcRepository<ChatTurnRow, String> {

    @Modifying
    @Query("""
            UPDATE chat_turn SET retrieval_json = :retrievalJson
            WHERE id = :id AND status = 'STREAMING'
            """)
    Mono<Integer> storeRetrieval(String id, String retrievalJson);

    @Modifying
    @Query("INSERT INTO conversation_active_turn (conversation_id, turn_id) VALUES (:conversationId, :turnId)")
    Mono<Integer> acquireConversation(Long conversationId, String turnId);

    @Modifying
    @Query("DELETE FROM conversation_active_turn WHERE turn_id = :turnId")
    Mono<Integer> releaseConversation(String turnId);

    @Modifying
    @Query("""
            DELETE slot FROM conversation_active_turn slot
            LEFT JOIN chat_turn t ON t.id = slot.turn_id COLLATE utf8mb4_unicode_ci
            WHERE t.id IS NULL OR t.status NOT IN ('STARTED', 'STREAMING')
            """)
    Mono<Integer> releaseTerminalConversations();

    @Query("""
            SELECT * FROM chat_turn
            WHERE actor_type = :actorType
              AND actor_id = :actorId
              AND client_request_id = :clientRequestId
            """)
    Mono<ChatTurnRow> findByActorAndClientRequestId(String actorType, Long actorId, String clientRequestId);

    @Modifying
    @Query("""
            UPDATE chat_turn
            SET status = 'STREAMING', updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status = 'STARTED'
            """)
    Mono<Integer> markStreaming(String id);

    @Modifying
    @Query("""
            UPDATE chat_turn
            SET status = 'COMPLETED', assistant_message_id = :assistantMessageId,
                error_code = NULL, completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status = 'STREAMING'
            """)
    Mono<Integer> markCompleted(String id, Long assistantMessageId);

    @Modifying
    @Query("""
            UPDATE chat_turn
            SET status = 'FAILED', error_code = :errorCode,
                completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status IN ('STARTED', 'STREAMING')
            """)
    Mono<Integer> markFailed(String id, String errorCode);

    @Modifying
    @Query("""
            UPDATE chat_turn
            SET status = 'CANCELLED', error_code = 'CHAT_CANCELLED',
                completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = :id AND status IN ('STARTED', 'STREAMING')
            """)
    Mono<Integer> markCancelled(String id);

    @Modifying
    @Query("""
            UPDATE chat_turn
            SET status = 'FAILED', error_code = :errorCode,
                completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE status IN ('STARTED', 'STREAMING')
              AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL :staleSeconds SECOND)
            ORDER BY updated_at ASC
            LIMIT :batchSize
            """)
    Mono<Integer> failStaleActiveTurns(long staleSeconds, int batchSize, String errorCode);
}
