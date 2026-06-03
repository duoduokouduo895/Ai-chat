package org.example.ai_agent.service.impl.document;

import dev.langchain4j.data.embedding.Embedding;
import org.example.ai_agent.dto.document.DocumentChunkDto;
import org.example.ai_agent.dto.document.DocumentProcessingMessage;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.entity.KnowledgeDocument;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentRepository;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry.EmbeddingProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class DocumentProcessingServiceImplTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private EmbeddingStoreRegistry embeddingStoreRegistry;
    @Mock
    private DocumentProcessingRecordService recordService;

    private DocumentProcessingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentProcessingServiceImpl();
        ReflectionTestUtils.setField(service, "documentRepository", documentRepository);
        ReflectionTestUtils.setField(service, "knowledgeBaseRepository", knowledgeBaseRepository);
        ReflectionTestUtils.setField(service, "documentChunkService", documentChunkService);
        ReflectionTestUtils.setField(service, "embeddingService", embeddingService);
        ReflectionTestUtils.setField(service, "embeddingStoreRegistry", embeddingStoreRegistry);
        ReflectionTestUtils.setField(service, "recordService", recordService);
        ReflectionTestUtils.setField(service, "embeddingBatchSize", 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processDocumentEmbedding_shouldProcessChunksInConfiguredBatches() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(9L);
        document.setKnowledgeBaseId(77L);

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(77L);

        EmbeddingProfile profile = new EmbeddingProfile(
                "embedding-default", "v1", 3, "knowledge_base_embeddings_v1", false);

        when(documentChunkService.getPendingEmbeddingChunks(9L)).thenReturn(Flux.just(
                chunk(1L), chunk(2L), chunk(3L), chunk(4L), chunk(5L)));
        when(recordService.startProcessing(9L, "EMBED")).thenReturn(Mono.just(new org.example.ai_agent.entity.DocumentProcessingRecord()));
        when(recordService.completeProcessing(9L, "EMBED", "Embedding completed"))
                .thenReturn(Mono.just(new org.example.ai_agent.entity.DocumentProcessingRecord()));
        when(documentRepository.findById(9L)).thenReturn(Mono.just(document));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(knowledgeBaseRepository.findById(77L)).thenReturn(Mono.just(knowledgeBase));
        when(embeddingStoreRegistry.resolveProfile(knowledgeBase)).thenReturn(profile);
        when(embeddingService.embedTexts(anyList(), eq("embedding-default")))
                .thenAnswer(invocation -> Mono.just(((List<?>) invocation.getArgument(0)).stream()
                        .map(ignored -> mock(Embedding.class))
                        .toList()));
        when(embeddingService.storeEmbeddings(
                eq(77L), eq(9L), anyList(), anyList(), anyList(),
                eq("embedding-default"), eq("knowledge_base_embeddings_v1")))
                .thenAnswer(invocation -> Mono.just(((List<Long>) invocation.getArgument(2)).stream()
                        .map(chunkId -> "embedding-" + chunkId)
                        .toList()));
        when(documentChunkService.batchUpdateChunkEmbeddingStatus(anyList(), anyList(), eq("COMPLETED")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.processDocumentEmbedding(DocumentProcessingMessage.createEmbedMessage(9L, 77L)))
                .expectNext(true)
                .verifyComplete();

        ArgumentCaptor<List<Long>> chunkIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkService, times(3)).batchUpdateChunkEmbeddingStatus(
                chunkIdsCaptor.capture(), anyList(), eq("COMPLETED"));
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(List.of(1L, 2L), List.of(3L, 4L), List.of(5L)),
                chunkIdsCaptor.getAllValues());
        org.junit.jupiter.api.Assertions.assertTrue(document.getIsEmbedded());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", document.getEmbeddingStatus());
    }

    private DocumentChunkDto chunk(Long id) {
        DocumentChunkDto chunk = new DocumentChunkDto();
        chunk.setId(id);
        chunk.setContent("chunk-" + id);
        return chunk;
    }
}
