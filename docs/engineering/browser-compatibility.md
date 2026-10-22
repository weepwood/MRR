# 浏览器兼容实施与验收

关联 Issue：[#381](https://github.com/weepwood/MRR/issues/381)。风险等级 P2。

## 目标与范围

本阶段将 Edge 入口及构建基线从 111 下探到 109，保留 Chrome 86、Firefox 114、Safari 16.4。版本声明不等于完成真实终端验收，具体证据见下表。

现有链路为：HTML 内联检测 → 生产 polyfill → main.ts → 现有路由守卫 / API / 组件。复用现有兼容样式、Clipboard 回退、权限与会话机制，不新增请求或认证通道。

不修改数据库、后端接口、权限、审计、外部票据和导出语义；不兼容 Chrome 85、IE/IE 模式和 EdgeHTML；不同时清理 CSS 遗留债务或升级依赖。未来下探 Chrome 80–84 应单独评估 Flexbox gap、焦点和第三方组件；不可只修改版本数字。

## 文件与实施顺序

1. `package.json` 的 browserslist 作为唯一版本来源；`src/utils/browser-baseline.ts` 供环境诊断与 Vite 共用；`vite/browser-targets.ts` 派生构建目标并注入 HTML。通过入口、环境诊断与 108/109 边界测试验证。
2. HTML 启动检测移除 polyfill 前的 at/findLast 检查，保留基础能力检查并加入 Vue 必需的 Proxy 和原生模块标记 noModule；缺少模块能力的未知浏览器直接显示升级提示。先测试误拒绝与缺失能力，再修改实现。
3. 保留默认 Mock 开发 E2E，增加独立的生产构建回归配置；quality-gate 在选中前端 E2E 时同时执行生产回归，不以 UA 模拟代替旧内核实测。测试读取实际内核主版本，禁止版本不符时继续；不产生包含凭据的 trace、截图或视频。
4. 修正文档中的门槛、通知行为与测试范围，记录实际验证及未确认项。

以上步骤可在单个兼容性 PR 中逐个提交。回滚时切回上一配套前端资源与入口，恢复 Edge 111 门槛；不单独替换某个带哈希的 chunk。无数据库迁移或数据恢复步骤。

## 自动验证

在 `frontend-fantastic-admin` 执行：

```powershell
pnpm lint:tsc
pnpm test:run
pnpm build
pnpm test:compat:chrome86
pnpm test:compat:browser
```

生产测试由 Vite preview 仅在 loopback 9010 端口提供 dist，禁用 Service Worker 干扰；不能复用未知服务器。测试不注入演示登录，不在产物中启用 Mock，以浏览器路由拦截提供明确的合成 API 响应。

固定版本示例（Windows PowerShell 5.1）：

```powershell
$env:MRR_COMPAT_BROWSER_EXECUTABLE = 'C:\BrowserTests\Edge109\msedge.exe'
$env:MRR_COMPAT_BROWSER_MAJOR = '109'
pnpm test:compat:browser
Remove-Item Env:MRR_COMPAT_BROWSER_EXECUTABLE
Remove-Item Env:MRR_COMPAT_BROWSER_MAJOR
```

使用医院批准的隔离浏览器或虚拟机，不替换工作站默认浏览器。浏览器路径和主版本必须同时指定；测试会检查 `browser.version()`，不通过伪装 UA 绕过检查。旧浏览器若与当前 Playwright 协议不兼容，应记录失败并在固定版本虚拟机人工验收，不能记为通过。

## 真实终端发布验收

| 环境 | 必须检查 | 状态 |
| --- | --- | --- |
| Edge 109 / Windows | 生产入口、登录、查询、分页、弹窗、日期控件、影像浏览 | 原生 109.0.1518.140 + 合成 API 的入口、登录、查询、分页、详情、401 回退已通过；日期控件、影像及真实后端待验收 |
| Chrome 86 / Windows | 同上，特别关注 CSS 降级与焦点可见性 | 待固定版本与真实后端验收 |
| 当前 Chromium | 生产构建与合成 API 的表单、查询、详情、会话失效回归 | 151.0.7922.34 已通过；CI 使用其安装的 Chromium，不代表固定版本 |
| Firefox 114 / Safari 16.4 | 对应引擎的核心流程 | 本阶段不新增已验证声明 |

真实后端验收还需检查无会话拒绝、权限不足、过期会话、大病案浏览，以及原有下载取消/失败处理。仅使用合成数据，不输出真实患者信息或会话凭据。HIS 内嵌控件须单独核对内核与 Cookie、弹窗行为。

静态门禁会报告无法自动判定的依赖选择器、color-mix 与容器查询。通过门禁不代表这些规则在基线浏览器生效，也不替代真实浏览器的可读、可点击、可滚动和键盘操作验收。

## 2026-10-22 本地验证记录

- 平台：Windows 10.0.26300，Node.js 24.21.0、pnpm 10.33.0。
- RED：原实现的 Edge 109 判定、未知浏览器缺少 at/findLast、缺少 Proxy 测试失败；独立审查发现的缺模块能力用例也先失败，修正后相关 30 项通过。
- `pnpm lint:tsc`、`pnpm build`、变更文件 ESLint 与 `git diff --check` 通过。
- `pnpm test:run`：62 文件、367 项通过。
- `pnpm test:compat:chrome86`：新生产构建通过；仍提示 29 处新选择器、152 处直接 color-mix 和 1 处容器查询区间语法，未将其记为已验证视觉效果。
- UA 策略 E2E：6 项（含预热）通过，覆盖 Edge 108/109、Chrome/Chromium 85 和 Chrome 86。
- 生产构建测试：原生 Microsoft Edge 109.0.1518.140 与 Chromium 151.0.7922.34 各 2 项通过。API 均为合成响应，没有证明真实后端权限或医院 Windows 7 环境。
- 将实际 Edge 109 的期望主版本指定为 110 时，用例明确失败，确认固定版本检查不会把当前浏览器冒充为目标版本。
- 治理 Python 单测 111 项、`test_inventory.py --fail-on-focused`、`release_baseline.py validate` 通过；兼容配置与基线文件独立变更也会触发 E2E，已先写失败测试确认作用范围。
- 独立上下文只读审查已完成；发现的未知浏览器模块能力边界已修正并复核。

Edge 测试包来自[微软更新目录](https://www.catalog.update.microsoft.com/Search.aspx?q=edge+109+x64+stable)。CAB 的 SHA-1 为 `1f83a194c1919bb272a7ab8b3c0ad7d8d401ae86`；MSI 与最终 msedge.exe 的 Authenticode 签名均为 Microsoft Corporation，验证状态 Valid。包仅解压至被忽略的测试缓存，未安装、未修改系统默认浏览器。该路径不属于正式部署组件。
