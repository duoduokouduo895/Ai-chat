package org.example.ai_agent.repository.search;

import org.example.ai_agent.entity.SearchHistory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SearchHistoryRepository extends R2dbcRepository<SearchHistory, Long> {
    
    /**
     * 根据查询内容查找最新的搜索历史记录
     * @param query 查询内容
     * @return 最新的搜索历史记录
     */
    @Query("SELECT * FROM search_history WHERE query = :query ORDER BY timestamp DESC LIMIT 1")
    Mono<SearchHistory> findLatestByQuery(String query);
    
    /**
     * 按时间倒序获取去重的搜索历史（MySQL版本）
     * @param limit 限制数量
     * @return 去重的搜索历史列表
     */
    @Query("SELECT s1.* FROM search_history s1 " +
           "WHERE s1.id IN (" +
           "  SELECT MAX(s2.id) FROM search_history s2 " +
           "  GROUP BY s2.query" +
           ") " +
           "ORDER BY s1.timestamp DESC LIMIT :limit")
    Flux<SearchHistory> findDistinctByQueryOrderByTimestampDesc(int limit);
    
    /**
     * 删除指定查询的旧记录
     * @param query 查询内容
     * @param keepId 要保留的记录ID
     * @return 删除完成信号
     */
    @Query("DELETE FROM search_history WHERE query = :query AND id != :keepId")
    Mono<Void> deleteOldRecordsByQuery(String query, Long keepId);
    
    /**
     * 获取所有搜索历史用于调试
     * @return 所有搜索历史记录
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC")
    Flux<SearchHistory> findAllOrderByTimestampDesc();
    
    /**
     * 清理重复的搜索历史，保留每个查询的最新记录
     * @return 清理完成信号
     */
    @Query("DELETE FROM search_history WHERE id NOT IN (" +
           "  SELECT max_id FROM (" +
           "    SELECT MAX(id) as max_id FROM search_history GROUP BY query" +
           "  ) AS temp" +
           ")")
    Mono<Void> cleanupDuplicateHistory();
}
