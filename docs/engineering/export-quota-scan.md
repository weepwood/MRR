# 导出临时目录配额核算

关联 #501，Parent #171；风险 P1。负责人 Codex；分支 `codex/171-export-quota-scan`；worktree `D:/Project/CodexWorkTress/export-quota-scan/MRR`；状态进行中。基线 `origin/main / 2b03f9c8`。

## 调查与计划

`ArchiveExportJobService.runJob -> ArchiveExportTempFileManager.reserve -> calculateUsageBytes` 是既有配额入口。当前扫描先判断链接和普通文件，再读大小；大小读取失败吞掉 IOException 并计为 0，isRegularFile/exists 也不能区分不存在与不可读取。无法确认占用时仍可能预留新任务。sum 还可能溢出。本批不声称现场已发生事故。

1. 在现有管理器测试中先复现读取失败、目录遍历失败、溢出和清理删除交错，保留正常预留/活动文件排除/路径保护。
2. 只改 calculateUsageBytes：目录直接列举；每个未排除子项读取一次 NOFOLLOW_LINKS 属性，普通文件累加并饱和；只忽略消失的子文件，其余扫描失败中止并用稳定错误消息包装。目录不存在仍返回 0，权限错误不能当作不存在。无大小缓存。
3. 定向测试、独立规格/代码审查、当前提交 CI；无 SQL/表/迁移、权限或任务状态变化。真实 Windows/NAS 仍待现场验收。

非目标：编号解析、导出列表准备、旧前端压缩、目录递归扫描、医疗文件变更。保留单任务最大额度预留、输出上限、活动预留文件排除和清理规则。

## 验证记录

- RED：先运行 `mvnw.cmd -o -B -ntp -Dtest=ArchiveExportTempFileManagerTest -Dlocal.config.exclude=true test`，9 项中 5 项失败。读取失败未抛异常、溢出变为 Long.MIN_VALUE、遍历异常未按稳定 IOException 处理以及额外 Files.size 调用均被测试检出。
- GREEN：`mvnw.cmd -o -B -ntp -Dtest=ArchiveExportTempFileManagerTest,ArchiveExportJobFailureTest,ArchiveExportJobServiceValidationTest,ArchiveExportJobPolicyTest -Dlocal.config.exclude=true test`，16 项通过，失败/错误/跳过均 0。真实 Windows 本地临时目录覆盖正常核算与排除规则；权限/遍历故障和超大大小通过 NIO 故障注入模拟，不冒充 NAS/生产场景。
- 架构边界、工程文档、发布基线、git diff --check 通过。未修改 SQL 或迁移。本地未跑全量套件，等待当前 PR CI；独立审查如下。

## 独立审查

`quota_scan_review` 在新上下文直接读取 Issue #501、架构规范、diff、测试和 reserve/Worker 调用链，发现原始异常 cause 仍会被 Worker 日志记录。已改为不附 cause 的稳定 IOException，并在四个故障断言增加 hasNoCause；增量审查确认阻塞消除，未发现其他阻塞。未改变其他模块既有日志语义。

目录遍历故障用例还覆盖已经计入一个文件后失败、目录流关闭及恢复重试，不只检查扫描开始即失败。符号链接/Windows 重解析点及真实 NAS 未现场验收；不把故障注入和本地临时目录等同于这些场景。

## 回滚

暂停新导出，等待活动任务结束，独立 revert 本批并重建包。无需数据库迁移或医疗数据恢复，保留任务记录与成功文件。回滚会恢复原核算失败时低估的风险，应优先前向修复。
