# 发布流程与版本基线

MRR 的发布必须能够回答五个问题：运行的是什么产品版本、对应哪个 Git Commit、何时构建、兼容哪些数据库迁移、是否允许直接回滚应用。

## 唯一版本来源

仓库根目录的 `VERSION` 是 MRR 产品版本的唯一可信来源，当前格式为不带 `v` 前缀的 SemVer，例如：

```text
0.4.0
```

其他位置不得独立决定产品版本：

- Git Tag 使用 `v<VERSION>`，例如 `v0.4.0`；
- 后端 Maven 使用 `${revision}`，其默认值必须与 `VERSION` 相同；
- 前端构建直接读取根目录 `VERSION`；
- 用户手册和内部文档构建直接读取根目录 `VERSION`；
- Windows ZIP 名称、`manifest.json` 和 GitHub Release 由工作流读取 `VERSION` 生成；
- `frontend-fantastic-admin/package.json` 的版本只表示前端模板/Node 包版本，不再作为 MRR 产品版本展示。

修改产品版本时只先修改 `VERSION`，随后同步 Maven 的 `revision`。`scripts/release_baseline.py validate` 会阻止二者不一致的提交进入发布流程。

## 发布兼容性来源

根目录 `release-baseline.json` 描述当前产品版本的兼容边界：

```json
{
  "manifestSchemaVersion": 1,
  "database": {
    "minimumCompatibleMigration": "20260715113552",
    "maximumCompatibleMigration": "20260722110500",
    "backwardCompatibleWithPreviousApplication": false
  },
  "applicationRollback": {
    "allowed": false,
    "reason": "回滚限制说明"
  },
  "configuration": {
    "schemaVersion": 1
  }
}
```

字段含义：

| 字段 | 含义 |
|------|------|
| `manifestSchemaVersion` | 发布清单结构版本，便于以后兼容解析 |
| `minimumCompatibleMigration` | 当前应用允许的最低 Flyway 正式迁移版本 |
| `maximumCompatibleMigration` | 当前应用构建时确认的最高 Flyway 正式迁移版本 |
| `backwardCompatibleWithPreviousApplication` | 升级数据库后，上一应用版本是否已经完成兼容演练 |
| `applicationRollback.allowed` | 是否允许只切回上一版本 JAR/前端而不恢复数据库 |
| `applicationRollback.reason` | 禁止直接回滚时的原因和前置条件 |
| `configuration.schemaVersion` | 配置文件结构版本，用于识别新增、删除或语义变化 |

兼容性必须基于实际升级和回滚演练填写，不能因为迁移看起来只是新增字段就默认标记为兼容。

## 基线校验

在仓库根目录执行：

```bash
python scripts/release_baseline.py validate
```

校验内容包括：

1. `VERSION` 是否为合法 SemVer；
2. Maven 是否使用 `${revision}`，且默认 `revision` 与 `VERSION` 相同；
3. 正式迁移是否全部符合 `VyyyyMMddHHmmss__description.sql`；
4. 最低和最高兼容迁移是否真实存在；
5. 最高兼容迁移是否等于当前正式迁移目录中的最新版本；
6. 回滚与配置结构字段是否完整、类型正确。

只要新增正式 Flyway 迁移，就必须显式更新 `release-baseline.json`。这会迫使发布人员重新判断数据库兼容性，而不是让发布清单静默落后。

## 构建身份

### 后端

Maven 构建会生成：

- `META-INF/build-info.properties`：产品版本和构建时间；
- `BOOT-INF/classes/git.properties`：完整 Git Commit、短 Commit、分支和提交时间。

运行中的后端可通过本机管理端口查看：

```http
GET http://127.0.0.1:18046/actuator/info
```

响应中的 `build.version` 必须等于 `VERSION`，`git.commit.id` 必须能够定位到唯一提交。

### 前端

Vite 构建会把以下内容写入系统信息面板：

