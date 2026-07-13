# AI-chat 云端发布 Module

第一版发布只使用一份 `compose.cloud.yaml`。候选和生产的差异来自 root-owned runtime env，
并由 `cloud-activate.sh` 在 Docker 构建前做强校验，避免把候选环境的“禁写配置”误当成生产。

## V1 版本矩阵

| 范围 | 固定版本 | 说明 |
|---|---:|---|
| Java / Maven | Temurin 21.0.11+10 / Maven 3.9.16 | 本地编译、Docker builder 和容器 runtime 统一 Java 21 |
| Spring Boot / Cloud | 4.0.7 / 2025.1.2 | 使用受支持的 Oakwood release train，不再使用已 EOL 的 2023.x/2025.0.x |
| Spring Cloud Alibaba | 2025.1.0.0 | 与 Boot 4、Cloud 2025.1 对齐 |
| Nacos Client | 3.1.1 | 由 Alibaba BOM 管理，不手工覆盖 |
| Elasticsearch | server / IK 9.3.2；Java client 9.2.8；legacy REST client 9.3.1 | server 与 IK 精确一致；客户端保持 BOM 解析结果 |
| LangChain4j | 1.15.1 / Spring `1.15.1-beta25` | 使用 Boot 4 专用 starter；beta 仅指 Spring 集成层 |
| OTel Java Agent | 2.29.0 | 只导出 Trace，Metrics 和日志仍走既有通道 |

这些值同时记录在 `versions.env`；依赖版本以 Maven BOM 的实际解析结果为准。隔离 Nacos 3.2.2
已经完成配置读取、鉴权、`ai-chat-candidate` 服务注册和受控停止/恢复重连验收；现有 Nacos 2.1.1
仍作为旧实例回滚链，未被替换。Nacos 2.1.1 到 3.x 涉及数据库结构变化，禁止直接替换生产容器。

Spring Data Elasticsearch 6 使用 Elasticsearch 9 client，不能连接现有共享 Elasticsearch 8.13.4。
V1 先连接独立、带认证与 IK 9.3.2 的候选；现有 8.13.4 容器和数据卷不参与原地升级。
候选已通过真实业务索引、IK Mapping、中文检索、dense_vector KNN 与 Guest SSE 验收；生产切换后
仍需补真实知识库上传与 RAG 检索回归。

Spring Boot 4 默认转向 Jackson 3；当前 RabbitMQ、Redis 和部分 LangChain4j 链路仍通过
`spring-boot-jackson2` 使用 Jackson 2 兼容桥。该桥已被标记为后续迁移债务，但 V1 不应在
没有消息兼容性与缓存回放测试的情况下强行切到 Jackson 3。

## 两种模式

| 模式 | 作用 | 写任务 | 默认 loopback 端口 | 活动链接 |
|---|---|---|---|---|
| `candidate` | 隔离 Nacos 3 鉴权、注册、启动、依赖、指标、Trace 与脱敏日志验收 | Flyway/listener/scheduler 等业务写任务关闭 | `18082` / `19091` | `/opt/ai-chat/candidate` |
| `production` | 正式业务实例 | Flyway、RabbitMQ listener、定时任务和索引同步开启 | `18083` / `19092` | `/opt/ai-chat/current` |

两个端口都只绑定 `127.0.0.1`。脚本不会修改 Nginx 或 DNS，也不会自动停止旧服务器。
`AICHAT_SERVICE_NAME` 同时驱动 `SPRING_APPLICATION_NAME`、AIDiag service name 与 OTel service name，
避免 Nacos 注册、指标、日志和 Trace 使用不同服务标识。

候选与生产必须使用不同的 `AICHAT_SECRETS_DIR`：候选固定为 `/opt/ai-chat/runtime/.secrets/candidate`，生产固定为 `/opt/ai-chat/runtime/.secrets/production`。Nacos 与 Elasticsearch 各自使用独立的 username/password file Secret；不得复制或覆盖同一组凭据来切换模式，否则轮换会同时破坏另一条回滚链。
生产模式只有在 `AICHAT_PRODUCTION_CONFIRMED=true` 时才会启动；这个值表示维护窗口已经开始，
旧实例中的 listener、scheduler 和其他写任务已经停止，避免双写。

## 安全边界

- AIDiag、Nacos 和 Elasticsearch 密钥只通过 Compose file secrets 挂载，不写入镜像、runtime env 或 `docker inspect`。
- `docker-entrypoint.sh` 只在容器内部读取 Nacos/Elasticsearch 文件密钥并导出给 Java 进程。
- 根文件系统保持只读；Nacos 客户端快照与内部日志只写入有大小上限、容器销毁即清空的 tmpfs。
- 生产模式强制要求 Nacos 鉴权；候选模式可以在鉴权切换前暂时使用匿名 Nacos，切换后应改为 `true`。
- 启动失败只收集 `ai-chat-backend` 最后 500 行日志，先脱敏再写入有大小上限的独立文件。
- 健康门禁失败会恢复同模式的上一个 immutable release，不会触碰另一个模式或 DNS。

## 云端目录

~~~text
/opt/ai-chat/releases/<release-name>       immutable source release
/opt/ai-chat/runtime/candidate.env         候选非密钥配置，mode 600/640
/opt/ai-chat/runtime/production.env        生产非密钥配置，mode 600/640
/opt/ai-chat/runtime/.secrets/candidate/   候选 Nacos 文件密钥，uid/gid 10001:10001、mode 400
/opt/ai-chat/runtime/.secrets/production/  生产 Nacos 文件密钥，uid/gid 10001:10001、mode 400
/opt/ai-chat/candidate                     当前候选 release 软链接
/opt/ai-chat/current                       当前生产 release 软链接
~~~

首次准备 runtime 文件：

~~~bash
install -d -m 700 /opt/ai-chat/runtime /opt/ai-chat/runtime/.secrets
install -m 600 deploy/candidate.env.example /opt/ai-chat/runtime/candidate.env
install -m 600 deploy/production.env.example /opt/ai-chat/runtime/production.env
~~~

候选部署：

~~~bash
bash deploy/cloud-stage-candidate.sh /opt/ai-chat/releases/<release-name>
~~~

生产预备文件中的 `AICHAT_PRODUCTION_CONFIRMED` 必须保持 `false`。只有维护窗口内确认旧实例停止写入后，
才改为 `true` 并执行：

~~~bash
bash deploy/cloud-activate-production.sh /opt/ai-chat/releases/<release-name>
~~~

生产容器健康只代表新进程可工作，仍需完成真实登录、SSE Chat、知识库、管理端、指标、日志、Trace、
告警和回滚演练，之后才能切换 Nginx/DNS。
