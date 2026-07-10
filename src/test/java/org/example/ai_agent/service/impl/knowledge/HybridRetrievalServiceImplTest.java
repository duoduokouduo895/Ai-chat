package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.document.KnowledgeChunkDocument;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.repository.knowledge.KnowledgeBaseRepository;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.HybridRetrievalService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceImplTest {

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private EmbeddingStoreRegistry embeddingStoreRegistry;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private RerankClient rerankClient;

    @Test
    void retrieve_shouldUseBm25WhenVectorTimesOut() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(7L);
        knowledgeBase.setCreatorId(42L);
        knowledgeBase.setIsPublic(false);
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBase.setEmbeddingModel("embedding-default");
        knowledgeBase.setIndexName("knowledge_base_embeddings");

        KnowledgeChunkDocument document = new KnowledgeChunkDocument();
        document.setChunkId(100L);
        document.setDocumentId(20L);
        document.setKnowledgeBaseId(7L);
        document.setChunkIndex(1);
        document.setContent("BM25 命中的内容");
        document.setDocumentTitle("测试文档");
        document.setKnowledgeBaseName("测试知识库");

        HybridRetrievalServiceImpl service = new HybridRetrievalServiceImpl(
                embeddingService,
                embeddingStoreRegistry,
                knowledgeChunkSearchService,
                rerankClient,
                new KnowledgeBaseAccessResolver(knowledgeBaseRepository));
        ReflectionTestUtils.setField(service, "vectorTimeoutMs", 10);
        ReflectionTestUtils.setField(service, "bm25TimeoutMs", 1000);

        when(knowledgeBaseRepository.findAllById(any(Iterable.class))).thenReturn(Flux.just(knowledgeBase));
        when(embeddingStoreRegistry.resolveProfile(knowledgeBase))
                .thenReturn(new EmbeddingStoreRegistry.EmbeddingProfile(
                        "embedding-default", "v1", 1024, "knowledge_base_embeddings", true));
        when(embeddingService.embedText(any(), eq("embedding-default"))).thenReturn(Mono.never());
        when(knowledgeChunkSearchService.searchAuthorizedChunks(any()))
                .thenReturn(Flux.just(new KnowledgeChunkSearchService.KnowledgeChunkSearchHit(document, 8.5D)));
        when(rerankClient.rerank(eq("退款规则"), anyList(), eq(5)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(1)));

        StepVerifier.create(service.retrieve("退款规则", List.of(7L), 5, 0.3D, 42L, false))
                .assertNext(results -> {
                    org.junit.jupiter.api.Assertions.assertEquals(1, results.size());
                    HybridRetrievalService.RetrievalCandidate candidate = results.get(0);
                    org.junit.jupiter.api.Assertions.assertEquals(100L, candidate.chunkId());
                    org.junit.jupiter.api.Assertions.assertEquals("BM25 命中的内容", candidate.content());
                })
                .verifyComplete();
    }
}
