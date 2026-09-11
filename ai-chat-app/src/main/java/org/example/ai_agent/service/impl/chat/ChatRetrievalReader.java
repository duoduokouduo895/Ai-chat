package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.knowledge.retrieval.RetrievalOutcome;
import org.example.ai_agent.knowledge.retrieval.RetrievalSnapshot;
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

    public Mono<org.example.ai_agent.entity.Message> attach(org.example.ai_agent.entity.Message message, Long actorId, boolean admin) {
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
                .flatMap(snapshot -> Flux.fromIterable(snapshot.sources())
                        .concatMap(source -> resolve(source, actorId, admin))
                        .collectList().map(sources -> new View(snapshot.status(), snapshot.channels(), sources)));
    }

    private Mono<SourceView> resolve(RetrievalSnapshot.Source source, Long actorId, boolean admin) {
        var unavailable = new SourceView(source.knowledgeBaseId(), source.documentId(), source.versionId(),
                source.chunkId(), false, null, null);
        if (source.knowledgeBaseId() == null || source.documentId() == null || source.chunkId() == null
                || actorId == null) return Mono.just(unavailable);
        return database.sql("""
                        SELECT d.title, d.file_name, kb.name AS knowledge_base_name, c.version_id
                        FROM knowledge_documents d
                        JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                        JOIN knowledge_document_chunks c ON c.document_id = d.id
                        WHERE d.id = :documentId AND kb.id = :knowledgeBaseId AND c.id = :chunkId
                          AND d.deletion_status = 'ACTIVE' AND kb.deletion_status = 'ACTIVE'
                          AND kb.status = 'ACTIVE'
                          AND (:admin = TRUE OR kb.creator_id = :actorId OR kb.is_public = TRUE)
                          AND (:versionId = 0 OR c.version_id = :versionId)
                        """)
                .bind("documentId", source.documentId()).bind("knowledgeBaseId", source.knowledgeBaseId())
                .bind("chunkId", source.chunkId()).bind("actorId", actorId).bind("admin", admin)
                .bind("versionId", source.versionId() == null ? 0L : source.versionId())
                .map((row, metadata) -> {
                    String title = row.get("title", String.class);
                    return new SourceView(source.knowledgeBaseId(), source.documentId(),
                            row.get("version_id", Long.class), source.chunkId(), true,
                            title == null || title.isBlank() ? row.get("file_name", String.class) : title,
                            row.get("knowledge_base_name", String.class));
                }).one().defaultIfEmpty(unavailable);
    }
}
