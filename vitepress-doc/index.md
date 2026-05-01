---
layout: home

hero:
  name: "MRR"
  text: "医疗影像记录管理系统"
  tagline: 基于 Spring Boot 4 + Vue 3 的现代化医疗影像管理解决方案
  image:
    src: /logo.svg
    alt: MRR Logo
  actions:
    - theme: brand
      text: 快速开始
      link: /getting-started/installation
    - theme: alt
      text: 项目概览
      link: /ai-generation/项目概览/项目概览
    - theme: alt
      text: 系统架构
      link: /ai-generation/系统架构/系统架构
    - theme: alt
      text: GitHub
      link: https://github.com/your-repo/mrr

features:
  - icon: 🏥
    title: 病案管理
    details: 完整的患者信息和扫描记录管理系统，支持多种医学影像格式
  - icon: 🖼️
    title: 影像浏览
    details: 在线浏览 DICOM 等医学影像格式，支持缩放、旋转等操作
  - icon: 📊
    title: 统计分析
    details: 丰富的数据统计和分析功能，支持多种图表展示
  - icon: 🔐
    title: 权限管理
    details: 完善的用户认证和授权机制，支持 RBAC 角色权限控制
  - icon: 📝
    title: 日志审计
    details: 完整的操作日志记录和审计追踪功能
  - icon: 🚀
    title: 高性能
    details: 基于 Spring Boot 4 + Vue 3 构建，响应迅速，性能卓越
---

## 📖 文档导航

<div class="doc-nav-grid">

<div>

### 🚀 快速开始
- [安装指南](/getting-started/installation) — 系统安装和部署指南
- [配置说明](/getting-started/configuration) — 详细的配置参数说明

### 📋 项目概览
- [项目概览](/ai-generation/项目概览/项目概览) — 项目整体介绍
- [技术架构概览](/ai-generation/项目概览/技术架构概览) — 技术栈和架构设计
- [核心功能模块](/ai-generation/项目概览/核心功能模块) — 主要功能模块介绍

</div>
<div>

### 🏗️ 系统架构
- [系统架构](/ai-generation/系统架构/系统架构) — 整体架构设计
- [前端架构](/ai-generation/系统架构/前端架构/前端架构) — Vue 3 前端架构
- [后端架构](/ai-generation/系统架构/后端架构/后端架构) — Spring Boot 后端架构
- [数据架构](/ai-generation/系统架构/数据架构/数据架构) — 数据库设计

### 🔌 后端 API
- [API 文档](/ai-generation/后端API文档/后端API文档) — API 文档概览
- [认证授权 API](/ai-generation/后端API文档/认证授权API) — 认证授权接口
- [扫描记录 API](/ai-generation/后端API文档/扫描记录API) — 扫描记录接口

</div>
<div>

### 🛠️ 开发指南
- [开发指南](/ai-generation/开发指南/开发指南) — 开发指南概览
- [代码规范](/ai-generation/开发指南/代码规范) — 编码规范和最佳实践
- [测试策略](/ai-generation/开发指南/测试策略) — 测试方法和策略

### 🔐 认证授权
- [认证授权](/ai-generation/认证授权/认证授权) — 认证授权概览
- [JWT 认证机制](/ai-generation/认证授权/JWT认证机制) — JWT 认证详解
- [权限控制系统](/ai-generation/认证授权/权限控制系统) — 权限管理设计

</div>
<div>

### 👤 用户指南
- [用户指南](/user-guide/index) — 系统使用说明
- [快速上手](/user-guide/getting-started) — 登录与基本操作
- [病案管理](/user-guide/patients) — 患者与扫描记录管理
- [影像浏览](/user-guide/images) — 医学影像在线查看

### 🔧 运维指南
- [运维指南](/maintenance/index) — 日常运维操作手册
- [数据备份与恢复](/maintenance/backup) — 备份策略与操作
- [故障处理](/maintenance/troubleshooting) — 常见问题排查

</div>
</div>

---

## 🛠️ 技术栈

<div class="tech-stack">

<div>

### 后端技术

- **Java 21** — 编程语言
- **Spring Boot 4** — 应用框架
- **MyBatis** — ORM 框架
- **PostgreSQL 16** — 数据库
- **JWT (Auth0)** — 认证机制
- **Maven** — 项目管理

</div>
<div>

### 前端技术

- **Vue 3** — 前端框架
- **TypeScript** — 编程语言
- **Vite** — 构建工具
- **Element Plus** — UI 组件库
- **Pinia** — 状态管理
- **UnoCSS** — 原子化 CSS

</div>
<div>

### DevOps

- **Docker** — 容器化
- **Docker Compose** — 容器编排
- **GitHub Actions** — CI/CD
- **Prometheus** — 监控
- **Spring Actuator** — 健康检查
- **Aliyun OSS** — 对象存储

</div>
</div>

---

## 📸 系统截图

<div class="screenshot-grid">
  <img src="/ai-generation/imgs/v0.0.9_imgs/登录界面.png" alt="登录界面" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/病案管理.png" alt="病案管理" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/统计分析.png" alt="统计分析" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/监控中心.png" alt="监控中心" />
</div>

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- PostgreSQL 16+
- Maven 3.9+

### 安装步骤

```bash
# 1. 克隆项目
git clone https://github.com/your-repo/mrr.git
cd mrr

# 2. 启动数据库
docker compose up -d postgres

# 3. 启动后端
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 4. 启动前端
cd frontend-fantastic-admin
npm install
npm run dev
```

### 访问系统

```
前端地址: http://localhost:9000
后端地址: http://localhost:18045
API 文档: http://localhost:18045/v1/swagger-ui/index.html
默认账号: br_admin / br_password
```

::: warning 注意
生产环境请立即修改默认密码！
:::

---

## 📞 联系我们

- **问题反馈**: [GitHub Issues](https://github.com/your-repo/mrr/issues)
- **功能建议**: [GitHub Discussions](https://github.com/your-repo/mrr/discussions)

## 📄 许可证

本项目基于 [MIT 许可证](https://opensource.org/licenses/MIT) 开源。
