# OCR Windows 离线交付与运行

关联 #342；P1。这是开发验证版本的交付步骤，目标 Windows Server 与真实病案验收尚未完成。
生产运行继续使用同机 Spring Boot 编排 Python 子进程；不安装 Docker、Python HTTP 服务或云 OCR。

## 准备不可变运行时

在可信 Windows 构建机准备独立 Python 环境，使用 ocr-runtime/requirements-validated.lock 固定依赖。
打包器会在复制运行时前核对锁文件中每个包的实际安装版本；不一致时拒绝打包。
当前开发冒烟基线是 CPython 3.12.14、RapidOCR 3.9.2、ONNX Runtime 1.23.2 CPU。
构建环境所需 wheel、Python 安装包及模型必须提前审查来源、许可与 SHA-256，保留供应链记录。
本仓库不随 Git 分发模型及 Python 二进制，也不在生产服务器运行 pip 或下载默认模型。

将已取得的中文 PP-OCRv5 mobile 检测/识别模型及配套方向模型转为固定目录。以下路径均为示例：

```powershell
& C:\build\ocr-env\Scripts\python.exe -X utf8 backend-repo\ocr-runtime\prepare_models.py `
  --det C:\build\models\ch_PP-OCRv5_det_mobile.onnx `
  --cls C:\build\models\ch_ppocr_mobile_v2.0_cls_mobile.onnx `
  --rec C:\build\models\ch_PP-OCRv5_rec_mobile.onnx --output C:\build\pinned-models

.\deploy\windows\build-ocr-runtime.ps1 `
  -PythonHome C:\build\Python312 -EnvironmentPython C:\build\ocr-env\Scripts\python.exe `
  -SitePackages C:\build\ocr-env\Lib\site-packages -Models C:\build\pinned-models `
  -Output C:\build\release\ocr-preview -Version 0.8.0-ocr-preview
```

输出目录和同名 ZIP 必须不存在。构建器复制标准库、固定依赖、worker、模型、评测工具和 doctor，
生成逐文件 SHA-256 清单，并用复制后的解释器运行断网识别冒烟。不会复制 RapidOCR 的默认模型。
成功后记录 ZIP 的 Get-FileHash -Algorithm SHA256；通过可信发布渠道传递并在目标服务器比对。
清单用于检测文件变更，不能替代可信渠道和发布来源校验。

## 服务器检查和配置

将包解压到新的 C:\MRR\ocr\releases\版本目录；管理员可写、后端服务账号只读。
临时目录 C:\MRR\data\ocr-temp 仅允许后端服务账号和管理员读写，避免患者图像暴露。
使用 Windows PowerShell 5.1 运行：

```powershell
.\deploy\windows\ocr-doctor.ps1 -Bundle C:\MRR\ocr\releases\版本目录
```

必须返回 OFFLINE_SMOKE_PASSED，且启动过程不需要外网。失败时保持 OCR 关闭。
复制 deploy/windows/templates/ocr-runtime.template.properties 到服务已有外置配置体系中，
按实际不可变目录配置白名单 profile；manifest-sha256 使用 doctor 返回的 modelManifestSha256。
修改路径需要服务端配置权限，不提供给网页用户。脚本不是 Windows 独立服务，交由后端监督。

设置页 ocrProfile 填 rapidocr-cpu；按需启用 ocrEnabled。默认一个进程、两推理线程、单页30秒、
输出4MB、输入50MB、解码40MP；存储读取也有30秒上限，阻塞读取线程最多8个。
源读取超时不会继续占用业务请求线程，后台遗留读取在完成后删除临时文件；24小时过期文件有界清理。
OCR 正文须额外授权 ocr:text-read；运行、批次、规则、人工审核和自动授权使用各自权限。

## 历史处理与自动归类

先执行 backend-repo/scripts/ocr-preflight.sql，在院内保存只读数据质量统计，完成 #142 归属基线核实。
自动队列仍逐页复核 archive_id 与病案编码；不一致的页面隔离，不猜测病案归属。
历史任务还须配置正常调阅的 image-p95-baseline-millis，且运行时能取得资源与延迟样本。
没有基线、没有观测值、资源不足或不在22:00–06:00窗口时保持等待；单病案交互任务不受夜间限制。
低流量导致延迟分位数窗口过期也会暂停历史领取，应在试点中验证监测方案并由运维确认基线。

先以仅建议模式运行1000页，再10000页，观察CPU、内存、磁盘和调阅延迟；不直接创建无界全量任务。
类别自动写入须依照 [自动归类验收说明](ocr-auto-apply.md) 完成独立样本验收、固定报告校验值、
运维允许类别、设置总开关及真实用户的批次授权。默认所有自动写入开关关闭、类别集合为空。

## 关闭与回退

先关闭 classificationAutoApplyEnabled 和服务端 auto-apply-allowed，暂停或取消历史批次。
关闭 ocrEnabled 会停止后续识别并终止受监督进程；保留文字、建议和审计。
更新模型/脚本时使用新目录，停止任务并重启后端后再切换 profile，禁止原目录覆盖导致混合版本。
回到旧模型和规则仅影响后续任务；旧结果与任务配置快照保留。
正式类型的数据回退须使用批次回退预览，只撤销仍是该批次最后一次自动变更的页面。
应用回退前停止后端，保留新增数据库结构，不删除表、不修改已发布 Flyway 迁移。

上线仍需目标 Windows Server、实际 NAS/Nginx/OSS、断网、Chrome86、资源压测及独立规格/代码审查。
