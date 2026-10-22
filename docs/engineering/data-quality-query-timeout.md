# 数据质量样本查询超时保护

风险 P1，关联 #350 / #349。复用 DataQualityController → DataQualityServiceImpl → JdbcTemplate
及既有检查目录、任务记录和指标。计数查询已有10秒超时，样本查询只有 LIMIT，不能限制扫描时间。

## 计划与验收

1. 先在 PostgreSQL16 用仅一行结果的慢样本 SQL 复现：LIMIT 不会限制执行时间。
2. 仅修改 DataQualityServiceImpl 的样本 PreparedStatement：设置与计数一致的10秒超时，绑定已有1..1000样本上限。
3. 验证超时将批次标为 FAILED、记录安全异常类型、不写成功检查结果、清除运行标志；后续新批次能成功。
4. 单元测试确认参数绑定、返回列兼容及共享 JdbcTemplate 的全局超时不被修改；PG16证明实际取消。
5. 独立上下文直接读取 Issue、diff、测试和架构审查。

不修改检查 SQL、范围口径、权限、API、Flyway、调度或配置；不开放修复，不在医院全库运行质量检查。
每条SQL限制不等于整个批次10秒内完成；超时不是完整性合格，必须在副本核对计划与失败状态。

## 回滚

独立撤销本提交并重新构建当前兼容版本，不涉及数据库迁移和业务数据恢复。
回退会恢复样本查询无独立超时的风险，回退维护期间暂停质量检查调度与手动触发。
现场性能、锁等待与全历史质量扫描继续由 #350 验收。

## 实际验证（2026-10-22）

- RED：原实现真实PG16慢样本查询没有被取消，测试失败；PreparedStatement参数/超时单测也先失败。
- `mvnw.cmd -B -ntp clean verify`：838项单元/切片、82项集成，失败/错误/跳过均0，BUILD SUCCESS。
- PG16测试证明确实在10秒左右取消慢样本，记录QueryTimeoutException类型及FAILED状态，清除运行标志；随后新批次成功。墙钟上限20秒用于容忍CI调度，实际取消以数据库异常及失败状态判定。
- 独立审查发现初始测试位置不会触发CI集成任务，已移入现有integration/service目录；实际范围检测返回integration_changed=True，完整性门禁确认21个PostgreSQL类执行且无跳过。
- 仓库工具110项、发布基线、架构边界、工程文档、测试资产与覆盖率基线检查通过；`git diff --check`通过。
- 独立上下文规格/代码复核通过。Windows11 / Java21 / Maven3.9.9；Testcontainers仅用于一次性PG16测试。
- 没有修改前端、Flyway或部署；未执行医院副本、真实存储或Chrome86验收。
