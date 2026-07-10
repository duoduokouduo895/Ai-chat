# AI-chat 模型目录手册

## 模型配置

[FACT-MODEL-001] 模型目录由 Nacos 配置维护。每个模型应显式声明 `type`，聊天模型使用 `chat`，向量模型使用 `embedding`。

[FACT-MODEL-002] 当前默认聊天模型别名是 `chat-fast`，适合通用对话。代码辅助模型别名是 `chat-coding`。

[FACT-MODEL-003] 当前默认 Embedding 模型别名是 `embedding-default`，用于将文档 Chunk 和用户问题转换为向量。

[FACT-MODEL-004] 如果旧模型配置缺少 `type`，服务会临时按 `CHAT` 类型兼容加载并输出迁移警告，但仍应尽快修复 Nacos 配置。
