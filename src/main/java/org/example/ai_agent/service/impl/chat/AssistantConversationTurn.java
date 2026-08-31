package org.example.ai_agent.service.impl.chat;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.service.ConversationService;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class AssistantConversationTurn {

    private final ConversationService conversationService;

    public AssistantConversationTurn(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    public Mono<ChatRequest> ensureConversationExists(ChatRequest request) {
        if (request.getConversationId() != null) {
            return conversationService.getConversationById(request.getConversationId(), request.getUserId())
                    .thenReturn(request);
        }

        log.info("会话ID为空，创建新会话");
        Conversation newConversation = new Conversation();
        newConversation.setUserId(request.getUserId());
        newConversation.setTitle("New Conversation");

        return conversationService.createConversation(newConversation)
                .doOnSuccess(savedConv -> {
                    log.info("新会话创建成功，ID: {}", savedConv.getId());
                    request.setConversationId(savedConv.getId());
                })
                .thenReturn(request);
    }

    public Mono<Message> saveUserMessage(ChatRequest request) {
        Message userMessage = new Message();
        userMessage.setConversationId(request.getConversationId());
        userMessage.setSender("user");
        userMessage.setContent(request.getTrimmedMessage());
        userMessage.setModel(request.getEffectiveModelId());
        userMessage.setDeduplicationKey(messageDeduplicationKey("user", request));

        return conversationService.addMessageToConversation(userMessage)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Message> saveAiMessage(ChatRequest request, String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("跳过保存空的AI消息");
            return Mono.empty();
        }

        Message aiMessage = new Message();
        aiMessage.setConversationId(request.getConversationId());
        aiMessage.setSender("ai");
        aiMessage.setContent(content.trim());
        aiMessage.setModel(request.getEffectiveModelId());
        aiMessage.setDeduplicationKey(messageDeduplicationKey("ai", request));

        return conversationService.addMessageToConversation(aiMessage)
                .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .filter(this::isTransientPersistenceFailure)
                        .doBeforeRetry(retrySignal -> log.warn(
                                "AI消息持久化遇到临时异常，准备重试: retry={}, errorType={}",
                                retrySignal.totalRetries() + 1,
                                retrySignal.failure().getClass().getSimpleName())))
                .doOnSuccess(savedMessage -> log.info("AI消息已成功保存，ID: {}, 内容长度: {}",
                        savedMessage.getId(), aiMessage.getContent().length()))
                .doOnError(e -> log.error("保存AI消息失败: contentLength={}, errorType={}",
                        aiMessage.getContent().length(), e.getClass().getSimpleName()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isTransientPersistenceFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current instanceof io.r2dbc.spi.R2dbcTransientException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String messageDeduplicationKey(String sender, ChatRequest request) {
        String requestId = StringUtils.hasText(request.getClientMessageId())
                ? request.getClientMessageId().trim()
                : UUID.randomUUID().toString();
        return sender + ":" + request.getUserId() + ":" + request.getConversationId() + ":" + requestId;
    }
}
