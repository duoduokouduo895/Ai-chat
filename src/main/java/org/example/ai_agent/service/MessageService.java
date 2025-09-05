package org.example.ai_agent.service;

import org.example.ai_agent.entity.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageService {
    Mono<Message> saveMessage(Message message);
    Flux<Message> findMessagesByConversationId(Long conversationId);
}
