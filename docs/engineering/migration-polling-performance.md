# OSS 迁移页后台轮询性能

任务与验收标准：[Issue #571](https://github.com/weepwood/MRR/issues/571)。风险 P2。基线 `origin/main@4e59857b`，分支 `codex/migration-polling-performance`，专属 worktree `D:\Project\CodexWorkTress\migration-polling-performance\MRR`，负责人本次 Codex，状态待合并。

## 调查与范围

调用链为 `routes.ts` 的 `cache:true` → 布局 KeepAlive → OSS 迁移页 → 现有 `useMigrationPolling` → 现有 OSS GET API。原轮询只在 scope 销毁时清理，缓存页停用与标签页隐藏后仍运行。

任务每 2 秒查询一次；概览每 15 秒调用一次，内部并发读取统计、就绪检查和任务列表。快速响应、任务持续运行的理想情况下，计数是每分钟 30 次任务查询与 4 次概览，即 42 个 GET；这不是实际医院流量或 CPU 测量。

本批只给现有轮询增加活动状态与已有 VueUse 文档可见性机制，页面通过 activated/deactivated 传递状态。暂停时清理定时器、使旧任务响应失效并保留已启用的刷新意图；恢复时补读已启用的任务和概览。慢请求沿用 busy 标记，保持不重叠；后台迁移任务继续执行，暂停只影响浏览器读取。

不修改 API、权限、后端任务、数据库、存储来源、迁移创建/取消/重试、依赖或部署配置。不引入第二套轮询框架，不处理其他页面、首屏包体或数据库索引，也不混入其他 worktree/PR。

## 测量与验证

环境为 Windows 开发机、Node 24.22.1、pnpm 10.33.0、真实 Vue 生命周期与现代 Chromium；接口和任务均使用合成数据。

| 场景 | 优化前 | 优化后 | 证据范围 |
| --- | --- | --- | --- |
| 假时钟停用/隐藏 60 秒 | 30 次任务读取；概览仍有定时器 | 0 次任务与概览读取 | 初轮新增用例在原模块 4 项失败，任务请求次数实际为 30 |
| 假时钟可见 60 秒 | 原间隔 2 秒/15 秒 | 30 次任务、4 次概览 | 定向控制用例，证明正常频率保留 |
| 浏览器离开缓存页 60 秒（手动关闭概览） | 总读取由 7 增至 36，新增 29 次 | 0 次新增读取 | 同一 Playwright 用例分别运行原版和修复模块；差异来自实际路由/请求与虚拟时钟 |
| 返回缓存页 | 原有行为 | 任务继续跟踪，概览保持手动 | 浏览器返回后阶段选择仍为限定批次，证明组件未卸载重建 |

执行记录：

- `corepack pnpm install --frozen-lockfile`：通过，无依赖/lockfile 改动。
- `corepack pnpm exec vitest run src/views/oss-migration/composables/useMigrationPolling.test.ts`：18 项通过，涵盖暂停/恢复、迟到任务响应、慢任务/概览、失败与终态、显式关闭、非空概览发现活动任务以及销毁。
- `corepack pnpm exec playwright test e2e/migration-polling-performance.spec.ts --project chromium --no-deps --workers 1`：1 项通过。原模块对照在新增读取断言失败，恢复修复模块后通过。使用真实菜单离开、浏览器历史返回及模拟 60 秒，不触发任何迁移写操作。
- `corepack pnpm lint:tsc`、定向 ESLint 和 `git diff --check`：通过。
- `corepack pnpm build`：通过，Vite 20.19s；`corepack pnpm test:compat:chrome86`：通过，仍提示既有依赖的现代 CSS 降级事项。
- 仓库治理与范围检查以 PR 的实际 `governance-gate` / `pr-gate` 为准。

## 语义与剩余限制

自动概览原本会通过服务端结果发现活动任务并调用 startPolling，本批保留该机制。恢复不直接重启已失败/终态停止的任务轮询；但已开启的概览取得新的活动任务后，仍会重新跟踪。独立审查发现原规格对此表述有歧义，Issue 已明确，并补充非空概览回调用例，而非永久禁止任务发现。

在途 GET 不承诺网络取消；已开始的概览回调可以完成，产生的后续 startPolling 在停用期间仍不会建定时器。任务读取的迟到响应由已有 generation 检查隔离。不得将本批视为全站异步响应隔离改造。

独立上下文直接读取 Issue、架构/治理、diff、测试和真实页面回调，未发现阻断项，并独立验证隐藏 60 秒不新增读取、恢复后概览重新发现活动任务。真实 Chrome 86、医院终端、PostgreSQL 大表负载、真实存储与 CPU/内存占用未测，不将本地计数外推为全项目性能百分比。

## 回滚

整体 revert 本 PR 的 squash 提交并重建前端；无数据迁移或数据恢复步骤。手动关闭概览的浏览器流程已验证；不改写数据库或生产配置。回滚包重建和现场恢复未执行。
