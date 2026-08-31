package org.example.ai_agent.config.ai;

import org.example.ai_agent.dto.chat.ChatRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Resolves the server-side model alias used for each chat mode.
 *
 * <p>V1 treats RAG generation as a separate capability from ordinary chat.
 * The configured RAG model has passed the retrieval-augmented streaming
 * contract and therefore does not depend on whichever model the UI selected
 * for a normal conversation.</p>
 */
@Component
public class ChatModelRoutingPolicy {

    private final String ragModelAlias;

    public ChatModelRoutingPolicy(
            @Value("${knowledge-base.rag.chat-model:chat-reasoning}") String ragModelAlias) {
        Assert.hasText(ragModelAlias, "knowledge-base.rag.chat-model must not be blank");
        this.ragModelAlias = ragModelAlias.trim();
    }

    public String resolve(ChatRequest request) {
        Assert.notNull(request, "chat request must not be null");
        return request.isRagMode() ? ragModelAlias : request.getEffectiveModelId();
    }
}
