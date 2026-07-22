# 安全

## 安全边界

MRR 处理患者、病案、图片和审计数据。安全设计需要覆盖浏览器、前端静态资源、Spring Boot API、PostgreSQL、图片服务、OSS、文档站点和运维端点。

## 密钥管理

生产环境必须独立设置：

```text
SPRING_DATASOURCE_PASSWORD
JWT_SECRET_KEY
AES_SECRET_KEY
IMAGE_PASSWORD
OSS_ACCESS_KEY_ID
OSS_ACCESS_KEY_SECRET
```

要求：

1. 不提交到 Git。
2. 不写入前端环境变量。
3. 不记录到日志。
4. 不在 PR 截图和错误信息中泄露。
5. 通过系统服务、权限受控配置或密钥管理系统注入。
6. 不同环境使用不同密钥。
7. 密钥泄露后立即轮换，不能只删除当前文件。

## JWT

- 签名密钥必须足够随机。
- 后端校验签名、有效期和令牌用途。
- 前端不能自行信任解码后的角色信息绕过后端。
- 401 处理应避免重复登出和重复提示。
- 日志不得记录完整令牌。

## 开发者档案袋兼容模式

系统设置键 `developerModeEnabled` 默认由 Flyway 初始化为 `false`。该开关只用于旧系统通过无 Token URL 打开独立影像档案袋，不再提供全系统开发者管理员身份。

模式必须同时满足：

```text
MRR_DEVELOPER_MODE_ALLOWED=true
+
系统设置 developerModeEnabled=true
+
后端连接来自启动配置允许的本机 Nginx
+
真实客户端 IP 命中系统设置中的单 IP/CIDR 白名单
+
请求没有 Authorization Header
+
请求属于档案袋只读 GET 白名单
```

启动配置只维护可信代理地址，默认只信任与后端同机的 Nginx：

```text
MRR_DEVELOPER_MODE_TRUSTED_PROXY_ADDRESSES=127.0.0.1,::1
```

真实客户端白名单在“系统设置 → 开发者模式”中维护，对应设置键 `developerModeAllowedSources`。每行或使用逗号填写一个规则，支持：

```text
192.168.1.20
192.168.1.0/24
10.20.0.0/16
::1
2001:db8::/64
```

后端只在 `request.remoteAddr` 命中可信代理配置时读取 `X-Forwarded-For` 或 `X-Real-IP`。直接访问后端或从非可信代理伪造转发头不会获得兼容访问权限。空白名单会拒绝全部兼容访问，非法 IP/CIDR 会阻止设置保存。

允许的兼容接口仅包括：

- `/api/v1/img/search`；
- 按病案号读取图片列表；
- 单张本地、Nginx、OSS 图片读取；
- 按病案号读取患者基本信息。

兼容身份使用虚拟 ID `-1`、角色 `DEVELOPER_ARCHIVE`，只具备 `record:read` 和 `search:read`。它不能进入用户管理、系统设置、扫描管理和其他后台页面，也不能下载 ZIP、导出 PDF、打印或修改图片类型。

旧系统示例：

```text
/archive?bah=789508&userid=HIS001
/archive?bah=10000001&sjh=12345678&userid=HIS001
```

`userid` 仅作为审计标识和 IP 绑定键，不是登录凭据。任何无效、过期、撤销、错误类型 Token 或禁用用户都必须返回 401/403，不得降级到兼容身份。

模式不会放开任意 Origin CORS。正常部署应通过同源 Nginx 访问；确需跨域联调时只配置精确 Origin。

边界：

- `/api/v1/integration/archive/tickets` 不经过 JWT 拦截器，仍执行 HMAC、时间戳、nonce 和 IP 白名单校验；
- 外部影像 Session 仍按 Ticket 授权范围访问；
- 兼容模式不会返回、生成或暴露 HMAC Secret；
- 数据库不可用、配置缺失或配置值无法识别时必须按关闭处理；
- 生产正式外部接入优先使用 HMAC Ticket，旧接口兼容完成后应关闭该模式。

