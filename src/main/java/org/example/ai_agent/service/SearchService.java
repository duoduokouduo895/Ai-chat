package org.example.ai_agent.service;

import org.example.ai_agent.document.ConversationDocument;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 搜索服务接口
 * 提供对话内容的全文搜索功能
 */
public interface SearchService {

    /**
     * 索引对话到Elasticsearch
     * @param conversationId 对话ID
     */
    Mono<Void> indexConversation(Long conversationId);

    /**
     * 批量索引对话到Elasticsearch
     * @param conversationIds 对话ID列表
     */
    Mono<Void> indexConversations(List<Long> conversationIds);

    /**
     * 更新对话索引
     * @param conversationId 对话ID
     */
    Mono<Void> updateConversationIndex(Long conversationId);

    /**
     * 删除对话索引
     * @param conversationId 对话ID
     */
    Mono<Void> deleteConversationIndex(Long conversationId);

    /**
     * 为指定用户的所有对话建立索引
     * @param userId 用户ID
     */
    Mono<Void> indexConversationsForUser(Long userId);

    /**
     * 全文搜索对话，并支持高级筛选
     * @param userId     执行搜索的用户ID
     * @param query      搜索关键词
     * @param startDate  开始时间
     * @param endDate    结束时间
     * @param models     AI模型列表
     * @param page       页码
     * @param size       每页大小
     * @return 搜索结果
     */
    Flux<ConversationDocument> searchConversations(Long userId, String query, LocalDateTime startDate, LocalDateTime endDate, List<String> models, int page, int size);

    /**
     * 根据标题搜索对话
     * @param title 标题关键词
     * @return 搜索结果
     */
    Flux<ConversationDocument> searchByTitle(String title);

    /**
     * 根据内容搜索对话
     * @param content 内容关键词
     * @return 搜索结果
     */
    Flux<ConversationDocument> searchByContent(String content);

    /**
     * 根据时间范围搜索对话
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 搜索结果
     */
    Flux<ConversationDocument> searchByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据模型搜索对话
     * @param model 模型名称
     * @return 搜索结果
     */
    Flux<ConversationDocument> searchByModel(String model);

    

    /**
     * 获取搜索建议
     * @param prefix 输入前缀
     * @param size 建议数量
     * @return 建议列表
     */
    Mono<List<String>> getSearchSuggestions(String prefix, int size);

    /**
     * 重建所有索引
     */
    Mono<Void> rebuildAllIndexes();

    /**
     * 检查Elasticsearch索引状态
     * @return 索引状态信息
     */
    Mono<String> checkIndexStatus();

    /**
     * 手动索引所有现有对话
     * @return 操作结果信息
     */
    Mono<String> indexAllExistingConversations();

    /**
     * 记录搜索历史
     * @param query 搜索关键词
     * @param type 搜索类型
     * @return 操作完成的信号
     */
    Mono<Void> recordSearchHistory(String query, String type);

    /**
     * 获取搜索历史对话
     * @param size 历史记录数量
     * @return 包含完整对话信息的搜索历史列表
     */
    Flux<ConversationDocument> getSearchHistory(int size);

    /**
     * 清除搜索历史
     * @return 操作完成的信号
     */
    Mono<Void> clearSearchHistory();
}