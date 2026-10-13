package org.example.ai_agent.chat.service;

import org.example.ai_agent.chat.dto.MessageDto;
import org.example.ai_agent.chat.entity.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

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

        // MessageDto 的内容与实体一致，保持为受限文本。
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

        message.setContent(dto.getContent());

        return message;
    }
}
