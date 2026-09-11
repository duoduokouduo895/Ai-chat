# AI-chat：核心可靠性能力说明

这页按“问题—机制—取舍—证据”描述当前源码已经实现且有验收证据的能力。它用于项目展示和面试解释，不把真实模型效果、生产迁移或部署写成已完成成果。

| 问题 | 机制 | 取舍与限制 | 证据 |
|---|---|---|---|
| 网络重试造成重复回答，消息与回合状态不一致 | 持久 ChatTurn、幂等键、会话活动占位；消息、来源与 `COMPLETED` 在同一事务提交，终态按 turn ID 条件释放占位 | 模型调用期间不持有数据库事务；极端终态写入失败由过期恢复任务收敛 | ChatTurn 隔离 MySQL 14 场景；`ChatTurnLifecycleTest` |
| Redis 过期或切换模型后历史丢失 | MySQL 的完整已完成回合作为事实源；请求内 memory；跨模型共享历史；失败/取消回合排除 | 旧消息只做可证明的成对恢复，孤立消息不猜测；token 为模型对应估算值 | `ConversationContextLoaderTest`；真实 MySQL 跨模型与旧消息恢复场景 |
| 超长上下文把关键问题挤出窗口 | 统一预算系统提示、历史、问题、RAG、工具描述和累计工具返回；整回合淘汰旧历史并按相关性裁剪资料 | 当前问题优先且不静默截断；真实供应商用量单独报告 | `InputBudgetStreamingChatModelTest`；聊天服务预算边界测试 |
| 文档最后一批向量完成后故障，状态永远卡住或重试重复写向量 | 可重复收尾操作核对 chunk 事实，以稳定版本/chunk ID 写向量，搜索投影成功后才完成阶段 | 空文档显式完成但 embedding 为 `SKIPPED`；外部部分失败仍通过相同步骤补偿 | `DocumentProcessingServiceImplTest`、`EmbeddingServiceImplTest`；真实 MySQL 投影失败补偿场景 |
| 新版本构建失败会覆盖可用知识 | parsing、chunk、向量和 BM25 按 version/generation 隔离；阶段完成后才原子切换 `active_version_id`；租约 token 阻止迟到 worker | 生产开关默认关闭；旧版本在激活后由后台任务回收，激活事务不等待外部删除 | IngestionRun 租约 MySQL 4 场景、命令链 MySQL 17 场景、V10 迁移防护 |
| 删除请求超时或外部存储失败，资源半删半留 | 事务内逻辑删除并受理持久清理任务，立即从用户视图隐藏；后台分步、幂等、退避清理并提供管理员重试 | 物理清理是异步的；超过上限保持隐藏并进入 `NEEDS_ATTENTION` | 202 API 契约测试、删除命令/worker 测试、真实 MySQL 重复删除与失败恢复场景 |
| “没有资料”与检索故障混为一谈 | `FOUND/EMPTY/DEGRADED/UNAVAILABLE` 和逐通道状态；EMPTY 固定资料不足回答，UNAVAILABLE 返回可重试错误，两者都不调用模型 | 有候选不等于结论充分；提示词仍要求证据不足时说明 | retrieval outcome、hybrid retrieval、RAG 与 chat service 测试 |
| 刷新后来源丢失，或权限撤销后仍泄露旧正文 | 回合保存来源标识快照；读取时复核权限、删除状态、活动版本和 chunk 存在性；界面称“本轮参考资料” | 不宣称逐句引用已经验证；资源回收后显示来源不可用 | ChatTurn 真实 MySQL 来源保存/刷新/撤权场景；来源组件桌面与移动端 QA |
| SSE 连接结束被误认为回答已经保存 | metadata 标识协议版本，提交成功后发送 retrieval/complete；新协议缺少 complete 视为中断，旧协议兼容 EOF | 未知事件安全忽略；网络中断可使用相同幂等键恢复 | 前后端 SSE 契约测试、普通前端 unit tests |
| ES 故障令内存补偿任务无限增长，旧结果仍可见 | bounded pending、rebuild-required 标志、合并补偿、启动分页对账；返回前用 MySQL 复核归属、存在与删除状态 | 接受对账前短暂遗漏，本轮没有新增持久搜索 Outbox | 队列与搜索服务测试；隔离 ES 索引删除/重建探针 |
| 评测分数上涨却无法解释 | chunk Precision 与 marker coverage 分离，调用错误单列，固化语料/配置/代码/工作区摘要 | 关键词和拒答短语只做自动初筛，人工项单独呈现 | rag-eval 指标测试 6 项 |

面试时可以围绕三个提交边界说明设计：数据库事务负责受理与状态真相，带租约的 worker 负责可接管执行，外部资源通过稳定标识和分步进度获得幂等补偿。这样可以具体解释故障发生后系统如何判断“已经做了什么”、为什么重试不会覆盖当前可用版本，以及资源为何会先对用户隐藏再后台物理清理。

验收数量、隔离清单和保留限制见 [核心可靠性验收记录](reliability-acceptance.md)，版本/处理契约见 [Knowledge Ingestion](knowledge-ingestion.md)，聊天上下文契约见 [Conversation Context](conversation-context.md)。
