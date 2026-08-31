package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;
import org.example.ai_agent.service.HybridRetrievalService;
import org.example.ai_agent.service.RagService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RetrievalContextAssembler {

    public KnowledgeSearchResultDto toSearchResult(List<HybridRetrievalService.RetrievalCandidate> candidates,
                                                   long startTimeMillis) {
        List<KnowledgeSearchResultDto.KnowledgeSearchMatch> matches = candidates.stream()
                .map(this::toSearchMatch)
                .collect(Collectors.toList());

        KnowledgeSearchResultDto result = new KnowledgeSearchResultDto();
        result.setMatches(matches);
        result.setTotalCount(matches.size());
        result.setSearchTime(System.currentTimeMillis() - startTimeMillis);
        return result;
    }

    public RagService.RetrievalContext toRetrievalContext(String question,
                                                          List<HybridRetrievalService.RetrievalCandidate> candidates,
                                                          long startTimeMillis) {
        List<KnowledgeSearchResultDto.KnowledgeSearchMatch> relevantDocs = candidates.stream()
                .map(this::toSearchMatch)
                .collect(Collectors.toList());

        String contextText = relevantDocs.stream()
                .map(doc -> String.format("文档：%s\n内容：%s\n", doc.getDocumentTitle(), doc.getContent()))
                .collect(Collectors.joining("\n---\n"));

        long retrievalTime = System.currentTimeMillis() - startTimeMillis;
        return new RagService.RetrievalContext(
                question,
                relevantDocs,
                contextText,
                relevantDocs.size(),
                retrievalTime);
    }

    public KnowledgeSearchResultDto.KnowledgeSearchMatch toSearchMatch(
            HybridRetrievalService.RetrievalCandidate result) {
        KnowledgeSearchResultDto.KnowledgeSearchMatch match =
                new KnowledgeSearchResultDto.KnowledgeSearchMatch();

        match.setChunkId(result.chunkId());
        match.setDocumentId(result.documentId());
        match.setKnowledgeBaseId(result.knowledgeBaseId());
        match.setContent(result.content());
        match.setScore(result.score());
        match.setMatchTime(LocalDateTime.now());
        match.setDocumentTitle(hasText(result.documentTitle()) ? result.documentTitle() : "未知文档");
        match.setKnowledgeBaseName(hasText(result.knowledgeBaseName()) ? result.knowledgeBaseName() : "未知知识库");
        match.setChunkIndex(result.chunkIndex() != null ? result.chunkIndex() : 0);
        return match;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
