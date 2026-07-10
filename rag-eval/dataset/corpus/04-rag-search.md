# AI-chat RAG 检索手册

## 检索行为

[FACT-RAG-001] RAG 检索会先将问题转换为 Embedding，再到 Elasticsearch 向量索引中执行相似度搜索。

[FACT-RAG-002] 主聊天链路默认最多取 5 个相关 Chunk，默认最小相似度为 0.3。知识库独立搜索接口的默认值可以更严格。

[FACT-RAG-003] 检索结果包含 `chunkId`、`documentId`、`knowledgeBaseId`、文本内容、相似度分数和 Chunk 序号。

[FACT-RAG-004] 当前版本没有启用 Rerank。召回结果会按相似度分数从高到低排序，再截取 Top-K。
