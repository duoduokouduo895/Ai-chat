package org.example.ai_agent.service.assistant;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
public class KnowledgeAssistantToolGateway {

    private static final Duration TOOL_TIMEOUT = Duration.ofSeconds(5);

    private final Long requesterUserId;
    private final boolean adminRequest;
    private final List<Long> selectedKnowledgeBaseIds;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;

    public KnowledgeAssistantToolGateway(Long requesterUserId,
                                         boolean adminRequest,
                                         List<Long> selectedKnowledgeBaseIds,
                                         KnowledgeBaseService knowledgeBaseService,
                                         DocumentService documentService) {
        this.requesterUserId = requesterUserId;
        this.adminRequest = adminRequest;
        this.selectedKnowledgeBaseIds = selectedKnowledgeBaseIds;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
    }

    public List<KnowledgeBaseDto> loadCandidateKnowledgeBases() {
        try {
            if (selectedKnowledgeBaseIds != null && !selectedKnowledgeBaseIds.isEmpty()) {
                return Flux.fromIterable(selectedKnowledgeBaseIds)
                        .distinct()
                        .concatMap(id -> knowledgeBaseService.getKnowledgeBase(id, requesterUserId, adminRequest))
                        .collectList()
                        .blockOptional(TOOL_TIMEOUT)
                        .orElseGet(List::of);
            }

            Long effectiveRequester = adminRequest ? null : requesterUserId;
            return knowledgeBaseService.getKnowledgeBases(effectiveRequester, null, 0, 10)
                    .collectList()
                    .blockOptional(TOOL_TIMEOUT)
                    .orElseGet(List::of);
        } catch (RuntimeException error) {
            log.warn("Assistant 知识库工具查询失败: adminRequest={}, errorType={}",
                    adminRequest, error.getClass().getSimpleName());
            return List.of();
        }
    }

    public Optional<DocumentDto> getDocument(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return Optional.empty();
        }

        try {
            return documentService.getDocument(documentId, requesterUserId, adminRequest)
                    .blockOptional(TOOL_TIMEOUT);
        } catch (RuntimeException error) {
            log.warn("Assistant 文档状态工具查询失败: documentId={}, errorType={}",
                    documentId, error.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
