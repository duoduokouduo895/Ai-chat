package org.example.ai_agent.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private Long conversationId;
    private String sender;
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 20_000, message = "消息内容长度不能超过20000个字符")
    private String content;
    @Size(max = 120, message = "模型ID长度不能超过120个字符")
    private String model;
    @Size(max = 100, message = "客户端消息ID长度不能超过100个字符")
    private String clientMessageId;
    private LocalDateTime timestamp;
}
