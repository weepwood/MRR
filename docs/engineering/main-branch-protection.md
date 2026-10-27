# main 分支保护实施记录

关联 #362、#450。用户采用单人维护策略：强制 PR、CI、解决审查对话、禁止强推与删除，独立审查继续留证；批准人数为 0，避免作者自批死锁。

## 当前配置

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

2026-10-27，#451 合并为 `005ffce7145e6ea475931f501dd8a231e1e33347`。该 main 的 [quality-gate 40473921669](https://github.com/weepwood/MRR/actions/runs/40473921669) 全部成功，包含生产 JAR 联调、真实 PostgreSQL 和 Windows PowerShell 5.1 恢复。

随后实际执行 `scripts.ci_protection` 只读预览、apply、restore、再次 apply，每步均读回成功：八项 → governance/pr-gate → 原八项 → governance/pr-gate。两个状态均绑定 App 15368，strict=true；除 required_status_checks 外，远程全部保护字段与原始快照完全一致。管理员约束、0 批准、对话解决、线性历史、禁止强推/删除均保留。

原始与第二次 apply 前快照保存在实施机 `$env:TEMP\MRR-ci-evidence-450\protection-before.json`、`protection-before-second-apply.json`；最终读回保存在同目录 `protection-after.json`。回滚演练在旧转发矩阵仍存在时完成；矩阵删除后必须先通过 PR 恢复旧检查生成，再恢复快照。
