package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.example.ai_agent.knowledge.retrieval.RetrievalOutcome;
import org.example.ai_agent.knowledge.retrieval.RetrievalSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatRetrievalReaderTest {
    private final DatabaseClient database = mock(DatabaseClient.class);
    private final DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ChatRetrievalReader reader = new ChatRetrievalReader(database, json);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void verifiesAllSourcesInOneQueryAndKeepsSnapshotOrder() throws Exception {
        when(database.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        List<Row> rows = List.of(
                row(Map.of("chunk_id", 30L, "version_id", 3L, "document_id", 20L, "knowledge_base_id", 10L,
                        "file_name", "c.txt", "knowledge_base_name", "kb")),
                row(Map.of("chunk_id", 31L, "version_id", 4L, "document_id", 21L, "knowledge_base_id", 10L,
                        "title", "B 手册", "knowledge_base_name", "kb")),
                row(Map.of("chunk_id", 32L, "version_id", 5L, "document_id", 99L, "knowledge_base_id", 10L,
                        "title", "moved", "knowledge_base_name", "kb")));
        when(spec.map(any(BiFunction.class))).thenAnswer(call -> {
            BiFunction<Row, RowMetadata, Object> mapper = call.getArgument(0);
            RowsFetchSpec fetch = mock(RowsFetchSpec.class);
            when(fetch.all()).thenReturn(Flux.fromIterable(rows).map(row -> mapper.apply(row, null)));
            return fetch;
        });
        String snapshot = json.writeValueAsString(new RetrievalSnapshot(RetrievalOutcome.Status.FOUND, List.of(), List.of(
                new RetrievalSnapshot.Source(10L, 21L, 4L, 31L),   // available, title
                new RetrievalSnapshot.Source(10L, 20L, null, 30L), // available, file name fallback
                new RetrievalSnapshot.Source(10L, 20L, 7L, 30L),   // version no longer matches
                new RetrievalSnapshot.Source(10L, 22L, 5L, 32L),   // chunk belongs to another document
                new RetrievalSnapshot.Source(10L, 23L, 6L, 33L),   // not readable / deleted
                new RetrievalSnapshot.Source(10L, null, 6L, 34L)))); // incomplete reference

        StepVerifier.create(reader.read(snapshot, 42L, false))
                .assertNext(view -> {
                    var sources = view.sources();
                    assertEquals(List.of(31L, 30L, 30L, 32L, 33L, 34L),
                            sources.stream().map(ChatRetrievalReader.SourceView::chunkId).toList());
                    assertEquals(List.of(true, true, false, false, false, false),
                            sources.stream().map(ChatRetrievalReader.SourceView::available).toList());
                    assertEquals("B 手册", sources.get(0).documentTitle());
                    assertEquals("c.txt", sources.get(1).documentTitle());
                    assertEquals(3L, sources.get(1).versionId());
                    assertNull(sources.get(3).documentTitle());
                }).verifyComplete();

        verify(database, times(1)).sql(anyString());
        verify(spec).bind("chunkIds", List.of(31L, 30L, 32L, 33L));
    }

    @Test
    void anonymousActorSeesNothingWithoutQuerying() throws Exception {
        String snapshot = json.writeValueAsString(new RetrievalSnapshot(RetrievalOutcome.Status.FOUND, List.of(),
                List.of(new RetrievalSnapshot.Source(10L, 20L, 3L, 30L))));

        StepVerifier.create(reader.read(snapshot, null, false))
                .assertNext(view -> assertFalse(view.sources().getFirst().available()))
                .verifyComplete();
        verifyNoInteractions(database);
    }

    private static Row row(Map<String, Object> values) {
        Row row = mock(Row.class);
        when(row.get(anyString(), any())).thenAnswer(call -> values.get((String) call.getArgument(0)));
        return row;
    }
}
