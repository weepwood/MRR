# 数据表导入教程

本节整理 MRR 业务数据的逐表 CSV 导入方案。所有脚本适用于 PostgreSQL 16、业务 Schema 为 `app` 的当前迁移链。

## 导入顺序

1. [`mr_patient` 患者数据](./mr-patient.md)
2. [`mr_statistics` 统计数据](./mr-statistics.md)
3. [`mr_archive_box_record` 装箱数据](./mr-archive-box-record.md)
4. [`mr_scan` 扫描影像数据](./mr-scan.md)

先导入 `mr_statistics` 可以建立 `mr_archive` 病案主档，随后装箱记录和扫描影像才能更高效、准确地解析 `archive_id`。

## CSV 表头

### `mr_patient`

```csv
brxh,id,bah,name,idcard,ruyuan,admissiontime,department,chuangwei,bingqu,keshicode,bingqucode
```

### `mr_statistics`

```csv
bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate,brxh
```

### `mr_archive_box_record`

```csv
bah,sjh,box_no,expected_box_no,status,remark
```

### `mr_scan`

完整旧表迁移格式：

```csv
id,brxh,bah,filename,btype,pages,openerno,uploaddate,uploadflag,folder,filesize,sjh,oss_url,checksum_md5,migration_status,migrated_at
```

简化格式：

```csv
sjh,bah,brxh,folder,filename,btype,filesize
```

简化格式没有稳定源 `id`，不适合 3000 万行级别的可恢复迁移。

## 统一规则

- CSV 使用 UTF-8 和英文逗号。
- `bah`、`sjh`、`brxh` 按文本处理，只去除首尾空格，不补零。
- 空上架号写为 `NULL`，不生成占位号。
- 病案号达到 `10000000` 时，缺少 `sjh` 不得自动关联。
- 不直接导入 `mr_archive`；由统计导入、解析函数和触发器维护。
- `id`、`archive_id` 和自动时间列除迁移教程明确要求外，不放入 CSV。
- 生产执行前必须备份，并在数据库副本完成演练。

通用原理、错误处理和上线检查见[数据导入与迁移总览](../data-migration.md)。

## 数据交换中心的文件校验

页面导入患者、统计、装箱和小批量扫描时，统一支持 CSV、XLS、XLSX，先校验再确认写入。
不存在的日历日期和入院时间 24:00 会被拒绝；CSV 引号错误或表头外多余列、
Excel 表头外非空单元格也会拒绝，不会静默丢弃内容。错误提示中的 CSV 行号按逻辑记录计算，
引号内换行不增加记录号；空记录不计入数据行数上限。

导出 CSV 为防止 Excel/WPS 公式执行、前导零丢失和长编号精度损失，可能带有单引号文本保护前缀。
通过数据交换中心回导会还原系统保护；不要将此类导出文件未经转换直接交给裸 `COPY` 入库。
已有手工单引号的外部 CSV 应先在副本校验，避免与系统保护约定混淆。

## 执行方式

教程中的 `\copy` 是 `psql` 元命令，应使用：

```powershell
psql -h 127.0.0.1 -p 5432 -U postgres -d mrr-app `
  -v ON_ERROR_STOP=1 `
  -f D:/MRR-Scripts/import.sql
```

不要把 `\copy` 直接粘贴到 pgAdmin Query Tool。Windows 路径建议写成 `D:/MRR-Data/file.csv`，并让 `\copy` 独占一行。
