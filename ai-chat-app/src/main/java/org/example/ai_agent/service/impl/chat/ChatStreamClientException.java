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

    public static ChatStreamClientException requestInProgress() {
        return new ChatStreamClientException("CHAT_REQUEST_IN_PROGRESS", "相同请求正在处理中，请等待当前回答完成。");
    }

    public static ChatStreamClientException previousRequestFailed(String errorCode) {
        return new ChatStreamClientException(errorCode == null || errorCode.isBlank()
                ? "CHAT_REQUEST_FAILED" : errorCode, "该请求已失败，请使用新的客户端消息 ID 重试。");
    }

    public String getErrorCode() {
        return errorCode;
    }
}