验收关闭状态时，应使用无 Token 请求 `/api/v1/img/search`，确认返回 `401`，并检查响应中不存在 `X-MRR-Access-Mode`。

## AES 与身份证令牌

身份证 URL 令牌使用 AES-GCM 和随机 IV：

- 每次加密使用新 IV。
- 验证认证标签和参数篡改。
- 不使用固定 IV。
- 不把明文身份证号作为 URL 持久状态。
- 响应只返回业务需要的脱敏值。
- AES 密钥不得与 JWT 密钥复用。

## 个人信息保护

敏感字段包括身份证号、患者姓名、病人 ID、病案号、上架号、图片路径、用户名、IP 和访问历史。

处理原则：

- 最小化返回字段。
- 页面只在业务需要时展示。
- URL 避免明文敏感信息。
- 日志进行脱敏。
- Prometheus 标签不得使用患者或病案字段。
- 导出和打印操作应保留访问审计。
- 状态页不得返回内部数据。

## RBAC

后端权限是最终边界。每个接口应明确是否公开、是否只要求登录、所需具体权限以及是否仅管理员允许。前端 `meta.auth`、菜单隐藏和按钮禁用不能替代服务端校验。

## 文档访问

| 内容 | 权限 |
|------|------|
| `/docs/` | 已登录账号 |
| `/docs/internal/` | `system:read` 或管理员 |
| `/api-docs/` | `system:read` 或管理员 |

文档访问 Cookie 应为 HttpOnly、短期有效，并在 HTTPS 环境启用 Secure，使用合适 SameSite，登出时清除。

Nginx 必须保护搜索索引、JS、CSS、图片和 OpenAPI 文件，不能只保护首页。

## 网络边界

### Actuator

默认只监听 `127.0.0.1:18046`，禁止直接暴露公网。跨机器采集使用白名单、VPN 或受控代理。

### PostgreSQL

- 应用账号只授予业务所需权限。
- `mrr_monitor` 只用于监控。
- 管理员账号不用于应用连接。
- `pg_hba.conf` 限制来源和认证方式。
- 生产环境优先使用 TLS。
- V0 初始化所需扩展由管理员预先确认或创建。

### Grafana 与 Prometheus

- 默认监听本机或内网。
- Grafana 修改默认管理员密码。
- Alertmanager 通知凭证不提交仓库。
- exporter 不使用业务写权限账号。

## 文件与路径安全

图片读取必须防止目录穿越：

1. 对输入路径规范化。
2. 验证最终路径仍位于配置根目录。
3. 限制参数长度和非法字符。
4. 不把服务器绝对路径返回前端。
5. 文件服务账户只授予必要目录权限。

## CORS

业务 API 与图片服务的 CORS 分别配置：

- 只允许实际前端来源。
- 明确允许的方法与请求头。
- 图片 PDF 导出使用 `credentials: omit`。
- 使用 Cookie 的接口需要精确来源和 `Allow-Credentials`，不能使用通配符。
- 开发者档案袋兼容模式不会修改 CORS 策略。

## 日志安全

禁止记录密码、JWT、AES/OSS 密钥、完整身份证号、敏感请求体和带密码的数据库连接字符串。异常响应不要返回堆栈、SQL 或服务器路径。

## 依赖与供应链

- 使用锁文件安装前端依赖。
- 不使用 `--legacy-peer-deps` 掩盖冲突。
- 依赖升级后执行完整测试和构建。
- 审查新依赖的维护状态、许可证和运行权限。
- 不从不可信来源复制监控二进制文件。

## 开源前检查

检查 Git 历史中的密钥、内网地址、本地配置、患者测试数据、截图、Nginx、Grafana 和 Alertmanager 凭证。已经进入历史的密钥必须轮换。
