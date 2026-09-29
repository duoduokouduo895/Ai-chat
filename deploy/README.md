# AI-chat 云端发布 Module

> **状态：已停用。** GCP 云端发布流程不再维护，当前运行环境是家庭服务器（部署文件见
> `../AI-ops/deployment/home/`）。应用已移除 Nacos，改为读取只读挂载的外部配置目录：
> `application.properties` 加 `secrets/`（每个属性一个文件，Spring configtree）。本目录脚本
> 仅删掉了 Nacos 相关门禁，没有针对新配置方式重新验收；重新启用前需要完整复核。
> 下文中关于 Nacos 的内容仅作历史记录。
> 家庭服务器的 Nacos 导出转换步骤见 `../scripts/README-external-config.md`；
> 不要按旧说明直接拼接或逐行拆分 properties 文件。

第一版发布只使用一份 `compose.cloud.yaml`。候选和生产的差异来自 root-owned runtime env，
并由 `cloud-activate.sh` 在 Docker 构建前做强校验，避免把候选环境的“禁写配置”误当成生产。

## V1 版本矩阵

| 范围 | 固定版本 | 说明 |
|---|---:|---|
| Java / Maven | Temurin 21.0.11+10 / Maven 3.9.16 | 本地编译、Docker builder 和容器 runtime 统一 Java 21 |
| Spring Boot | 4.0.7 | Spring Cloud / Alibaba / Nacos Client 已随 Nacos 一并移除 |
| Elasticsearch | server / IK 9.3.2；Java client 9.2.8；legacy REST client 9.3.1 | server 与 IK 精确一致；客户端保持 BOM 解析结果 |
| LangChain4j | 1.15.1 / Spring `1.15.1-beta25` | 使用 Boot 4 专用 starter；beta 仅指 Spring 集成层 |
| Apache Tika | 3.2.3（LangChain4j 传递管理） | 不再用显式 2.9.4 覆盖 parser 模块，避免 Tika 2/3 主版本混用 |
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
生产不再连接旧 `nacos:8848`，而是连接由已验收 Nacos 3 候选晋升出的稳定别名
`nacos-v3:8848`；Elasticsearch 同理使用 `elasticsearch-v9:9200`。晋升只复用已验收容器和数据卷，
不会再复制一套占用内存的中间件，旧 Nacos 2.1.1 / Elasticsearch 8.13.4 仍保留为旧版本回滚链。

生产模式不能只靠 `AICHAT_PRODUCTION_CONFIRMED=true` 启动。还必须设置唯一的
`AICHAT_CUTOVER_ID`，并由 `cloud-preflight-production.sh --ready` 校验以下证据：旧实例写任务已停止、
`ai_agent_db` 全量备份及 SHA-256、候选应用已停止、生产 Secret 与候选不同、批准记录绑定当前 release
内容摘要。缺少任一项都会 fail closed，避免 listener、scheduler、Flyway V5 或索引同步发生双写。

旧服务器的 systemd 启动脚本、环境文件或 PID 文件如果已经丢失，不能直接执行 `systemctl stop`。
先在旧服务器运行以下命令创建 root-only 恢复包；它会复制当前 Jar，并从 `/proc/<pid>` 精确保存命令行和
进程环境，但不会停止或重启服务，也不会输出环境值：

~~~bash
bash deploy/cloud-control-legacy-writer.sh prepare <cutover-id>
~~~

恢复包位于 `/opt/ai-chat-legacy-cutover/<cutover-id>`，目录 mode 700、文件 mode 600，其中可能包含 Secret，
不得下载、提交 Git 或写入普通日志。只有 `prepare` 返回 PASS 且 Jar SHA-256 一致后，维护窗口内才允许执行：

~~~bash
AICHAT_LEGACY_STOP_CONFIRMED=STOP_LEGACY_WRITER \
  bash deploy/cloud-control-legacy-writer.sh stop <cutover-id>
~~~

