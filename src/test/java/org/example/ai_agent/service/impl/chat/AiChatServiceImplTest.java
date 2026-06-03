package org.example.ai_agent.service.impl.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.GeneralAssistantFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("high-risk")
class AiChatServiceImplTest {

    @Mock
    private GeneralAssistantFactory generalAssistantFactory;
    @Mock
    private ConversationService conversationService;
    @Mock
    private RagService ragService;
    @Mock
    private GeneralAssistant generalAssistant;

    private AiChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiChatServiceImpl(generalAssistantFactory, conversationService, ragService, new ObjectMapper());
    }

    @Test
    void chat_shouldStreamChineseChunksAndPersistAiMessage() {
        ChatRequest request = ChatRequest.builder()
                .userId(42L)
                .conversationId(99L)
                .prompt("早上好")
                .model("test-model")
                .clientMessageId("request-1")
                .build();

        Conversation conversation = new Conversation();
        conversation.setId(99L);

        Message savedUserMessage = new Message();
        savedUserMessage.setId(1L);
        savedUserMessage.setSender("user");

        when(conversationService.getConversationById(99L, 42L)).thenReturn(Mono.just(conversation));
        when(conversationService.addMessageToConversation(argThat(message ->
                message != null && "user".equals(message.getSender()))))
                .thenReturn(Mono.just(savedUserMessage));
        when(conversationService.addMessageToConversation(argThat(message ->
                message != null && "ai".equals(message.getSender()))))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(generalAssistantFactory.createAssistant(any(ChatRequest.class))).thenReturn(generalAssistant);
        when(generalAssistant.chat("99:test-model", "早上好")).thenReturn(Flux.just("早上", "好"));

        StepVerifier.create(service.chat(request).collectList())
                .assertNext(events -> {
                    org.junit.jupiter.api.Assertions.assertEquals(3, events.size());
                    org.junit.jupiter.api.Assertions.assertEquals("metadata", events.get(0).event());
                    org.junit.jupiter.api.Assertions.assertEquals("message", events.get(1).event());
                    org.junit.jupiter.api.Assertions.assertEquals("早上", events.get(1).data());
                    org.junit.jupiter.api.Assertions.assertEquals("好", events.get(2).data());
                })
                .verifyComplete();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService, timeout(1000).times(2)).addMessageToConversation(captor.capture());
        List<Message> savedMessages = captor.getAllValues();
        org.junit.jupiter.api.Assertions.assertTrue(savedMessages.stream().anyMatch(message ->
                "ai".equals(message.getSender())
                        && "早上好".equals(message.getContent())
                        && "ai:42:99:request-1".equals(message.getDeduplicationKey())));
        org.junit.jupiter.api.Assertions.assertTrue(savedMessages.stream().anyMatch(message ->
                "user".equals(message.getSender())
                        && "user:42:99:request-1".equals(message.getDeduplicationKey())));
    }
}
