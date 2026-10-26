# 直连造数器：本地与独立测试库快速灌数

## 任务定位

`scripts/seed_dev_data.py` 面向开发与功能演示：把纯合成数据（患者、统计、档案、扫描、影像文件）批量灌入本地或独立 PostgreSQL 测试库，让病案查询、影像档案袋、统计分析、数据质量检查页面有可浏览的数据。风险 P3：新增独立离线工具，不修改生产代码、Flyway、权限或部署配置；单测位于 `scripts/tests/test_seed_dev_data.py`，由质量门禁的脚本测试发现机制执行。

与既有机制的关系：

| 场景 | 用什么 |
| --- | --- |
| 小规模、需要真实业务校验（导入路径测试） | `scripts/generate_test_data.py` 产出的 `test-data/generated/*/imports/*.csv` 走 data-exchange 导入 |
| 批量、快速、离线、直接可浏览 | 本工具直连库造数，复用同一套 `SYNTH` 合成编号规范与 PNG 绘制逻辑 |

不新建第二套编号规则或导入流程：编号、路径规则的单测直接读取 `ArchiveImagePathSupport.java` 与 `MedicalRecordCodeUtils.java` 源码常量做断言，规则漂移会在门禁红灯。

## 落库机制

只写三张表，档案与关联全部复用应用自身的数据库触发器：

```text
app.mr_patient     直接 INSERT
app.mr_statistics  BEFORE INSERT 触发器 trg_mr_statistics_sync_archive 自动创建/同步 app.mr_archive
app.mr_scan        BEFORE INSERT 触发器 trg_mr_scan_resolve_archive 自动解析 archive_id
```

编号分支：前 `--no-sjh-count` 条（默认 2）走无上架号分支（低位唯一编号：`00000001`、`9999999` 探针），其余走高位编号（`10000000` 起）并携带 `SYNTH-S` 上架号。所有编号唯一，保证触发器可唯一解析主档。

## 使用

```powershell
# 只看规模预估与样例，不写任何东西
python scripts/seed_dev_data.py --plan --patients 200

# 只产出 SQL 与影像目录（不连库），images/ 需整体复制到 image.basePath 存储根
python scripts/seed_dev_data.py --emit test-data/generated/my-seed --patients 200

# 生成并经 psql 单事务入库（目标确认必须显式给出）
python scripts/seed_dev_data.py --apply --dsn postgresql://postgres:***@localhost:5432/imgapi-dev `
    --storage-root D:\data\ba-img --confirm-target

# 只产出清理 SQL / 校验 emit 产物
python scripts/seed_dev_data.py --emit-cleanup out-dir
python scripts/seed_dev_data.py --verify test-data/generated/my-seed
```

psql 从 PATH 查找，或用 `--psql-path` 指定；`--no-images` 只造元数据不写影像文件。

## 护栏

- **目标确认**：`--apply` 必须显式 `--confirm-target`；数据库名拒绝 `prod` 字样与维护库（`postgres`/`template0`/`template1`）。
- **单事务**：SQL 自带 `BEGIN/COMMIT`，配合 `psql -v ON_ERROR_STOP=1`，任何失败整体回滚；已写入的影像文件保留，可修正后重跑。
- **规模上限**：患者 ≤ 10,000（不可放宽）、扫描行 ≤ 200,000（`--max-rows` 显式放宽，硬上限 200 万）；影像落盘默认 ≤ 48 张，其余仅元数据。
- **回滚首选**：销毁并重建独立测试库（`DROP SCHEMA app CASCADE` 后重放 Flyway）。`cleanup.sql` 按 `SYNTH-BR`/`SYNTH-C`/`SYNTH-S`/合成姓名标记删除，仅限独立测试库辅助使用；不要在共享库按编号批量删除。
- **可复现**：同 `--seed` 同参数产物逐字节一致；`manifest.json` 不含时间戳，`--verify` 复核 SHA-256。

**不要重复执行同一库的 `seed.sql`**：统计行会成倍重复；无上架号分支每条统计行会再建一条档案，导致部分扫描无法唯一解析主档（进入未关联待排查状态）。需要重跑时先执行 `cleanup.sql` 或重建库。

## 验证证据（2026-10-25）

- 单测 19 项通过；`python -m unittest discover -s scripts/tests -p 'test_*.py'` 全量 149 项通过；`test_inventory.py --fail-on-focused` 与 `release_baseline.py validate` 通过。
- 以开发库 `imgapi` 的 schema-only dump 建一次性冒烟库实跑：20 患者/20 统计 → 触发器自动创建 20 档案（2 条无上架号走 `source_statistics_id` 分支）；120 扫描全部自动解析 `archive_id`（0 条 NULL）；影像文件 MD5 与库内 `checksum_md5` 一致；路径 `24.05/24.05.12/SYNTH-S00000006-10000003/00000006-0001.png` 与库记录精确对应。
- 规模实跑：2,000 患者 × 8 扫描（16,000 行，含逐行触发器解析）6.1 秒完成；复跑行为与上文警告一致；`cleanup.sql` 执行后四表归零；冒烟库已销毁。

## 明确不处理

生产库使用（名字含 `prod` 直接拒绝）、`mr_scan` 千万级压测（属数据质量议题）、用户/权限/密码造数（凭据必须走应用真实流程）、OCR 执行链路、导入校验路径测试（已有合成夹具覆盖）。
