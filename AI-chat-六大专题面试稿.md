# AI-chat 六大专题面试稿

这份文档的用途是帮助你在面试中把 `AI-chat` 介绍成一个完整的 Java AI 应用项目。面试官关心的是：项目解决什么问题、核心链路怎么设计、你负责的技术点有哪些、遇到追问时能不能说清楚边界和取舍。

推荐开场：

> 我这个项目是一个基于 `Spring Boot 3 + WebFlux + LangChain4j + Vue3` 的通用 AI Assistant 系统。它不只是一个调用大模型 API 的聊天页面，而是围绕 Assistant 主链路做了会话管理、流式输出、知识库 RAG、文档 ingestion、Tool Calling、模型注册、安全鉴权和后台管理。后端用 `Java 17`，核心 AI 层使用 `LangChain4j 1.0.1-beta6`，中间件包括 `MySQL`、`Redis`、`Elasticsearch`、`RabbitMQ` 和 `Nacos`。

一句话定位：

> 这是一个面向个人知识问答和轻量任务查询的通用 Assistant 系统，用户可以进行多轮对话，也可以选择知识库让模型基于上传文档回答问题；系统内部通过统一 Assistant 主链路把模型、Memory、RAG、Tools 和流式输出串起来。

当前项目主线：

`/api/ai/chat -> AiController -> AiChatServiceImpl -> GeneralAssistantFactory -> GeneralAssistant -> Memory / RAG / Tools -> LLM`

面试时尽量用“系统如何工作”来讲，不要主动讲项目演进过程。如果面试官问你做过哪些调整，再补充设计取舍。

---

## 开场四层讲法

这四层是面试时最稳的表达顺序。先把项目说成一个真实可用的聊天与知识库问答系统，再进入 RAG 流程和工程设计，最后主动说明边界。不要一上来就讲 RAG 评估、citation、rerank 这些细节，它们更适合作为追问时的补充。

### 第一层：项目是什么

可以这样说：

> 这是一个基于 `Spring Boot 3 + WebFlux + LangChain4j + Vue3` 的 AI 聊天与知识库问答系统。用户可以进行普通多轮聊天，也可以上传文档建立知识库，然后基于知识库进行问答。系统支持 SSE 流式输出、会话历史、知识库管理、文档处理状态查询和后台管理。

这一层的重点是把项目讲清楚，不要夸大成高级 Agent 平台。更准确的定位是：

> 一个以 Java 后端工程能力为主体，融合普通聊天、RAG 知识库问答、Tool Calling 和异步文档处理的 AI 应用项目。

### 第二层：RAG 流程怎么跑

可以这样说：

> 项目的 RAG 分成离线入库和在线问答两部分。离线阶段，用户上传文档后，系统会解析文档、切分文本、调用 embedding model 生成向量，并把文本块和 metadata 存到 Elasticsearch。在线阶段，用户提问时，如果选择了知识库，系统会先用问题生成检索向量，在 Elasticsearch 中按 `knowledgeBaseId` 检索相关 chunk，再把检索结果拼进 prompt，最后交给 Assistant 生成回答。

这段是面试里最重要的 RAG 主线。你需要能脱稿说清楚：

- 文档怎么从文件变成文本。
- 文本怎么切成 chunk。
- chunk 怎么生成 embedding。
- embedding、文本和 metadata 怎么存入 Elasticsearch。
- 用户问题怎么检索相关 chunk。
- 检索结果怎么进入 Assistant 输入。
- Assistant 怎么通过 SSE 流式返回回答。

### 第三层：为什么这样设计

可以这样说：

> 文档处理是长任务，所以解析、分块、向量化不适合同步阻塞聊天接口；向量里保留 `knowledgeBaseId`、`documentId`、`chunkId`，是为了做知识库隔离、文档追踪和后续删除；RAG 没有单独做成另一套聊天系统，而是作为 Assistant 输入增强的一部分，这样普通聊天和知识库问答可以共用同一条主链路。

