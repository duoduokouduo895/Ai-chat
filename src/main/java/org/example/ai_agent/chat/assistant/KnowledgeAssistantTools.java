package org.example.ai_agent.chat.assistant;

import dev.langchain4j.agent.tool.Tool;
import org.example.ai_agent.document.dto.DocumentDto;
import org.example.ai_agent.knowledge.dto.KnowledgeBaseDto;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面向 Assistant 的知识库工具集。
 * 这些工具直接复用现有业务服务，避免再造一套展示型 API。
 */
public class KnowledgeAssistantTools {

    private static final int MAX_TOOL_RESULT_CHARACTERS = 4_000;

    private final KnowledgeAssistantToolGateway gateway;

    public KnowledgeAssistantTools(KnowledgeAssistantToolGateway gateway) {
        this.gateway = gateway;
    }

    @Tool("列出当前用户可访问的知识库。如果当前会话已经选择了知识库，则优先列出本次会话已选中的知识库。适合在回答前确认有哪些知识来源可用。")
    public String listAvailableKnowledgeBases() {
        List<KnowledgeBaseDto> knowledgeBases = loadCandidateKnowledgeBases();
        if (knowledgeBases.isEmpty()) {
            return "当前没有可用的知识库。";
        }

        return bounded(knowledgeBases.stream()
                .map(kb -> String.format(
                        "知识库ID=%d，名称=%s，文档数=%d，状态=%s，EmbeddingModel=%s，是否公开=%s",
                        kb.getId(),
                        safe(kb.getName()),
                        kb.getDocumentCount() != null ? kb.getDocumentCount() : 0,
                        safe(kb.getStatus()),
                        safe(kb.getEmbeddingModel()),
                        Boolean.TRUE.equals(kb.getIsPublic()) ? "是" : "否"
                ))
                .collect(Collectors.joining("\n")));
    }

    @Tool("查询某个文档的处理状态，包括解析状态、向量化状态、分块数量和是否已完成向量入库。适合用户询问某个知识库文档是否已经准备好用于问答。")
    public String getDocumentProcessingStatus(Long documentId) {
        if (documentId == null || documentId <= 0) {
            return "文档ID无效。";
        }

        return bounded(gateway.getDocument(documentId)
                .map(this::formatDocumentStatus)
                .orElse("未找到对应文档。"));
    }

    private String formatDocumentStatus(DocumentDto document) {
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
        return gateway.loadCandidateKnowledgeBases();
    }

    private String safe(String value) {
        return value != null && !value.isBlank() ? value : "未知";
    }

    private String bounded(String result) {
        if (result == null || result.length() <= MAX_TOOL_RESULT_CHARACTERS) return result;
        return result.substring(0, MAX_TOOL_RESULT_CHARACTERS) + "\n结果过长，已省略剩余内容。";
    }
}
