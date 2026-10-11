# MRR 项目概览与功能状态

> 审校基线：2026-10-11，main 提交 a987147c05f731dbb6465090b0ca8517f439f725。本文记录仓库和正式 Release 的事实，不代表已在医院生产环境执行验收；后续代码变化须重新核对。

## 项目定位与读者

MRR（Medical Record Repository）是一套运行在医疗机构内网的病案文件与扫描影像管理系统，提供病案检索、预览、下载导出、审计、数据治理和运维。**不是 DICOM 诊断工作站**，不提供医学测量、窗宽窗位和自动诊断结论。

| 角色 | 核心任务 | 推荐阅读 |
| --- | --- | --- |
| 病案工作人员 | 查询、浏览和导出病案文件 | [用户手册](../user-guide/index.md)、[影像档案袋](../user-guide/images.md) |
| 管理员 | 账号、权限、诊断、审计与存储 | [系统管理](../user-guide/admin.md)、[运维诊断](../user-guide/operations-center.md) |
| HIS / EMR 对接方 | 外部系统受控调阅 | [外部接入](./external-archive-integration.md) |
| 部署人员 | 安装、升级、回滚与故障处置 | [部署总览](./deployment.md)、[运行手册](./runbook.md) |
| 开发人员 | 代码模块、迁移与测试 | [系统架构](./architecture.md)、[开发流程](./development.md) |

## 运行架构

```text
浏览器 / HIS / EMR
       │
       ▼
Nginx（Windows 正式部署统一入口）
       │
       ▼
Spring Boot 4.1.0 单体应用（权限、病案、文件流、导出、审计）
       ├── PostgreSQL 16 / app Schema（患者、病案主档、扫描元数据、权限和日志）
       ├── 本地 / NAS / HTTP / Nginx 原始扫描文件
       └── S3 兼容 OSS（已迁移图片）
```

PostgreSQL 中的病案与扫描记录是元数据和关联关系，并不代表所有原图都存储于数据库。同一份病案允许 OSS、Nginx、NAS 和本地文件混合存在，读取由后端按配置解析和回退。

正式部署不需要 Docker、Redis、Kafka 或微服务集群。发布形式包括 Windows 离线包（默认经 Nginx 访问，后端业务端口 18045）及内嵌 Vue 前端的单体 JAR（默认业务端口 8002）。文档独立托管，详情见[部署总览](./deployment.md)。

## 功能状态：正式发布与 main

[正式 v0.8.0](https://github.com/weepwood/MRR/releases/tag/v0.8.0) 于 2026-07-28 发布。它包含：

- 病案、患者、统计、扫描与装箱管理；影像档案袋和全景平铺模式；ZIP/PDF 导出与审计。
- JWT/RBAC、注册审核、外部 HMAC Ticket、运行诊断、Nginx/OSS 图片来源管理。
- aStor EDS 路径式 S3 兼容、PWA 桌面安装与工程测试治理升级。
- **仅 OCR/智能分类第一阶段基础**：权限、设置、数据表、审核与任务约束；默认关闭，不等于已实现端到端自动识别。

当前 main 还有 4 个 v0.8.0 标签之后的提交：扫描数据校验、OSS 条件配置、远程存储错误语义，以及按影像 ID 受控读取的网关。这些**不属于 v0.8.0 Release 包**。

受控内容网关的代码入口为 `GET /api/v1/img/content/{id}`，要求 `record:read`，采用流式响应并设置私有禁缓存头。它是当前 main 的实现，并非不登录即可访问的静态文件路径。

## 不可忽略的约束

- `bah` 与 `sjh` 按原始字符串处理，不自动补零；病案号达到 10000000 时须同时提供上架号。
- ZIP 下载和 PDF 导出需要独立授权；前端菜单不可代替服务端权限检查。
- OCR 建表不等于能识别图片，参见 [OCR 第一阶段边界](./ocr-classification.md)。
- 完整 PWA 要求 HTTPS 或 localhost；普通内网 HTTP 下仍可使用业务，但不会启用完整 Service Worker，参见 [PWA 部署](./pwa.md)。
- 敏感影像不得公共缓存；患者信息、密码、Token、OSS 凭据不能写进公开文档和截图。
- 生产升级前必须核对实际构建 SHA、数据库迁移和制品 `manifest.json`，而不是假定 main 与正式标签完全一致。

## 事实来源和文档阅读顺序

优先从根目录 `VERSION`、`release-baseline.json`、后端 `application.properties`、当前 Controller 和 Flyway 迁移确认事实。建议按 [系统架构](./architecture.md) → [开发流程](./development.md) / [部署总览](./deployment.md) → [配置参考](./configuration-reference.md) → [发布基线](./release.md) 顺序阅读。
