# OCR 与智能分类：第一阶段边界

> 适用 v0.8.0 和截至 2026-10-11 的 main。该阶段仅提供安全、权限和持久化基础，**不是可用于生产批量自动 OCR 的完整工作流**。原始设计依据见 [后端实施基线](https://github.com/weepwood/MRR/blob/main/backend-repo/docs/ocr-classification-foundation.md)。

## 当前已有的基础

- 服务端图片类型目录覆盖 0–15；
- OCR 事实表 `mr_scan_ocr`，词库/规则的版本化存储；
- 单病案任务、分类建议、审核及类型变更审计模型；
- 独立的 OCR 与分类相关权限，以及设置的服务端参数校验；
- 数据库约束防止同一个病案并行创建多个活动分类任务。

上述结构由 `V20260724220000`、`V20260724220100`、`V20260724220200` 正式迁移引入。数据表、权限码和配置项存在，不代表已经提供可执行的 OCR 引擎或完整审核界面。

## 默认设置与安全约束

| 设置名 | 第一阶段默认值 |
| --- | --- |
| `ocrEnabled` | `false` |
| `ocrLanguages` | `chi_sim+eng` |
| `ocrMaxConcurrency` | `1` |
| `ocrPageTimeoutSeconds` | `30` |
| `ocrMaxOutputBytes` | `4194304` |
| `ocrAutoProcessNewScans` | `false` |
| `ocrLowConfidenceThreshold` | `0.70` |
| `classificationBatchReviewThreshold` | `0.92` |

服务端对批量审核阈值设置的最低值为 0.90。配置中的 `ocrProfile` 是白名单名称，**不得**作为用户提交的本地可执行程序路径。未经完整验收不要开启历史全量 OCR。

## 尚待后续阶段实施

1. 白名单 OCR 引擎适配、受限进程执行与任务持久化；
2. 词库及分类规则的管理、测试、发布 API；
3. 分类任务的执行、进度、取消和服务重启恢复；
4. 单张审核、批量确认、人工覆盖与正式变更事务；
5. 影像档案袋内完整的任务与审核交互。

## 开发验收底线

- OCR 文本是识别事实，分类输出是建议；人工审核前不得静默覆盖正式 `mr_scan.btype`。
- 原图变化须按 `content_hash` 标记旧结果过期，状态可追踪、可恢复。
- 任务边界必须包含权限拒绝、并发控制、超时、重试、审计与患者隐私保护。
- 不执行三千万级表的无限制扫描，也不在普通日志中输出 OCR 全文或病案敏感字段。
- 在真实 PostgreSQL 和隔离影像源上验收后，再将其描述为已交付的业务功能。

相关资料：[后端工程](./backend.md)、[数据库](./database.md)、[API 与权限](./api.md)。
