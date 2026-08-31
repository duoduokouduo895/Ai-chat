package org.example.ai_agent.service.impl.document;

import java.time.Duration;

/** 单次文档解析超出资源预算时抛出，以便进入受控重试和最终失败流程。 */
public class DocumentParsingTimeoutException extends RuntimeException {

    public DocumentParsingTimeoutException(Long documentId, Duration timeout, Throwable cause) {
        super("文档解析超时: documentId=" + documentId + ", timeout=" + timeout, cause);
    }
}
