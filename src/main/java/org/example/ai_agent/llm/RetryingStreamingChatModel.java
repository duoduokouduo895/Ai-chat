package org.example.ai_agent.llm;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RetryingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final String alias;
    private final int maxAttempts;
    private final long backoffMs;
    private final Executor executor;

    public RetryingStreamingChatModel(StreamingChatModel delegate,
                                      String alias,
                                      int maxAttempts,
                                      long backoffMs,
                                      Executor executor) {
        this.delegate = delegate;
        this.alias = alias;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = Math.max(0, backoffMs);
        this.executor = executor;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        chat(chatRequest, handler, 1);
    }

    private void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler, int attempt) {
        AtomicBoolean streamed = new AtomicBoolean(false);
        delegate.chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                streamed.set(true);
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialResponse(PartialResponse response, PartialResponseContext context) {
                streamed.set(true);
                handler.onPartialResponse(response, context);
            }

            @Override
            public void onPartialThinking(PartialThinking thinking, PartialThinkingContext context) {
                streamed.set(true);
                handler.onPartialThinking(thinking, context);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                if (!streamed.get() && attempt < maxAttempts && isRetryableIoError(error)) {
                    log.warn("AI模型调用发生可重试网络异常: alias={}, attempt={}/{}, error={}",
                            alias, attempt, maxAttempts, rootMessage(error));
                    CompletableFuture
                            .runAsync(() -> chat(chatRequest, handler, attempt + 1),
                                    CompletableFuture.delayedExecutor(backoffMs, TimeUnit.MILLISECONDS, executor))
                            .exceptionally(retryError -> {
                                handler.onError(retryError);
                                return null;
                            });
                    return;
                }
                handler.onError(error);
            }
        });
    }

    private boolean isRetryableIoError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof SocketException
                    || current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }

        String message = rootMessage(error).toLowerCase();
        return message.contains("i/o error")
                || message.contains("unknownhost")
                || message.contains("不知道这样的主机")
                || message.contains("connection reset")
                || message.contains("connect timed out")
                || message.contains("read timed out");
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        Throwable root = error;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root != null && root.getMessage() != null ? root.getMessage() : String.valueOf(error);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
