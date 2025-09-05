package org.example.ai_agent.dto.knowledge;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 知识库搜索请求DTO
 * <p>
 * 基于LangChain4j的EmbeddingSearchRequest设计
 * </p>
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
public class KnowledgeSearchDto {
    
    /**
     * 查询文本
     */
    private String query;
    
    /**
     * 知识库ID列表(可选，为空则搜索所有有权限的知识库)
     */
    private List<Long> knowledgeBaseIds;
    
    /**
     * 最大返回结果数量
     */
    private Integer maxResults = 10;
    
    /**
     * 最小相似度分数(0-1)
     */
    private Double minScore = 0.7;
    
    /**
     * 文档类型过滤
     */
    private List<String> fileTypes;
    
    /**
     * 是否只搜索公开知识库
     */
    private Boolean publicOnly = false;
    
    /**
     * 额外的元数据过滤条件
     */
    private Map<String, String> metadataFilter;
}