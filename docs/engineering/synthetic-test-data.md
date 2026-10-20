# 合成测试数据：生成、使用与验证

## 任务范围与调查结论

本次需求是提供可复用的数据文件、生成代码及异常场景，支持 MRR 的导入、数据处理、查询、影像和迁移检测。风险 P2：仅新增离线夹具和生成器，不修改生产代码、Flyway、权限或部署配置。**P2 而非 P3 的原因**：`scripts/tests/test_generate_test_data.py` 由 `quality-gate` 执行，且其中表头一致性测试直接读取后端 Java 源码常量，本资产的失败会表现为全仓 PR 门禁红灯。参考测试治理 Issue #244；本工作不代表完成该 Issue 的 PostgreSQL 权限审计验收，也不包含生产数据修复。

调查基线：`origin/main` 的 `1c698081`。按 `ARCHITECTURE.md`、AI 治理规范、后端工程指南、前端工程/设计文档、`vitepress-doc/internal/data-migration.md` 和现有测试核对接口与字段。当前主分支已有 OCR 事实表和规则基础；完整执行流程仍须针对待测版本核查。

已确认调用链：

| 入口 | 已有机制 | 数据影响 |
| --- | --- | --- |
| `DataExchangeController` 的 `/api/v1/data-exchange/{dataset}/import` | `record:edit`、multipart `file`、默认 `dryRun=true` | 使用现有 Service 的 staging/事务，不创建第二套导入流程 |
| `patients` → `PatientImportService` | 独立 CSV/Excel 读取、完整表头、重复指纹、200 条错误展示上限 | `mr_patient`，不是按 `bah` 覆盖旧患者 |
| `statistics` → `StatisticsDataExchangeService` | `TabularImportFileReader`、导入支持工具、临时表、事务锁 | `mr_statistics`、`mr_archive`；有上架号时重复执行会更新 |
| `scan` → `ScanDataExchangeService` | 目录+文件冲突检测、类型范围、关联解析、受控更新 | `mr_scan`、`mr_archive` |
| `archive-boxes` → `ArchiveBoxDataExchangeService` | 必须先唯一解析主档、装箱状态校验 | `mr_archive_box_record` |
| `DataQualityCheckCatalog` | 既有异常检测 SQL；部分扫描检查受最近 ID 窗口和估算影响 | 只提供测试状态配方，不绕过约束制造数据 |
| 内容网关、迁移、导出、外部调阅 | 复用现有 Service、存储 adapter 和测试 | 文件、会话和任务状态都需要真实运行验证 |

编号规范以项目当前实现为准：保留前导零，空上架号不造占位符，高位纯数字病案号 `>=10000000` 必须提供上架号。生成器的 `SYNTH-S...` 是明确的合成上架号，不代表医院编号规则。患者身份证字段为空或 `SYNTH-ID...`，刻意不生成可能与真人重合的合法身份证；因此不构成身份证格式校验用例。

## 文件交付

生成器：`scripts/generate_test_data.py`，Python 3.10+，仅用标准库。测试：`scripts/tests/test_generate_test_data.py`。无需数据库、网络、Docker、Pillow 或 PDF 库即可生成与自测。

本地已生成两套数据，位于 `test-data/generated/`（忽略 Git，避免提交批量二进制文件）：

| 数据集 | 患者/统计/装箱行数，各自 | 扫描行数 | 实际扫描 PNG | 分卷上限 |
| --- | ---: | ---: | ---: | ---: |
| `smoke-v2` | 12 | 48 | 48 | 50,000 行 |
| `scale-1000-v2` | 1,000 | 32,000 | 48 | 10,000 行 |

每套目录：

```text
imports/                 四类可导入 CSV，UTF-8、CRLF、完整表头
cases/                   69 个独立输入文件（含故意错误）
cases.json               每个文件的前置条件、预期或待确认探针
storage/                 与前48条扫描记录路径、大小匹配的 PNG
media/                   空白/旋转 PNG、两页 TIFF、两页 PDF、损坏/零字节 PNG
migration/               旧格式患者和扫描 CSV、字段映射与恢复检查配方
contracts/ocr.json       16种类型及冲突文本的分类契约
contracts/security.json  账号状态、权限、票据、审计和任务隔离契约
expected.json            首次/重复导入数量、汇总和清理预期
manifest.json            参数、来源文件、文件大小与SHA-256
README.txt               简要入口
```

