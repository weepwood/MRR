# MRR 医疗影像记录管理系统

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3.4-4FC08D)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

> 基于 Spring Boot 4 + Vue 3 的现代化医疗影像管理系统，提供病案管理、影像浏览、统计分析、权限控制等完整功能。

## 📋 项目概览

MRR 是一套面向医疗机构的影像记录管理系统，核心功能包括：

| 模块 | 说明 |
|------|------|
| 病案管理 | 患者信息与扫描记录全生命周期管理 |
| 影像浏览 | 在线浏览 DICOM 等医学影像格式 |
| 统计分析 | 多维度数据统计与趋势分析 |
| 权限控制 | 基于 RBAC 的细粒度权限管理 |
| 日志审计 | 操作日志记录与审计追踪 |

## 🏗️ 技术栈

| 层次 | 技术 |
|------|------|
| **前端** | Vue 3 + TypeScript + Vite + Element Plus + Pinia |
| **后端** | Java 21 + Spring Boot 4 + MyBatis |
| **数据库** | PostgreSQL 16 |
| **认证** | JWT + AES |
| **部署** | Docker + Docker Compose |

## 🚀 快速开始

### 环境要求
- JDK 21+
- Node.js 22 (see `frontend-fantastic-admin/.node-version`)
- PostgreSQL 16+
- Maven 3.9+

### 启动（开发模式）

```bash
# 1. 克隆项目
git clone <repo-url>
cd MRR

# 2. 启动数据库
docker compose up -d postgres

# 3. 启动后端
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. 启动前端
cd frontend-fantastic-admin
pnpm install --frozen-lockfile
pnpm dev
```

### 访问系统
```
前端地址: http://localhost:9000
后端接口: http://localhost:18045
默认账号: br_admin / br_password
```

> 首次使用请参考 [完整安装指南](vitepress-doc/getting-started/installation.md)。

### 启动完整容器环境

```bash
cp .env.example .env # Windows PowerShell: Copy-Item .env.example .env
# Set strong values for POSTGRES_PASSWORD, AES_SECRET_KEY, and JWT_SECRET_KEY in .env.
docker compose up --build
```

## 📚 文档

完整文档基于 VitePress 构建，涵盖架构设计、API 接口、部署运维等全部内容。

```bash
cd vitepress-doc
npm install
npm run docs:dev
```

- **在线文档**: 启动后访问 http://localhost:5173
- **文档目录**: `vitepress-doc/`

| 文档模块 | 说明 |
|----------|------|
| [项目概览](vitepress-doc/ai-generation/项目概览/项目概览.md) | 系统目标、特性、技术架构 |
| [安装指南](vitepress-doc/getting-started/installation.md) | 环境搭建与部署 |
| [配置说明](vitepress-doc/getting-started/configuration.md) | 后端/前端配置参数 |
| [系统架构](vitepress-doc/ai-generation/系统架构/系统架构.md) | 前后端架构、数据架构 |
| [API 文档](vitepress-doc/ai-generation/后端API文档/后端API文档.md) | RESTful API 接口说明 |
| [数据库设计](vitepress-doc/ai-generation/数据库设计/数据库设计.md) | 表结构、索引优化 |
| [开发指南](vitepress-doc/ai-generation/开发指南/开发指南.md) | 代码规范、测试策略 |
| [部署运维](vitepress-doc/ai-generation/部署运维/部署运维.md) | Docker 部署、CI/CD、监控 |

## 📁 项目结构

```
MRR/
├── backend-repo/              # 后端 Spring Boot 项目
│   ├── src/
│   ├── Dockerfile
│   ├── pom.xml
│   └── ENGINEERING_GUIDE.md
├── frontend-fantastic-admin/  # 前端 Vue 3 项目
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── mrr-db/                    # 数据库脚本
│   ├── schema.sql
│   └── migration_*.sql
├── vitepress-doc/             # 文档系统
│   ├── .vitepress/
│   ├── getting-started/
│   ├── ai-generation/
│   └── package.json
├── docker-compose.yml         # Docker 编排
├── start-docs.bat             # 文档启动脚本 (Windows)
└── start-docs.sh              # 文档启动脚本 (Linux/macOS)
```

## 📊 项目状态

当前版本: `v0.1.0` (开发阶段)

- [x] 用户认证与权限管理
- [x] 病案 CRUD
- [x] 影像上传与浏览
- [x] 统计分析
- [x] 日志审计
- [ ] 分布式部署
- [ ] 国际化支持
- [ ] 移动端适配

## 🤝 贡献

欢迎贡献代码！请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详情。

## 📄 许可证

本项目基于 MIT 许可证开源。详见 [LICENSE](LICENSE) 文件。
