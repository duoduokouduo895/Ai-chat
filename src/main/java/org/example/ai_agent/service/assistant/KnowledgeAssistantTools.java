package org.example.ai_agent.service.assistant;

import dev.langchain4j.agent.tool.Tool;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面向 Assistant 的知识库工具集。
 * 这些工具直接复用现有业务服务，避免再造一套展示型 API。
 */
public class KnowledgeAssistantTools {

    private final Long requesterUserId;
    private final boolean adminRequest;
    private final List<Long> selectedKnowledgeBaseIds;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;

    public KnowledgeAssistantTools(Long requesterUserId,
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

    @Tool("列出当前用户可访问的知识库。如果当前会话已经选择了知识库，则优先列出本次会话已选中的知识库。适合在回答前确认有哪些知识来源可用。")
    public String listAvailableKnowledgeBases() {
        List<KnowledgeBaseDto> knowledgeBases = loadCandidateKnowledgeBases();
        if (knowledgeBases.isEmpty()) {
            return "当前没有可用的知识库。";
        }

        return knowledgeBases.stream()
                .map(kb -> String.format(
                        "知识库ID=%d，名称=%s，文档数=%d，状态=%s，EmbeddingModel=%s，是否公开=%s",
                        kb.getId(),
                        safe(kb.getName()),
                        kb.getDocumentCount() != null ? kb.getDocumentCount() : 0,
                        safe(kb.getStatus()),
                        safe(kb.getEmbeddingModel()),
                        Boolean.TRUE.equals(kb.getIsPublic()) ? "是" : "否"
                ))
                .collect(Collectors.joining("\n"));
    }

    @Tool("查询某个文档的处理状态，包括解析状态、向量化状态、分块数量和是否已完成向量入库。适合用户询问某个知识库文档是否已经准备好用于问答。")
    public String getDocumentProcessingStatus(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return "文档ID无效。";
        }

        DocumentDto document = documentService.getDocument(documentId, requesterUserId, adminRequest).block();
        if (document == null) {
            return "未找到对应文档。";
        }

        return String.format(
                "文档ID=%d，文件名=%s，所属知识库ID=%d，解析状态=%s，向量化状态=%s，是否已嵌入=%s，分块数=%d，字符数=%d",
                document.getId(),
                safe(document.getFileName()),
                document.getKnowledgeBaseId(),
                safe(document.getParseStatus()),
                safe(document.getEmbeddingStatus()),
                Boolean.TRUE.equals(document.getIsEmbedded()) ? "是" : "否",
                document.getChunkCount() != null ? document.getChunkCount() : 0,
                document.getCharacterCount() != null ? document.getCharacterCount() : 0
        );
    }

    private List<KnowledgeBaseDto> loadCandidateKnowledgeBases() {
        if (selectedKnowledgeBaseIds != null && !selectedKnowledgeBaseIds.isEmpty()) {
            return selectedKnowledgeBaseIds.stream()
                    .distinct()
                    .map(id -> knowledgeBaseService.getKnowledgeBase(id, requesterUserId, adminRequest).block())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        Long effectiveRequester = adminRequest ? null : requesterUserId;
        return knowledgeBaseService.getKnowledgeBases(effectiveRequester, null, 0, 10)
                .collectList()
                .blockOptional()
                .orElseGet(List::of);
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "未知";
    }
}
