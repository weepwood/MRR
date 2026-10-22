# 医院副本验收包

关联 #350，配套 #171/#349。文档交付风险 P3；现场导出、恢复、故障演练及配置启用为 P1。
本包复用现有取证、查询计划、延迟测量、质量检查和备份恢复工具，不增加运行组件或连接配置。
仓库内测试不证明医院验收完成。先复制 [验收记录模板](hospital-replica-acceptance-record.md) 到院内受控目录填写。

## 准备和边界

- 由现场负责人确认已授权的隔离 PostgreSQL16 副本、Windows Server/Nginx、合成病案和图片源；故障演练不能使用生产实例或共享卷。
- 记录 VERSION、Git SHA、发布包 SHA256、数据库迁移上限、主机配置、数据量、浏览器版本及操作者。执行前核对实际部署 SHA 包含待验收修复，不能只看相同产品版本号。
- 数据库使用已有 pg_service.conf / pgpass；不在命令参数、脚本或记录中填写密码。证据目录与备份目录分别预建私有 ACL，不提交仓库、不上传 CI Artifact。
- 新日志采集在日常生产保持关闭；只在验收副本按下文比较开关。OCR及自动应用保持现场原状态，本包不启用。
- 取证失败、权限不足、数据范围不明时记录 BLOCKED；不能记录 PASS 或补零/合并数据来绕过。

以下命令在仓库根目录运行，使用院内自行配置的 `replica`、`empty-replica` service；路径为合成占位示例。
每一步分别检查退出码，失败即停止该步骤，不连续运行后续命令。

## A：只读基线与查询

```powershell
powershell.exe -NoProfile -File deploy/windows/collect-governance-evidence.ps1 -Service replica -OutputDirectory D:\MRR-Acceptance\baseline -ApplicationVersion 0.8.0
```

核对 PostgreSQL16、app schema、Flyway成功状态、估算行数、未验证外键、触发器、无效索引和等待摘要。
估算行数不能替代精确全库计数。发现结构冲突先记录 #350，不运行 Flyway repair 或历史回填。

用已授权的合成夹具从浏览器查询，覆盖低位病案号、前导零、高位缺少上架号、编号冲突、无有效影像和文件缺失。
普通用户只保留安全原因码、Request ID；管理员复用现有深入诊断。无Token应401、权限不足应403且不透露病案存在性。
另用合成账号分别验证过期Token、已登出Token和服务重启后的撤销Token仍被拒绝；不记录Token正文。
使用两个独立浏览器会话并发查询不同合成病案，确认响应、分类筛选和显示病案不会互相覆盖。
在隔离副本分别进入维护与只读降级模式，按既有接口策略核对允许的只读诊断和被拒绝的写入/导出；退出模式后复核恢复，不扩大放行列表。
真实空结果按 [既有排障手册](mrr-data-operations-runbook.md) 逐阶段定位，不能以合成结果推断根因。

```powershell
psql -X --set ON_ERROR_STOP=1 --dbname 'service=replica' --set archive_id=1 --set code=123 --file backend-repo/scripts/inspect-query-plans.sql --output D:\MRR-Acceptance\baseline\query-plans.txt
```

`1` 和 `123` 仅为合成示例，执行前在院内选择有效且获授权的夹具。该SQL实际执行SELECT，已有5秒语句/1秒锁等待限制。
保存扫描行数、Buffers、索引、耗时及数据范围；执行计划可能含敏感值，只留院内。
索引构建是单独维护变更，不纳入本包自动执行；已有 build-code-key-indexes.sql 仅在批准的副本窗口使用。
按#350单独安排两个并发索引的构建前/后对比：相同数据、相同SQL与负载，分别记录计划、扫描行数、Buffers、连接池/锁等待、P50/P95/P99和索引有效状态。
记录构建耗时、WAL及空间预算；失败或无效同名索引按既有手册处理。未完成前/后对比不能用“已有索引”替代验收。

## B：日志开关对比与容量

在相同副本、相同合成病案、相同负载和相同缓存条件下，先关闭日志采集测量，再仅在副本开启并重启后复测。
按现有安全方式在当前进程设置 `MRR_BENCH_TOKEN`、`MRR_BENCH_BAH` / `MRR_BENCH_SJH`；不输出环境变量或开启转录。

```powershell
python scripts/measure_query_latency.py --base-url http://127.0.0.1:18045 --replica --label logs-off --samples 100 --concurrency 1 --output D:\MRR-Acceptance\logs-off.json
python scripts/measure_query_latency.py --base-url http://127.0.0.1:18045 --replica --label logs-on --samples 100 --concurrency 1 --output D:\MRR-Acceptance\logs-on.json --baseline D:\MRR-Acceptance\logs-off.json
```

