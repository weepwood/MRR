# ADR-0003：外部系统安全影像调阅（HMAC 一次性 Ticket 模型）

- 状态：Accepted
- 日期：2026-10-09（补录；实现随 v0.7.x 合入 `main`）
- 决策者：MRR Team
- 关联 Issue：待补
- 关联 PR：待补
- 替代 ADR：无

## 背景

HIS、EMR 等外部系统需要在不接管 MRR 登录态的前提下，调阅指定病案影像档案袋。直接把生产存储凭据或受保护影像源地址暴露给外部系统会突破隐私边界（AGENTS.md 权限与隐私规则）。此前缺乏统一、可校验、可审计的受控外调机制，外部访问既无法限范围也无法追溯。

约束：

- 外部系统不得获得 JWT 或任何长期凭证；
- 访问必须可限定到具体病案（bah/sjh）、可过期、可撤销、可审计；
- 生产存储凭据与内部路径不得离开后端；
- 兼容 Chrome 86、Windows 内网部署，不引入新中间件。

## 决策

采用 **HMAC-SHA256 一次性 Ticket + 短期只读会话** 模型：

- 外部系统凭 `client_id` 与 HMAC 签名请求一次性 Ticket（`/archive/external?ticket=...`）；
- Ticket 与 Session 仅持久化 **SHA-256 摘要**，不保存令牌明文；
- Ticket 携带 `external_user_id`、`allow_download`、`grant_json`（范围授权）、`expires_at`、一次性 `used_at`；
- Session 支持短期复用与 `revoked_at` 主动撤销；
- 每次访问写入 `mr_external_archive_access_log`（client_id、bah、sjh、action、image_id、client_ip、result、request_id）；
- 外部兼容模式默认关闭，仅在开启后受开关、可信代理、IP/CIDR、只读接口与审计约束。

生效时间：迁移 `V20260718143000__external_archive_secure_access.sql` 合入 `main` 后。

## 必须保持的不变量

- 外部票据永远不能替代内部 RBAC；内部接口权限仍以后端为准；
- 生产存储凭据、内部路径、OSS Key 不得出现在前端或外部响应；
- 受保护影像必须经后端统一内容网关返回，不得把源 URL 直传给外部系统；
- 所有外部访问必须可审计且默认只读。

## 方案边界

### 包含

- 一次性 Ticket 与短期 Session 的签发、校验、撤销；
- 来源 IP 与 nonce 校验；
- 按病案范围的最小授权（grant_json）；
- 外部访问审计日志。

### 不包含

- 外部系统的用户身份体系（外部系统自行负责其用户）；
- 写操作、管理操作或任何非只读能力（除非显式授予 `allow_download` 且仅限下载）；
- 通用 OAuth/OIDC 联邦身份。

## 替代方案

### 方案 A：直接向外暴露带签名时效的 OSS 预签名 URL

- 优点：实现简单，CDN 直出性能好。
- 缺点：把存储凭据边界外推到外部；无法在 MRR 内做细粒度撤销与实时审计；范围控制弱。
- 未采用原因：违反"前端/外部不得获得生产存储凭据"与审计不变量。

### 方案 B：为外部系统开通长期 JWT 服务账号

- 优点：复用现有认证。
- 缺点：长期凭证外泄面大；难以限定到单次调阅；违背最小权限。
- 未采用原因：不符合一次性、可撤销、可审计要求。

## 影响

### 正面影响

- 外部调阅首次具备范围限定、过期、撤销与完整审计；
- 不引入新中间件，维持模块化单体边界。

### 负面影响与技术债

- 外部票据生命周期与内部会话两套机制并存，需明确不互相替代；
- 过期 Ticket/Session 的清理依赖定时任务或惰性校验，需监控堆积。

### 安全、隐私与审计

- 仅存摘要，令牌泄露面降到最低；
- 所有访问落审计；`bah/sjh` 等敏感目标在日志中受既有脱敏约束。

### 数据库、性能与容量

- 新增 4 张表（nonce、ticket、session、access_log）及对应索引；
- access_log 为写多读少，需保留策略与清理，避免无限增长。

### Chrome 86、Windows 与部署兼容性

- 纯后端能力，不影响前端兼容；Windows 内网部署无新依赖。

## 迁移方案

- 数据库：执行 `V20260718143000` 创建表与索引（已合入 main）；
- 代码：ExternalArchiveController / ExternalArchiveAccessService / 拦截器校验；
- 配置：外部兼容模式默认关闭，需运维显式开启并配置 IP/CIDR 白名单；
- 文档：内部文档补充外部系统接入说明（external-archive-integration.md）。

## 回滚与前向修复

- 代码回滚到此前版本即可关闭外部调阅入口；
- 已发布迁移 `V20260718143000` **不得回写**；如需下线，保留表结构（仅停止使用），或以后向新增迁移标记 `revoked`；
- 已签发未过期 Ticket 在代码回滚后随入口关闭而失效。

## 验证

- 单元测试：`ExternalArchiveAccessServiceTest`、`ExternalArchiveIntegrationStatusControllerTest`；
- PostgreSQL 集成测试：Ticket 一次性使用、过期、IP 校验、撤销路径；
- E2E 或手工流程：外部系统持 Ticket 成功调阅、过期拒绝、撤销拒绝；
- 性能与容量：access_log 写入在高频访问下的索引表现；
- 生产或灰度验证：在受 IP/CIDR 限制的内网环境灰度，观察审计完整性。

## 后续事项

- [ ] 补充真实 Issue/PR 编号链接；
- [ ] 评估 access_log 自动清理策略与保留期；
- [ ] 明确外部 client_id 的发放与回收流程。
