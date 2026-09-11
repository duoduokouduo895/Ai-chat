package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class EmbeddingServiceImplTest {
    @Test
    @SuppressWarnings("unchecked")
    void retryAfterPartialExternalWriteReusesIdsAndPreservesSegments() {
        var registry = mock(EmbeddingStoreRegistry.class);
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        var profile = new EmbeddingStoreRegistry.EmbeddingProfile("model", "v1", 2, "test-index", true);
        when(registry.requireProfile("model", "test-index")).thenReturn(profile);
        when(registry.getWritableStore(profile)).thenReturn(store);
        Map<String, TextSegment> persisted = new HashMap<>();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<TextSegment> segments = invocation.getArgument(2);
            persisted.put(ids.getFirst(), segments.getFirst());
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("simulated connection loss");
            for (int i = 0; i < ids.size(); i++) persisted.put(ids.get(i), segments.get(i));
            return null;
        }).when(store).addAll(anyList(), anyList(), anyList());
        var service = new EmbeddingServiceImpl(mock(AiModelRegistry.class), registry, mock(ActiveKnowledgeChunks.class));
        var operation = service.storeEmbeddings(1L, 2L, List.of(3L, 4L),
                List.of(Embedding.from(new float[]{1, 0}), Embedding.from(new float[]{0, 1})),
                List.of(TextSegment.from("first"), TextSegment.from("second")), "model", "test-index");
        StepVerifier.create(operation).expectError().verify();
        StepVerifier.create(operation).assertNext(ids -> assertEquals(2, ids.size())).verifyComplete();
        assertEquals(2, persisted.size());
        assertEquals("first", persisted.get(EmbeddingServiceImpl.stableEmbeddingId(profile, 2L, 3L)).text());
        assertNotEquals(EmbeddingServiceImpl.stableEmbeddingId(profile, 2L, 3L),
                EmbeddingServiceImpl.stableEmbeddingId(profile, 2L, 4L));
    }
}
