package org.example.ai_agent.repository.chat;

import org.example.ai_agent.entity.Message;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageRepository extends R2dbcRepository<Message, Long> {
    Flux<Message> findByConversationIdOrderByTimestampAsc(Long conversationId);

    /**
     * 根据对话ID和发送者查询第一条消息
     *
     * @param conversationId 对话ID
     * @param sender 发送者类型（user、ai、system）
     * @return 第一条匹配的消息
     */
    @Query("SELECT * FROM message WHERE conversation_id = :conversationId AND sender = :sender ORDER BY timestamp ASC LIMIT 1")
    Mono<Message> findFirstByConversationIdAndSenderOrderByTimestampAsc(@Param("conversationId") Long conversationId, @Param("sender") String sender);

    /**
     * 根据对话ID查询最后一条消息
     *
     * @param conversationId 对话ID
     * @return 最后一条消息
     */
    @Query("SELECT * FROM message WHERE conversation_id = :conversationId ORDER BY timestamp DESC LIMIT 1")
    Mono<Message> findFirstByConversationIdOrderByTimestampDesc(@Param("conversationId") Long conversationId);



    /**
     * 根据对话ID删除所有消息
     *
     * @param conversationId 对话ID
     * @return 删除操作完成后的Mono<Void>
     */
    Mono<Void> deleteByConversationId(Long conversationId);
}