这一层体现你的工程理解。面试官常问的不是“你用了什么”，而是“为什么这么用”：

- 为什么文档处理要异步：解析、分块、embedding 都可能耗时，同步做会阻塞上传请求。
- 为什么用 metadata：为了知识库隔离、文档定位、chunk 追踪和向量删除。
- 为什么 RAG 不单独做一套聊天入口：它本质是 Assistant 输入增强能力，不应该让聊天主链路分裂。
- 为什么用 Redis 做 Memory：模型上下文窗口需要高频读写和短期保存。
- 为什么 History 存 MySQL：完整消息需要持久化、查询和前端展示。
- 为什么用 SSE：大模型输出是流式生成，SSE 适合服务端持续推送文本片段。

### 第四层：主动承认边界

可以这样说：

> 当前这套 RAG 本质上还是 Naive RAG，主要是向量检索加上下文注入，还没有做 hybrid retrieval、rerank、citation 和系统化评估。它的重点是把完整链路跑通，并且在 Java 后端里把会话、文档、向量、权限和异步处理串起来。如果继续优化，我会优先做检索质量评估、引用来源展示和失败任务补偿。

主动承认边界不是减分，而是让项目显得真实。你可以把边界讲成后续优化方向：

- RAG 检索质量：补 hybrid retrieval、rerank、query rewrite 和评估集。
- 可解释性：回答里展示来源文档、chunk、相似度和引用片段。
- 文档处理可靠性：补失败任务补偿、DLQ 重新投递和任务状态面板。
- Tool Calling 安全：补工具调用日志、参数校验、超时控制和权限细化。
- Memory 能力：当前是窗口记忆，后续可做摘要记忆或长期记忆。

### 这一段的完整 1 分钟版本

> 我这个项目可以先理解成一个 AI 聊天与知识库问答系统。用户可以普通多轮聊天，也可以上传文档建立知识库，然后基于知识库进行问答。后端用 `Spring Boot 3 + WebFlux + LangChain4j`，聊天接口用 SSE 做流式输出。
>
> RAG 流程分成离线和在线两部分。离线阶段，文档上传后会经过解析、分块、embedding，并把文本块、向量和 `knowledgeBaseId`、`documentId`、`chunkId` 等 metadata 存入 Elasticsearch。在线阶段，用户提问时，如果选择了知识库，系统会先检索相关 chunk，再把检索结果拼进 Assistant 输入，让模型基于参考资料回答。
>
> 这个设计的重点不是把 RAG 说得很高级，而是把 Java 后端里的完整工程链路跑通：会话历史存 MySQL，模型 Memory 放 Redis，文档处理通过 RabbitMQ 异步推进，向量和检索放 Elasticsearch，接口层用 JWT 做鉴权。当前它还是 Naive RAG，后续可以继续做 hybrid retrieval、rerank、citation 和评估体系。

---

## 一、统一 Assistant 主链路

### 面试时怎么介绍

这个项目的核心入口是 `/api/ai/chat`。前端发起聊天请求后，`AiController` 负责接收请求、解析当前登录用户、判断是否管理员请求，然后把请求交给 `AiChatServiceImpl`。真正的业务编排不放在 Controller，而是在 Service 层统一完成。

`AiChatServiceImpl` 会做几件事：

- 确认当前 conversation 是否存在，不存在就创建新会话。
- 保存用户消息到数据库，作为用户可见的 History。
- 如果请求开启了 RAG，就调用 `RagService.retrieveContext(...)` 检索知识库上下文。
- 构造最终发给 Assistant 的输入。
- 通过 `GeneralAssistantFactory` 创建 LangChain4j 的 `GeneralAssistant`。
- 用 `ServerSentEvent` 按流式方式把模型输出返回给前端。
- 在输出完成后保存 AI 回复。