- MRR 产品版本；
- 完整 Git Commit 和短 Commit；
- 构建时间；
- 数据库最低/最高兼容迁移；
- 是否兼容上一应用版本；
- 是否允许直接回滚；
- 配置结构版本；
- 单独标注的 Fantastic Admin 模板版本。

正式构建由 GitHub Actions 注入 `MRR_GIT_COMMIT` 和 `MRR_BUILD_TIME`。本地开发时优先读取当前 Git 仓库提交。

### 文档

用户手册和内部文档的站点标题、导航栏都直接读取根目录 `VERSION`。文档包不再维护独立产品版本。

## Windows 发布包

`.github/workflows/windows-release-package.yml` 负责构建和装配单一离线包。

正式标签 `v0.4.0` 对应：

```text
MRR-v0.4.0.zip
```

PR 或手工验证构建会追加短 Commit，避免覆盖正式包：

```text
MRR-v0.4.0-1a2b3c4d.zip
```

发布包包含：

```text
backend/mrr-backend.jar
frontend/
docs/user/
docs/internal/
deploy/windows/
VERSION
release-baseline.json
manifest.json
release-notes.md
SHA256SUMS
```

`manifest.json` 由同一个基线脚本生成，包含：

```json
{
  "manifestSchemaVersion": 1,
  "productVersion": "0.4.0",
  "gitCommit": "完整 SHA",
  "buildTime": "UTC ISO-8601 时间",
  "database": {},
  "applicationRollback": {},
  "configuration": {}
}
```

`SHA256SUMS` 覆盖发布包中的程序、文档、部署脚本和基线文件。部署前应先校验哈希。

## 发布前检查

### 基线

```bash
python scripts/release_baseline.py validate
```

### 后端

```bash
cd backend-repo
mvn test
mvn package
```

确认生成的 JAR 内存在：

```text
META-INF/build-info.properties
BOOT-INF/classes/git.properties
```

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm lint:tsc
pnpm test:run
pnpm build
```

打开系统信息面板，核对产品版本、Commit、构建时间和兼容基线。

### 文档

```bash
cd vitepress-doc
npm ci
npm run docs:build
```

确认用户站和内部站均显示 `v<VERSION>`。

## 标签与自动发布

创建标签前必须确认目标提交已完成测试：

```bash
git tag v0.4.0
git push origin v0.4.0
```

工作流会校验标签必须严格等于 `v` 加 `VERSION`。例如 `VERSION=0.4.0` 时，`v0.4.1` 和 `0.4.0` 都会被拒绝。

标签构建通过后，工作流创建 GitHub Release 并上传 Windows ZIP。

## 数据库发布规则

1. 新数据库严格按 `db/migration` 中的日期时间版本链初始化；
2. 禁止修改已经发布的迁移，修复必须新增迁移；
3. 历史数据库升级前必须备份并执行独立预检；
4. `maximumCompatibleMigration` 表示应用构建时已知的最高迁移，不表示可以自动降级；
5. Flyway 不提供业务级回滚，数据库恢复依赖备份、恢复 SQL 或预先设计的兼容迁移。

## 回滚判定

部署人员必须先读取 `manifest.json`：

- `applicationRollback.allowed=true`：仍需确认目标旧版本在兼容矩阵内，才可只切换应用文件；
- `applicationRollback.allowed=false`：不得直接切回上一 JAR/前端，必须按 `reason` 执行数据库恢复或专项兼容方案；
- `database.backwardCompatibleWithPreviousApplication=false`：说明上一应用版本使用升级后数据库尚未通过验证。

任何情况下都应保留上一发布包、当前配置快照和发布前数据库备份。

## 发布后核对

1. `/actuator/info` 的后端版本与 Commit；
2. 前端系统信息面板的版本、Commit 与构建时间；
3. 用户手册和内部文档标题版本；
4. 解压目录中的 `manifest.json`、`VERSION` 和 `SHA256SUMS`；
5. Flyway 当前版本是否处于 manifest 声明的兼容范围；
6. 登录、权限、病案查询、影像访问、审计、系统设置和状态页冒烟测试。

同一次发布中，上述位置必须指向同一产品版本和同一 Git Commit。
