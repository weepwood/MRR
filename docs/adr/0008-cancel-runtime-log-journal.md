# ADR-0008：取消运行日志 journal 功能，运行错误中心承担 WARN/ERROR 展示

- 状态：Proposed
- 日期：2026-11-19
- 决策者：项目负责人（2026-11-19 口头确认：取消运行日志功能、完善运行错误中心、前向迁移 DROP 三张表、保留文件日志）
- 关联 Issue：无关联 Issue（产品方向由负责人直接拍板，见上方决策记录）
- 关联 PR：feat/remove-runtime-logs-journal
- 替代 ADR：部分替代 ADR-0002（可恢复的私有运行诊断）中 journal 采集入库与检索部分；错误中心实时捕获与脱敏契约继续沿用

## 背景

运行日志功能由 ADR-0002 引入，目标是把脱敏后的 JSON 日志文件采集进 PostgreSQL 分区表，提供页面检索与错误组投影。经过 #612/#613（正文恢复与问题概览）、#615/#616（线程名）、#620（E2E）等多轮迭代后，功能骨架已完整。

已确认事实：

- `app.runtime-logs.enabled` 从未进入 `application.properties`，部署模板 `data-governance.properties.example` 显式 `enabled=false`，即**生产环境从未启用**，生产库对应表为空；
- 运行错误中心（`system_error_event` + `RuntimeErrorLogCapture` 实时捕获）在开关关闭时（默认）独立工作，2026-11-19 本地端到端验证通过：触发 HTTP 500 后 8 秒内错误组入库并可查询；
- 运行日志的运维闭环存在多项长期缺口：分区只增不删无保留策略（方案文档 G2）、补录投影模块名失真（G3）、生产默认关闭（G1）；
- 维护两套并行采集路径（实时捕获 + journal 投影，由开关二选一）的复杂度持续存在。

负责人判断：错误中心已覆盖「前端直接查看后端 WARN/ERROR」的核心诉求，运行日志功能的边际价值不抵其维护成本。

## 决策

1. **取消运行日志功能**：删除 journal 采集入库链路与全部页面/接口，包括 `RuntimeLogController`、`RuntimeLogJournalService`、`RuntimeLogJournalReader`、`RuntimeLogQueryService`、`RuntimeLogQueryPlan`、`RuntimeLogContext`、`ObservableAsyncLogAppender`；
2. **运行错误中心成为 WARN/ERROR 的唯一展示入口**：`RuntimeErrorLogCapture` 去掉 `@ConditionalOnProperty`，常驻生效；`system_error_event`、`runtime_error_projection_receipt` 与其 upsert 契约不变；
3. **保留 logback 文件日志**（`img-api.log`，异步、10MB 轮转、`app.file-log.buffer-cap`/`app.file-log.buffer-days` 上限），作为数据库之外的排障兜底；缓冲配置键由 `app.runtime-logs.buffer-*` 更名以消除误导；
4. **新增前向迁移 DROP 三张表**：`app.runtime_log_event`（连带分区与索引）、`app.runtime_log_source`、`app.runtime_log_capacity_sample`。生产表为空，无数据丢失；本地测试数据不保留。

## 必须保持的不变量

- 所有 WARN/ERROR 仍经 `PrivateJsonLogEncoder` 脱敏后落盘，脱敏契约与 #612/#613 测试锁定行为不变；
- `SystemErrorEventService.capture` 的异步队列、自抑制、GlobalExceptionHandler 业务异常过滤逻辑不变；
- `system_error_event` upsert 的 fingerprint 分组、receipt 防重、RESOLVED 复发重开语义不变；
- 无 Token 访问错误中心接口仍返回 401，`system:error:read`/`system:error:manage` 权限口径不变；
- 已进 main 的 Flyway 历史迁移（V20261018102000、V20261119030000）不做任何修改，只新增前向迁移；
- 医疗数据隐私红线：删除表结构不涉及患者数据（运行日志表仅含脱敏系统日志）。

## 方案边界

### 包含

- 后端 journal 链路类删除、开关条件移除、logback 配置更名、部署模板与本地配置清理；
- 前端运行日志页面、路由、API 模块删除，及错误中心/日志管理页对 `/runtime-logs` 跳转入口的清理；
- 错误中心完善：详情显示真实线程名（移除 `privateRead` 强制 `[HIDDEN]`）、`[MESSAGE OMITTED]` 占位说明、Request ID 关联跳转访问日志、概览指标卡点击筛选；
- 相关测试的删除与新增。

### 不包含