`GeneralAssistantFactory` 是 AI 层的工厂。它根据用户选择的模型，从 `AiModelRegistry` 获取 `StreamingChatModel`，再把 `ChatMemoryProvider` 和 `KnowledgeAssistantTools` 挂到 LangChain4j 的 `AiServices.builder(...)` 上。这样模型调用、会话记忆和工具调用都集中在 Assistant 抽象里。

### 1 分钟回答

> 我项目里的聊天不是 Controller 直接调用模型。请求先进入 `/api/ai/chat`，再由 `AiChatServiceImpl` 统一编排会话创建、消息落库、RAG 上下文检索、Assistant 调用和 SSE 流式输出。真正和 LangChain4j 对接的是 `GeneralAssistantFactory`，它会根据模型配置创建 `GeneralAssistant`，并把 Memory 和 Tool 绑定进去。这样普通聊天、知识库问答和 Tool Calling 都在同一条 Assistant 主链路上，不会变成多个分散的聊天入口。

### 高频追问

- 为什么不让 Controller 直接调模型？
  Controller 只处理 HTTP、鉴权上下文和响应格式。会话、RAG、模型、Tool 和消息落库属于业务编排，放在 Service 层更容易维护和测试。

- `GeneralAssistantFactory` 的作用是什么？
  它负责创建 LangChain4j 的 AI Service 代理，把模型选择、MemoryProvider 和 Tools 组合到 `GeneralAssistant` 上。

- `AiModelRegistry` 解决什么问题？
  它根据配置初始化多个 `StreamingChatModel`，对外提供可用模型列表和按 modelId 获取模型实例的能力，避免业务层散落模型构造逻辑。

- 普通聊天和知识库聊天是不是两套流程？
  不是。它们都走统一 Assistant 主链路。区别只是开启 RAG 时，会在发给 Assistant 前补充知识库上下文。

### 源码锚点

- `src/main/java/org/example/ai_agent/controller/AiController.java`
- `src/main/java/org/example/ai_agent/service/impl/chat/AiChatServiceImpl.java`
- `src/main/java/org/example/ai_agent/service/assistant/GeneralAssistantFactory.java`
- `src/main/java/org/example/ai_agent/service/assistant/GeneralAssistant.java`
- `src/main/java/org/example/ai_agent/service/registry/AiModelRegistry.java`

---

## 二、RAG 与文档 Ingestion

### 面试时怎么介绍

项目里的 RAG 可以拆成两条链路：离线入库链路和在线问答链路。

离线链路是 `parse -> chunk -> embed -> store`：

- 用户上传文档到知识库。
- 文档解析使用 Apache Tika，对 PDF、DOCX、TXT、MD 等格式做文本提取。
- 文本分块使用 LangChain4j 的 `DocumentSplitter`，配置里默认 `maxSegmentSize=500`、`maxOverlapSize=50`。
- 向量化使用 embedding model。
- 向量和文本片段写入 Elasticsearch 的 `EmbeddingStore<TextSegment>`。
- metadata 会记录 `knowledge_base_id`、`document_id`、`chunk_id` 等信息，用于过滤、追踪和删除。

在线链路是“先检索，再增强输入”：

- 用户如果选择了知识库，`AiChatServiceImpl` 会调用 `RagService.retrieveContext(...)`。
- 检索层根据用户问题做向量相似度搜索，并按知识库 id 做过滤。
- 检索结果被整理成参考资料，拼进 Assistant 输入。
- Assistant 在回答时优先依据参考资料，如果资料不足，需要说明信息不足。

这块不要讲成“我做了很高级的 RAG”。更稳的说法是：当前是工程闭环完整的 Naive RAG，具备文档入库、向量存储、metadata 隔离、检索增强和失败降级，但还没有完整做 hybrid retrieval、rerank、引用展示和评估闭环。

### 1 分钟回答

