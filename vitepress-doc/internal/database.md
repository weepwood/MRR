# 数据库

## 数据库基线

正式环境使用 PostgreSQL 16，默认连接：

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/imgapi?currentSchema=app
spring.flyway.schemas=app
spring.flyway.default-schema=app
spring.flyway.baseline-on-migrate=false
```

新数据库的唯一初始迁移为：

```text
backend-repo/src/main/resources/db/migration/V0__baseline_schema.sql
```

旧增量迁移位于 `db/migration-legacy`，只用于审计和历史追溯，不会被当前 Flyway 配置执行。仓库中的 `mrr-db/schema.sql` 是 SQLite 开发参考，不是生产 PostgreSQL 建库入口。

## V0 基线包含内容

V0 基线集中定义：

- `app` Schema。
- 当前业务、认证、审计、迁移、设置、监控和状态表。
- 索引、视图、函数和表注释。
- 必要角色与种子数据。
- `pg_stat_statements`、`pg_trgm` 等所需扩展声明。

生产部署前应确认应用数据库账号是否具备扩展和建库所需权限；必要时由 PostgreSQL 管理员提前创建扩展和 Schema。

## 核心业务表

| 表 | 主要用途 |
|----|----------|
| `mr_scan` | 扫描记录、目录、文件名、页数、类型和 OSS 状态 |
| `mr_patient` | 身份证、病案号、姓名、科室和患者关联信息 |
| `mr_statistics` | 病案统计与检索数据 |
| `mr_count` | 病案及图片页数汇总 |
| `mr_archive_box_record` | 实体病案装箱位置、预期箱号和异常状态 |
| `mr_system_settings` | 服务端系统设置键值 |

## 认证、审计与运维表

| 表 | 主要用途 |
|----|----------|
| `mr_auth_user` | 用户账号、密码哈希、角色和状态 |
| `mr_auth_role` | 角色、权限集合和排序 |
| `access_log` | 请求、客户端、状态、耗时和图片访问审计 |
| `frontend_response_metric` | 前端上报的接口响应性能指标 |
| `image_migration_log` | 单文件 OSS 迁移、校验与失败信息 |
| `migration_job` | 批量迁移任务总量、进度和状态 |
| `mrr_data_quality_run` | 数据质量检查批次 |
| `mrr_data_quality_check_result` | 每项检查汇总 |
| `mrr_data_quality_issue` | 受样本上限控制的异常明细 |
| `system_availability_period` | 服务可用性状态时间区间 |

具体字段以 V0 基线和运行数据库为准。

## 字段规范

### 病案号与上架号

`bah` 和 `sjh` 使用字符串保存，应用层统一为八位数字。不能改为整数类型，否则会丢失前导零。

数据质量检查覆盖：

- 缺失。
- 非八位数字。
- 页数无效。
- 统计记录与扫描记录关联异常。
- 装箱位置异常。

### 身份证号

身份证号属于敏感个人信息：

- API 优先返回脱敏值。
- URL 不长期保留明文。
- 日志和 Prometheus 标签不得包含明文。
- 仅业务需要的表保存该字段。

### 时间字段

历史业务数据可能包含字符串日期。新增表优先使用 PostgreSQL 时间类型并明确时区语义。服务状态默认按 `Asia/Shanghai` 统计，可由配置覆盖。

## 索引原则

V0 基线包含病案号、上架号、患者、统计日期、类型、日志时间、迁移状态、装箱状态和监控查询所需索引，并使用 BRIN、GIN Trigram、部分索引或 INCLUDE 等 PostgreSQL 能力优化特定场景。

新增索引前必须根据真实查询和执行计划判断，避免因字段“可以筛选”就无条件建索引。大表索引应评估锁、磁盘和构建时间。

## 后续迁移规范

V0 发布后，如需变更数据库，新增迁移：

```text
V1__short_description.sql
V2__another_change.sql
```

规则：

1. 不直接修改已经部署的 V0。
2. 不把 `migration-legacy` 重新加入当前迁移链。
3. DDL 必须考虑已有数据。
4. 新增非空字段时提供安全默认值或分阶段迁移。
5. 大表回填和索引安排维护窗口。
6. 迁移应可在预发布数据库完整验证。

## 旧数据库升级

已经运行旧增量迁移链的数据库，其 Flyway 历史与 V0 不兼容。不能直接：

- 删除 `flyway_schema_history`。
- 开启 `baseline-on-migrate` 强行接管。
- 把 V0 放入旧链后继续启动。
- 使用 `flyway repair` 掩盖差异。

必须单独制定迁移方案，至少包含：

1. 当前结构与 V0 的差异比较。
2. 数据备份和恢复测试。
3. 新环境初始化或数据搬迁步骤。
4. 应用停机窗口。
5. 校验记录数量、索引、约束和图片关联。

## 监控账号

PostgreSQL 监控使用独立只读角色 `mrr_monitor`：

```sql
CREATE ROLE mrr_monitor LOGIN PASSWORD '替换为独立强密码';
```

随后执行：

```bash
psql -U postgres -d imgapi -f monitoring/postgresql/enable-monitoring.sql
```

应用账号、监控账号和管理员账号应彼此分离。

## 备份与恢复

生产环境至少保留：

- PostgreSQL 定期全量备份。
- 必要时的 WAL 或增量恢复能力。
- 图片文件服务目录备份。
- OSS 对象和迁移日志一致性检查。
- Nginx、应用和监控配置备份。

恢复演练必须验证数据库记录与实际图片文件仍能对应，不能只确认数据库进程可以启动。