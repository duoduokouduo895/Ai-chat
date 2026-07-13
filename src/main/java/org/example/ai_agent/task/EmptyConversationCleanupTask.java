package org.example.ai_agent.task;

import org.example.ai_agent.repository.chat.RedisChatMemeoryStore;
import org.example.ai_agent.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 清理没有任何消息记录的历史空会话。
 */
@Component
public class EmptyConversationCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(EmptyConversationCleanupTask.class);

    private static final long EMPTY_CONVERSATION_RETENTION_HOURS = 1L;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final SearchService searchService;
    private final RedisChatMemeoryStore chatMemoryStore;
    private final int cleanupBatchSize;
    private final ScheduledTaskGuard cleanupGuard = new ScheduledTaskGuard();

    public EmptyConversationCleanupTask(DatabaseClient databaseClient,
                                        TransactionalOperator transactionalOperator,
                                        SearchService searchService,
                                        RedisChatMemeoryStore chatMemoryStore,
                                        @Value("${scheduled.cleanup.empty-conversations.batch-size:100}") int cleanupBatchSize) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.searchService = searchService;
        this.chatMemoryStore = chatMemoryStore;
        this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
    }

    @Scheduled(cron = "${scheduled.cleanup.empty-conversations.cron:0 0 2 * * ?}")
    public void cleanupEmptyConversations() {
        if (!cleanupGuard.tryStart()) {
            log.warn("清理空会话任务 - 上一轮尚未结束，跳过本次触发");
            return;
        }

        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(EMPTY_CONVERSATION_RETENTION_HOURS);

        cleanupEmptyConversationBatches(thresholdTime)
                .doOnSuccess(deletedCount -> log.info("清理空会话任务完成，共删除 {} 条空会话记录", deletedCount))
                .doOnError(error -> log.error("清理空会话任务执行失败: errorType={}",
                        error.getClass().getSimpleName()))
                .doFinally(signal -> cleanupGuard.finish())
                .subscribe();
    }

    private Mono<Long> cleanupEmptyConversationBatches(LocalDateTime thresholdTime) {
        return cleanupEmptyConversationBatch(thresholdTime)
                .expand(batch -> batch.shouldContinue(cleanupBatchSize)
                        ? cleanupEmptyConversationBatch(thresholdTime)
                        : Mono.empty())
                .map(CleanupBatch::deletedCount)
                .reduce(0L, Long::sum);
    }

    private Mono<CleanupBatch> cleanupEmptyConversationBatch(LocalDateTime thresholdTime) {
        return Mono.defer(() -> findEmptyConversationIds(thresholdTime)
                        .flatMap(conversationIds -> {
                            if (conversationIds.isEmpty()) {
                                log.info("清理空会话任务 - 本轮未命中待清理记录，thresholdTime={}", thresholdTime);
                                return Mono.just(new CleanupBatch(List.of(), 0L));
                            }

                            log.warn("清理空会话任务 - 命中空会话: count={}", conversationIds.size());

                            return deleteConversationsByIds(conversationIds)
                                    .map(deletedCount -> new CleanupBatch(conversationIds, deletedCount));
                        })
                        .as(transactionalOperator::transactional))
                .flatMap(batch -> cleanupConversationIndexes(batch.conversationIds())
                        .thenReturn(batch));
    }

    @Scheduled(cron = "${scheduled.monitor.empty-conversations.cron:0 0 * * * ?}")
    public void monitorEmptyConversations() {
        countEmptyConversations()
                .doOnNext(count -> {
                    if (count > 0) {
                        log.warn("空会话监控 - 当前仍有 {} 条空会话待清理", count);
                        return;
                    }
                    log.info("空会话监控 - 当前没有空会话");
                })
                .doOnError(error -> log.error("空会话监控执行失败: errorType={}",
                        error.getClass().getSimpleName()))
                .subscribe();
    }

    private Mono<List<Long>> findEmptyConversationIds(LocalDateTime thresholdTime) {
        String sql = """
                SELECT c.id
                FROM conversation c
                LEFT JOIN message m ON m.conversation_id = c.id
                WHERE m.id IS NULL
                  AND c.created_at < :thresholdTime
                ORDER BY c.id
                LIMIT :batchSize
                """;

        return databaseClient.sql(sql)
                .bind("thresholdTime", thresholdTime)
                .bind("batchSize", cleanupBatchSize)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .collectList();
    }

    private Mono<Long> countEmptyConversations() {
        String sql = """
                SELECT COUNT(*) AS total
                FROM conversation c
                LEFT JOIN message m ON m.conversation_id = c.id
                WHERE m.id IS NULL
                """;

        return databaseClient.sql(sql)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    private Mono<Long> deleteConversationsByIds(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Mono.just(0L);
        }

        String sql = String.format(
                "DELETE FROM conversation WHERE id IN (%s)",
                buildNamedParameters("id", conversationIds.size())
        );

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < conversationIds.size(); i++) {
            spec = spec.bind("id" + i, conversationIds.get(i));
        }

        return spec.fetch().rowsUpdated();
    }

    private Mono<Void> cleanupConversationIndexes(List<Long> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(conversationIds)
                .concatMap(conversationId -> searchService.deleteConversationIndex(conversationId)
                .doOnError(error -> log.error("删除空会话索引失败: conversationId={}, errorType={}",
                        conversationId, error.getClass().getSimpleName()))
                        .onErrorResume(error -> Mono.empty())
                        .then(Mono.fromRunnable(() -> chatMemoryStore.deleteConversationMessages(conversationId)))
                        .onErrorResume(error -> {
                            log.error("删除空会话 Redis ChatMemory 失败: conversationId={}, errorType={}",
                                    conversationId, error.getClass().getSimpleName());
                            return Mono.empty();
                        }))
                .then();
    }

    private String buildNamedParameters(String prefix, int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> ":" + prefix + i)
                .collect(Collectors.joining(", "));
    }

    private record CleanupBatch(List<Long> conversationIds, Long deletedCount) {

        private boolean shouldContinue(int batchSize) {
            return conversationIds.size() == batchSize && deletedCount > 0;
        }
    }
}
