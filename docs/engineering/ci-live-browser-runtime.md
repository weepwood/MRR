# 主线真实浏览器运行时准备

关联 #508；风险P1（验证链路）。负责人Codex；分支`codex/508-ci-live-chromium`；worktree `D:/Project/CodexWorkTress/ci-live-chromium/MRR`；状态进行中。基线main `43840a1e`。

合并#507后的main run41468040399在真实浏览器步骤报Chromium可执行文件不存在。后端/PG、前端测试与构建均通过，两个真实浏览器用例未成功完成，不能当作已验收。

根因：可复用windows-release-package中浏览器安装位于`inputs.full`的Mock E2E块，真实联调在`inputs.full || inputs.live_checks`时运行；main的full=false/live_checks=true没有准备浏览器。

最小修复：既有锁定Playwright的安装命令独立为步骤，使用full或live_checks条件，并位于所有浏览器用例前。Mock E2E仍仅full，真实联调条件不变，普通PR不增加浏览器流程。无业务、认证、权限、数据库、依赖或保护分支改动。

先补workflow合同失败测试，再移动安装命令；定向Python测试、独立审查和当前PR候选门禁。真实浏览器是否运行以CI证据为准。不得以重跑相同缺失配置、扩大跳过或放宽断言替代。

RED新增合同用例1项失败；GREEN执行`python -m unittest scripts.tests.test_ci_build_reuse scripts.tests.test_ci_gate -v`，24项通过。工程文档、发布基线、测试资产及diff检查通过。ci_browser_review独立读取Issue、规范、调用方/full模式条件、diff及日志，无阻塞；未重复全套测试。

完整候选可验证full=true两类浏览器，不能冒充main live-only已执行；后者需合并后主线实际证据。配置组合：false/false均不运行浏览器；false/true安装后只运行真实联调；true/false与true/true安装后运行两类浏览器。仅准备一次浏览器运行时，不改测试启用规则。

回滚：独立revert本批工作流与合同测试即可，无生产部署/数据恢复，回滚会恢复main live-only缺失运行时，应前向修复。不创建tag或Release，不修改发布权限。
