package org.example.ai_agent.document.dto;

import lombok.Data;

import java.util.List;

/**
 * 文档批量上传响应DTO
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Data
public class DocumentBatchUploadDto {
    
    /**
     * 成功上传的文档
     */
    private List<DocumentDto> successDocuments;
    
    /**
     * 失败的文档及错误信息
     */
    private List<DocumentUploadError> failedDocuments;
    
    @Data
    public static class DocumentUploadError {
        private String fileName;
        private String errorMessage;
    }
    
    // 内部帮助类，用于处理成功和失败的结果
    public static class SuccessDocument {
        private final DocumentDto document;
        
        public SuccessDocument(DocumentDto document) {
            this.document = document;
        }
        
        public DocumentDto getDocument() {
            return document;
        }
    }
    
    public static class FailedDocument {
        private final String fileName;
        private final String errorMessage;
        
        public FailedDocument(String fileName, String errorMessage) {
            this.fileName = fileName;
            this.errorMessage = errorMessage;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}