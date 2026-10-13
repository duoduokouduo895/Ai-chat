package org.example.ai_agent.knowledge.cleanup;

import org.example.ai_agent.document.domain.CleanupOperation;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class KnowledgeCleanupCommands {
    private final DatabaseClient database;
    private final TransactionalOperator transaction;
    @org.springframework.beans.factory.annotation.Value("${knowledge-base.cleanup.quiescence-seconds:720}")
    private long quiescenceSeconds = 720;

    public KnowledgeCleanupCommands(DatabaseClient database, TransactionalOperator transaction) {
        this.database = database;
        this.transaction = transaction;
    }

    public Mono<CleanupOperation> deleteDocument(Long id, Long userId, boolean admin) {
        return existing("DOCUMENT", id, userId, admin).switchIfEmpty(Mono.defer(() -> database.sql(
                        "SELECT knowledge_base_id FROM knowledge_documents WHERE id = :id")
                .bind("id", id).map((row, meta) -> row.get(0, Long.class)).one()
                .switchIfEmpty(Mono.error(unavailable()))
                .flatMap(kb -> lockOwner(kb, userId, admin).flatMap(owner -> database.sql(
                                "SELECT id FROM knowledge_documents WHERE id = :id AND knowledge_base_id = :kb FOR UPDATE")
                        .bind("id", id).bind("kb", kb).map((row, meta) -> row.get(0, Long.class)).one()
                        .switchIfEmpty(Mono.error(unavailable()))
                        .then(acceptDocument(id, kb, owner))
                        .then(existing("DOCUMENT", id, userId, admin))))))
                .as(transaction::transactional);
    }

    public Mono<CleanupOperation> deleteKnowledgeBase(Long id, Long userId, boolean admin) {
        return existing("KNOWLEDGE_BASE", id, userId, admin).switchIfEmpty(Mono.defer(() ->
                lockOwner(id, userId, admin).flatMap(owner -> database.sql("""
                        UPDATE knowledge_bases SET deletion_status = 'DELETING', status = 'DELETING',
                            document_count = 0, updated_at = CURRENT_TIMESTAMP WHERE id = :id
                        """).bind("id", id).fetch().rowsUpdated()
                        .then(database.sql("""
                                UPDATE ingestion_runs SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                                WHERE knowledge_base_id = :id AND status IN ('PENDING', 'PROCESSING', 'RETRY')
                                """).bind("id", id).fetch().rowsUpdated())
                        .then(insert("KNOWLEDGE_BASE", id, owner))
                        .then(existing("KNOWLEDGE_BASE", id, userId, admin)))))
                .as(transaction::transactional);
    }

    private Mono<Long> lockOwner(Long id, Long userId, boolean admin) {
        return database.sql("SELECT creator_id FROM knowledge_bases WHERE id = :id FOR UPDATE")
                .bind("id", id).map((row, metadata) -> row.get(0, Long.class)).one()
                .filter(owner -> admin || owner.equals(userId)).switchIfEmpty(Mono.error(unavailable()));
    }

    Mono<Void> acceptDocument(Long id, Long knowledgeBaseId, Long owner) {
        return database.sql("""
                        UPDATE knowledge_documents SET deletion_status = 'DELETING', updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND deletion_status = 'ACTIVE'
                        """).bind("id", id).fetch().rowsUpdated()
                .flatMap(changed -> changed == 0 ? Mono.empty() : database.sql("""
                        UPDATE knowledge_bases SET document_count = GREATEST(document_count - 1, 0)
                        WHERE id = :id AND deletion_status = 'ACTIVE'
                        """).bind("id", knowledgeBaseId).fetch().rowsUpdated().then())
                .then(database.sql("""
                        UPDATE ingestion_runs SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
                        WHERE document_id = :id AND status IN ('PENDING', 'PROCESSING', 'RETRY')
                        """).bind("id", id).fetch().rowsUpdated())
                .then(insert("DOCUMENT", id, owner));
    }

    private Mono<Void> insert(String type, Long id, Long owner) {
        return database.sql("""
                        INSERT IGNORE INTO asset_cleanup_tasks (id, resource_type, resource_id, owner_id, next_attempt_at)
                        VALUES (:operationId, :type, :id, :owner, :nextAttempt)
                        """).bind("operationId", UUID.randomUUID().toString()).bind("type", type)
                .bind("id", id).bind("owner", owner)
                .bind("nextAttempt", LocalDateTime.now().plusSeconds(Math.max(720, Math.min(3600, quiescenceSeconds))))
                .fetch().rowsUpdated().then();
    }

    public Mono<CleanupOperation> get(String operationId, Long userId, boolean admin) {
        return database.sql("""
                        SELECT id, status, step, attempt, error_code, created_at, updated_at FROM asset_cleanup_tasks
                        WHERE id = :id AND (owner_id = :userId OR :admin = true)
                        """).bind("id", operationId).bind("userId", userId).bind("admin", admin)
                .map((row, meta) -> view(row)).one().switchIfEmpty(Mono.error(unavailable()));
    }

    public Flux<CleanupOperation> failed(boolean admin) {
        if (!admin) return Flux.error(unavailable());
        return database.sql("""
                        SELECT id, status, step, attempt, error_code, created_at, updated_at FROM asset_cleanup_tasks
                        WHERE status = 'NEEDS_ATTENTION' ORDER BY updated_at LIMIT 100
                        """).map((row, meta) -> view(row)).all();
    }

    public Mono<CleanupOperation> retry(String operationId, Long userId, boolean admin) {
        if (!admin) return Mono.error(unavailable());
        return database.sql("""
                        UPDATE asset_cleanup_tasks SET status = 'PENDING', attempt = 0, error_code = NULL,
                            next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND status = 'NEEDS_ATTENTION'
                        """).bind("id", operationId).fetch().rowsUpdated().then(get(operationId, userId, true));
    }

    private Mono<CleanupOperation> existing(String type, Long id, Long userId, boolean admin) {
        return database.sql("""
                        SELECT id, status, step, attempt, error_code, created_at, updated_at FROM asset_cleanup_tasks
                        WHERE resource_type = :type AND resource_id = :id AND (owner_id = :userId OR :admin = true)
                        """).bind("type", type).bind("id", id).bind("userId", userId).bind("admin", admin)
                .map((row, meta) -> view(row)).one();
    }

    private CleanupOperation view(io.r2dbc.spi.Row row) {
        return new CleanupOperation(row.get("id", String.class), row.get("status", String.class),
                row.get("step", Integer.class), row.get("attempt", Integer.class), row.get("error_code", String.class),
                row.get("created_at", LocalDateTime.class), row.get("updated_at", LocalDateTime.class));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_UNAVAILABLE");
    }
}
