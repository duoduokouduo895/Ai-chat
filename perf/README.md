# AI-chat 本地压测包

本目录用于生成可复现的本机压测基线。测试目标是得到面试可讲、可解释的数据，不代表生产极限容量。

## 1. 环境变量

在 PowerShell 7 中执行：

```powershell
$env:PERF_BASE_URL = "http://127.0.0.1:8080"
$env:JMETER_HOME = "E:\Jmeter\apache-jmeter-5.6.3"
$env:PERF_ADMIN_TOKEN = "临时管理员 JWT"
```

使用 `127.0.0.1` 是为了避免本机 HTTP 代理影响结果。管理员 JWT 只用于读取监控指标和删除本轮测试账号，不写入文件。

## 2. 创建专用账号

```powershell
pwsh -NoProfile -File .\perf\scripts\setup-users.ps1 -Count 50
```

脚本会在 `perf/local/` 下生成本轮清单和 JMeter CSV。该目录已加入 `.gitignore`。

## 3. 采集压测前快照

```powershell
pwsh -NoProfile -File .\perf\scripts\metrics-snapshot.ps1 -Label before
```

## 4. 运行普通 API 阶梯压测

先跑小流量：

```powershell
pwsh -NoProfile -File .\perf\scripts\run-baseline.ps1 -Scenarios me,conversation,search,knowledge-list -Threads 1,5,10
```

默认每次业务请求前等待 `100ms`，模拟基本用户思考时间，并避免本机发压工具先耗尽临时端口。可用 `-ThinkTimeMs` 调整。

确认错误率低于 `1%` 且服务健康后，再继续：

```powershell
pwsh -NoProfile -File .\perf\scripts\run-baseline.ps1 -Scenarios me,conversation,search,knowledge-list -Threads 20,50
```

知识库向量检索会调用外部 embedding API，单独低压运行：

```powershell
pwsh -NoProfile -File .\perf\scripts\run-baseline.ps1 -Scenarios knowledge-search -Threads 1,5,10
```

## 5. 运行 SSE 聊天专项

真实模型测试会产生外部 API 调用。默认只运行低并发短回答：

```powershell
pwsh -NoProfile -File .\perf\scripts\sse-benchmark.ps1 -ConcurrencyLevels 1,3,5,10 -RequestsPerLevel 10
```

脚本记录 TTFT、完整生成耗时、完成率和失败原因。需要测试 RAG 时，额外传入知识库 ID：

```powershell
pwsh -NoProfile -File .\perf\scripts\sse-benchmark.ps1 -KnowledgeBaseIds 1
```

## 6. 收尾与清理

```powershell
pwsh -NoProfile -File .\perf\scripts\metrics-snapshot.ps1 -Label after
pwsh -NoProfile -File .\perf\scripts\cleanup.ps1
```

`cleanup.ps1` 默认只展示本轮账号。确认清单无误后再执行：

```powershell
pwsh -NoProfile -File .\perf\scripts\cleanup.ps1 -Execute
```

删除脚本只接受本轮 manifest 中的账号，并逐个精确匹配用户名。不会按模糊前缀批量删除。

## 7. 停止条件

遇到以下任一情况时停止继续升压：

- 错误率超过 `1%`。
- 当前 P95 超过 1 并发基线的 3 倍。
- `/actuator/health` 不再返回 `UP`。
- 外部模型出现限流、费用异常或持续超时。

结果记录模板见 [result-template.md](./result-template.md)。
