# AI-chat 账号与鉴权手册

## 注册与登录

[FACT-AUTH-001] 普通用户需要先调用注册接口创建账号，再通过登录接口换取 JWT。JWT 应放入 `Authorization: Bearer <token>` Header，不应写入 URL。

[FACT-AUTH-002] 管理员和普通用户使用不同的登录入口。管理员 JWT 只用于管理操作，不应作为普通用户聊天 Token 使用。

## Token 生命周期

[FACT-AUTH-003] 用户退出登录后，服务端会撤销对应 Token。已撤销 Token 再次访问受保护 API 时应返回未认证状态。

[FACT-AUTH-004] 游客账号可以通过一键体验入口创建。游客账号适合临时体验，不用于长期保存正式资料。
