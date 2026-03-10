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
    Flux<Message> getMessagesByConversationId(Long conversationId, Long userId);
    Flux<ConversationWithPreview> getRecentConversationsWithPreview(Long userId);
    Flux<ConversationWithPreview> getAllRecentConversations(Integer page, Integer size);
    Flux<ConversationWithPreview> getConversationsForAdminByUserId(Long userId, Integer page, Integer size);
    Mono<Conversation> getConversationById(Long conversationId);
    Mono<Conversation> getConversationById(Long conversationId, Long userId);
    Mono<Void> deleteConversation(Long conversationId, Long userId);
    Mono<Void> deleteConversationAsAdmin(Long conversationId);
}

