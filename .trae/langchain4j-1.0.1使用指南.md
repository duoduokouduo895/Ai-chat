# LangChain4j 1.0.1 版本使用指南

## 目录
1. [版本概述](#版本概述)
2. [核心架构](#核心架构)
3. [AI模型使用](#ai模型使用)
4. [动态配置管理](#动态配置管理)
5. [流式输出](#流式输出)
6. [会话记忆管理](#会话记忆管理)
7. [Redis会话记忆存储](#redis会话记忆存储)
8. [实际应用示例](#实际应用示例)
9. [最佳实践](#最佳实践)

## 版本概述

LangChain4j 1.0.1 是一个功能完整的Java AI应用开发框架，提供了：

- **多模型支持**：OpenAI、Azure OpenAI、Ollama、Anthropic Claude等
- **流式处理**：支持实时流式响应
- **会话记忆**：内置多种记忆策略
- **动态配置**：支持运行时配置调整
- **模块化设计**：核心功能与扩展功能分离

## 核心架构

### 模块结构
```
langchain4j-1.0.1/
├── langchain4j-core/          # 核心接口和基础类
├── langchain4j-open-ai/       # OpenAI模型支持
├── langchain4j-azure-open-ai/ # Azure OpenAI支持
├── langchain4j-ollama/        # Ollama本地模型支持
├── langchain4j-anthropic/     # Anthropic Claude支持
├── langchain4j-mistral-ai/    # Mistral AI支持
├── langchain4j-google-ai-gemini/ # Google Gemini支持
└── langchain4j-http-client/   # HTTP客户端支持
```

### 核心接口
- `ChatModel`：聊天模型基础接口
- `StreamingChatModel`：流式聊天模型接口
- `ChatMemory`：会话记忆接口
- `ChatMemoryStore`：记忆存储接口

## AI模型使用

### 1. 基础聊天模型

```java
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.data.message.UserMessage;

// 创建OpenAI聊天模型
ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey("your-api-key")
    .modelName("gpt-4")
    .temperature(0.7)
    .maxTokens(1000)
    .timeout(Duration.ofSeconds(30))
    .build();

// 基础聊天
String response = chatModel.chat("你好，请介绍一下自己");
System.out.println(response);

// 使用消息列表
List<ChatMessage> messages = Arrays.asList(
    UserMessage.from("你好"),
    AiMessage.from("你好！我是AI助手，很高兴为您服务。")
);
ChatResponse response = chatModel.chat(messages);
```

### 2. 流式聊天模型

```java
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

// 创建流式聊天模型
StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
    .apiKey("your-api-key")
    .modelName("gpt-4")
    .temperature(0.7)
    .maxTokens(1000)
    .timeout(Duration.ofSeconds(30))
    .build();

// 流式响应处理
streamingModel.generate(Arrays.asList(UserMessage.from("请写一首诗")), 
    new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onNext(String token) {
            System.out.print(token); // 实时输出token
        }
        
        @Override
        public void onComplete(AiMessage response) {
            System.out.println("\n完成！");
        }
        
        @Override
        public void onError(Throwable error) {
            System.err.println("错误: " + error.getMessage());
        }
    });
```

### 3. 多模型支持

```java
// OpenAI模型
ChatModel openAiModel = OpenAiChatModel.builder()
    .apiKey("openai-key")
    .modelName("gpt-4")
    .build();

// Azure OpenAI模型
ChatModel azureModel = AzureOpenAiChatModel.builder()
    .endpoint("https://your-resource.openai.azure.com/")
    .apiKey("azure-key")
    .deploymentName("gpt-4")
    .build();

// Ollama本地模型
ChatModel ollamaModel = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama2")
    .build();
```

## 动态配置管理

### 1. Nacos配置中心集成

```properties
# langchain4j-config.properties
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=https://api.openai.com/v1

# 模型配置
langchain4j.open-ai.chat-model.gpt-4.model-name=gpt-4
langchain4j.open-ai.chat-model.gpt-4.temperature=0.7
langchain4j.open-ai.chat-model.gpt-4.max-tokens=2000

langchain4j.open-ai.chat-model.gpt-3.5.model-name=gpt-3.5-turbo
langchain4j.open-ai.chat-model.gpt-3.5.temperature=0.5
langchain4j.open-ai.chat-model.gpt-3.5.max-tokens=1000
```

### 2. 动态模型注册

```java
@Service
public class AiModelRegistry {
    
    private final Map<String, ChatModel> modelRegistry = new ConcurrentHashMap<>();
    private final AiModelProperties properties;
    
    @PostConstruct
    public void init() {
        // 从Nacos配置动态创建模型
        properties.getModels().forEach((alias, config) -> {
            ChatModel model = OpenAiChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .build();
                
            modelRegistry.put(alias, model);
        });
    }
    
    public ChatModel getModel(String modelId) {
        return modelRegistry.get(modelId);
    }
    
    public Collection<String> getAvailableModels() {
        return modelRegistry.keySet();
    }
}
```

### 3. 配置热更新

```java
@Component
@RefreshScope
public class DynamicAiService {
    
    @Value("${ai.model.default.temperature:0.7}")
    private Double temperature;
    
    @Value("${ai.model.default.max-tokens:2000}")
    private Integer maxTokens;
    
    public ChatModel createModel(String modelName) {
        return OpenAiChatModel.builder()
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();
    }
}
```

## 流式输出

### 1. 基础流式处理

```java
public Flux<String> streamChat(String message, String modelId) {
    StreamingChatModel model = getStreamingModel(modelId);
    
    return Flux.create(sink -> {
        model.generate(Arrays.asList(UserMessage.from(message)), 
            new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    sink.next(token);
                }
                
                @Override
                public void onComplete(AiMessage response) {
                    sink.complete();
                }
                
                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
    });
}
```

### 2. WebFlux集成

```java
@RestController
public class AiController {
    
    @PostMapping("/chat/stream")
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        return aiService.streamChat(request.getMessage(), request.getModelId())
            .doOnSubscribe(s -> log.info("开始流式聊天"))
            .doOnComplete(() -> log.info("流式聊天完成"))
            .doOnError(error -> log.error("流式聊天错误", error));
    }
}
```

### 3. 自定义流式处理器

```java
public class CustomStreamingHandler implements StreamingResponseHandler<AiMessage> {
    
    private final StringBuilder responseBuilder = new StringBuilder();
    private final Consumer<String> onToken;
    private final Consumer<String> onComplete;
    
    public CustomStreamingHandler(Consumer<String> onToken, Consumer<String> onComplete) {
        this.onToken = onToken;
        this.onComplete = onComplete;
    }
    
    @Override
    public void onNext(String token) {
        responseBuilder.append(token);
        onToken.accept(token);
    }
    
    @Override
    public void onComplete(AiMessage response) {
        onComplete.accept(responseBuilder.toString());
    }
    
    @Override
    public void onError(Throwable error) {
        log.error("流式处理错误", error);
    }
}
```

## 会话记忆管理

### 1. 基础记忆实现

```java
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;

// 消息窗口记忆（保留最近N条消息）
ChatMemory messageMemory = MessageWindowChatMemory.withMaxMessages(20);

// Token窗口记忆（保留最近N个token）
ChatMemory tokenMemory = TokenWindowChatMemory.builder()
    .maxTokens(1000, new OpenAiTokenizer())
    .build();

// 使用记忆
messageMemory.add(UserMessage.from("你好"));
messageMemory.add(AiMessage.from("你好！很高兴见到你。"));
messageMemory.add(UserMessage.from("我叫张三"));

// 获取完整对话历史
List<ChatMessage> history = messageMemory.messages();
```

### 2. 记忆提供者模式

```java
@Component
public class ChatMemoryProvider {
    
    private final Map<String, ChatMemory> memoryStore = new ConcurrentHashMap<>();
    
    public ChatMemory getMemory(String sessionId) {
        return memoryStore.computeIfAbsent(sessionId, 
            id -> MessageWindowChatMemory.withMaxMessages(20));
    }
    
    public void clearMemory(String sessionId) {
        memoryStore.remove(sessionId);
    }
}
```

### 3. AI Service集成

```java
interface ChatBot extends ChatMemoryAccess {
    String chat(@UserMessage String message);
}

ChatBot bot = AiServices.builder(ChatBot.class)
    .chatModel(chatModel)
    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
    .build();

// 自动维护对话历史
String response1 = bot.chat("你好");
String response2 = bot.chat("我叫张三"); // AI会记住之前的对话
```

## Redis会话记忆存储

### 1. 自定义Redis存储实现

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = redisTemplate.opsForValue().get(memoryId.toString());
        if (json == null) {
            return new ArrayList<>();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(
            memoryId.toString(), 
            json, 
            Duration.ofDays(1)
        );
    }
    
    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(memoryId.toString());
    }
}
```

### 2. 配置Redis存储

```java
@Configuration
public class LangChain4jConfig {
    
    @Bean
    public ChatMemoryStore chatMemoryStore() {
        return new RedisChatMemoryStore();
    }
    
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
            .maxMessages(20)
            .chatMemoryStore(store)
            .id(memoryId)
            .build();
    }
}
```

### 3. 多用户记忆隔离

```java
@Service
public class MultiUserChatService {
    
    private final ChatMemoryProvider memoryProvider;
    private final ChatModel chatModel;
    
    public Flux<String> chat(String userId, String message) {
        ChatMemory memory = memoryProvider.get(userId);
        memory.add(UserMessage.from(message));
        
        return Flux.create(sink -> {
            chatModel.generate(memory.messages(), 
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        sink.next(token);
                    }
                    
                    @Override
                    public void onComplete(AiMessage response) {
                        memory.add(response);
                        sink.complete();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        sink.error(error);
                    }
                });
        });
    }
}
```

## 实际应用示例

### 1. 完整的聊天服务

```java
@Service
@Slf4j
public class AiChatService {
    
    @Autowired
    private AiModelRegistry modelRegistry;
    
    @Autowired
    private ChatMemoryProvider memoryProvider;
    
    public Flux<String> chat(String modelId, String sessionId, String message) {
        // 获取模型
        StreamingChatModel model = modelRegistry.getStreamingModel(modelId);
        if (model == null) {
            return Flux.error(new IllegalArgumentException("不支持的模型: " + modelId));
        }
        
        // 获取会话记忆
        ChatMemory memory = memoryProvider.get(sessionId);
        memory.add(UserMessage.from(message));
        
        // 流式响应
        return Flux.create(sink -> {
            model.generate(memory.messages(), 
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        sink.next(token);
                    }
                    
                    @Override
                    public void onComplete(AiMessage response) {
                        memory.add(response);
                        sink.complete();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("AI聊天错误", error);
                        sink.error(error);
                    }
                });
        });
    }
}
```

### 2. 控制器层

```java
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiController {
    
    @Autowired
    private AiChatService aiChatService;
    
    @PostMapping("/chat")
    public Flux<String> chat(@RequestBody ChatRequest request) {
        return aiChatService.chat(
            request.getModelId(), 
            request.getSessionId(), 
            request.getMessage()
        );
    }
    
    @GetMapping("/models")
    public Mono<Collection<ModelInfo>> getAvailableModels() {
        return Mono.just(modelRegistry.getAvailableModels());
    }
    
    @DeleteMapping("/memory/{sessionId}")
    public Mono<Void> clearMemory(@PathVariable String sessionId) {
        memoryProvider.clear(sessionId);
        return Mono.empty();
    }
}
```

### 3. 配置类

```java
@Configuration
@EnableConfigurationProperties(AiModelProperties.class)
public class LangChain4jConfig {
    
    @Bean
    public ChatMemoryStore chatMemoryStore() {
        return new RedisChatMemoryStore();
    }
    
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
            .maxMessages(20)
            .chatMemoryStore(store)
            .id(memoryId)
            .build();
    }
    
    @Bean
    public AiModelRegistry aiModelRegistry(AiModelProperties properties) {
        return new AiModelRegistry(properties);
    }
}
```

## 最佳实践

### 1. 模型管理
- 使用模型注册中心统一管理所有AI模型
- 支持动态配置和热更新
- 实现模型健康检查和故障转移

### 2. 记忆策略
- 根据业务需求选择合适的记忆策略
- 使用Redis等外部存储实现持久化
- 实现记忆清理和过期策略

### 3. 错误处理
- 实现完善的错误处理和重试机制
- 使用熔断器模式防止级联故障
- 记录详细的日志信息便于调试

### 4. 性能优化
- 使用连接池管理HTTP连接
- 实现合理的缓存策略
- 支持异步处理和响应式编程

### 5. 安全考虑
- 不在代码中硬编码API密钥
- 使用环境变量或配置中心管理敏感信息
- 实现API访问频率限制

## 总结

LangChain4j 1.0.1 提供了完整的AI应用开发框架，支持：

1. **多模型集成**：OpenAI、Azure、Ollama等主流模型
2. **流式处理**：实时响应和流式输出
3. **会话记忆**：多种记忆策略和存储后端
4. **动态配置**：支持运行时配置调整
5. **模块化设计**：易于扩展和维护

通过合理使用这些功能，可以构建功能强大、性能优异的AI应用系统。 