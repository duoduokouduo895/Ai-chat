package org.example.ai_agent.knowledge.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * 知识库文档块全文检索索引。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "#{@environment.getProperty('knowledge-base.search.index-name', 'knowledge_base_chunks')}")
public class KnowledgeChunkDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long chunkId;

    @Field(type = FieldType.Long)
    private Long documentId;

    @Field(type = FieldType.Long)
    private Long versionId;

    @Field(type = FieldType.Long)
    private Long generation;

    @Field(type = FieldType.Long)
    private Long knowledgeBaseId;

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private String documentTitle;

    @Field(type = FieldType.Keyword)
    private String knowledgeBaseName;

    @Field(type = FieldType.Long)
    private Long knowledgeBaseCreatorId;

    @Field(type = FieldType.Boolean)
    private Boolean knowledgeBaseIsPublic;

    @Field(type = FieldType.Long)
    private Long documentUploaderId;

    @Field(type = FieldType.Keyword)
    private String knowledgeBaseStatus;

    @Field(type = FieldType.Keyword)
    private String documentParseStatus;

    @Field(type = FieldType.Keyword)
    private String documentEmbeddingStatus;

    @Field(type = FieldType.Keyword)
    private String chunkEmbeddingStatus;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = {}, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}
