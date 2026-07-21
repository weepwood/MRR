---
layout: home

hero:
  name: "MRR"
  text: "内部工程文档"
  tagline: 医疗病案文件记录管理系统的架构、开发、部署、运维与安全规范
  image:
    src: /logo.svg
    alt: MRR Logo
  actions:
    - theme: brand
      text: 内部文档
      link: /internal/
    - theme: alt
      text: 系统架构
      link: /internal/architecture
    - theme: alt
      text: 发布基线
      link: /internal/release
    - theme: alt
      text: 用户手册
      link: /user-guide/

features:
  - icon: 🧭
    title: 唯一事实来源
    details: 产品版本读取根目录 VERSION，发布兼容边界读取 release-baseline.json，文档不再独立维护版本事实。
  - icon: 🏗️
    title: 清晰的系统边界
    details: 说明单前端、单后端、PostgreSQL、本地或 OSS 图片存储与 Windows 部署组件的职责。
  - icon: 🧩
    title: 工程规范
    details: 统一前端组件、ECharts、后端分层、权限、数据库迁移、测试和发布要求。
  - icon: 🚀
    title: 原生部署
    details: 正式环境使用 JAR、Nginx 和 Windows 服务，不依赖 Docker 或额外运维平台。
  - icon: 📈
    title: 内置运维能力
    details: 覆盖 Actuator、数据库诊断、结构化日志、审计、备份和服务可用性历史。
  - icon: 🔐
    title: 安全与隐私
    details: 约束密钥、身份证、病案信息、外部调阅、文档访问、日志和运维网络边界。
---

## 文档定位

本内部站点面向开发、测试、部署与运维人员。站点标题和导航中的产品版本由仓库根目录 `VERSION` 在构建时自动生成；数据库兼容和回滚结论以 `release-baseline.json` 为准。旧 `ai-generation` 文档已从内部站点构建与搜索索引中排除，不再作为事实来源。

::: warning 产品边界
MRR 是医疗病案文件记录管理系统，不是通用 DICOM 诊断工作站。当前影像档案袋面向浏览器图片查看、选择、打印和前端 PDF 导出，不提供窗宽窗位、医学测量和多帧诊断播放。
:::

## 文档入口

| 主题 | 入口 | 解决的问题 |
|------|------|------------|
| 全局认识 | [内部文档首页](/internal/) | 文档范围、运行组成和维护原则 |
| 系统设计 | [系统架构](/internal/architecture) | 各组件如何协作、系统边界是什么 |
| 前端 | [前端工程](/internal/frontend) | 路由、状态、样式、图表和档案袋实现 |
| 后端 | [后端工程](/internal/backend) | 分层、认证、配置、日志和后台能力 |
| 数据 | [数据库](/internal/database) | 表、字段规范、日期时间迁移链、索引和备份 |
| 联调 | [API 与权限](/internal/api) | 接口分组、认证、RBAC 和 OpenAPI |
| 外部接入 | [外部系统影像接入](/internal/external-archive-integration) | HIS/EMR 如何安全调阅影像档案 |
| 开发 | [开发流程](/internal/development) | 环境、命令、检查、测试和 PR 规范 |
| 上线 | [部署](/internal/deployment) | JAR、Nginx、文档、图片服务和升级 |
| 运维 | [运维与监控](/internal/operations) | 指标、审计、数据质量、状态和巡检 |
| 安全 | [安全](/internal/security) | 密钥、隐私、权限、网络与日志边界 |
| 排错 | [故障排查](/internal/troubleshooting) | 常见启动、端口、数据库和图片问题 |
| 发布 | [发布流程与版本基线](/internal/release) | 唯一版本、构建身份、兼容矩阵和回滚判定 |

## 当前技术基线

| 层次 | 当前实现 |
|------|----------|
| 前端 | Vue 3.5、TypeScript 5.9、Vite 8、Element Plus、Pinia、ECharts 6 |
| 后端 | Java 21、Spring Boot 4.0.5、MyBatis 4、Flyway、Springdoc |
| 数据库 | PostgreSQL 16，业务 Schema 为 `app`，正式迁移使用日期时间版本链 |
| 文档 | VitePress 1.5、Mermaid，用户与内部站点独立构建但共用产品版本 |
| 部署 | Windows Server、Nginx、单后端 JAR、静态前端和文档 |
| 发布 | `VERSION`、`release-baseline.json`、构建 manifest 和 SHA256 清单 |

## 本地启动

```bash
# 先校验版本与迁移基线
python scripts/release_baseline.py validate

# 后端
cd backend-repo
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd ../frontend-fantastic-admin
corepack pnpm@10.33.0 install --frozen-lockfile
pnpm dev

# 内部文档
cd ../vitepress-doc
npm ci
npm run docs:dev:internal -- --port 5310
```

如果 `5310` 位于 Windows 端口排除范围，启动脚本会自动选择后续可用端口并打印实际地址。

## 文档维护规则

- 页面、路由、接口、配置和数据库变更应在同一 PR 更新文档；
- 产品版本不得在文档正文中写死，必须由 `VERSION` 构建注入或明确引用该文件；
- 新增正式 Flyway 迁移时必须同步更新 `release-baseline.json` 并重新判断回滚兼容性；
- API 字段以运行中的 Springdoc 为准，不复制容易过期的完整静态接口清单；
- 用户手册只写实际可见功能，内部文档可以说明限制、风险和运维流程；
- 不确定的实现不得写成已经支持的功能。
