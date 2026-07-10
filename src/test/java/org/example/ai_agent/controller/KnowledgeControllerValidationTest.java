package org.example.ai_agent.controller;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.controller.support.KnowledgeDocumentUploadFacade;
import org.example.ai_agent.controller.support.ReactiveMultipartFormReader;
import org.example.ai_agent.exception.GlobalExceptionHandler;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.DocumentProcessingRecordService;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.verifyNoInteractions;

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
                new KnowledgeAccessContextResolver(knowledgeBaseService, userRepository);
        KnowledgeDocumentUploadFacade uploadFacade = new KnowledgeDocumentUploadFacade(
                documentService, accessContextResolver, new ReactiveMultipartFormReader(), new DocumentUploadProperties());
        KnowledgeController controller = new KnowledgeController(
                ragService,
                knowledgeBaseService,
                documentService,
                recordService,
                documentChunkService,
                knowledgeChunkSearchService,
                accessContextResolver,
                uploadFacade);
        webTestClient = WebTestClient.bindToController(controller)
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
