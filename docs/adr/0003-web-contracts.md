# ADR-0003：两个前端独立发布并共享契约

## 状态

已接受。

## 决策

普通前端与管理前端保留独立 Router、状态与发布产物。后续通过 `@ai-chat/web-contracts` 共享 HTTP contract、错误模型和 SSE Interface；共享包不持有浏览器 Token 或直接访问存储。

现有 URL、HTTP 状态和 SSE 事件名称保持兼容；新字段必须可选。