`imports`、`cases`、`migration` 不要混合批量上传。`contracts` 是测试输入契约，不是可导入的数据库表。`expected.json` 是预期，不是后端已执行的测试报告。

## 重新生成与校验

在仓库根目录执行；每次必须使用新的输出目录，不存在覆盖开关：

```powershell
python scripts/generate_test_data.py --output test-data/generated/smoke-new
python scripts/generate_test_data.py --verify test-data/generated/smoke-new
python scripts/generate_test_data.py --output test-data/generated/batch-new --records 1000 --scans-per-record 32 --chunk-rows 10000
python -m unittest discover -s scripts/tests -p test_generate_test_data.py -v
```

参数：`--records` 为 12～10,000,000；`--scans-per-record` 为 1～10,000；`--chunk-rows` 为 1～100,000；`--seed` 默认 20261018。相同参数在受支持的同一 Python/zlib 环境输出字节相同；跨版本压缩结果可能不同，重建后应使用新 manifest。拒绝已存在目录，避免误覆盖。生成中断会保留不完整目录；没有完整 manifest 不能作为已完成产物使用，换新目录重跑即可。

分卷采用流式写入，内存不随扫描行数线性增加。实际文件和 manifest 仍占磁盘；超大规模先估算空间与耗时。例如以下命令会生成 3000 万扫描行，只用于专项基准，**本次没有执行**：

```powershell
python scripts/generate_test_data.py --output test-data/generated/benchmark-30m --records 1000000 --scans-per-record 30 --chunk-rows 50000
```

生成器最多实体化48张扫描图，剩余扫描只是元数据，manifest 显式计数。批量集不能用来断言所有源文件存在，也不能代表 NAS/OSS 的真实吞吐或磁盘容量。模拟大文件元数据不等于大文件 IO 测试。单卷也必须核对实际字节大小；行数分卷不能替代接口文件大小限制。

## 导入与重复执行

1. 使用独立、可销毁的 PostgreSQL 16 数据库；通过项目现有 Flyway 初始化，不修改历史迁移。
2. 用现有认证流程登录，使用具有 `record:edit` 的临时测试账号；口令、Token 和票据运行时生成，不放入数据文件。
3. 顺序导入 `patients` → `statistics` → `scan` → `archive-boxes`，每类按分卷文件名升序执行。每卷先 `dryRun=true`，确认通过后再导入。
4. 核对每次业务响应的 `canImport`、`errorRows` 等字段。HTTP 200 本身不证明导入成功；Controller 也可能包装业务失败。
5. 首次导入、重复导入都对照 `expected.json`；其中数量是所有分卷的合计。
6. 读取影像时，将独立测试环境的存储根目录配置到本数据集的 `storage/`。实际相对路径对齐 `ArchiveImagePathSupport.legacyRelativePath`：`folder前5位/folder/(低位brxh或高位sjh)-bah/filename`；folder是单一日期目录段，文件名含记录序号，满足导入器的目录+文件唯一性要求。不向浏览器暴露存储根路径。

接口映射（均为 POST，multipart 参数 `file`，query 参数 `dryRun`）：

| CSV 前缀 | 接口 |
| --- | --- |
| `patients` | `/api/v1/data-exchange/patients/import` |
| `statistics` | `/api/v1/data-exchange/statistics/import` |
| `scan` | `/api/v1/data-exchange/scan/import` |
| `archive-boxes` | `/api/v1/data-exchange/archive-boxes/import` |

默认冒烟基线应有12条患者、12条统计、48条扫描、12条装箱记录；统计页数总和48。再次导入：患者重复12、扫描重复48、统计更新12、装箱更新12，均不新增。`dryRun` 可以访问 staging 和数据库，不是“完全无数据库操作”；应验证它不改变正式业务表。

**重复导入的“重复”与“更新”如何划分**（核对 `expected.json` 时必须同时读两个字段）：

- 患者按八字段全等判重（`PatientImportService.PatientRow.fingerprint()` 与入库 SQL 的 `is not distinct from` 逐字段比较 `bah/name/idcard/ruyuan/admissiontime/department/bingqu/chuangwei`）。因此两条 `bah` 相同但入院日期或科室不同的记录**不是重复**，首次导入都会入库：`first_import.patients.insertedRows` 包含它们。
- 扫描按 `folder`+`filename` 定位已存在记录，再以八字段（含 `archive_id`）全等判定重复；`updatedRows = existingPaths - databaseDuplicates`。**若两轮之间 `archive_id` 解析结果不一致，同一批数据会从 `duplicateRows` 落到 `updatedRows`**。核对时先确认两轮 `archive_id` 一致，再判断 `duplicateRows` 是否符合预期，否则会把正常降级误报为缺陷。

