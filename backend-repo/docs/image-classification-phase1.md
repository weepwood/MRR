# 影像档案袋智能分类第一阶段

第一阶段采用 **OCR 标题识别 + 可解释关键词规则 + 人工确认**。识别结果保存在独立表中，不会自动覆盖 `mr_scan.btype`。

## 1. 功能范围

- 按一份病案异步识别图片；
- 支持“未分类、低置信度、全部”三种识别范围；
- 本地图片读取失败时，自动使用 `oss_url` 生成签名地址并下载临时副本；
- 保存建议类型、置信度、OCR 标题、规则版本和错误信息；
- 单张采用建议；
- 批量采用置信度不低于 92% 的建议；
- 手工修改和 AI 建议确认均写入审计表；
- 模型不会自动改写正式类型。

## 2. 数据库迁移

启动后端时，Flyway 会执行：

```text
V20260716223000__archive_image_classification_phase1.sql
```

新增表：

```text
app.mr_record_type_dict
app.mr_classification_job
app.mr_image_classification
app.mr_image_type_audit
```

检查迁移：

```sql
SELECT installed_rank, version, description, success
FROM app.flyway_schema_history
WHERE version = '20260716223000';

SELECT COUNT(*) FROM app.mr_record_type_dict;
```

## 3. OCR 命令约定

后端不绑定具体 OCR 厂商。配置的 OCR 程序必须满足：

1. 接收图片文件绝对路径；
2. 将 UTF-8 识别文本写入标准输出；
3. 成功时退出码为 `0`；
4. 失败时返回非零退出码，并把错误写入标准输出或标准错误。

参数中的 `{image}` 会被替换为后端创建的临时图片路径。

## 4. Windows + Tesseract 示例

安装中文语言包后，在 `application.properties` 同目录的外部配置文件中设置：

```properties
classification.enabled=true
classification.worker-threads=1
classification.batch-size=100
classification.timeout-seconds=60
classification.high-confidence=0.92
classification.temp-directory=D:/MRR_Service/temp/ocr
classification.ocr.executable=C:/Program Files/Tesseract-OCR/tesseract.exe
classification.ocr.arguments={image},stdout,-l,chi_sim+eng,--psm,6
```

也可以使用环境变量：

```powershell
$env:CLASSIFICATION_ENABLED = "true"
$env:CLASSIFICATION_OCR_EXECUTABLE = "C:\Program Files\Tesseract-OCR\tesseract.exe"
$env:CLASSIFICATION_OCR_ARGUMENTS = "{image},stdout,-l,chi_sim+eng,--psm,6"
$env:CLASSIFICATION_TEMP_DIRECTORY = "D:\MRR_Service\temp\ocr"

java -jar imgapi.jar --spring.config.additional-location=file:./application.properties
```

中文病案标题识别效果不足时，可以将 `classification.ocr.executable` 替换为医院自建的 PaddleOCR 包装程序；只要遵循相同的标准输出约定，后端代码不需要修改。

## 5. 前端操作

打开影像档案袋后，右下角工具栏新增“智能分类”：

- **识别未分类**：只处理 `btype` 为空或 `0` 的图片；
- **重试低置信度**：处理无匹配、失败或低于阈值的结果；
- **重新识别全部**：重新处理该病案全部有效图片；
- **采用当前建议**：将当前建议写入正式 `btype`；
- **批量采用 ≥92% 建议**：只确认当前病案的高置信度建议。

任务完成后，档案袋会重新拉取图片元数据，但原始图片文件不会被修改。

## 6. API

```http
GET  /api/v1/image-classification/types
POST /api/v1/image-classification/archives/{archiveId}/jobs
GET  /api/v1/image-classification/jobs/{jobId}
POST /api/v1/image-classification/jobs/{jobId}/cancel
GET  /api/v1/image-classification/archives/{archiveId}/results
PUT  /api/v1/image-classification/scans/{scanId}/confirm
POST /api/v1/image-classification/archives/{archiveId}/confirm-high-confidence
```

创建任务：

```json
{
  "scope": "UNCLASSIFIED",
  "createdBy": "user-001"
}
```

`scope` 可选值：

```text
UNCLASSIFIED
LOW_CONFIDENCE
ALL
```

## 7. 验收步骤

建议先选一份 20～100 页、标题清晰且类型已人工确认的病案：

1. 备份该病案现有 `btype`；
2. 启用 OCR，并确认 OCR 命令可在命令行独立执行；
3. 在档案袋执行“重新识别全部”；
4. 查看 `mr_image_classification` 中建议和置信度；
5. 对照人工类型计算各类别准确率；
6. 只人工确认建议，不开启任何自动覆盖；
7. 检查 `mr_image_type_audit` 是否完整记录操作。

查询示例：

```sql
SELECT
    c.scan_id,
    s.pages,
    s.btype AS current_btype,
    c.predicted_btype,
    c.confidence,
    c.classification_state,
    c.ocr_title,
    c.error_message
FROM app.mr_image_classification c
JOIN app.mr_scan s ON s.id = c.scan_id
WHERE c.archive_id = :archive_id
ORDER BY s.pages NULLS LAST, s.id;
```

## 8. 当前限制

- 第一阶段没有图像分类神经网络，仅使用 OCR 和关键词规则；
- OCR 质量由配置的本地 OCR 程序决定；
- 类型 `11` 在当前项目中没有明确业务名称，因此未加入自动预测规则；手工类型 `11` 仍可正常使用并进入审计；
- 不自动继承相邻页面类型；该功能留到第二阶段；
- 不批量处理三千万历史图片，第一阶段只按病案创建任务，避免误操作和资源冲击。
