# OSS 永久失败重置的当前状态约束

提案 #584，风险 P1。基线 main@224e7865；分支 codex/oss-retry-confirmation；worktree `D:\Project\CodexWorkTress\oss-retry-confirmation\MRR`；负责人 Codex，状态待合并。

## 调查与范围升级

用户要求继续完善OSS迁移/失败处理。调查先发现前端确认范围可能漂移，但追溯 Controller“重置失败记录”→MigrationServiceImpl.retryFailedScans→ScanMapper.resetMigrationFailures 后，确认实际SQL只限制ID和空OSS地址，没有当前永久失败状态约束。已暂停前端实现，以本提案优先修后端；前端确认范围另由#585跟踪。

生产改动仅一条条件UPDATE增加 `migration_status = 'failed'`。在同一数据库语句中同时检查当前状态、空OSS地址和指定ID，避免从旧失败日志选中已进入其他状态的记录后被重置。原事务、去重、1000条上限、活动任务检查、返回实际更新数量及 `record:manage` 授权不变。状态已变化的ID返回0或部分更新，调用方不得将其当作全部重置成功。

不改变数据库结构/Flyway迁移、影像内容、对象存储、接口签名、权限或审计模型。没有新基础设施；本地Docker只运行隔离测试容器，正式部署仍为Windows/Nginx/JAR/PostgreSQL。无需新ADR。

## PostgreSQL 16 RED → GREEN

使用既有PostgresqlIntegrationTestSupport/Testcontainers、`postgres:16-alpine`，在隔离库执行Flyway，未读取生产配置或连接医院DB/OSS。

- 原SQL：9条合成记录中应只更新2条当前failed且无OSS地址记录，实际更新8条，测试明确RED；错误来自状态条件缺失，不是环境错误或Mock结果。
- 收紧后：只更新2条；retry_wait、migrating、waiting_sjh、migrated、not_migrated、NULL及已有OSS地址的failed记录，其状态、attempts、error code和重试时间均保留；相同请求第二次更新0。
- Service边界测试确认活动任务拒绝、null/空/全null范围、1001个唯一ID拒绝、去重和实际条件更新条数；不会自动创建迁移任务。

执行命令（backend-repo）：

```powershell
.\mvnw.cmd -B -ntp '-Dlocal.config.exclude=true' '-Dtest=OssMigrationManagementMapperPostgresqlIT#resetsOnlyCurrentPermanentFailures' test
.\mvnw.cmd -B -ntp '-Dlocal.config.exclude=true' '-Dtest=OssMigrationManagementMapperPostgresqlIT,MigrationServiceImplTest,OssControllerTest' test
.\mvnw.cmd -B -ntp '-Dlocal.config.exclude=true' '-Dtest=OssControllerTest,AuthorizationInterceptorTest,ControllerAccessPolicyTest' test
```

第一条原实现失败（预期2/实际8）；第二条修复后25项通过（PG3/Service16/Controller6），零跳过；补权限用例后第三条18项通过（Controller7/Interceptor9/Policy2），零跳过。两次成功命令包含重叠Controller用例，不累加为43个不同测试。

权限回归使用真实OssController.retryFailedScans Handler和原AuthorizationInterceptor+MockHttp请求响应，覆盖无会话401、record:read为403、record:manage放行，拒绝路径未调用Service。这是拦截器层证据，不是实网JWT或医院授权验收。

独立上下文读取提案、架构/治理/后端规范/ADR与最终diff、实际测试报告，未发现阻断。未执行并发事务阻塞测试；既有“是否有活动任务”检查仍是先查后写，不宣称本次建立全局任务互斥。真实医院大表/角色/OSS仍待验收。

## 回滚与发布边界

独立提交可整体git revert并重建后端受管包，按现有发布基线安装；合并前校验完整反向补丁。无迁移或存储恢复。若撤销状态约束，会恢复旧范围过宽行为，现场应暂停使用失败重置并优先前向修复；不因前端/后端代码回滚而批量逆写已正常重置的业务状态。本次开发未执行真实业务POST，无生产数据需要恢复。受管回滚包重建与现场恢复未执行。
