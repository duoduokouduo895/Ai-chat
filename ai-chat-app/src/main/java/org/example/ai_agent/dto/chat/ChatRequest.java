package org.example.ai_agent.dto.chat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * AI聊天请求DTO
 * <p>
 * 用于封装前端发送的聊天请求数据，包括用户信息、会话信息和消息内容。
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    
    /**
     * 用户ID
     * 必填字段，用于标识发送消息的用户
     */
    private Long userId;
    
    /**
     * 会话ID
     * 可选字段，如果为空则创建新会话
     */
    @Positive(message = "会话ID必须为正数")
    private Long conversationId;
    
    /**
     * 用户消息内容
     * 必填字段，不能为空或空白字符串
     * 前端字段名：prompt
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 20000, message = "消息内容长度不能超过20000个字符")
    private String prompt;
    
    /**
     * AI模型ID
     * 可选字段，如果为空则使用默认模型
     * 前端字段名：model
     */
    @NotBlank(message = "模型ID不能为空")
    @Size(max = 120, message = "模型ID长度不能超过120个字符")
    private String model;
    
    /**
     * 用户消息内容（兼容性字段）
     * 获取prompt字段的值
     */
    public String getMessage() {
        return prompt;
    }
    
    /**
     * 设置用户消息内容（兼容性方法）
     */
    public void setMessage(String message) {
        this.prompt = message;
    }
    
    /**
     * AI模型ID（兼容性字段）
     * 获取model字段的值
     */
    public String getModelId() {
        return model;
    }
    
    /**
     * 设置AI模型ID（兼容性方法）
     */
    public void setModelId(String modelId) {
        this.model = modelId;
    }
    
    /**
     * 会话标题
     * 可选字段，用于创建新会话时设置标题
     */
    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    private String title;

    /**
     * 客户端为本次发送生成的稳定ID，用于网络重试时去重。
     */
    @Size(max = 100, message = "客户端消息ID长度不能超过100个字符")
    private String clientMessageId;
    
    /**
     * 是否保存消息到数据库
     * 默认为true，设置为false时仅进行临时对话
     */
    @Builder.Default
    private Boolean saveMessage = true;
    
    /**
     * 流式响应配置
     * 默认为true，设置为false时返回完整响应
     */
    @Builder.Default
    private Boolean streaming = true;

    /**
     * 是否为管理员上下文。
     * 由后端根据认证信息写入，前端无需传递。
     */
    @Builder.Default
    private Boolean adminRequest = false;
    
    /**
     * 知识库ID列表
     * 可选字段，如果提供则使用RAG模式进行对话
     */
    @Size(max = 20, message = "知识库数量不能超过20个")
    private java.util.List<@Positive(message = "知识库ID必须为正数") Long> knowledgeBaseIds;
    
    /**
     * 最大检索文档数
     * RAG模式下的参数，默认为5
     */
    @Max(value = 20, message = "最大检索文档数不能超过20")
    @Positive(message = "最大检索文档数必须为正数")
    @Builder.Default
    private Integer maxRetrievedDocs = 5;
    
    /**
     * 最小相似度
     * RAG模式下的参数，默认为0.3
     */
    @DecimalMin(value = "0.0", message = "最小相似度不能小于0")
    @DecimalMax(value = "1.0", message = "最小相似度不能大于1")
    @Builder.Default
    private Double minSimilarity = 0.3;
    
    /**
     * 验证请求参数的有效性
     * 
     * @return 如果参数有效返回true，否则返回false
     */
    public boolean isValid() {
        return userId != null && prompt != null && !prompt.trim().isEmpty();
    }
    
    /**
     * 获取处理后的消息内容（去除首尾空白字符）
     * 
     * @return 处理后的消息内容
     */
    public String getTrimmedMessage() {
        return prompt != null ? prompt.trim() : "";
    }
    
    /**
     * 检查是否需要创建新会话
     * 
     * @return 如果会话ID为空返回true，否则返回false
     */
    public boolean needsNewConversation() {
        return conversationId == null;
    }
    
    /**
     * 获取有效的模型ID（如果为空则返回默认值）
     * 
     * @return 模型ID或默认值
     */
    public String getEffectiveModelId() {
        return model != null && !model.trim().isEmpty() ? model.trim() : "default";
    }
    
    /**
     * 检查是否为RAG模式
     * 
     * @return 如果提供了知识库ID则返回true
     */
    public boolean isRagMode() {
        return knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty();
    }

    public boolean isAdminRequest() {
        return Boolean.TRUE.equals(adminRequest);
    }
    
    /**
     * 转换为RagChatDto
     * 
     * @return RAG对话请求DTO
     */
    public RagChatDto toRagChatDto() {
        RagChatDto ragDto = new RagChatDto();
        ragDto.setQuestion(this.getTrimmedMessage());
        ragDto.setKnowledgeBaseIds(this.knowledgeBaseIds);
        ragDto.setModelId(this.getEffectiveModelId());
        ragDto.setConversationId(this.conversationId);
        ragDto.setMaxRetrievedDocs(this.maxRetrievedDocs);
        ragDto.setMinSimilarity(this.minSimilarity);
        return ragDto;
    }
}
