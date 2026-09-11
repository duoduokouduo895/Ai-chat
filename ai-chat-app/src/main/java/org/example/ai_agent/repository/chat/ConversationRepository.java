package org.example.ai_agent.repository.chat;

import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
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
     * 单次查询最近会话与预览，避免逐条查询消息造成 N+1 数据库往返。
     */
    @Query("""
            SELECT recent.id,
                   recent.title,
                   recent.created_at,
                   recent.updated_at,
                   recent.user_id,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = recent.id
                         AND m.sender = 'user'
                       ORDER BY m.timestamp ASC
                       LIMIT 1
                   ) AS first_user_message,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = recent.id
                       ORDER BY m.timestamp DESC
                       LIMIT 1
                   ) AS last_message
            FROM (
                SELECT c.id, c.title, c.created_at, c.updated_at, c.user_id
                FROM conversation c
                WHERE c.user_id = :userId
                  AND EXISTS (
                      SELECT 1
                      FROM message existing_message
                      WHERE existing_message.conversation_id = c.id
                  )
                ORDER BY c.updated_at DESC
                LIMIT 5
            ) recent
            ORDER BY recent.updated_at DESC
            """)
    Flux<ConversationWithPreview> findRecentPreviewsByUserId(@Param("userId") Long userId);

    /**
     * 管理端分页查询会话与预览，单次查询避免逐条加载消息。
     */
    @Query("""
            SELECT c.id,
                   c.title,
                   c.created_at,
                   c.updated_at,
                   c.user_id,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                         AND m.sender = 'user'
                       ORDER BY m.timestamp ASC
                       LIMIT 1
                   ) AS first_user_message,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                       ORDER BY m.timestamp DESC
                       LIMIT 1
                   ) AS last_message
            FROM conversation c
            WHERE EXISTS (
                SELECT 1
                FROM message existing_message
                WHERE existing_message.conversation_id = c.id
            )
            ORDER BY c.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<ConversationWithPreview> findAllPreviewsForAdmin(@Param("limit") int limit, @Param("offset") long offset);

    @Query("""
            SELECT c.id,
                   c.title,
                   c.created_at,
                   c.updated_at,
                   c.user_id,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                         AND m.sender = 'user'
                       ORDER BY m.timestamp ASC
                       LIMIT 1
                   ) AS first_user_message,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                       ORDER BY m.timestamp DESC
                       LIMIT 1
                   ) AS last_message
            FROM conversation c
            WHERE EXISTS (
                SELECT 1
                FROM message existing_message
                WHERE existing_message.conversation_id = c.id
            )
              AND (:keyword IS NULL
                   OR CAST(c.id AS CHAR) = :keyword
                   OR CAST(c.user_id AS CHAR) = :keyword
                   OR LOWER(COALESCE(c.title, '')) LIKE CONCAT('%', LOWER(:keyword), '%'))
            ORDER BY c.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<ConversationWithPreview> findAdminPage(@Param("keyword") String keyword,
                                                @Param("limit") int limit,
                                                @Param("offset") long offset);

    @Query("""
            SELECT COUNT(*)
            FROM conversation c
            WHERE EXISTS (
                SELECT 1
                FROM message existing_message
                WHERE existing_message.conversation_id = c.id
            )
              AND (:keyword IS NULL
                   OR CAST(c.id AS CHAR) = :keyword
                   OR CAST(c.user_id AS CHAR) = :keyword
                   OR LOWER(COALESCE(c.title, '')) LIKE CONCAT('%', LOWER(:keyword), '%'))
            """)
    Mono<Long> countAdminPage(@Param("keyword") String keyword);

    /**
     * 管理端按用户分页查询会话与预览，单次查询避免逐条加载消息。
     */
    @Query("""
            SELECT c.id,
                   c.title,
                   c.created_at,
                   c.updated_at,
                   c.user_id,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                         AND m.sender = 'user'
                       ORDER BY m.timestamp ASC
                       LIMIT 1
                   ) AS first_user_message,
                   (
                       SELECT m.content
                       FROM message m
                       WHERE m.conversation_id = c.id
                       ORDER BY m.timestamp DESC
                       LIMIT 1
                   ) AS last_message
            FROM conversation c
            WHERE c.user_id = :userId
              AND EXISTS (
                  SELECT 1
                  FROM message existing_message
                  WHERE existing_message.conversation_id = c.id
              )
            ORDER BY c.updated_at DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<ConversationWithPreview> findPreviewsForAdminByUserId(@Param("userId") Long userId,
                                                               @Param("limit") int limit,
                                                               @Param("offset") long offset);

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

    @Query("SELECT COALESCE(MAX(id), 0) FROM conversation")
    Mono<Long> findSearchRebuildUpperBound();

    @Query("""
            SELECT c.id, c.title, c.created_at, c.updated_at, c.user_id
            FROM conversation c INNER JOIN users u ON c.user_id = u.user_id
            WHERE c.id > :afterId AND c.id <= :upperBound
            ORDER BY c.id ASC LIMIT :pageSize
            """)
    Flux<Conversation> findSearchRebuildPage(long afterId, long upperBound, int pageSize);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM conversation c
                INNER JOIN users u ON c.user_id = u.user_id
                WHERE c.id = :conversationId
            )
            """)
    Mono<Boolean> existsRegisteredConversationById(Long conversationId);
}
