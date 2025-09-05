package org.example.ai_agent.service;

import org.example.ai_agent.dto.chat.ChatRequest;
import reactor.core.publisher.Flux;

public interface AiChatService {
    Flux<String> chat(ChatRequest chatRequest);
}
