package org.example.ai_agent.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI任务数据传输对象
 * 功能说明：
 * 1. 封装异步AI处理任务的所有必要信息
 * 2. 用于在RabbitMQ消息队列中传输任务数据
 * 3. 支持任务状态跟踪和重试机制
 * 4. 包含用户请求信息和处理上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskDto {

    /**
     * 任务唯一标识符
     * 作用：用于跟踪和识别特定的AI处理任务
     */
    private String taskId;

    /**
     * 会话ID
     * 作用：关联到具体的对话会话，维护上下文连续性
     */
    private Long conversationId;

    /**
     * 用户消息内容
     * 作用：存储用户发送的原始消息文本
     */
    private String userMessage;

    /**
     * AI模型名称
     * 作用：指定使用哪个AI模型进行处理（如GPT-4、Claude等）
     */
    private String modelName;

    /**
     * 任务创建时间
     * 作用：记录任务创建的时间戳，用于监控和超时处理
     */
    private LocalDateTime createdAt;

    /**
     * 任务优先级
     * 作用：支持任务优先级处理，重要任务可优先执行
     */
    @Builder.Default
    private Integer priority = 1;

    /**
     * 重试次数
     * 作用：记录任务已重试的次数，防止无限重试
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 最大重试次数
     * 作用：设置任务的最大重试限制
     */
    @Builder.Default
    private Integer maxRetries = 3;

    /**
     * 任务状态
     * 作用：跟踪任务的处理状态（PENDING、PROCESSING、COMPLETED、FAILED）
     */
    @Builder.Default
    private String status = "PENDING";

    /**
     * 错误信息
     * 作用：记录任务处理过程中的错误信息，便于调试和监控
     */
    private String errorMessage;

    /**
     * 客户端IP地址
     * 作用：记录请求来源，用于安全审计和限流
     */
    private String clientIp;

    /**
     * 用户代理信息
     * 作用：记录客户端信息，用于统计和分析
     */
    private String userAgent;

    /**
     * 扩展属性
     * 作用：存储额外的任务相关信息，提供灵活性
     */
    private String metadata;

    /**
     * 检查任务是否可以重试
     * @return 如果重试次数未达到上限则返回true
     */
    public boolean canRetry() {
        return retryCount < maxRetries;
    }

    /**
     * 增加重试次数
     * 作用：在任务重试时调用，更新重试计数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
}