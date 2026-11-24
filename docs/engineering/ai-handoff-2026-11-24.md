# MRR 跨 AI 工具开发交接 · 2026-11-24

本文件为交接快照，不是实时状态。接手先读根目录 AGENTS.md、ARCHITECTURE.md、治理规范及相关 Issue；以 GitHub 最新分支、PR 和实际 CI 为准。仓库 https://github.com/weepwood/MRR 。交接前 main SHA：71d36fd036f49909deeb431dd18f9d17258285fc；VERSION 文件为 0.9.0，不等于 V1.0 已验收。此次仅文档交接，不改业务代码、部署、权限、生产数据库或分支保护。

## 1. 维护者明确的方向和规则

- 选定 01 历史 PR/工程欠账、03 三千万级数据库性能、04 运维存储可靠性、09 影像档案袋与导出、07 V1.0 收口，按此顺序。当前重点 04，同时继续收口既有 PR；05 演示和06 UI 不再新增专项；不主动扩展新的隐私专项，已存在的兼容改动仍须证据化收口。
- 日常迭代快速检查，高风险改动专项验证，正式发布完整验收。独立任务独立分支/工作区、中文 commit、不得直接推 main。AI 可自主审查并合并通过必要门禁且无阻塞的 PR；如无独立审查标记 AI 自审，不伪造审批。P1/P0 同步最新 main 后核验实际合并树及最新 SHA；P2/P3 无关变更不强制同步。不得降低 governance/pr-gate，不使用 force 或绕过失败门禁。
- 不设置周期性自动复审；接续对话或新工具启动时先核对 CI。集中完成有证据的任务，不反复建设同类工具。遵循测试先行 RED→GREEN；先提交测试不等于实际运行 RED，如实记录。
- 正式架构 Windows Server、Nginx、Spring Boot 4 / Java 21、PostgreSQL 16、Vue 3；Docker 仅开发/测试。仓库当前兼容目标 Chrome 86 / Windows PowerShell 5.1，见 AGENTS.md（以仓库最新规范为准）。不擅改已发布 Flyway，不访问真实病案、不提交真实编号或配置凭据，不自动执行生产 DDL、迁移、切换或正式发布。

## 2. 已合并的生产稳定性改动

以下各项实际必要门禁成功，非医院现场验收。旧 PR 的实际 head 与 CI 以链接为准。

