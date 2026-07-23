# 最新主分支代码审查

> 审查日期：2026-07-23  
> 审查基线：`v0.7.0` → `main`  
> 基线提交：`ce6ffbc2e7877fd2499f611d0976501e2188dad9`  
> 审查提交：`f04cd6a786e28a53ada3731972b9f170152d7132`

## 1. 审查范围

本次审查聚焦 `v0.7.0` 之后进入 `main` 的 15 个提交，同时抽查与新增能力直接相关的权限、数据库迁移、前端路由、发布工作流和既有部署配置。

主要变更包括：

- 后端运行错误采集、脱敏、指纹聚合和状态流转；
- 前端“运行错误中心”页面；
- `system:error:read` 与 `system:error:manage` 权限；
- `system_error_event` Flyway 迁移；
- 500 响应错误编号与 `X-Error-Id`；
- 前端内嵌单体 JAR 构建与 Release 上传；
- 单体 JAR 默认端口 `8002`。

本次没有在本地执行 Maven、pnpm、Playwright 或 PostgreSQL 集成测试。当前提交也没有可供读取的关联工作流状态，因此本文中的“通过”仅表示静态代码结构未发现阻断性语法问题，不代表已经完成真实构建和部署验证。

## 2. 总体结论

新增功能的分层较清晰：日志 Appender 只负责采集，Service 使用有界队列异步落库，Mapper 完成聚合，Controller 和前端分别按读写权限暴露能力。错误编号、Request ID、状态流转和前端列表也能够形成完整排障闭环。

当前不建议直接把 `main` 标记为新的正式版本。至少应先处理医疗数据脱敏边界、持久化失败丢事件、数据保留策略和新增功能集成测试四项问题，再执行完整质量门禁和 Windows 实机验证。

## 3. 发现的问题

### P1：脱敏规则不足以保证医疗敏感信息不会进入错误中心

涉及文件：

- `backend-repo/src/main/java/com/zjcxph/imgapi/utils/RuntimeErrorSanitizer.java`
- `backend-repo/src/main/java/com/zjcxph/imgapi/service/SystemErrorEventService.java`
- `backend-repo/src/main/resources/db/migration/V20260723163000__add_system_error_event.sql`

现有规则主要覆盖密码、Token、AccessKey、长身份证号、JWT 和部分密钥赋值，但没有覆盖：

- 患者姓名；
- 病案号 `bah`；
- 上架号 `sjh`；
- 住院号、床号等业务标识；
- 本地/NAS 文件路径；
- SQL 语句中的业务参数；
- URL Query 中未命中现有正则的敏感值。

错误中心向 `system:error:read` 用户返回“脱敏堆栈”，迁移又会把该权限补给部分已有 `log:read` 或 `system:read` 角色。若任意异常消息包含病案字段，敏感信息可能长期保存在数据库并被页面展示。

建议：

1. 不直接保存完整异常字符串，优先保存异常类型、受控栈帧、错误码和结构化上下文；
2. 增加病案号、上架号、患者姓名、文件路径、URL Query 和 SQL 参数的业务脱敏规则；
3. 建立脱敏回归样本集，覆盖真实历史错误格式；
4. 将“脱敏堆栈”改为“已执行基础脱敏的受控堆栈”，避免形成绝对安全承诺；
5. 重新核对 `system:error:read` 的默认角色授予范围。

### P1：落库失败后事件会被永久丢弃

涉及文件：

- `backend-repo/src/main/java/com/zjcxph/imgapi/service/SystemErrorEventService.java`

`flushPending()` 先从队列 `drainTo` 取出最多 250 条，再逐条调用 `mapper.upsert()`。单条写入失败时仅向 `System.err` 输出异常类型，事件不会重新入队，也没有失败计数、重试或监控指标。

这意味着数据库连接异常、迁移未完成或表不可用时，最需要记录的错误反而会消失。

建议：

1. 对可恢复异常使用有限次数、带退避的重试；
2. 重试仍失败时进入独立失败队列或本地受控文件；
3. 增加 `captured`、`persisted`、`dropped`、`persistence_failed` 指标；
4. 防止失败事件无限重入主日志采集链路；
5. 关闭阶段设置最大排空时长，避免停机长期阻塞。

### P2：错误事件表没有保留与归档策略

涉及文件：

- `backend-repo/src/main/resources/db/migration/V20260723163000__add_system_error_event.sql`
- `backend-repo/src/main/java/com/zjcxph/imgapi/mapper/SystemErrorEventMapper.java`

当前没有清理任务、保留天数或归档流程。虽然相同指纹会聚合，但包含动态文本的第三方 WARN/ERROR 仍可能持续产生新指纹。错误组和堆栈将无限增长。

建议：

