package org.example.ai_agent.dto.knowledge;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
 * @since 2025
 */
@Data
public class KnowledgeSearchDto {
    
    /**
     * 查询文本
     */
    @NotBlank(message = "搜索内容不能为空")
    @Size(max = 2_000, message = "搜索内容长度不能超过2000个字符")
    private String query;
    
    /**
     * 知识库ID列表(可选，为空则搜索所有有权限的知识库)
     */
    @Size(max = 20, message = "知识库数量不能超过20个")
    private List<@Positive(message = "知识库ID必须为正数") Long> knowledgeBaseIds;
    
    /**
     * 最大返回结果数量
     */
    @NotNull(message = "最大返回数量不能为空")
    @Positive(message = "最大返回数量必须为正数")
    @Max(value = 20, message = "最大返回数量不能超过20")
    private Integer maxResults = 10;
    
    /**
     * 最小相似度分数(0-1)
     */
    @NotNull(message = "最小相似度不能为空")
    @DecimalMin(value = "0.0", message = "最小相似度不能小于0")
    @DecimalMax(value = "1.0", message = "最小相似度不能大于1")
    private Double minScore = 0.7;
    
    /**
     * 文档类型过滤
     */
    @Size(max = 10, message = "文件类型筛选数量不能超过10个")
    private List<@Size(max = 20, message = "文件类型长度不能超过20个字符") String> fileTypes;
    
    /**
     * 是否只搜索公开知识库
     */
    private Boolean publicOnly = false;
    
    /**
     * 额外的元数据过滤条件
     */
    @Size(max = 10, message = "元数据筛选条件不能超过10个")
    private Map<@Size(max = 50, message = "元数据字段名长度不能超过50个字符") String,
            @Size(max = 200, message = "元数据字段值长度不能超过200个字符") String> metadataFilter;
}
