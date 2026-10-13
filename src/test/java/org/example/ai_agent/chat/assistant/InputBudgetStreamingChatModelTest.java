package org.example.ai_agent.chat.assistant;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.example.ai_agent.chat.service.ChatStreamClientException;
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
                ToolExecutionResultMessage.from("1", "status", "x".repeat(220)),
                ToolExecutionResultMessage.from("2", "status", "x".repeat(220))).build();
        for (var result : input.messages().subList(1, input.messages().size())) {
            model.chat(ChatRequest.builder().messages(UserMessage.from("question"), result).build(), handler);
        }
        verify(delegate, times(2)).chat(any(ChatRequest.class), eq(handler));
        clearInvocations(delegate);
        model.chat(input, handler);
        verify(handler).onError(any(ChatStreamClientException.class));
        verifyNoInteractions(delegate);
    }

    @Test
    void rejectsOversizedToolArgumentsEvenWithoutAssistantText() {
        var delegate = mock(StreamingChatModel.class);
        var handler = mock(StreamingChatResponseHandler.class);
        var toolCall = dev.langchain4j.agent.tool.ToolExecutionRequest.builder()
                .id("1").name("lookup").arguments("x".repeat(600)).build();
        var input = ChatRequest.builder().messages(UserMessage.from("question"),
                dev.langchain4j.data.message.AiMessage.from(toolCall)).build();
        new InputBudgetStreamingChatModel(delegate, 512, null).chat(input, handler);
        verify(handler).onError(any(ChatStreamClientException.class));
        verifyNoInteractions(delegate);
    }

    @Test
    void exactBudgetPassesAndOneExtraByteFails() {
        var delegate = mock(StreamingChatModel.class);
        var handler = mock(StreamingChatResponseHandler.class);
        var model = new InputBudgetStreamingChatModel(delegate, 512, null);
        model.chat(ChatRequest.builder().messages(UserMessage.from("x".repeat(464))).build(), handler);
        verify(delegate).chat(any(ChatRequest.class), eq(handler));
        clearInvocations(delegate);
        model.chat(ChatRequest.builder().messages(UserMessage.from("x".repeat(465))).build(), handler);
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
