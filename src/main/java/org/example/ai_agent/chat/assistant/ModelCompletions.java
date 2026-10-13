package org.example.ai_agent.chat.assistant;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Single-shot completion over a streaming model, budget-checked and cancellable. */
public final class ModelCompletions {
    private ModelCompletions() {
    }

    public static Mono<ChatResponse> complete(StreamingChatModel model, ChatRequest input,
                                              Integer inputBudget, String estimatorModel) {
        var guarded = new InputBudgetStreamingChatModel(model, inputBudget, estimatorModel);
        return Mono.create(sink -> {
            var cancelled = new AtomicBoolean();
            var handle = new AtomicReference<StreamingHandle>();
            sink.onCancel(() -> {
                cancelled.set(true);
                if (handle.get() != null) handle.get().cancel();
            });
            if (cancelled.get()) return;
            guarded.chat(input, new StreamingChatResponseHandler() {
                private void track(StreamingHandle streamingHandle) {
                    handle.set(streamingHandle);
                    if (cancelled.get()) streamingHandle.cancel();
                }

                @Override
                public void onPartialResponse(PartialResponse response, PartialResponseContext context) {
                    track(context.streamingHandle());
                }

                @Override
                public void onPartialThinking(PartialThinking thinking, PartialThinkingContext context) {
                    track(context.streamingHandle());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    sink.success(response);
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        });
    }
}
