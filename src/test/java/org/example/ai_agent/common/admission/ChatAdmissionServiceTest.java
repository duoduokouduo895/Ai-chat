package org.example.ai_agent.common.admission;

import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelType;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.auth.security.TokenRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAdmissionServiceTest {

    @Mock
    private RedisAdmissionStore redisAdmissionStore;

    private ChatAdmissionProperties properties;
    private ChatAdmissionService service;

    @BeforeEach
    void setUp() {
        properties = new ChatAdmissionProperties();
        properties.getGuest().setAllowedModels(List.of("guest-model"));
        service = new ChatAdmissionService(redisAdmissionStore, properties, new ModelCatalogProperties());
    }

    @Test
    void guestRequest_shouldConsumeQuotaAndReleaseBothLeases() {
        when(redisAdmissionStore.tryAcquireLease(anyString(), anyString(), anyInt(), any()))
                .thenReturn(Mono.just(true));
        when(redisAdmissionStore.tryConsume(anyString(), anyInt(), any())).thenReturn(Mono.just(true));
        when(redisAdmissionStore.releaseLease(anyString(), anyString())).thenReturn(Mono.empty());

        ChatRequest request = ChatRequest.builder().prompt("hello").model("guest-model").build();

        StepVerifier.create(service.admit(7L, true, request).flatMap(ChatAdmissionService.AdmissionLease::release))
                .verifyComplete();

        verify(redisAdmissionStore).tryConsume(TokenRedisKeys.chatGuestQuota(7L), 2,
                properties.getGuest().requestWindow());
        verify(redisAdmissionStore).releaseLease(eq(TokenRedisKeys.chatConcurrencyUser(7L)), anyString());
        verify(redisAdmissionStore).releaseLease(eq(TokenRedisKeys.chatConcurrencyGlobal()), anyString());
    }

    @Test
    void guestRequest_shouldRejectRagBeforeUsingRedis() {
        ChatRequest request = ChatRequest.builder()
                .prompt("hello")
                .model("guest-model")
                .knowledgeBaseIds(List.of(1L))
                .build();

        StepVerifier.create(service.admit(7L, true, request))
                .expectErrorMatches(error -> error instanceof ChatAdmissionException
                        && ((ChatAdmissionException) error).getStatusCode().value() == 403)
                .verify();

        verifyNoInteractions(redisAdmissionStore);
    }

    @Test
    void guestRequest_shouldReleaseLeasesWhenQuotaIsExhausted() {
        when(redisAdmissionStore.tryAcquireLease(anyString(), anyString(), anyInt(), any()))
                .thenReturn(Mono.just(true));
        when(redisAdmissionStore.tryConsume(anyString(), anyInt(), any())).thenReturn(Mono.just(false));
        when(redisAdmissionStore.releaseLease(anyString(), anyString())).thenReturn(Mono.empty());

        ChatRequest request = ChatRequest.builder().prompt("hello").model("guest-model").build();

        StepVerifier.create(service.admit(7L, true, request))
                .expectErrorMatches(error -> error instanceof ChatAdmissionException
                        && ((ChatAdmissionException) error).getStatusCode().value() == 429)
                .verify();

        verify(redisAdmissionStore).releaseLease(eq(TokenRedisKeys.chatConcurrencyUser(7L)), anyString());
        verify(redisAdmissionStore).releaseLease(eq(TokenRedisKeys.chatConcurrencyGlobal()), anyString());
    }

    @Test
    void guestRequest_shouldAllowAnyEnabledCatalogChatModelWhenAllowListIsEmpty() {
        properties.getGuest().setAllowedModels(List.of());
        ModelCatalogProperties catalog = catalogWithChatModels("chat-fast", "chat-reasoning", "chat-coding");
        service = new ChatAdmissionService(redisAdmissionStore, properties, catalog);
        when(redisAdmissionStore.tryAcquireLease(anyString(), anyString(), anyInt(), any()))
                .thenReturn(Mono.just(true));
        when(redisAdmissionStore.tryConsume(anyString(), anyInt(), any())).thenReturn(Mono.just(true));

        ChatRequest request = ChatRequest.builder().prompt("hello").model("chat-reasoning").build();

        StepVerifier.create(service.admit(7L, true, request)).expectNextCount(1).verifyComplete();
    }

    @Test
    void guestRequest_shouldRejectUnknownModelWhenAllowListIsEmpty() {
        properties.getGuest().setAllowedModels(List.of());
        service = new ChatAdmissionService(redisAdmissionStore, properties,
                catalogWithChatModels("chat-fast", "chat-reasoning", "chat-coding"));

        ChatRequest request = ChatRequest.builder().prompt("hello").model("unknown-model").build();

        StepVerifier.create(service.admit(7L, true, request))
                .expectErrorMatches(error -> error instanceof ChatAdmissionException
                        && ((ChatAdmissionException) error).getStatusCode().value() == 403)
                .verify();

        verifyNoInteractions(redisAdmissionStore);
    }

    private static ModelCatalogProperties catalogWithChatModels(String... aliases) {
        ModelCatalogProperties catalog = new ModelCatalogProperties();
        Map<String, ModelConfig> models = new LinkedHashMap<>();
        for (String alias : aliases) {
            ModelConfig config = new ModelConfig();
            config.setType(ModelType.CHAT);
            config.setEnabled(true);
            config.setProviderModelName("provider/" + alias);
            models.put(alias, config);
        }
        catalog.setModels(models);
        catalog.setDefaultChatModel(aliases[0]);
        return catalog;
    }
}
