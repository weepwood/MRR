# MRR 医疗影像记录管理系统 — 产品需求文档 (PRD)

| 文档版本 | 日期 | 作者 | 变更说明 |
|----------|------|------|----------|
| v1.0 | 2026-05-01 | MRR Team | 初始版本 |

---

## 1. 执行摘要

### 1.1 产品定位

MRR（Medical Record Repository）是一套面向医疗机构的影像记录管理系统，提供从患者信息管理、医学影像采集存储、在线浏览到统计分析的全链路解决方案。

### 1.2 核心价值

| 价值 | 说明 |
|------|------|
| **降本增效** | 数字化管理替代纸质档案，检索效率提升 80% |
| **合规安全** | 符合医疗数据管理规范，AES 加密 + JWT 认证 + 完整审计日志 |
| **数据驱动** | 多维度统计分析，支持管理决策 |
| **弹性扩展** | 本地存储 + 云端 OSS 双模架构，适配不同规模机构 |

### 1.3 项目状态

当前版本 `v0.0.7-SNAPSHOT`，处于功能完善阶段。

---

## 2. 产品概述

### 2.1 产品愿景

"让医学影像管理更简单、更安全、更合规"——构建覆盖影像采集、存储、检索、分析、归档全生命周期的智能管理平台。

### 2.2 目标用户

| 用户角色 | 描述 | 核心场景 |
|----------|------|----------|
| 系统管理员 (ADMIN) | IT 运维人员 | 用户管理、权限分配、系统配置、日志审计 |
| 医生 (DOCTOR) | 临床医生 | 病案检索、影像浏览、统计分析 |
| 护士 (NURSE) | 护理人员 | 病案录入、扫描记录管理 |
| 查看者 | 外部审核人员 | 只读访问，查看病案与影像 |

### 2.3 核心业务流程

```
患者就诊 → 身份登记 → 影像采集 → 扫描上传 
    → 在线浏览/诊断 → 统计归档 → 长期存储 (OSS)
```

---

## 3. 功能需求

### 3.1 功能全景图

```
MRR 功能模块
├── 🔐 认证与权限管理
│   ├── 用户登录/登出 (JWT)
│   ├── Token 刷新
│   ├── 角色管理 (RBAC)
│   ├── 用户 CRUD
│   └── 密码修改
├── 📋 病案管理
│   ├── 患者信息登记
│   ├── 扫描记录 CRUD
│   ├── 条件查询与分页
│   ├── 软删除
│   └── 批量下载 ZIP
├── 🖼️ 影像浏览与管理
│   ├── 影像按 BAH 查看
│   ├── 单张影像流式加载
│   ├── 影像类型标记 (0-14)
│   ├── BAH 级 ZIP 打包下载
│   └── OSS 代理访问
├── 🔍 搜索与检索
│   ├── 身份证号加密检索
│   ├── 加密 ID 解密查询 (AES/CBC)
│   └── 明文 ID 查询
├── 📊 统计分析
│   ├── 综合仪表盘 (概览/趋势/Top BAH)
│   ├── 按日期汇总
│   ├── 按 BAH 汇总
│   ├── 按类型汇总
│   └── 多条件筛选
├── 📝 日志审计
│   ├── 访问日志记录 (异步)
│   ├── 多条件检索
│   ├── CSV 导出
│   ├── 保留策略管理
│   └── 定时清理
├── ☁️ OSS 云端迁移
│   ├── 单条/批量上传至 OSS
│   ├── 预签名 URL 生成
│   ├── 迁移状态追踪
│   └── 迁移统计看板
├── 📈 系统监控
│   ├── 应用信息/健康检查
│   ├── JVM 内存监控
│   ├── 运行时信息
│   └── 压力测试工具
└── ⚙️ 系统管理
    ├── 系统设置
    ├── 缓存管理 (Caffeine)
    └── Swagger API 文档
```

### 3.2 详细功能说明

#### FR-1 认证与权限管理

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-1.1 | 用户登录 | P0 | 用户名+密码登录，返回 JWT Token，24 小时过期 |
| FR-1.2 | 获取当前用户 | P0 | 返回当前登录用户信息及权限列表 |
| FR-1.3 | 用户列表 | P0 | 分页查询所有用户 |
| FR-1.4 | 角色列表 | P0 | 查询所有角色定义 |
| FR-1.5 | 更新用户 | P1 | 修改用户显示名、角色、状态 |
| FR-1.6 | 禁用用户 | P1 | 软禁用账号，不可删除 |
| FR-1.7 | 修改密码 | P1 | 登录后修改个人密码 |
| FR-1.8 | 权限注解 | P0 | 基于 `@RequirePermissions` 注解的接口级权限控制 |

