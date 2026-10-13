package org.example.ai_agent.chat.service;

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

    public static ChatStreamClientException emptyResponse() {
        return new ChatStreamClientException("CHAT_EMPTY_RESPONSE", "模型未返回有效回答，请重试或切换模型。");
    }

    public static ChatStreamClientException requestInProgress() {
        return new ChatStreamClientException("CHAT_REQUEST_IN_PROGRESS", "相同请求正在处理中，请等待当前回答完成。");
    }

    public static ChatStreamClientException clientRequestConflict() {
        return new ChatStreamClientException(
                "CHAT_REQUEST_CONFLICT", "客户端消息 ID 已用于另一个会话，请生成新的客户端消息 ID。");
    }

    public static ChatStreamClientException previousRequestFailed(String errorCode) {
        return new ChatStreamClientException(errorCode == null || errorCode.isBlank()
                ? "CHAT_REQUEST_FAILED" : errorCode, "该请求已失败，请使用新的客户端消息 ID 重试。");
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static ChatStreamClientException conversationBusy() {
        return new ChatStreamClientException("CHAT_CONVERSATION_BUSY", "当前会话已有进行中的回答，请等待完成。");
    }

    public static ChatStreamClientException inputTooLarge() {
        return new ChatStreamClientException("CHAT_INPUT_TOO_LARGE", "本轮输入超过模型上下文预算，请缩短问题或减少参考资料。");
    }

    public static ChatStreamClientException retrievalUnavailable() {
        return new ChatStreamClientException("CHAT_RETRIEVAL_UNAVAILABLE", "知识库检索暂不可用，请稍后重试。");
    }
}
