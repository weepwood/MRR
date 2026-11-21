# 前端无后端 Mock 模式

该模式用于后端未启动、接口尚未完成或 CI 无法连接后端时的前端开发与自动化测试。

## 启动

```bash
cd frontend-fantastic-admin
pnpm install
pnpm dev:mock
```

浏览器访问 `http://localhost:9000`。Mock 模式会自动开启展示模式，不需要登录，也不会向 Java 后端发送请求。

常规联调仍使用：

```bash
pnpm dev
```

## 构建

```bash
pnpm build:mock
```

Mock 构建产物位于 `dist-mock`，可用于纯前端演示。正式构建仍使用 `pnpm build`，不会包含 Mock 服务。

## 自动化测试

```bash
pnpm test:e2e
```

Playwright 默认以 `mock` 模式启动 Vite，因此本地和 CI 均不依赖后端。`e2e/mock-mode.spec.ts` 会检查：

- 统计摘要与病案分页数据
- 响应趋势与慢接口数据
- 条件筛选
- 内存级设置写入与读取

## 已模拟模块

- 登录、用户、角色与权限
- 病案扫描记录、条件查询、档案搜索与批量下载
- 患者查询
- 病案统计总览、趋势、类型汇总、明细和 CSV 导出
- 系统日志、图片访问审计与审计分析
- 接口响应分析与前端指标上报
- 系统健康、JVM、内存、线程、GC 与 Actuator 指标
- OSS 迁移统计、待迁移列表、目录、日志、任务与上传
- 系统设置读取和保存

## 数据行为

Mock 数据固定生成，便于截图、回归测试和断言。创建、更新、删除、设置保存等操作只保存在当前 Vite 进程内存中，重启开发服务器后恢复初始数据。

Mock 请求仍使用前端现有的 `/proxy` 地址。`vite-plugin-fake-server` 在 Mock 模式下接管该前缀；普通开发模式则继续将 `/proxy` 转发到真实后端，两者不会同时工作。

## GitHub Pages 静态演示

Pages 使用独立的 `pages` 构建模式，复用现有 Mock 数据；演示站不包含真实的患者数据、影像凭据或后端连接。

```bash
cd frontend-fantastic-admin
pnpm install --frozen-lockfile
cp .env.pages.example .env.pages
pnpm build:pages
node e2e/smoke-pages-demo.mjs
```

预期站点地址：`https://weepwood.github.io/MRR/`。页面使用 `/MRR/#/records` 等 hash 路由，以兼容 GitHub Pages 不提供 SPA 回退规则的限制。浏览器 Mock 数据在页面会话中模拟，重新载入页面会重置。原 `dev:mock` 与正式 `build` 不受影响。

部署工作流见 `.github/workflows/deploy-pages-demo.yml`。合并到 `main` 后，在仓库 **Settings → Pages → Build and deployment → Source** 选择 **GitHub Actions**，由工作流自动发布；如仓库已有其它 Pages 站点，请先确认覆盖策略。GitHub Pages 为公网静态站，**不要上传真实病历、患者影像、内网地址、口令或凭据**。

Pages 产物用 CSP 阻止真实 API 连接。已模拟的请求在浏览器内完成，缺少 Mock 的请求会直接失败。

如需撤下已发布的站点，在 **Settings → Pages** 执行 **Unpublish site**，并撤销自动发布变更以避免重新发布。

Nginx 文件浏览器在静态演示中提供样例目录，可预览并下载样例文本。真实存储与文件流请在内网联调环境验证。
