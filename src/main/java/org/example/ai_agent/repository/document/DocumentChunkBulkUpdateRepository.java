package org.example.ai_agent.repository.document;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 文档块批量更新。
 *
 * 使用单条 CASE UPDATE 避免逐块执行 findById + save。
 */
@Repository
public class DocumentChunkBulkUpdateRepository {

    private final DatabaseClient databaseClient;

    public DocumentChunkBulkUpdateRepository(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Long> updateEmbeddingStatus(List<Long> chunkIds, List<String> embeddingIds, String status) {
        validateArguments(chunkIds, embeddingIds, status);

        if (chunkIds.isEmpty()) {
            return Mono.just(0L);
        }

        String idParameters = namedParameters("id", chunkIds.size());
        String embeddingCases = IntStream.range(0, chunkIds.size())
                .mapToObj(index -> "WHEN :id" + index + " THEN :embeddingId" + index)
                .collect(Collectors.joining(" "));

        String sql = """
                UPDATE knowledge_document_chunks
                SET embedding_id = CASE id %s ELSE embedding_id END,
                    embedding_status = :status,
                    is_embedded = :isEmbedded,
                    embedding_error = NULL,
                    updated_at = :updatedAt
                WHERE id IN (%s)
                """.formatted(embeddingCases, idParameters);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("status", status)
                .bind("isEmbedded", DocumentProcessingConstants.Status.COMPLETED.equals(status))
                .bind("updatedAt", LocalDateTime.now());

        for (int index = 0; index < chunkIds.size(); index++) {
            spec = spec.bind("id" + index, chunkIds.get(index));
            String embeddingId = embeddingIds.get(index);
            spec = embeddingId != null
                    ? spec.bind("embeddingId" + index, embeddingId)
                    : spec.bindNull("embeddingId" + index, String.class);
        }

        return spec.fetch().rowsUpdated();
    }

    private void validateArguments(List<Long> chunkIds, List<String> embeddingIds, String status) {
        if (chunkIds == null || embeddingIds == null || chunkIds.size() != embeddingIds.size()) {
            throw new IllegalArgumentException("文档块ID与向量ID数量必须一致");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("文档块向量化状态不能为空");
        }
        if (chunkIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("文档块ID必须为正数");
        }
        if (new HashSet<>(chunkIds).size() != chunkIds.size()) {
            throw new IllegalArgumentException("文档块ID不能重复");
        }
    }

    private String namedParameters(String prefix, int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> ":" + prefix + index)
                .collect(Collectors.joining(", "));
    }
}