> 我的 RAG 分为离线和在线两部分。离线部分是文档 ingestion，上传后经过解析、分块、向量化和写入 Elasticsearch。在线部分是在用户选择知识库后，先根据问题检索相关片段，再把这些片段作为参考资料拼入 Assistant 输入。这里我重点做的是工程闭环：文档状态可追踪，向量里有 metadata，检索时能按知识库隔离，ES 向量索引启动时会检查和创建。当前它属于 Naive RAG，但链路完整，后续可以继续扩展 hybrid retrieval、rerank 和 citation。

### 高频追问

- 为什么要把 RAG 拆成离线和在线两部分？
  文档解析、分块、embedding、写入向量库是长任务，适合异步入库；在线问答只负责检索和增强输入，响应链路更清晰。

- metadata 有什么价值？
  用于知识库隔离、文档追踪、chunk 定位、删除向量和后续权限控制。没有 metadata，多个知识库的向量会混在一起，很难做过滤和管理。

- 为什么说是 Naive RAG？
  当前主要是向量检索加上下文注入，还没有正式引入 BM25 混合检索、rerank、引用标注和自动化评估。

- ES index 不存在怎么办？
  `KnowledgeBaseConfig` 会检查并创建 `knowledge_base_embeddings`，mapping 包含 `dense_vector`、`text` 和 metadata 字段。

- 检索不到内容时怎么处理？
  Assistant 输入会说明没有检索到可直接引用的知识库内容，避免模型编造，也避免整个聊天接口直接失败。

### 源码锚点

- `src/main/java/org/example/ai_agent/service/DocumentIngestionService.java`
- `src/main/java/org/example/ai_agent/service/impl/document/DocumentIngestionServiceImpl.java`
- `src/main/java/org/example/ai_agent/config/ai/KnowledgeBaseConfig.java`
- `src/main/java/org/example/ai_agent/service/impl/knowledge/EmbeddingServiceImpl.java`
- `src/main/java/org/example/ai_agent/service/impl/knowledge/RagServiceImpl.java`
- `src/main/java/org/example/ai_agent/controller/KnowledgeController.java`

---

## 三、Memory / History 分层

### 面试时怎么介绍

项目里要区分两个概念：History 和 Memory。

History 是产品层的完整聊天记录，保存到数据库，用于前端展示、会话列表、消息详情、搜索和审计。用户发出的消息和 AI 回复都会落库，这部分强调完整性和可追溯。

Memory 是模型层真正消费的上下文窗口。模型不应该每次都读取数据库里的全部历史，否则 token 成本高，长对话噪声也会增加。项目使用 LangChain4j 的 `MessageWindowChatMemory`，通过 conversationId 作为 memoryId，为每个会话创建隔离的 Memory，并用 Redis 作为 `ChatMemoryStore`。

当前 `ChatMemoryProvider` 里配置 `maxMessages(20)`，含义是模型侧只保留最近窗口里的上下文，而不是无上限塞入历史记录。`RedisChatMemeoryStore` 负责把 LangChain4j 的 `ChatMessage` 序列化到 Redis，并设置 1 天过期时间。

### 1 分钟回答

> 我在项目里把聊天记录分成了 History 和 Memory。History 是用户可见的完整历史，保存到数据库，用于展示和追踪。Memory 是模型真正消费的上下文窗口，使用 LangChain4j 的 `MessageWindowChatMemory`，按 conversationId 隔离，并通过 Redis 存储。这样既能保证用户历史记录完整，又不会每次把全部历史都塞给模型，能控制 token 成本和上下文噪声。

### 高频追问

- 为什么数据库里有历史记录，还要 Redis Memory？
  数据库保存完整历史，Redis Memory 服务模型上下文窗口。一个强调持久化和查询，一个强调低延迟和上下文控制。

- 为什么不能把所有历史都给模型？
  长历史会增加 token 成本，也可能把无关内容带进上下文，影响回答稳定性。

