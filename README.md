# MRR 医疗病案文件记录管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![VitePress](https://img.shields.io/badge/VitePress-1.5-646CFF)](https://vitepress.dev/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

MRR 是面向医疗机构病案扫描、归档、查询与审计场景的管理系统。项目以病案号、上架号和患者信息为核心，管理病案扫描记录及图片文件，并提供统计分析、档案装箱、访问审计、系统监控、权限控制和外部系统调阅能力。

> 当前产品版本由根目录 [`VERSION`](VERSION) 唯一决定。正式环境采用 PostgreSQL、Spring Boot、Vue 与 Nginx 的单服务器原生部署方式，不依赖 Docker；仓库容器配置仅用于开发、测试或演示。

## 当前版本与发布基线

当前 `VERSION`：`0.4.0`。

同一次发布中的以下位置必须保持一致：

- 后端 `/actuator/info` 的 `build.version`；
- 前端系统信息面板的产品版本；
- 用户手册和内部文档标题；
- Windows 发布 ZIP 名称；
- 发布包 `manifest.json`；
- Git Tag `v<VERSION>`。

数据库兼容范围、应用回滚许可和配置结构版本由 [`release-baseline.json`](release-baseline.json) 描述。执行以下命令可检查版本与迁移基线是否漂移：

```bash
python scripts/release_baseline.py validate
```

详细流程见 [发布流程与版本基线](vitepress-doc/internal/release.md)。

## 核心功能

| 模块 | 当前能力 |
|------|----------|
| 记录与患者 | 查询扫描记录、患者和关联病案，查看明细并批量打包下载 |
| 影像档案袋 | 按病案号、上架号或身份证查询，浏览、选择、打印和前端导出 PDF |
| 外部系统调阅 | HIS/EMR 后端使用 HMAC-SHA256 申请一次性票据，按外部用户身份审计访问 |
| 统计分析 | 扫描规模、趋势、统计明细和病案统计，图表统一使用 ECharts |
| 档案装箱 | 管理箱号、箱内病案、预期位置和异常状态 |
| OSS 迁移 | 管理图片迁移任务、进度、校验和对象地址 |
| 权限与审计 | 用户、角色权限、密码生命周期、操作日志和图片访问审计 |
| 运维与状态 | Actuator、内置状态历史、数据库诊断、备份与 Windows 运维脚本 |
| 文档中心 | 用户手册、内部工程文档和受保护的 Springdoc 实时 API |

## 技术基线

| 层次 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis 4、Flyway、Springdoc、Micrometer |
| 前端 | Vue 3.5、TypeScript 5.9、Vite 8、Element Plus 2.13、Pinia 3、ECharts 6 |
| 数据库 | PostgreSQL 16，业务 Schema 为 `app` |
| 文档 | VitePress 1.5、Mermaid |
| 认证 | JWT、bcrypt、AES-GCM、HMAC 外部调阅票据 |
| 部署 | Windows Server、JAR、Nginx、静态前端与文档 |

## 本地开发

### 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 16+
- Node.js `^20.19.0` 或 `>=22.12.0`
- pnpm 10.33.0
- Python 3.10+，仅用于发布基线校验和 manifest 生成

