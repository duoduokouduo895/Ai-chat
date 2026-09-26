package org.example.ai_agent.service.memory;

import org.example.ai_agent.conversation.CompletedContextTurn;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** MySQL access for derived conversation memory; every write is guarded by version and lease. */
@Component
public class ConversationMemoryStore {
    static final String PROMPT_VERSION = "v1";

    private final DatabaseClient database;
    private final TransactionalOperator transaction;

    public ConversationMemoryStore(DatabaseClient database, TransactionalOperator transaction) {
        this.database = database;
        this.transaction = transaction;
    }

    /** Constraints are read at the summary's version, so both halves come from one committed state. */
    public Mono<ConversationMemory> load(Long conversationId) {
        return database.sql("""
                        SELECT m.version, m.covered_through_seq, s.content
                        FROM conversation_memory m
                        LEFT JOIN conversation_summary s
                               ON s.conversation_id = m.conversation_id AND s.version = m.version
                        WHERE m.conversation_id = :id
                        """).bind("id", conversationId)
                .map((row, meta) -> new ConversationMemory(row.get("version", Integer.class),
                        row.get("covered_through_seq", Long.class), row.get("content", String.class), List.of()))
                .one()
                .flatMap(memory -> memory.version() == 0 ? Mono.just(memory) : database.sql("""
                                SELECT id, kind, content, source_seq FROM conversation_constraint
                                WHERE conversation_id = :id AND created_version <= :version
                                  AND (superseded_version IS NULL OR superseded_version > :version)
                                ORDER BY id
                                """).bind("id", conversationId).bind("version", memory.version())
                        .map((row, meta) -> new ConversationMemory.MemoryConstraint(row.get("id", Long.class),
                                row.get("kind", String.class), row.get("content", String.class),
                                row.get("source_seq", Long.class)))
                        .all().collectList()
                        .map(constraints -> new ConversationMemory(memory.version(), memory.coveredThroughSeq(),
                                memory.summary(), constraints)))
                .defaultIfEmpty(ConversationMemory.EMPTY);
    }

    /** Marks a conversation for compaction; an in-flight lease or a recent failure is left alone. */
    public Mono<Void> requestCompaction(Long conversationId, String modelId) {
        return database.sql("""
                        INSERT INTO conversation_memory (conversation_id, model_id, status)
                        VALUES (:id, :model, 'PENDING')
                        ON DUPLICATE KEY UPDATE
                            attempt = IF(status = 'FAILED' AND next_attempt_at <= CURRENT_TIMESTAMP, 0, attempt),
                            next_attempt_at = IF(status = 'IDLE', CURRENT_TIMESTAMP, next_attempt_at),
                            status = IF(status = 'IDLE' OR (status = 'FAILED' AND next_attempt_at <= CURRENT_TIMESTAMP),
                                        'PENDING', status),
                            model_id = :model
                        """).bind("id", conversationId).bind("model", modelId)
                .fetch().rowsUpdated().then();
    }

    public Flux<Long> due(int limit) {
        return database.sql("""
                        SELECT conversation_id FROM conversation_memory
                        WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                           OR (status = 'PROCESSING' AND lease_until <= CURRENT_TIMESTAMP)
                        ORDER BY next_attempt_at, conversation_id LIMIT :limit
                        """).bind("limit", limit).map((row, meta) -> row.get(0, Long.class)).all();
    }

    public Mono<Lease> claim(Long conversationId, Duration leaseDuration) {
        String token = UUID.randomUUID().toString();
        return database.sql("""
                        UPDATE conversation_memory SET status = 'PROCESSING', lease_token = :token,
                            lease_until = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL :seconds SECOND), attempt = attempt + 1
                        WHERE conversation_id = :id AND ((status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                            OR (status = 'PROCESSING' AND lease_until <= CURRENT_TIMESTAMP))
                        """).bind("id", conversationId).bind("token", token)
                .bind("seconds", Math.max(60, leaseDuration.toSeconds()))
                .fetch().rowsUpdated()
                .filter(count -> count == 1)
                .flatMap(ignored -> database.sql("""
                                SELECT model_id, attempt FROM conversation_memory WHERE conversation_id = :id
                                """).bind("id", conversationId)
                        .map((row, meta) -> new Object[]{row.get("model_id", String.class), row.get("attempt", Integer.class)})
                        .one())
                .flatMap(row -> load(conversationId).map(memory ->
                        new Lease(conversationId, token, (String) row[0], (Integer) row[1], memory)));
    }