两条命令之间必须实际改变副本开关并核对生效；不能只改label。工具测量经过5次预热的HTTP元数据查询，不能证明冷查询、图片读取或全部病案性能。
两份报告均须 valid=true，第二份 comparable=true、withinTenPercentTarget=true；退出码0本身不证明额外P95≤10%。
另记录冷查询/大病案P50/P95/P99、连接池/锁等待、业务吞吐及采集前后磁盘和数据库字节差，使用真实现场预算判定。
观察窗口须覆盖日常高峰和轮转，按每日增长估算保留天数、备份空间与扩容责任人；不得以短时测量承诺无限保留。
清除当前进程的基准Token与病案环境变量，按原配置关闭副本采集。

## C：导出与质量检查失败路径

在Chrome86与真实Nginx入口，使用合成病案分别覆盖本地、NAS、Nginx、OSS及混合来源；记录ZIP/PDF完整性、Range、到期拒绝和非所有者拒绝。
取消等待中和执行中的任务，观察至终态及文件清理完成；取消已经提交时不得变回SUCCESS。完成先提交的任务允许保持SUCCESS，取消接口不应破坏其文件。
只在隔离临时卷模拟文件占用：到期清理失败时路径和可重试任务仍保留；解除占用后下一轮成功清理并进入EXPIRED。不得锁定或删除生产影像。

质量检查超时必须显示FAILED并释放运行标志，后续可重跑；结果不允许把失败显示为0异常。
普通API不接受任意SQL，不通过生产接口注入pg_sleep；10秒样本取消的确定性证明使用该修复PR的 DataQualityQueryTimeoutPostgresqlIT。
现场只对已有规则核对执行计划、扫描范围、锁等待和结果口径，区分窗口检查与全历史检查。

## D：中断和恢复

仅在隔离实例依次演练数据库中断、日志轮转、应用重启、重复补录、日志分区失败和缓冲卷满。每种故障独立执行和恢复。
核对未确认断点不推进、可恢复事件只入库一次、错误计数不重复、不可恢复缺口明确；保存持续时间、计数和安全错误码。
卷满使用独立测试卷及预先批准的限额，不填满系统盘或数据库共享卷。停止演练后恢复原配置并核对普通病案读取。

备份与恢复使用预建私有ACL目录以及全新空副本，以下步骤分别执行并核对退出码：

```powershell
powershell.exe -NoProfile -File deploy/windows/database-recovery.ps1 -Action Backup -Service replica -Archive D:\MRR-PrivateBackup\acceptance.dump
powershell.exe -NoProfile -File deploy/windows/database-recovery.ps1 -Action Verify -Service replica -Archive D:\MRR-PrivateBackup\acceptance.dump
powershell.exe -NoProfile -File deploy/windows/database-recovery.ps1 -Action RestoreToEmptyReplica -IsolatedReplica -Service empty-replica -Archive D:\MRR-PrivateBackup\acceptance.dump
```

Verify仅证明归档可读。实际恢复后核对Flyway、表行数、关联、应用账号权限和受控图片读取；脚本使用no-owner/no-privileges，部署角色及ACL需按现场既有流程重新配置并复核。
数据库备份不含NAS/OSS原文件，图片与配置/密钥需要独立恢复演练。记录实测RPO/RTO并由院方确认目标，不能仅因应用启动成功判PASS。

## E：全历史只读质量与验收结论

```powershell
powershell.exe -NoProfile -File backend-repo/scripts/check-scan-quality.ps1 -Service replica -Checkpoint D:\MRR-Acceptance\scan-quality.json -BatchSize 10000 -MaxBatches 10
```

该命令只处理有限批次。使用同一副本、service、规则及检查点继续执行，直至检查点达到固定高水位并完成；一次成功不代表全历史完成。
检查点绑定实例，需要pg_monitor或相应授权；失败不得手工推进。核对覆盖量、缺失、孤立、冲突及并发期间变化，异常由后续修复工单处理。
另核查遗留OSS浏览签名链接有效期、权限与内部地址边界，及Chrome86关键页面视觉与键盘流程。
在目标浏览器/系统支持时开启减少动态效果偏好，核对动画降级、焦点和主要操作；不支持时记录原因、替代验证与负责人，不直接标PASS。

签字前同时逐项核对#350最新验收清单及本记录，任一缺项继续保持待验收。所有适用项有院内证据和负责人签字后才可关闭#350；不适用项必须说明理由和批准人。对外仅提供脱敏结论、SHA、范围、耗时与错误码。
失败时关闭副本新采集或深入诊断，保留任务、审计和证据，采用当前基线上的前向修复；不回退到旧Token撤销或泄露日志的版本。

本包可独立撤销文档提交，不改变应用、数据库或部署；现场配置恢复需依上述各步骤核验。
