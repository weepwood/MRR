# 影像目录路由规则

## 任务与风险

Issue #588，P1。分支 `codex/image-directory-routing`，专属工作区
`D:/Project/CodexWorkTress/image-directory-routing/MRR`，负责人 Codex，状态：待合并。

现有 `ImageUrlService` 将特殊日期及年月映射写在代码中。新增管理配置时复用
`mr_system_settings`、`SettingRegistry`、系统设置 API、权限与 SETTING 审计，不新增表、SQL、Flyway、Store 或部署组件。
节点地址、凭据、原始目录和文件内容均不改变，无需新增 ADR。

## 方案及实现范围

- `ImageDirectoryRoutingRules`：解析、校验、内置规则、精确目录优先匹配；不可变值对象，不持有缓存或网络客户端。
- `SettingRegistry` / `SystemSettingServiceImpl`：注册敏感 TEXT 设置，所有单键和批量写入先完整校验；自定义规则目标及通用节点必须已配置。
- `SystemSettingController`：schema 对此键严格读取，并返回原文有效性标志；数据库故障不能伪装为内置配置。
- `ImageUrlService`：图片展示、Nginx 源、后台导出与迁移复用现有 URL 构建入口；DTO 批次对需要本地解析的记录只读取一次规则。可用 OSS 签名不依赖本地规则。
- `ImageDirectoryRoutingSettings`：位于“系统设置 → 档案浏览”，支持编辑、匹配预览、确认保存、重新读取和恢复内置规则；使用统一请求接口，独立单键保存，不写浏览器缓存。通用运行参数面板排除此键。

不开放 URL/账号密码编辑、正则/区间/任意路径匹配，不修改扫描记录或移动影像；不新增跨节点网络重试、不改变现有多来源降级策略。

## 规则语义

设置键：`imageDirectoryRoutingRules`。示例仅使用日期和节点代号：

```json
[
  { "type": "month", "value": "2026.06", "node": "ba02" },
  { "type": "exact", "value": "2026.06.05", "node": "ba03" }
]
```

优先级固定为精确目录 → 年月 → 通用节点，与数组顺序无关。
`exact` 接受 `YY.MM.DD` / `YYYY.MM.DD`；`month` 接受 `YY.MM` / `YYYY.MM`。
日历校验不转换匹配文本或实际路径，`24.04` 与 `2024.04` 分别匹配。
严格日期校验只约束规则输入，既有扫描记录的路径拼接和历史目录语义保持。
同种匹配方式、相同值不能重复；禁止未知字段、重复 JSON 字段、未知节点、尾随 JSON 文档。
最多 200 条，原始字符串不超过 32768 字符；后端始终是最终校验边界。

| 节点 | 既有配置 |
| --- | --- |
| `fallback` | `image.url`，未命中时使用 |
| `default` | `image.server-url-default` |
| `ba01` / `ba02` / `ba03` | 对应 `image.server-url-ba01/ba02/ba03` |

无目录记录不参与匹配，继续使用 `image.server-url-default`。
设置缺失或合法空白值沿用原 24 条内置规则及原节点缺失回退行为；显式 `[]` 取消日期分流。
自定义规则命中地址缺失节点时拒绝 Nginx URL 解析，不悄悄改选通用节点。
规则原文损坏、超长或数据库读取失败时同样拒绝路由解析；schema 对损坏原文标记无效，前端仍可明确 DELETE 恢复。

## 权限、审计及生效边界

沿用 `/api/v1/settings/schema` 的 `system:read`，以及
`PUT /api/v1/settings/imageDirectoryRoutingRules`、`DELETE` 的 `system:manage`。
未登录返回 401，缺少管理权限返回 403。SETTING 审计记录操作身份和设置键，不记录目录规则原文、源地址或凭据。
继承现有审计持久化机制及其已知边界（#102），不在本次另建审计流水。

保存前冻结本次 JSON，从确认开始防重复；加载确认同样全程占用读取状态。
销毁或 KeepAlive 离页使旧确认/迟到响应失效；同实例已经发出的写入仍占用防重状态直至结束。
页面无法撤回已到达服务器的写入；结果未知时要求重新读取核对，不自动重试 PUT/DELETE。
新实例或其他标签页沿用现有设置的后写覆盖语义，不新增全局写锁或跨标签页幂等协议。

