# Flyway 日期时间版本规范

## 命名格式

所有新的版本化迁移必须使用：

```text
VyyyyMMddHHmmss__description.sql
```

示例：

```text
V20260716143025__add_archive_audit_index.sql
```

规则：

- 版本固定为 14 位本地日期时间，时区统一按 `Asia/Shanghai` 理解；
- 描述只使用小写字母、数字和下划线；
- 同一秒内不得创建两个迁移；如发生冲突，后一个迁移顺延一秒；
- 已提交或已执行的迁移不得修改 SQL 内容；后续修复必须新增迁移；
- `db/migration-legacy` 仅用于历史审计，不参与 Flyway 执行。

## 当前迁移映射

| 旧版本 | 新版本 | 脚本 |
| --- | --- | --- |
| `0` | `20260715113552` | `baseline_schema` |
| `0.0.1` | `20260715232200` | `ensure_legacy_statistics_surrogate_key` |
| `0.1` | `20260715232228` | `refactor_archive_data_model` |
| `0.2` | `20260715232620` | `enforce_archive_lookup_rules` |
| `0.3` | `20260715232837` | `refresh_archive_links_on_code_change` |
| `0.4` | `20260715233205` | `optimize_scan_archive_backfill` |

迁移 SQL 内容和 Blob 校验和保持不变，只调整文件名及 Flyway 历史元数据。

## 旧数据库兼容

应用启动时，Flyway 会从以下位置发现回调：

```text
classpath:db/callback
```

`beforeValidate__normalize_legacy_versions.sql` 在校验前检查 `app.flyway_schema_history`：

1. 新数据库没有历史表时直接跳过；
2. 旧版本存在时，只更新 `version` 和 `script` 字段；
3. 新旧版本同时存在时立即报错，防止错误覆盖；
4. 不执行任何业务 DDL/DML，不会重复运行历史迁移。

完成映射后，Flyway 按新的日期时间文件名正常校验。迁移版本已经按时间递增，因此默认关闭 `out-of-order`。

## 验证

在仓库根目录执行：

```powershell
./backend-repo/scripts/verify-flyway-migrations.ps1
```

脚本会检查：

- 所有活动迁移是否符合 14 位日期时间格式；
- 日期时间是否真实有效；
- 是否存在重复版本；
- 描述是否符合小写下划线规范。

架构基础验证脚本也会先调用该检查。

## 新增迁移示例

PowerShell 生成文件名：

```powershell
$version = Get-Date -Format 'yyyyMMddHHmmss'
$file = "backend-repo/src/main/resources/db/migration/V${version}__add_example.sql"
New-Item -ItemType File -Path $file
```

创建后先执行命名校验，再提交代码。
