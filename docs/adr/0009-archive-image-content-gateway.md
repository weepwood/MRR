# ADR-0009：档案袋影像经内容网关下发（影像访问 Cookie）

- 状态：Proposed
- 日期：2026-11-23
- 决策者：待评审（MRR 维护者）
- 关联 Issue：待创建（建议标题：档案袋影像绕过后端权限与审计）
- 关联 PR：待创建
- 替代 ADR：无（补齐 ADR README 中“多来源影像解析与统一内容网关”缺口的下发部分）

## 背景

**已确认事实**

- 内容网关 `GET /api/v1/img/content/{id}`（#303）已存在：经 `ImageStorage` 按 OSS / Nginx / NAS / 本地顺序取图，返回 `private, no-store`，受 `record:read` 保护。外部调阅（ADR-0003）与旧 `/oss-image/{id}` 入口已经使用它。
- 档案袋主流程 `GET /api/v1/img/{bah}` 与 `/api/v1/img/search` 经 `ImageUrlService.toDtoList` 返回 `img_url`：本地模式为影像源 Nginx 静态地址（`节点/年月/日期/住院号-病案号/文件名`，可推算），OSS 模式为 `oss.url-expire-seconds`（默认 3600 秒）的预签名地址。浏览器直接加载，取图不经过后端。
- 因此逐张影像访问既不做权限判断，也不写访问审计；能访问影像源 Nginx 的任何内网主机都能按规律取图。这与 `ARCHITECTURE.md`“外部影像来源通过统一解析或内容网关访问”、AGENTS.md“受保护影像优先通过后端统一内容网关”不一致。
- 浏览器 `<img>` 无法携带 `Authorization: Bearer` 请求头；当前访问令牌保存在 `localStorage`。

**推断**

- 影像源 Nginx 节点在现场大多可被医生工作站直接访问（否则直连模式无法工作）。

**未知项**

- 各现场影像源节点能否只对 MRR 服务器开放；内容网关在高峰期的并发与延迟（取决于 NAS/Nginx/OSS 实际吞吐）。

## 决策

新增配置 `archive.image-delivery.mode`（环境变量 `ARCHIVE_IMAGE_DELIVERY_MODE`），取值 `direct`（默认，现状）或 `gateway`。

`gateway` 模式下：

1. 列表接口只返回 `img_url=/api/v1/img/content/{id}`，`ossUrl` 置空；不再读取来源设置、不再为每张图生成 OSS 预签名。`/api/v1/img/url/{id}` 同样返回网关地址。
2. Bearer 认证成功的档案袋列表类请求（`GET /api/v1/img/search`、`/api/v1/img/{bah}`、`/api/v1/img/url/{id}`），且会话具有 `record:read` 时，响应附带 HttpOnly Cookie：

   ```text
   MRR_IMAGE_ACCESS = v1.{userId}.{passwordVersion}.{tokenHash}.{expEpochSeconds}.{base64url(HMAC-SHA256)}
   ```

   - 签名子密钥 = `JwtUtil.deriveKey("MRR-IMAGE-ACCESS-V1")`（从 JWT 密钥派生，用途隔离）；
   - `tokenHash` 为签发时访问令牌在撤销表中的键（SHA-256(jti)）；
   - 过期时间等于访问令牌过期时间；`HttpOnly; SameSite=Strict; Path=/`，HTTPS 下附加 `Secure`。
3. `LoginInterceptor` 仅在 `GET/HEAD /api/v1/img/content/{数字}` 且无 `Authorization` 头时读取该 Cookie：先在内存校验格式、签名与过期（伪造值不访问数据库），再复用 Bearer 认证的撤销、账号状态与密码版本检查，最后由 `AuthorizationInterceptor` 按 `record:read` 授权。
4. 内容网关把影像所属的“病案号”或“病案号:上架号”绑定为审计目标，访问日志记为 `VIEW_IMAGE`（“经内容网关查看病案图片”），与列表审计口径一致，可在影像访问审计页按病案检索。
5. 登出时清除 Cookie；即使未清除，令牌撤销也使其立即失效。
6. 兼容模式（开发者旧档案袋）允许匿名 `GET /api/v1/img/content/{id}`，范围与已允许的 `/oss-image/{id}`（302 到网关）一致，仍受启动开关、可信代理与 IP/CIDR 约束。

