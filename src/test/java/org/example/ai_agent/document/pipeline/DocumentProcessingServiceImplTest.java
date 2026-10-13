package org.example.ai_agent.document.pipeline;

import dev.langchain4j.data.embedding.Embedding;
import org.example.ai_agent.document.dto.DocumentChunkDto;
import org.example.ai_agent.document.dto.DocumentProcessingMessage;
import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.knowledge.entity.KnowledgeBase;
import org.example.ai_agent.knowledge.entity.KnowledgeDocument;
import org.example.ai_agent.knowledge.repository.KnowledgeBaseRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.example.ai_agent.document.domain.AcceptedIngestionRun;
import org.example.ai_agent.document.domain.IngestionStage;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.document.service.DocumentParserService;
import org.example.ai_agent.document.service.DocumentProcessingRecordService;
import org.example.ai_agent.knowledge.service.EmbeddingService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.common.storage.OssService;
import org.example.ai_agent.llm.AiModelRegistry;
import org.example.ai_agent.knowledge.service.EmbeddingStoreRegistry;
import org.example.ai_agent.knowledge.service.EmbeddingStoreRegistry.EmbeddingProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private DocumentParserService documentParserService;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private OssService ossService;
    @Mock
    private EmbeddingStoreRegistry embeddingStoreRegistry;
    @Mock
    private DocumentProcessingRecordService recordService;
    @Mock
    private AiModelRegistry aiModelRegistry;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private DocumentProcessingOutboxService outboxService;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private DocumentProcessingStages stages;
    @Mock
    private IngestionRunCommandService ingestionRunCommandService;

    private DocumentProcessingServiceImpl service;
    private DocumentProcessingProperties processingProperties;

    @BeforeEach
    void setUp() {
        lenient().when(ingestionRunCommandService.requireLegacyWritable(any())).thenReturn(Mono.empty());
        processingProperties = new DocumentProcessingProperties();
        DocumentEmbeddingBatchProcessor embeddingBatchProcessor = new DocumentEmbeddingBatchProcessor(
                documentRepository,
                knowledgeBaseRepository,
                documentChunkService,
                embeddingService,
                embeddingStoreRegistry,
                knowledgeChunkSearchService);
        service = new DocumentProcessingServiceImpl(
                documentRepository,
                documentChunkService,
                embeddingService,
                recordService,
                knowledgeChunkSearchService,
                stages,
                embeddingBatchProcessor,
                outboxService,
                transactionalOperator,
                ingestionRunCommandService,
                processingProperties,
                2);
        lenient().when(transactionalOperator.transactional(org.mockito.ArgumentMatchers.<Mono<Boolean>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void processDocumentEmbedding_shouldProcessChunksInConfiguredBatches() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(9L);
        document.setKnowledgeBaseId(77L);
        document.setTitle("测试文档");

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(77L);
        knowledgeBase.setName("测试知识库");

        EmbeddingProfile profile = new EmbeddingProfile(
                "embedding-default", "v1", 3, "knowledge_base_embeddings_v1", false);

        when(documentChunkService.getPendingEmbeddingChunks(9L)).thenReturn(Flux.just(
                chunk(1L), chunk(2L), chunk(3L), chunk(4L), chunk(5L)));
        when(documentChunkService.getDocumentChunks(9L)).thenReturn(Flux.just(completedChunk(1L)));
        when(recordService.startProcessing(9L, "EMBED")).thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        when(recordService.completeProcessing(9L, "EMBED", "Embedding completed"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
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
        when(knowledgeChunkSearchService.indexDocumentChunks(9L)).thenReturn(Mono.empty());

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
        verify(knowledgeChunkSearchService).indexDocumentChunks(9L);
    }

    @Test
    void processDocumentParsing_shouldPersistTheNextStageOutboxEventWithTheParsedContent() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(9L);
        document.setKnowledgeBaseId(77L);
        document.setFileName("readme.md");
        document.setFileType("md");

        DocumentProcessingMessage message = DocumentProcessingMessage.createParseMessage(9L, 77L, "auto", true);
        message.setRunId("00000000-0000-0000-0000-000000000009");
        message.setVersionId(91L);
        message.setGeneration(2L);
        when(recordService.startProcessing(9L, "PARSE"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        when(documentRepository.findById(9L)).thenReturn(Mono.just(document), Mono.just(document));
        when(documentRepository.save(document)).thenReturn(Mono.just(document));
        when(stages.parseDocumentFromOss(document, "auto")).thenReturn(Mono.just("parsed content"));
        when(outboxService.enqueue(any(DocumentProcessingMessage.class),
                eq(org.example.ai_agent.document.domain.DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK)))
                .thenReturn(Mono.just(true));
        when(recordService.completeProcessing(9L, "PARSE", "Content length: 14"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        when(recordService.createProcessingRecord(any(DocumentProcessingMessage.class)))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));

        StepVerifier.create(service.processDocumentParsing(message))
                .expectNext(true)
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("parsed content", document.getContent());
        org.junit.jupiter.api.Assertions.assertEquals(14, document.getCharacterCount());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", document.getParseStatus());
        ArgumentCaptor<DocumentProcessingMessage> messageCaptor = ArgumentCaptor.forClass(DocumentProcessingMessage.class);
        verify(outboxService).enqueue(messageCaptor.capture(),
                eq(org.example.ai_agent.document.domain.DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK));
        org.junit.jupiter.api.Assertions.assertEquals("CHUNK", messageCaptor.getValue().getProcessingType());
        org.junit.jupiter.api.Assertions.assertEquals(9L, messageCaptor.getValue().getDocumentId());
        org.junit.jupiter.api.Assertions.assertEquals(message.getRunId(), messageCaptor.getValue().getRunId());
        org.junit.jupiter.api.Assertions.assertEquals(91L, messageCaptor.getValue().getVersionId());
        org.junit.jupiter.api.Assertions.assertEquals(2L, messageCaptor.getValue().getGeneration());
    }

    @Test
    void startDocumentProcessingShouldUseAtomicRunAcceptanceWhenFeatureIsEnabled() {
        processingProperties.setVersionedIngestionEnabled(true);
        when(ingestionRunCommandService.acceptInitialIngestion(9L, 77L, "auto", true))
                .thenReturn(Mono.just(new AcceptedIngestionRun(
                        "run-9", 9L, 77L, 91L, 1L, IngestionStage.PARSE, true)));

        StepVerifier.create(service.startDocumentProcessing(9L, 77L, "auto", true))
                .expectNext(true)
                .verifyComplete();

        verify(ingestionRunCommandService).acceptInitialIngestion(9L, 77L, "auto", true);
        verifyNoInteractions(recordService, outboxService);
    }

    @Test
    void processDocumentEmbedding_shouldRejectWrongStageBeforeCallingDependencies() {
        DocumentProcessingMessage parseMessage =
                DocumentProcessingMessage.createParseMessage(9L, 77L, "auto", true);
        org.junit.jupiter.api.Assertions.assertEquals("PARSE", parseMessage.getProcessingType());
        org.junit.jupiter.api.Assertions.assertEquals(
                org.example.ai_agent.document.domain.DocumentProcessingType.PARSE,
                parseMessage.processingType());

        StepVerifier.create(service.processDocumentEmbedding(parseMessage))
                .expectError(IllegalArgumentException.class)
                .verify();

        verifyNoInteractions(recordService, documentRepository, documentChunkService, stages);
    }

    @Test
    void embeddingRetryRepairsFinalizationWithoutRepeatingEmbedding() {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(9L);
        when(recordService.startProcessing(9L, "EMBED"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        when(documentChunkService.getPendingEmbeddingChunks(9L)).thenReturn(Flux.empty());
        when(documentChunkService.getDocumentChunks(9L)).thenReturn(Flux.just(completedChunk(1L)));
        when(knowledgeChunkSearchService.indexDocumentChunks(9L))
                .thenReturn(Mono.error(new IllegalStateException("projection unavailable")), Mono.empty());
        when(documentRepository.findById(9L)).thenReturn(Mono.just(document));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(recordService.completeProcessing(9L, "EMBED", "Embedding completed"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        StepVerifier.create(service.processDocumentEmbedding(DocumentProcessingMessage.createEmbedMessage(9L, 77L)))
                .expectErrorMessage("projection unavailable").verify();
        org.mockito.Mockito.verify(recordService, org.mockito.Mockito.never())
                .completeProcessing(any(), any(), any());
        StepVerifier.create(service.processDocumentEmbedding(DocumentProcessingMessage.createEmbedMessage(9L, 77L)))
                .expectNext(true).verifyComplete();
        verifyNoInteractions(embeddingService);
        org.junit.jupiter.api.Assertions.assertTrue(document.getIsEmbedded());
    }

    @Test
    void embeddingPendingQueryFailureMustNotBeTreatedAsEmpty() {
        when(recordService.startProcessing(9L, "EMBED"))
                .thenReturn(Mono.just(new org.example.ai_agent.document.entity.DocumentProcessingRecord()));
        when(documentChunkService.getPendingEmbeddingChunks(9L))
                .thenReturn(Flux.error(new IllegalStateException("database unavailable")));
        StepVerifier.create(service.processDocumentEmbedding(DocumentProcessingMessage.createEmbedMessage(9L, 77L)))
                .expectErrorMessage("database unavailable").verify();
        org.mockito.Mockito.verify(recordService, org.mockito.Mockito.never())
                .completeProcessing(any(), any(), any());
    }

    private DocumentChunkDto completedChunk(Long id) {
        DocumentChunkDto chunk = chunk(id);
        chunk.setEmbeddingStatus("COMPLETED");
        chunk.setIsEmbedded(true);
        chunk.setEmbeddingId("embedding-" + id);
        return chunk;
    }

    private DocumentChunkDto chunk(Long id) {
        DocumentChunkDto chunk = new DocumentChunkDto();
        chunk.setId(id);
        chunk.setContent("chunk-" + id);
        return chunk;
    }
}
