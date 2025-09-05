package org.example.ai_agent.service.impl.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.dto.chat.RagChatDto;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.exception.GuestLimitExceededException;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.provider.ChatMemoryProvider;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.alibaba.fastjson.JSON;
import java.util.Map;



@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final AiModelRegistry aiModelRegistry;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ConversationService conversationService;
    private final RagService ragService;

    public AiChatServiceImpl(AiModelRegistry aiModelRegistry,
                             ChatMemoryProvider chatMemoryProvider,
                             ConversationService conversationService,
                             RagService ragService) {
        this.aiModelRegistry = aiModelRegistry;
        this.chatMemoryProvider = chatMemoryProvider;
        this.conversationService = conversationService;
        this.ragService = ragService;
    }

    @Override
    public Flux<String> chat(ChatRequest chatRequest) {
        // 智能路由：根据是否提供知识库ID来选择聊天模式
        if (chatRequest.isRagMode()) {
            log.info("检测到知识库ID，使用RAG模式进行对话: knowledgeBaseIds={}, userId={}", 
                    chatRequest.getKnowledgeBaseIds(), chatRequest.getUserId());
            return handleRagChat(chatRequest);
        } else {
            log.info("未提供知识库ID，使用普通AI对话模式: userId={}", chatRequest.getUserId());
            return handleNormalChat(chatRequest);
        }
    }

    /**
     * 处理RAG模式对话
     */
    private Flux<String> handleRagChat(ChatRequest chatRequest) {
        return Mono.just(chatRequest)
                .flatMap(this::ensureConversationExists)
                .flatMapMany(request -> {
                    // 1. 创建包含conversationId的元数据
                    Map<String, Object> metadata = Map.of(
                            "type", "metadata",
                            "conversationId", request.getConversationId(),
                            "mode", "rag"
                    );
                    String metadataJson = JSON.toJSONString(metadata);
                    Flux<String> metadataFlux = Flux.just("[METADATA]" + metadataJson);

                    // 2. 使用RAG服务进行对话
                    RagChatDto ragDto = request.toRagChatDto();
                    Flux<String> ragFlux = ragService.ragChatStream(ragDto)
                            .doOnNext(response -> {
                                // 在RAG模式下也可以保存聊天记录
                                if (request.getSaveMessage() != null && request.getSaveMessage()) {
                                    saveUserMessageAsync(request);
                                }
                            })
                            .doOnComplete(() -> {
                                log.info("RAG对话完成: conversationId={}", request.getConversationId());
                            });

                    return Flux.concat(metadataFlux, ragFlux);
                })
                .onErrorResume(this::handleChatError);
    }

    /**
     * 处理普通对话模式
     */
    private Flux<String> handleNormalChat(ChatRequest chatRequest) {
        return Mono.just(chatRequest)
                .flatMap(this::ensureConversationExists)
                .flatMapMany(request -> {
                    // 1. 创建包含conversationId的元数据
                    Map<String, Object> metadata = Map.of(
                            "type", "metadata",
                            "conversationId", request.getConversationId(),
                            "mode", "normal"
                    );
                    String metadataJson = JSON.toJSONString(metadata);
                    Flux<String> metadataFlux = Flux.just("[METADATA]" + metadataJson);

                    // 2. 获取真实的AI聊天流
                    Flux<String> conversationFlux = performNormalChat(request);

                    return Flux.concat(metadataFlux, conversationFlux);
                })
                .onErrorResume(this::handleChatError);
    }

    /**
     * 统一的错误处理
     */
    private Flux<String> handleChatError(Throwable ex) {
        if (ex instanceof GuestLimitExceededException) {
            log.warn("游客消息超限，将错误信息发送至前端: {}", ex.getMessage());
            Map<String, Object> errorPayload = Map.of(
                    "type", "error",
                    "code", "GUEST_LIMIT_EXCEEDED",
                    "message", ex.getMessage()
            );
            String errorJson = JSON.toJSONString(errorPayload);
            return Flux.just("[ERROR]" + errorJson);
        } else {
            log.error("聊天处理失败", ex);
            Map<String, Object> errorPayload = Map.of(
                    "type", "error",
                    "code", "CHAT_ERROR",
                    "message", "聊天处理失败：" + ex.getMessage()
            );
            String errorJson = JSON.toJSONString(errorPayload);
            return Flux.just("[ERROR]" + errorJson);
        }
    }
    /**
     * 确保对话存在
     */
    private Mono<ChatRequest> ensureConversationExists(ChatRequest request) {
        if (request.getConversationId() != null) {
            return Mono.just(request);
        }

        log.info("会话ID为空，为用户 {} 创建新会话。", request.getUserId());
        Conversation newConversation = new Conversation();
        newConversation.setUserId(request.getUserId());
        newConversation.setTitle("New Conversation"); // 修正：将标题固定为 'New Conversation'

        return conversationService.createConversation(newConversation)
                .doOnSuccess(savedConv -> {
                    log.info("新会话创建成功，ID: {}", savedConv.getId());
                    request.setConversationId(savedConv.getId());
                })
                .thenReturn(request);
    }
    /**
     * 执行普通聊天
     */
    private Flux<String> performNormalChat(ChatRequest chatRequest) {
        // 1. 创建并保存用户消息，这将触发游客限制检查
        Message userMessage = new Message();
        userMessage.setConversationId(chatRequest.getConversationId());
        userMessage.setSender("user");
        userMessage.setContent(chatRequest.getPrompt());
        userMessage.setModel(chatRequest.getModel());

        return conversationService.addMessageToConversation(userMessage)
                .flatMapMany(savedUserMessage -> {
                    // 2. 用户消息保存成功后，继续执行AI聊天逻辑
                    try {
                        StreamingChatModel model = aiModelRegistry.getModel(chatRequest.getModel());
                        ChatMemory chatMemory = chatMemoryProvider.get(String.valueOf(chatRequest.getConversationId()));
                        chatMemory.add(UserMessage.from(chatRequest.getPrompt()));

                        return Flux.create(sink -> {
                            model.chat(chatMemory.messages(), new StreamingChatResponseHandler() {
                                private final StringBuilder responseBuilder = new StringBuilder();
                                
                                @Override
                                public void onPartialResponse(String partialResponse) {
                                    responseBuilder.append(partialResponse);
                                    sink.next(partialResponse);
                                }

                                @Override
                                public void onCompleteResponse(ChatResponse completeResponse) {
                                    log.info("AI响应流完成。 Token usage: {}", completeResponse.metadata().tokenUsage());
                                    AiMessage aiMessage = completeResponse.aiMessage();
                                    chatMemory.add(aiMessage);
                                    
                                    // 异步保存AI消息
                                    saveAiMessage(chatRequest, responseBuilder.toString())
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .subscribe();
                                    sink.complete();
                                }

                                @Override
                                public void onError(Throwable error) {
                                    log.error("AI流式响应出错", error);
                                    sink.error(error);
                                }
                            });
                        });
                    } catch (Exception e) {
                        log.error("聊天服务执行失败", e);
                        return Flux.error(e);
                    }
                });
    }

    /**
     * 异步保存用户消息（用于RAG模式）
     */
    private void saveUserMessageAsync(ChatRequest chatRequest) {
        Message userMessage = new Message();
        userMessage.setConversationId(chatRequest.getConversationId());
        userMessage.setSender("user");
        userMessage.setContent(chatRequest.getPrompt());
        userMessage.setModel(chatRequest.getModel());
        
        conversationService.addMessageToConversation(userMessage)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    savedMessage -> log.debug("用户消息已保存，ID: {}", savedMessage.getId()),
                    error -> log.error("保存用户消息失败", error)
                );
    }

    private Mono<Message> saveAiMessage(ChatRequest chatRequest, String content) {
        Message aiMessage = new Message();
        aiMessage.setConversationId(chatRequest.getConversationId());
        aiMessage.setSender("ai");
        aiMessage.setContent(content);
        aiMessage.setModel(chatRequest.getModel());
        // 使用ConversationService来保存AI消息，以确保更新对话时间等逻辑被执行
        return conversationService.addMessageToConversation(aiMessage)
                .doOnSuccess(savedMessage -> log.info("AI消息已保存，ID: {}", savedMessage.getId()))
                .doOnError(e -> log.error("保存AI消息失败", e));
    }
}
