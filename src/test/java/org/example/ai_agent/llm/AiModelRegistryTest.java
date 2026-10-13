package org.example.ai_agent.llm;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModelRegistryTest {

    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    @Test
    void shouldExposeStableChatAliasAndRegisterEmbeddingModel() {
        ModelCatalogProperties properties = createProperties();
        AiModelRegistry registry = new AiModelRegistry(properties, SAME_THREAD_EXECUTOR);

        registry.init();

        assertEquals(Set.of("chat-fast"), registry.getAvailableModels());
        assertEquals("chat-fast", registry.getModelMetadata().get(0).getValue());
        assertEquals(ModelType.CHAT, registry.getModelMetadata().get(0).getType());

        StreamingChatModel chatModel = registry.getStreamingModel("chat-fast");
        assertNotNull(chatModel);
        assertEquals(chatModel, registry.getStreamingModel("provider/chat-fast"));

        EmbeddingModel embeddingModel = registry.getEmbeddingModel("embedding-default");
        assertNotNull(embeddingModel);
        assertEquals("embedding-default", registry.getDefaultEmbeddingModel());
    }

    @Test
    void shouldKeepPreviousSnapshotWhenRefreshFails() {
        ModelCatalogProperties properties = createProperties();
        AiModelRegistry registry = new AiModelRegistry(properties, SAME_THREAD_EXECUTOR);
        registry.init();

        properties.getModels().get("chat-fast").setTimeout(0);

        assertThrows(IllegalStateException.class, registry::refresh);
        assertNotNull(registry.getStreamingModel("chat-fast"));
        assertNotNull(registry.getEmbeddingModel("embedding-default"));
    }

    private ModelCatalogProperties createProperties() {
        ModelCatalogProperties properties = new ModelCatalogProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://localhost:9999/v1");
        properties.setDefaultChatModel("chat-fast");
        properties.setDefaultEmbeddingModel("embedding-default");

        Map<String, ModelConfig> models = new LinkedHashMap<>();
        models.put("chat-fast", createChatModel());
        models.put("embedding-default", createEmbeddingModel());
        properties.setModels(models);
        return properties;
    }

    private ModelConfig createChatModel() {
        ModelConfig model = new ModelConfig();
        model.setType(ModelType.CHAT);
        model.setEnabled(true);
        model.setProviderModelName("provider/chat-fast");
        model.setTemperature(0.4);
        model.setMaxTokens(4096);
        model.setTimeout(15000);
        return model;
    }

    private ModelConfig createEmbeddingModel() {
        ModelConfig model = new ModelConfig();
        model.setType(ModelType.EMBEDDING);
        model.setEnabled(true);
        model.setProviderModelName("provider/embedding-v1");
        model.setDimension(1024);
        model.setVersion("embedding-v1");
        model.setTimeout(15000);
        return model;
    }
}