- conversationId 在 Memory 中有什么作用？
  conversationId 作为 memoryId，保证每个会话有独立上下文，避免不同会话之间串上下文。

- Redis Memory 丢失会不会影响历史？
  不影响数据库里的 History。Redis 主要影响模型短期上下文，完整消息仍然在数据库中。

- 这里的工程价值是什么？
  把“用户可见历史”和“模型消费上下文”拆开，职责清楚，后续可以单独优化消息查询、上下文窗口、摘要记忆或长期记忆。

### 源码锚点

- `src/main/java/org/example/ai_agent/service/provider/ChatMemoryProvider.java`
- `src/main/java/org/example/ai_agent/repository/chat/RedisChatMemeoryStore.java`
- `src/main/java/org/example/ai_agent/service/impl/chat/AiChatServiceImpl.java`
- `src/main/java/org/example/ai_agent/service/impl/chat/ConversationServiceImpl.java`
- `src/main/java/org/example/ai_agent/repository/chat/MessageRepository.java`

---

## 四、Tool Calling 与轻量 Agent 能力

### 面试时怎么介绍

这个项目不适合硬说成“全能 Agent 平台”。更准确的定位是：通用 Assistant 加上可控的 Tool Calling。

当前 Tool 集中在 `KnowledgeAssistantTools`，它把项目已有的业务服务封装给模型使用，主要有两类能力：

- `listAvailableKnowledgeBases()`：列出当前用户可访问的知识库。
- `getDocumentProcessingStatus(Long documentId)`：查询文档解析状态、向量化状态、分块数量和是否已完成向量入库。

这些 Tool 和项目主线强相关，因为它们服务于知识库问答和文档 ingestion。比如用户问“我现在有哪些知识库可以用”，模型不应该编一个列表，而应该调用 Tool 查系统真实数据。用户问“某个文档是否已经能用于问答”，模型也应该查文档处理状态。

面试中可以强调 Tool 的边界：当前优先暴露查询类和状态类工具，不直接开放危险写操作。这样既体现模型能访问系统内部能力，也能控制风险。

### 1 分钟回答

> 我没有把项目包装成很泛的 Agent 平台，而是做了轻量、可控的 Tool Calling。`KnowledgeAssistantTools` 复用现有的 `KnowledgeBaseService` 和 `DocumentService`，让模型在对话中可以查询用户可访问的知识库，以及某个文档是否解析和向量化完成。这些 Tool 和 RAG 主线直接相关，而且都是查询类能力，风险比较低。它体现的是模型不只生成文本，还能按需访问 Java 后端里的真实业务能力。

### 高频追问

- Tool 和普通 Service 调用有什么区别？
  普通 Service 调用是代码固定流程，Tool 是模型根据用户意图在对话中选择调用。

- 为什么优先做知识库 Tool？
  知识库和文档状态是项目主线的一部分，能增强 Assistant 对系统状态的感知，比外接无关工具更有面试价值。

- Tool Calling 有什么风险？
  模型可能误调用、传错参数或过度调用。因此当前只暴露查询类、状态类能力，避免直接执行高风险写操作。

- 为什么说这是轻量 Agent 能力？
  Agent 的关键不是名字，而是模型能够根据目标选择工具、读取系统状态并完成回答。当前项目具备这个入口，但没有扩展成复杂规划型 Agent。

- 这块后续怎么增强？
  可以增加 Tool 参数校验、调用审计、超时控制、权限细化，以及更多和知识库管理相关的低风险工具。

### 源码锚点

- `src/main/java/org/example/ai_agent/service/assistant/KnowledgeAssistantTools.java`
- `src/main/java/org/example/ai_agent/service/assistant/GeneralAssistant.java`
- `src/main/java/org/example/ai_agent/service/assistant/GeneralAssistantFactory.java`
- `src/main/java/org/example/ai_agent/service/KnowledgeBaseService.java`
- `src/main/java/org/example/ai_agent/service/DocumentService.java`

