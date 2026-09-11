package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.dto.knowledge.KnowledgeSearchDto;
import org.example.ai_agent.service.HybridRetrievalService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private AiModelRegistry aiModelRegistry;
    @Mock
    private HybridRetrievalService hybridRetrievalService;

    @Test
    void searchKnowledge_shouldAssembleFrontendSearchResult() {
        RagServiceImpl service = new RagServiceImpl(
                aiModelRegistry,
                hybridRetrievalService,
                new RetrievalContextAssembler());
        KnowledgeSearchDto request = new KnowledgeSearchDto();
        request.setQuery("退款规则");
        request.setKnowledgeBaseIds(List.of(7L));
        request.setMaxResults(5);
        request.setMinScore(0.3D);

        when(hybridRetrievalService.retrieveDetailed("退款规则", List.of(7L), 5, 0.3D, 42L, false))
                .thenReturn(Mono.just(org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.of(List.of(candidate()), List.of(), 0)));

        StepVerifier.create(service.searchKnowledge(request, 42L, false))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, result.getTotalCount());
                    org.junit.jupiter.api.Assertions.assertTrue(result.getSearchTime() >= 0);
                    var match = result.getMatches().get(0);
                    org.junit.jupiter.api.Assertions.assertEquals(100L, match.getChunkId());
                    org.junit.jupiter.api.Assertions.assertEquals("测试文档", match.getDocumentTitle());
                    org.junit.jupiter.api.Assertions.assertEquals("测试知识库", match.getKnowledgeBaseName());
                    org.junit.jupiter.api.Assertions.assertEquals("BM25 命中的内容", match.getContent());
                })
                .verifyComplete();
    }

    @Test
    void retrieveContext_shouldAssemblePromptContext() {
        RagServiceImpl service = new RagServiceImpl(
                aiModelRegistry,
                hybridRetrievalService,
                new RetrievalContextAssembler());

        when(hybridRetrievalService.retrieveDetailed("退款规则", List.of(7L), 5, 0.3D, 42L, false))
                .thenReturn(Mono.just(org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.of(List.of(candidate()), List.of(), 0)));

        StepVerifier.create(service.retrieveContext("退款规则", List.of(7L), 5, 0.3D, 42L, false))
                .assertNext(context -> {
                    org.junit.jupiter.api.Assertions.assertEquals("退款规则", context.question());
                    org.junit.jupiter.api.Assertions.assertEquals(1, context.documentCount());
                    org.junit.jupiter.api.Assertions.assertTrue(context.contextText().contains("文档：测试文档"));
                    org.junit.jupiter.api.Assertions.assertTrue(context.contextText().contains("内容：BM25 命中的内容"));
                })
                .verifyComplete();
    }

    private HybridRetrievalService.RetrievalCandidate candidate() {
        return new HybridRetrievalService.RetrievalCandidate(
                null,
                7L,
                20L,
                3L,
                100L,
                "BM25 命中的内容",
                "测试文档",
                "测试知识库",
                1,
                8.5D);
    }
}
