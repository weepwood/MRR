# 数据交换与数据治理实施方案

关联：[变更提案 #360](https://github.com/weepwood/MRR/issues/360)、#90、#142、#165、#166、#167、#171、#192。

## 目标与边界

数据操作按“进入前阻止错误、进入后量化质量、修复时可追溯”推进。继续使用 Windows Server、
Nginx、Spring Boot 单体和 PostgreSQL 16，不引入额外平台，不提供任意 SQL 或通用数据编辑器。

本轮风险为 **P1**，实现阶段一的文件校验与导出回导一致性。后续阶段作为独立 Issue/PR，
不能把本轮通过的测试理解为历史数据已经清理、生产验收已经完成。

## 调查结果

| 入口或机制 | 调用链与数据 | 本轮判断 |
| --- | --- | --- |
| 数据交换中心 | Vue `views/data-exchange` → 统一 `api/modules/data-exchange` → `DataExchangeController` | 复用现有页面和 API |
| 患者导入 | `PatientImportService` → 临时表 → `mr_patient` | 原独立解析器与其他数据分叉；导出文本保护前缀不能还原 |
| 统计、装箱、扫描导入 | 各 `*DataExchangeService` → `TabularImportFileReader` → 临时表、事务和导入互斥锁 | 复用；不再建立第二套导入服务 |
| 病案关联 | `mr_statistics` / `mr_archive_box_record` / `mr_scan` → `mr_archive.id` | 保持现有解析、触发器与幂等规则 |
| 表格导出 | `DataExchangeExportService` → 条件查询 → 有上限 CSV；`no-store` | 复用文本保护和导出接口 |
| 质量检查 | `DataQualityServiceImpl` / `DataQualityCheckCatalog` → 质量任务及样本 | 继续作为检测入口；本轮不改变检查 SQL |
| 影像导出 | 现有 ZIP/PDF 任务、取消、Range、配额与清理 | 独立使用 #171 收口，不与表格导出混建 |

已确认：默认 SMART 日期解析会接受不存在的月末日期；CSV 超出表头的值可能丢弃，
Excel 文本单元格的日期判断可能抛出异常，行数限制在完整文件解析后才判断。
单元测试与 PostgreSQL 16 测试已先复现失败。

推断：如果错误文件进入正式导入，可能污染日期或丢失源字段；没有据此断言生产已经发生。
未知：医院历史数据异常分布、实际源文件格式、3000 万级数据性能和真实存储可用性。
这些需要数据库副本和医院环境证据。

## 分阶段方案

| 阶段 | 方案与价值 | 验收及风险 | 状态 |
| --- | --- | --- | --- |
| 一：输入质量与回导一致性 | 四类数据复用读取器；拒绝畸形文件与不存在日期；导出回导保留文本和编号 | P1；失败测试、四类入口整批拒绝、PostgreSQL 16 回导与幂等 | 本轮实现 |
| 二：关联质量基线 | 复用 #142/#156，统计三张表关联覆盖、缺失、冲突及外键验证状态；异常只读展示 | P1；明确全量/窗口口径，副本 EXPLAIN、耗时和覆盖率证据 | 后续独立实施 |
| 三：受控修复与持续治理 | 复用 #166 的修复工单、白名单动作、预览、审批、版本校验与恢复；#165 管理字典，#167 管理保留和容量 | P1；失败回滚、并发、幂等、审计和恢复演练 | 后续独立实施 |

阶段二的最低交付是“可重复执行的只读基线”，不直接启动全表修复。确认唯一性规则后，
才按主键游标分批回填；每批记录扫描、更新、冲突、未解析数量与断点。冲突保留人工处理，
不能猜测 `archive_id`，不能给 BAH/SJH 补零或生成占位号。外键最终验证独立安排变更窗口。

阶段三先从“唯一可解析且当前值未变化”的关联补齐开始，不一次性实现主档合并等不可逆动作。
每个修复模板必须限定字段、批量阈值、审批策略、前后值记录、恢复条件和复验规则；
没有这些条件的动作只能记录工单，不能执行。

## 本轮实施计划与验收

1. `service/importer/TabularImportFileReader.java`：读取 CSV 时识别未闭合引号、非转义引号、
   闭合引号后的多余字符与表头外列；读取 XLS/XLSX 时拒绝表头外非空值，
   允许仅有格式的空单元格和缺失的尾部空字段。错误仅返回逻辑记录号和原因。
2. 解析过程中按非空数据记录检查上限，空行不计入 100,000 行；保留原文件记录号。
   CSV 引号内换行属于同一记录。CSV 仍受文件大小上限约束，Excel 仍先由 POI 加载工作簿，
   因而这次改进不等于流式处理任意大小工作簿。
3. `PatientImportService.java`：使用现有读取器，删除重复解码与解析实现；
   患者导出 CSV 与其他数据一致，还原系统加入的文本保护前缀。
   保留原事务、临时表、字段规则、去重及患者结果 DTO。
4. `DataExchangeImportSupport.java` 和患者日期解析：使用严格日历规则，
   拒绝非闰年 2 月 29 日、4 月 31 日、入院时间 24:00；
   保留合法闰日、斜杠/点分日期和原先支持的时间格式。
5. 单元测试证明解析错误发生在 JDBC 访问前；PostgreSQL 16 验证患者 CSV 导出回导，
   混合好坏行整批拒绝，以及四类入口在 dry-run 和正式模式均不写入结构错误文件。
6. 独立上下文审查直接读取 Issue、diff、测试和架构规范；记录实际测试结果后再提交 PR。

本轮 API、权限、审计、缓存、表结构、Flyway 和部署方式均不变。不新增状态机、后台任务或依赖。
修改只包含输入质量与回导一致性，能够独立提交和回滚。

## 操作流程

1. 先从数据交换中心下载对应模板。保留原始文件副本，BAH/SJH/身份证等编号使用文本格式。
2. 使用“校验文件”；修正返回的行号与字段错误。结构错误会直接拒绝文件，
   不应通过增加无名列、删除表头或忽略报错来规避。
3. 核对新增、更新和重复数量，再“确认导入”。正式操作仍会重新校验，不依赖前端放行。
4. 导出数据含文本保护前缀，用于 Excel/WPS 避免公式执行和编号精度损失。
   由数据交换中心重新导入时会还原系统保护；不要用裸 `COPY` 把保护前缀直接写入业务表。
5. 对原来已经带有单引号且紧接公式或特殊编号的外部 CSV，应先在副本确认语义。
   现有保护协议无法仅凭文件内容区分人为单引号和系统前缀；本轮不改变该已有约定。
6. 导入后使用现有质量检查和关系工作台复核，不能把文件校验成功当作全库质量合格。
   大规模迁移仍走现有 staging 与分批迁移流程。

## 验证方式

使用本机 Java 21 / Maven；PostgreSQL 16 使用一次性 Testcontainers 测试库，
Docker 只用于测试，正式部署不依赖 Docker。命令在 `backend-repo` 下执行：

```powershell
mvn -B -ntp "-Dtest=TabularImportFileReaderTest,DataExchangeDateValidationTest,DataExchangeSpreadsheetSafetyTest,PatientImportServiceTest,ScanDataExchangeServiceTest" test
mvn -B -ntp "-Dit.test=DataExchangeValidationPostgresqlIT,CoreDataExchangePostgresqlIT,MedicalRecordImportBoundaryPostgresqlIT,StatisticsImportRollbackPostgresqlIT" verify
```

仓库根目录执行：

```powershell
python -m unittest discover -s scripts/tests -p "test_*.py" -v
python scripts/test_inventory.py --fail-on-focused
python scripts/release_baseline.py validate
```

实际结果及审查结论见关联 PR。前端与运维脚本没有代码变更，
不能声称已验证 Chrome 86、Windows Server 或真实 NAS/OSS；这次本机 Windows 测试也不替代医院验收。

### 本轮实际证据（2026-10-18）

- 修改前：新增的 7 项 PostgreSQL 测试全部失败，复现日期误接受、患者回导编号变化和外列丢弃。
- 独立审查发现 Excel 隐藏格式可绕过外列检查；XLS/XLSX 两项回归测试先失败，再修改实现。
- 最终上述 `mvn verify` 成功：830 项后端单元测试、15 项指定 PostgreSQL 16 集成测试，
  失败/错误/跳过均为 0；同时完成打包。没有声称运行全部 PostgreSQL 集成类。
- 79 项治理工具单元测试通过；测试资产无聚焦/禁用标记；发布基线 v0.8.0 验证通过。
- 全量首次执行有 4 项既有 OCR 测试因找不到 Python 而失败；指定已有
  `MRR_TEST_PYTHON` 解释器后通过，未修改或跳过这些测试。复现命令同样需配置本机有效 Python。
- 独立上下文完成规格/代码审查及 P2 修复复核；另行验证 CSV 99,999/100,000 行接受、
  100,001 行拒绝，XLS/XLSX 隐藏数值/公式拒绝、仅格式空单元格允许。
- `git diff --check` 与反向补丁 `git apply --reverse --check` 通过；反向补丁检查未实际撤销代码。

## 回滚与上线检查

上线前在数据库副本试验真实格式的脱敏样本，特别是日期、Excel 午夜时间、引号、
带换行的备注、前导零和中文。确认旧文件没有依赖被忽略的多余列或自动修正的非法日期。

本轮没有数据库迁移。回滚时撤销本 PR 的独立代码提交、重新构建并按现有发布流程替换应用。
回滚后文件校验会恢复旧行为，因此在再次修复前暂停使用问题格式导入。
拒绝的文件不改业务表，修正后可重新上传；成功导入的数据不会随代码回滚而恢复。
若需要撤销成功导入，必须依据导入前备份及差异形成独立恢复方案，不执行按日期批量删除。
历史异常日期不自动修复，应先只读盘点，再通过后续治理工单审批处理。