前端只做两处适配：`/api/` 相对影像地址统一按 API 基址解析（开发环境经 `/proxy`）；浏览器侧 PDF/ZIP 合成对同源地址携带同源凭据，跨域影像源仍为 `omit`。

## 必须保持的不变量

- 影像访问 Cookie 只在内容网关路径被接受，不能替代任何其他接口的 Bearer 认证。
- Cookie 不得比签发它的访问令牌活得更久；令牌撤销、密码版本变化、账号停用后立即失效。
- 校验失败 fail-closed：格式、签名、撤销查询异常、用户查询异常都拒绝访问，异常信息不回显内部细节。
- 网关模式下浏览器不得获得影像源地址或 OSS 预签名地址。
- 内容网关响应保持 `private, no-store`、`nosniff`、`no-referrer`。
- `direct` 模式行为与本 ADR 之前完全一致。

## 方案边界

### 包含

- 档案袋（`/api/v1/img/search`、`/{bah}`、`/url/{id}`）的影像下发方式与逐图认证、审计。

### 不包含

- `scan-img` 等仍读取 `ossUrl || img_url` 的旧页面（网关模式下同样拿到网关地址，可工作，但未单独验证）。
- 后端 ZIP/PDF 导出（已在服务端读取影像，不受影响）。
- Nginx `X-Accel-Redirect` 卸载大文件传输、按科室的细粒度病案授权、影像源 Nginx 的网络隔离（运维配置，见“迁移方案”）。

## 替代方案

### 方案 A：短时效签名 URL（`?exp=&sig=`）

- 优点：无状态，不依赖 Cookie；可绑定单张影像。
- 缺点：档案袋三个组件的失败状态、`<img :key>`、选择恢复与懒加载计数都以 `imageUrl` 为键；URL 到期刷新会导致已加载影像全部重新下载（`no-store`）、懒加载位置重置，在“不记住选择”设置下还会清空选中。若把有效期拉长到可接受程度，又退化为“可转发的长期链接”。
- 未采用原因：对前端侵入大、体验差，安全收益不比 Cookie 方案高。

### 方案 B：前端用 axios 带 Bearer 拉取 Blob 再 `createObjectURL`

- 优点：不新增凭据类型。
- 缺点：数百张影像全部经 JS 内存，失去浏览器原生懒加载与解码调度；Chrome 86 下内存压力大；打印 iframe、全屏预览、PDF 合成都要改造并管理 `revokeObjectURL`。
- 未采用原因：改动面与性能风险都明显更大。

### 方案 C：把访问令牌改为 HttpOnly Cookie

- 优点：从根本上解决 `<img>` 认证并减轻 XSS 窃取令牌风险。
- 缺点：涉及全站认证与 CSRF 防护改造。
- 未采用原因：范围超出本问题，列为后续会话改造（见优化计划 2.4）。

## 影响

### 正面影响

- 逐张影像访问都经过账号状态、令牌撤销与 `record:read` 校验，并写入访问审计。
- 浏览器不再获得可推算的影像源地址与 1 小时 OSS 预签名；影像源节点可以只对 MRR 服务器开放。
- 列表接口不再为每张图生成 OSS 预签名。

### 负面影响与技术债

- 影像流经后端：Tomcat 线程与 `archive.image-source.*-max-concurrency` 许可成为瓶颈，需要容量验证；后续可用 `X-Accel-Redirect` 让 Nginx 发送本地/NAS 文件。
- 每张影像增加两次主键查询（撤销表、用户表）。浏览器对同源 HTTP/1.1 并发约 6 个，单用户压力有限；多用户高峰可考虑 5 秒级短缓存（优化计划 2.4）。
- `access_log` 由“每次列表一行”变为“每张影像一行”，增长更快，与 `access_log` 分区（优化计划 2.2）相关，建议同期推进。

### 安全、隐私与审计

