# 更新日志

项目版本遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/) 规范。

## [Unreleased]

### 新增

#### 功能
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

#### 项目文档
- 根目录 `README.md` — 项目总览、快速启动、技术栈
- `CONTRIBUTING.md` — 贡献指南（分支规范、Conventional Commits、PR 流程）
- `CHANGELOG.md` — 版本更新记录
- `PRD.md` — 产品需求文档（含功能全景图、数据模型、API 清单、路线图）
- 用户指南 7 篇 — 快速上手、病案管理、影像浏览、统计分析、系统管理、日志查看
- 运维指南 7 篇 — 日志管理、数据备份、性能监控、故障处理、定期维护、安全维护

#### 后端单元测试
- 新增 10 个测试文件、109 个测试方法，总计 **116 个测试全部通过**：

| 测试类 | 测试数 | 覆盖范围 |
|--------|--------|----------|
| `PasswordUtilTest` | 10 | 哈希、匹配、null 边界、空串 |
| `PaginationUtilsTest` | 15 | 偏移量、总页数、参数校验异常 |
| `JwtUtilTest` | 5 | Token 生成/解析、权限、null Session 往返 |
| `AESUtilTest` | 7 | JSON/下划线解析、异常路径 |
| `AuthSessionTest` | 7 | isAdmin、hasPermission、null 权限 |
| `AuthServiceImplTest` | 11 | 登录全路径、用户列表、禁用、角色列表 |
| `ScanServiceImplTest` | 15 | CRUD、条件查询、分页、异常参数 |
| `StatisticsServiceImplTest` | 14 | 全量/条件查询、BAH/日期/类型聚合 |
| `SearchServiceImplTest` | 2 | 身份证搜索、空结果 |
| `UserControllerTest` | 11 | 登录、当前用户、用户/角色列表、更新/禁用 |
| `ScanControllerTest` | 17 | CRUD、分页、条件查询、空值异常 |

### 修复

#### 🛡️ 安全修复
- **路径遍历漏洞** (`ImageController.getImage`) — 新增 `Path.normalize()` + `startsWith(basePath)` 双重校验，拦截 `../` 目录穿越攻击；添加路径参数长度和 null 校验
- **JSON 注入** (`AuthorizationInterceptor`, `LoginInterceptor`) — 使用 `ObjectMapper.writeValue()` 替代字符串拼接构造 JSON 响应，防止特殊字符注入
- **身份证号日志泄露** (`SearchController.getBAHByiDCard`) — 废弃 API 日志输出改为掩码格式 `1101***`

#### 🐛 Bug 修复
- **日志缓冲区竞态导致数据丢失** (`AsyncLogServiceImpl`) — 以 `synchronized` + `LinkedList` 替换 `CopyOnWriteArrayList`，保证 `snapshot → clear → insert` 的原子性，杜绝并发丢失和重复写入
- **`/api/v1/auth/me` 返回 500** (`AuthServiceImpl`) — 移除无参数 `@Cacheable(key = "#username")` 注解，该 SpEL 引用不存在的方法参数导致 Cache 切面异常
- **`PasswordUtil.encode()` NPE** — 添加 null 守卫，防止 null 入参导致 `NullPointerException`
- **`ScanServiceImpl.getImagePath()` NPE** — 添加 `folder`/`brxh` 为 null 时的防御性返回
- **`ImageController.extractYearMonth()` NPE** — 添加参数 null 校验

#### 🔧 代码质量
- **Swagger 注解乱码** (`ScanController`) — 修复 UTF-8 编码问题导致的中文乱码
- **包声明前导空格** (`RestTemplateConfig`) — 删除 `package` 行首多余空格
- **`ImageController` 未使用导入** — 清理 `RestTemplate` 等无用 import

### 优化

#### 后端
- 后端分层架构（Controller → Service → Mapper）
- 统一异常处理和响应格式
- **密码加密存储**（SHA-256，待升级 bcrypt）
- **敏感数据加密**（AES/CBC）
- `AsyncLogServiceImpl` 缓冲队列重写：`CopyOnWriteArrayList` → `LinkedList` + `synchronized` 块，消除 GC 压力和竞态条件
- `ImageController.getImage` 路径构建增加 `normalize()` 防止路径穿越

#### 前端
- 前端组件化设计（Element Plus + Fantastic Admin）
- 数据库索引与查询优化

### 文档
- VitePress 文档系统搭建
- 项目概览、架构设计文档
- API 接口文档
- 数据库设计文档
- 部署运维指南
- 开发指南与代码规范
- 新增用户指南模块
- 新增运维指南模块
- 新增产品需求文档 (PRD)
- 新增贡献指南 (CONTRIBUTING.md)
- 完善根目录 README

## [0.1.0] - 2026-04-01

### 新增
- 项目初始化
- 基础框架搭建（Spring Boot 4 + Vue 3）
- 数据库 Schema 设计
- 文档系统初始化
