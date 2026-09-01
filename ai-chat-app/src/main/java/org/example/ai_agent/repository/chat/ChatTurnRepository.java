package org.example.ai_agent.repository.chat;

import org.example.ai_agent.entity.ChatTurnRow;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface ChatTurnRepository extends R2dbcRepository<ChatTurnRow, String> {

    @Query("""
            SELECT * FROM chat_turn
            WHERE actor_type = :actorType
              AND actor_id = :actorId
              AND conversation_id = :conversationId
              AND client_request_id = :clientRequestId
            """)
    Mono<ChatTurnRow> findByActorAndConversationAndClientRequestId(String actorType, Long actorId,
                                                                     Long conversationId, String clientRequestId);

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
}
