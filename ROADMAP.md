# MRR 唯一活跃任务池

定位：Windows 单机、医院内网、模块化单体。当前处于本地架构简化与产品收口，非正式发布。以实际 Git 工作树为准；不擅自推送、合并、发布。历史文档和证据不再作为重复任务队列。

| 优先级 | 当前任务 | 状态 / 停止条件 |
|---|---|---|
| P1 | 医疗权限及字段 | 优先患者/病案、影像、导出、OCR、外部调阅和存储写入。复用[276条矩阵](docs/backend/api-resource-review-batch13.tsv)，54条已有定向真实响应，222条未有；同类风险参数化验收，不追求重复覆盖数量。病案主档有效关联及字段投影PG已验，实际HTTP和旧调用方待验；OCR工作者历史事实错配已修复，OCR与分类25项PG定向通过，未穷尽并发源变化；导出成功文件长度错配拒绝已修复，34项PG及45项Java定向通过，同长度损坏及现场存储仍待验；凭据改密/重置条件更新及冲突处理已修复，64Java/2PG通过，管理员在途授权和停用再启用会话语义待核对。 |
| P1 | 默认审计可靠性 | 明确医院审计保留、故障与容量要求；内存模式崩溃/满容量丢失风险不得隐瞒。关键审计缺陷按真实要求最小修复，禁止扩张冻结引擎。 |
| P1 | 迁移及外部调阅 | 保留UUID/来源/条件更新和[独占维护](docs/backend/migration-maintenance-batch13.md)，实际OSS/NAS、外部流终态和断流待验；无多实例生产需求不做自动跨主机接管。 |
| P2 | 核心业务交付 | 病案查询/影像阅读/患者/装箱/OCR/导出/系统管理，优先已证实的操作缺陷；不新增业务平台。 |
| P2 | 代码与UI维护 | 仅有证据的重复、失效机制及过度封装；遵循设计规范和Chrome86，不因行数拆类、不改变视觉契约。 |
| P1 | 版本收口 | 固定候选SHA，按依赖顺序分段审查累计diff；发布迁移上限已同步；旧正式基线合成数据直接/分段升级及空库安装3/3通过。合成旧库原生备份/独立恢复/升级及损坏归档拒绝已验；实际医院副本、Windows恢复脚本与ACL、完整CI、真实Chrome86/存储及独立安全复核未完成。授权后执行，现不发布。 |

## 冻结及非目标

[实验候选及发布阻塞](docs/backend/governance-stage-freeze-batch13.md) 是既有边界。自定义审计存储、双阶段分组、快照优化、条件OSS、外部严格候选保持默认关闭；不删除密文/账本/钥匙，不自动删除孤儿对象。无新生产证据不重复性能基准，不增加新框架、消息中间件或部署组件。

## 证据和历史

[交接索引](docs/engineering/ai-handoff.md) · [后端治理历史](docs/backend/ROADMAP-history-20261126.md) · [产品历史](docs/product/ROADMAP-history-20261126.md) · [正式发布门禁](docs/engineering/v1.2-release-preparation.md)。

每次只更新本任务池相关状态，必要时附一份实施/验证记录；不重复写入多个历史文件。

本轮完成记录：[架构简化与产品收口](docs/engineering/v1.2-simplification-closure.md)。

稳定期第一轮：[发布元数据及病案主档查询收口](docs/engineering/v1.2-stability-release-baseline.md)。

稳定期第二轮：[旧数据库前向升级验收](docs/engineering/v1.2-stability-upgrade.md)。

稳定期第三轮：[备份恢复及候选检查](docs/engineering/v1.2-stability-backup.md)。

稳定期第四轮：[OCR工作者事实复用边界](docs/engineering/v1.2-stability-ocr-review.md)。

稳定期第五轮：[导出文件交付边界](docs/engineering/v1.2-stability-export-review.md)。

稳定期第六轮：[凭据更新一致性](docs/engineering/v1.2-stability-credential-review.md)。
