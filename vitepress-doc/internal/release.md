# 发布流程

## 版本来源

当前产品版本为 `v0.1.1`。仓库中仍存在多个子项目版本：

- 产品版本：Git Tag、GitHub Release、根 README 与 `CHANGELOG.md`。
- 前端模板版本：`frontend-fantastic-admin/package.json`。
- 后端 Maven 版本：`backend-repo/pom.xml`。
- 文档包版本：`vitepress-doc/package.json`。

发布说明必须明确版本类型，不能把前端模板版本直接当作产品版本。

## 发布前准备

1. 确认目标分支、产品版本和发布 Commit。
2. 汇总自上次版本以来的 PR。
3. 标记数据库、配置、权限和兼容性变化。
4. 更新 `CHANGELOG.md` 与用户更新说明。
5. 更新内部部署和运维文档。
6. 检查仓库中没有密钥、患者数据或内网凭证。

## 代码冻结检查

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm lint:tsc
pnpm test:run
pnpm build
pnpm build:mock
```

涉及路由、弹窗、档案袋、设置或图表时执行对应 Playwright 回归。

### 后端

```bash
cd backend-repo
mvn test
mvn package
```

### 文档

```bash
cd vitepress-doc
npm install
npm run docs:build
```

### 监控配置

```bash
promtool check config monitoring/prometheus/prometheus.yml
amtool check-config monitoring/alertmanager/alertmanager.yml
```

Grafana 看板使用 JSON 校验工具检查。

## 数据库发布模式

### 新数据库

- 使用 `V0__baseline_schema.sql` 初始化。
- `baseline-on-migrate=false`。
- 确认 `app` Schema 和 PostgreSQL 扩展权限。
- 验证 V0 创建的表、索引、视图、函数、注释和种子数据。

### 已使用 V0 的数据库

V0 部署后，结构变化必须使用 V1、V2 等新迁移，不能修改已经部署的 V0。

### 旧增量迁移链数据库

旧数据库不能直接切换到 V0。发布前必须准备独立迁移方案，至少包含：

- 结构差异报告。
- 完整备份和恢复演练。
- 数据迁移或新库搬迁脚本。
- 停机窗口。
- 记录数、约束、索引和图片关联校验。
- 明确回滚点。

未完成该方案时，不得让旧库加载 V0。

## 发布说明结构

建议包含：

- 发布日期和版本。
- 新增功能。
- 行为变化。
- 缺陷修复。
- 数据库迁移模式。
- 新增或废弃配置。
- 权限变化。
- 已知限制。
- 升级步骤。
- 回滚条件。

公开说明不得包含真实密钥、患者信息和内部网络细节。

## 发布顺序

1. 宣布维护窗口或停止写入。
2. 备份数据库、配置和当前构建产物。
3. 按数据库类型执行 V0 初始化、后续增量迁移或旧库专用迁移。
4. 部署后端并验证 Flyway 与健康检查。
5. 部署前端。
6. 部署用户手册与内部文档。
7. 验证 Nginx 路由和文档权限。
8. 验证图片服务与 PDF 导出。
9. 恢复业务访问。
10. 观察日志和监控。

## 冒烟测试

发布后至少验证：

- 登录与当前用户接口。
- 用户权限导航。
- 记录管理和患者查询。
- 统计图表。
- 档案装箱。
- `/archive` 普通病案号查询。
- 高位病案号与上架号成对查询。
- 身份证查询和 URL 令牌替换。
- 图片全屏切换、显示设置和刷新版本。
- 浏览器端 PDF 导出。
- 系统设置读取与保存。
- 日志和图片访问审计。
- 系统监控和数据质量页面。
- `/status`。
- `/docs/`、`/docs/internal/` 和 `/api-docs/` 权限。

## 回滚

### 前端与文档

保留上一版本目录或压缩包，Nginx 切回旧目录后重新加载配置。

### 后端

保留上一版本 JAR 和配置。只有数据库结构与旧版本兼容时才能直接回滚 JAR。

### 数据库

Flyway 不自动降级。数据库回滚依赖发布前备份、针对性恢复 SQL 或预先设计的兼容迁移。不可逆数据转换完成后，不能在未验证恢复的情况下回退应用。

## 发布后观察

关注：

- HTTP 5xx 和错误率。
- 接口响应时间。
- HikariCP 活跃、等待和超时。
- PostgreSQL 锁、死锁和连接使用率。
- 图片服务 404、缓存和 CORS 错误。
- 状态页异常区间。
- 日志中的敏感信息。

## GitHub 合并与标签

功能 PR 过程提交较多时优先 Squash and merge。产品标签应指向已经验证的目标分支 Commit，并同步创建 GitHub Release 与更新日志。