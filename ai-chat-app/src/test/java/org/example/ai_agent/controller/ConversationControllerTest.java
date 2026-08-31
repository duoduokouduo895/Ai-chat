package org.example.ai_agent.controller;

import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.dto.chat.MessageDto;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.controller.support.RequestActorResolver;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.impl.identity.RepositoryActorIdentityLookup;
import org.example.ai_agent.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private UserRepository userRepository;

    @Test
    void recentConversationsUsesAuthenticatedUserIdWithoutLoadingUserRecord() {
        ConversationWithPreview preview = new ConversationWithPreview();
        preview.setId(10L);
        preview.setUserId(42L);
        when(conversationService.getRecentConversationsWithPreview(42L))
                .thenReturn(Flux.just(preview));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("alice", "token", List.of());
        authentication.setDetails(42L);

        ConversationController controller = new ConversationController(conversationService,
                new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));

        StepVerifier.create(controller.getRecentConversations(authentication))
                .expectNext(preview)
                .verifyComplete();

        verify(conversationService).getRecentConversationsWithPreview(42L);
        verifyNoInteractions(userRepository);
    }

    @Test
    void addMessageAlwaysUsesAuthenticatedSenderAndScopedClientMessageId() {
        Conversation conversation = new Conversation();
        conversation.setId(10L);
        MessageDto dto = new MessageDto();
        dto.setSender("ai");
        dto.setContent("伪造内容");
        dto.setClientMessageId("request-1");

        when(conversationService.getConversationById(10L, 42L)).thenReturn(Mono.just(conversation));
        when(conversationService.addMessageToConversation(org.mockito.ArgumentMatchers.any(Message.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("alice", "token", List.of());
        authentication.setDetails(42L);

        ConversationController controller = new ConversationController(conversationService,
                new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));

        StepVerifier.create(controller.addMessage(10L, dto, authentication))
                .assertNext(saved -> {
                    org.junit.jupiter.api.Assertions.assertEquals("user", saved.getSender());
                    org.junit.jupiter.api.Assertions.assertEquals("user:42:request-1", saved.getDeduplicationKey());
                })
                .verifyComplete();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(conversationService).addMessageToConversation(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("user", captor.getValue().getSender());
    }
}
