# AI-chat 故障处理手册

## 常见问题

[FACT-TROUBLE-001] 如果知识库检索没有返回预期结果，应先确认文档解析和向量化是否完成，再检查 Chunk 切分、Embedding 模型、Top-K 和相似度阈值。

[FACT-TROUBLE-002] 如果正确证据已经进入 Top-K，但最终回答仍然错误，应优先检查 Prompt、模型理解能力和幻觉问题。

[FACT-TROUBLE-003] 如果没有相关证据但模型仍给出确定答案，应标记为疑似幻觉，并交由人工复核。

[FACT-TROUBLE-004] 如果 HTTPS 页面请求 HTTP API，浏览器可能因 mixed-content 策略阻止请求。生产前端应优先使用相对路径 `/api`。
