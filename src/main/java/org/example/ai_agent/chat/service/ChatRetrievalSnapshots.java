package org.example.ai_agent.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.knowledge.domain.RetrievalSnapshot;
import org.example.ai_agent.knowledge.service.RagService;

final class ChatRetrievalSnapshots {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ChatRetrievalSnapshots() { }

    static String encode(RagService.RetrievalContext context) {
        var sources = context.relevantDocuments().stream().map(match -> new RetrievalSnapshot.Source(
                match.getKnowledgeBaseId(), match.getDocumentId(), match.getVersionId(), match.getChunkId())).toList();
        try {
            return JSON.writeValueAsString(new RetrievalSnapshot(context.status(), context.channels(), sources));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Retrieval references could not be serialized", failure);
        }
    }
}