**角色权限矩阵：**

| 权限 | ADMIN | DOCTOR | NURSE |
|------|-------|--------|-------|
| user:manage | ✅ | - | - |
| role:read | ✅ | - | - |
| role:manage | ✅ | - | - |
| record:read | ✅ | ✅ | ✅ |
| record:manage | ✅ | ✅ | - |
| record:edit | ✅ | ✅ | - |
| search:read | ✅ | ✅ | ✅ |
| statistics:read | ✅ | ✅ | - |
| log:read | ✅ | - | - |
| system:read | ✅ | - | - |

#### FR-2 病案管理

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-2.1 | 创建扫描记录 | P0 | 录入 BAH、BRXH、文件名、类型、页数等 |
| FR-2.2 | 删除扫描记录 | P0 | 软删除，逻辑标记 |
| FR-2.3 | 更新扫描记录 | P0 | 修改扫描记录属性 |
| FR-2.4 | 查询全部记录 | P1 | 无条件全量查询 |
| FR-2.5 | 按 BAH 查询 | P0 | 以住院号检索关联记录 |
| FR-2.6 | 按 BRXH 查询 | P1 | 以患者序号检索 |
| FR-2.7 | 分页条件查询 | P0 | 支持多字段组合筛选 + 分页 |
| FR-2.8 | 条件查询(无分页) | P1 | 无条件限制的全量条件查询 |
| FR-2.9 | 批量下载 ZIP | P0 | 选定记录打包为 ZIP |

#### FR-3 影像浏览

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-3.1 | 按 BAH 查看影像 | P0 | 获取某住院号下所有影像列表 |
| FR-3.2 | 下载 BAH 压缩包 | P0 | 整包下载 |
| FR-3.3 | 单张影像查看 | P0 | `{BAH}/{BRXH}/{FOLDER}/{FILENAME}` 路径访问 |
| FR-3.4 | 更新影像类型 | P1 | 修改影像分类标记 (0-14) |
| FR-3.5 | OSS 影像代理 | P1 | 通过后端代理 `302` 跳转至 OSS |
| FR-3.6 | 影像类型枚举 | P1 | 身份证、报告单、化验单、病理报告、CT、MRI、超声等 15 类 |

#### FR-4 搜索检索

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-4.1 | 加密 ID 查询 BAH | P0 | AES/CBC 加密身份证号 → 解密 → 查 BAH |
| FR-4.2 | 加密 ID 查询(无时间戳) | P0 | 兼容旧版加密格式 |
| FR-4.3 | 明文 ID 查询 (GET) | P1 | 直接传入身份证号查询 (Deprecated) |
| FR-4.4 | 明文 ID 查询 (POST) | P1 | POST 方式明文查询 |

#### FR-5 统计分析

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-5.1 | 综合仪表盘 | P0 | 概览统计、近期趋势、Top BAH |
| FR-5.2 | 全量统计查询 | P0 | 分页 + 关键字/类型/日期筛选 |
| FR-5.3 | 统计概览 | P0 | 总记录数、总页数、唯一 BAH 数、按类型分布 |
| FR-5.4 | 按 BAH 查询 | P0 | 指定住院号的统计明细 |
| FR-5.5 | 按日期查询 | P0 | 指定日期的统计明细 |
| FR-5.6 | BAH 汇总 | P1 | 每 BAH 的记录数和页数 |
| FR-5.7 | 日期汇总 | P1 | 每日的记录数和页数趋势 |
| FR-5.8 | 日期汇总(条件) | P1 | 按日期范围+类型筛选 |
| FR-5.9 | 类型汇总 | P1 | 各类型的记录数和页数 |

#### FR-6 日志审计

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-6.1 | 异步日志记录 | P0 | 拦截器采集请求信息 → 异步写入 |
| FR-6.2 | 日志搜索 | P0 | 多条件过滤 (IP、URI、方法、状态码、时间范围) |
| FR-6.3 | 日志详情 | P1 | 按 ID 查看单条日志 |
| FR-6.4 | CSV 导出 | P1 | 搜索结果的 CSV 导出 |
| FR-6.5 | 保留策略清理 | P1 | 手动触发清理过期日志 |
| FR-6.6 | 定时自动清理 | P1 | Cron 表达式 `0 30 2 * * ?`，每日自动清理 |
| FR-6.7 | MDC 上下文 | P0 | 每条日志携带 requestId、clientIp、userId、userRole |

