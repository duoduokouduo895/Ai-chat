package org.example.ai_agent.service.impl.knowledge;

import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.entity.KnowledgeBase;
import org.example.ai_agent.service.EmbeddingService;
import org.example.ai_agent.service.HybridRetrievalService;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry;
import org.example.ai_agent.service.registry.EmbeddingStoreRegistry.EmbeddingProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import org.example.ai_agent.knowledge.retrieval.RetrievalOutcome;
import org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.Channel;
import org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HybridRetrievalServiceImpl implements HybridRetrievalService {

    private final EmbeddingService embeddingService;
    private final EmbeddingStoreRegistry embeddingStoreRegistry;
    private final KnowledgeChunkSearchService knowledgeChunkSearchService;
    private final RerankClient rerankClient;
    private final KnowledgeBaseAccessResolver knowledgeBaseAccessResolver;

    @Value("${knowledge-base.rag.retrieval-mode:hybrid}")
    private String retrievalMode;

    @Value("${knowledge-base.rag.vector-top-k:20}")
    private int vectorTopK;

    @Value("${knowledge-base.rag.bm25-top-k:20}")
    private int bm25TopK;

    @Value("${knowledge-base.rag.rrf-k:60}")
    private int rrfK;

    @Value("${knowledge-base.rag.vector-timeout-ms:2500}")
    private int vectorTimeoutMs;

    @Value("${knowledge-base.rag.bm25-timeout-ms:1500}")
    private int bm25TimeoutMs;

    public HybridRetrievalServiceImpl(EmbeddingService embeddingService,
                                      EmbeddingStoreRegistry embeddingStoreRegistry,
                                      KnowledgeChunkSearchService knowledgeChunkSearchService,
                                      RerankClient rerankClient,
                                      KnowledgeBaseAccessResolver knowledgeBaseAccessResolver) {
        this.embeddingService = embeddingService;
        this.embeddingStoreRegistry = embeddingStoreRegistry;
        this.knowledgeChunkSearchService = knowledgeChunkSearchService;
        this.rerankClient = rerankClient;
        this.knowledgeBaseAccessResolver = knowledgeBaseAccessResolver;
    }

    @Override
    public Mono<List<RetrievalCandidate>> retrieve(String query, List<Long> knowledgeBaseIds,
                                                   Integer maxResults, Double minSimilarity,
                                                   Long requesterUserId, boolean isAdmin) {
        return retrieveDetailed(query, knowledgeBaseIds, maxResults, minSimilarity, requesterUserId, isAdmin)
                .map(RetrievalOutcome::candidates);
    }

    @Override
    public Mono<RetrievalOutcome<RetrievalCandidate>> retrieveDetailed(String query, List<Long> knowledgeBaseIds,
            Integer maxResults, Double minSimilarity, Long requesterUserId, boolean isAdmin) {
        return Mono.defer(() -> retrieveOutcome(query, knowledgeBaseIds, maxResults, minSimilarity, requesterUserId, isAdmin));
    }

    private Mono<RetrievalOutcome<RetrievalCandidate>> retrieveOutcome(String query, List<Long> knowledgeBaseIds,
            Integer maxResults, Double minSimilarity, Long requesterUserId, boolean isAdmin) {
        long started = System.nanoTime();
        int finalMaxResults = maxResults != null && maxResults > 0 ? maxResults : 10;
        double finalMinSimilarity = minSimilarity != null ? minSimilarity : 0.7D;

        return knowledgeBaseAccessResolver.resolveReadable(knowledgeBaseIds, requesterUserId, isAdmin)
                .collectList()
                .flatMap(knowledgeBases -> {
                    if (knowledgeBases.isEmpty()) {
                        return Mono.just(RetrievalOutcome.<RetrievalCandidate>of(List.of(), List.of(), elapsedMs(started)));
                    }

                    int candidateLimit = Math.max(finalMaxResults, finalMaxResults * 2);
                    Mono<ChannelResult> vector = "bm25".equalsIgnoreCase(retrievalMode)
                            ? Mono.just(new ChannelResult(List.of(), new Channel("Vector", ChannelStatus.DISABLED, 0)))
                            : withRetrievalGuard("Vector", query,
                                    Mono.defer(() -> vectorSearch(query, knowledgeBases, finalMinSimilarity, candidateLimit)),
                                    vectorTimeoutMs);
                    Mono<ChannelResult> bm25 = "vector".equalsIgnoreCase(retrievalMode)
                            ? Mono.just(new ChannelResult(List.of(), new Channel("BM25", ChannelStatus.DISABLED, 0)))
                            : withRetrievalGuard("BM25", query,
                                    Mono.defer(() -> bm25Search(query, knowledgeBases, requesterUserId, isAdmin, candidateLimit)),
                                    bm25TimeoutMs);

                    return Mono.zip(vector, bm25)
                            .flatMap(tuple -> {
                                var candidates = fuse(tuple.getT1().candidates(), tuple.getT2().candidates());
                                var channels = List.of(tuple.getT1().channel(), tuple.getT2().channel());
                                if (candidates.isEmpty()) {
                                    return Mono.just(RetrievalOutcome.of(candidates, channels, elapsedMs(started)));
                                }
                                return rerankClient.rerankDetailed(query, candidates, finalMaxResults)
                                        .map(ranked -> {
                                            List<Channel> allChannels = new ArrayList<>(channels);
                                            allChannels.add(ranked.channel());
                                            return RetrievalOutcome.of(ranked.candidates(), allChannels, elapsedMs(started));
                                        });
                            });
                });
    }

    private Mono<ChannelResult> withRetrievalGuard(String channel, String query,
                                                              Mono<List<RetrievalCandidate>> retrieval,
                                                              int timeoutMs) {
        long startNanos = System.nanoTime();
        int queryLength = query == null ? 0 : query.length();
        return retrieval
                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)))
                .doOnSuccess(results -> log.info("{} 召回完成: queryLength={}, resultCount={}, durationMs={}",
                        channel, queryLength, results != null ? results.size() : 0, elapsedMs(startNanos)))
                .map(results -> new ChannelResult(results, new Channel(channel, ChannelStatus.SUCCEEDED, elapsedMs(startNanos))))
                .onErrorResume(error -> {
                    log.warn("{} 召回失败或超时，降级为空结果: queryLength={}, durationMs={}, errorType={}",
                            channel, queryLength, elapsedMs(startNanos),
                            error.getClass().getSimpleName());
                    return Mono.just(new ChannelResult(List.of(), new Channel(channel, ChannelStatus.FAILED, elapsedMs(startNanos))));
                });
    }

    private record ChannelResult(List<RetrievalCandidate> candidates, Channel channel) { }

    private Mono<List<RetrievalCandidate>> vectorSearch(String query, List<KnowledgeBase> knowledgeBases,
                                                        double minSimilarity, int candidateLimit) {
        Map<EmbeddingProfile, List<Long>> idsByProfile = new LinkedHashMap<>();
        for (KnowledgeBase knowledgeBase : knowledgeBases) {
            EmbeddingProfile profile = embeddingStoreRegistry.resolveProfile(knowledgeBase);
            idsByProfile.computeIfAbsent(profile, ignored -> new ArrayList<>()).add(knowledgeBase.getId());
        }

        List<String> uniqueAliases = idsByProfile.keySet().stream()
                .map(EmbeddingProfile::modelAlias)
                .distinct()
                .toList();

        return Flux.fromIterable(uniqueAliases)
                .flatMap(alias -> embeddingService.embedText(TextSegment.from(query), alias)
                        .map(embedding -> Map.entry(alias, embedding)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMapMany(embeddingMap -> Flux.fromIterable(idsByProfile.entrySet())
                        .flatMap(entry -> {
                            dev.langchain4j.data.embedding.Embedding queryEmbedding =
                                    embeddingMap.get(entry.getKey().modelAlias());
                            if (queryEmbedding == null) {
                                return Mono.just(List.<EmbeddingService.EmbeddingSearchResult>of());
                            }
                            return embeddingService.searchSimilarEmbeddings(
                                    queryEmbedding,
                                    entry.getValue(),
                                    Math.max(1, Math.min(vectorTopK, candidateLimit)),
                                    minSimilarity,
                                    entry.getKey().modelAlias(),
                                    entry.getKey().indexName());
                        }))
                .flatMapIterable(results -> results)
                .map(this::fromVector)
                .sort(Comparator.comparing(RetrievalCandidate::score).reversed())
                .collectList();
    }

    private Mono<List<RetrievalCandidate>> bm25Search(String query, List<KnowledgeBase> knowledgeBases,
                                                      Long requesterUserId, boolean isAdmin, int candidateLimit) {
        List<Long> authorizedIds = knowledgeBases.stream().map(KnowledgeBase::getId).toList();
        return knowledgeChunkSearchService.searchAuthorizedChunks(new KnowledgeChunkSearchService.SearchRequest(
                        query,
                        authorizedIds,
                        requesterUserId,
                        isAdmin,
                        Math.max(1, Math.min(bm25TopK, candidateLimit))))
                .map(hit -> {
                    var doc = hit.document();
                    return new RetrievalCandidate(
                            null,
                            doc.getKnowledgeBaseId(),
                            doc.getDocumentId(),
                            doc.getVersionId(),
                            doc.getChunkId(),
                            doc.getContent(),
                            doc.getDocumentTitle(),
                            doc.getKnowledgeBaseName(),
                            doc.getChunkIndex(),
                            hit.score());
                })
                .collectList();
    }

    private List<RetrievalCandidate> fuse(List<RetrievalCandidate> vectorResults,
                                          List<RetrievalCandidate> bm25Results) {
        Map<Long, FusedCandidate> fused = new LinkedHashMap<>();
        addRanked(fused, vectorResults);
        addRanked(fused, bm25Results);
        return fused.values().stream()
                .sorted(Comparator.comparing(FusedCandidate::score).reversed())
                .map(fusedCandidate -> withScore(fusedCandidate.candidate(), fusedCandidate.score()))
                .toList();
    }

    private void addRanked(Map<Long, FusedCandidate> fused, List<RetrievalCandidate> results) {
        for (int i = 0; i < results.size(); i++) {
            RetrievalCandidate candidate = results.get(i);
            if (candidate.chunkId() == null) {
                continue;
            }
            double contribution = 1.0D / (Math.max(1, rrfK) + i + 1);
            fused.compute(candidate.chunkId(), (ignored, existing) -> {
                if (existing == null) {
                    return new FusedCandidate(candidate, contribution);
                }
                return new FusedCandidate(preferRicher(existing.candidate(), candidate),
                        existing.score() + contribution);
            });
        }
    }

    private RetrievalCandidate preferRicher(RetrievalCandidate current, RetrievalCandidate next) {
        boolean nextHasTitle = hasText(next.documentTitle()) && !"未知文档".equals(next.documentTitle());
        boolean currentHasTitle = hasText(current.documentTitle()) && !"未知文档".equals(current.documentTitle());
        if (nextHasTitle && !currentHasTitle) {
            return next;
        }
        return current;
    }

    private RetrievalCandidate withScore(RetrievalCandidate candidate, double score) {
        return new RetrievalCandidate(
                candidate.embeddingId(),
                candidate.knowledgeBaseId(),
                candidate.documentId(),
                candidate.versionId(),
                candidate.chunkId(),
                candidate.content(),
                candidate.documentTitle(),
                candidate.knowledgeBaseName(),
                candidate.chunkIndex(),
                score);
    }

    private RetrievalCandidate fromVector(EmbeddingService.EmbeddingSearchResult result) {
        var metadata = result.textSegment().metadata();
        String documentTitle = metadata.getString("document_title");
        String knowledgeBaseName = metadata.getString("knowledge_base_name");
        Integer chunkIndex = metadata.getInteger("chunk_index");

        return new RetrievalCandidate(
                result.embeddingId(),
                result.knowledgeBaseId(),
                result.documentId(),
                metadata.getLong("version_id"),
                result.chunkId(),
                result.textSegment().text(),
                hasText(documentTitle) ? documentTitle : "未知文档",
                hasText(knowledgeBaseName) ? knowledgeBaseName : "未知知识库",
                chunkIndex != null ? chunkIndex : 0,
                result.score());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record FusedCandidate(RetrievalCandidate candidate, double score) {
    }
}
