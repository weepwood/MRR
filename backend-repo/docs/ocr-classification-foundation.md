# OCR 与智能分类实施基线

## 当前阶段

本阶段只建立 OCR 与智能分类的安全边界和持久化基础，不会启动历史全量 OCR，也不会自动修改 `mr_scan.btype`。

已完成：

- 后端统一病案影像类型目录，完整覆盖 `0-15`；
- OCR、分类读取、运行、审核、批量审核和覆盖的独立权限代码；
- 单张图片 OCR 事实表 `mr_scan_ocr`；
- OCR 词库和分类规则的草稿、发布、归档版本表；
- 单病案智能分类任务、任务项目、分类建议和类型变更审计表；
- 同一病案只允许一个活动分类任务的数据库约束；
- OCR 设置页面和服务端安全范围校验；
- OCR 默认关闭，批量确认阈值服务端最低为 `0.90`。

## 配置原则

设置页中的 `ocrProfile` 只是服务端白名单配置名称，例如：

```text
ocrProfile=tesseract-local
```

它不是可执行程序路径，也不是命令行。后续 OCR 适配器只能从服务端部署配置读取实际程序和固定参数，禁止从数据库或前端接收任意命令。

第一阶段默认值：

```text
ocrEnabled=false
ocrLanguages=chi_sim+eng
ocrMaxConcurrency=1
ocrPageTimeoutSeconds=30
ocrMaxOutputBytes=4194304
ocrAutoProcessNewScans=false
ocrLowConfidenceThreshold=0.70
classificationBatchReviewThreshold=0.92
```

## 数据边界

### OCR 事实

`mr_scan_ocr` 保存每张图片的当前 OCR 结果。分类任务和后续病案内搜索必须复用该表，不得分别重复识别同一图片。

源文件发生变化时，根据 `content_hash` 将旧结果标记为 `STALE`，重新识别不得静默覆盖无法追踪的人工审核结果。

### 分类建议

`mr_image_classification_suggestion` 与正式类型分开保存。建议只有通过审核接口后，才允许在同一事务中更新 `mr_scan.btype` 并写入 `mr_image_type_audit`。

### 任务

`mr_smart_classification_job` 只按单份病案执行。数据库部分唯一索引禁止同一病案同时存在多个 `PENDING/RUNNING/CANCEL_REQUESTED` 任务。

## 后续实施顺序

1. 本地 OCR 白名单适配器、受限进程执行和持久化 OCR 任务；
2. OCR 词库与分类规则管理 API、规则测试台和发布流程；
3. 智能分类任务执行、服务重启恢复和取消；
4. 单张审核、人工覆盖、高置信度批量确认和事务审计；
5. 影像档案袋“智能 OCR 分类”按钮、任务进度抽屉与审核界面。

## 验收重点

- OCR 未启用或未选择白名单配置时不能创建任务；
- 只读用户不能运行 OCR 或修改类型；
- 类型 `0`、`11`、`15` 均被正确处理；
- 同病案并发创建任务被数据库约束拒绝；
- 分类建议不会自动覆盖正式类型；
- 批量确认阈值无法由客户端降低到服务端安全下限以下；
- OCR 文本、患者信息和完整搜索词不会写入普通应用日志。
