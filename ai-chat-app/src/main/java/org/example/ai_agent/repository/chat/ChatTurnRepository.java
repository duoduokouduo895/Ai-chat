package org.example.ai_agent.repository.chat;

import org.example.ai_agent.entity.ChatTurnRow;
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
}
