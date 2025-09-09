package org.example.ai_agent.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.dto.chat.ChatRequest;
import org.example.ai_agent.service.AiChatService;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class AiController {

    private final AiModelRegistry aiModelRegistry;
    private final AiChatService aiChatService;
    private final org.example.ai_agent.util.JwtUtil jwtUtil;

    public AiController(AiModelRegistry aiModelRegistry, AiChatService aiChatService, org.example.ai_agent.util.JwtUtil jwtUtil) {
        this.aiModelRegistry = aiModelRegistry;
        this.aiChatService = aiChatService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 获取所有可用的AI模型元数据列表，供前端展示。
     *
     * @return 模型元数据列表
     */
    @GetMapping("/models")
    public Mono<List<AiModelProperties.Metadata>> getAvailableModels() {
        return Mono.just(aiModelRegistry.getModelMetadata());
    }

    /**
     * 处理流式聊天请求。
     *
     * @param chatRequest 聊天请求体
     * @return 响应内容的流
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatRequest chatRequest, @RequestHeader(org.springframework.http.HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        String token = authorizationHeader.substring(7).trim(); // 去除token中的空白字符
        
        // 从Token中获取用户信息
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            return Flux.error(new IllegalArgumentException("无效的Token: 无法获取用户ID"));
        }
        chatRequest.setUserId(userId);
        
        log.info("📨 [控制器] 用户 {} 聊天请求: 模型={}, 知识库数量={}",
                userId, chatRequest.getModel(), 
                chatRequest.getKnowledgeBaseIds() != null ? chatRequest.getKnowledgeBaseIds().size() : 0);
        
        return aiChatService.chat(chatRequest);
    }
}

