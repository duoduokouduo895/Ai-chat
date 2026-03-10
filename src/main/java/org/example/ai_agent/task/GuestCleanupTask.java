package org.example.ai_agent.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

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

    public GuestCleanupTask(DatabaseClient databaseClient,
                            TransactionalOperator transactionalOperator) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
    }

    /**
     * 定时任务：每 5 分钟执行一次检查
     * 目标：清理最后活跃时间超过 3 小时期限的游客数据
     */
    @Scheduled(cron = "0 0/5 * * * ?")
    public void cleanupExpiredGuests() {
        Mono.defer(() -> {
                    log.info("开始执行定时任务：清理过期的游客数据...");

                    LocalDateTime thresholdTime = LocalDateTime.now().minusHours(3);

                    return findExpiredGuestUserIds(thresholdTime)
                            .flatMap(expiredGuestUserIds -> {
                                if (expiredGuestUserIds.isEmpty()) {
                                    log.info("清理过期游客数据 - 本轮没有命中过期游客，thresholdTime={}", thresholdTime);
                                    return Mono.just(0L);
                                }

                                log.warn("清理过期游客数据 - 命中过期游客 userIds={}", expiredGuestUserIds);

                                return findConversationIdsByUserIds(expiredGuestUserIds)
                                        .flatMap(expiredConversationIds -> {
                                            if (expiredConversationIds.isEmpty()) {
                                                log.info("清理过期游客数据 - 命中游客无对话记录 userIds={}", expiredGuestUserIds);
                                                return Mono.just(0L)
                                                        .flatMap(ignored -> deleteByIds("DELETE FROM search_history WHERE user_id IN (%s)", expiredGuestUserIds))
                                                        .doOnNext(deletedSearchHistory -> log.info("清理过期游客数据 - 已删除 {} 条搜索历史记录", deletedSearchHistory))
                                                        .flatMap(ignored -> deleteGuestUsersByIds(expiredGuestUserIds));
                                            }

                                            log.warn("清理过期游客数据 - 即将删除 conversationIds={}", expiredConversationIds);

                                            return deleteByIds("DELETE FROM message WHERE conversation_id IN (%s)", expiredConversationIds)
                                                    .doOnNext(deletedMessages -> log.info("清理过期游客数据 - 已删除 {} 条消息记录", deletedMessages))
                                                    .flatMap(ignored -> deleteByIds("DELETE FROM conversation WHERE user_id IN (%s)", expiredGuestUserIds))
                                                    .doOnNext(deletedConversations -> log.info("清理过期游客数据 - 已删除 {} 条对话记录", deletedConversations))
                                                    .flatMap(ignored -> deleteByIds("DELETE FROM search_history WHERE user_id IN (%s)", expiredGuestUserIds))
                                                    .doOnNext(deletedSearchHistory -> log.info("清理过期游客数据 - 已删除 {} 条搜索历史记录", deletedSearchHistory))
                                                    .flatMap(ignored -> deleteGuestUsersByIds(expiredGuestUserIds));
                                        });
                            })
                            .as(transactionalOperator::transactional);
                })
                .doOnSuccess(deletedUsers -> log.info("清理过期游客数据 - 任务完成，共清理 {} 个过期游客账号", deletedUsers))
                .doOnError(e -> log.error("清理过期游客数据任务执行失败: {}", e.getMessage(), e))
                .subscribe();
    }

    private Mono<List<Long>> findExpiredGuestUserIds(LocalDateTime thresholdTime) {
        String sql = "SELECT user_id FROM users " +
                "WHERE is_guest = 1 AND COALESCE(last_active_at, created_at) < :thresholdTime";

        return databaseClient.sql(sql)
                .bind("thresholdTime", thresholdTime)
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
}
