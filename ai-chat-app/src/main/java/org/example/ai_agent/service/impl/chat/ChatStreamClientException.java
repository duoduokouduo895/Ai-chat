package org.example.ai_agent.service.impl.chat;

/** 仅承载可以安全返回给 SSE 客户端的、已分类的聊天失败原因。 */
public final class ChatStreamClientException extends RuntimeException {

    private final String errorCode;

    private ChatStreamClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public static ChatStreamClientException modelTimeout() {
        return new ChatStreamClientException("CHAT_TIMEOUT", "模型响应超时，请稍后重试或切换模型。");
    }

    public String getErrorCode() {
        return errorCode;
    }
}
