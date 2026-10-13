package org.example.ai_agent.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.knowledge.domain.RetrievalOutcome;
import org.example.ai_agent.knowledge.domain.RetrievalSnapshot;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Resolves durable identifiers against current resource ownership; never reads chunk text. */
@Component
public class ChatRetrievalReader {
    private final DatabaseClient database;
    private final ObjectMapper json;

    public ChatRetrievalReader(DatabaseClient database, ObjectMapper json) {
        this.database = database;
        this.json = json;
    }

    public record SourceView(Long knowledgeBaseId, Long documentId, Long versionId, Long chunkId,
                             boolean available, String documentTitle, String knowledgeBaseName) { }
    public record View(RetrievalOutcome.Status status, List<RetrievalOutcome.Channel> channels,
                       List<SourceView> sources) { }

    public Mono<org.example.ai_agent.chat.entity.Message> attach(org.example.ai_agent.chat.entity.Message message, Long actorId, boolean admin) {
        if (!"ai".equals(message.getSender()) || message.getId() == null) return Mono.just(message);
        return database.sql("SELECT id, retrieval_json FROM chat_turn WHERE assistant_message_id = :messageId AND status = 'COMPLETED'")
                .bind("messageId", message.getId())
                .map((row, metadata) -> {
                    message.setChatTurnId(row.get("id", String.class));
                    String snapshot = row.get("retrieval_json", String.class);
                    return snapshot == null ? "" : snapshot;
                }).one()
                .flatMap(snapshot -> read(snapshot, actorId, admin))
                .doOnNext(message::setRetrieval).thenReturn(message);
    }

    public Mono<View> read(String snapshotJson, Long actorId, boolean admin) {
        if (snapshotJson == null || snapshotJson.isBlank()) return Mono.empty();
        return Mono.fromCallable(() -> json.readValue(snapshotJson, RetrievalSnapshot.class))
                .flatMap(snapshot -> resolve(snapshot.sources(), actorId, admin)
                        .map(sources -> new View(snapshot.status(), snapshot.channels(), sources)));
    }

    /** Verifies every source in one round trip; snapshot order is preserved. */
    private Mono<List<SourceView>> resolve(List<RetrievalSnapshot.Source> sources, Long actorId, boolean admin) {
        List<Long> chunkIds = actorId == null ? List.of() : sources.stream()
                .filter(ChatRetrievalReader::resolvable)
                .map(RetrievalSnapshot.Source::chunkId).distinct().toList();
        if (chunkIds.isEmpty()) return Mono.just(sources.stream().map(ChatRetrievalReader::unavailable).toList());
        return database.sql("""
                        SELECT c.id AS chunk_id, c.version_id, d.id AS document_id, kb.id AS knowledge_base_id,
                               d.title, d.file_name, kb.name AS knowledge_base_name
                        FROM knowledge_document_chunks c
                        JOIN knowledge_documents d ON d.id = c.document_id
                        JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                        WHERE c.id IN (:chunkIds)
                          AND d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE'
                          AND kb.status = 'ACTIVE'
                          AND (:admin = TRUE OR kb.creator_id = :actorId OR kb.is_public = TRUE)
                        """)
                .bind("chunkIds", chunkIds).bind("actorId", actorId).bind("admin", admin)
                .map((row, metadata) -> {
                    String title = row.get("title", String.class);
                    return new ChunkRow(row.get("chunk_id", Long.class), row.get("version_id", Long.class),
                            row.get("document_id", Long.class), row.get("knowledge_base_id", Long.class),
                            title == null || title.isBlank() ? row.get("file_name", String.class) : title,
                            row.get("knowledge_base_name", String.class));
                }).all()
                .collectMap(ChunkRow::chunkId)
                .map(rows -> sources.stream().map(source -> toView(source, rows.get(source.chunkId()))).toList());
    }

    private static SourceView toView(RetrievalSnapshot.Source source, ChunkRow row) {
        if (row == null || !resolvable(source)
                || !source.documentId().equals(row.documentId())
                || !source.knowledgeBaseId().equals(row.knowledgeBaseId())
                || (source.versionId() != null && source.versionId() != 0L
                    && !source.versionId().equals(row.versionId()))) {
            return unavailable(source);
        }
        return new SourceView(source.knowledgeBaseId(), source.documentId(), row.versionId(), source.chunkId(),
                true, row.documentTitle(), row.knowledgeBaseName());
    }

    private static boolean resolvable(RetrievalSnapshot.Source source) {
        return source.knowledgeBaseId() != null && source.documentId() != null && source.chunkId() != null;
    }

    private static SourceView unavailable(RetrievalSnapshot.Source source) {
        return new SourceView(source.knowledgeBaseId(), source.documentId(), source.versionId(),
                source.chunkId(), false, null, null);
    }

    private record ChunkRow(Long chunkId, Long versionId, Long documentId, Long knowledgeBaseId,
                            String documentTitle, String knowledgeBaseName) { }
}