---

## 五、异步文档流水线与中间件观测

### 面试时怎么介绍

文档处理是项目里比较工程化的一条链路。上传文件本身不是最复杂的，真正耗时的是解析、分块、embedding 和向量入库。如果全部放在一次同步 HTTP 请求里，用户体验差，也容易因为任意一步失败拖垮整个请求。

项目使用 RabbitMQ 把文档处理拆成多个阶段：

- `document.parse`：解析文档。
- `document.chunk`：文本分块。
- `document.embed`：向量化并入库。
- 对应还有 retry queue 和 DLQ，用于失败重试和死信兜底。

`DocumentProcessingRabbitConfig` 里定义了 topic exchange、解析队列、分块队列、向量化队列、重试队列和死信队列。`DocumentProcessingServiceImpl` 负责把文档处理任务投递到对应 routing key。这样每个阶段可以独立消费、独立记录状态，也方便面试时讲清楚“长任务异步化”的价值。

项目还做了启动时的基础设施观测。`StartupInfrastructureReporter` 会集中检查 Nacos 配置、MySQL、Redis、RabbitMQ、Elasticsearch 等关键组件状态。面试时可以说：中间件不是只写在简历里的名词，项目启动时能看到关键依赖是否可用，方便本地演示和问题定位。

### 1 分钟回答

> 文档处理不是同步完成的，而是通过 RabbitMQ 拆成解析、分块、向量化几个阶段。上传接口可以快速返回，后续处理通过队列推进，每个阶段都有自己的状态记录，也有 retry queue 和 DLQ 做失败兜底。这样做的价值是长任务异步化、阶段解耦、失败可追踪。除此之外，我还用 `StartupInfrastructureReporter` 在启动时集中检查 Nacos、MySQL、Redis、RabbitMQ 和 Elasticsearch，方便面试演示时快速判断环境是否正常。

### 高频追问

- 为什么文档处理要异步？
  解析、分块、embedding 和写向量库都可能耗时，同步做会阻塞上传请求，也不利于失败重试。

- RabbitMQ 在这里解决什么问题？
  解决阶段解耦、削峰、失败隔离、延迟重试和死信兜底。

- retry queue 和 DLQ 的作用是什么？
  retry queue 用 TTL 延迟后重新投递，DLQ 用来承接最终失败的消息，方便后续人工排查或补偿。

- 怎么知道文档处理到哪一步了？
  文档和 chunk 上有解析状态、向量化状态、分块数量等字段，后台接口也能查询 processing record。

- 启动观测有什么实际价值？
  本地演示时常见问题是中间件没启动或配置没加载。集中输出关键组件状态，可以快速定位是业务问题还是环境问题。

### 源码锚点

- `src/main/java/org/example/ai_agent/config/messaging/DocumentProcessingRabbitConfig.java`
- `src/main/java/org/example/ai_agent/constants/DocumentProcessingConstants.java`
- `src/main/java/org/example/ai_agent/service/impl/document/DocumentProcessingServiceImpl.java`
- `src/main/java/org/example/ai_agent/service/impl/document/DocumentProcessingRecordServiceImpl.java`
- `src/main/java/org/example/ai_agent/config/startup/StartupInfrastructureReporter.java`

---

## 六、安全鉴权、流式接口与模型管理

### 面试时怎么介绍

项目的对话接口是受保护的。`SecurityConfig` 基于 WebFlux Security 配置了统一鉴权链路：

- 登录、注册、游客入口、管理员登录等少量接口放行。
- `/api/admin/**`、`/api/knowledge/admin/**`、`/api/conversations/admin/**` 需要 `ROLE_ADMIN`。
- 其他接口默认需要认证。
- JWT 的 jti 会落 Redis，认证时检查 `token:jti:{jti}` 是否存在，用于 token 撤销和登录态控制。
- 还支持 ticket 方式，用于一次性或临时凭证场景。
- `TokenRefreshFilter` 在认证后处理 token 刷新。

