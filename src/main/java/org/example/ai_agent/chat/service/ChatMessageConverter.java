package org.example.ai_agent.chat.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.example.ai_agent.chat.entity.Message;

import java.time.LocalDateTime;

/**
 * ChatMessage转换器
 * 用于在Message实体和ChatMessage数据传输对象之间进行转换
 *
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
public class ChatMessageConverter {

    /**
     * 将Message实体转换为ChatMessage对象
     *
     * @param message 消息实体
     * @return ChatMessage对象
     */
    public static ChatMessage toChatMessage(Message message) {
        // 根据消息的发送者类型，创建UserMessage或AiMessage
        if ("user".equalsIgnoreCase(message.getSender())) {
            return UserMessage.from(message.getContent());
        } else {
            return AiMessage.from(message.getContent());
        }
    }

    /**
     * 将ChatMessage对象转换为Message实体
     *
     * @param chatMessage    ChatMessage对象
     * @param conversationId 关联的对话ID
     * @return Message实体
     */
    public static Message fromChatMessage(ChatMessage chatMessage, Long conversationId) {
        Message message = new Message();
        message.setConversationId(conversationId);
        // 根据LangChain4j版本，尝试不同的文本获取方法
        String content;
        if (chatMessage instanceof UserMessage) {
            content = ((UserMessage) chatMessage).singleText();
        } else if (chatMessage instanceof AiMessage) {
            content = ((AiMessage) chatMessage).text();
        } else {
            content = chatMessage.toString(); // 备用方案
        }
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());

        // 根据ChatMessage的类型，设置发送者
        if (chatMessage instanceof UserMessage) {
            message.setSender("user");
        } else if (chatMessage instanceof AiMessage) {
            message.setSender("ai");
        } else {
            // 对于其他类型的消息，可以设置为system或直接抛出异常
            message.setSender("system");
        }

        return message;
    }
}
