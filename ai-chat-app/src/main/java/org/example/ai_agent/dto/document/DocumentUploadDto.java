package org.example.ai_agent.dto.document;

import lombok.Data;

/**
 * 文档上传请求DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class DocumentUploadDto {
    
    /**
     * 知识库ID
     */
    private Long knowledgeBaseId;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件类型
     */
    private String fileType;
    
    /**
     * 文件大小
     */
    private Long fileSize;
    
    /**
     * OSS路径
     */
    private String ossPath;
    
    /**
     * OSS访问URL
     */
    private String ossUrl;
}