    /** Nothing to compact: return to idle without touching the version. */
    public Mono<Void> release(Lease lease) {
        return database.sql("""
                        UPDATE conversation_memory SET status = 'IDLE', lease_token = NULL, lease_until = NULL,
                            attempt = 0, error_code = NULL
                        WHERE conversation_id = :id AND lease_token = :token
                        """).bind("id", lease.conversationId()).bind("token", lease.token())
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> fail(Lease lease, String errorCode, int maxAttempts, Duration backoff, Duration cooldown) {
        boolean exhausted = lease.attempt() >= maxAttempts;
        long delay = exhausted ? cooldown.toSeconds()
                : backoff.toSeconds() * (1L << Math.min(6, Math.max(0, lease.attempt() - 1)));
        return database.sql("""
                        UPDATE conversation_memory SET status = :status, lease_token = NULL, lease_until = NULL,
                            error_code = :error, next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL :delay SECOND)
                        WHERE conversation_id = :id AND lease_token = :token
                        """).bind("status", exhausted ? "FAILED" : "PENDING").bind("error", errorCode)
                .bind("delay", Math.max(1, delay))
                .bind("id", lease.conversationId()).bind("token", lease.token())
                .fetch().rowsUpdated().then();
    }

    /**
     * Publishes the next memory version atomically. Returns false when the lease was lost
     * or another writer advanced the version; the transaction is then rolled back.
     */
    public Mono<Boolean> commit(Lease lease, CompactionResult result, List<CompletedContextTurn> archived,
                                long coveredThroughSeq, boolean morePending, boolean archiveEnabled,
                                int versionsRetained) {
        int next = lease.memory().version() + 1;
        long id = lease.conversationId();
        Mono<Long> archive = archiveEnabled ? Flux.fromIterable(archived).concatMap(turn -> {
            long bytes = (long) turn.userText().getBytes(StandardCharsets.UTF_8).length
                    + turn.assistantText().getBytes(StandardCharsets.UTF_8).length;
            var spec = database.sql("""
                            INSERT IGNORE INTO conversation_archive_turn
                                (conversation_id, seq, turn_id, user_text, assistant_text, content_bytes, completed_at)
                            VALUES (:id, :seq, :turn, :user, :assistant, :bytes, :completed)
                            """).bind("id", id).bind("seq", turn.sequenceId()).bind("turn", turn.turnId())
                    .bind("user", turn.userText()).bind("assistant", turn.assistantText())
                    .bind("bytes", (int) Math.min(Integer.MAX_VALUE, bytes));
            spec = turn.completedAt() == null ? spec.bindNull("completed", java.time.LocalDateTime.class)
                    : spec.bind("completed", turn.completedAt());
            return spec.fetch().rowsUpdated().map(count -> count * bytes);
        }).reduce(0L, Long::sum) : Mono.just(0L);

        Mono<Void> supersede = Flux.fromIterable(result.removeConstraintIds()).concatMap(constraintId -> database.sql("""
                        UPDATE conversation_constraint SET status = 'SUPERSEDED', superseded_version = :version
                        WHERE id = :cid AND conversation_id = :id AND status = 'ACTIVE'
                        """).bind("version", next).bind("cid", constraintId).bind("id", id)
                .fetch().rowsUpdated()).then();
        Mono<Void> add = Flux.fromIterable(result.addConstraints()).concatMap(constraint -> database.sql("""
                        INSERT INTO conversation_constraint (conversation_id, kind, content, source_seq, created_version)
                        VALUES (:id, :kind, :content, :seq, :version)
                        """).bind("id", id).bind("kind", constraint.kind()).bind("content", constraint.content())
                .bind("seq", constraint.sourceSeq()).bind("version", next)
                .fetch().rowsUpdated()).then();
        Mono<Void> summary = database.sql("""
                        INSERT INTO conversation_summary
                            (conversation_id, version, covered_through_seq, content, token_estimate, model_id, prompt_version)
                        VALUES (:id, :version, :covered, :content, :tokens, :model, :prompt)
                        """).bind("id", id).bind("version", next).bind("covered", coveredThroughSeq)
                .bind("content", result.summary()).bind("tokens", result.summaryTokens())
                .bind("model", lease.modelId() == null ? "" : lease.modelId()).bind("prompt", PROMPT_VERSION)
                .fetch().rowsUpdated().then();
        Mono<Void> prune = database.sql("""
                        DELETE FROM conversation_summary WHERE conversation_id = :id AND version <= :oldest
                        """).bind("id", id).bind("oldest", next - Math.max(1, versionsRetained))
                .fetch().rowsUpdated().then();

        Mono<Boolean> work = archive.flatMap(bytes -> database.sql("""
                                UPDATE conversation_memory SET version = :next, covered_through_seq = :covered,
                                    status = :status, lease_token = NULL, lease_until = NULL, attempt = 0,
                                    error_code = NULL, next_attempt_at = CURRENT_TIMESTAMP,
                                    archive_bytes = archive_bytes + :bytes
                                WHERE conversation_id = :id AND version = :version AND lease_token = :token
                                """).bind("next", next).bind("covered", coveredThroughSeq)
                        .bind("status", morePending ? "PENDING" : "IDLE").bind("bytes", bytes)
                        .bind("id", id).bind("version", lease.memory().version()).bind("token", lease.token())
                        .fetch().rowsUpdated())
                .flatMap(updated -> updated == 1
                        ? supersede.then(add).then(summary).then(prune).thenReturn(true)
                        : Mono.error(new LeaseLostException()));
        return transaction.transactional(work)
                .onErrorResume(LeaseLostException.class, ignored -> Mono.just(false));
    }

    /** Exact recall over this conversation's archived turns only. */
    public Flux<ArchivedTurn> search(Long conversationId, String query, int limit) {
        return database.sql("""
                        SELECT seq, user_text, assistant_text,
                               MATCH(user_text, assistant_text) AGAINST (:query IN NATURAL LANGUAGE MODE) AS score
                        FROM conversation_archive_turn
                        WHERE conversation_id = :id
                          AND MATCH(user_text, assistant_text) AGAINST (:query IN NATURAL LANGUAGE MODE)
                        ORDER BY score DESC, seq DESC LIMIT :limit
                        """).bind("id", conversationId).bind("query", query).bind("limit", limit)
                .map((row, meta) -> new ArchivedTurn(row.get("seq", Long.class),
                        row.get("user_text", String.class), row.get("assistant_text", String.class)))
                .all();
    }

    /** Evicts the oldest archived turns in one batch when the total exceeds {@code maxBytes}. */
    public Mono<Long> evictOldestArchive(long maxBytes, int batchSize) {
        return database.sql("SELECT COALESCE(SUM(archive_bytes), 0) FROM conversation_memory")
                .map((row, meta) -> row.get(0, Number.class).longValue()).one()
                .filter(total -> total > maxBytes)
                .flatMap(total -> transaction.transactional(evictBatch(batchSize)))
                .defaultIfEmpty(0L);
    }

    private Mono<Long> evictBatch(int batchSize) {
        return database.sql("""
                        SELECT id, conversation_id, content_bytes FROM conversation_archive_turn
                        ORDER BY id LIMIT :limit FOR UPDATE
                        """).bind("limit", batchSize)
                .map((row, meta) -> new long[]{row.get("id", Long.class), row.get("conversation_id", Long.class),
                        row.get("content_bytes", Integer.class)})
                .all().collectList()
                .flatMap(rows -> {
                    if (rows.isEmpty()) return Mono.just(0L);
                    Map<Long, Long> freed = rows.stream().collect(Collectors.groupingBy(r -> r[1],
                            Collectors.summingLong(r -> r[2])));
                    Mono<Void> delete = Flux.fromIterable(rows).concatMap(r -> database.sql(
                                    "DELETE FROM conversation_archive_turn WHERE id = :id")
                            .bind("id", r[0]).fetch().rowsUpdated()).then();
                    Mono<Void> account = Flux.fromIterable(freed.entrySet()).concatMap(entry -> database.sql("""
                                    UPDATE conversation_memory
                                    SET archive_bytes = GREATEST(0, archive_bytes - :bytes)
                                    WHERE conversation_id = :id
                                    """).bind("bytes", entry.getValue()).bind("id", entry.getKey())
                            .fetch().rowsUpdated()).then();
                    return delete.then(account).thenReturn(freed.values().stream().mapToLong(Long::longValue).sum());
                });
    }

    public record Lease(long conversationId, String token, String modelId, int attempt, ConversationMemory memory) {
    }

    private static final class LeaseLostException extends RuntimeException {
        LeaseLostException() {
            super(null, null, false, false);
        }
    }
}
