package org.example.ai_agent.controller;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class ModelController {

    @GetMapping
    public List<Map<String, String>> getAvailableModels() {
        return Arrays.asList(
            createModelInfo("zai-org/GLM-4.5-Air", "GLM-4", "超快响应模型，适合快速对话（推荐）", "fast"),
            createModelInfo("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "高性能推理模型，适合复杂对话", "medium"),
            createModelInfo("Tongyi-Zhiwen/QwenLong-L1-32B", "通义千问-32B", "长文本处理模型，响应较慢但适合长对话", "slow")
        );
    }

    private Map<String, String> createModelInfo(String value, String label, String description, String speed) {
        Map<String, String> model = new HashMap<>();
        model.put("value", value);
        model.put("label", label);
        model.put("description", description);
        model.put("speed", speed);
        return model;
    }
}
