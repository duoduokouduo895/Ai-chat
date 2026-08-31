package org.example.ai_agent.task;

import org.example.ai_agent.repository.chat.RedisChatMemoryStore;
import org.example.ai_agent.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 游客数据清理定时任务
 * 用于自动清理超过有效期的游客记录及其相关的对话和消息数据
 */
@Component
public class GuestCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(GuestCleanupTask.class);
    
    // 注入 DatabaseClient 用于执行底层的批量/级联删除 SQL
    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final Duration guestSessionTtl;
    private final SearchService searchService;
    private final RedisChatMemoryStore chatMemoryStore;
    private final int cleanupBatchSize;
    private final ScheduledTaskGuard taskGuard = new ScheduledTaskGuard();

    public GuestCleanupTask(DatabaseClient databaseClient,
                            TransactionalOperator transactionalOperator,
                            SearchService searchService,
                            RedisChatMemoryStore chatMemoryStore,
                            @Value("${scheduled.cleanup.expired-guests.ttl-hours:3}") long guestSessionTtlHours,
                            @Value("${scheduled.cleanup.expired-guests.batch-size:100}") int cleanupBatchSize) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.searchService = searchService;
        this.chatMemoryStore = chatMemoryStore;
        this.guestSessionTtl = Duration.ofHours(Math.max(1, guestSessionTtlHours));
        this.cleanupBatchSize = Math.max(1, cleanupBatchSize);
    }

    /**
     * 定时任务：每 5 分钟执行一次检查
     * 目标：清理最后活跃时间超过 3 小时期限的游客数据
     */
    @Scheduled(cron = "${scheduled.cleanup.expired-guests.cron:0 0/5 * * * ?}")
    public void cleanupExpiredGuests() {
        if (!taskGuard.tryStart()) {
            log.warn("清理过期游客数据 - 上一轮尚未结束，跳过本次触发");
            return;
        }

        LocalDateTime thresholdTime = LocalDateTime.now().minus(guestSessionTtl);
        cleanupExpiredGuestBatches(thresholdTime)
                .doOnSuccess(deletedUsers -> log.info("清理过期游客数据 - 任务完成，共清理 {} 个过期游客账号", deletedUsers))
                .doOnError(e -> log.error("清理过期游客数据任务执行失败: errorType={}",
                        e.getClass().getSimpleName()))
                .doFinally(signal -> taskGuard.finish())
                .subscribe();
    }

    private Mono<Long> cleanupExpiredGuestBatches(LocalDateTime thresholdTime) {
        return cleanupExpiredGuestBatch(thresholdTime)
                .expand(batch -> batch.shouldContinue(cleanupBatchSize)
                        ? cleanupExpiredGuestBatch(thresholdTime)
                        : Mono.empty())
                .map(GuestCleanupBatch::deletedUsers)
                .reduce(0L, Long::sum);
    }

    private Mono<GuestCleanupBatch> cleanupExpiredGuestBatch(LocalDateTime thresholdTime) {
        return findExpiredGuestUserIds(thresholdTime)
                .flatMap(userIds -> {
                    if (userIds.isEmpty()) {
                        return Mono.just(new GuestCleanupBatch(List.of(), List.of(), 0L));
                    }

                    return findConversationIdsByUserIds(userIds)
                            .flatMap(conversationIds -> deleteByIds(
                                            "DELETE FROM message WHERE conversation_id IN (%s)", conversationIds)
                                    .then(deleteByIds("DELETE FROM conversation WHERE user_id IN (%s)", userIds))
                                    .then(deleteByIds("DELETE FROM search_history WHERE user_id IN (%s)", userIds))
                                    .then(deleteGuestUsersByIds(userIds))
                                    .map(deletedUsers -> new GuestCleanupBatch(userIds, conversationIds, deletedUsers))
                                    .as(transactionalOperator::transactional))
                            .flatMap(batch -> cleanupConversationAssets(batch.conversationIds()).thenReturn(batch));
                });
    }

    private Mono<List<Long>> findExpiredGuestUserIds(LocalDateTime thresholdTime) {
        String sql = "SELECT user_id FROM users " +
                "WHERE is_guest = 1 AND COALESCE(last_active_at, created_at) < :thresholdTime " +
                "ORDER BY user_id LIMIT :batchSize";

        return databaseClient.sql(sql)
                .bind("thresholdTime", thresholdTime)
                .bind("batchSize", cleanupBatchSize)
                .map((row, rowMetadata) -> row.get("user_id", Long.class))
                .all()
                .collectList();
    }

    private Mono<List<Long>> findConversationIdsByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Mono.just(List.of());
        }

        String sql = String.format("SELECT id FROM conversation WHERE user_id IN (%s)", buildNamedParameters("id", userIds.size()));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < userIds.size(); i++) {
            spec = spec.bind("id" + i, userIds.get(i));
        }

        return spec.map((row, rowMetadata) -> row.get("id", Long.class))
                .all()
                .collectList();
    }

    private Mono<Long> deleteGuestUsersByIds(List<Long> userIds) {
        String sql = String.format("DELETE FROM users WHERE is_guest = 1 AND user_id IN (%s)", buildNamedParameters("id", userIds.size()));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < userIds.size(); i++) {
            spec = spec.bind("id" + i, userIds.get(i));
        }
        return spec.fetch().rowsUpdated();
    }

    private Mono<Long> deleteByIds(String sqlTemplate, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(0L);
        }

        String sql = String.format(sqlTemplate, buildNamedParameters("id", ids.size()));
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int i = 0; i < ids.size(); i++) {
            spec = spec.bind("id" + i, ids.get(i));
        }
        return spec.fetch().rowsUpdated();
    }

    private String buildNamedParameters(String prefix, int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> ":" + prefix + i)
                .collect(Collectors.joining(", "));
    }

    private Mono<Void> cleanupConversationAssets(List<Long> conversationIds) {
        return Flux.fromIterable(conversationIds)
                .concatMap(conversationId -> searchService.deleteConversationIndex(conversationId)
                        .onErrorResume(error -> {
                log.warn("清理游客会话索引失败: conversationId={}, errorType={}",
                        conversationId, error.getClass().getSimpleName());
                            return Mono.empty();
                        })
                        .then(Mono.fromRunnable(() -> chatMemoryStore.deleteConversationMessages(conversationId)))
                        .onErrorResume(error -> {
                log.warn("清理游客 Redis ChatMemory 失败: conversationId={}, errorType={}",
                        conversationId, error.getClass().getSimpleName());
                            return Mono.empty();
                        }))
                .then();
    }

    private record GuestCleanupBatch(List<Long> userIds, List<Long> conversationIds, long deletedUsers) {

        private boolean shouldContinue(int batchSize) {
            return userIds.size() == batchSize && deletedUsers > 0;
        }
    }
}
