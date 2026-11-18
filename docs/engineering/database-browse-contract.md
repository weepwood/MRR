# 受控只读数据库浏览：接口契约与注册表定义

- 状态：Accepted（对应 ADR-0007，已随本 PR 实现）
- 日期：2026-11-19
- 适用版本：随 ADR-0007 落地的首个版本
- 关联：`docs/adr/0007-controlled-readonly-database-browse.md`

本文定义「受控只读数据库浏览」的服务端契约，供后端实现、前端对接、测试与安全评审共同使用。凡本文未列出的能力都属于非目标，不得在实现中顺带加入。

---

## 1. 命名空间与端点总览

沿用现有 `/api/v1/system/database` 命名空间，不新建第二套监控前缀。

| 方法 | 路径 | 用途 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/v1/system/database/browse/tables` | 当前账号可浏览的表清单 | `system:db:browse` |
| GET | `/api/v1/system/database/browse/tables/{tableKey}` | 单表结构：列、类型、可过滤、可排序、脱敏标记 | `system:db:browse` |
| GET | `/api/v1/system/database/browse/tables/{tableKey}/rows` | 数据行查询 | `system:db:browse` + 表级附加权限 |

约定：

- `tableKey` 是注册表内定义的稳定逻辑标识（小写蛇形，如 `patient`、`scan`），**不是**物理表名；物理表名只在服务端出现，也不允许逐字符猜表。
- 不提供 `POST` / `PUT` / `DELETE` 任何写语义端点。
- 不提供任意 SQL、任意表名、任意列名、任意 `WHERE` 片段。

---

## 2. 权限与开关

### 2.1 权限

- 新增常量 `Permissions.SYSTEM_DB_BROWSE = "system:db:browse"`，并加入 `ALL_PERMISSIONS`，使角色授权界面可以分配。
- 不复用 `system:read` 与 `system:manage`：现有 `system:read` 用户（监控查看者）不应自动获得看数据行的能力。
- `@RequirePermissions` 的多个权限是 **AND**（`AuthorizationInterceptor` 使用 `allMatch`）。因此表级附加权限**不能**写在注解上，必须在 Service 层按注册表逐表校验，防止注解把「任意表都需要 record:read」写死。
- 已知事实：`session.isAdmin()` 在权限判定前直接放行。管理员天然可访问本功能，这是既有设计，需在授权评审中显式确认，不得在文档或界面中暗示「新权限可以限制管理员」。

### 2.2 表级附加权限

注册表可为单表声明 `requiredPermissions`（AND 组合），例如：

- `mr_patient`、`mr_archive`：`["record:read"]`
- `mr_auth_user`：`["system:manage"]`
- `mr_system_settings`：`["system:manage"]`
- 字典表（如 `mr_medical_record_type`）：`[]`

判定顺序：功能开关 → 全局权限 → 表已登记 → 表已启用 → 表级权限。任一步失败即拒绝，且拒绝原因写入审计日志，但**响应中不回显表是否存在**（未登记与无权限统一返回「资源不存在」语义）。

### 2.3 开关

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `app.operations.db-browse-enabled` | `false` | 功能总开关 |

实现要求：Controller **常驻注册**，开关在 Service 层判定。不要用 `@ConditionalOnProperty(matchIfMissing = true)` 让整个 Bean 消失——项目已确认这种路径会落到 `ResourceHttpRequestHandler`，返回 500 而不是 404，前端无法区分「功能关闭」与「后端故障」。

开关关闭时返回 `404` + 业务码 `3007`，响应中不含任何表信息。

---

## 3. 通用约定

### 3.1 响应包裹

统一使用 `Result<T>`（`code` / `message` / `data` / `meta` / `timestamp`），HTTP 状态码由 `ResultCode.resolveHttpStatus` 决定，禁止业务失败返回 HTTP 200。

### 3.2 响应头

| 头 | 值 | 说明 |
| --- | --- | --- |
| `Cache-Control` | `no-store` | 敏感响应，禁止任何缓存 |
| `Pragma` | `no-cache` | 兼容旧客户端 |
| `X-MRR-Browse-Rows` | 数字 | 本次实际返回行数，供前端与审计对账 |
| `X-Request-Id` | 现有 MDC 值 | 与 `app.access_log.request_id` 对齐 |

### 3.3 时间与编码

- 库内 `TIMESTAMP WITHOUT TIME ZONE` 列按 `yyyy-MM-dd'T'HH:mm:ss` 输出，**不带** `Z`，避免前端误按 UTC 转换。
- `DATE` 列输出 `yyyy-MM-dd`。
- 所有文本按 UTF-8，JSON 不转义中文。

### 3.4 分页

- 对外参数为 `page`（默认 1）与 `size`（默认 50，上限取注册表 `maxPageSize`，硬上限 200）。
- 服务端内部**不使用 `OFFSET` 翻页**：
  - `page=1` 且无 `cursor`：`ORDER BY <keyColumns> LIMIT size`；
  - `page>1`：必须携带上一页返回的 `cursor`，否则返回 `400` + `3006`；
  - 传 `cursor` 时 `page` 仅用于展示，服务端以游标为准。
- `cursor` 为不透明字符串（Base64URL 编码的排序键与方向），前端不得解析、拼接或构造。
- 前端只提供「上一页 / 下一页」，不提供任意页码跳转。上一页由前端维护游标栈实现，服务端不返回 `prevCursor`。

### 3.5 行数与估算

- 不使用 `count(*)`。
- 仅当注册表 `estimateRowCount = true` 时返回估算值，并标记 `estimated: true`。
- 取值顺序：`pg_class.reltuples` ≥ 0 时直接用；为 `-1`（从未 ANALYZE）时退回
  `pg_stat_user_tables.n_live_tup`；两者都不可用才返回 `null`。
- **不要用 `relpages = 0` 判断空表**：`pg_class.relpages` 只在 VACUUM/ANALYZE 时更新，
  新建表插入数据后仍是 0。实测 `flyway_schema_history` 有 44 行、`mr_auth_role` 有 4 行，
  两张表都是 `reltuples = -1` 且 `relpages = 0`，按 relpages 推断会把它们显示成「0 行」。
- 前端区分三种状态：`null` → 「未统计」，`0` → 「0 行」，其余 → 「约 N 行」。
  把「不知道」显示成「没有数据」比不显示更糟。

---

## 4. 注册表定义

注册表是唯一的可见性来源，建议落在 `com.zjcxph.imgapi.setting.browse` 或同级新包，用不可变对象 + 构造校验（缺字段启动即失败）。注册表为空是合法状态（等于全关闭）。

### 4.1 表级：`DbBrowseTableDefinition`

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
| --- | --- | --- | --- | --- |
| `key` | String | 是 | 对外标识，URL 段 | `^[a-z][a-z0-9_]{2,40}$`，全局唯一 |
| `schemaName` | String | 是 | 固定 `app` | 必须等于 `app`，其他值启动失败 |
| `tableName` | String | 是 | 物理表名 | `^[a-z][a-z0-9_]{2,60}$`，且启动自检时须存在于 `information_schema.tables` |
| `label` | String | 是 | 中文展示名 | 非空，≤40 字 |
| `description` | String | 否 | 用途说明 | ≤200 字 |
| `enabled` | boolean | 是 | 灰度开关 | 默认 `false` |
| `requiredPermissions` | List\<String\> | 是 | 表级附加权限（AND） | 元素必须属于 `Permissions.ALL_PERMISSIONS` |
| `keyColumns` | List\<String\> | 是 | 稳定排序键，用于游标与去重 | 非空；必须对应主键或唯一索引；必须全部是已登记列 |
| `defaultOrder` | List\<String\> | 否 | 默认排序 | 为空时取 `keyColumns`；元素必须在 `orderableColumns` 内 |
| `orderableColumns` | Set\<String\> | 是 | 可排序列白名单 | 必须是已登记列；建议只放有索引的列 |
| `maxPageSize` | int | 否 | 该表页大小上限 | 1..200，默认 50 |
| `requireFilter` | boolean | 是 | 是否强制带过滤条件 | 默认 `false`；仅当排序键没有可用索引时才设 `true`，否则首屏会退化成全表扫描 |
| `estimateRowCount` | boolean | 是 | 是否返回行数估算 | 大表可 `true`（`reltuples` 成本极低） |
| `personalData` | boolean | 是 | 是否含个人敏感信息 | `true` 时前端须显示水印提示，审计级别提升 |

### 4.2 列级：`DbBrowseColumnDefinition`

| 字段 | 类型 | 必填 | 说明 | 校验规则 |
| --- | --- | --- | --- | --- |
| `name` | String | 是 | 物理列名 | `^[a-z][a-z0-9_]{0,60}$`；必须存在于表结构自检结果中 |
| `label` | String | 是 | 中文列名 | 非空，≤30 字 |
| `type` | enum | 是 | `TEXT` / `NUMBER` / `BOOLEAN` / `DATETIME` / `DATE` / `JSON` / `BINARY` | — |
| `visible` | boolean | 是 | 是否出现在响应 | `BINARY` 必须 `false` |
| `filterable` | boolean | 是 | 是否可作为过滤字段 | `BINARY` 必须 `false` |
| `orderable` | boolean | 是 | 是否可排序 | `BINARY` / `JSON` 建议 `false` |
| `mask` | enum | 是 | 脱敏策略，见 4.3 | 敏感列必须非 `NONE` |
| `maxLength` | int | 否 | 文本截断长度 | 默认 512，上限 4096；`TEXT` / `JSON` 生效 |
| `allowedOps` | Set\<Op\> | 否 | 允许的过滤操作符 | 见 4.4；为空时取默认集合 |

### 4.3 脱敏枚举 `MaskStrategy`

| 值 | 规则 | 适用 |
| --- | --- | --- |
| `NONE` | 原值返回 | 非敏感列 |
| `FULL` | 整列不返回，仅计入 `omittedColumnCount` | 密码哈希、盐、令牌、密钥类 |
| `NAME` | 保留首字符，其余替换为 `*`（`张*`、`张**`） | 患者姓名 |
| `ID_CARD` | 保留前 6 后 4，中间 `*`；长度不足 18 时全掩 | 身份证号 |
| `CODE` | 保留后 4 位，前面 `*`；≤4 位时全掩 | 病案号、上架号（精确匹配仍可用，返回值掩码） |
| `PHONE` | 保留前 3 后 4 | 联系电话 |
| `PATH` | 只保留文件名，去掉目录部分 | 本地/NAS 路径 |

约束：

- `FULL` 列的**列名也不得出现在响应中**，只返回 `omittedColumnCount`。否则等于泄露「系统有 password_hash 这一列」。
- 掩码在服务端完成，`mask` 标记随列元数据下发，前端只负责展示。
- `CODE` 掩码配合过滤使用：过滤在库内按原值匹配，返回时掩码；不得为了展示而放宽脱敏。
- 掩码列只允许等值过滤（见 4.4）；`PREFIX` 会把掩码变成可逐字符还原的构造过程。
- 掩码只覆盖结构化列。`mr_scan.filename`、`image_migration_log.local_path` 这类由外部系统
  命名的字段内容不受控，可能本身含病案号；它们所在表已标 `personalData = true`，
  但「掩码列 = 该表所有标识都已脱敏」不成立，不要据此推断。
- `access_log`、`archive_ip_binding` 里的账号与 IP 属一般个人标识，但没有可掩码的列，
  因此 `personalData = false`；它们仍受 `system:manage` 权限与请求级审计约束。

### 4.4 过滤操作符 `Op`

| 值 | 语义 | 备注 |
| --- | --- | --- |
| `EQ` | 等值 | 默认允许 |
| `NE` | 不等 | 默认允许 |
| `GT` / `GTE` / `LT` / `LTE` | 比较 | 仅 `NUMBER` / `DATETIME` / `DATE` 默认允许 |
| `PREFIX` | 前缀匹配 | 服务端拼 `LIKE ?`，值中的 `%`、`_`、`\` 必须转义；**只对 `mask = NONE` 的文本列开放**，否则可逐字符逼近被掩码的原值 |
| `IS_NULL` / `IS_NOT_NULL` | 空值判断 | 不需要值 |

禁止：`REGEXP`、`ILIKE`、`SIMILAR TO`、`IN`（超长列表）、`OR` 组合、括号嵌套、子查询。

**不提供「包含」（前导通配）匹配**：它无法使用索引，且对掩码列等价于全文探测；需要按片段查找时由业务检索接口承担。

### 4.5 登记清单（当前实现，32 张表）

表定义按领域放在 `backend-repo/src/main/java/com/zjcxph/imgapi/setting/browse/catalog/`：

| 目录类 | 覆盖范围 |
|---|---|
| `PlatformCatalog` | 平台自身产生的运行与配置数据 |
| `RecordCatalog` | 病案业务数据（全部个人数据，`record:read`） |
| `AnalyticsCatalog` | OCR、影像智能分类、数据质量（`record:read`） |
| `OpsCatalog` | 错误、审计、账号角色、迁移与版本记录 |

`DbBrowseRegistry` 只做装配：逐个 `Supplier` 构建并捕获 `IllegalArgumentException`，
单表定义非法时打日志禁用该表，不阻断启动。**副作用是写错的表会静默消失**，因此
`DbBrowseRegistryTest` 用固定清单断言登记结果，`DatabaseBrowseCatalogPostgresqlIT`
再拿真实库核对每一列是否存在、能否执行。

| 表标识 | 物理表 | 附加权限 | 强制过滤 | 脱敏与剪列 | 默认 |
|---|---|---|---|---|---|
| `frontend_response_metric` | `frontend_response_metric` | 无 | 否 | 无个人数据 | 启用 |
| `system_settings` | `mr_system_settings` | `system:manage` | 否 | `setting_value` 整列剪掉；`updated_by` 姓名脱敏 | **关闭** |
| `archive_box_record` | `mr_archive_box_record` | `record:read` | 否 | `bah`/`sjh` 保留后 4 位 | 启用 |
| `patient` | `mr_patient` | `record:read` | 否 | `idcard` 前 6 后 4、`bah` 后 4 位、`name` 保留首字 | 启用 |
| `archive` | `mr_archive` | `record:read` | 否 | `bah`/`sjh`/`patient_id` 后 4 位、`patient_name` 保留首字 | 启用 |
| `scan` | `mr_scan` | `record:read` | 否 | `bah`/`sjh` 后 4 位、`folder` 只留文件名、`oss_url` 整列剪掉 | 启用 |
| `statistics` | `mr_statistics` | `record:read` | 否 | `bah`/`sjh`/`patientid` 后 4 位、`patientname` 保留首字 | 启用 |
| `data_quality_issue` | `mrr_data_quality_issue` | `record:read` | 否 | `bah`/`sjh` 后 4 位，`detail` 截断 500 | 启用 |
| `system_error_event` | `system_error_event` | `system:error:read` | 否 | `error_ids` 整列剪掉，堆栈截断 2000 | 启用 |
| `access_log` | `access_log` | `system:manage` | 否 | `query_string`/`request_body`/`referer` 整列剪掉 | 启用 |
| `auth_user` | `mr_auth_user` | `system:manage` | 否 | `password_hash`/`password_version` 整列剪掉；`display_name` 保留首字；`contact_info` 电话脱敏 | 启用 |
| `auth_role` | `mr_auth_role` | `system:manage` | 否 | 角色名称非个人数据，原样返回 | 启用 |
| `archive_ip_binding` | `mr_archive_ip_binding` | `system:manage` | 否 | 无 | 启用 |
| `external_access_log` | `mr_external_archive_access_log` | `system:manage` | 否 | `bah`/`sjh` 后 4 位 | 启用 |
| `archive_search_history` | `mr_archive_search_history` | `system:manage` | 否 | `bah`/`sjh` 后 4 位 | 启用 |
| `migration_job` | `migration_job` | `system:read` | 否 | 无 | 启用 |
| `image_migration_log` | `image_migration_log` | `system:read` | 否 | `local_path` 只留文件名、`oss_url` 整列剪掉 | 启用 |
| `flyway_history` | `flyway_schema_history` | `system:manage` | 否 | 无 | 启用 |
| `ocr_lexicon_set` | `mr_ocr_lexicon_set` | `record:read` | 否 | 词典配置非个人数据，`checksum` 截断 64 | 启用 |
| `ocr_lexicon_entry` | `mr_ocr_lexicon_entry` | `record:read` | 否 | `aliases`/`common_ocr_errors` 截断 500 | 启用 |
| `ocr_batch` | `mr_ocr_batch` | `record:read` | 否 | `config_snapshot` 截断 300 | 启用 |
| `classification_rule_set` | `mr_classification_rule_set` | `record:read` | 否 | 同词典集 | 启用 |
| `classification_rule_entry` | `mr_classification_rule_entry` | `record:read` | 否 | `keyword` 截断 200 | 启用 |
| `image_type_audit` | `mr_image_type_audit` | `record:read` | 否 | 无个人数据；`operator_username` 为登录账号，原样返回以便核对归属 | 启用 |
| `image_classification_suggestion` | `mr_image_classification_suggestion` | `record:read` | 否 | `matched_rules`/`excluded_rules` 截断 300 | 启用 |
| `smart_classification_job` | `mr_smart_classification_job` | `record:read` | 否 | **`lease_token` 整列剪掉**（租约令牌等同凭据） | 启用 |
| `smart_classification_job_item` | `mr_smart_classification_job_item` | `record:read` | 否 | 复合主键 `(job_id, scan_id)`；`failure_message` 截断 500 | 启用 |
| `data_quality_run` | `mrr_data_quality_run` | `record:read` | 否 | `error_message` 截断 500 | 启用 |
| `data_quality_check` | `mrr_data_quality_check_result` | `record:read` | 否 | 无 | 启用 |
| `runtime_log_source` | `runtime_log_source` | `system:read` | 否 | `file_name` 只有文件名、不含目录 | 启用 |
| `availability_period` | `system_availability_period` | `system:read` | 否 | 无 | 启用 |
| `permission_matrix_snapshot` | `permission_matrix_snapshot` | `system:manage` | 否 | `matrix_json` 截断 2000 | 启用 |

**明确不登记的表**（不是遗漏，改登记前必须先解决对应问题）：

| 表 | 不登记的原因 |
|---|---|
| `mr_system_settings` | 已登记但默认关闭，配置值含凭据类键 |
| `token_revocation`、`mr_external_archive_ticket`、`mr_external_archive_nonce`、`mr_external_archive_session` | 凭据与外部调阅握手材料，可浏览等于可伪造调阅 |
| `runtime_log_event` 及其月分区 | 分区父表查询会跨全部月分区；日志另有专门页面 |
| `archive_export_job`、`runtime_error_projection_receipt`、`runtime_log_capacity_sample` | 主键为 `uuid` / `timestamptz`，游标第 2 页会因 `uuid < varchar` 报错，需先补游标类型支持 |
| `mr_scan_ocr`、`mr_scan_ocr_history` | 含 `ocr_text`（病历原文），需先定整列剪掉或截断策略 |

**权限分档理由**：审计类（`access_log`、`external_access_log`、`archive_search_history`）
记录的是「谁在什么时候查了哪个病案」，这本身是敏感事实，所以统一收到 `system:manage`；
纯技术状态的错误事件与迁移任务对 `system:read` 开放；病案内容统一走 `record:read`。

**大表为什么不再强制过滤**：`mr_scan`（三千万级）与 `mr_patient` 原先设了
`requireFilter = true`，效果是运维点开表只看到「请先输入过滤条件」，无法判断表里到底有没有数据。
现在改为可直接打开第一页，代价由三条既有护栏兜住：

1. 排序键就是主键，`ORDER BY id DESC LIMIT n` 走索引反向扫描，代价是 `O(页大小)` 而不是全表扫描；
2. 翻页只能用游标（`page > 1` 必须带 `cursor`），不存在深 `OFFSET`；
3. 单条语句 3 秒超时，单页上限 50 行（`scan` / `patient`）。

`requireFilter` 机制保留：将来若登记一张排序键没有索引的表，必须把它设回 `true`，
否则首屏会退化成全表扫描。当前 32 张表**没有任何一张**开启它，`DbBrowseRegistryTest` 断言了这一点，
以免有人悄悄放宽后又忘了收紧。

**永不登记**（不是遗漏，是明令排除）：

| 表 | 原因 |
|---|---|
| `token_revocation` | 除 `token_hash` 与过期时间外没有别的列，任何返回都是把凭据材料搬到前端 |
| `mr_external_archive_ticket` / `mr_external_archive_nonce` | 一次性凭据与防重放材料 |
| `runtime_log_event_2026xx` | 按月分区，物理表名随月份变化；运行日志已有专用接口 |
| `archive_export_job` | 主键是 `uuid`，游标的键集比较需要 UUID 类型支持，当前 `ColumnType` 未提供，登记后翻页会报类型错误 |
| 一切影像二进制表 | `ColumnType.BINARY` 一律不可见，且没有任何浏览价值 |

### 4.6 列类型与日期列

`ColumnType.DATE` 与 `DATETIME` 的差别只在 SQL 侧格式化模板：日期列用 `YYYY-MM-DD`，
时间列用 `YYYY-MM-DD"T"HH24:MI:SS`。用错会让「出院日期」这类字段返回
`2026-01-01T00:00:00`，看起来像带了错误的时间。

---

## 5. 端点契约

### 5.1 表清单

`GET /api/v1/system/database/browse/tables`

响应 `data` 为数组，只包含「已登记 + `enabled=true` + 当前账号通过表级权限」的表：

```json
[
  {
    "tableKey": "mr_medical_record_type",
    "label": "病案类型字典",
    "description": "病案分类字典表",
    "personalData": false,
    "requireFilter": false,
    "estimatedRows": 128,
    "estimated": true,
    "columnCount": 3
  }
]
```

要求：注册表为空或全部无权限时返回 `[]`，不返回全表清单，不回显被过滤掉的表示。

### 5.2 表结构

`GET /api/v1/system/database/browse/tables/{tableKey}`

```json
{
  "tableKey": "mr_patient",
  "label": "患者主索引",
  "personalData": true,
  "requireFilter": true,
  "keyColumns": ["id"],
  "maxPageSize": 50,
  "columns": [
    { "name": "id", "label": "主键", "type": "NUMBER", "filterable": false, "orderable": true, "mask": "NONE", "allowedOps": ["EQ"] },
    { "name": "bah", "label": "病案号", "type": "TEXT", "filterable": true, "orderable": true, "mask": "CODE", "allowedOps": ["EQ", "PREFIX"] },
    { "name": "patient_name", "label": "姓名", "type": "TEXT", "filterable": true, "orderable": false, "mask": "NAME", "allowedOps": ["EQ"] }
  ],
  "omittedColumnCount": 1
}
```

要求：`mask = FULL` 的列不出现在 `columns`，只计入 `omittedColumnCount`；不返回列注释、默认值、索引定义等库内元数据。

### 5.3 数据行

`GET /api/v1/system/database/browse/tables/{tableKey}/rows`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 默认 1；`>1` 时必须带 `cursor` |
| `size` | 否 | 默认 50，上限 `min(maxPageSize, 200)` |
| `cursor` | 否 | 不透明游标，`page>1` 时必填 |
| `orderBy` | 否 | 必须属于 `orderableColumns`；缺省用 `keyColumns`。阶段一限制：可排序列只登记 `keyColumns` 的子集，排序键最终必须落在唯一键上，否则 keyset 翻页会丢行或重复 |
| `order` | 否 | `asc` / `desc`，默认 `desc`（按时间倒序更符合排障习惯） |
| `filter` | 否 | 形如 `col:op:value`，多个用 `,` 连接，语义为 AND，最多 5 个 |

`filter` 规则：

- `col` 必须是该表登记为 `filterable` 的列；
- `op` 必须在 `allowedOps` 内；
- `value` 按列类型解析：`NUMBER` 必须可解析为长整型或 `BigDecimal`，`DATETIME` 必须为 `yyyy-MM-dd'T'HH:mm:ss`，`DATE` 必须为 `yyyy-MM-dd`，`BOOLEAN` 只接受 `true`/`false`；
- `value` 中出现 `,` 时用 URL 编码 `%2C`，服务端按「先按 `,` 切分再还原 `%2C`」处理；
- 服务端只还原 `%2C`（大小写皆可），**不做完整 URL 解码**：完整解码会把值里本来就有的 `+` 变成空格、把 `%` 当转义起点，反而搜不到原值；
- `DATETIME` / `DATE` 的值在服务端转成 `java.sql.Timestamp` / `java.sql.Date` 后绑定。直接把字符串交给 JDBC 会得到「operator does not exist: timestamp >= character varying」，而这条路径在 mock 掉数据访问层的单测里看不见（已由 `DatabaseBrowsePostgresqlIT` 覆盖）；
- `IS_NULL` / `IS_NOT_NULL` 写成 `col:IS_NULL`（两段）。

响应示例：

```json
{
  "code": 200,
  "message": "获取数据成功",
  "data": {
    "tableKey": "patient",
    "columns": [
      { "name": "id", "label": "主键", "type": "NUMBER", "mask": "NONE" },
      { "name": "bah", "label": "病案号", "type": "TEXT", "mask": "CODE" },
      { "name": "name", "label": "姓名", "type": "TEXT", "mask": "NAME" }
    ],
    "rows": [
      { "id": 100231, "bah": "****5678", "name": "张*" }
    ],
    "page": 1,
    "size": 50,
    "hasMore": true,
    "nextCursor": "eyJrIjoicGF0aWVudCIsImMiOlsiaWQiXSwiZCI6ImRlc2MiLCJ2IjpbMTAwMjMxXX0",
    "estimatedRows": 812345,
    "estimated": true,
    "omittedColumnCount": 1,
    "truncatedColumns": ["ocr_text"],
    "tookMs": 42
  },
  "meta": { "requestId": "a1b2c3d4e5f60718" }
}
```

字段说明：

- `nextCursor`：`hasMore=false` 时为 `null`。结构为 Base64URL 的 `{k: tableKey, c: 排序键, d: asc|desc, v: 键值}`；`d` 与请求方向不一致、`c` 与当前排序键不一致、`v` 长度或类型不匹配，一律按游标无效（`3006`）处理，不做「尽量解释」。
- `truncatedColumns`：因 `maxLength` 被截断的列名，前端须显示「已截断」提示。
- `tookMs`：服务端实际执行耗时，用于前端提示慢查询。
- 行对象只包含 `columns` 中可见的列，键名即列名；不额外注入 `__rowId` 等内部字段。

---

## 6. 错误码

在 `ResultCode` 新增 3xxx 段（2xxx 已被 OSS 占用）：

| 码 | 枚举名 | HTTP | 场景 | 响应文案 |
| --- | --- | --- | --- | --- |
| 3001 | `DB_BROWSE_TABLE_NOT_FOUND` | 404 | 表未登记 / 未启用 / 无表级权限（三者统一） | 资源不存在 |
| 3002 | `DB_BROWSE_PARAMETER_INVALID` | 400 | 列不存在、不可过滤、不可排序、操作符不允许、值类型不合法 | 请求参数错误 |
| 3003 | `DB_BROWSE_FILTER_REQUIRED` | 400 | 该表要求过滤但未提供 | 该表必须提供过滤条件 |
| 3004 | `DB_BROWSE_QUERY_TIMEOUT` | 504 | 超过 `statement_timeout` | 查询超时，请缩小过滤范围 |
| 3005 | `DB_BROWSE_BUDGET_EXCEEDED` | 400 | `size` 超限、过滤条件超过 5 个 | 查询范围超出限制 |
| 3006 | `DB_BROWSE_CURSOR_INVALID` | 400 | 游标损坏、过期、与当前表或排序不匹配 | 分页游标无效，请重新查询 |
| 3007 | `DB_BROWSE_DISABLED` | 404 | 功能开关关闭 | 资源不存在 |

约定：

- 3001 与 403 的区分：**未登记/未启用统一 404**，表级权限不足也归入 3001，避免通过状态码枚举出系统有哪些表。
- 任何错误响应都不得包含 SQL 片段、表名、列名、连接串、驱动异常堆栈。
- 超时不得返回「部分结果」，必须整体失败。

---

## 7. 审计

沿用现有两套机制，不新建审计表。

### 7.1 请求级（自动，无需新代码）

`LogInterceptor` 已对所有非跳过请求写入 `app.access_log`：`username`、`client_ip`、`request_uri`、`query_string`、`request_body`、`response_status`、`execute_time`。数据浏览的 `filter`、`page`、`size`、`orderBy` 都在 `query_string` 中，且 `SENSITIVE_PARAMETER_NAMES` 已覆盖 `idcard`、`phone`、`token` 等键名。

要求：前端**不得**把过滤值放到 POST body 或自定义头里绕过记录；必须走 query string。

已知限制：`AuditPayloadSanitizer` 会把 `query_string` 里的 `filter` 整体替换为 `[REDACTED]`（未登记参数一律脱敏），所以请求级审计只能证明「有人在某时查了某端点」，看不到过滤摘要。过滤摘要只存在于 7.2 的业务日志中，这也是 7.2 必须覆盖拒绝路径的原因。

### 7.2 业务级（需实现）

Service 层在每次请求结束时写一条结构化运行日志（走 ADR-0002 的运行日志体系），字段固定：

| 字段 | 说明 |
| --- | --- |
| `event` | 固定 `DB_BROWSE` |
| `requestId` | 与 MDC 一致 |
| `tableKey` | 表标识 |
| `outcome` | `OK` / `DENIED` / `BAD_REQUEST` / `TIMEOUT` |
| `rowCount` | 实际返回行数，拒绝时为 0 |
| `filterDigest` | 过滤条件的结构摘要（列名 + 操作符，值只保留长度与前 2 位） |
| `durationMs` | 服务端耗时 |
| `estimated` | 是否走了行数估算 |

要求：

- 拒绝路径同样写一条 `outcome=DENIED`，否则无法发现越权探测。
- 审计写入失败不得阻断读取（与 ADR-0002 日志降级一致），但必须在应用日志中暴露缺口。
- 不记录行内容、不记录完整过滤值、不记录身份证/姓名原文。

### 7.3 保留期

运行日志保留期短于合规要求时，再评估新增 `app.db_browse_audit` 表（前向迁移）。当前不预建。

---

## 8. 服务端护栏实现要点

1. **只读数据源**（实现偏差，已记录）：阶段一使用业务 `DataSource` + 专用 `JdbcTemplate`（`setQueryTimeout(3)`），配合 `@Transactional(readOnly = true)` 保证只读，不新建连接池。原计划的独立 `dbBrowseDataSource`（`maximumPoolSize=2`）需要生产侧新增只读账号配置，列为后续项。代价是极端慢查询会占用业务连接最多 3 秒；由 `statement_timeout` 等效的驱动超时兜住。
2. **SQL 构造**：只有 `SELECT <白名单列> FROM app.<注册表表名> WHERE ... ORDER BY <白名单列> LIMIT ?` 一种形态；表名与列名来自注册表对象（非字符串拼接用户输入）；所有值走 `?` 绑定。
3. **排序**：`orderBy` 只接受注册表里的字符串常量，通过 `Map<String, String>` 映射到物理列，未命中即 400。
4. **过滤**：按 `Op` 枚举分支生成固定 SQL 片段，不拼接用户提供的操作符文本。
5. **列裁剪**：`visible=false` 的列不出现在 `SELECT` 列表中；`mask=FULL` 的列在 SQL 层就不查（最彻底），而不是查出后再丢弃。
6. **截断**：`TEXT` / `JSON` 列在 SQL 中用 `left(col, ?)`，或在结果映射层截断；两种方式择一，必须一致。
7. **开关与自检**：启动时校验注册表（表存在、列存在、keyColumns 有唯一索引），校验失败只记录告警并禁用该表，不阻断应用启动（避免运维功能拖垮业务启动）。
8. **只读降级兼容**：ADR-0006 的只读降级不拦截 GET，浏览接口在降级期间仍可用；不得为绕过降级而改造拦截器。

---

## 9. 失败路径测试清单

编号规则 `TC-DBB-<组>-<序号>`。P1 变更要求这些用例在实现 PR 中先行编写（RED → GREEN）。

### 9.1 权限与开关（AUTH）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-AUTH-01 | 无 Token 请求任一浏览端点 | 401，不进入 Service |
| TC-DBB-AUTH-02 | 有 Token，无 `system:db:browse` | 403「没有接口访问权限」 |
| TC-DBB-AUTH-03 | 有 `system:db:browse`，表要求 `record:read`，用户无该权限 | 404 + 3001（不返回 403，避免暴露表存在） |
| TC-DBB-AUTH-04 | 有 `system:db:browse` 与 `record:read` | 200，正常返回 |
| TC-DBB-AUTH-05 | 管理员账号（`isAdmin()`）无显式权限 | 200（记录既有行为，测试固化而非改变） |
| TC-DBB-AUTH-06 | `app.operations.db-browse-enabled=false` | 404 + 3007，响应不含表名 |
| TC-DBB-AUTH-07 | 开关关闭时请求 `browse/tables` | 404 + 3007，不是 500，不是空数组 |
| TC-DBB-AUTH-08 | 权限校验在类级与方法级同时存在 | 方法级优先（与 `AuthorizationInterceptor` 现有语义一致） |

### 9.2 白名单（WL）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-WL-01 | `tableKey` 未登记（如 `mr_scan_typo`） | 404 + 3001 |
| TC-DBB-WL-02 | `tableKey` 为物理表名但未登记（如 `access_log`） | 404 + 3001 |
| TC-DBB-WL-03 | `tableKey` 为系统表（`pg_catalog.pg_user`、`information_schema.tables`） | 404 + 3001，URL 编码变体同样 404 |
| TC-DBB-WL-04 | `tableKey` 为 `flyway_schema_history` | 404 + 3001 |
| TC-DBB-WL-05 | `tableKey` 大小写混写（`MR_PATIENT`） | 归一化后命中或 404，不得命中未登记表；行为需固定并被测试 |
| TC-DBB-WL-06 | 注册表为空 | `browse/tables` 返回 `[]`，任何 `{tableKey}` 返回 3001 |
| TC-DBB-WL-07 | 表已登记但 `enabled=false` | 不出现在清单，直接访问 404 + 3001 |
| TC-DBB-WL-08 | 注册表声明了库中不存在的列 | 启动自检告警并禁用该表；请求返回 3001，不抛 500 |
| TC-DBB-WL-09 | 响应中不出现被 `FULL` 掩码的列名 | 断言响应体不含该列名，仅 `omittedColumnCount` |

### 9.3 注入与参数（INJ）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-INJ-01 | `orderBy=id;DROP TABLE app.mr_patient--` | 400 + 3002，SQL 未执行 |
| TC-DBB-INJ-02 | `orderBy` 为非登记列 | 400 + 3002 |
| TC-DBB-INJ-03 | `filter=id:EQ:1;DELETE FROM app.mr_patient` | 400，值按字符串处理不报 SQL 错 |
| TC-DBB-INJ-04 | `filter` 列名不在注册表（如 `password_hash`） | 400 + 3002 |
| TC-DBB-INJ-05 | `filter` 操作符为 `REGEXP` / `ILIKE` / `SIMILAR` | 400 + 3002 |
| TC-DBB-INJ-06 | 值为 `' OR 1=1--`、`\`、`%`、`_` | 绑定执行，返回 0 行；`PREFIX` 下 `%`/`_` 被转义，不作为通配 |
| TC-DBB-INJ-07 | 表名作为 URL 段含 `..`、`/`、`%00` | 404，路由不匹配或注册表未命中 |
| TC-DBB-INJ-08 | `size=1e9`、`size=-1`、`size=0` | 400 + 3005 或 3002，不执行查询 |
| TC-DBB-INJ-09 | `filter` 提供 6 个条件 | 400 + 3005 |
| TC-DBB-INJ-10 | 错误响应体不含 SQL、表名、堆栈 | 断言响应不含 `SELECT`、`app.`、`Exception` |

