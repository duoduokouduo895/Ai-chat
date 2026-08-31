package org.example.ai_agent.config.database;

import org.example.ai_agent.document.ConversationDocument;
import org.example.ai_agent.document.KnowledgeChunkDocument;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElasticsearchMappingContractTest {

    @Test
    void shouldKeepIkAnalyzersOnChineseFullTextFields() throws NoSuchFieldException {
        assertIkField(ConversationDocument.class, "title");
        assertIkField(ConversationDocument.class, "content");
        assertIkField(KnowledgeChunkDocument.class, "content");
    }

    @Test
    void shouldKeepReviewedIndexNamesInsideCandidateRoleAllowlist() {
        assertEquals("conversations", ConversationDocument.class.getAnnotation(Document.class).indexName());
        assertEquals("knowledge_base_chunks", KnowledgeChunkDocument.class.getAnnotation(Document.class).indexName());
    }

    private void assertIkField(Class<?> documentType, String fieldName) throws NoSuchFieldException {
        Field field = documentType.getDeclaredField(fieldName).getAnnotation(Field.class);
        assertEquals("ik_max_word", field.analyzer());
        assertEquals("ik_smart", field.searchAnalyzer());
    }
}
