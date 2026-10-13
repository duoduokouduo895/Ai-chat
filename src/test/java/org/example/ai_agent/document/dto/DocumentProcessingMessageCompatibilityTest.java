package org.example.ai_agent.document.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.document.domain.DocumentProcessingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DocumentProcessingMessageCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void legacyPayloadWithoutVersionedIngestionFieldsRemainsReadable() throws Exception {
        String legacyPayload = """
                {
                  "documentId": 9200001,
                  "knowledgeBaseId": 9200001,
                  "processingType": "EMBED",
                  "retryCount": 0
                }
                """;

        DocumentProcessingMessage message = objectMapper.readValue(
                legacyPayload, DocumentProcessingMessage.class);

        assertEquals(9200001L, message.getDocumentId());
        assertEquals(9200001L, message.getKnowledgeBaseId());
        assertEquals(DocumentProcessingType.EMBED, message.processingType());
        assertNull(message.getRunId());
        assertNull(message.getVersionId());
        assertNull(message.getGeneration());
    }
}
