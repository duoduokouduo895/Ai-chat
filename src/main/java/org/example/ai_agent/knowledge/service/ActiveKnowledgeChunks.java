package org.example.ai_agent.knowledge.service;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/** MySQL visibility is authoritative even while search projections lag behind. */
@Component
public class ActiveKnowledgeChunks {
    private final DatabaseClient database;

    public ActiveKnowledgeChunks(DatabaseClient database) {
        this.database = database;
    }

    public Mono<List<Long>> ids(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) return Mono.just(List.of());
        return database.sql("""
                        SELECT c.id FROM knowledge_document_chunks c
                        INNER JOIN knowledge_documents d ON d.id = c.document_id
                        INNER JOIN knowledge_bases k ON k.id = d.knowledge_base_id
                        WHERE k.id IN (:ids) AND k.status = 'ACTIVE'
                          AND k.deletion_status = 'ACTIVE' AND d.deletion_status = 'ACTIVE'
                          AND d.parse_status = 'COMPLETED'
                          AND (c.version_id = d.active_version_id OR
                               (c.version_id IS NULL AND c.generation = 1 AND NOT EXISTS
                                   (SELECT 1 FROM ingestion_runs r WHERE r.document_id = d.id)
                                AND NOT EXISTS (SELECT 1 FROM document_versions v
                                                WHERE v.document_id = d.id AND v.generation > 1)))
                        ORDER BY c.id LIMIT 60001
                        """).bind("ids", knowledgeBaseIds)
                .map((row, metadata) -> row.get("id", Long.class)).all().collectList()
                .flatMap(ids -> ids.size() > 60000
                        ? Mono.error(new IllegalStateException("RETRIEVAL_SCOPE_TOO_LARGE")) : Mono.just(ids));
    }
}
