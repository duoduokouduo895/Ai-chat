package org.example.ai_agent.dto.chat;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long conversationId;
    private String sender;
    private Object content;
    private String model;
    private LocalDateTime timestamp;
}