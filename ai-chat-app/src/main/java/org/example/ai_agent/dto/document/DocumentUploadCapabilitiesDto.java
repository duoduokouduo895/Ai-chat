package org.example.ai_agent.dto.document;

import java.util.List;

/** 服务端当前生效的上传体验限制；文件实际校验仍由服务端执行。 */
public record DocumentUploadCapabilitiesDto(
        long maxFileBytes,
        long maxRequestBytes,
        int maxBatchFiles,
        List<String> supportedExtensions) {
}
