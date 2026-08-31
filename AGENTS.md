# AI-chat 家庭服务器上下文

## 目的

本项目有一台长期运行的家庭 Ubuntu 服务器。涉及 AI-chat 的运行状态、Docker、部署、日志、监控、备份或故障时，先使用本文提供的家庭服务器上下文，不要重新从仓库、历史命令或聊天记录猜测连接方式。

普通本地编码、单元测试和源码阅读不需要连接家庭服务器。

## 家庭服务器如何远程访问

家庭服务器位于家庭网络/NAT 后面，本身不直接拥有云服务器的公网 IP。远程 SSH 维护链路是：

```text
当前电脑 OpenSSH
  -> 本机个人 SSH 私钥
  -> 阿里云云服务器公网入口上的 frps
  -> 家庭服务器主动连接出去的 frpc
  -> 家庭服务器 sshd
```

FRP 只提供 SSH 维护通道，不承载 AI-chat 用户业务流量。

项目只保存逻辑连接名，不保存公网 IP、FRP 端口、用户名、IdentityFile、Token 或主机指纹。真实参数必须保存在当前电脑的 `~/.ssh/config` 和受控 `known_hosts` 中。

预期 SSH alias：

- `home-server-frp`：不在家庭局域网时，经阿里云 FRP 进入家庭服务器；
- `home-server-lan`：当前电脑与家庭服务器在同一可信局域网时的恢复入口。

标准连接命令：

```powershell
ssh home-server-frp
```

同一 Wi-Fi 下且 FRP 故障时，才使用：

```powershell
ssh home-server-lan
```

如果 alias 不存在、Host key 不匹配或密钥认证失败，停止在远程操作之前，报告缺少哪项本机连接条件。不要搜索或输出私钥，不要猜公网地址/端口，不要使用 `StrictHostKeyChecking=no`。

## 服务器里有什么

以下是仓库资料和历史只读核验形成的家庭环境基线，不代表本次运行态；连接后仍需先核验。

### 宿主机

- Lenovo XiaoXinAir-14API 2019（81NJ）；
- AMD Ryzen 5 3500U；
- Ubuntu 24.04 系列；
- Linux 可见内存约 9.65 GiB，swap 约 4 GiB；
- Samsung 512 GB NVMe；
- 资源有限，新增服务或放宽容器上限前必须做内存、磁盘和峰值预算。

### 宿主机系统服务

- `sshd`：SSH 登录；
- `docker`：运行应用、中间件与观测组件；
- `frpc`：连接阿里云 `frps`，提供远程 SSH 维护通道；
- `cloudflared`：仅当家庭环境承担对应公网应用入口时，连接 Cloudflare Tunnel。

### Docker 服务组

- AI-chat：`ai-chat-backend`；
- 业务依赖：MySQL、Redis、RabbitMQ、Nacos、Elasticsearch；
- AIDiag：`aidiag-backend`、PostgreSQL；
- 观测：Prometheus、Alertmanager、Grafana、Alloy、Loki、Tempo、Blackbox Exporter；
- HomeOps 采集候选：node_exporter 与只读电池/温度采集器。

容器名可能随 Compose project 和 release 改变。优先使用 `com.docker.compose.project` / `com.docker.compose.service` label 或 `docker compose ps` 发现服务，不要依赖带日期的容器名。

### 重要目录和配置所有者

- AI-chat 家庭运行数据、日志和 Secret 预期位于 `/opt/ai-chat-home/runtime/`；
- AIDiag 家庭运行数据和 Secret 预期位于 `/opt/aidiag-home/runtime/`；
- Secret 只存在于服务器受限文件中，不读取正文，不复制到日志、项目或聊天；
- AI-chat 家庭 Compose 覆盖与监控配置由相邻 `AI-ops` 仓库维护：`../AI-ops/deployment/home/`；
- AI-chat 本仓库的 `deploy/` 主要包含云端/GCP 发布流程，不能直接作为家庭服务器当前运行态。

## 开始排错前的只读基线

连接后先确认目标确实是家庭服务器，再依次检查：

```bash
hostnamectl
uptime
free -h
df -hT /
df -ih /
systemctl --failed --no-pager
systemctl is-active ssh docker frpc cloudflared
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
```

不要输出 `docker inspect` 全文；它可能包含环境信息。需要容器状态时，只查询 `.State`、health、restart count 和 Compose labels。

## AI-chat 排错顺序

按下面顺序定位，不从公网错误直接跳到数据库或重启：

1. **连接层**：FRP SSH 是否可用；同一局域网时 LAN SSH 是否可用；Host key 是否匹配。
2. **宿主机层**：是否重启、磁盘/inode 是否不足、是否内存或 swap 压力、是否有 failed unit/OOM。
3. **Docker 层**：daemon 是否可用；AI-chat 容器是否存在、healthy、反复重启或 OOMKilled；必要网络是否存在。
4. **应用层**：AI-chat loopback application/management health；只读取有限、脱敏的最近日志。
5. **依赖层**：MySQL、Redis、RabbitMQ、Nacos、Elasticsearch 的容器健康与 TCP 可达性；可达不等于账号、数据或业务正确。
6. **业务层**：登录、SSE Chat、知识库上传、RAG/检索和管理端按问题选择最小回归。
7. **遥测层**：Metrics、脱敏 operations log、Trace 是否新鲜；Prometheus target 和 AIDiag 告警是否匹配。
8. **公网层**：先确认当前公网环境究竟由家庭服务器还是 GCP 承担，再选择 Cloudflare Tunnel 或 GCP/Nginx 的探针契约。不得仅凭域名或历史文档猜部署位置。

`frpc active` 只表示进程存活，不证明已成功登录 `frps` 或代理已注册。FRP 失败时检查两端版本、配置验证、最近日志、认证/TLS 和云端监听/安全组；一次受控重启仍失败时停止重复重启，转到 `frps` 侧诊断。

## Agent 可直接进行的动作

- 读取上述只读状态；
- 按 Compose service label 发现容器；
- 读取指定服务最近、有行数和时间范围上限的日志并脱敏；
- 运行 `docker compose config --quiet`、固定 health/probe 和不产生业务数据的检查；
- 报告 `RUNTIME_VERIFIED`、仓库预期、历史证据和未知项。

没有当前任务明确授权时，不重启/停止容器，不部署，不修改 Compose、FRP、Cloudflare、DNS、UFW、Nacos 配置或数据库，不执行 prune、迁移、恢复和 Secret 轮换。需要修复时只改造成故障的最小命名服务，并在变更前给出影响、回滚和验收方式。

## 家庭服务器事实来源

家庭环境的仓库事实优先读取：

1. `../AI-ops/deployment/home/README.md`；
2. `../AI-ops/deployment/home/` 中对应 Compose 覆盖；
3. `../AI-ops/docs/AI-chat-接入指南.md`；
4. 本次 SSH 只读检查。

当仓库文档互相冲突时，本次只读运行态证据优先；仍无法确认时标为 `UNKNOWN`，不得执行依赖该事实的写操作。
