# main 分支保护实施记录

关联 #362。用户已确认单人维护策略：强制 PR、CI、解决审查对话和禁止强推，
独立审查继续留证；增加第二名维护者后再考虑强制批准人数与 Code Owner 审批。

## 规则

实际配置保存在 `quality/main-branch-protection.json`。要求五个稳定汇总检查，
来源固定为 GitHub Actions App（15368）；不把会按路径跳过的内部 Job 设为 required。
管理员同样受限，线性历史开启，禁止强推和删除。要求 PR，但批准人数为 0，避免作者自批死锁；
这不是免除 AGENTS.md 的 P0/P1 独立审查要求。

## 应用与核验

使用已授权的仓库管理账户，在根目录执行：

```powershell
gh api --method PUT repos/weepwood/MRR/branches/main/protection --input quality/main-branch-protection.json
gh api repos/weepwood/MRR/branches/main/protection
```

读回必须核对 strict、enforce_admins、五个 App 绑定检查、批准人数 0、
conversation resolution、force pushes/deletions。不通过真实强推或直接推送来试验保护。

## 实施前状态及回退

实施前保护 API 返回 `404 Branch not protected`，已有 Ruleset 是禁用状态；
不修改该既有 Ruleset。恢复到实施前状态只能由授权管理员执行：

```powershell
gh api --method DELETE repos/weepwood/MRR/branches/main/protection
```

删除保护会降低仓库控制，只在本次配置错误的恢复流程使用，不能为绕过失败 CI 临时取消保护。
后续若已有额外保护，先重新读取并保存实际配置，不能再沿用本次“原来未保护”的快照。
未来 Job 改名先安排规则迁移，避免 required check 永远等待。
