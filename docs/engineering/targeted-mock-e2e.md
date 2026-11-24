# 定向 Mock 浏览器回归

关联当前 #736 / #737 和 #724 验收。P2 补充测试入口，不改 main 保护、quality-gate、发布完整验收或生产权限。Pages PR 已安装 Chromium；复用同一次 runner，仅当 PR 新增/修改顶层 e2e/*.spec.ts 时运行这些 Mock 用例（预热依赖照常）；monitoring-refresh-status 同时带上 monitoring-polling-performance。无 spec 改动仍保持原日常检查，无全量 E2E。工作流使用固定 SHA、NUL 分隔 diff、严格文件名校验、参数数组，无 shell 展开用户路径。手动 frontend-mock 与正式发布全量策略不变。

测试先提交 f67219f（RED 未执行）；Python 选择与参数回归、实际 Pages 冒烟及治理检查待 CI。现有分支需同步此入口后取得对应的真实定向结果，不将原 CI 冒烟冒充 E2E。独立 revert；无本地 worktree。当前接口不支持 workflow_dispatch，故增加自动有界入口。
