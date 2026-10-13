package org.example.ai_agent.knowledge.service;

import org.example.ai_agent.knowledge.dto.KnowledgeBaseDocumentStatsProjection;
import org.example.ai_agent.knowledge.dto.AdminKnowledgeBaseSummaryProjection;
import org.example.ai_agent.knowledge.entity.KnowledgeBase;
import org.example.ai_agent.knowledge.repository.KnowledgeBaseRepository;
import org.example.ai_agent.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;
import org.example.ai_agent.knowledge.cleanup.KnowledgeCleanupCommands;

@ExtendWith(MockitoExtension.class)
@Tag("minimal")
@Tag("high-risk")
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock
    private KnowledgeCleanupCommands knowledgeCleanupCommands;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;

    private KnowledgeBaseServiceImpl service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(knowledgeBaseRepository.lockActiveById(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> knowledgeBaseRepository.findById((Long) invocation.getArgument(0)));
        service = new KnowledgeBaseServiceImpl(
                knowledgeBaseRepository,
                knowledgeDocumentRepository,
                knowledgeCleanupCommands,
                knowledgeChunkSearchService);
    }

    @Test
    void deleteKnowledgeBase_shouldUseDurableAcceptanceWithoutDeletingRecords() {
        when(knowledgeCleanupCommands.deleteKnowledgeBase(5L, 100L, false)).thenReturn(Mono.just(
                new org.example.ai_agent.document.domain.CleanupOperation(
                        "cleanup-5", "PENDING", 0, 0, null, null, null)));
        StepVerifier.create(service.deleteKnowledgeBase(5L, 100L, false)).verifyComplete();
        org.mockito.Mockito.verify(knowledgeCleanupCommands).deleteKnowledgeBase(5L, 100L, false);
        org.mockito.Mockito.verifyNoInteractions(knowledgeBaseRepository, knowledgeDocumentRepository);
    }

    @Test
    void getAuthorizedKnowledgeBase_shouldAllowOwnerWriteAccess() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(6L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(false);

        when(knowledgeBaseRepository.findById(6L)).thenReturn(Mono.just(knowledgeBase));

        StepVerifier.create(service.getAuthorizedKnowledgeBase(6L, 100L, false, true))
                .expectNext(knowledgeBase)
                .verifyComplete();
    }

    @Test
    void getAuthorizedKnowledgeBase_shouldAllowPublicReadForNonOwner() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(7L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(true);

        when(knowledgeBaseRepository.findById(7L)).thenReturn(Mono.just(knowledgeBase));

        StepVerifier.create(service.getAuthorizedKnowledgeBase(7L, 200L, false, false))
                .expectNext(knowledgeBase)
                .verifyComplete();
    }

    @Test
    void getAuthorizedKnowledgeBase_shouldDenyPrivateReadForNonOwner() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(8L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(false);

        when(knowledgeBaseRepository.findById(8L)).thenReturn(Mono.just(knowledgeBase));

        StepVerifier.create(service.getAuthorizedKnowledgeBase(8L, 200L, false, false))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("当前用户无权访问该知识库", error.getMessage());
                })
                .verify();
    }

    @Test
    void getAuthorizedKnowledgeBase_shouldDenyPublicWriteForNonOwner() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(9L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(true);

        when(knowledgeBaseRepository.findById(9L)).thenReturn(Mono.just(knowledgeBase));

        StepVerifier.create(service.getAuthorizedKnowledgeBase(9L, 200L, false, true))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("当前用户无权修改该知识库", error.getMessage());
                })
                .verify();
    }

    @Test
    void getAuthorizedKnowledgeBase_shouldAllowAdminAccess() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(10L);
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(false);

        when(knowledgeBaseRepository.findById(10L)).thenReturn(Mono.just(knowledgeBase));

        StepVerifier.create(service.getAuthorizedKnowledgeBase(10L, null, true, true))
                .expectNext(knowledgeBase)
                .verifyComplete();
    }

    @Test
    void getKnowledgeBaseStats_shouldAuthorizeAndReturnAggregatedCounts() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(11L);
        knowledgeBase.setName("合同知识库");
        knowledgeBase.setCreatorId(100L);
        knowledgeBase.setIsPublic(false);

        KnowledgeBaseDocumentStatsProjection stats = new KnowledgeBaseDocumentStatsProjection();
        stats.setDocumentCount(8L);
        stats.setParsedDocumentCount(6L);
        stats.setFailedDocumentCount(1L);
        stats.setEmbeddedDocumentCount(5L);

        when(knowledgeBaseRepository.findById(11L)).thenReturn(Mono.just(knowledgeBase));
        when(knowledgeDocumentRepository.summarizeByKnowledgeBaseId(11L)).thenReturn(Mono.just(stats));

        StepVerifier.create(service.getKnowledgeBaseStats(11L, 100L, false))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(11L, result.getKnowledgeBaseId());
                    org.junit.jupiter.api.Assertions.assertEquals("合同知识库", result.getKnowledgeBaseName());
                    org.junit.jupiter.api.Assertions.assertEquals(8L, result.getDocumentCount());
                    org.junit.jupiter.api.Assertions.assertEquals(6L, result.getParsedDocumentCount());
                    org.junit.jupiter.api.Assertions.assertEquals(1L, result.getFailedDocumentCount());
                    org.junit.jupiter.api.Assertions.assertEquals(5L, result.getEmbeddedDocumentCount());
                })
                .verifyComplete();
    }

    @Test
    void getAllKnowledgeBasesPage_shouldReturnDatabaseTotalsAndSummary() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(12L);
        knowledgeBase.setName("产品知识库");

        AdminKnowledgeBaseSummaryProjection summary = new AdminKnowledgeBaseSummaryProjection();
        summary.setKnowledgeBaseCount(8L);
        summary.setDocumentCount(42L);
        summary.setPublicKnowledgeBaseCount(3L);

        when(knowledgeBaseRepository.findAdminPage("产品", 10, 0L))
                .thenReturn(Flux.just(knowledgeBase));
        when(knowledgeBaseRepository.countAdminPage("产品")).thenReturn(Mono.just(1L));
        when(knowledgeBaseRepository.summarizeForAdmin()).thenReturn(Mono.just(summary));

        StepVerifier.create(service.getAllKnowledgeBasesPage(0, 10, " 产品 "))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1L, result.getTotal());
                    org.junit.jupiter.api.Assertions.assertEquals(12L, result.getItems().get(0).getId());
                    org.junit.jupiter.api.Assertions.assertEquals(8L, result.getSummary().get("knowledgeBaseCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(42L, result.getSummary().get("documentCount"));
                    org.junit.jupiter.api.Assertions.assertEquals(3L, result.getSummary().get("publicKnowledgeBaseCount"));
                })
                .verifyComplete();
    }
}
