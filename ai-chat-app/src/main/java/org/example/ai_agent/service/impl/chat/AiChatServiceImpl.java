package org.example.ai_agent.service.impl.chat;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.observability.ChatStreamTelemetry;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.GeneralAssistantFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final GeneralAssistantFactory generalAssistantFactory;
    private final AssistantConversationTurn conversationTurn;
    private final ChatTurnLifecycle chatTurnLifecycle;
    private final AssistantInputBuilder inputBuilder;
    private final ChatStreamEventFactory eventFactory;
    private final ChatStreamTelemetry telemetry;
    private final long streamTimeoutMs;

    public AiChatServiceImpl(GeneralAssistantFactory generalAssistantFactory,
                             AssistantConversationTurn conversationTurn,
                             ChatTurnLifecycle chatTurnLifecycle,
                             AssistantInputBuilder inputBuilder,
                             ChatStreamEventFactory eventFactory,
                             ChatStreamTelemetry telemetry,
                             @Value("${assistant.stream.timeout-ms:60000}") long streamTimeoutMs) {
        this.generalAssistantFactory = generalAssistantFactory;
        this.conversationTurn = conversationTurn;
        this.chatTurnLifecycle = chatTurnLifecycle;
        this.inputBuilder = inputBuilder;
        this.eventFactory = eventFactory;
        this.telemetry = telemetry;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    @Override
    public Flux<ServerSentEvent<String>> chat(ChatRequest chatRequest) {
        log.info("收到统一 Assistant 对话请求: model={}, ragMode={}",
                chatRequest.getModel(), chatRequest.isRagMode());

        Flux<ServerSentEvent<String>> stream = Mono.just(chatRequest)
                .flatMap(conversationTurn::ensureConversationExists)
                .flatMap(chatTurnLifecycle::claim)
                .flatMapMany(claim -> handleClaim(chatRequest, claim));
        return telemetry.observe(stream, chatRequest.isRagMode())
                .onErrorResume(this::handleChatError);
    }

    private Flux<ServerSentEvent<String>> handleClaim(ChatRequest request, ChatTurnLifecycle.Claim claim) {
        return switch (claim.kind()) {
            case OWNED -> chatTurnLifecycle.markStreaming(claim)
                    .flatMapMany(streamingClaim -> handleAssistantChat(request, streamingClaim)
                            .doOnCancel(() -> markCancelledAsync(streamingClaim)));
            case REPLAY -> Flux.concat(
                    Flux.just(eventFactory.metadata(request)),
                    Flux.just(eventFactory.status("正在重放已完成回答")),
                    Flux.just(eventFactory.message(claim.assistantMessage().getContent())));
            case IN_PROGRESS -> Flux.error(ChatStreamClientException.requestInProgress());
            case FAILED -> Flux.error(ChatStreamClientException.previousRequestFailed(claim.row().getErrorCode()));
        };
    }

    /**
     * 统一处理 Assistant 对话。
     * 是否启用知识增强，只影响发送给模型的输入内容，不再影响主链结构。
     */
    private Flux<ServerSentEvent<String>> handleAssistantChat(ChatRequest request, ChatTurnLifecycle.Claim claim) {
        Flux<ServerSentEvent<String>> metadataFlux = Flux.just(eventFactory.metadata(request));
        Flux<ServerSentEvent<String>> statusFlux = Flux.just(eventFactory.status(
                request.isRagMode() ? "正在检索知识库" : "正在连接模型"));

        Flux<ServerSentEvent<String>> messageFlux = performAssistantChat(request, claim);
        return Flux.concat(metadataFlux, statusFlux, messageFlux);
    }

    /**
     * 统一的错误处理
     */
    private Flux<ServerSentEvent<String>> handleChatError(Throwable ex) {
        log.error("聊天处理失败: errorType={}", ex.getClass().getSimpleName());
        return Flux.just(eventFactory.error(ex));
    }

    /**
     * 执行统一 Assistant 聊天。
     */
    private Flux<ServerSentEvent<String>> performAssistantChat(ChatRequest chatRequest, ChatTurnLifecycle.Claim claim) {
        Mono<String> assistantInput = inputBuilder.build(chatRequest);

        return assistantInput
                .flatMapMany(input -> Flux.concat(
                        Flux.just(eventFactory.status("正在生成回答")),
                        streamAssistantResponse(chatRequest, claim, input)));
    }

    private Flux<ServerSentEvent<String>> streamAssistantResponse(ChatRequest chatRequest,
                                                                   ChatTurnLifecycle.Claim claim,
                                                                   String assistantInput) {
        return Flux.defer(() -> {
            GeneralAssistant assistant = generalAssistantFactory.createAssistant(chatRequest);
            String effectiveModelId = chatRequest.getEffectiveModelId();
            String memoryId = chatRequest.getConversationId() + ":" + effectiveModelId;
            StringBuilder responseBuilder = new StringBuilder();
            long streamStartNanos = System.nanoTime();
            AtomicInteger chunkCount = new AtomicInteger();
            AtomicInteger nonEmptyChunkCount = new AtomicInteger();
            AtomicBoolean firstNonEmptyChunkLogged = new AtomicBoolean(false);

            log.info("Assistant调用模型: conversationId={}, modelId={}, memoryId={}",
                    chatRequest.getConversationId(), effectiveModelId, memoryId);

            return assistant.chat(memoryId, assistantInput)
                    .doOnSubscribe(subscription -> log.info(
                            "Assistant流开始: conversationId={}, modelId={}",
                            chatRequest.getConversationId(), effectiveModelId))
                    .doOnNext(chunk -> {
                        chunkCount.incrementAndGet();
                        if (chunk != null && !chunk.isBlank()) {
                            nonEmptyChunkCount.incrementAndGet();
                            if (firstNonEmptyChunkLogged.compareAndSet(false, true)) {
                                log.info("Assistant首个非空chunk: conversationId={}, modelId={}, durationMs={}",
                                        chatRequest.getConversationId(), effectiveModelId, elapsedMs(streamStartNanos));
                            }
                        }
                    })
                    .doOnNext(responseBuilder::append)
                    .map(eventFactory::message)
                    .concatWith(Mono.defer(() -> {
                        if (responseBuilder.length() > 0) {
                            return conversationTurn.saveAiMessage(chatRequest, responseBuilder.toString())
                                    .doOnNext(savedAiMessage -> log.info("Assistant AI消息已保存，ID: {}", savedAiMessage.getId()))
                                    .flatMap(savedAiMessage -> chatTurnLifecycle.markCompleted(claim, savedAiMessage))
                                    .then(Mono.<ServerSentEvent<String>>empty());
                        }
                        return chatTurnLifecycle.markEmptyResponse(claim)
                                .then(Mono.error(ChatStreamClientException.emptyResponse()));
                    }))
                    .doOnComplete(() -> {
                        log.info("Assistant对话完成: conversationId={}, ragMode={}, durationMs={}, chunks={}, nonEmptyChunks={}",
                                chatRequest.getConversationId(), chatRequest.isRagMode(),
                                elapsedMs(streamStartNanos), chunkCount.get(), nonEmptyChunkCount.get());
                    })
                    .doOnError(error -> {
                        log.warn("Assistant流异常: conversationId={}, modelId={}, durationMs={}, chunks={}, nonEmptyChunks={}, error={}",
                                chatRequest.getConversationId(), effectiveModelId,
                                elapsedMs(streamStartNanos), chunkCount.get(), nonEmptyChunkCount.get(), error.toString());
                    });
        })
                .subscribeOn(Schedulers.boundedElastic()) // 隔离：防止 LangChain4j 同步读取 Redis ChatMemory 或同步构造流时阻塞 Netty 事件循环线程
                .timeout(
                        Duration.ofMillis(Math.max(1L, streamTimeoutMs)),
                        Flux.error(ChatStreamClientException.modelTimeout()))
                .onErrorResume(error -> chatTurnLifecycle.markFailed(claim, error)
                        .then(Mono.error(error)));
    }

    private void markCancelledAsync(ChatTurnLifecycle.Claim claim) {
        Mono<Void> cancellation = chatTurnLifecycle.markCancelled(claim);
        if (cancellation != null) {
            cancellation.subscribe();
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
