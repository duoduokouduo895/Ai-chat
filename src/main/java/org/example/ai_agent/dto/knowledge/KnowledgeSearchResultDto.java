package org.example.ai_agent.dto.knowledge;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库搜索结果DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
@Data
public class KnowledgeSearchResultDto {
    
    /**
     * 搜索结果列表
     */
    private List<KnowledgeSearchMatch> matches;
    
    /**
     * 总结果数
     */
    private Integer totalCount;
    
    /**
     * 搜索耗时(毫秒)
     */
    private Long searchTime;
    
    @Data
    public static class KnowledgeSearchMatch {
        
        /**
         * 文档块ID
         */
        private Long chunkId;
        
        /**
         * 文档ID
         */
        private Long documentId;
        
        /**
         * 文档标题
         */
        private String documentTitle;
        
        /**
         * 知识库ID
         */
        private Long knowledgeBaseId;
        
        /**
         * 知识库名称
         */
        private String knowledgeBaseName;
        
        /**
         * 匹配的文本内容
         */
        private String content;
        
        /**
         * 相似度分数(0-1)
         */
        private Double score;
        
        /**
         * 文档块在文档中的位置
         */
        private Integer chunkIndex;
        
        /**
         * 元数据
         */
        private Map<String, String> metadata;
        
        /**
         * 匹配时间
         */
        private LocalDateTime matchTime;
    }
}