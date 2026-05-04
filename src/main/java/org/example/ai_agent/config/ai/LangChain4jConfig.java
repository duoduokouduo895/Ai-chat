package org.example.ai_agent.config.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    /**
     * 统一通过 {@link org.example.ai_agent.service.provider.ChatMemoryProvider}
     * 为每个会话创建独立的 ChatMemory。
     *
     * 这里不再暴露共享的单例 ChatMemory Bean，避免和按会话隔离的记忆设计冲突。
     */
}
