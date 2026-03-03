package org.example.ai_agent.config.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * LangChain4j的动态化配置类
 * <p>
 * 负责配置AI模型Bean、聊天记忆等核心组件。
 * 支持从配置文件中读取模型参数并创建相应的Bean实例。
 * </p>
 * 
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AiModelProperties.class)
public class LangChain4jConfig {
    
    @Autowired
    private ChatMemoryStore store;
    
    @Autowired
    private AiModelProperties aiModelProperties;

    //构建会话记忆对象
    @Bean
    public ChatMemory chatMemory(){
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20) // 每个对话保留最近20条消息
                .build();
        return chatMemory;
    }

    
    
    /**
     * AiModelRegistry在应用启动时会：
     * 1. 从Nacos配置中心读取API密钥和基础URL
     * 2. 根据配置动态创建所有AI模型实例
     * 3. 提供按模型名称获取模型实例的方法
     * 
     * 这种设计避免了硬编码API密钥，支持动态模型选择，
     * 并且符合单一职责原则：LangChain4jConfig只负责聊天记忆配置
     */
}