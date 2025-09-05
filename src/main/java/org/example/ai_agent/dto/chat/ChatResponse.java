package org.example.ai_agent.dto.chat;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * AI聊天响应DTO
 * <p>
 * 用于封装API响应数据，提供统一的响应格式。
 * 支持成功和失败两种状态，包含响应码、消息和数据。
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse<T> {
    
    /**
     * 响应状态码
     * 200: 成功
     * 400: 客户端错误
     * 500: 服务器错误
     */
    private Integer code;
    
    /**
     * 响应消息
     * 用于描述响应状态或错误信息
     */
    private String message;
    
    /**
     * 响应数据
     * 泛型类型，可以包含任何类型的数据
     */
    private T data;
    
    /**
     * 响应时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    /**
     * 请求追踪ID
     * 用于日志追踪和问题排查
     */
    private String traceId;
    
    /**
     * 是否成功
     * 
     * @return 如果响应码为200返回true，否则返回false
     */
    public boolean isSuccess() {
        return code != null && code == 200;
    }
    
    /**
     * 创建成功响应
     * 
     * @param message 成功消息
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> success(String message) {
        return ChatResponse.<T>builder()
                .code(200)
                .message(message)
                .build();
    }
    
    /**
     * 创建成功响应（带数据）
     * 
     * @param message 成功消息
     * @param data 响应数据
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> success(String message, T data) {
        return ChatResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }
    
    /**
     * 创建成功响应（仅数据）
     * 
     * @param data 响应数据
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> success(T data) {
        return ChatResponse.<T>builder()
                .code(200)
                .message("操作成功")
                .data(data)
                .build();
    }
    
    /**
     * 创建客户端错误响应
     * 
     * @param message 错误消息
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> clientError(String message) {
        return ChatResponse.<T>builder()
                .code(400)
                .message(message)
                .build();
    }
    
    /**
     * 创建服务器错误响应
     * 
     * @param message 错误消息
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> serverError(String message) {
        return ChatResponse.<T>builder()
                .code(500)
                .message(message)
                .build();
    }
    
    /**
     * 创建自定义错误响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @return ChatResponse实例
     */
    public static <T> ChatResponse<T> error(Integer code, String message) {
        return ChatResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
    
    /**
     * 设置追踪ID
     * 
     * @param traceId 追踪ID
     * @return 当前实例（支持链式调用）
     */
    public ChatResponse withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
    
    /**
     * 设置数据
     * 
     * @param data 响应数据
     * @return 当前实例（支持链式调用）
     */
    public ChatResponse<T> withData(T data) {
        this.data = data;
        return this;
    }
}