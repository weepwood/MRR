# 后端集中治理本地候选冻结（2026-11-28）

状态：本地定向回归通过，正式发布 BLOCKED。冻结生产/测试源码，不冻结未提交工作树，不等于集成或发布批准。

- 实测源码：`83a4e280cfa985350724542cc56aa0183ffcc8ea`，Git tree `cd08ee597b54558938dc6a1f3d5124e95f8c7c14`。
- 相对远端 `74b08bd2d98e5691eaf2ba635ea8c02f542f919f`：24提交、43文件（21生产Java、1 MyBatis XML、20测试Java、1发布说明），+1937/-187。
- 29个单测类、11个PG集成类，共270 Java + 106 PG16 = 376项，零失败、错误、跳过，40.539s，BUILD SUCCESS。隔离数据库正常停止。含真实回环HTTP、实际文件、日志、事务竞争及统计查询；不代表完整生产部署。
- 证据目录 `/Volumes/data/AI-Development/artifacts/mrr-final-local-candidate-20261128.Aycc5Q`：head.txt、tree.txt、files.txt、numstat.txt、commits.txt、source.sha256、verify.log、verify.exit、stop.log；43文件摘要校验一致。未重跑历史RED或未受影响全部测试，Jacoco跳过。
- 累计审查：复用第28节对前12生产Java累计diff的审查，并核对后续全部生产增量及测试变更摘要；不是所有测试逐行独立人工审批。历史迁移、部署、CI无改动。冻结之后只允许已证实的阻塞修复，任何源码变化须重新确定验收范围。

## 待负责人确认的安全契约（建议，非已批准决策）

| 事项 | 当前边界 | 建议及停止条件 |
|---|---|---|
| 单Token撤销 | 新请求实时检查，凭据版本与管理事务授权已强化；在途中止未定义 | 保留单会话语义；确认敏感写提交前的授权边界，勿擅自全端注销 |
| 异步医疗任务 | OCR分阶段复核；导出创建/管理/下载授权；Worker执行期权限未全面定义 | 确认任务开始与安全检查点的撤权处理，避免发布未经授权成果 |
| 关键审计持久化 | 事务后结构化安全日志；等配置队列饱和实测可丢WARN | 明确必需事件、持久性及失败时业务策略；未批准不得启用冻结审计引擎或全局阻塞 |
| 任务恢复与部署 | 现有单实例及独占维护约束 | 不擅自开启跨实例恢复；确认真实部署拓扑和恢复流程 |

## 发布准入剩余门禁

- PR #850 仍OPEN/DRAFT/UNSTABLE，远端head仍74b08bd2，base integration/v1.2；Issue #350 OPEN。没有推送、合并、发布授权。
- 历史276资源声明矩阵54条具备定向实际响应证据，222条历史待验证；新增局部回归不直接重算覆盖，不能视为全部资源安全审查通过。
- 医院A1-F4/E5、真实Windows/NTFS ACL、NAS/OSS、Chrome86、HIS消费者、等量PG、容量和RPO/RTO仍须原门禁，禁止将本地实验当现场签字。
- 实际集成/合并SHA完整CI、覆盖率、Release dry_run=true retain_candidate=true、同轮ZIP/JAR/manifest及Windows恢复验收尚未取得。旧候选制品不可复用。
- 原始四份未提交文档仍受保护，不纳入本次冻结提交；无生产操作。基线禁止旧应用直接回滚，优先批准的维护/只读降级及安全前向修复。

详细验收见 [发布准备](../engineering/v1.2-release-preparation.md) 第33节与 [RC交付清单](../engineering/v1-rc-delivery-checklist.md)。
