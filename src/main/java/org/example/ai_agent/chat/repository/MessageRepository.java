package org.example.ai_agent.chat.repository;

import org.example.ai_agent.chat.entity.Message;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageRepository extends R2dbcRepository<Message, Long> {
    @org.springframework.data.r2dbc.repository.Query("""
            SELECT t.id AS turn_id, u.content AS user_text, a.content AS assistant_text,
                   a.timestamp AS completed_at, a.id AS sequence_id
            FROM chat_turn t JOIN message u ON u.id = t.user_message_id
            JOIN message a ON a.id = t.assistant_message_id
            WHERE t.conversation_id = :conversationId AND t.status = 'COMPLETED'
            ORDER BY a.timestamp DESC, a.id DESC LIMIT 100
            """)
    Flux<org.example.ai_agent.chat.domain.CompletedContextTurn> findCompletedContext(Long conversationId);

    /** Completed turns after a memory watermark, oldest first, for compaction. */
    @org.springframework.data.r2dbc.repository.Query("""
            SELECT t.id AS turn_id, u.content AS user_text, a.content AS assistant_text,
                   a.timestamp AS completed_at, a.id AS sequence_id
            FROM chat_turn t JOIN message u ON u.id = t.user_message_id
            JOIN message a ON a.id = t.assistant_message_id
            WHERE t.conversation_id = :conversationId AND t.status = 'COMPLETED' AND a.id > :afterSeq
            ORDER BY a.id ASC LIMIT :limit
            """)
    Flux<org.example.ai_agent.chat.domain.CompletedContextTurn> findCompletedContextAfter(Long conversationId,
                                                                                           Long afterSeq, int limit);

    @org.springframework.data.r2dbc.repository.Query("""
            SELECT m.id, m.conversation_id, m.timestamp, m.model,
                   CASE WHEN EXISTS (SELECT 1 FROM chat_turn t
                           WHERE t.user_message_id = m.id OR t.assistant_message_id = m.id)
                        THEN 'system' ELSE m.sender END AS sender,
                   CASE WHEN EXISTS (SELECT 1 FROM chat_turn t
                           WHERE t.user_message_id = m.id OR t.assistant_message_id = m.id)
                        THEN NULL ELSE m.content END AS content
            FROM message m
            WHERE m.conversation_id = :conversationId
            ORDER BY m.timestamp DESC, m.id DESC LIMIT 200
            """)
    Flux<Message> findLegacyContext(Long conversationId);
    Flux<Message> findByConversationIdOrderByTimestampAsc(Long conversationId);

    Mono<Message> findByDeduplicationKey(String deduplicationKey);

    /**
     * 根据对话ID删除所有消息
     *
     * @param conversationId 对话ID
     * @return 删除操作完成后的Mono<Void>
     */
    Mono<Void> deleteByConversationId(Long conversationId);
}
