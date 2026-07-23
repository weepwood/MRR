# MRR 医疗病案文件管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![VitePress](https://img.shields.io/badge/VitePress-1.5-646CFF)](https://vitepress.dev/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

MRR 是面向医疗机构病案扫描、归档、检索、调阅、导出、统计和审计场景的管理系统。系统以病案主档、患者、扫描图片和实体档案位置为核心，提供管理端、影像档案袋、外部系统调阅、权限审计、OSS 迁移、数据治理与 Windows 单机运维能力。

> 当前主分支产品版本为 **0.6.5**。版本号以根目录 [`VERSION`](VERSION) 为唯一事实来源；数据库兼容、配置结构与回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。

## 项目定位

MRR 适用于病案室、档案管理部门、信息科和受控外部业务系统，重点解决以下问题：

- 将患者、病案、扫描记录和图片文件建立稳定关联；
- 在数千万级扫描图片元数据规模下完成检索与导出；
- 同时兼容本地/NAS、Nginx 静态资源和 OSS 图片来源；
- 对病案查看、下载、PDF 导出、修改和管理操作进行权限控制与审计；
- 通过 HMAC 一次性票据向 HIS、EMR 等外部系统提供受控调阅入口；
- 在 Windows Server 内网环境中完成部署、升级、回滚和日常运维。

MRR 不是 DICOM 诊断工作站，不提供窗宽窗位、医学测量、多帧诊断播放或自动医学结论。

## 当前能力

| 领域 | 当前主分支能力 |
| --- | --- |
| 患者与病案 | 患者查询、编辑、CSV/Excel 导入、条件导出、年度/科室/身份证缺失与疑似同人统计 |
| 扫描记录 | 记录查询、病案类型管理、统计明细和大表分页 |
| 影像档案袋 | 按病案号、上架号或身份证查询，分类浏览、全屏预览、选择、打印、ZIP/PDF 导出 |
| 大文件导出 | 后台异步生成 ZIP/PDF，展示进度，支持取消、重新下载、临时文件过期清理和断点下载 |
| 多来源图片 | OSS 优先或本地优先；OSS 缺失时按 Nginx、NAS/HTTP、本地目录降级读取 |
| OSS 迁移 | 从 Nginx 原图迁移到 OSS，按上架号分组，支持等待补齐、失败重试、校验与迁移任务管理 |
| 文件浏览 | 只读 OSS 文件浏览器；只读 Nginx 文件浏览器，支持 default、BA01、BA02、BA03 节点 |
| 外部调阅 | HMAC-SHA256 一次性 Ticket、有效期、nonce、来源 IP 校验和外部用户审计 |
| 权限与账号 | JWT、RBAC、管理员建号、自助注册后管理员审核、首次改密、密码重置和账号状态管理 |
| 审计与监控 | 操作日志、图片访问审计、接口响应分析、Actuator、服务状态历史和数据库诊断 |
| 部署运维 | Windows 原生 JAR + Nginx，离线发布包、校验清单、服务控制脚本和版本基线 |
| 文档 | 登录后用户手册、受权限保护的内部文档和 Springdoc 实时 API 文档 |

## 技术基线

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis 4、Flyway、Springdoc、Micrometer |
| 前端 | Vue 3.5、TypeScript 5.9、Vite 8、Element Plus、Pinia、ECharts |
| 数据库 | PostgreSQL 16，业务 Schema 为 `app` |
| 认证安全 | JWT、bcrypt、AES-GCM、RBAC、HMAC 外部调阅票据 |
| 对象存储 | S3 兼容 OSS，后端使用 SDK 读取和签名 |
| 文档 | VitePress、Mermaid |
| 正式部署 | Windows Server、Spring Boot JAR、Nginx、PostgreSQL |

## 业务规则摘要

### 病案号与上架号

- `bah`、`sjh` 保留数据库原始格式，不自动补零；
- 病案号小于 `10000000` 时可以单独查询；
- 病案号大于或等于 `10000000` 时必须同时提供上架号；
- 非空且唯一的上架号可以单独查询；
- 身份证首次查询可使用 `/archive?id=<身份证号>`，成功后 URL 会替换为不透明令牌。

### 图片降级顺序

图片读取由后端统一解析。具体顺序取决于系统设置和 `archive.image-source.prefer-oss`：

