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

1. 部分历史 PostgreSQL 数据库中的 `mr_statistics` 没有 `id`；`V0_0_1` 会先补齐序列、现有行 ID、非空约束和唯一索引。
2. 旧 XLSX/SQL 仍可写入 `mr_statistics`，触发器会创建或更新病案主记录并写回 `archive_id`。
3. 旧扫描写入不要求显式传入 `archive_id`；有效上架号会由触发器解析或创建主记录。
4. 缺失上架号统一使用 `NULL`，不会生成占位编号。
5. 仅病案号匹配时，数据库只接受唯一匹配；大于等于 `10000000` 的病案号缺少上架号时拒绝自动关联。
6. `mr_scan` 可能达到数千万行，Flyway 不在启动事务中全量更新，而是通过游标批次渐进回填。

## 上线步骤

### 1. 备份并执行迁移

应用启动后 Flyway 会执行：

```text
V0_0_1  兼容旧库，为 mr_statistics 补齐稳定行 ID
V0_1    建立病案主表、关联列、触发器与回填函数
V0_2    强制大病案号查询规则
V0_3    编号修改后刷新 archive_id
V0_4    优化数千万扫描记录的分批回填
```

这些版本不占用 OSS 迁移 PR 使用的 `V1`。部分已有数据库已经先执行了 `V1`，项目因此默认允许 Flyway 补执行尚未应用的低版本迁移：

```properties
spring.flyway.out-of-order=${SPRING_FLYWAY_OUT_OF_ORDER:true}
```

`validate-on-migrate` 仍保持开启，不应删除或手工修改 `app.flyway_schema_history`。所有环境完成 `V0_0_1`～`V0_4` 后，可设置：

```powershell
$env:SPRING_FLYWAY_OUT_OF_ORDER = 'false'
```

恢复严格的版本顺序检查。

如果此前 `V0_1` 因 `mr_statistics.id` 不存在而失败，PostgreSQL 日志显示 `Changes successfully rolled back` 时，不需要删除表、执行 `flyway repair` 或修改 Flyway 历史记录。拉取最新分支，执行一次 `mvn clean` 后重新启动即可。

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

脚本会执行 Maven 编译、病案服务单元测试；配置数据库连接且本机存在 `psql` 时，还会运行数据库验收 SQL。

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
