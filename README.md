# MRR 医疗病案文件记录管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![VitePress](https://img.shields.io/badge/VitePress-1.5-646CFF)](https://vitepress.dev/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

MRR 是面向医疗机构病案扫描、归档、查询与审计场景的管理系统。项目以病案号、上架号和患者信息为核心，管理病案扫描记录及图片文件，并提供统计分析、档案装箱、访问审计、系统监控和权限控制能力。

> 当前产品版本：`v0.1.1`。正式环境采用 PostgreSQL、Spring Boot、Vue 与 Nginx 的原生部署方式，不依赖 Docker；仓库容器配置仅用于开发、测试或演示。

## 核心功能

| 模块 | 当前能力 |
|------|----------|
| 记录与患者 | 查询扫描记录、患者和关联病案，查看明细并批量打包下载 |
| 影像档案袋 | 按病案号、上架号或身份证查询，浏览、选择、打印和前端导出 PDF |
| 统计分析 | 扫描规模、类型分布、趋势、统计明细和病案统计，图表统一使用 ECharts |
| 档案装箱 | 管理箱号、箱内病案、预期位置和异常状态 |
| OSS 迁移 | 管理图片迁移任务、进度、校验和对象地址 |
| 权限与审计 | 用户、角色权限、操作日志、图片访问审计和响应分析 |
| 系统监控 | PostgreSQL、HikariCP、数据质量、Actuator 和原生监控组件 |
| 服务状态 | `/status` 展示当前状态、近 90 天可用率和异常区间 |
| 文档中心 | 用户手册、内部工程文档和受保护的 Springdoc 实时 API |

## 技术基线

| 层次 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis 4、Flyway、Springdoc、Micrometer |
| 前端 | Vue 3.5、TypeScript 5.9、Vite 8、Element Plus 2.13、Pinia 3、ECharts 6 |
| 数据库 | PostgreSQL 16，业务 Schema 为 `app` |
| 文档 | VitePress 1.5、Mermaid |
| 认证 | JWT、AES-GCM；文档访问使用短期 HttpOnly Cookie |
| 监控 | Actuator、Prometheus、Grafana、Alertmanager、postgres_exporter |

## 本地开发

### 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 16+
- Node.js `^20.19.0` 或 `>=22.12.0`
- pnpm 10.33.0

### 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git checkout dev-no-login
```

### 数据库

新数据库由 Flyway 的 `V0__baseline_schema.sql` 初始化：

```properties
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
```

旧增量迁移保存在 `db/migration-legacy`，只用于审计和历史追溯。已经部署旧迁移链的数据库不能直接切换到 V0，必须制定独立迁移方案。

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

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

默认访问：`http://localhost:9000`。

### 文档

```bash
cd vitepress-doc
npm install
npm run docs:dev:user
npm run docs:dev:internal
npm run docs:build
```

Windows 请求端口处于系统排除范围时，文档启动脚本会自动跳过 `EACCES` 或 `EADDRINUSE` 端口并打印实际地址。

## 查询规则

影像档案袋使用具名参数：

```text
/archive?bah=09999999
/archive?sjh=00000456
/archive?bah=10000000&sjh=00000456
```

- 病案号和上架号统一为八位数字字符串。
- 病案号小于 `10000000` 时可单独查询。
- 病案号大于或等于 `10000000` 时必须同时提供上架号。
- 上架号可单独查询。
- 首次可使用 `/archive?id=<身份证号>`；查询成功后 URL 中的明文会替换为服务端不透明令牌。

## 访问入口

| 入口 | 路由 | 权限 |
|------|------|------|
| 管理端 | `/` | 登录与业务权限 |
| 服务状态 | `/status` | 公开脱敏信息 |
| 系统监控 | `/monitoring` | `system:read` |
| 帮助中心 | `/help` | 登录用户 |
| 用户手册 | `/docs/` | 已登录账号 |
| 内部文档 | `/docs/internal/` | 管理员或 `system:read` |
| 实时 API | `/api-docs/` | 管理员或 `system:read` |

## 生产部署要点

1. 使用 PostgreSQL 16，并从 V0 基线初始化新数据库。
2. 通过环境变量提供数据库密码、JWT、AES 和 OSS 密钥。
3. 以 JAR 运行后端，限制 `18046` 只允许本机或监控网络访问。
4. 使用 Nginx 托管前端和两个文档站点，并反向代理 API 与 Springdoc。
5. 图片服务需要为浏览器端 PDF 配置精确的 CORS 来源。
6. 原生监控配置见 [`monitoring/README.md`](monitoring/README.md)。

## 项目结构

```text
MRR/
├── backend-repo/              # Spring Boot 后端和 Flyway V0 基线
├── frontend-fantastic-admin/  # Vue 管理端与生产 Nginx 配置
├── mrr-db/                    # 数据库辅助脚本与开发参考
├── monitoring/                # Prometheus、Grafana、Alertmanager 配置
├── vitepress-doc/             # 用户手册与正式内部工程文档
├── docker-compose.yml         # 本地开发/演示环境
├── CHANGELOG.md
└── README.md
```

## 文档

- [内部文档首页](vitepress-doc/internal/index.md)
- [系统架构](vitepress-doc/internal/architecture.md)
- [开发流程](vitepress-doc/internal/development.md)
- [部署指南](vitepress-doc/internal/deployment.md)
- [运维与监控](vitepress-doc/internal/operations.md)
- [安装指南](vitepress-doc/getting-started/installation.md)
- [配置说明](vitepress-doc/getting-started/configuration.md)
- [用户手册](vitepress-doc/user-guide/index.md)
- [用户更新说明](vitepress-doc/user-guide/release-notes.md)
- [更新日志](CHANGELOG.md)

## 验证

```bash
cd backend-repo && mvn test && mvn package
cd ../frontend-fantastic-admin && pnpm lint:tsc && pnpm test:run && pnpm build
cd ../vitepress-doc && npm run docs:build
```

GitHub Actions 可能因账户额度不足在执行步骤前失败，需结合本地检查结果判断。

## 贡献与许可

提交变更前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。项目基于 [MIT License](LICENSE) 发布。