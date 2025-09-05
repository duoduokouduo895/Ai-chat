package org.example.ai_agent.dto.chat;

import lombok.Data;
import org.example.ai_agent.dto.knowledge.KnowledgeSearchResultDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RAG对话响应DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
public class RagChatResponseDto {
    
    /**
     * AI生成的回答
     */
    private String answer;
    
    /**
     * 参考的文档来源
     */
    private List<KnowledgeSearchResultDto.KnowledgeSearchMatch> sources;
    
    /**
     * 对话ID
     */
    private Long conversationId;
    
    /**
     * 响应时间
     */
    private LocalDateTime responseTime;
    
    /**
     * 处理耗时(毫秒)
     */
    private Long processingTime;
}