### 9.4 大表与预算（BIG）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-BIG-01 | 注册表声明 `requireFilter=true` 的表，无 `filter` | 400 + 3003，未执行查询 |
| TC-DBB-BIG-02 | 同上，带 `filter=bah:EQ:<值>` | 200，行数 ≤ size |
| TC-DBB-BIG-02b | `mr_scan`（`requireFilter=false`）无 `filter` | 200，走主键索引反向扫描，耗时与页大小同阶 |
| TC-DBB-BIG-03 | `page=1000` 且无 `cursor` | 400 + 3006，不产生 OFFSET 查询 |
| TC-DBB-BIG-04 | 带合法 `cursor` 翻到第 3 页 | 200，结果与 `ORDER BY keyColumns` 一致，无重复无缺行 |
| TC-DBB-BIG-05 | `cursor` 属于另一张表 | 400 + 3006 |
| TC-DBB-BIG-06 | 构造的 `cursor`（Base64 篡改排序键） | 400 + 3006，不执行任意排序 |
| TC-DBB-BIG-07 | 走行数估算路径 | SQL 中不含 `count(`（用 `pg_class` 或打桩断言） |
| TC-DBB-BIG-08 | 慢查询触发 `statement_timeout` | 504 + 3004，不返回部分行，连接归还池 |
| TC-DBB-BIG-09 | 并发 5 个请求占满只读池（`max=2`） | 排队或快速失败，业务 Hikari 指标不受影响 |

