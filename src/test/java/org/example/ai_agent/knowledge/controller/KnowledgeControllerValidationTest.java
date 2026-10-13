package org.example.ai_agent.knowledge.controller;

import org.example.ai_agent.document.config.DocumentUploadProperties;
import org.example.ai_agent.document.controller.KnowledgeDocumentUploadFacade;
import org.example.ai_agent.auth.security.RequestActorResolver;
import org.example.ai_agent.common.web.ReactiveMultipartFormReader;
import org.example.ai_agent.common.exception.GlobalExceptionHandler;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.document.service.DocumentProcessingRecordService;
import org.example.ai_agent.document.service.DocumentService;
import org.example.ai_agent.knowledge.service.KnowledgeBaseService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.knowledge.service.RagService;
import org.example.ai_agent.auth.service.RepositoryActorIdentityLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verifyNoInteractions;
import org.example.ai_agent.document.controller.DocumentProcessingRecordController;
import org.example.ai_agent.document.controller.KnowledgeDocumentController;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerValidationTest {

    @Mock
    private RagService ragService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private DocumentService documentService;
    @Mock
    private DocumentProcessingRecordService recordService;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private UserRepository userRepository;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        KnowledgeAccessContextResolver accessContextResolver =
                new KnowledgeAccessContextResolver(knowledgeBaseService,
                        new RepositoryActorIdentityLookup(userRepository),
                        new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));
        KnowledgeDocumentUploadFacade uploadFacade = new KnowledgeDocumentUploadFacade(
                documentService, accessContextResolver, new ReactiveMultipartFormReader(), new DocumentUploadProperties());
        KnowledgeController controller = new KnowledgeController(
                ragService,
                knowledgeBaseService,
                knowledgeChunkSearchService,
                accessContextResolver, org.mockito.Mockito.mock(org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands.class));
        KnowledgeDocumentController documentController = new KnowledgeDocumentController(
                documentService, documentChunkService, accessContextResolver, uploadFacade, org.mockito.Mockito.mock(org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands.class));
        DocumentProcessingRecordController processingRecordController = new DocumentProcessingRecordController(
                documentService, recordService, accessContextResolver);
        webTestClient = WebTestClient.bindToController(
                        controller, documentController, processingRecordController, new LegacyRagController())
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void searchKnowledge_shouldRejectExcessiveResultLimitBeforeCallingRetrieval() {
        webTestClient.post()
                .uri("/api/knowledge/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "query": "合同条款",
                          "knowledgeBaseIds": [1],
                          "maxResults": 21,
                          "minScore": 0.5
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_ERROR")
                .jsonPath("$.message").isEqualTo("最大返回数量不能超过20");

        verifyNoInteractions(ragService);
    }

    @Test
    void legacyRagEndpoints_shouldReturnGoneWithoutCallingTheModel() {
        webTestClient.post()
                .uri("/api/knowledge/chat")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectHeader().valueEquals("Deprecation", "true")
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("LEGACY_RAG_ENDPOINT_RETIRED")
                .jsonPath("$.replacement").isEqualTo("/api/ai/chat");

        webTestClient.post()
                .uri("/api/knowledge/chat/stream")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectHeader().valueEquals("Deprecation", "true");

        verifyNoInteractions(ragService);
    }
}