- 增加可配置保留期，例如已解决错误 180 天、未解决错误长期保留；
- 清理前保留聚合统计或导出审计快照；
- 为清理任务增加批量上限和执行指标；
- 在部署就绪检查中展示表大小、增长速度和最老记录。

### P2：错误编号随机后缀只有 32 位

涉及文件：

- `backend-repo/src/main/java/com/zjcxph/imgapi/utils/RuntimeErrorSanitizer.java`
- `backend-repo/src/main/resources/db/migration/V20260723163000__add_system_error_event.sql`

错误编号格式为 `ERR-yyyyMMdd-8位十六进制`，随机空间约为 2³²。日内生成量较高时会出现生日碰撞风险，而 `error_id` 又有唯一约束；碰撞会导致 `ON CONFLICT (fingerprint)` 无法处理的唯一键异常。

建议将随机部分扩展到至少 12～16 位十六进制，或者使用数据库序列、ULID/UUIDv7，并补充冲突重试测试。

### P2：新增错误中心缺少完整集成测试

目前已有：

- 脱敏工具单元测试；
- 权限继承单元测试；
- 全局异常错误编号测试；
- 单体 JAR 构建脚本测试。

仍缺少：

- `system_error_event` PostgreSQL 迁移测试；
- 指纹冲突时的 Upsert 聚合测试；
- 最近 50 个错误编号滚动测试；
- RESOLVED 后再次发生自动重新打开测试；
- Controller 的 401/403/400/404/200 测试；
- 队列满、数据库失败、关闭排空测试；
- 前端筛选、详情和状态变更测试。

建议把 Mapper 与迁移测试放入现有 PostgreSQL 集成门禁，而不是使用 H2 代替。

### P2：Windows 发布工作流对 PR 仍授予写权限

涉及文件：

- `.github/workflows/windows-release-package.yml`

工作流顶层设置了 `contents: write`，因此 PR 构建和标签发布共用写权限。单体 JAR 工作流已经采用“构建只读、发布任务写入”的更合理结构，两者应保持一致。

建议：

- 顶层改为 `contents: read`；
- 只在标签发布 Job 上设置 `contents: write`；
- 保留 `pull-requests: read` 和 `issues: read` 的最小需求；
- 在仓库规则中要求 Release 工作流只能由受保护标签触发。

### P3：源码默认端口与单体 JAR 默认端口容易混淆

事实如下：

- 源码和 Windows 离线包后端默认端口：`18045`；
- Actuator 默认端口：`18046`；
- Release 单体 JAR在构建时把业务默认端口改为：`8002`；
- 两种制品都允许通过 `SERVER_PORT` 覆盖。

文档和故障排查必须明确区分制品类型，不应笼统写“MRR 默认端口为 8002”或“MRR 默认端口为 18045”。

## 4. 设计上做得较好的部分

- Controller 默认拒绝未声明访问策略的 API，新增接口使用独立读写权限；
- 列表接口不返回堆栈，只有详情接口返回，降低了普通列表暴露面；
- 日志线程不直接访问数据库，避免业务线程被错误记录拖慢；
- 队列有容量上限，能够避免数据库故障时无限占用内存；
- 相同指纹会聚合并累计次数，已解决错误再次发生会自动重新打开；
- 500 响应同时返回用户可读错误编号和 `X-Error-Id`；
- 单体 JAR工作流会验证前端资源、默认端口、构建版本、Git Commit 和 SHA-256；
- `release-baseline.json` 已更新到迁移 `20260723163000`，并明确禁止仅替换旧 JAR 回滚。

## 5. 建议处理顺序

1. 收紧并验证医疗敏感信息脱敏；
2. 为错误落库增加失败计数、重试和降级通道；
3. 增加 PostgreSQL 集成测试和 Controller 权限测试；
4. 增加错误事件保留与清理策略；
5. 扩展错误编号随机空间；
6. 收紧 Windows Release 工作流权限；
7. 执行完整质量门禁、Windows PowerShell 5.1 检查和实际单体 JAR 启动验证；
8. 验证通过后再决定是否升级 `VERSION` 并创建新标签。

## 6. 发布前验证清单

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/release_baseline.py validate

cd backend-repo
mvn -B -ntp verify

cd ../frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm lint:tsc
pnpm test:run
pnpm build
pnpm test:e2e

cd ../vitepress-doc
npm ci
npm run docs:changelog:test
npm run docs:build
```

还应在 Windows 环境验证：

- `MRR-vX.Y.Z-standalone.jar` 默认监听 `8002`；
- Windows 离线包通过 Nginx 访问，后端监听 `18045`；
- 内嵌前端和外置前端回退均能加载；
- PostgreSQL 迁移成功创建 `app.system_error_event`；
- 普通用户无法访问运行错误中心；
- 具有只读权限的用户不能修改状态；
- 数据库中不存在可识别的患者、病案、密钥和文件路径明文。
