# MRR 架构边界地图

本文只记录会约束代码修改的稳定边界。详细业务、部署和数据库文档位于 `vitepress-doc/`、`backend-repo/docs/` 与 `docs/engineering/`。

## 1. 系统边界

MRR 当前是医院内网 Windows 单机部署的模块化单体：

```text
浏览器 / HIS 外部系统
        ↓
本机 Nginx
        ↓
Spring Boot 单体应用
        ↓
PostgreSQL 16 + 本地/NAS/Nginx/OSS 影像来源
```

正式部署不依赖 Docker、Redis、Kafka、微服务注册中心或外部任务平台。Docker 仅用于本地开发、测试和演示。

## 2. 仓库结构

```text
backend-repo/              Spring Boot 4 / Java 21 / MyBatis / PostgreSQL
frontend-fantastic-admin/  Vue 3 / TypeScript / Vite / Pinia
mrr-db/                    数据整理、迁移和辅助脚本
vitepress-doc/             用户、开发和运维文档
deploy/                    Windows 与 Nginx 部署资产
scripts/                   仓库治理、测试范围、发布基线工具
docs/engineering/          工程治理规范
docs/adr/                  架构决策记录
```

## 3. 前端依赖方向

```text
views / layouts
      ↓
业务组件 / store / composables
      ↓
api/modules
      ↓
统一 Axios 实例与响应处理
```

约束：

- 页面不得自行创建第二个 Axios 实例或绕过统一鉴权；
- 页面不得直接拼接受保护影像源 URL；
- 公共 UI 组件不得依赖具体病案业务；
- Store 不直接访问数据库或存储源，只通过 API；
- 权限展示使用统一权限指令，但后端仍是最终授权边界；
- 新交互必须评估 Chrome 86；
- 不因为局部页面方便而建立第二套主题、错误处理、日期或持久化机制。

## 4. 后端依赖方向

```text
controller
    ↓
service / application logic
    ↓
mapper / repository
    ↓
PostgreSQL / storage adapters
```

约束：

- Controller 负责协议、参数和响应，不承载复杂事务与 SQL；
- Service 负责业务规则、事务、权限语义和审计编排；
- Mapper 负责数据访问，不隐藏跨领域业务流程；
- DTO 与实体分离，公共接口不得直接暴露数据库实体；
- 外部影像来源通过统一解析或内容网关访问，不把生产凭据交给浏览器；
- 异步任务必须有边界、状态、取消/恢复、配额、过期和审计；
- 定时任务不得无界扫描三千万级表。

## 5. 核心领域

| 领域 | 主要职责 | 不得承担 |
| --- | --- | --- |
| auth / user | 登录、密码版本、账号状态、角色权限 | 病案业务规则 |
| record / scan | 病案、扫描记录、查询与类型 | OSS 凭据管理 |
| image / archive | 影像调阅、档案袋、来源解析 | 绕过权限直接返回敏感源地址 |
| ocr / classification | OCR 事实、建议、审核与类型变更 | 静默覆盖正式分类 |
| audit | 访问、变更、下载导出和外部调阅审计 | 代替权限校验 |
| storage / oss | 本地、NAS、Nginx、S3 兼容存储适配 | 直接决定业务权限 |
| integration | HIS 等外部系统票据与受限访问 | 建立全权限兼容会话 |
| export | ZIP/PDF 任务、配额、下载和清理 | 在前端无界加载全案文件 |
| operations | 健康、监控、备份、发布准备度 | 改写业务数据 |

新增跨领域能力前，优先判断它属于现有领域还是需要 ADR，而不是直接建立新的全局 `utils` 或 `common`。

## 6. 数据与迁移边界

- PostgreSQL 16 是生产事实标准；
- SQLite/H2 只能用于明确适合的开发或单元测试；
- PostgreSQL 方言、锁、事务、索引、Flyway 和复杂 Mapper 使用 PostgreSQL 集成测试；
- 已进入 `main` 的 `V...__*.sql` 版本迁移不可修改、重命名或删除；
- 错误迁移使用新的前向修复迁移；
- 大表查询必须考虑 `mr_scan` 三千万级规模；
- 批处理必须可分批、可重试、可观察，避免单事务长期占锁；
- 数据规则以数据库约束、业务服务和测试共同表达，不能只写在页面提示中。

## 7. 身份、权限与隐私边界

- 管理端默认使用真实 JWT、账号状态、密码版本和 RBAC；
- 无 Token 默认返回 401；
- 旧兼容模式默认关闭，并受开关、可信代理、IP/CIDR、只读接口和审计约束；
- 外部系统使用短期、可校验、可审计的 HMAC Ticket；
- 医疗影像和敏感响应不得使用公共缓存；
- 日志、异常、截图、测试和 Artifact 不得包含真实患者信息、密码、Token 或密钥；
- 权限检查必须在后端执行，前端隐藏按钮不是安全控制。

## 8. 变更规则

以下变更必须先有高风险 Change Proposal，通常还需要 ADR：

- 改变认证、权限、审计或外部调阅模型；
- 引入新的部署组件或持久化系统；
- 改变数据库迁移策略或关键数据不变量；
- 改变影像访问链路、缓存或凭据边界；
- 建立跨多个领域复用的新基础设施；
- 修改本文列出的依赖方向或生产部署边界。

所有 AI 辅助开发还必须遵守 `AGENTS.md` 和 `docs/engineering/ai-assisted-development-governance.md`。
