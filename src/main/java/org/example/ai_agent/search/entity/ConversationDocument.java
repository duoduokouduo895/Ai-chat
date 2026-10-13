package org.example.ai_agent.search.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 对话文档类，用于Elasticsearch索引
 * 包含对话的基本信息和相关消息内容，便于全文搜索
 */
@Data
@NoArgsConstructor
@Document(indexName = "conversations")
public class ConversationDocument {
    
    @Id
    private String id; // Elasticsearch文档ID，由conversationId转换而来
    
    @Field(type = FieldType.Long)
    private Long conversationId; // 业务主键，对应数据库中的conversation表主键
    
    @Field(type = FieldType.Long)
    private Long userId; // 新增：用户ID，用于数据隔离

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title; // 对话标题
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt; // 创建时间，使用ISO 8601格式
    
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content; // 对话内容摘要或全部消息内容
    
    @Field(type = FieldType.Keyword)
    private List<String> participants; // 参与者列表（user, ai, system等）
    
    @Field(type = FieldType.Keyword)
    private List<String> models; // 使用的AI模型列表
    
    @Field(type = FieldType.Integer)
    private Integer messageCount; // 消息数量
    
    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastMessageTime; // 最后一条消息时间，使用ISO 8601格式
    
    @Field(type = FieldType.Keyword)
    private List<String> tags; // 标签，用于分类
    
    /**
     * 构造函数，从数据库实体创建ES文档
     * 
     * @param conversationId 会话业务ID
     * @param userId 用户ID
     * @param title 会话标题
     * @param createdAt 创建时间
     * @param content 会话内容
     * @param participants 参与者列表
     * @param models 模型列表
     * @param messageCount 消息数量
     * @param lastMessageTime 最后消息时间
     */
    public ConversationDocument(Long conversationId, Long userId, String title, LocalDateTime createdAt, 
                              String content, List<String> participants, List<String> models,
                              Integer messageCount, LocalDateTime lastMessageTime) {
        // 参数验证
        Objects.requireNonNull(conversationId, "会话ID不能为null");
        Objects.requireNonNull(userId, "用户ID不能为null");
        Objects.requireNonNull(title, "会话标题不能为null");
        Objects.requireNonNull(createdAt, "创建时间不能为null");
        Objects.requireNonNull(content, "会话内容不能为null");
        Objects.requireNonNull(participants, "参与者列表不能为null");
        Objects.requireNonNull(models, "模型列表不能为null");
        Objects.requireNonNull(messageCount, "消息数量不能为null");
        Objects.requireNonNull(lastMessageTime, "最后消息时间不能为null");
        
        if (messageCount < 0) {
            throw new IllegalArgumentException("消息数量不能为负数");
        }
        
        // 设置字段值
        this.id = conversationId.toString();
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title.trim();
        this.createdAt = createdAt;
        this.content = content;
        this.participants = participants;
        this.models = models;
        this.messageCount = messageCount;
        this.lastMessageTime = lastMessageTime;
    }
}