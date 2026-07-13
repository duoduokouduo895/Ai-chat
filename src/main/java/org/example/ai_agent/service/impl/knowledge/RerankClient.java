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
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class RerankClient {

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
        if (!enabled || candidates == null || candidates.isEmpty() || !StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(model)) {
            return Mono.just(limit(candidates, maxResults));
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
                .map(body -> applyScores(rerankCandidates, body, maxResults))
                .onErrorResume(error -> {
                    log.warn("Rerank 失败，降级使用 RRF 结果: errorType={}", error.getClass().getSimpleName());
                    return Mono.just(limit(candidates, maxResults));
                });
    }

    private List<RetrievalCandidate> applyScores(List<RetrievalCandidate> candidates, String body, int maxResults) {
        JSONObject response = JSONObject.parseObject(body);
        JSONArray results = response.getJSONArray("results");
        if (results == null) {
            results = response.getJSONArray("data");
        }
        if (results == null) {
            log.warn("Rerank 响应缺少 results/data 字段，降级使用 RRF 结果");
            return limit(candidates, maxResults);
        }

        return results.stream()
                .map(JSONObject.class::cast)
                .map(item -> {
                    Integer index = item.getInteger("index");
                    if (index == null) {
                        index = item.getInteger("document_index");
                    }
                    Double score = item.getDouble("relevance_score");
                    if (score == null) {
                        score = item.getDouble("score");
                    }
                    if (index == null || index < 0 || index >= candidates.size()) {
                        return null;
                    }
                    RetrievalCandidate candidate = candidates.get(index);
                    return new RetrievalCandidate(
                            candidate.embeddingId(),
                            candidate.knowledgeBaseId(),
                            candidate.documentId(),
                            candidate.chunkId(),
                            candidate.content(),
                            candidate.documentTitle(),
                            candidate.knowledgeBaseName(),
                            candidate.chunkIndex(),
                            score != null ? score : candidate.score());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(RetrievalCandidate::score).reversed())
                .limit(Math.max(1, maxResults))
                .toList();
    }

    private List<RetrievalCandidate> limit(List<RetrievalCandidate> candidates, int maxResults) {
        return candidates.stream()
                .limit(Math.max(1, maxResults))
                .toList();
    }
}