两份 `bah=10000000` 的合成患者对应两个不同上架号，是特意保留的查询歧义样本。按 `bah` 单独调阅不得随意选一份；提供组合编号才能精确定位。`PATIENT_ARCHIVE_AMBIGUOUS` 在空库仅导入本基线的前提下预计涉及2条患者；需在真实 PostgreSQL 执行确认，不能把有意歧义误判为生成器关联损坏。

## 各功能使用矩阵

| 功能 | 输入与触发 | 需要核对的问题 |
| --- | --- | --- |
| CSV导入 | 四类模板、BOM、GB18030、引号逗号换行 | 按表头映射、中文完整、原值无截断，重复行计数正确 |
| 输入拒绝 | 缺列、重列、未知列、科学计数法、超长、负数、整数/长整型溢出 | `canImport=false`、错误行/字段定位、整批不落库 |
| 错误报告 | `patients-error-cap.csv` 的205条坏行 | 总错误205、只展示200、`errorsTruncated=true`、敏感字段脱敏 |
| 查询/分页 | 低位/高位/前导零编号、相同病案号不同上架号、批量分卷 | 精确命中、歧义拒绝、空条件保护、排序稳定、末页和越界页 |
| 患者分析 | 缺失合成身份证、相同合成身份多病案、三个合成科室 | 缺失数量、分组、日期范围、统计口径与分页总量 |
| 影像类型 | 扫描类型0～15，另有16越界样本 | 字典一致；已分类不被自动覆盖；人工变更身份/权限/审计可信 |
| 内容网关 | 实际PNG、零字节、损坏文件、缺失路径、穿越探针 | 拒绝越界/未授权、异常受控、Range边界、错误不泄露底层路径 |
| ZIP/PDF/打印 | 48张实际扫描图、双页PDF、多页TIFF | 顺序、页数、可解码、配额、取消、到期、临时文件清理；跨用户隔离 |
| 数据迁移/清洗 | `migration/legacy-*.csv` | 历史字段映射、不补零、高位缺上架号拒绝、冲突隔离、逐卷核对 |
| OSS/NAS/Nginx迁移 | `storage-cases.json` 的路径/大小/MD5/故障注入项 | 文件缺失、目标失败、超时、取消、校验失败不标记verified、不删源 |
| 中断恢复 | 第2批提交前中断、提交后响应丢失 | 当前批回滚；已提交批保留；重跑不重复、不漏行；游标可恢复 |
| 数据质量 | 有意歧义基线、`migration/expectations.json` 的状态配方 | 检测缺关联/错关联/成功无URL/未校验等；受外键限制时断言拒绝，不禁用约束 |
| 登录/权限/审计 | `contracts/security.json` | 无Token 401、账号状态拒绝、无权限无副作用、服务端审计身份、敏感内容禁公共缓存 |
| 并发导入 | 两个会话同时提交同一 `bah`、不同 `sjh` 的分卷 | 四个导入服务各持 `pg_advisory_xact_lock(hashtext('mrr.<dataset>.import'))` 全局串行锁；应验证后到者等待而非报 500，且不产生重复主档或半批数据 |
| HIS外部调阅 | 运行时生成有效/过期/篡改票据，复用同一合成病案 | 失效票据、接口范围、IP边界、只读、审计 |
| OCR | `contracts/ocr.json`、空白/旋转图 | 文本/建议/正式类型分离，冲突不静默确认；基础英文扫描图不用于中文OCR准确率评估 |
| 运维/部署 | manifest完整性、独立测试库/目录清理、已有运维测试 | 清理不越界；PowerShell 5.1、Chrome86、真实存储仍需对应环境验证 |

异常文件必须**逐个在独立前置状态中执行**，不能依赖上一个异常文件的落库结果。装箱样本先建立匹配主档；低位无上架号的装箱样本须先建立相应低位统计。`cases.json` 中 `expected.probe` 表示需要观察的行为，不是肯定通过/拒绝断言。

