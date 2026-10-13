package org.example.ai_agent.knowledge.cleanup;

import org.example.ai_agent.knowledge.entity.KnowledgeBase;
import org.example.ai_agent.knowledge.entity.KnowledgeDocument;
import org.example.ai_agent.document.repository.DocumentProcessingRecordRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentChunkRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.knowledge.service.EmbeddingService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.common.storage.OssService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("cleanup")
class KnowledgeAssetCleanupServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeDocumentChunkRepository chunkRepository;
    @Mock
    private DocumentProcessingRecordRepository recordRepository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private OssService ossService;

    private KnowledgeAssetCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new KnowledgeAssetCleanupService(
                documentRepository, chunkRepository, recordRepository,
                embeddingService, knowledgeChunkSearchService, ossService);
    }

    @Test
    void deleteDocumentAggregate_shouldDeleteExternalResourcesAndDatabaseRecord() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(10L);
        document.setOssPath("kb_1/doc.pdf");

        when(embeddingService.deleteDocumentEmbeddings(10L)).thenReturn(Mono.just(true));
        when(knowledgeChunkSearchService.deleteDocumentChunks(10L)).thenReturn(Mono.empty());
        when(ossService.deleteFile("kb_1/doc.pdf")).thenReturn(Mono.just(true));
        when(chunkRepository.deleteByDocumentId(10L)).thenReturn(Mono.just(3L));
        when(recordRepository.deleteByDocumentId(10L)).thenReturn(Mono.just(2L));
        when(documentRepository.deleteById(10L)).thenReturn(Mono.empty());

        StepVerifier.create(cleanupService.deleteDocumentAggregate(document))
                .verifyComplete();

        InOrder inOrder = inOrder(embeddingService, ossService, chunkRepository, recordRepository, documentRepository);
        inOrder.verify(embeddingService).deleteDocumentEmbeddings(10L);
        inOrder.verify(ossService).deleteFile("kb_1/doc.pdf");
        inOrder.verify(chunkRepository).deleteByDocumentId(10L);
        inOrder.verify(recordRepository).deleteByDocumentId(10L);
        inOrder.verify(documentRepository).deleteById(10L);
    }

    @Test
    void deleteKnowledgeBaseAssets_shouldDeleteAllDocumentsThenKnowledgeBaseEmbeddings() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(88L);

        KnowledgeDocument doc1 = new KnowledgeDocument();
        doc1.setId(1L);
        doc1.setOssPath("kb_88/doc1.pdf");
        KnowledgeDocument doc2 = new KnowledgeDocument();
        doc2.setId(2L);
        doc2.setOssPath("kb_88/doc2.pdf");

        when(documentRepository.findByKnowledgeBaseId(88L)).thenReturn(Flux.just(doc1, doc2));
        when(embeddingService.deleteDocumentEmbeddings(anyLong())).thenReturn(Mono.just(true));
        when(knowledgeChunkSearchService.deleteDocumentChunks(anyLong())).thenReturn(Mono.empty());
        when(ossService.deleteFile("kb_88/doc1.pdf")).thenReturn(Mono.just(true));
        when(ossService.deleteFile("kb_88/doc2.pdf")).thenReturn(Mono.just(true));
        when(chunkRepository.deleteByDocumentId(anyLong())).thenReturn(Mono.just(1L));
        when(recordRepository.deleteByDocumentId(anyLong())).thenReturn(Mono.just(1L));
        when(documentRepository.deleteById(anyLong())).thenReturn(Mono.empty());
        when(knowledgeChunkSearchService.deleteKnowledgeBaseChunks(88L)).thenReturn(Mono.empty());
        when(embeddingService.deleteKnowledgeBaseEmbeddings(88L)).thenReturn(Mono.just(true));

        StepVerifier.create(cleanupService.deleteKnowledgeBaseAssets(knowledgeBase))
                .verifyComplete();

        verify(documentRepository, times(2)).deleteById(anyLong());
        verify(embeddingService, times(2)).deleteDocumentEmbeddings(anyLong());
        verify(knowledgeChunkSearchService, times(2)).deleteDocumentChunks(anyLong());
        verify(knowledgeChunkSearchService).deleteKnowledgeBaseChunks(88L);
        verify(embeddingService).deleteKnowledgeBaseEmbeddings(88L);
    }
}
