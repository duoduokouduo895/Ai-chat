# Nacos 导出转外部配置

本工具只准备候选目录，不改正在运行的配置。先在 Nacos 控制台导出 `DEFAULT_GROUP` 的九个
`*-config.properties`，放进仅 root 可访问的目录；文件名必须与
[`prepare_external_config.py`](prepare_external_config.py) 中的 `SOURCE_FILES` 一致。
不要把导出、密钥清单或生成目录放进 Git 仓库。

在服务器上使用 Python 3.10+，以 root 身份执行。以下命令只打印键名和数量，不打印值：

```bash
umask 077
python3 scripts/prepare_external_config.py /root/nacos-export
```

检查输出的重复键与 `review-secret` 键名。将所有实际密钥的**准确属性名**逐行写入
`/root/ai-chat-secret-keys.txt`（权限 `0600`）；敏感关键词误报的非密钥键可以逐行写入
`/root/ai-chat-public-exceptions.txt`。不能仅凭自动匹配认为其余键都安全，仍须人工核对
完整键名清单。准备候选目录：

```bash
python3 scripts/prepare_external_config.py /root/nacos-export \
  --secret-keys /root/ai-chat-secret-keys.txt \
  --public-exceptions /root/ai-chat-public-exceptions.txt \
  --output-dir /opt/ai-chat-home/runtime/config.next \
  --uid 10001 --gid 10001
```

若没有误报清单，省略 `--public-exceptions`。输出目录必须事先不存在；工具不会覆盖运行目录。
输出文件的权限为：目录 `0500`、文件 `0400`，所有者 `10001:10001`。
只核对键名、文件数量、权限和所需文件是否存在，**不要输出 `secrets/` 正文**。
在候选镜像中验证配置加载、健康、登录与一次 RAG 对话后，才将候选目录切换为运行目录。
保留受限导出和旧 release 供回滚；不要在切换时停掉 Nacos。

工具按九份文件的既有导入顺序读取属性，后文件覆盖前文件。它解析 properties 续行和
`\uXXXX` 转义，输出非密钥文件时使用 ASCII 及 Unicode 转义，以匹配当前 Spring Boot 4.0
读取 properties 的行为。密钥文件写入解码后的原值。空值、换行密钥和不受支持的属性名会
报错，不会默默截断。
