# MRR 医疗病案文件管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![VitePress](https://img.shields.io/badge/VitePress-1.5-646CFF)](https://vitepress.dev/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

MRR 面向医疗机构的病案扫描、归档、检索、调阅、导出、统计、数据治理和运维场景。系统以患者、病案主档、扫描图片、实体档案位置和多来源文件为核心，提供管理端、影像档案袋、外部系统调阅、权限审计、OSS 迁移、运行诊断与 Windows 原生部署能力。

> 根目录 [`VERSION`](VERSION) 是唯一产品版本源，当前值为 **0.7.2**，正式标签为 `v0.7.2`。正式版本、数据库兼容范围和回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。

## 项目定位

MRR 主要解决：

- 将患者、病案、扫描记录、图片和实体档案位置建立稳定关联；
- 在数千万级扫描图片元数据规模下完成检索、调阅和导出；
- 同时兼容本地/NAS、Nginx 静态资源、HTTP 节点和 OSS；
- 对病案查看、修改、下载、PDF 导出和系统管理执行权限控制与审计；
- 通过 HMAC 一次性 Ticket 向 HIS、EMR 等外部系统提供受控调阅入口；
- 在 Windows Server 内网环境中完成部署、升级、回滚、维护和排障。

MRR 不是 DICOM 诊断工作站，不提供窗宽窗位、医学测量、多帧诊断播放或自动医学结论。

## 当前能力

| 领域 | `main` 当前能力 |
| --- | --- |
| 患者与病案 | 查询、编辑、导入导出、年度/科室统计、身份证缺失与疑似同人分析 |
| 扫描记录 | 大表分页、记录查询、病案类型、扫描统计与明细 |
| 影像档案袋 | 按病案号、上架号或身份证查询，分类浏览、全屏预览、选择、打印、ZIP/PDF 导出 |
| 异步导出 | 后台生成 ZIP/PDF、进度、取消、重新下载、临时文件过期和 Range 续传 |
| 多来源图片 | OSS 或非 OSS 优先；Nginx、NAS/HTTP、本地目录降级读取 |
| OSS 迁移 | 从 Nginx 原图迁移，按上架号分组，等待补齐、失败重试、校验和任务管理 |
| 文件浏览 | 只读 OSS 浏览器；只读 Nginx 浏览器，支持 default、BA01、BA02、BA03 节点 |
| 外部调阅 | HMAC-SHA256 Ticket、有效期、nonce、来源 IP 校验和外部访问审计 |
| 账号与权限 | JWT、RBAC、管理员建号、自助注册审核、首次改密、重置密码和账号状态 |
| 审计与监控 | 操作日志、图片访问审计、接口响应分析、Actuator、服务状态和数据库诊断 |
| 运行错误中心 | 聚合后端 WARN/ERROR、错误编号、Request ID、基础脱敏堆栈和处理状态 |
| 部署运维 | 内嵌前端 JAR、Nginx 统一入口、外置前端回退、Windows 离线包、一键管理和单体 JAR |
| 文档 | 登录后用户手册、受权限保护的内部文档和 Springdoc 实时 API 文档 |

## 技术基线

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis 4、Flyway、Springdoc、Micrometer |
| 前端 | Vue 3.5、TypeScript 5.9、Vite 8、Element Plus、Pinia、ECharts |
| 数据库 | PostgreSQL 16，业务 Schema 为 `app` |
| 认证安全 | JWT、bcrypt、AES-GCM、RBAC、HMAC 外部调阅票据 |
| 对象存储 | S3 兼容 OSS，后端 SDK 读取和签名 |
| 文档 | VitePress、Mermaid |
| 正式部署 | Windows Server、Spring Boot JAR、Nginx、WinSW、PostgreSQL |

## 业务规则摘要

### 病案号与上架号

- `bah`、`sjh` 保留数据库原始格式，不自动补零；
- 病案号小于 `10000000` 时可以单独查询；
- 病案号大于或等于 `10000000` 时必须同时提供上架号；
- 非空且唯一的上架号可以单独查询；
- 身份证首次查询可使用 `/archive?id=<身份证号>`，成功后 URL 替换为不透明令牌。

