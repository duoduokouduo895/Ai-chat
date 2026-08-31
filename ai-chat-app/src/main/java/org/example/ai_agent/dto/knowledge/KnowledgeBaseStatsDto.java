package org.example.ai_agent.dto.knowledge;

import lombok.Data;

/**
 * 知识库统计响应。
 */
@Data
public class KnowledgeBaseStatsDto {

    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private Long documentCount;
    private Long parsedDocumentCount;
    private Long failedDocumentCount;
    private Long embeddedDocumentCount;
}