### 9.5 脱敏（MASK）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-MASK-01 | 姓名 `张三` / `欧阳修文` | `张*` / `欧***`，长度与原文一致 |
| TC-DBB-MASK-02 | 18 位身份证 | 保留前 6 后 4，中间 `*` |
| TC-DBB-MASK-03 | 15 位身份证 / 空值 / 非数字 | 全掩或返回 `null`，不抛异常 |
| TC-DBB-MASK-04 | 病案号 `12345678` | `****5678`；用原值 `EQ` 过滤仍能命中 |
| TC-DBB-MASK-05 | `password_hash` 类列 | 不出现在 `SELECT`、不出现在 `columns`、`omittedColumnCount` +1 |
| TC-DBB-MASK-06 | 文本列超过 `maxLength` | 截断，列名进 `truncatedColumns` |
| TC-DBB-MASK-07 | `BINARY` 列 | 不返回、不可过滤，不影响其他列 |
| TC-DBB-MASK-08 | 响应体全量扫描 | 不含原文姓名、原文身份证、任何密钥形态字符串 |

### 9.6 只读与副作用（RO）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-RO-01 | 浏览前后对比表行数与 `xmin` | 无变化 |
| TC-DBB-RO-02 | 只读事务内执行写语句（测试直连注入） | PostgreSQL 报只读事务错误，接口不可能触达该路径 |
| TC-DBB-RO-03 | 只读降级模式（`READ_ONLY_DEGRADED`）下浏览 | 200（GET 不受拦截），且不改变降级状态 |
| TC-DBB-RO-04 | 请求触发序列/函数 | 注册表不登记含副作用的视图或函数列，测试断言无 `nextval` 调用 |

