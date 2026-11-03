# 导出启动恢复的有界调度

关联 #505，Related to #171；风险 P1。负责人 Codex；分支 `codex/505-export-recovery-backpressure`；worktree `D:/Project/CodexWorkTress/export-recovery-backpressure/MRR`；状态进行中。基线 main `2b03f9c8`；先用GitHub REST确认，随后以`git -c http.sslBackend=openssl fetch origin main`成功同步，保留TLS验证，未改全局Git配置。

## 调查与计划

现有 Repository.findRecoverable 全量读取未完成作业，Ready事件逐项reset后立即submit；队列拒绝直接markFailed。正常活跃数量受到旧队列间接限制，没有生产积压统计，不能将Job表等同mr_scan大表。

本批复用现有Job表、条件更新、Executor和Scheduler：启动固定(created_at,id)上界，每轮按稳定游标读取至多min(200,queueCapacity)。队列拒绝时保留PENDING，不推进该项游标，使用现有Scheduler每秒重试；普通新请求拒绝语义不变。

当前进程已接收ID在insert前登记，恢复先认领同一ID，防止Ready前请求、并列时间戳或时间回拨导致活跃Worker被重置。入库失败/幂等冲突/拒绝、非提交恢复项及Runnable结束均释放所有权；不保存全部恢复ID或建立第二套Job状态。

取消只有条件状态更新成功后才清理文件；reset与取消交错时重新确认，终态成功文件不可删除。重复Ready不重置恢复边界或游标。

先复现队列满误判失败、重复Ready及当前进程任务被恢复，再补分页/所有权/取消失败路径和PG16集成测试；独立审查与当前提交CI。无外部API、Owner/权限、医疗文件、表或迁移变化；不混入前批PR，浏览器预算、配额和导出准备均非目标。

恢复间隔配置为`archive.export.recovery-interval`，默认PT1S，沿用既有Scheduler。查询/重置期间数据库故障保留游标，记录稳定提示后下轮重试，不让首次Ready查询失败直接中止启动。只有首次查询上界成功才标记初始化完成。LIMIT约束返回行和内存，不证明(status,updated_at)现有索引下扫描成本严格有界。

游标在Executor接受作业后推进，不表示作业已经导出成功。已接收作业仍使用既有Worker的错误与状态语义；本批确保Worker前置findById/markProcessing异常也会释放提交所有权，不新增运行时作业重试策略。活动任务即使原expires_at已过仍按旧规则重建并延长保留期；终态EXPIRED不恢复。

## 本地验证

- RED：`mvnw.cmd -o -B -ntp -Dtest=ArchiveExportRecoveryTest -Dlocal.config.exclude=true test`，3项全部断言失败，证明暂满误判失败、重复Ready和新进程作业被重置。
- GREEN：`mvnw.cmd -o -B -ntp -Dtest=ArchiveExportRecoveryTest,ArchiveExportJobFailureTest,ArchiveExportJobServiceValidationTest,ArchiveExportJobPolicyTest -Dlocal.config.exclude=true test`，补审查交错用例后23项通过，失败/错误/跳过均0。
- 新增PG16类ArchiveExportRecoveryPostgresqlIT共11项：空边界、同时间戳UUID分页、四类终态排除、固定上界、行消失不漏页、200硬上限、容量1真实三作业完成、PROCESSING部分文件重建、原活动过期时间重设、取消与终态保护。SQL实际运行等待当前PR CI，不以本地编译或跳过当作通过。
- 架构边界、工程文档、发布基线、测试资产和git diff --check通过。真实Windows/NAS、生产积压和恢复吞吐未验收。

## 独立审查

recovery_review先核对#505、架构/治理/ADR和方案，再直接读取diff、全部测试和日志。发现普通请求拒绝后先释放ID、后markFailed的窗口，可能被Ready恢复抢先接收。已改为普通submit在失败状态更新结束的finally释放，恢复未接受项仍由自身finally释放。补交错用例在审查前旧释放顺序下实际失败（recovery-review-red.log：1项失败），恢复修复后全体定向测试通过。最终增量复核确认阻塞解除，无新增代码/规格阻塞；当前提交PG16 CI结果记录在PR。

## 回滚

暂停新导出并等待活动任务结束，独立revert并重建包；保留Job表、任务输出和医疗原文件，无数据库回滚或数据恢复操作。回滚恢复旧全量/即时提交行为，应优先前向修复。
