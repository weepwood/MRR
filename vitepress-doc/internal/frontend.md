# 前端工程

## 技术基线

前端目录为 `frontend-fantastic-admin/`：

- Vue 3.5
- TypeScript 5.9
- Vite 8
- Vue Router 5
- Pinia 3
- Element Plus 2.13
- ECharts 6
- UnoCSS 66
- Vitest 与 Playwright
- pnpm 10.33.0

Node.js 必须满足 `^20.19.0 || >=22.12.0`，仓库推荐 Node.js 22。

## 浏览器兼容

正式支持的最低浏览器版本：

| 浏览器 | 最低版本 |
|--------|----------|
| Microsoft Edge | 111 |
| Google Chrome | 109 |
| Firefox | 114 |
| Safari | 16.4 |

不支持 Internet Explorer、Edge IE 模式和旧版 EdgeHTML。生产环境优先使用医院统一维护的最新稳定版 Edge 或 Chrome。

Chrome 109 的兼容目标是保证系统可访问，并支持登录、菜单导航、查询、分页、详情、编辑、设置和影像档案袋基础浏览等核心操作。Chrome 109 不支持 `color-mix()` 等部分视觉增强能力，相关效果通过兼容样式降级，不要求与最新版 Chrome 完全一致。

兼容目标同时声明在：

- `package.json` 的 `browserslist`，用于 Autoprefixer 等 CSS 工具。
- `vite.config.ts` 的 `build.target`，用于固定 JavaScript 构建目标。

`index.html` 中的浏览器检测通知独立于 Vue 应用启动。检测到版本过低、IE、旧版 Edge，或未知浏览器缺少必要功能时，会显示置顶通知。视觉增强能力缺失不会阻断应用启动；通知不会自动消失，只能由用户手动关闭，关闭状态仅保留在当前浏览器会话中，下次重新打开浏览器仍会提示。

普通内网 HTTP 页面无法使用原生 Clipboard API 时，应用启动阶段会安装仅包含 `writeText` 的降级实现。HTTPS 或安全上下文仍使用浏览器原生 Clipboard API。

Playwright 默认同时运行 Chromium、Firefox 和 WebKit。它用于验证三类浏览器引擎，不代表已经验证每个最低版本；调整最低版本时还应使用固定版本浏览器执行人工回归。

Chrome 109 发布回归至少检查：

- 生产构建页面可打开且不白屏。
- 登录、退出和错误提示正常。
- 菜单展开、路由切换、查询、重置、分页和排序正常。
- 表格、表单、弹窗、抽屉、下拉框和日期选择器可操作。
- 影像档案袋可查询档案、切换档案并浏览图片。
- 系统设置可读取和保存基础配置。
- 控制台没有阻断主流程的未捕获异常。

必须使用真实 Chrome 109 或固定版本虚拟机验证生产构建，不能只修改 User-Agent 或只测试 Vite 开发服务器。

## 目录职责

| 目录 | 职责 |
|------|------|
| `src/views/` | 页面级业务模块 |
| `src/components/` | 通用组件和业务复用组件 |
| `src/router/` | 固定路由、权限路由和文件系统路由兼容 |
| `src/store/` | Pinia 状态管理 |
| `src/api/` | Axios 请求封装和接口定义 |
| `src/assets/styles/` | 设计令牌、全局基础样式和页面样式 |
| `src/utils/` | 格式化、导出、错误处理等工具 |
| `public/` | 静态资源和 `healthz.txt` |

## 路由体系

业务菜单分为：

- 系统：用户管理、权限管理、系统设置。
- 业务：记录、患者、统计、统计明细、病案统计、档案装箱、OSS 迁移、影像档案袋。
- 运维：日志、图片访问审计、系统监控、服务状态、接口响应分析。
- 帮助：帮助与文档。

特殊路由：

- `/status`：公开页面，不进入管理端布局。
- `/archive`：独立影像档案袋，不缓存页面实例。
- `/archive/embed`：管理端内嵌入口。

影像档案袋不进入 `KeepAlive`，用于确保离开页面时清理全局沉浸样式、键盘监听、水印定时器和图片状态。

## 权限控制

路由 `meta.auth` 声明访问权限：

```ts
meta: {
  title: '系统监控',
  auth: ['system:read'],
  cache: true,
}
```

前端权限只用于界面和路由控制，后端仍必须独立校验权限。

## 请求与错误处理

Axios 请求层负责设置 `baseURL`、携带认证信息、处理 401，并对未被页面处理的请求错误提供一次兜底提示。页面已经显示业务错误时，请求层不应再次弹出相同错误。

## 系统设置

运行时设置优先级：

1. 服务端 `mr_system_settings`。
2. 浏览器本地配置回退。
3. 代码默认值。

已经有实际消费方的设置包括：

- 系统名称。
- 影像栏默认模式。
- 单页或连续预览模式。
- 缩略图目标宽度和首批渲染数量。
- 图片自动适应。
- 记住影像选择状态。
- 水印开关与透明度。
- 页面标题风格。
- 全局圆角系数。
- 科室配色。
- 档案袋局部显示设置和本地浏览偏好。

禁止在设置页展示尚未接入业务逻辑的占位配置。

## 设计系统

全局样式入口为 `src/assets/styles/globals.css`，由其加载令牌、基础样式、公共组件和页面兼容层。页面应优先使用：

- `MrrPageHeader`
- `MrrMetricCard`
- `MrrSectionCard`
- `MrrFilterBar`
- `MrrDataTablePanel`
- `MrrStatusTag`

圆角应读取全局令牌，不继续硬编码多个互不一致的值。

## 图表体系

业务图表统一使用 ECharts 基础层：

- `useMrrChart`：实例生命周期和响应式管理。
- `MrrChart`：通用容器。
- `MrrChartCard`：标题、说明、加载、空状态和摘要。
- 折线、柱状、双轴柱线、环形、横向排行和迷你趋势组件。

图表必须支持明暗主题、ResizeObserver、KeepAlive 恢复、实例销毁、加载状态和空数据状态。不要在新页面重新实现手写 SVG 坐标计算。

## 影像档案袋

规范 URL：

```text
/archive?bah=00001234
/archive?sjh=00789123
/archive?bah=10000000&sjh=00789123
/archive?id=<opaque-token>&bah=00001234&sjh=00789123
```

病案号和上架号进入接口及 URL 前统一为八位数字。病案号大于或等于 `10000000` 时必须同时提供上架号。

图片刷新应更新并持久化版本参数，避免浏览器继续复用旧缓存。档案列表、患者卡片、类型筛选和缩略图区域的显示项由当前档案袋显示设置统一控制。

## PDF 导出

选中导出 PDF 在浏览器端完成：

1. 使用 `fetch` 读取图片 Blob。
2. 使用 Canvas 转换为 JPEG。
3. 根据方向创建 A4 页面。
4. 保持比例居中写入 PDF。
5. 生成 Blob 并下载。

图片服务必须允许前端来源的 CORS。不要恢复已删除的后端图片代理或 PDF 合成接口，除非形成新的架构决定。

## 开发命令

```bash
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
pnpm dev:mock
pnpm lint:tsc
pnpm lint:eslint
pnpm lint:stylelint
pnpm test:run
pnpm test:e2e
pnpm build
pnpm build:mock
```

PR 至少应保证新增和修改文件通过定向 ESLint、Stylelint，以及完整 TypeScript 检查和生产构建。
