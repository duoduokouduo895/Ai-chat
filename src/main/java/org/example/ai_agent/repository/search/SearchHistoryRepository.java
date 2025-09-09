package org.example.ai_agent.repository.search;

import org.example.ai_agent.entity.SearchHistory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SearchHistoryRepository extends R2dbcRepository<SearchHistory, Long> {
    
    /**
     * 根据查询内容查找搜索历史
     * @param query 查询内容
     * @return 搜索历史记录
     */
    Mono<SearchHistory> findByQuery(String query);
    
    /**
     * 按时间倒序获取去重的搜索历史（MySQL版本）
     * @param limit 限制数量
     * @return 去重的搜索历史列表
     */
    @Query("SELECT s1.* FROM search_history s1 " +
           "INNER JOIN (SELECT query, MAX(timestamp) as max_timestamp " +
           "FROM search_history GROUP BY query) s2 " +
           "ON s1.query = s2.query AND s1.timestamp = s2.max_timestamp " +
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
}
