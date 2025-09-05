package org.example.ai_agent.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.dto.chat.MessageDto;
import org.example.ai_agent.entity.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息转换器工具类
 * 职责：负责消息实体与DTO之间的转换
 * 优势：
 * 1. 单一职责：专门处理数据转换逻辑
 * 2. 可复用：多个服务类可以共享使用
 * 3. 易测试：独立的转换逻辑便于单元测试
 * 4. 易维护：转换规则集中管理
 */
@Component
public class MessageConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将Message实体转换为MessageDto
     * @param message 消息实体
     * @return MessageDto 消息DTO
     */
    public MessageDto convertToDto(Message message) {
        if (message == null) {
            return null;
        }

        MessageDto dto = new MessageDto();
        BeanUtils.copyProperties(message, dto, "content"); // 排除content，手动处理

        // 安全地将String类型的content设置到Object类型的dto.content
        dto.setContent(message.getContent());

        // 处理关联对象的转换
        if (message.getConversationId() != null) {
            dto.setConversationId(message.getConversationId());
        }

        return dto;
    }

    /**
     * 将MessageDto转换为Message实体
     * @param dto 消息DTO
     * @return Message 消息实体
     */
    public Message convertToEntity(MessageDto dto) {
        if (dto == null) {
            return null;
        }

        Message message = new Message();
        BeanUtils.copyProperties(dto, message, "content"); // 排除content，手动处理

        // 手动处理Object类型的content字段
        if (dto.getContent() != null) {
            if (dto.getContent() instanceof String) {
                message.setContent((String) dto.getContent());
            } else if (dto.getContent() instanceof Map) {
                // 如果是Map，尝试提取通用键
                @SuppressWarnings("unchecked")
                Map<String, Object> contentMap = (Map<String, Object>) dto.getContent();
                String text = (String) contentMap.getOrDefault("text", contentMap.getOrDefault("content", contentMap.toString()));
                message.setContent(text);
            } else {
                // 其他类型，调用toString()作为备用
                message.setContent(dto.getContent().toString());
            }
        }

        return message;
    }
}