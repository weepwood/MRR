# 运行错误中心

运行错误中心用于集中收集后端 `WARN` 和 `ERROR` 日志，按错误指纹合并重复事件，并通过错误编号和 Request ID 与用户报障、访问日志和接口调用关联。

该功能位于 `main` 的未发布变更中，数据库迁移版本为 `20260723163000`。

## 1. 访问入口与权限

| 能力 | 路由或接口 | 权限 |
| --- | --- | --- |
| 前端页面 | `/runtime-errors` | `system:error:read` |
| 列表 | `GET /api/v1/system-errors` | `system:error:read` |
| 总览 | `GET /api/v1/system-errors/overview` | `system:error:read` |
| 详情 | `GET /api/v1/system-errors/{id}` | `system:error:read` |
| 修改状态 | `POST /api/v1/system-errors/{id}/status` | `system:error:manage` |

权限继承关系：

```text
system:manage
├── system:read
└── system:error:manage
    └── system:error:read
```

管理员不受普通权限列表限制。为普通角色授权时，应优先只授予 `system:error:read`；只有负责处理故障的人员才授予 `system:error:manage`。

## 2. 数据流

```mermaid
flowchart LR
    A[业务或框架 WARN/ERROR] --> B[RuntimeErrorLogCapture]
    B --> C[基础脱敏与指纹计算]
    C --> D[有界内存队列]
    D --> E[定时批量排空]
    E --> F[SystemErrorEventMapper]
    F --> G[(app.system_error_event)]
    G --> H[运行错误中心页面]
```

### 采集阶段

`RuntimeErrorLogCapture` 动态挂载到 Logback Root Logger。Appender 不直接访问数据库，只调用 `SystemErrorEventService.capture()`。

采集逻辑会：

- 忽略低于 `WARN` 的日志；
- 忽略错误中心自身记录器，避免自循环；
- 忽略已知的业务异常和客户端输入异常；
- 生成错误编号；
- 执行基础脱敏；
- 计算指纹；
- 放入最大 5000 条的内存队列。

### 持久化阶段

默认每 2 秒执行一次排空，每次最多处理 250 条：

```properties
app.runtime-errors.flush-interval-ms=2000
```

该配置当前没有显式写入 `application.properties`，使用代码中的默认值。需要覆盖时可使用：

```text
APP_RUNTIME_ERRORS_FLUSH_INTERVAL_MS=2000
```

Spring Boot 会把环境变量映射到 `app.runtime-errors.flush-interval-ms`。

## 3. 指纹和聚合

错误指纹由以下字段生成 SHA-256：

```text
日志级别 | Logger 名称 | 异常类型 | 规范化错误摘要
```

规范化会替换：

- 错误编号；
- UUID；
- 两位及以上数字；
- 较长十六进制值。

同一指纹再次发生时：

- `occurrence_count` 加 1；
- 更新最近发生时间、摘要、堆栈、Request ID 和线程；
- 保存最近 50 个错误编号；
- 已解决事件重新变为 `OPEN`；
- 清空原处理人和解决时间。

指纹聚合用于减少重复记录，但不能替代业务错误码。动态文本未被规范化时，仍可能生成大量不同错误组。

## 4. 状态流转

```text
OPEN（待处理）
  ├── ACKNOWLEDGED（处理中）
  └── RESOLVED（已解决）

RESOLVED 再次发生同类错误 → 自动回到 OPEN
```

状态接口只接受：

- `OPEN`
- `ACKNOWLEDGED`
- `RESOLVED`

前端显示为“待处理”“处理中”“已解决”。

## 5. 错误编号

未处理的 500 异常会返回：

```text
服务器内部错误，请联系管理员（错误编号：ERR-20260723-AB12CD34）
```

同时响应头包含：

```text
X-Error-Id: ERR-20260723-AB12CD34
```

排查时优先让用户提供错误编号，再在运行错误中心搜索。若事件包含 Request ID，可继续在访问日志中查找同一次请求。

