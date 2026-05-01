# 更新日志

项目版本遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/) 规范。

## [Unreleased]

### 新增
- 用户认证与权限管理（JWT + RBAC）
- 病案 CRUD 功能（患者信息、扫描记录）
- 影像上传与在线浏览
- 多维度统计分析
- 操作日志审计
- 系统监控（Spring Actuator + Prometheus）
- 压力测试工具
- 结构化日志（JSON 格式，支持 ELK）
- CI/CD 流水线（GitHub Actions）
- Docker 容器化部署

### 优化
- 后端分层架构（Controller → Service → Mapper）
- 前端组件化设计（Element Plus + Fantastic Admin）
- 数据库索引与查询优化
- 密码加密存储（BCrypt）
- 敏感数据加密（AES）
- 统一异常处理和响应格式

### 文档
- VitePress 文档系统搭建
- 项目概览、架构设计文档
- API 接口文档
- 数据库设计文档
- 部署运维指南
- 开发指南与代码规范

## [0.1.0] - 2026-04-01

### 新增
- 项目初始化
- 基础框架搭建（Spring Boot 4 + Vue 3）
- 数据库 Schema 设计
- 文档系统初始化
