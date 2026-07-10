package org.example.ai_agent.config.ai;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryingStreamingChatModelTest {

    @Test
    void shouldRetryNetworkErrorBeforeStreamingTokens() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                if (calls.incrementAndGet() == 1) {
                    handler.onError(new RuntimeException(new UnknownHostException("api.siliconflow.cn")));
                    return;
                }
                handler.onPartialResponse("好");
                handler.onCompleteResponse((ChatResponse) null);
            }
        };
        RetryingStreamingChatModel model = new RetryingStreamingChatModel(delegate, "chat-fast", 2, 1, Runnable::run);
        List<String> chunks = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);

        ChatRequest request = ChatRequest.builder().messages(List.of(UserMessage.from("你好"))).build();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                chunks.add(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                completed.countDown();
            }

            @Override
            public void onError(Throwable error) {
                completed.countDown();
            }
        });

        assertTrue(completed.await(1, TimeUnit.SECONDS));
        assertEquals(2, calls.get());
        assertEquals(List.of("好"), chunks);
    }
}