注意 probe 条目可能同时携带 `canImport` 等硬编码值（例如 `patients-missing-name` 的 `canImport=true`，其说明为“若业务要求必填，另行确认需求”）。这些值只是编写时对照当时实现的**观测提示**，不构成验收断言；接口未实跑前，`canImport` 与 `probe` 冲突时以 `probe` 为准，并将实测结果回填。部分条目的 `note` 为复制模板残留（如患者用例提到“装箱须先导入基线统计”），执行时按 `dataset` 与 `setup` 判断，不要按 `note` 排序。坏行应检查失败后的业务表无变化；成功用例可再导出重导入检查往返一致性。

所有生成的 CSV 表头与当前四个导入 Service 通过自动测试核对，避免文档字段与代码漂移；存储路径测试同时覆盖低位brxh和高位sjh分支。Excel `.xls/.xlsx` 的日期序列、公式缓存、数字精度和多工作表差异仍需专门的原生工作簿夹具；本次CSV不能替代这些文件格式测试。也没有把账号、审计、OCR任务表直接灌库，以免绕过生产服务。

## 已识别的探针与未知项

- 日期（**结论随基线变化，勿沿用旧说法**）：本资产最初基于 `1c698081` 编写，当时 `DataExchangeImportSupport.DATE_FORMATTERS` 为 `yyyy-M-d`（SMART 解析），非闰年 `2025-02-29` 会被调整到月末。`origin/main` 的 #361（`7756c98d`）已改为 `uuuu-M-d` + `ResolverStyle.STRICT`（`PatientImportService` 的 `DATE_FORMATTERS` 同步改动），同一输入应被判为日期非法并拒绝，不再到月末。`statistics-calendar-probe.csv` 保留该输入，但**预期取决于被测基线的版本**：`7756c98d` 之后应断言拒绝；断言“调整到月末”只对旧基线成立，已失效。登记业务缺陷前必须先确认被测 jar 的提交。
- 列数：`toValueMap` 只遍历表头列，额外单元格可能被忽略。四个 `*-extra-cell-probe.csv` 检查是否发生静默丢数；不要仅测试“接口不报错”。
- 隐形字符：`patients-invisible-code.csv` 检查零宽字符是否导致找不到或错误关联。不要在生成阶段先清掉，否则无法发现清洗问题。
- 路径：当前导入校验非空目录/文件名，不代表内容网关会放行穿越。必须分开记录“导入”和“读取”结果。
- 大表：质量检测有最近ID窗口和统计估算。低ID合成数据未出现在检测样本中，不等于质量规则正确或错误；应按现有集成测试控制窗口，不修改生产主键序列来凑结果。

真实生产唯一性规则、真实医院编号格式、NAS权限、OSS实现差异、浏览器兼容及OCR引擎识别率均未通过合成数据自动确认。不能用本夹具声称已经完成全功能或三千万级性能验收。

## 验证记录与完成边界（2026-10-18）

实际执行：

```text
python -m unittest discover -s scripts/tests -p test_generate_test_data.py -v
  10 tests，全部通过
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
  88 tests，全部通过
python scripts/generate_test_data.py --verify test-data/generated/smoke-v2
  ok=true，errors=[]
python scripts/generate_test_data.py --verify test-data/generated/scale-1000-v2
  ok=true，errors=[]
python scripts/test_inventory.py --fail-on-focused
  未发现 .only 或 @Disabled
python scripts/release_baseline.py validate
  MRR release baseline valid: v0.8.0
```

环境实际使用 Codex 自带 Python 3.12.14；仓库脚本和测试本身只依赖标准库。测试覆盖：生成确定性、关联/页数/16种类型、分卷、拒绝覆盖和非法参数、manifest损坏/穿越、异常场景清单、媒体结构及真实Service表头一致性。先运行缺失生成器的失败测试，再实现并使测试通过。

### 独立复审补测（2026-10-20）

复审在 Python 3.13 环境重跑并补强，结果与上述一致（生成器 10 条、`scripts/tests` 全量 88 条、`--verify` 两套均 `ok=true`）。改动仅限测试与本文，生成器与已生成数据未变：

1. 表头一致性测试改为显式断言"来源文件存在"和"正则命中常量"，并输出可读失败信息。原先 `re.search(...).group(1)` 在常量声明被重构时抛 `AttributeError`，失败信息无法指向真实原因，且该测试在 `quality-gate` 中执行，容易演变为难排查的门禁红灯。
2. 新增"实体化扫描图缺失"用例：删除 `storage/` 下一张 PNG 后 `verify` 必须返回带 `storage` 定位的错误。此前只覆盖内容篡改与 manifest 穿越，未覆盖中断生成留下的"目录在、文件不全"状态。
3. 新增参数下边界用例：`--records 12 --scans-per-record 1 --chunk-rows 1` 可生成、分卷数为 12、`verify` 通过。此前只测了 `0` 与 `10000001` 两个越界值，未验证文档声明的下边界。

