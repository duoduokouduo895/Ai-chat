package org.example.ai_agent.service.registry;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.example.ai_agent.config.ai.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.config.database.ElasticsearchConfig;
import org.example.ai_agent.entity.KnowledgeBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 Embedding profile 管理 Elasticsearch 向量索引。
 * 新 profile 使用版本化索引；旧知识库继续访问历史固定索引，等待显式迁移。
 */
@Slf4j
@Service
public class EmbeddingStoreRegistry {

    private final AiModelRegistry aiModelRegistry;
    private final ElasticsearchConfig elasticsearchConfig;
    private final Map<String, EmbeddingStore<TextSegment>> stores = new ConcurrentHashMap<>();
    private final Set<String> initializedIndexes = ConcurrentHashMap.newKeySet();
    private volatile RestClient restClient;

    @Value("${knowledge-base.embedding.index-name:knowledge_base_embeddings}")
    private String legacyIndexName;

    @Value("${knowledge-base.embedding.versioned-index-prefix:knowledge_base_embeddings_}")
    private String versionedIndexPrefix;

    public EmbeddingStoreRegistry(AiModelRegistry aiModelRegistry, ElasticsearchConfig elasticsearchConfig) {
        this.aiModelRegistry = aiModelRegistry;
        this.elasticsearchConfig = elasticsearchConfig;
    }

    public EmbeddingProfile createProfile(String modelAlias) {
        String resolvedAlias = aiModelRegistry.resolveEmbeddingAlias(modelAlias);
        ModelConfig config = aiModelRegistry.getEmbeddingModelConfig(resolvedAlias);
        return new EmbeddingProfile(
                resolvedAlias,
                config.getVersion(),
                config.getDimension(),
                versionedIndexPrefix + normalizeVersion(config.getVersion()),
                false
        );
    }

    public EmbeddingProfile resolveProfile(KnowledgeBase knowledgeBase) {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不能为空");
        }

        if (!StringUtils.hasText(knowledgeBase.getIndexName())
                || legacyIndexName.equals(knowledgeBase.getIndexName())
                || knowledgeBase.getIndexName().startsWith("kb_")) {
            return createLegacyProfile(knowledgeBase.getEmbeddingModel());
        }

