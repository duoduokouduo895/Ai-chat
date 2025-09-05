package org.example.ai_agent.service;

import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


    public interface ConversationService {
    Mono<Conversation> createConversation(Conversation conversation);
    Mono<Message> addMessageToConversation(Message message);
    Flux<Message> getMessagesByConversationId(Long conversationId);
    Flux<ConversationWithPreview> getRecentConversationsWithPreview(Long userId);
    Mono<Conversation> getConversationById(Long conversationId);
    Mono<Void> deleteConversation(Long conversationId, Long userId);
}

