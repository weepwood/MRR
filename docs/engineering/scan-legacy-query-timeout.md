# 旧扫描查询执行时限

关联#99，风险P1。负责人Codex，分支`codex/99-legacy-query-timeout`，worktree `D:/Project/CodexWorkTress/legacy-query-timeout/MRR`；状态进行中，等待PostgreSQL CI。基线main `2b03f9c8`。

## 调查、计划与范围

旧入口ScanController → ScanServiceImpl → ScanMapper的findAll、findAllWithPagination、findByCondition、findByConditionWithPagination、countByCondition没有显式Statement超时；LIMIT不约束扫描或锁等待成本。新条件游标语句已有3秒时限。

本批只在两个注解查询及三个XML语句设置同样的3秒JDBC时限。不新增API、缓存或索引，不修改历史Flyway；保留旧包含式搜索、OFFSET、同步COUNT、排序和权限，以免静默改变兼容语义。单条3秒不等于整个HTTP请求3秒：列表加COUNT可能接近6秒，获取连接与网络时限沿用现有机制。

先通过实际MyBatis语句配置的失败测试复现缺失预算，再做最小修改；PG16测试以独立事务持有表锁，JDBC取消后验证SQLState57014，释放锁后在同一SqlSession及物理backend pid重新执行原查询。额外验证既有异常处理不会把合成SQL正文传回客户端。数据库只使用可销毁测试库，所有锁最终回滚。

范围外：生产索引选型、编号规则与字段规范化、取消总数的API演进、旧客户端退役、等量副本查询计划。不能将此改动说成已解决三千万行查询性能。

## 本地实际证据

- RED：`mvnw.cmd -o -B -ntp -Dtest=ScanLegacyQueryTimeoutTest,ScanTimeoutErrorPrivacyTest -Dlocal.config.exclude=true test`，6项中5条缺少时限的配置断言失败，隐私响应1项通过。
- GREEN：`mvnw.cmd -o -B -ntp -Dtest=ScanLegacyQueryTimeoutTest,ScanTimeoutErrorPrivacyTest,ScanControllerTest -Dlocal.config.exclude=true test`，22项通过，失败/错误/跳过均0。
- 测试资产、发布基线、工程文档、架构边界、git diff --check通过。
- 首次PG命令误带skipTests，报告明确skipped，不作为证据；移除后实际执行5项，但因Docker Linux引擎不可用发生环境错误，不属于功能RED或验证成功。未改用H2。
- 本地未执行全量套件；PG16真实行为由当前PR CI执行并核对新类5项和既有Mapper兼容测试的实际报告。

## 独立审查

独立只读代理legacy_timeout_review直接读取Issue99、规范、diff、测试、CI及调用链，生产改动无阻塞。其发现取消后经SqlSessionTemplate重借连接不足以证明同连接恢复，已将测试改为持有同一SqlSession/Connection、比较pg_backend_pid并重跑原read。PG实际执行结论仍以当前CI为准。

## 回滚和验收

本批独立提交可通过git revert撤销并重启，无迁移、无医疗数据修复。撤销会恢复旧SQL长期等待风险；保留现有游标入口，现场大查询优先切换至游标查询，不能回退权限、令牌撤销或审计。

超时沿用现有脱敏500和错误编号，不新增异常协议或将底层SQL返回前端。真实医院锁/负载、存储和三千万行性能仍由#350验收，需记录真实查询计划及P50/P95/P99，不能由此测试代替。
