# V1.0 发布准备与剩余验收矩阵（2026-11-25）

关联 #350、#230、#99、#112、#715、#729。只做候选准备，不批准生产操作或正式发布。正式版本仍为 0.9.0；建议待阻塞解除后单独审批 1.0.0-rc.1 的版本/说明变更，再固定最终 SHA。当前验证基线 6ac73ed4bf142206904ab6e72004177083b61020（#795 合并），不是 V1.0 候选制品。任何后续代码或版本变更须重新验证最终 SHA，不能继承旧制品身份。

## 当前证据和强制门禁

| 项目 | 当前证据 | 对最终候选的状态 |
| --- | --- | --- |
| 主线集成 | #795 head 01f1d635；治理45450605471、质量45450605334、演示45450605080通过；1184单测/150 PG16/933前端单测 | 待最终SHA |
| 发布基线及工具 | VERSION 0.9.0；release_baseline.py validate、17项release工具单测通过 | 待最终SHA |
| 完整候选预演 | [45451701999](https://github.com/weepwood/MRR/actions/runs/45451701999)，dry_run=true，SHA 6ac73ed4；identity 已通过，完整候选/恢复截至初始记录未完成 | PENDING |
| 覆盖率 | 保留 quality 下原门禁；用本次完整工作流实际单元/combined报告 | PENDING |
| 真实浏览器与Chrome86 | #795 Chromium运维联合8项含预热；全站、Chrome86现场及减少动效不能以Mock代替 | PENDING |
| 三千万精确检索 | 已有[实测报告](synthetic-scan-benchmark-2026-11-24.md)，旧包含COUNT前后5秒超时；不重复造数 | 合成证据存在；现场BLOCKED |
| 模糊GIN | [三百万选择性及ABBA提交写入报告](synthetic-trgm-benchmark-2026-11-25.md)，默认精确游标、不部署GIN | 现场评估待审批 |
| 索引容量和回滚 | #790 实库万行预检/回滚，#794 目录快照 | 服务器实际卷/WAL/复制、批准阈值BLOCKED |
| 访问权限、撤销、历史协议 | 既有授权/审计与PG16回归；#712/#722尚未合并 | 现场调用方清单及安全迁移BLOCKED |
| 存储网关及导出 | #782/#789/#793/#795 隔离、熔断、Range/许可恢复本地/CI证据；真实医院Nginx/NAS/OSS、慢客户端及长期压力未验收 | BLOCKED |
| 日志/数据质量/分区 | 保留既有检查及迁移；采样/COUNT已设10秒超时，access_log分区现场切换及回退不自动执行 | 现场BLOCKED |
| Windows实际恢复 | 既有工作流必须运行 windows-database-recovery；Mac无pwsh或现场 Windows | PENDING（CI）/BLOCKED（医院） |
| 候选制品身份 | ZIP/JAR/checksum/manifest 对应最终版本、SHA、run、attempt；dry-run制品不授权发布 | PENDING |
| 正式发布与医院启用 | 维护者审批、医院负责人批准适用范围及回滚；未收到 | BLOCKED |

完整医院验收以[医院副本记录](hospital-replica-acceptance-record.md) A1–F4、E5 与 #350 为准，不用此摘要替代、不得把合成结果填为现场PASS。所有N/A需说明理由并审批。优先核对 #350 当前清单与实际现场证据，仅在院内留原始数据、配置、日志和备份。

## 历史工程欠账：2026-11-25 实际 open PR

| PR | 头部SHA | 核查结论 | 下一步 |
| --- | --- | --- | --- |
| #712 草稿 | db3678e9 | 可合并但涉及旧Search GET返回410，外部协议兼容风险；未核实现场消费者 | 保留；先盘点消费者、POST/HMAC兼容与真实浏览器证据，再单独P1验收 |
| #722 非草稿 | de997f4d | GitHub显示CLEAN；主线 PatientController 与前端仍保留GET身份证路径，不能将旧实现视为已删除 | 保留；优先新增兼容POST迁移、核查旧消费者，退役需明确审批；不以CLEAN代替现场验收 |
| #752 草稿 | 5da2f425 | DIRTY，依赖#751已合并e196d80f；仅演示OCR审核 | 暂缓；不作为首轮V1.0范围，不启用自动归类；后续独立Mock同步 |
| #795 | 01f1d635 | 已合并6ac73ed4 | 不重复实施 |

保留旧协议不是声称隐私风险已经解决；发布范围必须明确该风险、迁移责任和批准，不能为减少PR数量直接强制退役。维护者未授权批量关闭、删除远端分支或回退安全修复。复用 SourceNodeCircuit、SourcePermitGuard、现有网关、运维总览、发布脚本和医院验收模板；不另建工作流、指标库、恢复工具。已解决的数据质量 sample timeout、编号游标、隔离造数不重复。历史快照不反向改写，现行事实由本页及 Git/Issue 核查。

## Windows 部署与恢复顺序（只读准备）

1. 选择最终候选 SHA，按[发布流程](../../vitepress-doc/internal/release.md)校验 VERSION/Maven revision、版本说明、release-baseline.json（当前迁移上限 20261123170000）；不改已有 Flyway，不降低覆盖率或审批要求。
2. 使用现有 Release workflow dry_run=true 固定 SHA 完成完整候选、覆盖率、真实候选浏览器和 Windows PS5.1 恢复演练；核对实际 job 与跳过原因。下载工作流候选证据，逐一核对版本/commit/run/attempt/哈希；不能复用不同 SHA 的结果。dry-run 不是发布。
3. 按[Windows 原生部署手册](../../deploy/windows/README.md)，仅在授权的测试服务器核对 JDK21/PG16/WinSW/Nginx、外置配置、NTFS ACL、各实际卷余量、服务身份与可用的回滚/前向修复路径。正式环境不运行 Docker、Vite 或 git pull。
4. 用既有 database-recovery.ps1 的 Backup、Verify、RestoreToEmptyReplica 流程完成批准的隔离空库演练，记录归档哈希、数据库服务、恢复耗时、真实 RPO/RTO、迁移与授权/影像读取；不得将生产库作为恢复目标。实际操作命令与安全参数以手册为准，不自动执行。
5. 使用现有 mrrctl/Manager 受控入口安装/部署，核对 manifest、actuator/info、维护模式、同源网关、健康状态、日志、权限及业务冒烟。应用回滚当前 baseline 明确不允许直接回到旧撤销机制；采用安全前向修复，分区切换后按已批准的独立流程回退，保护审计、撤销事实和旧表。
6. 现场执行医院验收全矩阵并签字确认启用范围。只有最终 SHA 的所有必需门禁与适用现场项目通过、维护者单独批准正式发布后，才能运行 dry_run=false。禁止手工创建Tag/Release绕过候选身份。

## 交接与停止条件

任何候选失败、未结束、制品身份不符、现场授权/容量不足、隐私协议迁移未知均不标记为已发布；记录实际阻塞并修复后重新绑定最终SHA。新监控PR可能改变主线，当前dry-run只代表6ac73ed4，不作为后续SHA的正式发布通过证据。
