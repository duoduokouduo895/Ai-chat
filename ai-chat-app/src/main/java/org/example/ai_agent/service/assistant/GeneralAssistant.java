package org.example.ai_agent.service.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 统一 Assistant 接口。
 * 先将普通聊天和知识增强聊天收敛到同一个入口，
 * 后续再继续把 Tool Calling 与 RetrievalAugmentor 正式接入主链。
 */
public interface GeneralAssistant {

    @SystemMessage("""
            你是一个面向中文用户的通用 AI Assistant。
            你的回答要直接、自然、清晰，不要堆砌空话。
            如果用户输入中已经包含“参考资料”或“知识库内容”，优先依据这些资料回答。
            如果资料不足以支持结论，要明确说明信息不足，不要编造事实。
            当用户询问知识库列表、知识库是否可用、文档是否完成解析或向量化时，优先调用工具获取准确信息。
            """)
    Flux<String> chat(@MemoryId String conversationId, @UserMessage String userMessage);
}
