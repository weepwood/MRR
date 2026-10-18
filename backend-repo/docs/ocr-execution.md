# OCR 执行与文字查看（阶段二）

关联 #342；P1。引擎、持久化任务和文字查看默认关闭，当前阶段不修改正式分类。

## 配置

由运维在服务端配置 ocr.runtime.profiles.<名称>.executable、script、model-directory、
manifest-sha256；设置页 ocrProfile 只能引用该名称。executable 指向离线 Python，script
指向发布目录的 ocr-runtime/worker.py。不得把路径配置进数据库或 API 请求。

模型目录 manifest.json 的结构为：

```json
{
  "model": "PP-OCRv5-mobile",
  "preprocessing": "rgb-exif-v1",
  "versions": {"rapidocr": "已验证固定版本", "onnxruntime": "已验证固定版本", "Pillow": "已验证固定版本"},
  "files": {
    "det": {"name": "det.onnx", "sha256": "文件的64位哈希"},
    "cls": {"name": "cls.onnx", "sha256": "文件的64位哈希"},
    "rec": {"name": "rec.onnx", "sha256": "文件的64位哈希"},
    "keys": {"name": "keys.txt", "sha256": "文件的64位哈希"}
  }
}
```

这是结构说明，不能直接用于启动。发布前必须生成真实版本与哈希，不允许自动下载补齐。
模型、脚本、运行时目录仅允许运维写入；临时目录仅允许后端服务账号和管理员访问。
JSONL stdout 不记录原文；stderr 丢弃内容，只统计大小。运行时 socket 出站禁用。

## 接口

创建使用 POST /api/v1/archive-records/{id}/ocr/jobs，body 为 idempotencyKey 和可选 scanId。
只选归属校验通过、有效、btype=0 的影像；超过 10000 页拒绝。数据库队列上限 100。
同病案只能有一个活动任务。GET /api/v1/ocr/jobs/{id}/items 使用 after/limit 游标，最多 50。
GET /api/v1/scan/{id}/ocr 默认只返回状态；full=true 额外要求 ocr:text-read。
任务查询和取消只允许创建人或管理员；所有请求重新检查有效账号和实时权限。

## 验证证据

2026-10-18：OcrAccessTest/OcrProtocolTest 6 项通过；OcrExecutionPostgresqlIT
在 PostgreSQL 16 Testcontainer 上 4 项通过，Flyway 完成 35 条迁移。
worker.py 2 项边界测试通过；前端轮询过期响应与终止测试通过，类型检查通过。
未将真实病案交给模型；真实模型质量和 Windows Server/NAS 故障验证仍待部署验收。

## 回滚

关闭 ocrEnabled，停止领取并取消当前推理；保留结果、历史和任务记录。
本阶段可独立回退应用；新增数据库结构保留，不修改旧迁移。
