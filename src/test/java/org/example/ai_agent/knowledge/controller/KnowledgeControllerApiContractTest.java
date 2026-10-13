package org.example.ai_agent.knowledge.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.ai_agent.document.dto.DocumentChunkDto;
import org.example.ai_agent.document.dto.DocumentDto;
import org.example.ai_agent.knowledge.dto.KnowledgeBaseDto;
import org.example.ai_agent.knowledge.dto.KnowledgeBaseStatsDto;
import org.example.ai_agent.knowledge.dto.KnowledgeSearchDto;
import org.example.ai_agent.knowledge.dto.KnowledgeSearchResultDto;
import org.example.ai_agent.document.controller.KnowledgeDocumentUploadFacade;
import org.example.ai_agent.auth.security.RequestActorResolver;
import org.example.ai_agent.common.web.ReactiveMultipartFormReader;
import org.example.ai_agent.document.config.DocumentUploadProperties;
import org.example.ai_agent.auth.repository.UserRepository;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.document.service.DocumentProcessingRecordService;
import org.example.ai_agent.document.service.DocumentService;
import org.example.ai_agent.knowledge.service.KnowledgeBaseService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.knowledge.service.RagService;
import org.example.ai_agent.auth.service.RepositoryActorIdentityLookup;
import org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.example.ai_agent.document.controller.KnowledgeDocumentController;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class KnowledgeControllerApiContractTest {

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
    @Mock
    private KnowledgeCleanupCommands cleanup;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private KnowledgeController controller;
    private KnowledgeDocumentController documentController;

    @BeforeEach
    void setUp() {
        KnowledgeAccessContextResolver accessContextResolver =
                new KnowledgeAccessContextResolver(knowledgeBaseService,
                        new RepositoryActorIdentityLookup(userRepository),
                        new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));
        ReactiveMultipartFormReader formReader = new ReactiveMultipartFormReader();
        KnowledgeDocumentUploadFacade uploadFacade =
                new KnowledgeDocumentUploadFacade(documentService, accessContextResolver, formReader,
                        new DocumentUploadProperties());
        controller = new KnowledgeController(
                ragService,
                knowledgeBaseService,
                knowledgeChunkSearchService,
                accessContextResolver, cleanup);
        documentController = new KnowledgeDocumentController(
                documentService, documentChunkService, accessContextResolver, uploadFacade, cleanup);
    }

    @Test
    void getKnowledgeBase_shouldExposeFrontendRequiredFields() {
        KnowledgeBaseDto dto = new KnowledgeBaseDto();
        dto.setId(1L);
        dto.setName("合同文档库");
        dto.setDescription("用于前端详情页");
        dto.setCreatorId(11L);
        dto.setCreatorName("alice");
        dto.setIsPublic(false);
        dto.setStatus("ACTIVE");
        dto.setDocumentCount(3);
        dto.setVectorDimension(1024);
        dto.setEmbeddingModel("qwen-embedding");
        dto.setIndexName("kb_contracts");
        dto.setCreatedAt(LocalDateTime.of(2026, 3, 28, 10, 0));
        dto.setUpdatedAt(LocalDateTime.of(2026, 3, 28, 10, 30));

        when(knowledgeBaseService.getKnowledgeBase(1L, 11L, false)).thenReturn(Mono.just(dto));

        StepVerifier.create(controller.getKnowledgeBase(1L, userAuthentication(11L))
                        .map(this::toJson))
                .assertNext(json -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, json.get("id").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("合同文档库", json.get("name").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("用于前端详情页", json.get("description").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(11L, json.get("creatorId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("alice", json.get("creatorName").asText());
                    org.junit.jupiter.api.Assertions.assertFalse(json.get("isPublic").asBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", json.get("status").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(3, json.get("documentCount").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals("qwen-embedding", json.get("embeddingModel").asText());
                    org.junit.jupiter.api.Assertions.assertTrue(json.hasNonNull("createdAt"));
                    org.junit.jupiter.api.Assertions.assertTrue(json.hasNonNull("updatedAt"));
                })
                .verifyComplete();
    }

    @Test
    void getDocuments_shouldExposeFrontendRequiredFields() {
        DocumentDto document = new DocumentDto();
        document.setId(9L);
        document.setKnowledgeBaseId(1L);
        document.setKnowledgeBaseName("合同文档库");
        document.setTitle("租赁协议");
        document.setFileName("lease.md");
        document.setFileType("md");
        document.setFileSize(1024L);
        document.setParseStatus("COMPLETED");
        document.setCharacterCount(2048);
        document.setChunkCount(6);
        document.setIsEmbedded(true);
        document.setEmbeddingStatus("COMPLETED");
        document.setUploaderId(11L);
        document.setUploaderName("alice");
        document.setCreatedAt(LocalDateTime.of(2026, 3, 28, 11, 0));
        document.setUpdatedAt(LocalDateTime.of(2026, 3, 28, 11, 10));

        when(documentService.getDocuments(1L, 0, 10, 11L, false)).thenReturn(Flux.just(document));

        StepVerifier.create(documentController.getDocuments(1L, 0, 10, userAuthentication(11L))
                        .collectList()
                        .map(this::toJson))
                .assertNext(json -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, json.size());
                    JsonNode first = json.get(0);
                    org.junit.jupiter.api.Assertions.assertEquals(9L, first.get("id").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, first.get("knowledgeBaseId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("合同文档库", first.get("knowledgeBaseName").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("lease.md", first.get("fileName").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", first.get("parseStatus").asText());
                    org.junit.jupiter.api.Assertions.assertTrue(first.get("isEmbedded").asBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", first.get("embeddingStatus").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(6, first.get("chunkCount").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals("alice", first.get("uploaderName").asText());
                })
                .verifyComplete();
    }

    @Test
    void getKnowledgeBaseStats_shouldExposeAggregatedCounts() {
        KnowledgeBaseStatsDto stats = new KnowledgeBaseStatsDto();
        stats.setKnowledgeBaseId(1L);
        stats.setKnowledgeBaseName("合同文档库");
        stats.setDocumentCount(8L);
        stats.setParsedDocumentCount(6L);
        stats.setFailedDocumentCount(1L);
        stats.setEmbeddedDocumentCount(5L);

        when(knowledgeBaseService.getKnowledgeBaseStats(1L, 11L, false)).thenReturn(Mono.just(stats));

        StepVerifier.create(controller.getKnowledgeBaseStats(1L, userAuthentication(11L))
                        .map(this::toJson))
                .assertNext(json -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, json.get("knowledgeBaseId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("合同文档库", json.get("knowledgeBaseName").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(8L, json.get("documentCount").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(6L, json.get("parsedDocumentCount").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, json.get("failedDocumentCount").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(5L, json.get("embeddedDocumentCount").asLong());
                })
                .verifyComplete();
    }

    @Test
    void checkDocumentEmbeddingStatus_shouldExposeExpectedStatusShape() {
        DocumentDto document = new DocumentDto();
        document.setId(9L);
        document.setFileName("lease.md");
        document.setParseStatus("COMPLETED");
        document.setEmbeddingStatus("PROCESSING");
        document.setIsEmbedded(false);
        document.setChunkCount(2);
        document.setCharacterCount(2048);

        DocumentChunkDto embeddedChunk = new DocumentChunkDto();
        embeddedChunk.setId(100L);
        embeddedChunk.setIsEmbedded(true);
        embeddedChunk.setEmbeddingStatus("COMPLETED");

        DocumentChunkDto pendingChunk = new DocumentChunkDto();
        pendingChunk.setId(101L);
        pendingChunk.setIsEmbedded(false);
        pendingChunk.setEmbeddingStatus("PENDING");

        when(documentService.getDocument(9L, 11L, false)).thenReturn(Mono.just(document));
        when(documentChunkService.getDocumentChunks(9L)).thenReturn(Flux.just(embeddedChunk, pendingChunk));

        StepVerifier.create(documentController.checkDocumentEmbeddingStatus(9L, userAuthentication(11L))
                        .map(this::toJson))
                .assertNext(json -> {
                    org.junit.jupiter.api.Assertions.assertEquals(9L, json.get("documentId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("lease.md", json.get("fileName").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", json.get("parseStatus").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("PROCESSING", json.get("embeddingStatus").asText());
                    org.junit.jupiter.api.Assertions.assertFalse(json.get("isEmbedded").asBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals(2, json.get("totalChunks").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals(1, json.get("embeddedChunks").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals(50.0d, json.get("embeddingProgress").asDouble());
                    org.junit.jupiter.api.Assertions.assertFalse(json.get("shouldBeEmbedded").asBoolean());
                    org.junit.jupiter.api.Assertions.assertFalse(json.get("statusMismatch").asBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, json.get("chunkStatusCounts").get("COMPLETED").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, json.get("chunkStatusCounts").get("PENDING").asLong());
                    org.junit.jupiter.api.Assertions.assertTrue(json.hasNonNull("timestamp"));
                })
                .verifyComplete();
    }

    @Test
    void searchKnowledge_shouldExposeMatchesAndSummaryFields() {
        KnowledgeSearchDto request = new KnowledgeSearchDto();
        request.setQuery("违约责任");
        request.setKnowledgeBaseIds(List.of(1L));
        request.setMaxResults(5);

        KnowledgeSearchResultDto.KnowledgeSearchMatch match = new KnowledgeSearchResultDto.KnowledgeSearchMatch();
        match.setChunkId(100L);
        match.setDocumentId(9L);
        match.setDocumentTitle("租赁协议");
        match.setKnowledgeBaseId(1L);
        match.setKnowledgeBaseName("合同文档库");
        match.setContent("违约责任由双方共同承担");
        match.setScore(0.93d);
        match.setChunkIndex(2);
        match.setMetadata(Map.of("section", "违约责任"));
        match.setMatchTime(LocalDateTime.of(2026, 3, 28, 12, 0));

        KnowledgeSearchResultDto result = new KnowledgeSearchResultDto();
        result.setMatches(List.of(match));
        result.setTotalCount(1);
        result.setSearchTime(36L);

        when(ragService.searchKnowledge(request, 11L, false)).thenReturn(Mono.just(result));

        StepVerifier.create(controller.searchKnowledge(request, userAuthentication(11L))
                        .map(this::toJson))
                .assertNext(json -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, json.get("totalCount").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals(36L, json.get("searchTime").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(1, json.get("matches").size());
                    JsonNode first = json.get("matches").get(0);
                    org.junit.jupiter.api.Assertions.assertEquals(100L, first.get("chunkId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals(9L, first.get("documentId").asLong());
                    org.junit.jupiter.api.Assertions.assertEquals("租赁协议", first.get("documentTitle").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("合同文档库", first.get("knowledgeBaseName").asText());
                    org.junit.jupiter.api.Assertions.assertEquals("违约责任由双方共同承担", first.get("content").asText());
                    org.junit.jupiter.api.Assertions.assertEquals(0.93d, first.get("score").asDouble());
                    org.junit.jupiter.api.Assertions.assertEquals("违约责任", first.get("metadata").get("section").asText());
                    org.junit.jupiter.api.Assertions.assertTrue(first.hasNonNull("matchTime"));
                })
                .verifyComplete();
    }

    @Test
    void deleteKnowledgeBase_shouldReturnAcceptedCleanupOperation() {
        var operation = new org.example.ai_agent.document.domain.CleanupOperation(
                "cleanup-kb-1", "PENDING", 0, 0, null,
                LocalDateTime.of(2026, 3, 28, 13, 0), LocalDateTime.of(2026, 3, 28, 13, 0));
        when(cleanup.deleteKnowledgeBase(1L, 11L, false)).thenReturn(Mono.just(operation));

        StepVerifier.create(controller.deleteKnowledgeBase(1L, userAuthentication(11L)))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(202, response.getStatusCode().value());
                    org.junit.jupiter.api.Assertions.assertSame(operation, response.getBody());
                    org.junit.jupiter.api.Assertions.assertEquals("cleanup-kb-1", response.getBody().operationId());
                    org.junit.jupiter.api.Assertions.assertEquals("PENDING", response.getBody().status());
                })
                .verifyComplete();
    }

    @Test
    void deleteDocument_shouldReturnAcceptedCleanupOperation() {
        var operation = new org.example.ai_agent.document.domain.CleanupOperation(
                "cleanup-doc-9", "PENDING", 0, 0, null,
                LocalDateTime.of(2026, 3, 28, 13, 0), LocalDateTime.of(2026, 3, 28, 13, 0));
        when(cleanup.deleteDocument(9L, 11L, false)).thenReturn(Mono.just(operation));

        StepVerifier.create(documentController.deleteDocument(9L, userAuthentication(11L)))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(202, response.getStatusCode().value());
                    org.junit.jupiter.api.Assertions.assertSame(operation, response.getBody());
                    org.junit.jupiter.api.Assertions.assertEquals("cleanup-doc-9", response.getBody().operationId());
                    org.junit.jupiter.api.Assertions.assertEquals("PENDING", response.getBody().status());
                })
                .verifyComplete();
    }

    private JsonNode toJson(Object value) {
        return objectMapper.valueToTree(value);
    }

    private UsernamePasswordAuthenticationToken userAuthentication(Long userId) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(userId);
        return authentication;
    }
}
