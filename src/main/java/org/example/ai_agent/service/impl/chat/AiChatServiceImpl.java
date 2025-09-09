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
            log.info("🔄 [路由决策] 检测到知识库ID，使用RAG模式进行对话: knowledgeBaseIds={}, userId={}",
                    chatRequest.getKnowledgeBaseIds(), chatRequest.getUserId());
            return handleRagChat(chatRequest);
        } else {
            log.info("🔄 [路由决策] 未提供知识库ID，使用普通AI对话模式: userId={}", chatRequest.getUserId());
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
                    // 1. 先检查是否已经存在相同的用户消息（防止重复）
                    return checkAndSaveUserMessage(request)
                            .flatMapMany(savedUserMessage -> {
                                // 2. 创建包含conversationId的元数据
                                Map<String, Object> metadata = Map.of(
                                        "type", "metadata",
                                        "conversationId", request.getConversationId(),
                                        "mode", "rag"
                                );
                                String metadataJson = JSON.toJSONString(metadata);
                                Flux<String> metadataFlux = Flux.just("[METADATA]" + metadataJson);

                                // 3. 使用RAG服务进行对话并收集AI响应
                                RagChatDto ragDto = request.toRagChatDto();
                                log.info("🤖 [RAG模式] 转换为ChatRequest为RagChatDto: question={}, knowledgeBaseIds={}, minSimilarity={}",
                                        ragDto.getQuestion(), ragDto.getKnowledgeBaseIds(), ragDto.getMinSimilarity());
                                
                                StringBuilder aiResponseBuilder = new StringBuilder();
                                Flux<String> ragFlux = ragService.ragChatStream(ragDto)
                                        .doOnNext(chunk -> aiResponseBuilder.append(chunk))
                                        .doOnComplete(() -> {
                                            // 流式响应完成后保存AI消息
                                            if (aiResponseBuilder.length() > 0) {
                                                saveAiMessage(request, aiResponseBuilder.toString())
                                                        .subscribeOn(Schedulers.boundedElastic())
                                                        .subscribe(
                                                                savedAiMessage -> log.info("RAG模式AI消息已保存，ID: {}", savedAiMessage.getId()),
                                                                error -> log.error("保存RAG模式AI消息失败", error)
                                                        );
                                            }
                                            log.info("RAG对话完成: conversationId={}", request.getConversationId());
                                        });

                                return Flux.concat(metadataFlux, ragFlux);
                            });
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
        log.error("聊天处理失败", ex);
        Map<String, Object> errorPayload = Map.of(
                "type", "error",
                "code", "CHAT_ERROR",
                "message", "聊天处理失败：" + ex.getMessage()
        );
        String errorJson = JSON.toJSONString(errorPayload);
        return Flux.just("[ERROR]" + errorJson);
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
     * 检查并保存用户消息（防止重复）
     */
    private Mono<Message> checkAndSaveUserMessage(ChatRequest request) {
        // 检查最近的消息是否与当前消息相同
        return conversationService.getMessagesByConversationId(request.getConversationId())
                .filter(message -> "user".equals(message.getSender()))
                .takeLast(1)
                .singleOrEmpty()
                .flatMap(lastUserMessage -> {
                    // 如果最近的用户消息内容相同，则不重复保存
                    if (lastUserMessage.getContent().equals(request.getPrompt().trim())) {
                        log.warn("检测到重复的用户消息，跳过保存: {}", request.getPrompt());
                        return Mono.just(lastUserMessage);
                    } else {
                        // 内容不同，保存新消息
                        return saveNewUserMessage(request);
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 没有历史消息，直接保存
                    return saveNewUserMessage(request);
                }));
    }
    
    /**
     * 保存新的用户消息
     */
    private Mono<Message> saveNewUserMessage(ChatRequest request) {
        Message userMessage = new Message();
        userMessage.setConversationId(request.getConversationId());
        userMessage.setSender("user");
        userMessage.setContent(request.getPrompt());
        userMessage.setModel(request.getModel());
        
        return conversationService.addMessageToConversation(userMessage)
                .doOnSuccess(savedMessage -> log.info("新用户消息已保存，ID: {}", savedMessage.getId()));
    }
    
    private Mono<Message> saveAiMessage(ChatRequest chatRequest, String content) {
        if (content == null || content.trim().isEmpty()) {
            log.warn("跳过保存空的AI消息");
            return Mono.empty();
        }
        
        Message aiMessage = new Message();
        aiMessage.setConversationId(chatRequest.getConversationId());
        aiMessage.setSender("ai");
        aiMessage.setContent(content.trim());
        aiMessage.setModel(chatRequest.getModel());
        
        // 使用ConversationService来保存AI消息，并添加重试机制
        return conversationService.addMessageToConversation(aiMessage)
                .retry(3) // 最多重试3次
                .doOnSuccess(savedMessage -> log.info("AI消息已成功保存，ID: {}, 内容长度: {}", 
                        savedMessage.getId(), content.length()))
                .doOnError(e -> log.error("保存AI消息失败，内容: {}, 错误: {}", 
                        content.substring(0, Math.min(content.length(), 100)), e.getMessage(), e))
                .onErrorResume(e -> {
                    log.error("最终保存AI消息失败，将返回空结果", e);
                    return Mono.empty();
                });
    }
}
