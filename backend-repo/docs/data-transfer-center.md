# MRR 数据交换中心

数据交换中心用于通过 CSV 导入和导出 `mr_statistics`、`mr_scan`。前端仅负责创建任务、上传或登记文件、展示进度和下载结果；大批量数据由后端通过 PostgreSQL `COPY`、UNLOGGED 暂存表和集合 SQL 处理。

## 1. 支持范围

| 数据类型 | 规模 | 导入 | 导出 |
|---|---:|---:|---:|
| `mr_statistics` | 约 20 万行 | 单文件或多文件 | 单卷/分卷 CSV.GZ |
| `mr_scan` | 约 3000 万行 | 多文件、文件级恢复 | 按 ID 游标分卷 CSV.GZ |

系统不接受前端传入任意表名或原始 SQL。用户、密码、运行日志和迁移状态表不能通过本功能覆盖。

## 2. 文件目录

默认配置：

```properties
app.data-transfer.base-dir=./data/data-transfer
app.data-transfer.inbox-dir=./data/import-inbox
```

Windows 生产环境建议使用独立数据盘：

```properties
app.data-transfer.base-dir=D:/MRR/data-transfer
app.data-transfer.inbox-dir=D:/MRR/import-inbox
```

目录用途：

```text
D:/MRR/import-inbox/                 运维人员放置超大 CSV
D:/MRR/data-transfer/job-100/input/  任务输入文件
D:/MRR/data-transfer/job-100/output/ 导出分卷
D:/MRR/data-transfer/job-100/errors/ 完整错误报告
```

接口只能访问上述受控目录，不能读取前端提交的任意服务器路径。

## 3. CSV 编码和空值

- 编码：UTF-8，可包含 BOM。
- 格式：标准 CSV，首行必须是表头。
- 空字段：写入 `NULL`。
- 日期：`YYYY-MM-DD` 或 `YYYY/MM/DD`。
- 上架号缺失：字段留空，不能填写 `00000000` 等虚假编号。
- 1～8 位纯数字病案号和上架号会补齐为 8 位。

## 4. 支持的历史 CSV

### 4.1 `mr_statistics`

标准英文表头：

```csv
bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate
```

现有 7 列中文格式可直接导入：

```csv
上架号,病案号,设备ID,操作人,页数,日期,类型
```

扩展中文格式：

```csv
上架号,病案号,设备ID,操作人,页数,日期,类型,姓名,科室,病人ID,出院日期
```

### 4.2 `mr_scan`

标准英文表头：

```csv
brxh,bah,sjh,filename,btype,pages,openerno,uploaddate,uploadflag,folder,file_size
```

现有 7 列中文格式可直接导入：

```csv
上架号,病案号,病人序号,文件夹,文件名,病案类型,文件大小
```

导入器会按表头识别字段顺序，不要求先手工重排历史文件。

## 5. 3000 万行 `mr_scan` 导入

不要在浏览器上传一个包含全部数据的单文件。建议拆分为每个 50 万至 100 万行：

```text
mr-scan-0001.csv
mr-scan-0002.csv
...
mr-scan-0030.csv
```

将文件复制到服务器 inbox，然后在前端“数据交换中心”选择：

```text
数据类型：mr_scan
文件来源：服务器 inbox
重复处理：跳过重复
```

每个文件按以下流程独立提交：

```text
COPY 暂存表
→ 批量规范化和校验
→ 批量创建/解析 mr_archive
→ INSERT SELECT 合并 mr_scan
→ 更新文件进度
```

一个文件失败不会回滚此前完成的文件。重试只处理失败文件。

## 6. 导入顺序

推荐顺序：

1. 先导入 `mr_statistics`，建立较完整的病案主数据；
2. 再导入 `mr_scan`，批量关联 `archive_id`；
3. 下载错误报告，处理无法唯一关联的记录；
4. 检查关联覆盖率和重复数据。

无上架号的扫描记录只有在病案号能够唯一匹配 `mr_archive` 时才会导入。病案号达到 `10000000` 时必须提供上架号。

## 7. 去重规则

### `mr_statistics`

系统根据规范化后的以下字段生成业务指纹：

```text
上架号 + 病案号 + 设备 ID + 日期 + 类型
```

### `mr_scan`

系统根据以下字段生成文件记录键：

```text
文件夹 + 病人序号 + 病案号 + 文件名
```

导入模式：

- `SKIP_DUPLICATES`：已有记录保持不变；
- `UPSERT`：使用本次 CSV 更新已有记录。

历史 `mr_statistics` 会在 Flyway 迁移中建立指纹。历史 `mr_scan` 数据量较大，需在低峰期分批执行：

```powershell
./backend-repo/scripts/backfill-scan-source-keys.ps1 -BatchSize 10000
```

中断后根据脚本最后输出继续：

```powershell
./backend-repo/scripts/backfill-scan-source-keys.ps1 `
  -BatchSize 10000 `
  -StartAfterId 1230000
```

## 8. 暂停、恢复和服务重启

- 暂停会等待当前文件处理结束，再停止领取下一个文件；
- 取消不会删除已经提交的数据；
- 服务重启后，执行中的任务会标记为失败；
- 管理员可以重试失败文件；
- 导出任务从最后完成分卷的 `last_record_id` 继续。

## 9. 导出

导出使用主键游标，不使用大 OFFSET。默认每卷 100 万行：

```text
mr-scan-part-0001.csv.gz
mr-scan-part-0002.csv.gz
...
```

前端任务详情中可以逐卷下载。可以设置起始 ID 和结束 ID，用于增量备份或分段迁移。

## 10. 本地验收

GitHub Actions 额度不足时，在本地执行：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
$env:PGPASSWORD = '数据库密码'

./backend-repo/scripts/verify-data-transfer.ps1
```

检查内容：

- Maven 编译；
- 文件存储单元测试；
- 前端 TypeScript 类型检查；
- 数据交换表、视图、索引和暂存表；
- 暂存表是否为 UNLOGGED；
- 历史去重键回填函数。

## 11. 上线前压测

按以下规模逐级测试：

1. 1 万行：格式和错误报告；
2. 20 万行：完整 `mr_statistics`；
3. 100 万行：单个 `mr_scan` 文件；
4. 1000 万行：暂停、重启和继续；
5. 3000 万行：生产规模演练。

观察 PostgreSQL WAL、磁盘剩余空间、临时表大小、锁等待、连接池和普通查询延迟。生产导入前必须备份数据库。
