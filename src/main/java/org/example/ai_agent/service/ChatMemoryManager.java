package org.example.ai_agent.service;

import dev.langchain4j.data.message.ChatMessage;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 聊天记忆管理器接口
 * <p>
 * 负责管理聊天记忆的加载和保存，支持从数据库中获取历史消息
 * 并将新的聊天记录保存到数据库中。
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public interface ChatMemoryManager {
    
    /**
     * 获取指定对话的历史消息
     * 
     * @param conversationId 对话ID
     * @return 历史消息列表
     */
    List<ChatMessage> getMessages(Long conversationId);
    
    /**
     * 保存聊天消息到数据库
     * 
     * @param conversationId 对话ID
     * @param messages 要保存的消息列表
     * @param modelId 使用的模型ID
     * @return 保存结果的Mono
     */
    Mono<Void> saveMessages(Long conversationId, List<ChatMessage> messages, String modelId);
    
    /**
     * 清除指定对话的聊天记忆
     * 
     * @param conversationId 对话ID
     * @return 清除结果的Mono
     */
    Mono<Void> clearMemory(Long conversationId);
}