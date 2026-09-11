package org.example.ai_agent.service.impl.knowledge;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.service.HybridRetrievalService.RetrievalCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class RerankClient {

    public record RerankResult(List<RetrievalCandidate> candidates,
                               org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.Channel channel) { }

    @Value("${knowledge-base.rag.enable-reranking:false}")
    private boolean enabled;

    @Value("${knowledge-base.rag.rerank.base-url:}")
    private String baseUrl;

    @Value("${knowledge-base.rag.rerank.api-key:}")
    private String apiKey;

    @Value("${knowledge-base.rag.rerank.model:}")
    private String model;

    @Value("${knowledge-base.rag.rerank.timeout-ms:3000}")
    private int timeoutMs;

    @Value("${knowledge-base.rag.rerank.top-n:20}")
    private int topN;

    public Mono<List<RetrievalCandidate>> rerank(String query, List<RetrievalCandidate> candidates, int maxResults) {
        return rerankDetailed(query, candidates, maxResults).map(RerankResult::candidates);
    }

    public Mono<RerankResult> rerankDetailed(String query, List<RetrievalCandidate> candidates, int maxResults) {
        long started = System.nanoTime();
        if (!enabled || candidates == null || candidates.isEmpty()) {
            return Mono.just(new RerankResult(limit(candidates, maxResults), channel(
                    org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.DISABLED, started)));
        }
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            log.warn("Rerank 已启用但缺少调用配置，降级使用 RRF 结果");
            return Mono.just(new RerankResult(limit(candidates, maxResults), channel(
                    org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.FAILED, started)));
        }

        List<RetrievalCandidate> rerankCandidates = candidates.stream()
                .limit(Math.max(1, topN))
                .toList();

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("query", query);
        payload.put("documents", rerankCandidates.stream().map(RetrievalCandidate::content).toList());

        WebClient.RequestBodySpec request = WebClient.builder()
                .baseUrl(baseUrl)
                .build()
                .post()
                .contentType(MediaType.APPLICATION_JSON);

        if (StringUtils.hasText(apiKey)) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }

        return request.bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.max(1, timeoutMs)))
                .map(body -> parseScores(rerankCandidates, body, maxResults))
                .map(parsed -> new RerankResult(parsed.candidates(), channel(
                        parsed.valid()
                                ? org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.SUCCEEDED
                                : org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.FAILED,
                        started)))
                .onErrorResume(error -> {
                    log.warn("Rerank 请求失败，降级使用 RRF 结果: errorType={}",
                            error.getClass().getSimpleName());
                    return Mono.just(new RerankResult(limit(candidates, maxResults), channel(
                            org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus.FAILED, started)));
                });
    }

    List<RetrievalCandidate> applyScores(List<RetrievalCandidate> candidates, String body, int maxResults) {
        return parseScores(candidates, body, maxResults).candidates();
    }

    private ParsedScores parseScores(List<RetrievalCandidate> candidates, String body, int maxResults) {
        if (candidates == null || candidates.isEmpty() || !StringUtils.hasText(body)) {
            return new ParsedScores(limit(candidates, maxResults), false);
        }

        JSONObject response;
        try {
            response = JSONObject.parseObject(body);
        } catch (RuntimeException error) {
            log.warn("Rerank 响应不是合法 JSON，降级使用 RRF 结果: errorType={}",
                    error.getClass().getSimpleName());
            return new ParsedScores(limit(candidates, maxResults), false);
        }
        if (response == null) {
            log.warn("Rerank 响应顶层结构为空，降级使用 RRF 结果");
            return new ParsedScores(limit(candidates, maxResults), false);
        }

        JSONArray results;
        try {
            results = response.getJSONArray("results");
            if (results == null) {
                results = response.getJSONArray("data");
            }
        } catch (RuntimeException error) {
            log.warn("Rerank 响应结果字段类型无效，降级使用 RRF 结果: errorType={}",
                    error.getClass().getSimpleName());
            return new ParsedScores(limit(candidates, maxResults), false);
        }
        if (results == null || results.isEmpty()) {
            log.warn("Rerank 响应缺少有效结果，降级使用 RRF 结果");
            return new ParsedScores(limit(candidates, maxResults), false);
        }

        List<RetrievalCandidate> scoredCandidates = new ArrayList<>(results.size());
        Set<Integer> seenIndexes = new HashSet<>();
        for (Object result : results) {
            if (!(result instanceof JSONObject item)) {
                log.warn("Rerank 响应结果元素类型无效，降级使用 RRF 结果: valueType={}",
                        result == null ? "null" : result.getClass().getSimpleName());
                return new ParsedScores(limit(candidates, maxResults), false);
            }

                    Integer index;
                    Double score;
                    try {
                        index = item.getInteger("index");
                        if (index == null) {
                            index = item.getInteger("document_index");
                        }
                        score = item.getDouble("relevance_score");
                        if (score == null) {
                            score = item.getDouble("score");
                        }
                    } catch (RuntimeException error) {
                        log.warn("Rerank 响应结果字段类型无效，降级使用 RRF 结果: errorType={}",
                                error.getClass().getSimpleName());
                        return new ParsedScores(limit(candidates, maxResults), false);
                    }
                    if (index == null || index < 0 || index >= candidates.size()
                            || !seenIndexes.add(index)
                            || score == null || !Double.isFinite(score)) {
                        log.warn("Rerank 响应索引或分数无效，降级使用 RRF 结果");
                        return new ParsedScores(limit(candidates, maxResults), false);
                    }
                    RetrievalCandidate candidate = candidates.get(index);
                    scoredCandidates.add(new RetrievalCandidate(
                            candidate.embeddingId(),
                            candidate.knowledgeBaseId(),
                            candidate.documentId(),
                            candidate.versionId(),
                            candidate.chunkId(),
                            candidate.content(),
                            candidate.documentTitle(),
                            candidate.knowledgeBaseName(),
                            candidate.chunkIndex(),
                            score));
        }

        return new ParsedScores(scoredCandidates.stream()
                .sorted(Comparator.comparing(RetrievalCandidate::score).reversed())
                .limit(Math.max(1, maxResults))
                .toList(), true);
    }

    private org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.Channel channel(
            org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.ChannelStatus status, long started) {
        return new org.example.ai_agent.knowledge.retrieval.RetrievalOutcome.Channel(
                "Rerank", status, (System.nanoTime() - started) / 1_000_000);
    }

    private record ParsedScores(List<RetrievalCandidate> candidates, boolean valid) { }

    private List<RetrievalCandidate> limit(List<RetrievalCandidate> candidates, int maxResults) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .limit(Math.max(1, maxResults))
                .toList();
    }
}