- Cookie 为 HttpOnly，脚本无法读取；`SameSite=Strict` 阻止第三方页面嵌入影像或跨站跳转携带。
- Cookie 只授权 GET/HEAD 读取影像，不授权任何写操作。
- 访问日志的查询串只保留白名单参数，审计目标经 HMAC 保护，不以明文保存病案号。
- 仍依赖前端 `localStorage` 中的访问令牌签发 Cookie，XSS 风险评估不变（方案 C 解决）。

### 数据库、性能与容量

- 无数据库结构变更，无 Flyway 迁移。
- 新增查询均为主键 / 唯一键等值查询。

### Chrome 86、Windows 与部署兼容性

- `SameSite=Strict`、HttpOnly Cookie、`fetch` 的 `credentials: 'same-origin'` 在 Chrome 86 均可用。
- 要求前端与 API 同源（Windows 正式 Nginx 单入口、单体 JAR 内嵌前端均满足）。若现场把 `VITE_APP_API_BASEURL` 指向其他源，请保持 `direct`。

## 迁移方案

1. 合并后默认 `direct`，行为不变。
2. 灰度：在一台服务器的 `mrr-backend.xml` 中增加 `<env name="ARCHIVE_IMAGE_DELIVERY_MODE" value="gateway" />`，重跑安装脚本刷新服务定义并重启后端。
3. 观察：影像访问审计出现逐图 `VIEW_IMAGE`；档案袋打开 300 张影像的首屏时间与失败率；后端线程、Hikari 连接池与影像源许可等待（Actuator / Prometheus）。
4. 稳定后：影像源 Nginx 节点只允许 MRR 服务器访问；OSS 桶策略禁止公网读取。
5. 全量切换后评估是否把默认值改为 `gateway`（需新的变更说明）。

## 回滚与前向修复

- 把 `ARCHIVE_IMAGE_DELIVERY_MODE` 改回 `direct` 并重启即可恢复直连；已签发的 Cookie 在直连模式下不再被接受。
- 若已收紧影像源节点访问，回滚前须先恢复工作站对节点的访问。
- 无数据库变更，无需前向修复迁移。

## 验证

- 单元测试：`ImageAccessCookieServiceTest`（签发范围、属性、篡改、过期、密钥轮换）、`LoginInterceptorImageAccessTest`（签发→使用、撤销、改密、停用、伪造不查库、非网关路径不接受、直连模式不接受、存储故障 503）、`ImageUrlServiceTest`（网关模式不下发源地址、不预签名）、`DeveloperModeServiceTest`、`LogInterceptorTest`、`ImageContentControllerTest`、`ImageContentServiceImplTest`、`JwtUtilTest`、`TokenBlacklistHashTest`、`DocumentationSessionCleanupInterceptorTest`；前端 `constants.test.ts`、`image-fetch.test.ts`。
- PostgreSQL 集成测试：`TokenBlacklist.isRevokedHash` 与 `revoke` 写入的哈希一致（建议补充到现有撤销集成测试）。
- E2E 或手工流程：网关模式下打开档案袋、全屏预览、连续滚动、打印、浏览器侧 PDF 导出；登出后在新标签页直接访问 `/api/v1/img/content/{id}` 得到 401；无 `record:read` 账号得到 403；关闭影像源节点对工作站的访问后影像仍正常显示。
- 性能与容量：10 个并发用户各打开 300 张影像，记录 P95 首图时间、后端 CPU、连接池等待与影像源许可等待。
- 生产或灰度验证：单台服务器灰度一周，对比影像访问审计条数与档案袋打开次数。

## 后续事项

- [ ] 创建 Issue 与 PR，评审后将状态改为 Accepted
- [ ] 补充撤销哈希的 PostgreSQL 集成测试与网关模式 Playwright 用例
- [ ] 评估 `X-Accel-Redirect` 发送本地/NAS 影像
- [ ] 与 `access_log` 分区同期上线，避免逐图审计放大积压
- [ ] 会话改造（访问令牌 HttpOnly 化）后，评估是否用同一 Cookie 取代本凭据
