package org.example.ai_agent.repository.chat;

import org.example.ai_agent.entity.Conversation;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationRepository extends R2dbcRepository<Conversation, Long> {
    Mono<Conversation> findByIdAndUserId(Long id, Long userId);

    /**
     * 根据用户ID查找最近5个对话，按更新时间降序排序
     * @param userId 用户ID
     * @return 最近对话列表的响应式流
     */
    Flux<Conversation> findTop5ByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 根据用户ID查找所有对话，并按更新时间降序排序
     * @param userId 用户ID
     * @return 对话列表的响应式流
     */
    Flux<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Flux<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    /**
     * 查询全站会话（分页）
     *
     * @param pageable 分页信息
     * @return 会话列表
     */
    Flux<Conversation> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    /**
     * 查找所有用户的对话（用于搜索索引）
     * @return 所有对话列表的响应式流
     */
    @Query("SELECT c.id, c.title, c.created_at, c.updated_at, c.user_id FROM conversation c INNER JOIN users u ON c.user_id = u.user_id")
    Flux<Conversation> findAllConversationsFromRegisteredUsers();
}