此外用独立 Pillow 解码48张扫描PNG、空白/旋转PNG和TIFF，用 pypdf 确认PDF有2页且可提取合成标识；Poppler渲染PDF并人工视检，无截断或重叠（Poppler报告本机缺少两个未使用字体的告警，渲染退出0且页面可读）。这些独立库只用于本次验证，不是生成器依赖。

未执行：后端 `mvn verify`、PostgreSQL16导入/事务/查询/迁移、Chrome86、真实NAS/OSS、全量负载、原生Excel格式。当前未发现可用Java/Maven/PostgreSQL命令；不访问现有数据库，也未对应用调用导入接口。因此这里交付的是经过格式与生成器验证的测试资产，业务预期仍须执行矩阵验证。合并前仍需按AGENTS进行独立规格/代码审查及适用集成验证；本次自测不代替独立审查。

## 范围控制与回滚

最小改动范围：生成器、生成器测试、本文、测试数据入口及忽略规则。可作为一个独立的测试资产提交，提交说明可用“测试：增加可复现的合成病案数据与异常场景”。生成文件不入版本库；没有修改主分支、业务API、数据表、历史迁移或配置。相邻业务缺陷仅保留探针，未顺手修复。

源代码回滚为撤销该独立提交。生成物清理只删除明确的本次生成目录；不提供递归删除自动化。执行导入后，通过销毁专用测试数据库恢复；共享库不应使用这些数据，尤其不能按重复的`bah`删除。若需要在真实数据库中修复/迁移数据，另建P1变更提案，补充只读统计、批处理、审计、恢复和独立审查。

本地旧版`smoke/`、`scale-1000/`仅为中间产物，已从 `test-data/generated/` 移出到系统临时目录 `%TEMP%\mrr-generated-deprecated-20261020\`（保留以便回溯，不占用仓库空间，可随时删除）。只使用`*-v2`或新生成的数据，交付ZIP排除旧版。

## 合并前置条件（尚未满足）

本节记录独立代码审查的结论，供后续接续者判断当前交付边界。以下四项未完成前不应把本资产视为已合并就绪：

1. **变更尚未提交**。生成器、生成器测试、本文、`test-data/README.md` 与 `test-data/.gitignore` 仍是未跟踪文件，没有 diff、提交说明和 CI 结果；“先写失败测试再实现”的过程因此无法被第三方核验。下一步是作为一个独立提交并通过 PR 走 `governance-gate`。
2. **分支基线落后**。审查时工作分支为 `1c698081`，`origin/main` 已前进 104 个提交。合并前须同步并**重跑“已识别的探针与未知项”全部条目**，特别是日期解析一条：`7756c98d` 已把日期格式化器改为 STRICT，旧结论失效。
3. **生成器实现层未逐行审查，已用黑盒探测缩小范围**。审查时该文件读取受安全策略限制，故改为行为验证，2026-10-20 实测：
   - 已存在目录、已存在普通文件：退出码 `2`，报 `[WinError 183]`，不覆盖任何内容。拒绝依赖 `mkdir` 的排他语义而不是“先检查后创建”，因此没有 TOCTOU 窗口。
   - `--output ../escape-root`（相对逃逸）与 `--output <绝对路径>`：均正常生成并写入指定位置；多级不存在的父目录会自动创建。这是本地命令行工具的预期行为，输出路径由操作者显式给出，没有不可信输入来源；但反过来也说明**生成器不提供“只能写进仓库”的护栏**，路径传错会直接落到任意位置。
   - 拒绝时错误信息直接输出底层 `WinError` 与绝对路径，对操作者可接受，不适合转发给最终用户。
   - 仍未确认：`manifest.json` 写入是否原子（中断后是否可能留下“结构完整但内容错误”的 manifest）、指向符号链接目录时是否照样写入、`--records` 取极大值时的临时文件与内存曲线。
4. **无集成证据**。后端 `mvn verify`、PostgreSQL 16 导入/事务/查询、Chrome 86、真实 NAS/OSS、原生 Excel 格式均未执行；`expected.json` 与 `cases.json` 的数值是设计预期，不是已执行的测试报告。

