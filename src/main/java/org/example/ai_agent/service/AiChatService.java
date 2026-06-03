package org.example.ai_agent.service;

import org.example.ai_agent.dto.chat.ChatRequest;
import reactor.core.publisher.Flux;

import org.springframework.http.codec.ServerSentEvent;

public interface AiChatService {
    Flux<ServerSentEvent<String>> chat(ChatRequest chatRequest);
}