### 9.7 审计（AUD）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-AUD-01 | 成功浏览 | `access_log` 有对应 `request_uri` 与 `query_string`；运行日志有 `DB_BROWSE` + `OK` + `rowCount` |
| TC-DBB-AUD-02 | 因无表级权限被拒 | 运行日志 `DENIED`，`rowCount=0`；`access_log` 记录 404 |
| TC-DBB-AUD-03 | 因 `requireFilter` 被拒 | 运行日志 `BAD_REQUEST` |
| TC-DBB-AUD-04 | 查询超时 | 运行日志 `TIMEOUT` |
| TC-DBB-AUD-05 | 审计/日志写入失败 | 业务响应仍为 200，应用日志出现缺口告警 |
| TC-DBB-AUD-06 | 过滤值含身份证号 | 运行日志与 `access_log` 中均无原文（`filterDigest` 只保留长度与前 2 位） |

### 9.8 前端与兼容（FE）

| 编号 | 场景 | 期望 |
| --- | --- | --- |
| TC-DBB-FE-01 | 无 `system:db:browse` 的账号登录 | 侧边栏与路由均无入口，直接访问路由被守卫拦截 |
| TC-DBB-FE-02 | 接口返回 403 | 显示「无权限」提示，不是空白页或无限加载 |
| TC-DBB-FE-03 | 接口返回 3003 | 提示需先输入过滤条件，不自动重试 |
| TC-DBB-FE-04 | 接口返回 504 | 提示缩小范围，不自动重试 |
| TC-DBB-FE-05 | 401 | 走统一刷新令牌流程，刷新失败回登录页 |
| TC-DBB-FE-06 | 含 `truncatedColumns` | 列头显示截断标记 |
| TC-DBB-FE-07 | 含 `personalData=true` 的表 | 页面显示敏感数据提示 |
| TC-DBB-FE-08 | 浏览器返回/刷新 | 不恢复上次查询结果（不持久化敏感响应） |
| TC-DBB-FE-09 | Chrome 86 快照 | 表格、分页、提示正常渲染，无布局塌陷 |
| TC-DBB-FE-10 | 响应缓存 | 网络面板 `Cache-Control: no-store`，无 disk cache 命中 |

---

## 10. 非目标与后续

不在本契约内，且不得顺手实现：

- 任意 SQL、查询历史保存、执行计划、索引建议；
- 写操作、DDL、`COPY`、批量导出；
- 跨表关联查询、聚合统计（统计走 `statistics` 域）；
- 表结构变更入口（走 Flyway 流程）；
- 面向业务用户的「数据字典浏览」（如需，另立需求并单独评估脱敏）。

后续待定：

- [ ] 注册表落哪个包、是否复用 `SettingRegistry` 的注册风格，需在后端评审确认
- [ ] 只读数据源的连接串来源（复用主库凭据 + 会话级只读，还是独立账号）需与运维确认
- [ ] 审计保留期与合规要求对齐后再决定是否新增审计表
- [ ] 可浏览表清单与脱敏规则需同步到运维文档，避免文档与注册表漂移
