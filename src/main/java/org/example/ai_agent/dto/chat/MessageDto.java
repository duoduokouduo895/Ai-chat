package org.example.ai_agent.dto.chat;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long conversationId;
    private String sender;
    private Object content;
    private String model;
    @Size(max = 100, message = "客户端消息ID长度不能超过100个字符")
    private String clientMessageId;
    private LocalDateTime timestamp;
}
