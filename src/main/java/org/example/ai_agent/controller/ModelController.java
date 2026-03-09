package org.example.ai_agent.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@CrossOrigin(origins = {"http://localhost:3000", "http://dduo.chat", "https://dduo.chat"}, allowCredentials = "true")
public class ModelController {

    @GetMapping
    public List<Map<String, String>> getAvailableModels() {
        return Arrays.asList(
                createModelInfo("zai-org/GLM-4.5-Air", "GLM-4", "快速响应模型，适合日常对话", "fast"),
                createModelInfo("Pro/deepseek-ai/DeepSeek-R1", "DeepSeek R1", "推理能力更强，适合复杂对话", "medium"),
                createModelInfo("Qwen/Qwen3-Coder-30B-A3B-Instruct", "Qwen3 Coder 30B", "长文本与代码能力更强，适合复杂任务", "slow")
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