- 不引入任何新运维组件（VictoriaLogs/ELK/Loki 等外部日志平台调研结论存档于 `.workbuddy/analysis/runtime-logs-completion-plan.md`，未采纳）；
- 不改动脱敏白名单与 `RuntimeErrorSanitizer` 契约；
- 不处理 `message_summary` 脱敏口径放宽（含路径消息仍为 `[MESSAGE OMITTED]`，属 #612 既定行为）；
- 不删除 `img-api.log` 文件输出与轮转策略。

## 替代方案

### 方案 A：保留功能并补齐运维闭环（保留策略 ADR、生产启用）

- 优点：保留按请求检索原始日志、历史补录、缺口观测能力；
- 缺点：生产从未启用说明真实需求不存在；维护两套采集路径与无界分区表的长期成本高；
- 未采用原因：负责人拍板取消，错误中心已满足核心诉求。

### 方案 B：仅下线入口，代码与表保留

- 优点：改动最小，回滚最容易；
- 缺点：死代码、死表与 `@ConditionalOnProperty` 双路径继续制造认知负担（本功能历史上正是双路径引发了 #615/#616 的口径不一致）；
- 未采用原因：功能取消是长期决策，半下线只会延迟成本。

## 影响

### 正面影响

- WARN/ERROR 链路收敛为单一路径（实时捕获），消除开关二选一的分支复杂度；
- 数据库减少三张表与按月分区维护负担，`databaseBytes` 容量监控诉求消失；
- 前端减少一个高复杂度页面及其测试。

### 负面影响与技术债

- 失去按 requestId/errorId 检索原始日志正文与堆栈的能力（错误中心仅保留摘要与最近一次堆栈）；
- 失去断点续采与坏行恢复能力：`SystemErrorEventService` 内存队列在进程崩溃时丢失的事件不再能从文件自动补录，降级为人工查看 `img-api.log` 文件（`destroy()` 提示文案同步修改）；
- `insights` 问题概览（趋势、模块分布）随页面一并消失。

### 安全、隐私与审计

- 攻击面缩小：`/api/v1/runtime-logs/**` 全部下线；
- 脱敏契约不变，文件日志继续受 `PrivateJsonLogEncoder` 保护；
- 状态流转审计（acknowledged_by/resolved_at）不受影响。

### 数据库、性能与容量

- 前向迁移 DROP 三张表（生产为空表）；`runtime_error_projection_receipt` 保留；
- 日志写入不再进数据库，PostgreSQL 容量增速可预期下降。

### Chrome 86、Windows 与部署兼容性

- 前端仅删除页面与入口，无新增样式，Chrome 86 红线不受影响；
- 部署模板 `data-governance.properties.example` 同步清理配置键；`app.file-log.buffer-*` 在 Windows PowerShell 5.1 运维脚本无涉及。

## 迁移方案

1. 新迁移 `V20261119083000__drop_runtime_log_journal.sql` 执行 DROP；
2. `application.properties`（本就无相关键）、`application-local.properties`（删除 `app.runtime-logs.enabled=true`）、部署模板（删除 journal 键、新增 file-log 键注释）同步更新；
3. logback `ASYNC_FILE` 改用标准 `ch.qos.logback.classic.AsyncAppender`（参数等价：queueSize=512、discardingThreshold=0、neverBlock=true、includeCallerData=false）；
4. 前端删除页面/路由/API 模块；错误中心与日志管理页移除指向 `/runtime-logs` 的跳转；
5. vitepress 文档如引用运行日志页面需同步（本轮检查 `code-review.md` 仅泛引，无操作文档，无需改）。

## 回滚与前向修复

- 代码回滚 = revert PR 重新部署；
- 数据库回滚：revert 不删历史迁移，需新前向迁移重建三表（可从 V20261018102000 抄表结构；表为空，无数据恢复问题）；
- 若取消后业务需要原始日志检索，前向修复方向为恢复 journal 链路或引入外部日志库（须重新走 ADR）。

## 验证

- 单元测试：新增「journal Bean 不存在 + `RuntimeErrorLogCapture` 无条件常驻」上下文测试；调整 `LoginInterceptorDeveloperModeTest`、`DbBrowseRegistryTest` 中对已删端点/表的引用；
- PostgreSQL 集成测试：Flyway 迁移后断言三表不存在、`runtime_error_projection_receipt` 存在；
- E2E 或手工流程：本地启动后端，触发 HTTP 500，8 秒内错误中心接口可见该错误组；`/api/v1/runtime-logs/health` 返回 404 语义；前端 `vue-tsc` + 定向 Vitest 通过；
- 性能与容量：不适用（删除性变更）；
- 生产或灰度验证：随下一次发布观察错误中心数据流与 Flyway 执行日志。
