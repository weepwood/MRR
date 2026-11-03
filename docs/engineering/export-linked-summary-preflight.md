# 关联病案导出创建阶段摘要

关联#485，Parent #171；风险P1。负责人Codex；分支`codex/export-preflight-summary`；worktree `D:/Project/CodexWorkTress/export-preflight-summary/MRR`；状态进行中，等待真实PG16 CI。基线main `2b03f9c8`。

## 项目现状与本批目标

当前正式架构仍是Windows/Nginx/Spring Boot/PostgreSQL模块化单体。历史隐私、签名缓存、旧查询时限和ZIP冗余规划分别由#481–#484提交，尚未合并，不能当成main已具备的修复。本批继续收敛已有导出机制，不增加部署组件或第二套任务系统。

已有ArchiveExportJobService.create与Worker都会调用prepareArchive，构造完整Scan/PathDO列表；现有编号解析、歧义拒绝、权限、所有者、幂等、配额、取消和恢复必须保留。只优化成功关联archive_id且存在有效影像的整案创建路径，遗留编号与选中列表仍执行原完整校验。

## 调查、实现与兼容

- Mapper新增单行数量/大小摘要，按archive_id及`uploadflag != 0`过滤，沿用现有索引边界，显式3秒JDBC时限，不返回路径或图片行。SUM使用PG numeric，超过Long上限时饱和；任一大小缺失或非正时返回0，仍使用现有每图保守估算。
- ScanService摘要入口复用同一resolveArchiveId，含exact/compatible及原候选歧义检查。没有关联或无有效影像时返回“需完整校验”，ArchiveExportService再调用原prepareArchive；没有建立新的编号解析规则、缓存或状态机。
- 整案create只消费摘要；选中create仍prepareSelectedArchive。队列只保留任务ID，不保留完整计划。Worker执行时才准备关联病案完整明细，并以原取消/配额/文件机制生成结果。
- Worker在任何文件操作前，通过`PROCESSING AND NOT cancel_requested`条件更新实际数量、大小和来源。更新失败不打开输出；终态和取消不接收晚到计划。完成计数使用实际planned_count，不把创建阶段摘要误当执行快照。
- 路径与响应字段不变。整案PENDING的sourceSummary为空字符串表示尚未解析，Worker准备后填充；当前前端仅声明该字段，未消费。没有SQL表、迁移、权限或医疗原文件修改。

不处理未关联历史数据和选中列表的双重准备，不修改编号或图片来源规则，不删除兼容API/依赖，不将此摘要声明为三千万行性能已验收。现场archive_id覆盖率和查询计划仍未知。

## 测试先行与本地证据

- RED：先引入摘要API的兼容默认实现及创建行为测试，4项中2项失败，证明create仍调用完整准备而非摘要；选中校验与幂等复用保持通过。
- 初次扩充测试出现1项失败，原因是Mockito对未stub的Long返回0，与数据库未找到返回null不符。已显式模拟数据库空结果，保留“歧义不得调用摘要/明细”断言，没有放宽生产逻辑。
- 定向命令：`mvnw.cmd -o -B -ntp -Dtest=ArchiveExportJobPreflightTest,ArchiveExportSummaryServiceTest,ArchiveExportJobServiceValidationTest,ArchiveExportJobFailureTest,ArchiveExportSelectionValidationTest,ScanServiceArchiveLookupTest,ArchiveExportJobPolicyTest -Dlocal.config.exclude=true test`。补兼容解析用例后32项通过，失败/错误/跳过均0。
- 新PG16类ArchiveExportSummaryPostgresqlIT共11项，覆盖空/有效/软删除/跨案隔离、未知或非正大小、大小溢出、仅执行中更新、终态拒绝、完成实际计数及取消持锁后计划更新等待的交错。既有状态PG测试继续验证文件、取消与恢复。
- 工程文档、架构边界、发布基线、测试资产及git diff --check通过。本地不运行全量套件，SQL和行锁等待当前PR真实PG16 CI；不以H2或本地skip替代。

## 独立审查

只读代理export_summary_review使用新上下文直接读取Issue485、规范/ADR、diff、DTO、测试及完整解析/Worker/Repository/配额调用链，未发现阻塞性代码问题。确认fallback校验、聚合边界、更新先于文件操作、终态和取消保护、API字段消费边界。其建议补兼容编号成功路径，已增加对应断言。审查未重复全量Maven，PG实际证据仍以当前CI为准。

## 回滚与现场验收

独立git revert本批并重建发布包；暂停新导出，等待活动任务结束后替换。无数据库迁移或医疗数据恢复，任务表和成功文件保留。旧代码会恢复请求阶段完整准备成本，不能回退原状态竞争、取消、清理和权限保护。

真实Windows/Chrome86、混合NAS/OSS/Nginx、等量副本与性能收益仍按#350验收。PENDING来源暂未知不授予权限，也不把创建时摘要当作固定影像快照。
