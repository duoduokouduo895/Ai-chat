package org.example.ai_agent.repository.search;

import org.example.ai_agent.entity.SearchHistory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SearchHistoryRepository extends R2dbcRepository<SearchHistory, Long> {

    /**
     * 根据查询内容查找最新的搜索历史记录
     */
    @Query("SELECT * FROM search_history WHERE user_id = :userId AND query = :query ORDER BY `timestamp` DESC LIMIT 1")
    Mono<SearchHistory> findLatestByUserIdAndQuery(Long userId, String query);

    /**
     * 按时间倒序获取去重的搜索历史（MySQL版本）
     */
    @Query("SELECT s1.* FROM search_history s1 " +
           "WHERE s1.id IN (" +
           "  SELECT MAX(s2.id) FROM search_history s2 " +
           "  WHERE s2.user_id = :userId " +
           "  GROUP BY s2.query" +
           ") " +
           "ORDER BY s1.`timestamp` DESC LIMIT :limit")
    Flux<SearchHistory> findDistinctByUserIdOrderByTimestampDesc(Long userId, int limit);
    
    /**
     * 删除指定查询的旧记录
     */
    @Query("DELETE FROM search_history WHERE user_id = :userId AND query = :query AND id != :keepId")
    Mono<Void> deleteOldRecordsByUserIdAndQuery(Long userId, String query, Long keepId);
    
    /**
     * 获取所有搜索历史用于调试
     */
    @Query("SELECT * FROM search_history WHERE user_id = :userId ORDER BY `timestamp` DESC")
    Flux<SearchHistory> findAllByUserIdOrderByTimestampDesc(Long userId);

    @Query("SELECT * FROM search_history " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR query LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY `timestamp` DESC, id DESC LIMIT :limit OFFSET :offset")
    Flux<SearchHistory> findAdminHistory(@Param("keyword") String keyword,
                                         @Param("limit") long limit,
                                         @Param("offset") long offset);

    @Query("SELECT COUNT(*) FROM search_history " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR query LIKE CONCAT('%', :keyword, '%'))")
    Mono<Long> countAdminHistory(@Param("keyword") String keyword);

    /**
     * 高性能游标分页查询（Keyset Paging）
     *
     * @param keyword 搜索关键字
     * @param lastTimestamp 上一页最后一条记录的时间戳
     * @param lastId 上一页最后一条记录的ID
     * @param limit 限制数量
     * @return 搜索历史列表
     */
    @Query("SELECT * FROM search_history " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR query LIKE CONCAT('%', :keyword, '%')) " +
           "AND (:lastTimestamp IS NULL OR `timestamp` < :lastTimestamp OR (`timestamp` = :lastTimestamp AND id < :lastId)) " +
           "ORDER BY `timestamp` DESC, id DESC LIMIT :limit")
    Flux<SearchHistory> findHistoryKeyset(@Param("keyword") String keyword,
                                          @Param("lastTimestamp") java.time.LocalDateTime lastTimestamp,
                                          @Param("lastId") Long lastId,
                                          @Param("limit") int limit);

    Mono<Long> countByTimestampAfter(java.time.LocalDateTime timestamp);

    /**
     * 清理重复的搜索历史，保留每个查询的最新记录
     */
    @Query("DELETE FROM search_history WHERE user_id = :userId AND id NOT IN (" +
           "  SELECT max_id FROM (" +
           "    SELECT MAX(id) as max_id FROM search_history WHERE user_id = :userId GROUP BY query" +
           "  ) AS temp" +
           ")")
    Mono<Void> cleanupDuplicateHistoryByUserId(Long userId);

    /**
     * 清理用户的所有搜索历史
     */
    @Query("DELETE FROM search_history WHERE user_id = :userId")
    Mono<Void> deleteByUserId(Long userId);
}