脚本只对 systemd 记录的 MainPID 发送 `SIGTERM`，不会自动升级为 `SIGKILL`；随后验证旧 PID、prepare 时
绑定的应用端口和
Java 中间件连接均消失，并生成 `stopped.env`。将该文件传到 GCP 后必须以 root 身份安装为
`/opt/ai-chat/runtime/cutovers/<cutover-id>/legacy-old-writer-stopped.env`（owner `root:root`、mode 600）；
`cloud-approve-production-cutover.sh` 会校验 cutover id、PID/端口断言和文件 SHA-256，并针对本次旧站强制
`LEGACY_APP_PORT=8080`，拒绝只靠口头确认或在 stop/start 间更换端口继续。
回滚启动要求先确认 GCP production 已停止，防止双写：

~~~bash
AICHAT_LEGACY_START_CONFIRMED=START_LEGACY_WRITER \
AICHAT_GCP_PRODUCTION_STOPPED_CONFIRMED=GCP_PRODUCTION_IS_STOPPED \
  bash deploy/cloud-control-legacy-writer.sh start <cutover-id>
~~~

恢复使用临时 systemd unit 和已校验的 Jar/环境快照；只把“进程 active 且 8080 监听”视为恢复启动，业务健康
仍要单独验证。完成稳定切换前不能删除旧服务器或该恢复包。

发布前可在具有 systemd 的非生产主机执行真实生命周期测试。测试自动选择空闲高位端口，创建并清理临时 unit、
临时 Jar 和恢复目录，覆盖 MainPID 漂移、缺少确认、优雅停写证据和回滚启动：

~~~bash
bash deploy/tests/legacy-writer-lifecycle-test.sh \
  deploy/cloud-control-legacy-writer.sh
~~~

## 安全边界

- AIDiag 和 Elasticsearch 密钥只通过 Compose file secrets 挂载；应用配置密钥位于只读挂载的
  `AICHAT_CONFIG_HOST_DIR/secrets/`。三者都不写入镜像、runtime env 或 `docker inspect`。
- `docker-entrypoint.sh` 只在容器内部读取 Elasticsearch 文件密钥并导出给 Java 进程。
- 根文件系统保持只读。
- 启动失败只收集 `ai-chat-backend` 最后 500 行日志，先脱敏再写入有大小上限的独立文件。
- 健康门禁失败会恢复同模式的上一个 immutable release，不会触碰另一个模式或 DNS。

## 云端目录

~~~text
/opt/ai-chat/releases/<release-name>       immutable source release
/opt/ai-chat/runtime/candidate.env         候选非密钥配置，mode 600/640
/opt/ai-chat/runtime/production.env        生产非密钥配置，mode 600/640
/opt/ai-chat/runtime/.secrets/candidate/   候选 Elasticsearch 文件密钥，uid/gid 10001:10001、mode 400
/opt/ai-chat/runtime/.secrets/production/  生产 Elasticsearch 文件密钥，uid/gid 10001:10001、mode 400
/opt/ai-chat/runtime/config/<mode>/        应用外部配置：application.properties + secrets/
/opt/ai-chat/candidate                     当前候选 release 软链接
/opt/ai-chat/runtime/cutovers/<id>/        生产切换备份、停写与批准证据（root:root、mode 600）
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

已存在健康 production 时，若新版本没有任何 Flyway migration 变化，使用专用升级入口：

~~~bash
bash deploy/cloud-upgrade-production.sh \
  /opt/ai-chat/releases/<release-name> --confirm-no-schema-production-upgrade
~~~

该入口会先验证现有 production 健康、逐文件比较新旧 migration 目录（允许 Windows/Unix 行尾差异）并构建新镜像，再原子切换
`/opt/ai-chat/current` 和重建后端容器。新实例健康门禁失败时自动构建并恢复旧 release；升级过程不修改
Nginx 或 DNS。migration 有任何差异时必须回到维护窗口、备份和批准流程，不能使用此入口。

生产预备文件中的 `AICHAT_PRODUCTION_CONFIRMED` 必须保持 `false`。完成生产身份、稳定别名和非密钥配置后，
先执行无副作用预检：

~~~bash
bash deploy/cloud-preflight-production.sh --prepare /opt/ai-chat/releases/<release-name>
~~~

进入维护窗口后，先备份当前 Flyway V4 的 `ai_agent_db`；脚本使用 `--single-transaction`，生成 gzip
及 SHA-256，但不会停止任何服务：

