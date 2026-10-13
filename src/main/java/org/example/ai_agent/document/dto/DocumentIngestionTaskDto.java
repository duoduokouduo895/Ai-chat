package org.example.ai_agent.document.dto;

import java.time.LocalDateTime;

/**
 * 文档 ingestion 任务受理结果。
 * 用于统一描述 parse -> chunk -> embed 这条入库流水线的启动动作。
 */
public record DocumentIngestionTaskDto(
        Long documentId,
        Long knowledgeBaseId,
        String action,
        String firstStage,
        String parseMode,
        Boolean enableVectorization,
        LocalDateTime acceptedAt
) {
}