1. 优先来源（OSS 或非 OSS）；
2. 非 OSS 图片优先通过配置的 Nginx 节点读取；
3. 再尝试 NAS/HTTP 节点；
4. 后端可直接访问文件时，最后回退本地目录；
5. 单个病案允许同时存在已迁移和未迁移图片。

### 记录类权限

| 权限 | 当前主分支能力 |
| --- | --- |
| `record:read` | 查看病案和图片；v0.6.5 的图片类型修改接口当前也继承此权限 |
| `record:edit` | 已定义的记录编辑权限，并包含读取；当前主分支尚未将全部写接口切换到该权限 |
| `record:download` | 生成和下载 ZIP，并包含读取 |
| `record:pdf:export` | 生成和下载 PDF，并包含读取 |
| `record:manage` | 包含读取、编辑、下载和 PDF 导出 |

具体接口的最终权限边界以当前后端 Controller、自动化测试和运行中的 OpenAPI 为准，不应仅根据权限名称推断。

## 访问入口

| 入口 | 路由 | 访问要求 |
| --- | --- | --- |
| 管理端 | `/` | 登录及相应业务权限 |
| 影像档案袋 | `/archive` 或 `/archive/embed` | `record:read` |
| 外部影像调阅 | `/archive/external?ticket=...` | 有效的一次性 Ticket |
| Nginx 文件浏览 | `/nginx-browser` | `record:read` |
| OSS 文件浏览 | `/oss-browser` | `record:read` |
| 服务状态 | `/status` | 公开脱敏信息 |
| 系统监控 | `/monitoring` | `system:read` |
| 用户手册 | `/docs/` | 已登录账号 |
| 内部文档 | `/docs/internal/` | 管理员或 `system:read` |
| 实时 API | `/api-docs/` | 管理员或 `system:read` |

## 本地开发

### 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 16+
- Node.js `^20.19.0` 或 `>=22.12.0`
- pnpm 10.33.0
- Python 3.10+（发布基线、清单和文档辅助脚本）

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

迁移文件统一采用：

```text
VyyyyMMddHHmmss__description.sql
```

本地开发可以只启动仓库提供的 PostgreSQL 容器；正式部署不依赖 Docker：

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

默认业务端口为 `18045`，管理端口为仅本机监听的 `18046`：

```text
http://127.0.0.1:18046/actuator/health
http://127.0.0.1:18046/actuator/info
```

### 前端

```bash
cd frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev
```

### 文档

```bash
cd vitepress-doc
npm ci
npm run docs:dev:user
npm run docs:dev:internal
npm run docs:build
```

用户手册和内部文档分别构建，避免内部工程资料进入普通用户的搜索索引。

## 生产部署摘要

正式环境推荐单台 Windows Server 原生部署：

1. PostgreSQL 16 保存业务数据；
2. Spring Boot JAR 监听业务端口 `18045`；
3. Actuator 监听 `127.0.0.1:18046`；
4. Nginx 托管前端、文档和静态图片，并反向代理后端 API；
5. 密钥、数据库密码和 OSS 凭据通过环境变量或外部配置提供；
6. 使用发布包中的 `manifest.json`、`release-baseline.json` 和 `SHA256SUMS` 校验版本与兼容性；
7. 部署前保留数据库备份、配置快照和上一版本发布包。

详细步骤见 [Windows Server 部署](vitepress-doc/internal/windows-deployment.md)。

## 项目结构

```text
MRR/
├── VERSION                       # 唯一产品版本
├── release-baseline.json         # 数据库、配置与回滚兼容基线
├── backend-repo/                 # Spring Boot 后端与 Flyway 迁移
├── frontend-fantastic-admin/     # Vue 管理端与影像调阅端
├── vitepress-doc/                # 用户手册与内部文档
├── deploy/windows/               # Windows 部署和服务管理脚本
├── scripts/                      # 发布、校验、迁移和文档辅助脚本
├── mrr-db/                       # 数据库辅助脚本与参考资料
├── monitoring/                   # 可选扩展监控配置
├── CHANGELOG.md
└── README.md
```

## 验证命令

```bash
python scripts/release_baseline.py validate

cd backend-repo
mvn test
mvn package
```
