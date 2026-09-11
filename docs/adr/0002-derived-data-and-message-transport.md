# ADR-0002：事实数据、派生数据与消息传递

## 状态

已接受。

## 决策

MySQL 保存业务事实。Redis 保存短生命周期会话、配额和 Token 状态；Elasticsearch 保存可重建 SearchProjection；RabbitMQ 只传递异步工作。Outbox 是数据库提交后的待投递事实，Broker Confirm 只表示 Broker 已接收消息。

文档重处理必须构建新的 DocumentVersion/IngestionRun，成功后原子激活；不得为了重建而先销毁当前可用版本。
