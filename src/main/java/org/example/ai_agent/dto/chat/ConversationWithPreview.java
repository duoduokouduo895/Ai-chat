package org.example.ai_agent.dto.chat;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 对话预览DTO类
 * 包含对话基本信息和第一条用户消息预览
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationWithPreview {
    
    /**
     * 对话ID
     */
    private Long id;
    
    /**
     * 对话标题
     */
    private String title;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 第一条用户消息内容（用于预览显示）
     */
    private String firstUserMessage;
    
    /**
     * 最后一条消息内容（备用预览）
     */
    private String lastMessage;
    
    /**
     * 从Conversation实体创建ConversationWithPreview对象
     * 
     * @param conversation 对话实体
     * @param firstUserMessage 第一条用户消息
     * @param lastMessage 最后一条消息
     * @return ConversationWithPreview对象
     */
    public static ConversationWithPreview fromConversation(
            org.example.ai_agent.entity.Conversation conversation, 
            String firstUserMessage, 
            String lastMessage) {
        
        ConversationWithPreview dto = new ConversationWithPreview();
        dto.setId(conversation.getId());
        dto.setTitle(conversation.getTitle());
        dto.setCreatedAt(conversation.getCreatedAt());
        dto.setUpdatedAt(conversation.getUpdatedAt());
        dto.setUserId(conversation.getUserId());
        dto.setFirstUserMessage(firstUserMessage);
        dto.setLastMessage(lastMessage);
        
        return dto;
    }
}