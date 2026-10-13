package org.example.ai_agent.chat.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ConversationDto {
    private Long id;
    private String tempId; // 临时对话ID
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastMessage;
    private String firstUserMessage;
    private Integer messageCount;
    private Boolean isTemporary = false; // 是否为临时对话
}