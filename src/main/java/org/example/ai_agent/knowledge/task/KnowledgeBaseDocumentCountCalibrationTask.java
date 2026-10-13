package org.example.ai_agent.knowledge.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.ai_agent.common.scheduling.ScheduledTaskGuard;

/**
 * 定时对知识库文档数量（document_count）进行重新 COUNT 校准，确保强一致性。
 *
 * <p>本任务运行在每天凌晨 4 点低峰期，通过一条极速的级联子查询 SQL 直接更新 `knowledge_bases`，
 * 避免了列表读取阶段的 N+1 开销，同时兼顾了数据的强一致性兜底。</p>
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Component
public class KnowledgeBaseDocumentCountCalibrationTask {

    private final DatabaseClient databaseClient;
    private final int calibrationBatchSize;
    private final ScheduledTaskGuard taskGuard = new ScheduledTaskGuard();

    public KnowledgeBaseDocumentCountCalibrationTask(
            DatabaseClient databaseClient,
            @Value("${scheduled.calibration.knowledge-base-document-count.batch-size:100}") int calibrationBatchSize) {
        this.databaseClient = databaseClient;
        this.calibrationBatchSize = Math.max(1, calibrationBatchSize);
    }

    /**
     * 定时校准任务（每天凌晨4点执行）
     */
    @Scheduled(cron = "${scheduled.calibration.knowledge-base-document-count.cron:0 0 4 * * ?}")
    public void calibrateDocumentCount() {
        if (!taskGuard.tryStart()) {
            log.warn("知识库文档数量校准任务 - 上一轮尚未结束，跳过本次触发");
            return;
        }

        log.info("开始知识库文档数量（document_count）定时校准任务...");

        calibrateBatches(0L)
                .doOnSuccess(rows -> log.info("知识库文档数量校准任务完成，成功更新/校准了 {} 个活跃知识库的数据", rows))
                .doOnError(error -> log.error("知识库文档数量校准任务失败: errorType={}",
                        error.getClass().getSimpleName()))
                .doFinally(signal -> taskGuard.finish())
                .subscribe();
    }

    private Mono<Long> calibrateBatches(long afterId) {
        return calibrateBatch(afterId)
                .expand(batch -> batch.shouldContinue(calibrationBatchSize)
                        ? calibrateBatch(batch.lastId())
                        : Mono.empty())
                .map(CalibrationBatch::updatedRows)
                .reduce(0L, Long::sum);
    }

    private Mono<CalibrationBatch> calibrateBatch(long afterId) {
        return findActiveKnowledgeBaseIds(afterId)
                .flatMap(ids -> {
                    if (ids.isEmpty()) {
                        return Mono.just(new CalibrationBatch(afterId, 0, 0L));
                    }

                    return updateDocumentCounts(ids)
                            .map(updatedRows -> new CalibrationBatch(
                                    ids.get(ids.size() - 1), ids.size(), updatedRows));
                });
    }

    private Mono<List<Long>> findActiveKnowledgeBaseIds(long afterId) {
        String sql = """
                SELECT id
                FROM knowledge_bases
                WHERE status = 'ACTIVE'
                  AND id > :afterId
                ORDER BY id
                LIMIT :batchSize
                """;

        return databaseClient.sql(sql)
                .bind("afterId", afterId)
                .bind("batchSize", calibrationBatchSize)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .collectList();
    }

    private Mono<Long> updateDocumentCounts(List<Long> knowledgeBaseIds) {
        String sql = """
                UPDATE knowledge_bases kb
                SET kb.document_count = (
                    SELECT COUNT(*)
                    FROM knowledge_documents kd
                    WHERE kd.knowledge_base_id = kb.id
                )
                WHERE kb.id IN (%s)
                """.formatted(namedParameters("id", knowledgeBaseIds.size()));

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int index = 0; index < knowledgeBaseIds.size(); index++) {
            spec = spec.bind("id" + index, knowledgeBaseIds.get(index));
        }
        return spec.fetch().rowsUpdated();
    }

    private String namedParameters(String prefix, int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> ":" + prefix + index)
                .collect(Collectors.joining(", "));
    }

    private record CalibrationBatch(long lastId, int selectedCount, long updatedRows) {

        private boolean shouldContinue(int batchSize) {
            return selectedCount == batchSize;
        }
    }
}
