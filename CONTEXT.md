# AI-chat 领域词汇

## RequestActor

一次请求的强类型主体。`USER`、`GUEST`、`ADMIN` 与数值 ID 共同构成身份；领域 Module 不读取 HTTP 认证对象或 Redis key。

## ChatTurn

一次用户请求触发的完整聊天回合。它拥有幂等键、用户消息、模型生成状态、AI 消息、失败和取消语义；消息去重本身不等同于 ChatTurn 幂等。

## DocumentVersion

同一文档一次可独立激活的解析和向量化产物。重处理先构建新版本，只有完成后才替换当前激活版本。

## IngestionRun

围绕一个 DocumentVersion 的可恢复异步处理执行。它拥有 generation、stage、retry、lease token 和 lease until；RabbitMQ 只传递工作，不拥有其状态。

## RetrievalContext

一次 RAG 检索中经过权限过滤、召回、融合、Rerank 和预算后的结构化证据，不是仅供提示词拼接的字符串。

## Citation

RetrievalContext 中可追溯到知识库、文档和 chunk 的单条证据。

## SearchProjection

由 MySQL 事实数据派生的 Elasticsearch 搜索文档。它可重建，不能成为对话或知识事实的唯一来源。
