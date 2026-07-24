# MRR 内部文档

> 面向开发、测试、数据库、部署和运维人员。根目录 `VERSION` 当前为 **0.7.4**，正式标签为 `v0.7.4`。事实来源依次为当前代码与 Flyway、`application.properties`、`VERSION`、`release-baseline.json`、自动化测试和运行中的 OpenAPI。

## 按任务查找文档

### 了解系统

| 文档 | 说明 |
| --- | --- |
| [系统架构](./architecture.md) | 运行边界、模块、数据流、存储和部署拓扑 |
| [最新代码审查](./code-review.md) | `v0.7.0` 到当前 `main` 的审查范围、风险和处理优先级 |
| [前端工程](./frontend.md) | Vue、路由、权限、设计系统、图表和浏览器兼容 |
| [后端工程](./backend.md) | Spring Boot 分层、认证、存储、导出任务和 Flyway |
| [数据库](./database.md) | PostgreSQL、病案主档、关联、编号、约束和索引 |
| [API 与权限](./api.md) | 接口分组、认证、权限继承和实时 OpenAPI |

### 配置与开发

| 文档 | 说明 |
| --- | --- |
| [配置参考](./configuration-reference.md) | 端口、数据库、图片来源、导出、OSS、日志和监控环境变量 |
| [开发流程](./development.md) | 本地启动、分支、检查、测试和提交规范 |
| [文档维护规范](./documentation.md) | 文档分层、事实来源、同步规则和 PR 检查清单 |
| [更新日志工作流](./changelog-workflow.md) | 自动 Git 记录与人工更新日志 |

### 数据与迁移

| 文档 | 说明 |
| --- | --- |
| [数据导入与迁移](./data-migration.md) | CSV、COPY、分卷、回填、校验和恢复 |
| [逐表导入教程](./data-import/) | 患者、统计、装箱和扫描数据导入 |
| [数据库](./database.md) | `mr_archive`、`archive_id`、大表设计和约束 |
| [OSS 迁移](../user-guide/oss-migration.md) | Nginx 原图、上架号分组、等待补齐和失败重试 |

### 部署与运行

| 文档 | 说明 |
| --- | --- |
| [部署总览](./deployment.md) | Windows 离线包、单体 JAR、内嵌前端、端口和升级验证 |
| [Windows Server 部署](./windows-deployment.md) | Nginx、WinSW、脚本、服务管理和外置前端回退 |
| [单体 JAR 部署](./standalone-jar.md) | Release JAR 下载、SHA-256、8002 端口和直接运行 |
| [运维诊断中心](../user-guide/operations-center.md) | 部署体检、图片诊断、完整性、任务、权限矩阵和维护模式 |
| [运行错误中心](./runtime-errors.md) | WARN/ERROR 采集、权限、指纹、错误编号、脱敏和降级边界 |
| [运维与监控](./operations.md) | Actuator、Prometheus、状态历史和数据质量 |
| [生产运行手册](./runbook.md) | 日常检查、发布、故障处置和回滚 |
| [故障排查](./troubleshooting.md) | 启动、数据库、图片、导出、文档和监控问题 |
| [发布流程](./release.md) | 版本、迁移、构建、验收和回滚 |

### 安全与外部接入

| 文档 | 说明 |
| --- | --- |
| [安全](./security.md) | 密钥、个人信息、日志、网络和开发者模式 |
| [外部系统影像接入](./external-archive-integration.md) | HIS/EMR HMAC Ticket、IP 白名单和接入示例 |
| [API 与权限](./api.md) | JWT、RBAC、独立导出权限和接口边界 |

## 当前系统边界

MRR 管理：

- 患者和关联病案；
- 病案主档 `mr_archive`；
- 统计记录、扫描图片元数据和病案类型；
- 实体档案装箱位置；
- Local/NAS/HTTP/Nginx/OSS 图片来源；
- ZIP/PDF 异步导出任务和临时文件；
- OSS 迁移任务与只读文件浏览；
- 用户、角色、注册审核、日志、访问审计和运行状态；
- 部署就绪、图片来源、完整性、权限矩阵和维护模式；
- 后端运行错误聚合、错误编号和处理状态；
- 外部系统一次性影像调阅。

系统不是 DICOM 诊断工作站，不承担医学诊断、影像测量和诊断报告生成。

## 当前架构事实

### 数据库

- PostgreSQL 16，业务 Schema 为 `app`；
- `mr_archive.id` 是稳定技术主键；
- `mr_statistics`、`mr_scan`、`mr_archive_box_record` 通过 `archive_id` 关联；
- 上架号允许为空，非空时应唯一；
- 病案号不保证全局唯一；
- 编号保留原始格式，不自动补零；
- 新迁移使用 `VyyyyMMddHHmmss__description.sql`；
- 当前兼容迁移上限为 `20260723163000`。

### 大表

- `mr_scan` 面向数千万行；
- 普通接口限制查询范围和返回数量；
- 顺序处理使用主键游标；
- 导入、回填和迁移使用分批、分卷、可恢复脚本；
- 不在普通 Flyway 启动事务中执行数千万行全表更新。

### 图片与导出

- 单个病案允许混合 OSS 与非 OSS 图片；
- 非 OSS 图片优先通过受控 Nginx 节点读取，再降级 NAS/HTTP/本地；
- ZIP/PDF 由后端统一生成；
- 大病案、多来源或超阈值任务转为后台执行；
- 临时文件受总配额、单文件上限和保留期控制；
- 下载支持取消、重新下载和 Range 续传。

### 部署

- Windows 离线包默认由 Nginx 将页面和 API 转发到内嵌前端 JAR `18045`；
- 外置前端目录只作为受管理回退；
- Release 单体 JAR 默认业务端口为 `8002`；
- Actuator 默认只监听 `127.0.0.1:18046`；
- 两种业务端口都可通过 `SERVER_PORT` 覆盖。

### 权限

- 管理端 API 使用 JWT 与 RBAC；
- `record:download` 与 `record:pdf:export` 独立；
- `record:manage` 包含读取、编辑、ZIP 和 PDF；
- `system:read` 可进入运维诊断中心；
- `system:error:read` 查看运行错误，`system:error:manage` 修改处理状态；
- 用户手册要求登录；
- 内部文档和实时 API 要求管理员或 `system:read`；
- `/status` 公开但只返回脱敏状态。

### 账号

- 登录页支持匿名注册；
- 注册账号先进入 `pending`；
- 管理员批准后分配角色，或填写原因拒绝；
- 管理员也可以直接创建一次性临时密码账号；
- 普通密码长度为 6～64 位；
- 首次改密、密码修改和重置会使旧 Token 失效。

## 文档维护原则

1. 代码、迁移和当前配置优先于历史说明；
2. 只描述已进入当前分支的能力，并区分正式标签与未发布主分支变更；
3. 新增页面、接口、权限、配置、迁移或脚本时，同一 PR 更新文档；
4. 用户手册描述可见操作，内部文档记录实现、限制、风险和恢复步骤；
5. API 字段优先查阅运行中的 Springdoc，不复制容易过期的完整接口清单；
6. 用户站点和内部站点独立构建，避免内部内容进入用户搜索；
7. 已执行迁移不可为了让文档一致而修改；
8. 命令必须说明平台、目录和风险边界；
9. 同一主题只保留一个规范入口，旧目录使用指向规范文档的说明，避免重复维护。

完整要求见 [文档维护规范](./documentation.md)。

## 快速验证

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

涉及 PostgreSQL、Nginx、NAS、OSS、Windows 服务或大数据导入时，还应在隔离环境完成真实集成验证。工作流已配置不等于工作流已经成功执行。
