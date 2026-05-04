package org.example.ai_agent.controller;

import org.example.ai_agent.config.ai.AiModelProperties;
import org.example.ai_agent.service.registry.AiModelRegistry;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 旧模型接口兼容层。
 * 主模型元数据入口已经收敛到 /api/ai/models，这里仅保留给历史前端调用。
 */
@Deprecated
@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class ModelController {

    private final AiModelRegistry aiModelRegistry;

    public ModelController(AiModelRegistry aiModelRegistry) {
        this.aiModelRegistry = aiModelRegistry;
    }

    /**
     * 兼容旧前端的模型列表接口。
     * 数据来源与 /api/ai/models 完全一致，不再维护独立硬编码模型列表。
     */
    @GetMapping
    public List<AiModelProperties.Metadata> getAvailableModels() {
        return aiModelRegistry.getModelMetadata();
    }
}


