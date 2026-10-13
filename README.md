# AI-chat

后端是单模块 Spring Boot 工程，启动类为 `org.example.ai_agent.AiAgentApplication`。

在 PowerShell 7 中从仓库根目录直接启动后端：

```powershell
mvn spring-boot:run
```

需要先打包再启动 JAR 时，也可以使用 `.\scripts\run-backend.ps1`；只打包时加上 `-BuildOnly`。产物位于 `target/ai-chat-1.0.0.jar`。应用启动仍需要相应的本地配置和外部依赖。

## 仓库目录

| 目录 | 内容 |
|---|---|
| `src/` | 后端源码、配置、Flyway 迁移（`src/main/resources/db/migration`）和测试 |
| `frontend/` | 用户端 Vue 前端 |
| `frontend_admin/` | 管理端 Vue 前端 |
| `deploy/` | 云端/GCP 发布脚本与 Nginx 配置（家庭服务器部署在相邻的 `AI-ops` 仓库） |
| `scripts/` | 本地运行、外部配置准备与集成测试辅助脚本 |
| `perf/` | JMeter 压测脚本与结果 |
| `rag-eval/` | RAG 检索效果评测数据集与脚本 |
| `sql/` | 早期手工建表脚本，仅供参考；实际表结构以 Flyway 迁移为准 |

## 后端包结构

代码按业务功能分包，找某个功能时先定位功能包，再按 `controller → service → repository` 往下看。

```text
org.example.ai_agent
├── auth/       注册登录、管理员登录、JWT、游客账号（security/ 放鉴权过滤器与配置）
├── chat/       会话与流式对话：chat turn 生命周期、assistant 工具调用、memory 上下文压缩
├── knowledge/  知识库管理、混合检索与 RAG、知识资产清理（cleanup/）
├── document/   文档上传与解析、异步入库流水线（pipeline/：outbox、RabbitMQ 消费、版本化处理）
├── search/     会话全文搜索与搜索历史（Elasticsearch）
├── admin/      管理端看板、用户/搜索查询、运维与 AIDiag 集成（operations/）
├── llm/        模型目录、路由策略、重试与 Provider HTTP 客户端
└── common/     跨功能基础设施：持久化配置、消息、Web、异常、可观测性、调度、对象存储、限流准入
```

各功能包内部使用统一的子包名：`controller`、`dto`、`service`、`repository`、`entity`、`task`（定时任务）、`config`、`domain`（不依赖框架的领域类型，由 `ModuleArchitectureTest` 约束）。
