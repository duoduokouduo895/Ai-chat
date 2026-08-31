# ADR-0001：采用 Maven 多模块单体

## 状态

已接受。

## 决策

AI-chat 保持一个 Spring Boot 进程、一个 MySQL 数据库和一个部署单元。Maven Module 用于限制依赖方向和集中领域复杂度，不拆分为内部网络调用的微服务。

`kernel` 不依赖框架或基础设施；Identity、Conversation、Knowledge、Search 定义领域 Interface；Redis、RabbitMQ、Elasticsearch、OSS 和 AI Provider 作为 Adapter；Web 只保留 HTTP/SSE 与认证映射。

## 后果

可获得明确的编译期依赖和测试 seam，而不引入分布式事务、服务发现、远程失败和独立发布成本。跨 Module 的修改需要保持公开 Interface 兼容。
