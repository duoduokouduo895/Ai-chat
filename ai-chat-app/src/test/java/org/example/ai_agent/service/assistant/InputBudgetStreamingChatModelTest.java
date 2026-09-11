package org.example.ai_agent.service.assistant;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.service.impl.chat.ChatStreamClientException;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InputBudgetStreamingChatModelTest {
    @Test
    void accumulatedToolResultsCannotExceedBudgetEvenWhenEachResultFits() {
        var delegate = mock(StreamingChatModel.class);
        var handler = mock(StreamingChatResponseHandler.class);
        var model = new InputBudgetStreamingChatModel(delegate, 512, null);
        var input = ChatRequest.builder().messages(UserMessage.from("question"),
                ToolExecutionResultMessage.from("1", "status", "x".repeat(200)),
                ToolExecutionResultMessage.from("2", "status", "x".repeat(200))).build();
        model.chat(input, handler);
        verify(handler).onError(any(ChatStreamClientException.class));
        verifyNoInteractions(delegate);
    }

    @Test
    void withinBudgetRequestIsPassedUnchangedToProvider() {
        var delegate = mock(StreamingChatModel.class);
        var handler = mock(StreamingChatResponseHandler.class);
        var input = ChatRequest.builder().messages(UserMessage.from("question")).build();
        new InputBudgetStreamingChatModel(delegate, 512, null).chat(input, handler);
        verify(delegate).chat(input, handler);
        verifyNoInteractions(handler);
    }

    @Test
    void oversizedQuestionIsRejectedWithoutTruncatingOrCallingProvider() {
        var delegate = mock(StreamingChatModel.class);
        var handler = mock(StreamingChatResponseHandler.class);
        var input = ChatRequest.builder().messages(UserMessage.from("问题".repeat(1000))).build();
        new InputBudgetStreamingChatModel(delegate, 512, null).chat(input, handler);
        verify(handler).onError(any(ChatStreamClientException.class));
        verifyNoInteractions(delegate);
    }
}