错误编号不是访问凭据，不应包含患者信息、账号或接口参数。

## 6. 数据表

表名：

```text
app.system_error_event
```

关键字段：

| 字段 | 说明 |
| --- | --- |
| `error_id` | 错误组首次编号 |
| `error_ids` | 同组最近 50 个错误编号 |
| `fingerprint` | 聚合指纹 |
| `level` | `WARN` 或 `ERROR` |
| `module` | 根据 Logger 名称提取的模块 |
| `message_summary` | 基础脱敏后的摘要 |
| `stack_trace` | 基础脱敏后的受控长度堆栈 |
| `request_id` | 最近一次关联请求编号 |
| `occurrence_count` | 累计发生次数 |
| `status` | 处理状态 |
| `acknowledged_by` | 最近处理人 |
| `resolved_at` | 最近解决时间 |

列表接口不返回 `stack_trace`，详情接口才返回。

## 7. 脱敏边界

当前实现能够处理部分常见秘密和标识：

- Bearer Token 和 JWT；
- password、token、secret、signature、ticket、AccessKey 等赋值；
- 16～19 位长身份号码；
- 摘要最长 2000 字符；
- 堆栈最长 16000 字符。

这只是基础脱敏，不代表所有医疗敏感信息都已清除。当前规则不保证覆盖患者姓名、病案号、上架号、文件路径、SQL 参数和任意业务字段。

生产要求：

1. 业务日志不得主动拼接患者原始字段；
2. 页面只授予必要运维人员；
3. 上线前使用真实历史错误样本做脱敏回归；
4. 定期抽查数据库记录；
5. 发现敏感信息时，应先阻断继续采集，再修正规则并清理已有数据。

## 8. 容量与保留

当前代码按错误指纹聚合，但没有自动清理 `system_error_event` 的任务。运维需要监控：

- 错误组总数；
- 表和索引大小；
- 每日新增指纹；
- 单组累计次数；
- 队列丢弃事件；
- 持久化失败。

在正式版本中应增加保留策略。建议初始策略：

- 未解决错误：持续保留；
- 已解决错误：保留 180 天；
- 清理前保留聚合报表；
- 每次分批删除，避免长事务；
- 清理过程写入运维审计。

## 9. 已知降级行为

### 队列已满

队列容量为 5000。满后新事件不会阻塞业务线程，而是被丢弃；下一次排空时只向标准错误输出丢弃数量。

### 数据库写入失败

当前排空逻辑会先从队列取出事件。单条 Upsert 失败后不会自动重新入队，因此该事件可能永久丢失。数据库故障期间不能把错误中心当作完整日志来源，仍需保留主日志文件和 Nginx 入口日志。

### 应用完全停止

错误中心与业务应用同进程。JVM 无法启动或进程崩溃时，页面也无法访问。此时应查看：

- Windows 服务日志；
- `img-api.log`；
- Nginx `error.log`；
- Windows 事件查看器；
- PostgreSQL 日志。

## 10. 排查流程

1. 获取用户提供的错误编号和发生时间；
2. 在 `/runtime-errors` 搜索错误编号；
3. 查看级别、模块、摘要、首次和最后发生时间；
4. 通过 Request ID 查询访问日志；
5. 对照接口响应分析和系统监控；
6. 核对数据库、OSS、Nginx、临时目录和只读模式；
7. 修复后标记为“处理中”；
8. 验证通过后标记“已解决”；
9. 继续观察是否自动重新打开。

## 11. 建议验证用例

- 相同异常不同数字能够聚合；
- 不同异常类型不会错误合并；
- JWT、密码和身份证号不会出现在数据库；
- 普通用户访问返回 403；
- 只读用户可以查看但不能改状态；
- 管理用户可以完成三种状态流转；
- 已解决错误再次发生后重新打开；
- 50 个以上错误编号只保留最近 50 个；
- 数据库不可用时业务请求不会被日志采集线程阻塞；
- 关闭应用时不会因排空队列无限等待。
