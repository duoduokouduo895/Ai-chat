package org.example.ai_agent.service.registry;

import org.example.ai_agent.config.ai.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.config.database.ElasticsearchConfig;
import org.example.ai_agent.entity.KnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingStoreRegistryTest {

    @Mock
    private AiModelRegistry aiModelRegistry;
    @Mock
    private ElasticsearchConfig elasticsearchConfig;

    private EmbeddingStoreRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EmbeddingStoreRegistry(aiModelRegistry, elasticsearchConfig);
        ReflectionTestUtils.setField(registry, "legacyIndexName", "knowledge_base_embeddings");
        ReflectionTestUtils.setField(registry, "versionedIndexPrefix", "knowledge_base_embeddings_");
    }

    @Test
    void shouldCreateVersionedProfileForNewKnowledgeBase() {
        ModelConfig config = embeddingConfig("qwen3-v2", 4096);
        when(aiModelRegistry.resolveEmbeddingAlias("embedding-current")).thenReturn("embedding-current");
        when(aiModelRegistry.getEmbeddingModelConfig("embedding-current")).thenReturn(config);

        EmbeddingStoreRegistry.EmbeddingProfile profile = registry.createProfile("embedding-current");

        assertEquals("embedding-current", profile.modelAlias());
        assertEquals("knowledge_base_embeddings_qwen3-v2", profile.indexName());
        assertEquals(4096, profile.dimension());
        assertEquals(false, profile.legacy());
    }

    @Test
    void shouldMapOldKnowledgeBaseToLegacyIndex() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(10L);
        knowledgeBase.setEmbeddingModel("qwen-embedding-8b");
        knowledgeBase.setIndexName("kb_1700000000000");

        ModelConfig config = embeddingConfig("legacy-v1", 4096);
        when(aiModelRegistry.resolveEmbeddingAlias("qwen-embedding-8b"))
                .thenThrow(new IllegalArgumentException("unknown"));
        when(aiModelRegistry.getDefaultEmbeddingModel()).thenReturn("embedding-default");
        when(aiModelRegistry.getEmbeddingModelConfig("embedding-default")).thenReturn(config);

        EmbeddingStoreRegistry.EmbeddingProfile profile = registry.resolveProfile(knowledgeBase);

        assertEquals("embedding-default", profile.modelAlias());
        assertEquals("knowledge_base_embeddings", profile.indexName());
        assertEquals(true, profile.legacy());
    }

    @Test
    void shouldRejectSilentProfileMutation() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(11L);
        knowledgeBase.setEmbeddingModel("embedding-current");
        knowledgeBase.setIndexName("knowledge_base_embeddings_qwen3-v1");

        ModelConfig config = embeddingConfig("qwen3-v2", 4096);
        when(aiModelRegistry.resolveEmbeddingAlias("embedding-current")).thenReturn("embedding-current");
        when(aiModelRegistry.getEmbeddingModelConfig("embedding-current")).thenReturn(config);

        assertThrows(IllegalStateException.class, () -> registry.resolveProfile(knowledgeBase));
    }

    private ModelConfig embeddingConfig(String version, int dimension) {
        ModelConfig config = new ModelConfig();
        config.setVersion(version);
        config.setDimension(dimension);
        return config;
    }
}
