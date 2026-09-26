# AI-chat RAG 效果评测工具包

本目录用于可重复执行 RAG 效果评测，与 `perf/` 性能压测分离。首版使用 8 份可控产品手册和 120 条标准问题，区分检索失败、生成失败、疑似幻觉和外部服务错误。

## 1. 前置条件

- 本地后端已启动，默认地址为 `http://127.0.0.1:8080`。
- Elasticsearch、Redis、RabbitMQ、Nacos 和外部 Embedding API 可用。
- Python 3.10 或更高版本可用，仅使用标准库。
- Nacos 中 OSS 凭证应使用有效的临时或已轮换凭证，不要将导出配置提交到 Git。

设置运行参数：

```powershell
$env:RAG_EVAL_BASE_URL = "http://127.0.0.1:8080"
$env:RAG_EVAL_MODEL = "chat-fast"
$env:PYTHONUTF8 = "1"
```

所有 HTTP 脚本都会显式绕过本机代理。

## 2. 准备专用知识库

```powershell
pwsh -NoProfile -File .\rag-eval\scripts\setup.ps1
```

脚本会：

1. 注册一个 `rag_eval_时间戳` 普通用户并登录。
2. 创建一个私有知识库。
3. 上传 `dataset/corpus/` 中的 8 份 Markdown。
4. 轮询文档向量化状态，全部完成后退出。
5. 将本轮 Token、账号、知识库 ID 和文档 ID 写入 `rag-eval/local/`。

`rag-eval/local/` 已加入 `.gitignore`，不要提交或分享其中内容。

## 3. 运行检索评测

```powershell
python .\rag-eval\scripts\run-retrieval-eval.py
```

脚本对 120 条问题执行三组参数：

| 配置 | `maxResults` | `minScore` |
|---|---:|---:|
| `baseline` | 5 | 0.3 |
| `strict` | 5 | 0.7 |
| `wide` | 10 | 0.3 |

结果写入 `rag-eval/results/retrieval-时间戳.json` 和 `.csv`。

## 4. 运行生成评测

```powershell
python .\rag-eval\scripts\run-generation-eval.py
```

脚本对固定分层抽样的 40 条问题各运行 3 轮，调用统一入口 `/api/ai/chat`。完成条件是 SSE 流正常结束并至少收到一条 `message` 事件；首条 `message` 的到达时间记为 TTFT。

可调整单次 SSE 超时：

```powershell
$env:RAG_EVAL_SSE_TIMEOUT_SECONDS = "120"
```

## 5. 生成报告

```powershell
python .\rag-eval\scripts\build-report.py
```

报告写入 `rag-eval/results/report-时间戳.md`。`POSSIBLE_HALLUCINATION` 只表示需要人工复核，不代表脚本已经自动证明模型出现幻觉。

## 6. 展示并清理测试资源

先展示清单：

```powershell
pwsh -NoProfile -File .\rag-eval\scripts\cleanup.ps1
```

确认清单无误后，使用临时管理员 JWT 删除：

```powershell
$env:RAG_EVAL_ADMIN_TOKEN = "临时管理员 JWT"
pwsh -NoProfile -File .\rag-eval\scripts\cleanup.ps1 -Execute
Remove-Item Env:RAG_EVAL_ADMIN_TOKEN
```

删除脚本只处理本轮 manifest 中记录的精确知识库 ID 和用户名。完成后会删除本地敏感 manifest，保留 `rag-eval/results/` 中的报告。

## 7. 重新生成问题集

问题集由确定性脚本生成：

```powershell
python .\rag-eval\dataset\build_dataset.py
```

生成器会断言题型分布、总题数 `120` 和生成层抽样数 `40`。

## 8. 长会话记忆评测

`run-memory-eval.py` 针对滚动摘要、关键约束和原文回读：每个场景在独立会话中先写入事实或约束，再用若干轮长回答的铺垫话题把早期内容挤出输入预算，等待后台压缩后再追问。

```powershell
$env:MEMORY_EVAL_FILLER_TURNS = "10"     # 铺垫轮数，需让历史超过模型输入预算
$env:MEMORY_EVAL_SETTLE_SECONDS = "20"   # 追问前等待后台压缩的秒数
$env:MEMORY_EVAL_LABEL = "memory-on"
python .\rag-eval\scripts\run-memory-eval.py
```

场景定义在 `dataset/memory_conversations.jsonl`，分为 `constraint`、`constraint_update`、`detail_recall`、`coreference` 和 `injection` 五类。只有全部期望关键词命中、且没有出现禁止词时，才判为通过。`injection` 场景用来检查引用给模型的内容是否被误当成指令固化进记忆。

对照方式：后端设置 `ASSISTANT_MEMORY_ENABLED=false` 并把 `MEMORY_EVAL_LABEL` 改为 `memory-off` 再跑一次，比较两份 `results/memory-*.json` 的 `passRate` 与 `byCategory`。脚本会以 `saveMessage=true` 创建真实会话，评测后可按 `conversationId` 清理。