~~~bash
bash deploy/cloud-backup-production-database.sh <cutover-id>
~~~

确认外部旧实例的 listener、scheduler、索引同步和其他写入口全部停止后，才可以记录批准证据。
`--confirm-old-writers-stopped` 是人工断言，不会代替实际停机检查：

~~~bash
bash deploy/cloud-approve-production-cutover.sh \
  /opt/ai-chat/releases/<release-name> --confirm-old-writers-stopped
~~~

只有维护窗口内完成数据库备份、停止旧实例和候选应用，并生成与当前 release 绑定的停写/批准证据后，
才将确认值改为 `true`，再次执行 `--ready`，最后启动：

~~~bash
bash deploy/cloud-preflight-production.sh --ready /opt/ai-chat/releases/<release-name>
bash deploy/cloud-activate-production.sh /opt/ai-chat/releases/<release-name>
~~~

首次 GCP 生产启动失败时，本机没有旧 production release 可以自动恢复；脚本只会移除失败实例，
必须按切换手册重新启动外部旧实例。因为此时 Nginx/DNS 尚未切换，回滚不会涉及 DNS，但维护窗口会延长。

生产容器健康只代表新进程可工作，仍需完成真实登录、SSE Chat、知识库、管理端、指标、日志、Trace、
告警和回滚演练，之后才能切换 Nginx/DNS。

## Nginx 与 DNS 切换

`deploy/nginx/api.dduo.chat.conf.template` 只把公开入口代理到 `127.0.0.1:18083`，并关闭 proxy
buffering 以支持 SSE；`/healthz` 只代理到 loopback management health，供外部检查验证 DNS、TLS、Nginx
和 JVM 总体状态。激活脚本要求 production 容器 `healthy/restart 0`、两个端口只监听 IPv4 loopback、
证书至少七天有效且 SAN 精确覆盖 `api.dduo.chat`、证书与私钥匹配。它会先执行 `nginx -t`，reload
后在 20 秒有界窗口内通过 `--resolve api.dduo.chat:443:127.0.0.1` 重试 TLS/upstream 探测，以兼容 Nginx
异步切换 worker；门禁要求 `/healthz` 返回 200，且无认证访问只读 `/api/ai/models` 精确返回 401。不得使用
会创建数据的 Guest POST，也不得把任意 4xx/5xx 当作成功。脚本本身不会修改 DNS。

~~~bash
AICHAT_PRODUCTION_NGINX_CONFIRMED=ACTIVATE_AI_CHAT_PRODUCTION_NGINX \
  bash deploy/cloud-activate-production-nginx.sh \
  /opt/ai-chat/releases/<release-name> <cutover-id>
~~~

只有外部 DNS 已恢复到非 GCP 地址且解析结果不再包含 `34.150.80.173` 时，才允许撤回 Nginx：

~~~bash
AICHAT_PRODUCTION_NGINX_ROLLBACK_CONFIRMED=ROLLBACK_AI_CHAT_PRODUCTION_NGINX \
AICHAT_PUBLIC_DNS_ROLLBACK_CONFIRMED=DNS_POINTS_AWAY_FROM_GCP \
  bash deploy/cloud-rollback-production-nginx.sh <cutover-id>
~~~

首次切换时 GCP 尚无 `api.dduo.chat` 证书。必须先通过 DNS-01 或从受信任旧服务器安全迁移证书，不能用
自签名证书代替生产 TLS 门禁。DNS 切换和外部可用性监控仍按 AIDiag V1 生产切换手册人工执行。

2026-07-14 已从旧服务器安全迁移仍有效的 Let's Encrypt 证书，GCP 私钥权限收紧为 `root:root 600`；使用
control release `20260714-v1-production-nginx-gate-r8` 修复 reload 即时探测和错误 Guest GET 门禁后，Nginx
原子激活通过。本机及主机外 `--resolve` 均得到 `/healthz=200`、`/api/ai/models=401`、TLS verify result 0；
AI-chat application release/current 仍为已批准 r7，容器未重启，DNS 未修改。证书到期时间为
2026-08-12，正式长期运行前必须在 GCP 配置续期或新的 DNS-01 证书流程。