| PR | main 合并提交 | 主要交付 | 实际 CI 证据 |
| --- | --- | --- | --- |
| [#755](https://github.com/weepwood/MRR/pull/755) | a990203 | Pages 三场景、确认重载、场景枚举持久化，恢复默认；业务状态仍内存 | governance 45407306971 / quality 45407306989 / Pages 45407306948 |
| [#756](https://github.com/weepwood/MRR/pull/756) | f836ff3 | 无条件 ID 游标补齐3秒语句预算，真实锁取消和连接恢复 | [quality](https://github.com/weepwood/MRR/actions/runs/45407989745)；PG16 136项，锁等待7项 |
| [#757](https://github.com/weepwood/MRR/pull/757) | 3fe158b | 短非空末页免额外COUNT；满页/空后续页保留COUNT，偏移溢出拒绝 | [quality](https://github.com/weepwood/MRR/actions/runs/45408967772)；1118单测、137 PG16 |
| [#758](https://github.com/weepwood/MRR/pull/758) | 3b0a05b | 编号索引检查真实表、类型、状态、键序、函数依赖等目录定义 | [quality](https://github.com/weepwood/MRR/actions/runs/45409847512)；1118单测、140 PG16 |
| [#759](https://github.com/weepwood/MRR/pull/759) | 676b6dd | 编号索引构建前置检查，阻止错误同名对象；不自动删除 | [quality](https://github.com/weepwood/MRR/actions/runs/45410673024)；1118单测、143 PG16 |
| [#760](https://github.com/weepwood/MRR/pull/760) | 114b448 | 只读同快照 BAH/SJH 同页计划比较，archive_id 完整投影，完整无界查询只 EXPLAIN | [quality](https://github.com/weepwood/MRR/actions/runs/45411756050)；1118单测、144 PG16 |
| [#761](https://github.com/weepwood/MRR/pull/761) | 4ffab09 | HTTP延迟报告v2，校验预期条数/预热/案例，吞吐与失败率，比较不达标返回非零 | [quality](https://github.com/weepwood/MRR/actions/runs/45414401584)；Python256项，新增3项 |
| [#762](https://github.com/weepwood/MRR/pull/762) | 71d36fd | 迁移统计/目录共享代际，防失效后旧查询回填，重置失败记录提交后失效 | [quality](https://github.com/weepwood/MRR/actions/runs/45415453095)；1121单测、144 PG16、迁移专项19项，零失败/错误/跳过 |

#723 与 #727 已按 #724、#728 的实现/CI 证据关闭；真实环境父任务仍开放。此前完成的演示数据共享、合成图片、OSS、档案盒、单病案 OCR、历史 OCR 批次及六模块严格 Mock 契约门禁见 [交接索引](ai-handoff.md) 与 [演示引擎](mrr-demo-engine.md)。#754 时静态接口匹配136/198（68.7%），62 未匹配，0 重复；不是当前实时覆盖率或业务完成率。#755 不保存业务记录或凭据。

## 3. 第一优先级：接手 #763（未合并，真实 CI 已失败）

- [PR #763](https://github.com/weepwood/MRR/pull/763)，P1，分支 fix/230-migration-source-cache-eviction，head 78290f25c0d73988d3e65ff9bed6985e99a9e4a5；base 71d36fd036f49909deeb431dd18f9d17258285fc。测试先提交 321c47db3670ed8e8ac78c0c31a0a5a1cc6d81cc，RED 未实际执行。交接时可合并树无冲突，但绝不能因 mergeable 就合并。
- governance 45416755143 成功；[quality 45416755045](https://github.com/weepwood/MRR/actions/runs/45416755045) 失败。backend job 138525331363，单测1123项、1失败、0错误/跳过，迁移专项21项、1失败；本次没有完成 PG16 集成。
- 失败用例 MigrationServiceImplTest.successfulMigrationWithoutTransactionInvalidatesSingleRecordImmediately：期待 success，实际 retry_wait。日志明确为 Mockito PotentialStubbingProblem：测试仅 stub getCache("scanById")，生产实现先调用 getCache("scanByBah")，严格 stub 参数不匹配。先用真实 SimpleCacheManager 或明确映射五类缓存的统一 stub 修复夹具，不削弱业务断言，也不盲目全局 lenient。运行对应单测，再跑真实 PG16/必要门禁。
- 关联 #230/#99/#350；实现位置 backend-repo/src/main/java/com/zjcxph/imgapi/service/impl/MigrationServiceImpl.java，测试 backend-repo/src/test/java/com/zjcxph/imgapi/unit/service/MigrationServiceImplTest.java。updateOssInfo 更新 OSS Key/大小/校验值后，原路径绕过 ScanServiceImpl 缓存失效。现有 PR 仅更新行数>0时使 scanById 精确失效，遍历其他四个有界 Caffeine 缓存的结果，按 scanId/非空 archiveId 选择性删除；事务同步时 afterCommit；无关缓存及 ossSignedUrl 不变。
- **代码审查风险，不止修测试：** 失效处于上传成功后的 try/catch 内，若缓存清理异常可能将已写库/已上传对象误标 retry_wait/failed；实际测试已展示异常进入该路径，不能仅修 stub 就认定无风险。检查提交后回调异常、更新影响0行的既有成功语义、事务回滚、批次/并发及旧回填竞态；按风险补测试和最小修复。已缓存项选择性失效不解决在途旧查询重填，且每次最多遍历四个各1000项的缓存，缓存值内列表未新设上限；需评价实际迁移吞吐/命中。不要新建第二套来源规则或降低权限、审计。
- 接手从 PR 当前最新 head 开始，不重做同一 PR；保留历史提交，不 force。P1 主线同步后复验完整合并树，检查所有必要 CI 及 review threads，记录 AI 自审，确认无阻塞后 squash。回滚为独立 revert，不删除已迁移 OSS 对象。

## 4. 其他开放 PR 与保留分支

| 事项 | 分支与 head | 处理边界 |
| --- | --- | --- |
| [#752](https://github.com/weepwood/MRR/pull/752) | feat/729-demo-classification-review / 5da2f4256df7ab1e62980feffae0b9bf8d9d0444 | draft，有冲突；旧检查非最新合并树。之前同步操作被工具安全检查阻断，保留分支，不绕过。演示非当前专项。 |
| [#722](https://github.com/weepwood/MRR/pull/722) | fix/715-patient-idcard-post / de997f4d9b4d5910f3a4b66f7c9e43279de8b291 | 有冲突，旧 CI 通过，外部调用方 GET 退役兼容证据不足；勿强行合并。 |
| [#712](https://github.com/weepwood/MRR/pull/712) | fix/112-retire-idcard-query / db3678e930cd7e62d07f427a22fcfc75d8d4df72 | draft，有冲突，旧 CI 通过，兼容验收阻塞。 |
| 演示导出规划（无 PR） | feat/729-archive-export-plan / dfe502225a719e84fbaa8de8d7e4e83ec4f7ca1f | 仅测试先行；实现操作此前被工具安全检查阻断，保留，不绕过。 |

本次用 GitHub API 操作独立远程分支，无本地 worktree；新工具如果拥有本地工作区，先核实 git status、worktree、独有提交，勿覆盖或删除用户改动。历史 #736/#737/#717/#719 待逐项核对，不能按 merged 自动关闭整个 Issue。

## 5. 下一阶段具体顺序（代码层面与现场验收分离）

| 顺序 | 专项 | 入口与验收 |
| --- | --- | --- |
| 1 | 04.1 #763 修复/复审 | 见第3节；必须成功运行最新 P1 专项，解决实际缓存异常及相关失败路径 |
| 2 | 04.2 统一影像网关 | #230、ADR-0009；核对 ImageController、ImageContentServiceImpl、ImageUrlService、ImageStorage/ResolvedImageStorage、外部票据、导出和迁移来源。盘点 direct/gateway 残留路径，验证有效影像、权限、审计、流关闭、Range及失败映射；不自动切换默认模式 |
| 3 | 04.3 来源诊断 | #230/#636；复用 ImageSourceDiagnosticsService 与现有来源策略，受控 Nginx/NAS/OSS 故障和超时诊断，脱敏，不泄露底层地址/凭据，不开放任意 URL |
| 4 | 04.4 日志分区 | #694、ADR-0010、runtime-log-function-review.md；状态/容量/积压/断点/缺口、重复投影、失败恢复；实际 copy/cutover/rollback/abort/删除仅授权副本 |
| 5 | 04.5–04.7 容量与恢复 | #636/#350；复用运维中心、现有只读取证及 Windows database-recovery.ps1。区分未知和0、数据库/缓冲/源存储卷、备份验证与真实恢复；不直接写生产 |
| 6 | 09 档案袋与导出 | #171/#519/#523/#524；复核现有 ZIP/PDF 任务状态、选页、来源回退、取消、过期、下载预算、Range、临时文件清理、并发状态；不要重建已有任务机制 |
| 7 | 07 V1.0 收口 | #350/#619/#381；固定 SHA、完整候选测试、真实 PG16、Chrome86、Windows升级/恢复、产物身份和现场证据，单独批准正式发布 |

03 剩余工作并行列为独立事项：#99/#350 千万级/三千万级合成基准、实际 archive_id/编号执行计划、冷/热查询及端到端负载、病案缓存统一失效与并发回源、命中率。保留现有 seed_dev_data.py 小规模保护，不为了造数规模放宽安全上限；如需大规模基准另设计可销毁隔离环境、有界批次、容量及停止条件。#760 只验证脚本核心SQL，不是完整 psql 流程通过；#761 v2 需新必填参数、旧报告须重测。不能将超时保护、目录检查或合成 CI 当作性能提速证据。

## 6. 关键文档、实际执行与禁止事项

- [架构](../../ARCHITECTURE.md)、[治理](ai-assisted-development-governance.md)、[后端工程](../../backend-repo/ENGINEERING_GUIDE.md)、[前端工程](../../frontend-fantastic-admin/ENGINEERING.md)。
- [数据运维手册](mrr-data-operations-runbook.md)、[扫描查询预算](scan-legacy-query-timeout.md)、[医院副本验收](hospital-replica-acceptance.md)、[医院上线事实台账](hospital-readiness-roadmap.md)、[日志功能](runtime-log-function-review.md)、[分支保护](main-branch-protection.md)。
- 根目录 Python 快速检查：python -m unittest discover -s scripts/tests -p 'test_*.py' -v；python scripts/test_inventory.py --fail-on-focused；python scripts/release_baseline.py validate；python scripts/check_engineering_docs.py。
- 后端按需使用 Maven Wrapper（Windows .\\mvnw.cmd，Linux ./mvnw），在 backend-repo 运行 -B -ntp -Dtest=MigrationServiceImplTest test；P1 真实 PG16 verify 按现有 CI/工程指南执行，外部测试库必须可销毁（测试会清表）。前端按现有 pnpm 命令、实际定向 E2E 和发布验收规则。
- 不执行医院真实连接、生产索引/配置/DDL、源站隔离、日志切换、备份恢复或正式发布；现场由授权负责人验收。真实病案/地址/Token/凭据不得写入交接、测试、日志、Artifact。
- 每个 PR 更新交接索引与对应专题，保存实际 SHA、CI run、测试数/失败/跳过、未执行项、风险、回滚。此文件为 2026-11-24 历史快照；更新状态写入索引和新记录，不依赖聊天上下文。
