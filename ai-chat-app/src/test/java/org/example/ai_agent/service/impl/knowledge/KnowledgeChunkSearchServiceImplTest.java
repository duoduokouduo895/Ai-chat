package org.example.ai_agent.service.impl.knowledge;

import org.example.ai_agent.dto.knowledge.KnowledgeChunkIndexProjection;
import org.example.ai_agent.service.KnowledgeChunkSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.relational.core.mapping.Column;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkSearchServiceImplTest {

    @Test
    void nonAdminQueryAddsRedundantPermissionAndStatusFilters() {
        KnowledgeChunkSearchServiceImpl service = new KnowledgeChunkSearchServiceImpl(null, null, null, null);

        StringQuery query = service.buildSearchQuery(new KnowledgeChunkSearchService.SearchRequest(
                "退款流程",
                List.of(10L, 11L),
                99L,
                false,
                20));

        String source = query.getSource();
        assertTrue(source.contains("\"knowledgeBaseStatus\":\"ACTIVE\""));
        assertTrue(source.contains("\"documentParseStatus\":\"COMPLETED\""));
        assertTrue(source.contains("\"knowledgeBaseId\":[10,11]"));
        assertTrue(source.contains("\"knowledgeBaseIsPublic\":true"));
        assertTrue(source.contains("\"knowledgeBaseCreatorId\":99"));
        assertTrue(source.contains("\"documentUploaderId\":99"));
        assertTrue(source.contains("\"minimum_should_match\":1"));
    }

    @Test
    void adminQueryStillFiltersStatusButSkipsUserOwnershipFilter() {
        KnowledgeChunkSearchServiceImpl service = new KnowledgeChunkSearchServiceImpl(null, null, null, null);

        StringQuery query = service.buildSearchQuery(new KnowledgeChunkSearchService.SearchRequest(
                "监控告警",
                List.of(12L),
                null,
                true,
                10));

        String source = query.getSource();
        assertTrue(source.contains("\"knowledgeBaseStatus\":\"ACTIVE\""));
        assertTrue(source.contains("\"documentParseStatus\":\"COMPLETED\""));
        assertTrue(source.contains("\"knowledgeBaseId\":[12]"));
        assertFalse(source.contains("knowledgeBaseCreatorId"));
        assertFalse(source.contains("documentUploaderId"));
        assertFalse(source.contains("knowledgeBaseIsPublic"));
    }

    @Test
    void bm25QueryDoesNotRequireChunkEmbeddingStatus() {
        KnowledgeChunkSearchServiceImpl service = new KnowledgeChunkSearchServiceImpl(null, null, null, null);

        StringQuery query = service.buildSearchQuery(new KnowledgeChunkSearchService.SearchRequest(
                "导入失败",
                List.of(13L),
                88L,
                false,
                10));

        assertFalse(query.getSource().contains("chunkEmbeddingStatus"));
        assertFalse(query.getSource().contains("documentEmbeddingStatus"));
    }

    @Test
    void deleteQueryUsesTermFilterRequiredByCurrentSpringDataApi() {
        KnowledgeChunkSearchServiceImpl service = new KnowledgeChunkSearchServiceImpl(null, null, null, null);

        DeleteQuery query = service.termDeleteQuery("documentId", 42L);

        assertTrue(((StringQuery) query.getQuery()).getSource().contains("\"documentId\":42"));
    }

    @Test
    void indexProjectionUsesExplicitR2dbcColumnNames() throws NoSuchFieldException {
        Map<String, String> expectedColumns = Map.ofEntries(
                Map.entry("chunkId", "chunk_id"),
                Map.entry("documentId", "document_id"),
                Map.entry("knowledgeBaseId", "knowledge_base_id"),
                Map.entry("chunkIndex", "chunk_index"),
                Map.entry("documentTitle", "document_title"),
                Map.entry("knowledgeBaseName", "knowledge_base_name"),
                Map.entry("knowledgeBaseCreatorId", "knowledge_base_creator_id"),
                Map.entry("knowledgeBaseIsPublic", "knowledge_base_is_public"),
                Map.entry("documentUploaderId", "document_uploader_id"),
                Map.entry("knowledgeBaseStatus", "knowledge_base_status"),
                Map.entry("documentParseStatus", "document_parse_status"),
                Map.entry("documentEmbeddingStatus", "document_embedding_status"),
                Map.entry("chunkEmbeddingStatus", "chunk_embedding_status"),
                Map.entry("createdAt", "created_at"),
                Map.entry("updatedAt", "updated_at")
        );

        for (Map.Entry<String, String> entry : expectedColumns.entrySet()) {
            Field field = KnowledgeChunkIndexProjection.class.getDeclaredField(entry.getKey());
            Column column = field.getAnnotation(Column.class);
            assertEquals(entry.getValue(), column.value(), entry.getKey());
        }
    }
}