新配置影响后续 URL 解析。单次 URL/DTO 批次使用一次规则快照；**长时间导出或 OSS 迁移不冻结整个任务的规则**，应先暂停相关任务再修改。
逐文件解析沿用既有调用方式，每次需要日期分流的 URL 构建增加一次按设置键的读取；没有针对医院等量任务做吞吐验收。
此处的拒绝指目录路由/Nginx URL 解析。导出存储解析器仍按既有策略尝试其他受控来源；OSS 迁移的 Nginx-only 解析失败则不改选其他节点。

## 验证与恢复

先补 `ImageDirectoryRoutingTest`：原实现 20 项失败（19 断言失败、1 未注册设置错误），再实施。
后续定向验证覆盖路由优先级、路径兼容、配置故障、OSS、权限、审计、确认与缓存离页、schema 损坏状态、PostgreSQL 持久化及浏览器交互，实际结果见本 PR。

功能恢复：在规则面板“恢复内置目录规则”，确认后单键 DELETE。原扫描数据及文件不受影响。
若需恢复某份自定义配置，管理员可在授权 API 或界面重新保存变更前规则；本次不提供版本历史存储。
代码回滚通过 `git revert` 对应 PR squash 提交，无 schema 回退。
隔离 PostgreSQL16 的测试已实际验证保存覆盖、显式空数组、拒绝非法批量写入、DELETE 后恢复原路由。
未验证医院 Windows Server、真实 Nginx/NAS/OSS、Chrome86 和代表性任务吞吐；现场验收仍由 #350 跟踪。


### 本地执行证据（2026-11-15）

- RED：`mvnw.cmd -o -B -ntp -Dtest=ImageDirectoryRoutingTest -Dlocal.config.exclude=true test`，20 项未通过；随后实现。
- GREEN：后端定向 12 类 107 项通过；补充边界后最终重跑 `ImageDirectoryRoutingTest,ImageUrlServiceTest,ImageDirectoryRoutingSettingTest`，46 项通过（与前次有重叠，不相加）。
- PostgreSQL：`mvnw.cmd -o -B -ntp -Dtest=ImageDirectoryRoutingPostgresqlIT -Dlocal.config.exclude=true test`，真实 `postgres:16-alpine` 隔离容器，1 项通过、无跳过；仅本地测试使用 Docker，运行后容器已退出。
- 前端：`pnpm install --frozen-lockfile`、改动源 ESLint/Stylelint、`pnpm exec vitest run src/views/settings/components/image-directory-routing.test.ts`，17 项通过；`pnpm build`（含 vue-tsc）、`pnpm test:compat:chrome86` 通过，兼容检查保留既有依赖 CSS 提示。
- 浏览器：`pnpm exec playwright test e2e/image-directory-routing.spec.ts --config .mrr-dev/routing-playwright.config.ts --project chromium --workers 1`，2 项功能用例和 1 项预热通过。任务端口 13081，沿用 admin 合成会话夹具并仅调整夹具 Origin；demo 模式关闭，全部服务响应使用合成数据拦截。
- 浏览器覆盖优先级预览、重复条件、确认单键 PUT、DELETE 恢复、503 后禁止直接重试和重新加载恢复；查看了亮色、暗色、1024px 窄桌面截图，并断言操作不超出视口。390px 手机布局未作为本次验收（项目默认关闭移动端适配）。
- 独立审查修复了 OSS 回退重复解析、KeepAlive 离页、重载确认防重、损坏原文判定四项问题；最终独立复跑 17 项前端单测并核对后端、PG16 与 E2E 报告，未发现剩余阻断。

首轮 CI 的 963 项单测通过，但集成套件在后续空库迁移测试触发 PostgreSQL 连接数上限。新增测试现将自身 Hikari 池限制为最多 2 个连接、最小空闲 0，并在类结束后关闭自己的上下文，避免增加其他测试的驻留连接。独立检查确认只影响本测试；本地重跑路由持久化和空库迁移两项均通过。