        EmbeddingProfile profile = createProfile(knowledgeBase.getEmbeddingModel());
        if (!profile.indexName().equals(knowledgeBase.getIndexName())) {
            throw new IllegalStateException("知识库 Embedding profile 已变化，请保留历史模型配置并执行显式迁移: knowledgeBaseId="
                    + knowledgeBase.getId());
        }
        return profile;
    }

    public EmbeddingProfile requireProfile(String modelAlias, String indexName) {
        if (legacyIndexName.equals(indexName)) {
            return createLegacyProfile(modelAlias);
        }

        EmbeddingProfile profile = createProfile(modelAlias);
        if (!profile.indexName().equals(indexName)) {
            throw new IllegalArgumentException("Embedding 索引与模型版本不匹配: modelAlias=" + modelAlias
                    + ", indexName=" + indexName);
        }
        return profile;
    }

    public EmbeddingStore<TextSegment> getStore(EmbeddingProfile profile) {
        return stores.computeIfAbsent(profile.indexName(), this::createStore);
    }

    public EmbeddingStore<TextSegment> getWritableStore(EmbeddingProfile profile) {
        ensureIndexExists(profile);
        return getStore(profile);
    }

    public List<EmbeddingProfile> getCleanupProfiles() {
        Map<String, EmbeddingProfile> profiles = new LinkedHashMap<>();
        EmbeddingProfile legacy = createLegacyProfile(aiModelRegistry.getDefaultEmbeddingModel());
        profiles.put(legacy.indexName(), legacy);
        aiModelRegistry.getAvailableEmbeddingModels().stream()
                .map(this::createProfile)
                .forEach(profile -> profiles.put(profile.indexName(), profile));
        return new ArrayList<>(profiles.values());
    }

    private EmbeddingProfile createLegacyProfile(String requestedAlias) {
        String alias;
        try {
            alias = aiModelRegistry.resolveEmbeddingAlias(requestedAlias);
        } catch (IllegalArgumentException ignored) {
            alias = aiModelRegistry.getDefaultEmbeddingModel();
            log.warn("知识库仍使用旧 Embedding 标识 {}，迁移期间临时映射到 {}", requestedAlias, alias);
        }
        ModelConfig config = aiModelRegistry.getEmbeddingModelConfig(alias);
        return new EmbeddingProfile(alias, config.getVersion(), config.getDimension(), legacyIndexName, true);
    }

    private EmbeddingStore<TextSegment> createStore(String indexName) {
        return ElasticsearchEmbeddingStore.builder()
                .restClient(getRestClient())
                .indexName(indexName)
                .build();
    }

    private void ensureIndexExists(EmbeddingProfile profile) {
        if (initializedIndexes.contains(profile.indexName())) {
            return;
        }

        synchronized (initializedIndexes) {
            if (initializedIndexes.contains(profile.indexName())) {
                return;
            }
            try {
                Request existsRequest = new Request("HEAD", "/" + profile.indexName());
                try {
                    getRestClient().performRequest(existsRequest);
                } catch (ResponseException e) {
                    if (e.getResponse().getStatusLine().getStatusCode() != 404) {
                        throw e;
                    }
                    Request createRequest = new Request("PUT", "/" + profile.indexName());
                    createRequest.setJsonEntity(buildEmbeddingIndexMapping(profile.dimension()));
                    getRestClient().performRequest(createRequest);
                    log.info("Elasticsearch 向量索引创建成功: indexName={}, dimension={}",
                            profile.indexName(), profile.dimension());
                }
                initializedIndexes.add(profile.indexName());
            } catch (IOException e) {
                throw new IllegalStateException("初始化 Elasticsearch 向量索引失败: " + profile.indexName(), e);
            }
        }
    }

    public void refresh(EmbeddingProfile profile) throws IOException {
        getRestClient().performRequest(new Request("POST", "/" + profile.indexName() + "/_refresh"));
    }

    private RestClient getRestClient() {
        if (restClient != null) {
            return restClient;
        }

        synchronized (this) {
            if (restClient == null) {
                URI uri = URI.create(elasticsearchConfig.getElasticsearchUri());
                int port = uri.getPort() > 0 ? uri.getPort() : 9200;
                HttpHost httpHost = new HttpHost(uri.getHost(), port, uri.getScheme());
                restClient = RestClient.builder(httpHost)
                        .setRequestConfigCallback(builder -> builder
                                .setConnectTimeout(Math.max(1000, Math.min(30000, elasticsearchConfig.getConnectionTimeout())))
                                .setSocketTimeout(Math.max(1000, Math.min(120000, elasticsearchConfig.getSocketTimeout()))))
                        .setHttpClientConfigCallback(builder -> {
                            if (!elasticsearchConfig.hasCredentials()) {
                                return builder;
                            }
                            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                            credentialsProvider.setCredentials(
                                    AuthScope.ANY,
                                    new UsernamePasswordCredentials(
                                            elasticsearchConfig.getUsername(),
                                            elasticsearchConfig.getPassword()
                                    )
                            );
                            return builder.setDefaultCredentialsProvider(credentialsProvider);
                        })
                        .build();
            }
            return restClient;
        }
    }

    private String normalizeVersion(String version) {
        if (!StringUtils.hasText(version)) {
            throw new IllegalArgumentException("Embedding version 不能为空");
        }
        return version.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    private String buildEmbeddingIndexMapping(int dimension) {
        return """
                {
                  "mappings": {
                    "properties": {
                      "vector": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      },
                      "text": {
                        "type": "text"
                      },
                      "metadata": {
                        "type": "object",
                        "dynamic": true
                      }
                    }
                  }
                }
                """.formatted(dimension);
    }

    @PreDestroy
    public void close() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }

    public record EmbeddingProfile(
            String modelAlias,
            String version,
            int dimension,
            String indexName,
            boolean legacy
    ) {
    }
}
