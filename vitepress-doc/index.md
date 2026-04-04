---
layout: home

hero:
  name: "MRR"
  text: "医疗影像记录管理系统"
  tagline: 现代化的医疗影像管理解决方案
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
    details: 完善的用户认证和授权机制，支持角色权限控制
  - icon: 📝
    title: 日志审计
    details: 完整的操作日志记录和审计追踪功能
  - icon: 🚀
    title: 高性能
    details: 基于 Spring Boot 和 Vue 3 构建，响应迅速，性能卓越
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #3eaf7c 30%, #42b883);
  --vp-home-hero-image-background-image: linear-gradient(-45deg, #3eaf7c50 50%, #42b88350 50%);
  --vp-home-hero-image-filter: blur(44px);
}

.VPHero .image-bg {
  transition: transform 1s ease;
}

.VPHero:hover .image-bg {
  transform: scale(1.1);
}
</style>

## 📖 文档导航

<div class="doc-nav-grid">

### 🚀 快速开始

- [安装指南](/getting-started/installation) - 系统安装和部署指南
- [配置说明](/getting-started/configuration) - 详细的配置参数说明

### 📋 项目概览

- [项目概览](/ai-generation/项目概览/项目概览) - 项目整体介绍
- [技术架构概览](/ai-generation/项目概览/技术架构概览) - 技术栈和架构设计
- [核心功能模块](/ai-generation/项目概览/核心功能模块) - 主要功能模块介绍
- [系统特性与优势](/ai-generation/项目概览/系统特性与优势) - 系统特点和优势

### 🏗️ 系统架构

- [系统架构](/ai-generation/系统架构/系统架构) - 整体架构设计
- [前端架构](/ai-generation/系统架构/前端架构/前端架构) - Vue 3 前端架构
- [后端架构](/ai-generation/系统架构/后端架构/后端架构) - Spring Boot 后端架构
- [数据架构](/ai-generation/系统架构/数据架构/数据架构) - 数据库设计

### 🎨 前端组件

- [前端组件](/ai-generation/前端组件/前端组件) - 组件库概览
- [仪表板组件](/ai-generation/前端组件/仪表板组件) - 仪表板相关组件
- [数据表格组件](/ai-generation/前端组件/数据表格组件) - 表格组件
- [表单组件](/ai-generation/前端组件/表单组件) - 表单相关组件

### 🔌 后端 API

- [后端API文档](/ai-generation/后端API文档/后端API文档) - API 文档概览
- [认证授权API](/ai-generation/后端API文档/认证授权API) - 认证授权接口
- [扫描记录API](/ai-generation/后端API文档/扫描记录API) - 扫描记录接口
- [统计分析API](/ai-generation/后端API文档/统计分析API) - 统计分析接口

### 💾 数据库设计

- [数据库设计](/ai-generation/数据库设计/数据库设计) - 数据库设计概览
- [核心业务表](/ai-generation/数据库设计/核心业务表/核心业务表) - 核心业务表设计
- [认证授权表](/ai-generation/数据库设计/认证授权表/认证授权表) - 认证授权表设计

### 🛠️ 开发指南

- [开发指南](/ai-generation/开发指南/开发指南) - 开发指南概览
- [代码规范](/ai-generation/开发指南/代码规范) - 编码规范和最佳实践
- [测试策略](/ai-generation/开发指南/测试策略) - 测试方法和策略
- [性能优化](/ai-generation/开发指南/性能优化) - 性能优化指南

### 🔐 认证授权

- [认证授权](/ai-generation/认证授权/认证授权) - 认证授权概览
- [JWT认证机制](/ai-generation/认证授权/JWT认证机制) - JWT 认证详解
- [权限控制系统](/ai-generation/认证授权/权限控制系统) - 权限管理设计

### 📊 日志审计与监控

- [日志审计与监控](/ai-generation/日志审计与监控/日志审计与监控) - 日志和监控概览
- [日志管理](/ai-generation/日志审计与监控/日志管理) - 日志管理功能
- [监控系统](/ai-generation/日志审计与监控/监控系统) - 系统监控方案

### 🚀 部署运维

- [部署运维](/ai-generation/部署运维/部署运维) - 部署运维概览
- [容器化部署](/ai-generation/部署运维/容器化部署) - Docker 部署方案
- [监控告警](/ai-generation/部署运维/监控告警) - 监控告警配置
- [备份恢复](/ai-generation/部署运维/备份恢复) - 数据备份和恢复

</div>

<style>
.doc-nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
  margin-top: 2rem;
}

.doc-nav-grid h3 {
  margin-top: 0;
  margin-bottom: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid var(--vp-c-divider);
  font-size: 1.1rem;
  font-weight: 600;
}

.doc-nav-grid ul {
  list-style: none;
  padding-left: 0;
  margin: 0;
}

.doc-nav-grid li {
  margin: 0.5rem 0;
  padding-left: 1rem;
  position: relative;
}

.doc-nav-grid li::before {
  content: '→';
  position: absolute;
  left: 0;
  color: var(--vp-c-brand-1);
  font-weight: bold;
}

.doc-nav-grid a {
  color: var(--vp-c-text-1);
  text-decoration: none;
  transition: color 0.2s;
}

.doc-nav-grid a:hover {
  color: var(--vp-c-brand-1);
}
</style>

## 🛠️ 技术栈

### 后端技术

- **Java 21** - 编程语言
- **Spring Boot 4** - 应用框架
- **MyBatis** - ORM 框架
- **PostgreSQL** - 数据库
- **JWT** - 认证机制
- **Maven** - 项目管理

### 前端技术

- **Vue 3** - 前端框架
- **TypeScript** - 编程语言
- **Vite** - 构建工具
- **Element Plus** - UI 组件库
- **Pinia** - 状态管理
- **Vue Router** - 路由管理

### 开发工具

- **IntelliJ IDEA** - 后端 IDE
- **Visual Studio Code** - 前端编辑器
- **Docker** - 容器化
- **Git** - 版本控制

## 📸 系统截图

<div class="screenshot-grid">
  <img src="/ai-generation/imgs/v0.0.9_imgs/登录界面.png" alt="登录界面" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/病案管理.png" alt="病案管理" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/统计分析.png" alt="统计分析" />
  <img src="/ai-generation/imgs/v0.0.9_imgs/监控中心.png" alt="监控中心" />
</div>

<style>
.screenshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
  margin: 2rem 0;
}

.screenshot-grid img {
  width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transition: transform 0.2s, box-shadow 0.2s;
}

.screenshot-grid img:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}
</style>

## 🚀 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- PostgreSQL 14+
- Maven 3.9+

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/your-repo/mrr.git
cd mrr
```

2. **配置数据库**
```bash
# 创建数据库
createdb mrr_db

# 导入初始数据
psql -d mrr_db -f backend-repo/sql/init.sql
```

3. **启动后端**
```bash
cd backend-repo
mvn spring-boot:run
```

4. **启动前端**
```bash
cd frontend-repo
npm install
npm run dev
```

5. **访问系统**
```
前端地址: http://localhost:5173
后端地址: http://localhost:18045
默认账号: admin / admin123
```

## 📞 联系我们

- **问题反馈**: [GitHub Issues](https://github.com/your-repo/mrr/issues)
- **功能建议**: [GitHub Discussions](https://github.com/your-repo/mrr/discussions)

## 📄 许可证

本项目基于 [MIT 许可证](https://opensource.org/licenses/MIT) 开源。
