# MRR 内部文档

> 面向开发、测试、部署、数据库和运维人员。本文档以 `main` 当前代码、`application.properties` 和实际 Flyway 迁移为事实来源。

## 文档导航

| 模块 | 说明 |
| --- | --- |
| [系统架构](./architecture.md) | 运行边界、病案主档、图片存储和大表设计 |
| [前端工程](./frontend.md) | Vue、路由、权限、设计系统、图表和浏览器兼容 |
| [后端工程](./backend.md) | Spring Boot 分层、认证、存储、分页和 Flyway |
| [数据库](./database.md) | PostgreSQL、`mr_archive`、关联、编号和索引原则 |
| [数据导入与迁移](./data-migration.md) | CSV、COPY、分卷、回填和校验 |
| [API 与权限](./api.md) | 接口分组、认证、权限和实时 OpenAPI |
| [外部系统影像接入](./external-archive-integration.md) | HIS、EMR 的 HMAC Ticket、IP 白名单和 Python、Java、C# 接入 |
| [开发流程](./development.md) | 本地启动、分支、检查、测试和文档维护 |
| [部署](./deployment.md) | 非 Docker 正式部署、Nginx、文档和图片服务 |
| [Windows Server 部署](./windows-deployment.md) | Windows 原生 JAR、Nginx 和服务管理 |
| [运维与监控](./operations.md) | Actuator、Prometheus、状态页和数据质量 |
| [生产运行手册](./runbook.md) | 日常检查、发布、故障处置和回滚 |
| [安全](./security.md) | 密钥、个人信息、文档访问、日志和网络边界 |
| [故障排查](./troubleshooting.md) | 启动、数据库、图片、文档和监控问题 |
| [发布流程](./release.md) | 版本、迁移、构建、验收和回滚 |
| [更新记录](/user-guide/changelog) | 从 Git 提交历史自动生成的变更记录 |

## 当前系统边界

MRR 是医疗病案文件记录管理系统，管理：

- 患者和关联病案。
- 病案主档 `mr_archive`。
- 扫描记录和图片文件元数据。
- 统计数据和实体装箱位置。
- 本地/NAS 与 OSS 图片来源。
- 用户、角色、日志、访问审计和运行状态。

系统不是 DICOM 诊断工作站，不提供窗宽窗位、医学测量、多帧诊断播放或自动医学结论。

## 关键架构事实

### 数据库

- PostgreSQL 16，业务 Schema 为 `app`。
- `mr_archive.id` 是稳定技术主键。
- `mr_statistics`、`mr_scan`、`mr_archive_box_record` 使用 `archive_id` 关联。
- 上架号允许为空；非空时应唯一。
- 病案号不保证全局唯一。
- 编号保留原始格式，不自动补零。
- 新 Flyway 迁移使用 `VyyyyMMddHHmmss__description.sql`。

### 大表

- `mr_scan` 面向数千万行规模。
- 兼容查询在 SQL 层限制数量。
- 顺序遍历使用主键游标。
- 关联回填和大数据导入使用分批、分卷和可恢复脚本。
- 不在普通 Flyway 启动事务中进行数千万行全表更新。

### 图片

- 默认使用本地/NAS 图片。
- 系统设置可切换 OSS；签名失败时回退本地。
- 图片路径由存储层统一校验。
- 服务端 ZIP 当前仍从本地存储读取。
- 浏览器端 PDF 依赖图片服务 CORS。

### 权限

- 管理端 API 使用 JWT 与 RBAC。
- 用户手册要求登录。
- 内部文档和实时 API 要求 `system:read` 或管理员。
- `/status` 无需登录，但只返回脱敏状态。
- Actuator 默认只监听 `127.0.0.1:18046`。

## 文档维护原则

1. 代码、当前配置和实际迁移链优先于历史说明。
2. 新增页面、接口、配置、迁移或脚本时，同一 PR 更新对应文档。
3. 用户手册只描述可见操作；内部文档记录限制、风险和维护步骤。
4. API 字段优先查阅运行中的 Springdoc，不复制容易过期的完整接口清单。
5. 用户站点和内部站点独立构建，避免内部内容进入用户搜索索引。
6. 已执行迁移不可为了让文档“看起来一致”而修改；应修正文档或新增迁移。
7. 文档中的命令必须说明适用平台、执行目录和风险边界。

## 快速验证

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm lint:tsc
pnpm test:run
pnpm build

cd ../backend-repo
mvn test
mvn package

cd ../vitepress-doc
npm install
npm run docs:build
```

涉及数据库时，还应在 PostgreSQL 副本执行迁移、回填和数据校验。GitHub Actions 未实际运行时，不应把“工作流已创建”等同于代码已经验证。