#### FR-7 OSS 云端迁移

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-7.1 | 按扫描 ID 上传 | P1 | 选定记录上传至阿里云 OSS |
| FR-7.2 | 按 BAH 上传 | P1 | 某住院号下所有记录上传 |
| FR-7.3 | 预签名 URL | P2 | 生成临时访问链接 (3600s 过期) |
| FR-7.4 | 迁移统计 | P1 | 总/已迁移/待迁移/失败数 + 完成百分比 |
| FR-7.5 | 待迁移列表 | P1 | 查询未迁移记录 |
| FR-7.6 | 迁移日志 | P1 | 分页查看迁移历史 |
| FR-7.7 | 删除 OSS 文件 | P2 | 按 OSS Key 删除云端文件 |

#### FR-8 系统监控

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-8.1 | 应用信息 | P1 | 应用名、版本、Spring Profiles、时区 |
| FR-8.2 | JVM 内存 | P1 | 堆/非堆内存使用详情 |
| FR-8.3 | 运行时信息 | P1 | 启动时间、运行时长、JVM 参数 |
| FR-8.4 | 健康检查 | P0 | 数据库 + 磁盘空间状态 |
| FR-8.5 | 系统属性 | P1 | Java 系统属性列表 |
| FR-8.6 | 综合概览 | P1 | 所有信息合并返回 |
| FR-8.7 | Prometheus 指标 | P1 | `/actuator/prometheus` 暴露指标 |

#### FR-9 压力测试

| ID | 功能 | 优先级 | 说明 |
|----|------|--------|------|
| FR-9.1 | 执行压测 | P2 | 自定义并发数、请求数、目标 URL |
| FR-9.2 | 查看历史 | P2 | 历史压测报告列表 |
| FR-9.3 | 最新结果 | P2 | 最近一次压测报告 |
| FR-9.4 | 按 ID 查看 | P2 | 指定 runId 的报告 |
| FR-9.5 | 清除历史 | P2 | 删除所有历史记录 |

**压测报告字段：** runId, targetUrl, concurrency, totalRequests, successfulRequests, failedRequests, successRate, averageResponseTime, min/max/p95 latency, requestsPerSecond, durationMillis, startTime/endTime, memorySnapshots

---

## 4. 非功能需求

### 4.1 性能指标

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| API 平均响应时间 | ≤ 200ms | Actuator Metrics |
| API P95 响应时间 | ≤ 500ms | 压测报告 |
| 并发用户数 | ≥ 50 | 压测工具 |
| 请求吞吐量 | ≥ 100 req/s | 压测报告 |
| 登录响应时间 | ≤ 1s | Actuator Metrics |
| 影像加载(100KB) | ≤ 500ms | 前端感知 |
| 批量下载(100 张) | ≤ 5s | 功能测试 |
| 日志清理(10 万条) | ≤ 30s | 定时任务监控 |

### 4.2 安全需求

| 类别 | 要求 |
|------|------|
| 认证 | JWT Token，HMAC256 签名，24h 过期 |
| 密码存储 | SHA-256 哈希，非明文存储 |
| 敏感数据 | AES/CBC 加密，每用户独立 IV |
| 权限控制 | 基于 RBAC 的接口级 `@RequirePermissions` |
| 请求过滤 | CORS 白名单 + 登录/权限拦截器链 |
| 防篡改 | 加密 ID 附带时间戳校验 |
| 日志安全 | 敏感信息脱敏，完整审计追踪 |
| 依赖安全 | OWASP 依赖扫描 (CI/CD) |

### 4.3 可用性需求

- 服务可用性: ≥ 99.9% (生产环境)
- 计划内维护窗口: 每月第 2 个周六 02:00-06:00
- 数据库备份: 每日全量 + WAL 归档
- 灾备恢复: RTO ≤ 4h, RPO ≤ 24h

### 4.4 可维护性需求

- 配置外部化: `application-{profile}.properties` + 环境变量覆写
- 日志结构化: 生产环境 JSON 格式，支持 ELK
- 健康检查: Spring Actuator + Prometheus
- 容器化: Docker 多阶段构建 + Docker Compose 编排
- CI/CD: GitHub Actions (lint → build → test → release)