聊天接口使用 SSE：

- `AiController.streamChat(...)` 产出 `MediaType.TEXT_EVENT_STREAM_VALUE`。
- Service 层返回 `Flux<ServerSentEvent<String>>`。
- 先发送 metadata 事件，再持续发送 message 事件。
- 如果异常，会返回 error 事件，前端可以按事件类型处理。

模型管理方面，`AiModelRegistry` 会从 `AiModelProperties` 读取配置，创建多个 `OpenAiStreamingChatModel`。这里使用 OpenAI-compatible 的方式接入模型，业务层只关心 modelId，不需要知道每个模型如何构造。`/api/ai/models` 给前端返回模型元数据，`/api/ai/chat` 中的请求会携带模型选择。

### 1 分钟回答

> 项目的聊天接口不是匿名开放的。WebFlux Security 会验证 JWT 或 ticket，JWT 的 jti 存在 Redis 里，能支持 token 撤销和登录态校验。普通用户和管理员接口也做了角色隔离。聊天返回使用 SSE，后端返回 `Flux<ServerSentEvent<String>>`，先发 metadata，再持续推送 message，异常时发 error。模型侧由 `AiModelRegistry` 统一管理，从配置创建多个 `StreamingChatModel`，前端通过 `/api/ai/models` 获取模型列表，聊天时只传 modelId。

### 高频追问

- 为什么聊天接口用 SSE？
  大模型输出天然是流式的，SSE 可以边生成边返回，前端体验比等待完整结果更好，协议也比 WebSocket 更轻。

- SSE 事件怎么组织？
  当前先发 `metadata`，包含 conversationId 和 mode；模型输出按 `message` 事件分块返回；异常用 `error` 事件返回。

- JWT 为什么要配合 Redis？
  只校验 JWT 签名无法主动撤销 token。把 jti 放 Redis 后，可以通过删除 key 控制 token 是否仍然有效。

- 管理员和普通用户怎么隔离？
  Security 配置里管理员路径要求 `ROLE_ADMIN`，业务接口中也会传入 `adminRequest`，用于知识库和文档访问判断。

- 模型为什么要注册中心？
  模型配置集中在 `AiModelProperties`，`AiModelRegistry` 负责初始化和查询，业务层只按 modelId 获取模型，便于扩展多个模型。

### 源码锚点

- `src/main/java/org/example/ai_agent/config/security/SecurityConfig.java`
- `src/main/java/org/example/ai_agent/filter/TokenRefreshFilter.java`
- `src/main/java/org/example/ai_agent/util/JwtUtil.java`
- `src/main/java/org/example/ai_agent/controller/AiController.java`
- `src/main/java/org/example/ai_agent/service/registry/AiModelRegistry.java`
- `src/main/java/org/example/ai_agent/dto/chat/ChatRequest.java`

---

## 面试总讲法

如果面试官让你完整介绍项目，可以按这个顺序讲：

1. 项目定位：这是一个基于 Spring Boot、WebFlux、LangChain4j 的通用 AI Assistant。
2. 用户能力：支持多轮聊天、流式输出、模型选择、知识库问答、文档上传和后台管理。
3. 主链路：`AiController -> AiChatServiceImpl -> GeneralAssistantFactory -> GeneralAssistant`。
4. AI 能力：统一 Assistant 抽象，挂载 Memory、RAG 和 Tool Calling。
5. 工程能力：RabbitMQ 异步文档流水线，Redis 管理 Memory 和 token 状态，Elasticsearch 存储向量和搜索数据。
6. 安全和边界：JWT 鉴权、管理员角色隔离、SSE 异常事件、RAG 当前是 Naive RAG，后续可扩展 hybrid retrieval、rerank 和 citation。

完整 2 分钟版本：

