# main 分支保护实施记录

关联 #362、#450。用户采用单人维护策略：强制 PR、CI、解决审查对话、禁止强推与删除，独立审查继续留证；批准人数为 0，避免作者自批死锁。

## 当前目标

`quality/main-branch-protection.json` 定义两个稳定必需状态：

- governance
- pr-gate

来源固定为 GitHub Actions App（15368），strict=true。管理员同样受限，线性历史、解决对话、禁强推/删除继续生效。按路径选择的内部 job 不直接设为 required。

P0/P1 的独立审查义务不由批准人数 0 免除。完整发布验收必须先于正式 tag 和公开 Release。

## 迁移与核验

不得以目标 JSON 或本地测试声称远程已启用。先合并 #450 的新门禁并确认 main 上 pr-gate 实际成功，旧状态由过渡矩阵继续转发真实汇总结果。

```powershell
python -m scripts.ci_protection
python -m scripts.ci_protection --apply --snapshot "$env:TEMP\MRR-ci-protection-before-450.json"
gh api repos/weepwood/MRR/branches/main/protection
```

工具仅 PATCH required_status_checks，不覆盖其他保护。读回核对 strict、两个 App 绑定检查、enforce_admins、0 批准、对话解决、线性历史和禁强推/删除。迁移成功后才通过后续 PR 移除 legacy-gates 矩阵。

## 回滚

先通过 PR 恢复旧工作流及检查上下文，再执行：

```powershell
python -m scripts.ci_protection --restore "$env:TEMP\MRR-ci-protection-before-450.json"
```

不要删除分支保护或使用管理员绕过失败 CI。快照保存的是实际旧配置，不得覆盖或用目标文件代替。

## 历史实施证据

2026-10-18 应用初始五项保护，随后 #362 合并 #366/#368/#369 等批次，启用 governance、release-baseline-gate、frontend-gate、backend-gate、windows-management-gate、windows-package-gate、live-e2e、windows-database-recovery 八项状态并读回。

#450 将 PR 检查与发布验收分层；新的远程应用证据应在完成迁移后记录，不能将旧八项记录解释为两个新状态已经启用。