### 图片降级顺序

具体顺序取决于系统设置和 `archive.image-source.prefer-oss`：

1. 优先来源（OSS 或非 OSS）；
2. 非 OSS 图片优先通过配置的 Nginx 节点读取；
3. 再尝试 NAS/HTTP 节点；
4. 后端可直接访问文件时，最后回退本地目录；
5. 单个病案允许同时存在已迁移和未迁移图片。

### 记录类权限

| 权限 | 能力 |
| --- | --- |
| `record:read` | 查看病案和图片 |
| `record:edit` | 编辑记录，并继承读取 |
| `record:download` | 生成和下载 ZIP，并继承读取 |
| `record:pdf:export` | 生成和下载 PDF，并继承读取 |
| `record:manage` | 包含读取、编辑、下载和 PDF 导出 |

具体接口权限以当前 Controller、授权拦截器、自动化测试和运行中的 OpenAPI 为准。

## 访问入口

| 入口 | 路由 | 访问要求 |
| --- | --- | --- |
| 管理端 | `/` | 登录及相应业务权限 |
| 影像档案袋 | `/archive` 或 `/archive/embed` | `record:read` |
| 外部影像调阅 | `/archive/external?ticket=...` | 有效的一次性 Ticket |
| Nginx 文件浏览 | `/nginx-browser` | `record:read` |
| OSS 文件浏览 | `/oss-browser` | `record:read` |
| 运维诊断中心 | `/operations-center` | `system:read` |
| 运行错误中心 | `/runtime-errors` | `system:error:read` |
| 服务状态 | `/status` | 公开脱敏信息 |
| 系统监控 | `/monitoring` | `system:read` |
| 用户手册 | `/docs/` | 已登录账号 |
| 内部文档 | `/docs/internal/` | 管理员或 `system:read` |
| 实时 API | `/api-docs/` | 管理员或 `system:read` |

## 发布制品与端口

| 制品 | 默认业务端口 | 内容 | 适用场景 |
| --- | ---: | --- | --- |
| `MRR-vX.Y.Z.zip` | `18045` | JAR、Nginx、WinSW、文档、运维脚本和外置前端回退 | Windows Server 正式受管理部署 |
| `MRR-vX.Y.Z-standalone.jar` | `8002` | 后端 + 内嵌 Vue 前端 | 直接运行、已有反向代理或轻量部署 |
| 源码直接运行 | `18045` | Spring Boot 开发实例 | 本地开发 |

Actuator 默认监听 `127.0.0.1:18046`。所有业务端口都可以通过 `SERVER_PORT` 覆盖。

## 本地开发

### 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 16+
- Node.js `^20.19.0` 或 `>=22.12.0`
- pnpm 10.33.0
- Python 3.10+

### 获取代码

```bash
git clone https://github.com/weepwood/MRR.git
cd MRR
git switch main
```

### 数据库

正式迁移位于：

```text
backend-repo/src/main/resources/db/migration
```

迁移命名：

```text
VyyyyMMddHHmmss__description.sql
```

本地可以只启动仓库提供的 PostgreSQL 容器；正式部署不依赖 Docker：

```bash
docker compose up -d postgres
```

### 后端

```powershell
Copy-Item backend-repo/src/main/resources/application-local.template.properties `
  backend-repo/src/main/resources/application-local.properties

$env:SPRING_DATASOURCE_PASSWORD = '本地数据库密码'
$env:JWT_SECRET_KEY = '本地 JWT 密钥'
$env:AES_SECRET_KEY = '本地 AES 密钥'

cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

默认业务端口 `18045`，管理端口 `18046`：

```text
http://127.0.0.1:18046/actuator/health
```

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

开发入口默认是 `http://localhost:9200`，Vite 通过 `/proxy/api/**` 转发到后端。正式构建不使用 `/proxy`。

### 文档

