# MRR GitHub 仓库保护设置清单

该清单用于把代码中的治理规则与 GitHub 仓库设置连接起来。设置完成后，每次规则变更都应重新核对 Job 名称。

## main 分支保护

在 `Settings → Branches` 或 Rulesets 中为 `main` 配置：

- [ ] Require a pull request before merging；
- [ ] Required approvals：至少 1；
- [ ] Dismiss stale approvals when new commits are pushed；
- [ ] Require review from Code Owners；
- [ ] Require conversation resolution before merging；
- [ ] Require status checks to pass before merging；
- [ ] Require branches to be up to date before merging；
- [ ] Block force pushes；
- [ ] Block deletions；
- [ ] 不允许管理员绕过上述规则，或仅保留明确的紧急流程；
- [ ] 禁止直接推送到 `main`。

## 必须通过的状态检查

至少要求：

- [ ] `governance-gate / governance`；
- [ ] `quality-gate / release-baseline-gate`；
- [ ] `quality-gate / frontend-gate`；
- [ ] `quality-gate / backend-gate`；
- [ ] `quality-gate / windows-management-gate`；
- [ ] 监控、发布或安全相关工作流中被项目确认为稳定的汇总 Job。

不要把可能因路径条件被跳过的内部 Job 直接设为 required，优先选择始终产生结果的汇总 Gate。

## 合并策略

推荐：

- [ ] 默认使用 squash merge；
- [ ] 合并提交说明使用中文；
- [ ] 关闭不需要的 merge commit，减少历史噪声；
- [ ] 不启用无人审查的自动合并；
- [ ] Dependabot PR 仍需人工 Review，并确认升级范围、变更日志和 CI 结果。

## Actions 安全

- [ ] Workflow permissions 默认 `Read repository contents`；
- [ ] 只有确实需要时才授予写权限；
- [ ] 不在 `pull_request_target` 中执行来自 PR 分支的未受信任脚本；
- [ ] Secrets 不提供给来自 Fork 的普通 PR；
- [ ] 第三方 Action 固定到可信主版本，关键发布链路可进一步固定到 commit SHA；
- [ ] Artifact 中不得包含患者信息、生产配置或密钥。

## Issue 与安全报告

- [ ] 关闭空白 Issue，使用结构化表单；
- [ ] 启用 Private vulnerability reporting；
- [ ] 高风险变更先使用 Change Proposal；
- [ ] P0/P1 Issue 明确责任人、复现证据、止损、回滚和验证结果。

## 发布保护

- [ ] Tag 和 Release 只能从已通过门禁的 `main` 创建；
- [ ] 发布版本与后端、前端、文档、Windows 包基线一致；
- [ ] 发布产物保留校验值；
- [ ] 发布前验证数据库迁移、配置模板、Windows 启动与回滚步骤；
- [ ] 不从未经 Review 的临时分支直接制作正式包。

## 紧急变更流程

仅限 P0：

1. 创建 P0 Issue，记录影响和止损；
2. 从 `main` 创建最小修复分支；
3. 不跳过测试和治理门禁，除非门禁本身故障；
4. 至少进行一次独立 Review；
5. 合并后立即验证生产并记录结果；
6. 若使用了例外，24 小时内补齐测试、文档和复盘 Issue。

## 定期复核

每月或重大版本后核对：

- required checks 是否仍使用当前 Job 名称；
- 是否存在长期失败或被绕过的门禁；
- CODEOWNERS 是否覆盖新增模块；
- Dependabot 分组和频率是否产生过多噪声；
- 审查者是否只勾选模板而没有提供实际证据；
- 警告规则是否应升级为阻塞，或因误报需要收窄。