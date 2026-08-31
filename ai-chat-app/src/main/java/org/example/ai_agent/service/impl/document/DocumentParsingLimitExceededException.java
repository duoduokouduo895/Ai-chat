package org.example.ai_agent.service.impl.document;

/** 解析产物超过系统允许文本上限时抛出，重试不会改变结果。 */
public class DocumentParsingLimitExceededException extends RuntimeException {

    public DocumentParsingLimitExceededException(int maximumCharacters) {
        super("解析文本超过最大长度限制: " + maximumCharacters);
    }
}