### 4.5 兼容性需求

| 领域 | 要求 |
|------|------|
| 浏览器 | Chrome 90+, Firefox 88+, Edge 90+ , Safari 14+ |
| 分辨率 | 1280×720 以上，支持响应式布局 |
| JDK | 21+ |
| PostgreSQL | 15+ |
| Node.js | 18+ (构建), 20+ (推荐) |

---

## 5. 技术架构

### 5.1 系统架构图

```
┌──────────────────────┐     ┌──────────────────────┐
│   Frontend (Vue 3)   │     │   Nginx (Production) │
│   Port: 9000 (Dev)   │     │   Port: 80           │
│   Vite + Element Plus│     │   Static Files + SSL │
└──────────┬───────────┘     └──────────┬────────────┘
           │ HTTP/HTTPS                 │
           ▼                            ▼
┌──────────────────────────────────────────────────────────┐
│              Backend (Spring Boot 4)                      │
│  Port: 18045                                              │
│                                                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │   Controller │ │  Interceptor │ │  Async Service   │  │
│  │     Layer    │ │   Chain      │ │   (Logging)      │  │
│  └──────┬───────┘ └──────────────┘ └──────────────────┘  │
│         │                                                 │
│  ┌──────▼───────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │  Service     │ │  Scheduler   │ │  Monitoring      │  │
│  │  Layer       │ │  (Cleanup)   │ │  (Pressure Test) │  │
│  └──────┬───────┘ └──────────────┘ └──────────────────┘  │
│         │                                                 │
│  ┌──────▼───────┐                                         │
│  │  Mapper      │  MyBatis + XML                          │
│  │  Layer       │                                         │
│  └──────────────┘                                         │
└──────────────────────────────────────────────────────────┘
           │ JDBC
           ▼
┌──────────────────────┐     ┌──────────────────────┐
│   PostgreSQL 16      │     │   Aliyun OSS (S3)    │
│   Schema: app        │     │   Medical Records    │
│   7 Tables + Views   │     │   Bucket             │
└──────────────────────┘     └──────────────────────┘
```

### 5.2 技术选型

| 层次 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| 前端框架 | Vue 3 | 3.5.x | Composition API, 生态成熟 |
| 构建工具 | Vite | 8.x | 极速 HMR, ESM 原生 |
| UI 组件 | Element Plus | 2.13.x | 企业级组件库, 中后台首选 |
| 状态管理 | Pinia | 3.x | TypeScript 友好, 轻量 |
| 路由 | Vue Router | 5.x | SPA 路由, 导航守卫 |
| HTTP | Axios | 1.14.x | 拦截器机制, 请求重试 |
| CSS | UnoCSS | 66.x | 按需生成, 零运行时 |
| 后端框架 | Spring Boot | 4.0.5 | 快速开发, 生态完善 |
| ORM | MyBatis | 4.0.1 | 灵活 SQL, 性能可控 |
| 数据库 | PostgreSQL | 16 | 功能丰富, 性能优异 |
| 认证 | JWT (Auth0) | 4.5.1 | 无状态, 分布式友好 |
| 加密 | AES/CBC | JDK 内置 | 国家标准支持 |
| 缓存 | Caffeine | Spring Boot | 高性能本地缓存 |
| 监控 | Actuator + Prometheus | - | 生产级可观测性 |
| API 文档 | SpringDoc (Swagger) | 3.0.2 | OpenAPI 3.0 规范 |
| 容器 | Docker | 24+ | 环境一致性 |
| 编排 | Docker Compose | 2.x | 单机多容器部署 |
| CI/CD | GitHub Actions | - | 仓库集成, 免费额度 |
| 对象存储 | Aliyun OSS (S3 兼容) | - | 低成本海量存储 |

---

## 6. 数据模型

### 6.1 实体关系图

```
mr_auth_role (1) ────── (N) mr_auth_user
                                    │
mr_patient (1) ────── (N) mr_scan ──┤
                                    │
mr_statistics ──────── (N) mr_scan ──┤
                                    │
image_migration_log ── (N) mr_scan ──┘

access_log (独立审计表)
mr_user (遗留用户表)
```

### 6.2 核心表结构

