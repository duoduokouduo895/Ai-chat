# 核心可靠性实施记录

本轮接续工作区中已有的 ChatTurn、DocumentVersion、IngestionRun 和租约改动，不重写既有迁移，不部署生产。实现按 A → B → C → D → E 收敛，并只整理这些链路涉及的模块职责。

## A：文档收尾与评测口径

- embedding 使用稳定的版本/chunk 标识写入。批次已经完成后重试不会再次调用 embedding；搜索投影没有成功前不提交整个阶段成功。
- 收尾操作重新核对 chunk 事实。全部完成时补齐状态和投影；失败或未完成时拒绝完成；合法空文档记录为空内容完成，embedding 阶段为 `SKIPPED`。
- 查询失败不再被解释为空集合。最后批次提交后故障、搜索收尾故障与外部部分写入均有回归场景。
- 评测分别输出 returned-chunk Precision、marker evidence coverage 和 requested K。外部错误单列，不计为正确拒答或正常无结果；报告保存语料、配置、代码和工作区摘要，关键词只作为生成初筛。

## B：版本化与可靠删除

- `DocumentVersion` 与 `IngestionRun` 统一初次上传、重新解析和重新向量化。同文档同动作重复受理返回原 run，不同活动动作冲突；每次阶段提交都校验 lease token。
- 解析、chunk、向量和搜索投影按 generation/version 隔离。重新向量化复制当前版本的解析产物与 chunk，构建新向量；构建中和退役版本不参与普通召回。
- V10 在检查空 generation、legacy 活动任务和活动 run 后升级 chunk 唯一约束。激活只在所需阶段和搜索投影可用后于 MySQL 事务内切换 `active_version_id`，并受理旧版本清理任务。
- legacy generation 1 的兼容读取只适用于尚未进入多版本流程的文档。普通读取、计数与投影使用相同条件，避免激活基线版本后旧数据突然消失，也避免多版本受理后继续写回 legacy 路径。
- 文档和知识库删除在事务内完成权限检查、逻辑删除、活动 run 取消和清理任务受理，HTTP 返回 202。普通列表、下载、搜索、RAG 和统计立即排除删除中资源。
- 后台 worker 按步骤清理 OSS、各版本向量、BM25 与数据库关联；不存在即成功。任务等待活动租约，执行末尾再次清理并做残留检查，临时失败退避重试，超过上限进入 `NEEDS_ATTENTION`。管理员查询与重试仅返回安全状态和错误码。

## C：聊天上下文与并发

- `ai-chat-conversation` 中的 `ConversationContext` 是领域 Interface；应用 Adapter 从 MySQL 加载完整 `COMPLETED` 回合。请求内 memory 构建模型输入，Redis 不再充当跨请求历史事实源。
- 历史跨模型共享，失败、取消、处理中回合被排除。ChatTurn 引入前的消息只恢复可按时间和 ID 稳定配对的 user/ai；孤立消息仍可展示但不猜测为完整回合。
- 历史只保存原始问题和最终回答。RAG 资料只注入本轮，避免旧增强提示反复累积。
- 预算包含系统提示、完整历史、本轮问题、检索资料、工具描述，以及工具执行后累积返回内容。优先保留本轮问题，按完整回合淘汰最旧历史，再按相关性裁剪资料；问题本身超预算时明确拒绝。
- 会话活动回合占位由数据库条件 claim。同会话并发返回 `CHAT_CONVERSATION_BUSY`；幂等键仍支持完成重放和处理中判定。完成、失败、取消及过期恢复都按 turn ID 条件释放。
- 整回合 `max-duration-ms` 与流式 idle timeout 独立；恢复窗口通过配置校验保证大于整回合上限。模型调用期间不持有数据库事务。

## D：RAG 状态、来源和前端

- 检索返回 `FOUND`、`EMPTY`、`DEGRADED`、`UNAVAILABLE` 及通道状态、候选和耗时。关闭通道不计失败；已启用 rerank 但配置缺失会记为失败并使用 RRF 候选，因此结果为降级而非正常关闭。
- `EMPTY` 保存固定资料不足回答并完成回合，不调用模型；`UNAVAILABLE` 返回可重试错误，不调用模型；有证据时才进入生成，并要求模型在证据不足时明确说明。
- ChatTurn 持久保存检索状态和来源标识。刷新和幂等重放恢复来源时重新检查权限、删除状态、活动版本与 chunk 是否存在；失效来源只显示不可用，不回传旧正文。
- SSE metadata 带可选协议版本，新增 retrieval 与 complete。complete 只在最终回答和回合状态提交成功后发送；新前端要求新协议有 complete，旧协议保留 EOF 兼容，未知事件忽略。
- 普通前端提供紧凑来源区、降级/资料不足和后台清理提示；管理端提供删除任务状态和幂等重试反馈。修改沿用既有 Token 与布局。

## E：搜索兜底与模块约束

- `pendingTasks` 复用队列容量并设置上限。溢出时标记需要重建并记录指标；多个失败事件只合并为一个补偿重建。
- 保留每日重建并增加启动后 MySQL ID 游标分页对账。搜索返回前复核 MySQL 的存在性、归属和删除状态，接受启动/定时对账前的短暂遗漏。
- ingestion 决策、聊天上下文与 retrieval outcome 契约分别进入对应领域 Module，基础设施通过 Adapter 接入；ArchUnit 约束这些方向，没有迁移无关 Controller、身份或管理模块。

## 配置与兼容

- 版本化生产默认关闭：`knowledge-base.processing.versioned-ingestion-enabled=false`；`application-reliability-it.properties` 只在测试中开启。
- 文档/知识库 DELETE 路径保持，返回 202 及 `operationId/status`。重处理响应追加可选 `runId/versionId/generation`。
- 搜索结果保留旧字段并追加检索与通道状态；历史消息使用可选来源扩展；处理消息新增字段走兼容路径。
- `assistant.chat-turn.max-duration-ms`、恢复窗口、输入预算、模型估算器和测试资源命名均由配置提供，没有写入生产 Nacos。

## 验证状态

当前源码的本地后端全量、真实 MySQL 分项、真实四中间件隔离探针、评测、两个前端构建、普通前端协议测试、组件视觉/键盘检查及 whitespace 检查均已通过。确切数量、清单和限制见 [核心可靠性验收记录](reliability-acceptance.md)。

家庭服务器业务实例已经完成本轮候选更新和 V6→V10 迁移；真实模型效果、云端生产部署与完整迁移演练不属于本轮完成声明。完整登录后的浏览器业务旅程和不中断业务容器前提下无法安全制造的真实网络断连，作为后续发布门禁保留。
