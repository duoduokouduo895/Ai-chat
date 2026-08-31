package org.example.ai_agent.service.impl.search;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.search.ConversationDocumentRepository;
import org.example.ai_agent.repository.chat.ConversationRepository;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.repository.search.SearchHistoryRepository;
import org.example.ai_agent.service.SearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConversationDocumentRepository conversationDocumentRepository;
    private final ReactiveElasticsearchOperations elasticsearchOperations;
    private final SearchHistoryRepository searchHistoryRepository;
    private final AtomicBoolean rebuildRunning = new AtomicBoolean(false);

    public SearchServiceImpl(ConversationRepository conversationRepository,
                             MessageRepository messageRepository,
                             ConversationDocumentRepository conversationDocumentRepository,
                             ReactiveElasticsearchOperations elasticsearchOperations,
                             SearchHistoryRepository searchHistoryRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.conversationDocumentRepository = conversationDocumentRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    @Override
    public Mono<Void> indexConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .flatMap(this::indexConversation)
                .doOnSuccess(v -> log.info("成功为对话建立索引: {}", conversationId))
                .doOnError(e -> log.error("为对话建立索引失败: conversationId={}, errorType={}",
                        conversationId, e.getClass().getSimpleName()))
                .then();
    }

    private Mono<Void> indexConversation(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return Mono.empty();
        }
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversation.getId())
                .collectList()
                .flatMap(messages -> {
                    ConversationDocument doc = createConversationDocument(conversation, messages);
                    return conversationDocumentRepository.save(doc);
                })
                .doOnSuccess(doc -> log.info("成功为对话建立索引 (使用已有实体): {}", doc.getConversationId()))
                .doOnError(e -> log.error("为对话建立索引失败 (使用已有实体): conversationId={}, errorType={}",
                        conversation.getId(), e.getClass().getSimpleName()))
                .then();
    }

    private ConversationDocument createConversationDocument(Conversation conversation, List<Message> messages) {
        String content = messages.stream().map(Message::getContent).collect(Collectors.joining("\n"));
        List<String> participants = messages.stream().map(Message::getSender).distinct().collect(Collectors.toList());
        List<String> models = messages.stream().map(Message::getModel).distinct().collect(Collectors.toList());
        LocalDateTime lastMessageTime = messages.isEmpty() ? conversation.getCreatedAt() : messages.get(messages.size() - 1).getTimestamp();

        return new ConversationDocument(
                conversation.getId(),
                conversation.getUserId(), // 关键改动：传入userId
                conversation.getTitle(),
                conversation.getCreatedAt(),
                content,
                participants,
                models,
                messages.size(),
                lastMessageTime
        );
    }

    @Override
    public Mono<Void> indexConversations(List<Long> conversationIds) {
        return Flux.fromIterable(conversationIds)
                .flatMap(this::indexConversation)
                .then();
    }

    @Override
    public Mono<Void> updateConversationIndex(Long conversationId) {
        return indexConversation(conversationId);
    }

    @Override
    public Mono<Void> deleteConversationIndex(Long conversationId) {
        return conversationDocumentRepository.deleteById(conversationId.toString())
                .doOnSuccess(v -> log.info("成功删除对话索引: {}", conversationId))
                .doOnError(e -> log.error("删除对话索引失败: conversationId={}, errorType={}",
                        conversationId, e.getClass().getSimpleName()));
    }

    @Override
    public Mono<Void> indexConversationsForUser(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .flatMap(conversation -> indexConversation(conversation.getId()))
            .doOnComplete(() -> log.info("用户对话索引建立完成"))
            .doOnError(e -> log.error("用户对话索引建立失败: errorType={}", e.getClass().getSimpleName()))
            .then();
    }

        @Override
    public Flux<ConversationDocument> searchConversations(Long userId, String query, LocalDateTime startDate, LocalDateTime endDate, List<String> models, int page, int size) {
        int queryLength = query == null ? 0 : query.length();
        log.info("执行统一搜索: queryLength={}, startDate={}, endDate={}, modelCount={}, page={}, size={}",
                queryLength, startDate, endDate, models == null ? 0 : models.size(), page, size);

        // 使用 FastJSON 构建原生JSON查询
        JSONObject boolQuery = new JSONObject();
        JSONObject boolClause = new JSONObject();
        boolQuery.put("bool", boolClause);

        // 1. Must Clause for text search
        if (query != null && !query.isBlank()) {
            JSONObject multiMatch = new JSONObject();
            multiMatch.put("query", query);
            multiMatch.put("fields", new String[]{"title", "content"});
            multiMatch.put("type", "best_fields");

            JSONArray mustClauses = new JSONArray();
            mustClauses.add(new JSONObject().fluentPut("multi_match", multiMatch));
            boolClause.put("must", mustClauses);
        }

        // 2. Filter Clause for exact matches
        JSONArray filterClauses = new JSONArray();

        // User ID Filter (Always active)
        JSONObject userTerm = new JSONObject();
        userTerm.put("term", new JSONObject().fluentPut("userId", userId));
        filterClauses.add(userTerm);

        // Date Range Filter
        if (startDate != null || endDate != null) {
            JSONObject dateRangeQuery = new JSONObject();
            JSONObject dateRange = new JSONObject();
            if (startDate != null) {
                dateRange.put("gte", startDate.toString());
            }
            if (endDate != null) {
                dateRange.put("lte", endDate.toString());
            }
            dateRangeQuery.put("range", new JSONObject().fluentPut("createdAt", dateRange));
            filterClauses.add(dateRangeQuery);
        }

        // Models Filter
        if (models != null && !models.isEmpty()) {
            JSONObject modelsTerm = new JSONObject();
            // 使用 .keyword 确保对数组进行精确匹配
            modelsTerm.put("terms", new JSONObject().fluentPut("models.keyword", models));
            filterClauses.add(modelsTerm);
        }

        boolClause.put("filter", filterClauses);

        String jsonQueryString = boolQuery.toJSONString();
        log.debug("Elasticsearch 查询已构建: queryLength={}, filterCount={}", queryLength, filterClauses.size());

        StringQuery stringQuery = new StringQuery(jsonQueryString);
        stringQuery.setPageable(PageRequest.of(page, size));

        return elasticsearchOperations.search(stringQuery, ConversationDocument.class)
                .map(searchHit -> searchHit.getContent())
                .doOnComplete(() -> log.info("统一搜索完成"))
                .doOnError(e -> log.error("统一搜索失败: errorType={}", e.getClass().getSimpleName()));
    }

    @Override
    public Flux<ConversationDocument> searchByTitle(String title) {
        return conversationDocumentRepository.findByTitleContaining(title, PageRequest.of(0, 100));
    }

    @Override
    public Flux<ConversationDocument> searchByContent(String content) {
        // This is a simplified implementation. For better results, consider using a proper query.
        return conversationDocumentRepository.findByTitleContainingOrContentContaining("", content, PageRequest.of(0, 100));
    }

    @Override
    public Flux<ConversationDocument> searchByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        CriteriaQuery criteriaQuery = new CriteriaQuery(
                new Criteria("createdAt").between(startTime, endTime)
        );
        return elasticsearchOperations.search(criteriaQuery, ConversationDocument.class)
                .map(searchHit -> searchHit.getContent());
    }

    @Override
    public Flux<ConversationDocument> searchByModel(String model) {
        CriteriaQuery criteriaQuery = new CriteriaQuery(
                new Criteria("models").is(model)
        );
        return elasticsearchOperations.search(criteriaQuery, ConversationDocument.class)
                .map(searchHit -> searchHit.getContent());
    }



    @Override
    public Mono<List<String>> getSearchSuggestions(Long userId, String prefix, int size) {
        if (userId == null || userId <= 0 || prefix == null || prefix.isBlank() || size <= 0) {
            return Mono.just(List.of());
        }

        return searchHistoryRepository.findSuggestionQueriesByUserId(userId, escapeLikePrefix(prefix.trim()), size)
                .distinct()
                .take(size)
                .collectList();
    }

    private String escapeLikePrefix(String prefix) {
        return prefix.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    @Override
    public Mono<Void> rebuildAllIndexes() {
        if (!rebuildRunning.compareAndSet(false, true)) {
            return Mono.error(new IllegalStateException("ES 索引重建任务正在执行"));
        }

        log.info("开始无损重建 ES 索引...");

        return conversationRepository.findAllConversationsFromRegisteredUsers()
                .doOnNext(conversation -> log.debug("正在更新对话索引: {}", conversation.getId()))
                .buffer(50)
                .concatMap(conversations -> Flux.fromIterable(conversations)
                        .flatMap(this::indexConversation, 5)
                        .then())
                .then(pruneStaleConversationIndexes())
                .doOnSuccess(deleted -> log.info("ES 索引无损重建完成，清理了 {} 条失效索引", deleted))
                .then()
                .doOnError(error -> log.error("ES 索引无损重建失败，保留已有可用索引: errorType={}",
                        error.getClass().getSimpleName()))
                .doFinally(signal -> rebuildRunning.set(false));
    }

    private Mono<Long> pruneStaleConversationIndexes() {
        return conversationDocumentRepository.findAll()
                .flatMap(document -> resolveConversationId(document)
                        .flatMap(conversationId -> conversationRepository.existsRegisteredConversationById(conversationId)
                                .filter(Boolean.FALSE::equals)
                                .flatMap(ignored -> conversationDocumentRepository.deleteById(document.getId())
                                        .thenReturn(1L))), 5)
                .reduce(0L, Long::sum);
    }

    private Mono<Long> resolveConversationId(ConversationDocument document) {
        if (document.getConversationId() != null) {
            return Mono.just(document.getConversationId());
        }
        try {
            return Mono.just(Long.parseLong(document.getId()));
        } catch (NumberFormatException error) {
            log.warn("删除无法识别业务ID的失效会话索引: esId={}", document.getId());
            return conversationDocumentRepository.deleteById(document.getId()).then(Mono.empty());
        }
    }

    @Override
    public Mono<String> checkIndexStatus() {
        return elasticsearchOperations.indexOps(ConversationDocument.class).exists()
                .map(exists -> exists ? "索引 'conversations' 已存在." : "索引 'conversations' 不存在.");
    }

    @Override
    public Mono<String> indexAllExistingConversations() {
        return conversationRepository.findAll()
                .flatMap(conversation -> indexConversation(conversation.getId()))
                .count()
                .map(count -> "成功索引 " + count + " 个对话。");
    }

    @Override
    public Mono<Void> recordSearchHistory(Long userId, String query, String type) {
        if (query == null || query.trim().isEmpty()) {
            return Mono.empty();
        }

        String trimmedQuery = query.trim();

        // 检查是否已存在相同的查询（使用最新记录）
        return searchHistoryRepository.findLatestByUserIdAndQuery(userId, trimmedQuery)
                .flatMap(existingHistory -> {
                    // 如果存在，更新时间戳
                    existingHistory.setTimestamp(LocalDateTime.now());
                    return searchHistoryRepository.save(existingHistory)
                            .doOnSuccess(updated -> log.debug("搜索历史已更新: id={}", updated.getId()))
                            .then();
                })
                .switchIfEmpty(
                    // 如果不存在，创建新记录
                    Mono.fromCallable(() -> {
                        org.example.ai_agent.entity.SearchHistory searchHistory = new org.example.ai_agent.entity.SearchHistory();
                        searchHistory.setUserId(userId);
                        searchHistory.setQuery(trimmedQuery);
                        searchHistory.setType(type != null ? type : "GENERAL");
                        searchHistory.setTimestamp(LocalDateTime.now());
                        return searchHistory;
                    })
                    .flatMap(searchHistoryRepository::save)
                    .doOnSuccess(saved -> log.debug("搜索历史记录已保存: type={}, id={}", type, saved.getId()))
                    .then()
                )
                .doOnError(error -> log.error("保存搜索历史失败: errorType={}", error.getClass().getSimpleName()));
    }

    @Override
    public Flux<ConversationDocument> getSearchHistory(Long userId, int size) {
        log.debug("开始获取去重搜索历史: size={}", size);

        // 优先使用改进的去重查询
        return searchHistoryRepository.findDistinctByUserIdOrderByTimestampDesc(userId, Math.max(1, size))
                .doOnNext(history -> log.debug("找到去重历史记录: id={}, timestamp={}",
                    history.getId(), history.getTimestamp()))
                .map(searchHistory -> {
                    // 将搜索历史转换为ConversationDocument格式，以便前端显示
                    ConversationDocument doc = new ConversationDocument();
                    doc.setId(searchHistory.getId().toString()); // 使用搜索历史ID
                    doc.setTitle(searchHistory.getQuery()); // 使用查询内容作为标题
                    doc.setCreatedAt(searchHistory.getTimestamp()); // 使用搜索时间
                    doc.setMessageCount(0); // 搜索历史没有消息数量
                    doc.setUserId(null); // 搜索历史没有用户ID
                    return doc;
                })
                .doOnComplete(() -> log.debug("去重搜索历史查询完成, size={}", size))
                .doOnError(error -> {
                    log.error("获取去重搜索历史失败: errorType={}", error.getClass().getSimpleName());
                    // 如果去重查询失败，尝试使用普通查询并在应用层去重
                    log.warn("降级使用普通查询并在应用层去重");
                });
    }

    @Override
    public Mono<Void> clearSearchHistory(Long userId) {
        return searchHistoryRepository.deleteByUserId(userId)
                .doOnSuccess(v -> log.info("用户搜索历史已清空"))
                .doOnError(error -> log.error("用户搜索历史清空失败: errorType={}",
                        error.getClass().getSimpleName()));
    }

    @Override
    public Mono<String> cleanupDuplicateSearchHistory(Long userId) {
        return searchHistoryRepository.findAllByUserIdOrderByTimestampDesc(userId)
                .doOnNext(history -> log.debug("处理历史记录: id={}, timestamp={}",
                    history.getId(), history.getTimestamp()))
                .count()
                .doOnNext(totalBefore -> log.info("清理前总记录数: {}", totalBefore))
                .then(searchHistoryRepository.cleanupDuplicateHistoryByUserId(userId))
                .then(searchHistoryRepository.findAllByUserIdOrderByTimestampDesc(userId).count())
                .doOnNext(totalAfter -> log.info("清理后总记录数: {}", totalAfter))
                .map(totalAfter -> "清理重复搜索历史完成，剩余记录数: " + totalAfter)
                .doOnError(error -> log.error("清理重复搜索历史失败: errorType={}",
                        error.getClass().getSimpleName()));
    }
}