#### `app.mr_scan` — 扫描记录表（核心业务表）

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| brxh | VARCHAR(100) | 患者序号 |
| bah | VARCHAR(100) | 住院号 (病案号) |
| filename | VARCHAR(255) | 文件名 |
| btype | INTEGER | 影像类型 (0-14) |
| pages | INTEGER | 页数 |
| opener_no | VARCHAR(100) | 开单人编号 |
| upload_date | TIMESTAMP | 上传日期 |
| upload_flag | VARCHAR(10) | 上传标记 |
| folder | VARCHAR(255) | 存储目录 |
| oss_url | TEXT | OSS 存储 URL (迁移后) |
| migration_status | VARCHAR(20) | 迁移状态 |
| migrated_at | TIMESTAMP | 迁移时间 |
| file_size | BIGINT | 文件大小 (字节) |
| checksum_md5 | VARCHAR(32) | MD5 校验值 |

索引: `idx_bah`, `idx_brxh`, `idx_migration_status`, `idx_mr_scan_oss_url`

#### `app.mr_patient` — 患者表

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| idcard | VARCHAR(18) | 身份证号 (加密存储) |
| bah | VARCHAR(100) | 住院号 |
| name | VARCHAR(100) | 姓名 |
| admissiontime | TIMESTAMP | 入院时间 |
| department | VARCHAR(100) | 科室 |

#### `app.mr_auth_user` — 认证用户表

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| username | VARCHAR(50) UNIQUE | 登录名 |
| display_name | VARCHAR(100) | 显示名 |
| password_hash | VARCHAR(64) | SHA-256 哈希 |
| role_code | VARCHAR(20) FK | 角色编码 → `mr_auth_role.code` |
| status | VARCHAR(20) | 状态: active / disabled |
| last_login_at | TIMESTAMP | 最后登录时间 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### `app.mr_auth_role` — 角色表

| 列名 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(20) PK | 角色编码 |
| name | VARCHAR(100) | 角色名称 |
| description | TEXT | 角色描述 |
| permissions | TEXT | 权限列表 (逗号分隔) |
| sort_order | INTEGER | 排序号 |

#### `app.access_log` — 访问日志表

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| client_ip | VARCHAR(45) | 客户端 IP |
| request_uri | VARCHAR(500) | 请求 URI |
| method | VARCHAR(10) | HTTP 方法 |
| user_agent | TEXT | User-Agent |
| access_time | TIMESTAMP | 访问时间 |
| query_string | TEXT | 查询参数 |
| request_body | TEXT | 请求体 |
| response_status | INTEGER | 响应状态码 |
| execute_time | BIGINT | 执行耗时 (ms) |
| referer | TEXT | 来源页 |

索引: `idx_access_time`, `idx_client_ip`, `idx_request_uri`, `idx_method`, `idx_response_status`

#### `app.image_migration_log` — OSS 迁移日志

| 列名 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 主键 |
| scan_id | BIGINT FK | 扫描记录 ID |
| local_path | TEXT | 本地路径 |
| oss_url | TEXT | OSS 地址 |
| migration_status | VARCHAR(20) | 状态: pending/success/failed |
| error_message | TEXT | 错误信息 |
| file_size | BIGINT | 文件大小 |
| checksum_md5 | VARCHAR(32) | MD5 校验 |
| migrated_at | TIMESTAMP | 迁移时间 |
| verified_at | TIMESTAMP | 验证时间 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

#### `app.mr_statistics` — 统计表

| 列名 | 类型 | 说明 |
|------|------|------|
| bah | VARCHAR(100) | 住院号 |
| cid | VARCHAR(100) | 关联 ID |
| opener_no | VARCHAR(100) | 开单人 |
| date | DATE | 统计日期 |
| type | VARCHAR(50) | 统计类型 |
| pages | INTEGER | 页数 |

---

## 7. API 设计

### 7.1 通用规范

| 规范 | 说明 |
|------|------|
| Base URL | `http://localhost:18045/api/v1` (开发) |
| 响应格式 | `{ code, message, data, timestamp }` |
| 成功码 | `200` |
| 业务错误 | `400` (参数错误), `401` (未认证), `403` (无权限), `404` (未找到), `500` (服务器错误) |
| 认证方式 | `Authorization: Bearer <token>` |
| 分页参数 | `pageNum`, `pageSize` (默认 1, 20) |
| 分页响应 | `{ list[], total, pageNum, pageSize }` |

### 7.2 接口清单