> 我做的是一个通用 AI Assistant 系统，后端基于 `Spring Boot 3 + WebFlux + LangChain4j`，前端是 Vue3。它的核心不是简单调用大模型 API，而是围绕 Assistant 主链路整合了会话、Memory、RAG、Tool Calling 和流式输出。用户可以普通聊天，也可以选择知识库进行文档问答。
>
> 后端入口是 `/api/ai/chat`，Controller 只负责请求、鉴权和 SSE 响应，核心编排在 `AiChatServiceImpl`。它会处理会话创建、用户消息落库、RAG 上下文检索、Assistant 调用和 AI 回复保存。模型调用通过 `GeneralAssistantFactory` 创建 LangChain4j 的 `GeneralAssistant`，同时挂载按 conversationId 隔离的 Memory 和知识库相关 Tools。
>
> RAG 这块分成离线和在线两条链路。离线是文档 ingestion，经过解析、分块、向量化和写入 Elasticsearch；在线是在用户提问时检索相关片段，再把上下文注入 Assistant 输入。文档处理使用 RabbitMQ 拆成 parse、chunk、embed 几个阶段，并配置 retry queue 和 DLQ，避免长任务阻塞上传接口。
>
> 安全上项目使用 WebFlux Security 和 JWT，token 的 jti 存 Redis，可以支持登录态校验和撤销；管理员接口通过 `ROLE_ADMIN` 隔离。整体上，这是一个具备 AI 主链路、知识库问答、异步文档处理和基础安全控制的 Java AI 应用。

## 面试时要主动承认的边界

- RAG 当前以向量检索加上下文注入为主，还不是完整的高级 RAG 评估体系。
- Tool Calling 目前偏查询类和状态类，适合演示模型访问系统内部能力，但还需要继续加强审计、超时和权限细化。
- Memory 当前是窗口记忆，不是长期记忆系统；后续可以做摘要记忆或用户画像记忆。
- 文档处理已经有异步队列和状态记录，但生产级还可以继续补充可视化任务面板、补偿任务和更完整的告警。
- SSE 已经能改善输出体验，但前端还可以继续增强重连、取消生成和消息级状态展示。

## 最容易被问到的 10 个问题

1. 这个项目和普通聊天 Demo 的区别是什么？
   它有统一 Assistant 主链路，整合了 Memory、RAG、Tools、模型注册、SSE、鉴权和异步文档处理，不只是调用一个模型接口。

2. RAG 的完整流程是什么？
   离线 `parse -> chunk -> embed -> store`，在线 `retrieve -> build context -> assistant answer`。

3. 为什么同时用 MySQL、Redis、Elasticsearch？
   MySQL 存业务数据和完整历史，Redis 存短期 Memory 和 token 状态，Elasticsearch 存向量和搜索索引。

4. Memory 和 History 有什么区别？
   History 是用户可见的完整记录，Memory 是模型实际消费的上下文窗口。

5. 为什么文档处理要用 RabbitMQ？
   文档解析、分块和 embedding 是长任务，异步队列能解耦阶段、支持重试和失败追踪。

6. Tool Calling 解决什么问题？
   让模型在对话中查询系统真实数据，例如知识库列表和文档处理状态，而不是靠 prompt 猜测。

7. 如何保证不同用户的知识库隔离？
   业务层按用户和管理员身份判断访问权限，向量 metadata 中也带有 knowledgeBaseId、documentId、chunkId，用于检索过滤和管理。

8. 为什么用 SSE？
   大模型输出适合流式返回，SSE 简单、轻量，适合服务端持续推送文本片段。

9. 模型是怎么切换的？
   `AiModelRegistry` 根据配置注册多个 `StreamingChatModel`，前端拿到模型元数据后在请求里传 modelId。

10. 你觉得项目后续最值得优化哪里？
    RAG 检索质量和可解释性最值得优化，包括 hybrid retrieval、rerank、citation、评估集和失败样例分析。