```bash
cd vitepress-doc
npm ci
npm run docs:dev:user
npm run docs:dev:internal
npm run docs:build
```

用户手册和内部文档分别构建，避免内部工程资料进入普通用户搜索索引。

## 生产部署

### Windows 离线包

推荐拓扑：

```text
浏览器
  → Nginx :80
      ├── /、/assets/** → Spring Boot 内嵌前端 :18045
      ├── /api/**       → Spring Boot API :18045
      ├── /docs/**      → 用户手册
      ├── /docs/internal/** → 内部文档
      └── /api-docs/**  → Springdoc

Spring Boot → PostgreSQL / 图片源 / OSS
Actuator    → 127.0.0.1:18046
```

发布包保留外置前端目录，但默认由 Nginx 代理到 JAR 内嵌前端。外置模式只用于受管理回退。

### 单体 JAR

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app'
$env:SPRING_DATASOURCE_USERNAME='mrr_app'
$env:SPRING_DATASOURCE_PASSWORD='数据库密码'
$env:JWT_SECRET_KEY='JWT随机密钥'
$env:AES_SECRET_KEY='AES随机密钥'

java -jar .\MRR-vX.Y.Z-standalone.jar
```

默认访问：

```text
http://localhost:8002
```

单体 JAR不包含数据库、Nginx、WinSW、文档或医院环境配置。

## 项目结构

```text
MRR/
├── VERSION                       # 唯一产品版本
├── release-baseline.json         # 数据库、配置与回滚兼容基线
├── backend-repo/                 # Spring Boot 后端与 Flyway 迁移
├── frontend-fantastic-admin/     # Vue 管理端与影像调阅端
├── vitepress-doc/                # 用户手册与内部文档
├── deploy/windows/               # Windows 部署和服务管理脚本
├── deploy/standalone/            # 单体 JAR说明
├── scripts/                      # 发布、校验、迁移和文档辅助脚本
├── mrr-db/                       # 数据库辅助脚本与参考资料
├── monitoring/                   # 可选扩展监控配置
├── CHANGELOG.md
└── README.md
```

## 验证命令

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/release_baseline.py validate

cd backend-repo
mvn -B -ntp verify

cd ../frontend-fantastic-admin
pnpm lint:tsc
pnpm test:run
pnpm build
pnpm test:e2e

cd ../vitepress-doc
npm run docs:changelog:test
npm run docs:build
```

涉及 PostgreSQL、Nginx、OSS、文件系统、大数据导入或 Windows 服务管理的修改，还应在隔离环境完成真实集成验证。单元测试通过不能替代部署验证。

## 文档入口

- [用户手册](vitepress-doc/user-guide/index.md)
- [日志、审计与运行错误](vitepress-doc/user-guide/logs.md)
- [系统架构](vitepress-doc/internal/architecture.md)
- [配置参考](vitepress-doc/internal/configuration-reference.md)
- [部署总览](vitepress-doc/internal/deployment.md)
- [Windows Server 部署](vitepress-doc/internal/windows-deployment.md)
- [单体 JAR 部署](vitepress-doc/internal/standalone-jar.md)
- [运行错误中心](vitepress-doc/internal/runtime-errors.md)
- [外部系统影像接入](vitepress-doc/internal/external-archive-integration.md)
- [发布流程与版本基线](vitepress-doc/internal/release.md)
- [最新主分支代码审查](vitepress-doc/internal/code-review.md)
- [更新日志](CHANGELOG.md)

## 文档与代码一致性

文档只描述已进入 `main` 的能力。尚未合并的 Issue、PR 或实验分支不视为当前功能。发生冲突时，事实来源优先级为：

1. 当前 `main` 代码与 Flyway 迁移；
2. `application.properties`、`VERSION`、`release-baseline.json`；
3. 自动化测试和运行中的 OpenAPI；
4. 内部文档；
5. 用户手册和历史更新日志。

## 贡献与许可

提交变更前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。项目基于 [MIT License](LICENSE) 发布。
