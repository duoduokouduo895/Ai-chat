package org.example.ai_agent.service.impl.chat;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.service.RagService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AssistantInputBuilder {

    private final RagService ragService;

    public AssistantInputBuilder(RagService ragService) {
        this.ragService = ragService;
    }

    public Mono<String> build(ChatRequest request) {
        if (!request.isRagMode()) {
            return Mono.just(request.getTrimmedMessage());
        }

        return ragService.retrieveContext(
                        request.getTrimmedMessage(),
                        request.getKnowledgeBaseIds(),
                        request.getMaxRetrievedDocs(),
                        request.getMinSimilarity(),
                        request.getUserId(),
                        request.isAdminRequest()
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
}
