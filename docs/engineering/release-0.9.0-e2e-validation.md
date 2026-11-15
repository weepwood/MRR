# 0.9.0 发布候选浏览器测试修正

关联 #592；风险 P3，仅修正浏览器测试与本记录。负责人 Codex，分支 `codex/592-release-e2e-alignment`，worktree `D:\Project\CodexWorkTress\592-release-e2e-alignment\MRR`，状态待合并。发布整体仍按 P1 提案执行。

## 失败与最小范围

固定提交 `77be4dd51b0edc1ec4e7ee16b82b7a87f34f0284` 的 main 集成成功，但完整 dry-run [43711408071](https://github.com/weepwood/MRR/actions/runs/43711408071) 在全量 Mock E2E 中出现 4 项失败、150 项通过。发布被阻断，没有创建标签或公开 Release。

- 字体测试用 `**/api/**` 拦截所有响应，实际命中 Vite 的 `/src/api/modules/user.ts`，将 ESM 模块替换为 JSON；失败快照为“系统启动失败”，登录页没有挂载。两个字体用例在本地原样复现失败。原生产 preview 使用 `/assets/`，未覆盖此冲突。
- 标题测试仍断言 28px 和卡片渐变/阴影；已合并 #568 的设计规范明确为 26px、实色细边框、无阴影。参见 [全局视觉验证](./global-ui-polish-verification.md)。

修改仅限三份 E2E：字体拦截限定到 `/api/v1/` 命名空间，保留首屏小于 2MiB、三个字重、不下载完整字库及生僻字完整字库/CDP 实际字体验证；标题测试对齐既有设计，明确验证实色背景、1px 实线边框、圆角、无渐变/阴影及 plain/card 切换差异。

不修改应用、路由、权限、字体文件、CSS、依赖、测试超时或发布门禁，不跳过断言。问题用测试修正提交回滚，不涉及数据或配置恢复；不改变产品禁止直接回退旧应用的发布基线。

## 定向验证

- `corepack pnpm install --frozen-lockfile`：通过，锁文件未变。
- 原字体测试本地 Mock dev-server 复现 2 项失败；独立审查以 Playwright 实际 glob 实现及 Vite 实际模块导入证实原因。
- `corepack pnpm exec playwright test e2e/frontend-font-loading.spec.ts e2e/page-header-style.spec.ts e2e/mock-mode.spec.ts --project chromium --workers 1`：包含 warmup 共 9 项通过，38.5s，使用既有合成认证夹具与 Mock。
- Mock 通过 pnpm 10.33.0 单独启动，`VITE_APP_DEMO_MODE=false`；本机默认 pnpm 12 的嵌套启动版本不符，没有修改仓库版本限制。
- `git diff --check`：通过。独立审查及最新 HEAD 的 PR CI 结果记录在关联 PR；修正合并后重新固定 main SHA，重新执行完整候选和 Windows 恢复验收。

这些证据不替代真实医院、Chrome 86 或存储验收。没有把失败的旧候选当成发布完成。
