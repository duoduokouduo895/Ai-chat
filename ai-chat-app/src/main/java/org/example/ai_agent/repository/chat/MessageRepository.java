package org.example.ai_agent.repository.chat;

import org.example.ai_agent.entity.Message;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageRepository extends R2dbcRepository<Message, Long> {
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
