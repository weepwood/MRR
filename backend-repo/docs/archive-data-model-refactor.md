# 病案主数据重构说明

## 目标

旧结构将上架号、病案号和患者信息分别散落在 `mr_statistics`、`mr_scan` 与装箱表中，扫描影像只能依靠文本字段关联。本次重构新增 `app.mr_archive` 作为病案主表，并保留旧字段作为导入兼容层。

```text
mr_archive
  ├─ 1:N mr_scan
  ├─ 1:N mr_statistics（历史重复数据允许指向同一病案）
  └─ 1:N mr_archive_box_record（暂不强制唯一，先清理历史冲突）
```

- `mr_archive.id`：稳定数据库主键。
- `mr_archive.sjh`：允许 `NULL`，非空值通过部分唯一索引保证唯一。
- `mr_archive.bah`：业务查询字段，但从 `10000000` 开始不保证唯一，必须同时提供上架号。
- `archive_id`：新增到统计、扫描影像和装箱记录中。

## 兼容策略

1. 部分历史 PostgreSQL 数据库中的 `mr_statistics` 没有 `id`；`V20260715232200` 会先补齐序列、现有行 ID、非空约束和唯一索引。
2. 旧 XLSX/SQL 仍可写入 `mr_statistics`，触发器会创建或更新病案主记录并写回 `archive_id`。
3. 旧扫描写入不要求显式传入 `archive_id`；有效上架号会由触发器解析或创建主记录。
4. 缺失上架号统一使用 `NULL`，不会生成占位编号。
5. 仅病案号匹配时，数据库只接受唯一匹配；大于等于 `10000000` 的病案号缺少上架号时拒绝自动关联。
6. `mr_scan` 可能达到数千万行，Flyway 不在启动事务中全量更新，而是通过游标批次渐进回填。

## 上线步骤

### 1. 备份并执行迁移

应用启动后 Flyway 会按日期时间顺序执行：

```text
V20260715232200  兼容旧库，为 mr_statistics 补齐稳定行 ID
V20260715232228  建立病案主表、关联列、触发器与回填函数
V20260715232620  强制大病案号查询规则
V20260715232837  编号修改后刷新 archive_id
V20260715233205  优化数千万扫描记录的分批回填
```

迁移文件统一使用：

```text
VyyyyMMddHHmmss__description.sql
```

旧数据库启动时，`db/callback/beforeValidate__normalize_legacy_versions.sql` 会在校验前把历史 `V0`、`V0_0_1`、`V0_1`～`V0_4` 记录映射为对应日期时间版本。映射只修改 `app.flyway_schema_history` 的版本和脚本名称，不会重复执行迁移 SQL。

日期时间版本已经保持严格递增，默认配置为：

```properties
spring.flyway.out-of-order=${SPRING_FLYWAY_OUT_OF_ORDER:false}
spring.flyway.validate-on-migrate=true
spring.flyway.validate-migration-naming=true
```

不要手工删除或修改 `app.flyway_schema_history`。如果新旧版本记录同时存在，兼容回调会主动终止启动，要求先检查历史表，避免覆盖异常记录。

如果病案主数据迁移此前因 `mr_statistics.id` 不存在而失败，PostgreSQL 日志显示 `Changes successfully rolled back` 时，不需要删除业务表。拉取最新分支，执行一次 `mvn clean` 后重新启动即可。

详细版本规范见：

```text
backend-repo/docs/flyway-versioning.md
```

### 2. 分批回填扫描记录

Windows PowerShell：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:PGPASSWORD = '数据库密码'

./backend-repo/scripts/backfill-archive-links.ps1 -BatchSize 10000
```

中断后可使用日志中的最后 ID 继续：

```powershell
./backend-repo/scripts/backfill-archive-links.ps1 -BatchSize 10000 -StartAfterId 1230000
```

### 3. 本地检查

GitHub Actions 不可用时执行：

```powershell
./backend-repo/scripts/verify-archive-refactor.ps1
```

脚本会先检查 Flyway 日期时间命名，再执行 Maven 编译、病案服务单元测试；配置数据库连接且本机存在 `psql` 时，还会运行数据库验收 SQL。

### 4. 验证并启用外键全量校验

```sql
SELECT * FROM app.v_archive_link_quality;

ALTER TABLE app.mr_statistics VALIDATE CONSTRAINT fk_mr_statistics_archive;
ALTER TABLE app.mr_scan VALIDATE CONSTRAINT fk_mr_scan_archive;
ALTER TABLE app.mr_archive_box_record VALIDATE CONSTRAINT fk_archive_box_record_archive;
```

如果仍有未关联记录，先查看缺失上架号、重复病案号和异常编码，不要直接填充虚假上架号。

## 后端接口

```text
GET /api/v1/archive-records/{id}
GET /api/v1/archive-records/resolve?bah=00000123&sjh=00000456
GET /api/v1/archive-records/{id}/scans
```

现有扫描与统计接口继续保留，并在响应实体中增加 `archiveId`。
