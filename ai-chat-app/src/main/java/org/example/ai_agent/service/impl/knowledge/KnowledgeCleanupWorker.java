package org.example.ai_agent.service.impl.knowledge;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.OssService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class KnowledgeCleanupWorker {
    private final DatabaseClient database;
    private final TransactionalOperator transaction;
    private final KnowledgeCleanupCommands commands;
    private final EmbeddingService embeddings;
    private final KnowledgeChunkSearchService search;
    private final OssService oss;
    private final MeterRegistry meters;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Disposable subscription;
    @Value("${knowledge-base.cleanup.enabled:true}")
    private boolean enabled = true;
    @Value("${knowledge-base.cleanup.max-attempts:8}")
    private int maxAttempts = 8;

    public KnowledgeCleanupWorker(DatabaseClient database, TransactionalOperator transaction,
            KnowledgeCleanupCommands commands, EmbeddingService embeddings,
            KnowledgeChunkSearchService search, OssService oss, MeterRegistry meters) {
        this.database = database; this.transaction = transaction; this.commands = commands;
        this.embeddings = embeddings; this.search = search; this.oss = oss; this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${knowledge-base.cleanup.poll-delay-ms:5000}")
    public void poll() {
        if (!enabled || !running.compareAndSet(false, true)) return;
        subscription = drain().doFinally(signal -> running.set(false))
                .subscribe(unused -> {}, error -> meters.counter("knowledge.cleanup.poll.errors").increment());
    }

    public Mono<Void> drain() {
        return database.sql("""
                        SELECT id FROM asset_cleanup_tasks
                        WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                           OR (status = 'PROCESSING' AND lease_until <= CURRENT_TIMESTAMP)
                        ORDER BY next_attempt_at, id LIMIT 4
                        """).map((row, meta) -> row.get(0, String.class)).all()
                .concatMap(this::process).then();
    }

    private Mono<Void> process(String id) {
        String token = UUID.randomUUID().toString();
        return database.sql("""
                        UPDATE asset_cleanup_tasks SET status = 'PROCESSING', lease_token = :token,
                            lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 10 MINUTE), attempt = attempt + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                            OR (status = 'PROCESSING' AND lease_until <= CURRENT_TIMESTAMP))
                        """).bind("id", id).bind("token", token).fetch().rowsUpdated()
                .filter(count -> count == 1)
                .flatMap(unused -> database.sql("""
                        SELECT resource_type, resource_id, owner_id, step, attempt FROM asset_cleanup_tasks WHERE id = :id
                        """).bind("id", id).map((row, meta) -> new Task(id, token,
                        row.get("resource_type", String.class), row.get("resource_id", Long.class),
                        row.get("owner_id", Long.class), row.get("step", Integer.class), row.get("attempt", Integer.class))).one())
                .flatMap(task -> waitForWriters(task).then(Mono.defer(() -> step(task)))
                        .timeout(Duration.ofMinutes(3))
                        .flatMap(done -> advance(task, done))
                        .onErrorResume(error -> retry(task, error)))
                .then();
    }

    private Mono<Void> waitForWriters(Task task) {
        String predicate = switch (task.type()) {
            case "DOCUMENT" -> "r.document_id = :id";
            case "KNOWLEDGE_BASE" -> "r.knowledge_base_id = :id";
            case "VERSION" -> "r.version_id = :id";
            default -> throw new IllegalArgumentException("Unknown cleanup resource");
        };
        return database.sql("SELECT COUNT(*) FROM ingestion_runs r WHERE " + predicate
                        + " AND r.lease_until > CURRENT_TIMESTAMP")
                .bind("id", task.resource()).map((row, meta) -> row.get(0, Long.class)).one()
                .flatMap(count -> count == 0 ? Mono.empty() : Mono.error(new WritersActiveException()));
    }

    private Mono<Boolean> step(Task task) {
        return switch (task.type()) {
            case "DOCUMENT" -> documentStep(task);
            case "VERSION" -> versionStep(task);
            case "KNOWLEDGE_BASE" -> knowledgeBaseStep(task);
            default -> Mono.error(new IllegalArgumentException("Unknown cleanup resource"));
        };
    }

    private Mono<Boolean> documentStep(Task task) {
        Mono<Void> action = switch (task.step()) {
            case 0 -> requireSuccess(embeddings.deleteDocumentEmbeddings(task.resource()));
            case 1 -> search.deleteDocumentChunks(task.resource());
            case 2 -> deleteOss(task.resource());
            // Repeat external cleanup after the quiescence window before removing the last identifiers.
            case 3 -> requireSuccess(embeddings.deleteDocumentEmbeddings(task.resource()))
                    .then(search.deleteDocumentChunks(task.resource())).then(deleteOss(task.resource()));
            default -> database.sql("DELETE FROM knowledge_documents WHERE id = :id AND deletion_status = 'DELETING'")
                    .bind("id", task.resource()).fetch().rowsUpdated().then();
        };
        return action.thenReturn(task.step() >= 4);
    }

    private Mono<Void> deleteOss(Long documentId) {
        return database.sql("SELECT oss_path FROM knowledge_documents WHERE id = :id AND deletion_status = 'DELETING'")
                .bind("id", documentId).map((row, meta) -> java.util.Objects.toString(row.get(0, String.class), ""))
                .one().flatMap(path -> path.isBlank() ? Mono.empty() : requireSuccess(oss.deleteFile(path)));
    }

    private Mono<Boolean> versionStep(Task task) {
        return database.sql("""
                        SELECT COUNT(*) FROM document_versions v WHERE v.id = :id
                          AND (v.activation_status <> 'RETIRED' OR EXISTS
                            (SELECT 1 FROM knowledge_documents d WHERE d.active_version_id = v.id))
                        """).bind("id", task.resource()).map((row, meta) -> row.get(0, Long.class)).one()
                .flatMap(active -> active == 0 ? Mono.just(true) : Mono.error(new IllegalStateException("VERSION_STILL_ACTIVE")))
                .then(database.sql("SELECT id FROM knowledge_document_chunks WHERE version_id = :id ORDER BY id")
                        .bind("id", task.resource()).map((row, meta) -> row.get(0, Long.class)).all().collectList())
                .flatMap(ids -> {
                    Mono<Void> action = task.step() < 2
                            ? reactor.core.publisher.Flux.fromIterable(ids).buffer(100).concatMap(batch ->
                                requireSuccess(embeddings.deleteChunkEmbeddings(batch)).then(search.deleteVersionChunks(batch))).then()
                            : database.sql("DELETE FROM document_versions WHERE id = :id AND activation_status = 'RETIRED'")
                                .bind("id", task.resource()).fetch().rowsUpdated().then();
                    return action.thenReturn(task.step() >= 2);
                });
    }

    private Mono<Boolean> knowledgeBaseStep(Task task) {
        return database.sql("""
                        SELECT id FROM knowledge_documents WHERE knowledge_base_id = :id AND deletion_status = 'ACTIVE'
                        ORDER BY id LIMIT 50
                        """).bind("id", task.resource()).map((row, meta) -> row.get(0, Long.class)).all().collectList()
                .flatMap(ids -> ids.isEmpty() ? Mono.just(true) : reactor.core.publisher.Flux.fromIterable(ids)
                        .concatMap(id -> commands.acceptDocument(id, task.resource(), task.owner()).as(transaction::transactional))
                        .then(Mono.error(new WritersActiveException())))
                .then(database.sql("SELECT COUNT(*) FROM knowledge_documents WHERE knowledge_base_id = :id")
                        .bind("id", task.resource()).map((row, meta) -> row.get(0, Long.class)).one())
                .flatMap(count -> count == 0 ? requireSuccess(embeddings.deleteKnowledgeBaseEmbeddings(task.resource()))
                        .then(search.deleteKnowledgeBaseChunks(task.resource()))
                        .then(database.sql("DELETE FROM knowledge_bases WHERE id = :id AND deletion_status = 'DELETING'")
                                .bind("id", task.resource()).fetch().rowsUpdated()).thenReturn(true)
                        : Mono.error(new WritersActiveException()));
    }

    private Mono<Void> advance(Task task, boolean done) {
        return database.sql("""
                        UPDATE asset_cleanup_tasks SET status = :status, step = step + 1, attempt = 0,
                            lease_token = NULL, lease_until = NULL, error_code = NULL,
                            next_attempt_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP,
                            completed_at = IF(:done, CURRENT_TIMESTAMP, NULL)
                        WHERE id = :id AND lease_token = :token AND status = 'PROCESSING' AND lease_until > CURRENT_TIMESTAMP
                        """).bind("status", done ? "COMPLETED" : "PENDING").bind("done", done)
                .bind("id", task.id()).bind("token", task.token()).fetch().rowsUpdated()
                .filter(count -> count == 1).switchIfEmpty(Mono.error(new IllegalStateException("CLEANUP_LEASE_LOST")))
                .doOnNext(unused -> { if (done) meters.counter("knowledge.cleanup.completed").increment(); }).then();
    }

    private Mono<Void> retry(Task task, Throwable error) {
        boolean waiting = error instanceof WritersActiveException;
        boolean exhausted = !waiting && task.attempt() >= Math.max(1, maxAttempts);
        long delay = waiting ? 30 : Math.min(3600, 5L << Math.min(task.attempt(), 9));
        if (exhausted) meters.counter("knowledge.cleanup.needs_attention").increment();
        return database.sql("""
                        UPDATE asset_cleanup_tasks SET status = :status, error_code = :code,
                            attempt = IF(:waiting, GREATEST(attempt - 1, 0), attempt), lease_token = NULL, lease_until = NULL,
                            next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL :delay SECOND), updated_at = CURRENT_TIMESTAMP
                        WHERE id = :id AND lease_token = :token AND status = 'PROCESSING'
                        """).bind("status", exhausted ? "NEEDS_ATTENTION" : "PENDING")
                .bind("code", waiting ? "WAITING_FOR_PROCESSING" : "CLEANUP_STEP_FAILED")
                .bind("waiting", waiting).bind("delay", delay).bind("id", task.id()).bind("token", task.token())
                .fetch().rowsUpdated().then();
    }

    private Mono<Void> requireSuccess(Mono<Boolean> operation) {
        return operation.filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("External cleanup failed"))).then();
    }

    @PreDestroy
    public void stop() {
        enabled = false;
        Disposable current = subscription;
        if (current != null) current.dispose();
    }

    private record Task(String id, String token, String type, Long resource, Long owner, int step, int attempt) {}
    private static class WritersActiveException extends RuntimeException {}
}
