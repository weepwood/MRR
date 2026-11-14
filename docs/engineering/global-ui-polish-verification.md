# 全局视觉优化验证

任务：用户要求优化项目 UI，并明确优先“全局视觉：导航、配色、字体和整体质感”。关联 #89 的渐进设计系统目标；本轮不声称完成该 Issue 的全部业务样板验收。

- 基线：`main@4e59857b`，2026-11-12 从最新 `origin/main` 创建。
- 分支：`codex/global-ui-polish`。
- worktree：`D:\Project\CodexWorkTress\global-ui-polish\MRR`。
- 负责人：本次 Codex；状态：待合并。
- 风险：P2，共享主题和布局影响多个页面；无接口、权限、数据库、部署方式或依赖变更。

## 调查与范围

调用链为现有 `themes/index.ts` → 设置 Store 应用主题 → `tokens.css` / 兼容层 → 布局导航、共享页头及 Element Plus 桥接。继续复用同一主题、请求、权限和状态机制。

已确认的视觉问题：亮色卡面与工作区过于接近；标题卡和工作区叠加装饰性渐变；暗色选中导航对比度偏低；Element Plus 按需加载覆盖表格行表面。导航基线在默认蓝色下，浏览器按实际 sRGB 颜色复核约为亮色 4.49:1、暗色 2.90:1。初轮测试曾错误地按 0–255 解析归一化的 `color(srgb ...)`，已修正计算并独立复核，不使用该轮数值作为证据。

修改集中于现有主题表面、壳层样式、共享页头、导航文字及焦点、表格主题桥接。标题 plain/card 统一 26px/700，窄屏 22px；33px 控件、圆角设置、导航结构、查询、授权与所有业务入口保留。

明确不处理：业务页信息架构迁移、查询/API/Store 改造、账号权限、导出与影像流程、登录重设计、历史 CSS 全面清理及其他 worktree 的重设计。原有自定义主色在其他业务控件上的对比度不在本轮保证范围。

## 验收与证据

- 使用仓库 Mock 与原有认证夹具，页面及截图仅含合成数据。
- `corepack pnpm install --frozen-lockfile`：通过，pnpm 10.33.0，未修改依赖与 lockfile。
- `corepack pnpm lint:tsc`：通过。
- 定向 ESLint、Stylelint：通过（页头空行问题修正后复查）。
- 新增 `e2e/global-visual.spec.ts`，6 项通过：验证明暗选中导航对比度、当前链接语义、键盘焦点、收起侧栏后通过浮层进入权限页、1920/1440/1366/768/375px 无页面横向溢出；验证五种主色以及内容卡/行/工作区分层。独立审查实测其中最低为白色主色下亮色 5.74:1。
- 兼容层用例在现代 Chromium 强制启用已有回退分支，检查明暗导航文字与焦点；不等同于真实 Chrome 86。
- 既有 `shared-ui-layout.spec.ts` 与 `control-layout.spec.ts`：26 项通过，覆盖页面壳、圆角设置、分页、控件、日期浮层和窄屏。
- 最终合并运行三份定向 Playwright：32 项通过（37.1s）。
- `corepack pnpm build`：通过（Vite 23.97s）；`corepack pnpm test:compat:chrome86`：通过，仍提示依赖产物中的 1 处容器查询、29 处现代选择器及 140 处直接混色声明，不把静态门禁视为全站兼容证明。
- 仓库治理：216 项 Python 工具测试通过；`test_inventory.py --fail-on-focused` 与 `release_baseline.py validate` 均通过。PR 的 `governance-gate` / 范围检查结果以实际 Actions 为准。
- 浏览器人工检查读取了亮/暗用户页、side 模式概览、head 模式设置、375px 明暗截图；均无页面横向溢出或 pageerror。截图保存在工作区之外，只含 Mock 合成内容。
- impeccable detector 仅提示既有 Inter 字体“常见”。按既有设计和 Operate 模式保留字体栈，未为品牌独特性引入新字体；该提示不是功能故障。

仅在 Windows 开发机与现代 Chromium Mock 下验证；医院显示器、缩放、真实 Chrome 86、Windows Server/Nginx 和真实存储未验收。后端和数据库未变更，本轮不以全量后端测试代替 UI 证据。

## 独立审查与回滚

独立上下文直接读取 Issue #89、架构/治理、diff 和测试，提出并修正：极端自定义主色对比度、暗色兼容层优先级、按需表格样式覆盖、plain/card 标题重复定义，以及收起导航和异步表格测试缺口。

最终独立审查未发现阻断项。既有主导航分组纯键盘语义缺口另记 [#567](https://github.com/weepwood/MRR/issues/567)，本轮只验证“悬停展开浮层后键盘激活叶子链接”，不声称全程纯键盘可用。

独立提交后可整体 `git revert` 并重建前端；无数据迁移、配置持久化格式变化或数据恢复步骤。不回退既有认证与权限边界。回滚产物和医院终端演练尚未执行。
