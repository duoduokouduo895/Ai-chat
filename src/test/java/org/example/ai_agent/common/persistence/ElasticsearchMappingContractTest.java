package org.example.ai_agent.common.persistence;

import org.example.ai_agent.search.entity.ConversationDocument;
import org.example.ai_agent.knowledge.entity.KnowledgeChunkDocument;
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
        assertEquals("knowledge_base_chunks", resolvedKnowledgeIndex(null));
    }

    @Test
    void isolatedConfigurationResolvesOnlyTheNamedTestIndex() {
        assertEquals("ai-chat-it-20260907-test", resolvedKnowledgeIndex("ai-chat-it-20260907-test"));
    }

    private String resolvedKnowledgeIndex(String index) {
        try (var context = new org.springframework.context.support.GenericApplicationContext()) {
            if (index != null) context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test", java.util.Map.of(
                            "knowledge-base.search.index-name", index)));
            context.refresh();
            var mapping = new org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext();
            mapping.setApplicationContext(context);
            return mapping.getRequiredPersistentEntity(KnowledgeChunkDocument.class)
                    .getIndexCoordinates().getIndexName();
        }
    }

    private void assertIkField(Class<?> documentType, String fieldName) throws NoSuchFieldException {
        Field field = documentType.getDeclaredField(fieldName).getAnnotation(Field.class);
        assertEquals("ik_max_word", field.analyzer());
        assertEquals("ik_smart", field.searchAnalyzer());
    }
}