### 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git checkout dev-no-login
```

### 数据库

新数据库由 `backend-repo/src/main/resources/db/migration` 中的日期时间版本迁移链初始化：

```text
VyyyyMMddHHmmss__description.sql
```

当前基线从 `V20260715113552__baseline_schema.sql` 开始。旧迁移保存在 `db/migration-legacy`，只用于审计和历史映射，不参与正式启动迁移链。

本地可以只启动仓库提供的 PostgreSQL 容器：

```bash
docker compose up -d postgres
```

### 后端

```powershell
Copy-Item backend-repo/src/main/resources/application-local.template.properties `
  backend-repo/src/main/resources/application-local.properties

$env:JWT_SECRET_KEY = '本地签名密钥'
$env:AES_SECRET_KEY = '本地 AES 密钥'

cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

默认业务端口为 `18045`，Actuator 管理端口为仅本机监听的 `18046`。

运行中的构建身份：

```text
http://127.0.0.1:18046/actuator/info
```

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

默认开发端口以 Vite 控制台输出为准。系统信息面板会读取根目录 `VERSION`、当前 Git Commit 和 `release-baseline.json`。

### 文档

```bash
cd vitepress-doc
npm ci
npm run docs:dev:user
npm run docs:dev:internal
npm run docs:build
```

两个文档站点的标题和导航会直接显示根目录产品版本。

## 影像查询规则

影像档案袋使用具名参数：

```text
/archive?bah=9999999
/archive?sjh=456
/archive?bah=10000000&sjh=456
```

- 病案号和上架号保留数据库原始格式，不再全局补齐固定长度；
- 病案号小于 `10000000` 时可单独查询；
- 病案号大于或等于 `10000000` 时必须同时提供上架号；
- 唯一上架号可单独查询；
- 首次可使用 `/archive?id=<身份证号>`，查询成功后 URL 中的明文会替换为服务端不透明令牌。

## 访问入口

| 入口 | 路由 | 权限 |
|------|------|------|
| 管理端 | `/` | 登录与业务权限 |
| 外部影像调阅 | `/archive/external?ticket=...` | 一次性 HMAC 票据 |
| 服务状态 | `/status` | 公开脱敏信息 |
| 系统监控 | `/monitoring` | `system:read` |
| 帮助中心 | `/help` | 登录用户 |
| 用户手册 | `/docs/` | 已登录账号 |
| 内部文档 | `/docs/internal/` | 管理员或 `system:read` |
| 实时 API | `/api-docs/` | 管理员或 `system:read` |

## 生产部署要点

1. 使用 PostgreSQL 16，并按正式日期时间迁移链初始化或升级数据库；
2. 通过外部配置文件或环境变量提供数据库密码、JWT、AES、HMAC 和 OSS 密钥；
3. 以 JAR 运行后端，限制 `18046` 只允许本机访问；
4. 使用 Nginx 托管前端和文档，并反向代理 API；
5. 使用 Windows 离线发布包中的 `manifest.json` 判断版本、数据库兼容和回滚条件；
6. 部署前校验 `SHA256SUMS`，并保留上一发布包、配置快照和数据库备份；
7. 图片服务需要为浏览器端 PDF 配置精确的 CORS 来源。

## 项目结构

```text
MRR/
├── VERSION                     # 唯一产品版本
├── release-baseline.json       # 数据库、回滚和配置兼容基线
├── scripts/release_baseline.py # 基线校验与 manifest 生成
├── backend-repo/               # Spring Boot 后端和 Flyway 迁移
├── frontend-fantastic-admin/   # Vue 管理端与影像调阅端
├── vitepress-doc/              # 用户手册与内部工程文档
├── deploy/windows/             # 单服务器 Windows 部署与运维脚本
├── mrr-db/                     # 数据库辅助脚本与开发参考
├── monitoring/                 # 可选的扩展监控配置
├── CHANGELOG.md
└── README.md
```

## 验证

```bash
python scripts/release_baseline.py validate
cd backend-repo && mvn test && mvn package
cd ../frontend-fantastic-admin && pnpm lint:tsc && pnpm test:run && pnpm build
cd ../vitepress-doc && npm run docs:build
```

GitHub Actions 会进一步校验：

- 标签与 `VERSION` 一致；
- 后端 JAR 内的产品版本与 Git SHA；
- 前端产物内的产品版本与 Git SHA；
- 文档产物内的产品版本；
- 发布 ZIP 的 manifest 和 SHA256 清单。

## 文档

- [内部文档首页](vitepress-doc/internal/index.md)
- [系统架构](vitepress-doc/internal/architecture.md)
- [发布流程与版本基线](vitepress-doc/internal/release.md)
- [Windows Server 部署](vitepress-doc/internal/windows-deployment.md)
- [外部系统影像接入](vitepress-doc/internal/external-archive-integration.md)
- [用户手册](vitepress-doc/user-guide/index.md)
- [更新日志](CHANGELOG.md)

## 贡献与许可

提交变更前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。项目基于 [MIT License](LICENSE) 发布。