| 模块 | 接口数 | 基础路径 |
|------|--------|----------|
| 认证授权 | 7 | `/auth` |
| 扫描记录 | 9 | `/scan` |
| 影像管理 | 6 | `/img` |
| 搜索检索 | 5 | `/search` |
| 统计分析 | 9 | `/statistics` |
| 日志审计 | 5 | `/logs` |
| OSS 迁移 | 7 | `/oss` |
| 系统监控 | 6 | `/system` |
| 压力测试 | 5 | `/monitoring/pressure-tests` |
| Actuator | 4 | `/actuator/*` |

---

## 8. 部署与运维

### 8.1 部署架构

```
开发环境:     单机 Docker Compose (Postgres + Backend + Frontend)
测试环境:     Docker Compose + 独立数据库
生产环境:     Nginx 反向代理 + Spring Boot 集群 + PostgreSQL 主从 + OSS
```

### 8.2 环境要求

| 资源 | 开发 | 生产 |
|------|------|------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 20GB | 100GB+ (SSD 推荐) |
| JDK | 21+ | 21+ (Eclipse Temurin) |
| PostgreSQL | 16 | 16+ |
| Node.js | 18+ | 仅构建时需要 |

### 8.3 CI/CD 流水线

```
代码提交 → GitHub Actions
├── Frontend Gate: lint → build
├── Backend Gate: compile → test → package
└── Tag 推送: build → test → Docker 构建 → GitHub Release
```

### 8.4 运维要点

| 类别 | 频率 | 工具/方式 |
|------|------|----------|
| 数据库备份 | 每日 | pg_dump, 保留 7 天 |
| 日志清理 | 每日 (02:30) | 内置定时任务, 默认保留 3 年 |
| 临时文件清理 | 每日 | TempZipCleaner |
| 性能监控 | 实时 | Spring Actuator + Prometheus |
| 依赖安全扫描 | 每次构建 | OWASP Dependency Check |
| 压力测试 | 按需 | 内置 PressureTestService |

---

## 9. 路线图

### 9.1 当前阶段 (v0.1.x)

- [x] 认证授权与 RBAC
- [x] 扫描记录 CRUD
- [x] 影像浏览与下载
- [x] 加密 ID 检索
- [x] 统计分析
- [x] 异步日志审计
- [x] 系统监控
- [x] 压力测试工具
- [x] OSS 云端迁移

### 9.2 近期规划 (v0.2.x)

- [ ] DICOM 医学影像原生支持
- [ ] 影像在线标注与测量工具
- [ ] 批量导入导出 (Excel/CSV)
- [ ] 前端响应式优化与移动端适配
- [ ] 国际化支持 (i18n)
- [ ] 数据库迁移工具 (Flyway)
- [ ] 分布式追踪 (OpenTelemetry + Jaeger)

### 9.3 远期规划 (v0.3.x+)

- [ ] AI 辅助诊断 (影像识别/分类)
- [ ] 多云存储支持 (AWS S3, 腾讯 COS)
- [ ] WebSocket 实时推送
- [ ] 电子病历集成接口 (HL7/FHIR)
- [ ] 报表自定义引擎
- [ ] 多租户 (SaaS 化)
- [ ] 审计合规报表自动生成

---

## 10. 附录

### 10.1 术语表

| 术语 | 说明 |
|------|------|
| BAH | 病案号/住院号，患者每次住院的唯一标识 |
| BRXH | 患者序号，标识住院期间的单次检查 |
| btype | 影像类型编码 (0-14)，如身份证、CT、MRI 等 |
| OSS | 对象存储服务 (Object Storage Service) |
| RBAC | 基于角色的访问控制 (Role-Based Access Control) |
| AES/CBC | 高级加密标准/密码块链接模式 |
| JWT | JSON Web Token |
| SHA-256 | 安全哈希算法 256 位 |
| MDC | Mapped Diagnostic Context，日志诊断上下文 |

### 10.2 参考资料

| 文档 | 位置 |
|------|------|
| 工程化指南 | `backend-repo/ENGINEERING_GUIDE.md` |
| API 契约 | `vitepress-doc/ai-generation/API_CONTRACT.md` |
| 设计规范 | `frontend-fantastic-admin/DESIGN.md` |
| 系统架构 | `vitepress-doc/ai-generation/系统架构/` |
| 安装指南 | `vitepress-doc/getting-started/installation.md` |
| 配置说明 | `vitepress-doc/getting-started/configuration.md` |
| 项目概览 | `vitepress-doc/ai-generation/项目概览/` |
| 贡献指南 | `CONTRIBUTING.md` |
| 更新日志 | `CHANGELOG.md` |
