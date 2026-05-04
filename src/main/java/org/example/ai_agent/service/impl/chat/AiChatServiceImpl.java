package org.example.ai_agent.service.impl.chat;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.RagService;
import org.example.ai_agent.service.assistant.GeneralAssistant;
import org.example.ai_agent.service.assistant.GeneralAssistantFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.alibaba.fastjson.JSON;
import java.util.Map;



@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private final GeneralAssistantFactory generalAssistantFactory;
    private final ConversationService conversationService;
    private final RagService ragService;

    public AiChatServiceImpl(GeneralAssistantFactory generalAssistantFactory,
                             ConversationService conversationService,
                             RagService ragService) {
        this.generalAssistantFactory = generalAssistantFactory;
        this.conversationService = conversationService;
        this.ragService = ragService;
    }

    @Override
    public Flux<ServerSentEvent<String>> chat(ChatRequest chatRequest) {
        log.info("收到统一 Assistant 对话请求: userId={}, model={}, ragMode={}",
                chatRequest.getUserId(), chatRequest.getModel(), chatRequest.isRagMode());

        return Mono.just(chatRequest)
                .flatMap(this::ensureConversationExists)
                .flatMapMany(this::handleAssistantChat)
                .onErrorResume(this::handleChatError);
    }

    /**
     * 统一处理 Assistant 对话。
     * 是否启用知识增强，只影响发送给模型的输入内容，不再影响主链结构。
     */
    private Flux<ServerSentEvent<String>> handleAssistantChat(ChatRequest request) {
        Map<String, Object> metadata = Map.of(
                "type", "metadata",
                "conversationId", request.getConversationId(),
                "mode", request.isRagMode() ? "rag" : "normal"
        );
        String metadataJson = JSON.toJSONString(metadata);
        Flux<ServerSentEvent<String>> metadataFlux = Flux.just(
                ServerSentEvent.<String>builder()
                        .event("metadata")
                        .data(metadataJson)
                        .build()
        );

        Flux<ServerSentEvent<String>> messageFlux = performAssistantChat(request);
        return Flux.concat(metadataFlux, messageFlux);
    }

    /**
     * 统一的错误处理
     */
    private Flux<ServerSentEvent<String>> handleChatError(Throwable ex) {
        log.error("聊天处理失败", ex);
        Map<String, Object> errorPayload = Map.of(
                "type", "error",
                "code", "CHAT_ERROR",
                "message", "聊天处理失败：" + ex.getMessage()
        );
        String errorJson = JSON.toJSONString(errorPayload);
        return Flux.just(
                ServerSentEvent.<String>builder()
                        .event("error")
                        .data(errorJson)
                        .build()
        );
    }
    /**
     * 确保对话存在
     */
    private Mono<ChatRequest> ensureConversationExists(ChatRequest request) {
        if (request.getConversationId() != null) {
            return conversationService.getConversationById(request.getConversationId(), request.getUserId())
                    .thenReturn(request);
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
     * 执行统一 Assistant 聊天。
     */
    private Flux<ServerSentEvent<String>> performAssistantChat(ChatRequest chatRequest) {
        Message userMessage = new Message();
        userMessage.setConversationId(chatRequest.getConversationId());
        userMessage.setSender("user");
        userMessage.setContent(chatRequest.getTrimmedMessage());
        userMessage.setModel(chatRequest.getEffectiveModelId());

        return conversationService.addMessageToConversation(userMessage)
                .flatMapMany(savedUserMessage -> buildAssistantInput(chatRequest)
                        .flatMapMany(assistantInput -> streamAssistantResponse(chatRequest, assistantInput)));
    }

    private Flux<ServerSentEvent<String>> streamAssistantResponse(ChatRequest chatRequest, String assistantInput) {
        try {
            GeneralAssistant assistant = generalAssistantFactory.createAssistant(chatRequest);
            String memoryId = String.valueOf(chatRequest.getConversationId());
            StringBuilder responseBuilder = new StringBuilder();

            return assistant.chat(memoryId, assistantInput)
                    .doOnNext(responseBuilder::append)
                    .map(chunk -> ServerSentEvent.<String>builder()
                            .event("message")
                            .data(chunk)
                            .build())
                    .doOnComplete(() -> {
                        if (responseBuilder.length() > 0) {
                            saveAiMessage(chatRequest, responseBuilder.toString())
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe(
                                            savedAiMessage -> {
                                                if (savedAiMessage != null) {
                                                    log.info("Assistant AI消息已保存，ID: {}", savedAiMessage.getId());
                                                }
                                            },
                                            error -> log.error("保存Assistant AI消息失败", error)
                                    );
                        }
                        log.info("Assistant对话完成: conversationId={}, ragMode={}",
                                chatRequest.getConversationId(), chatRequest.isRagMode());
                    });
        } catch (Exception e) {
            log.error("Assistant执行失败", e);
            return Flux.error(e);
        }
    }

    private Mono<String> buildAssistantInput(ChatRequest request) {
        if (!request.isRagMode()) {
            return Mono.just(request.getTrimmedMessage());
        }

        return ragService.retrieveContext(
                        request.getTrimmedMessage(),
                        request.getKnowledgeBaseIds(),
                        request.getMaxRetrievedDocs(),
                        request.getMinSimilarity()
                )
                .map(context -> buildKnowledgeAugmentedPrompt(request, context))
                .doOnSuccess(prompt -> log.info("知识增强输入已构建: conversationId={}, knowledgeBaseCount={}",
                        request.getConversationId(),
                        request.getKnowledgeBaseIds() != null ? request.getKnowledgeBaseIds().size() : 0));
    }

    private String buildKnowledgeAugmentedPrompt(ChatRequest request, RagService.RetrievalContext context) {
        String knowledgeContext = context.contextText();
        if (knowledgeContext == null || knowledgeContext.isBlank()) {
            knowledgeContext = "未检索到可直接引用的知识库内容。";
        }

        return String.format("""
                你正在处理一个带知识库上下文的用户问题。
                请优先根据参考资料回答；如果参考资料不足以支撑结论，请明确说明信息不足。
                不要编造资料中没有的信息，也不要机械复述“根据知识库”这类套话。

                参考资料：
                %s

                用户问题：
                %s
                """, knowledgeContext, request.getTrimmedMessage());
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
        aiMessage.setModel(chatRequest.getEffectiveModelId());
        
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
