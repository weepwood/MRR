# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

MRR (Medical Record Repository) — 医疗影像记录管理系统。基于 Spring Boot 4 + Vue 3 的现代化医疗影像管理系统，提供病案管理、影像浏览、统计分析、权限控制等完整功能。

## Repository Structure (Monorepo)

```
MRR/
├── backend-repo/              # Spring Boot 4 (Java 21, MyBatis, PostgreSQL 16)
│   ├── src/main/java/         # Java 源文件
│   ├── src/test/java/         # JUnit 5 + Mockito 测试
│   └── ENGINEERING_GUIDE.md   # 后端工程规范
├── frontend-fantastic-admin/  # Vue 3 SPA
│   ├── src/                   # Vue 3 + TS + Vite
│   ├── ENGINEERING.md         # 前端工程规范 (必读)
│   ├── DESIGN.md              # 设计语言系统
│   └── package.json
├── mrr-db/                    # PostgreSQL/SQLite 数据库脚本
├── vitepress-doc/             # VitePress 文档系统
└── docker-compose.yml         # 本地开发/演示用容器编排（非正式部署方式）
```

## Frontend (frontend-fantastic-admin)

### Tech Stack
- Vue 3.5 (Composition API + `<script setup>`) + TypeScript 5.9
- Vite 8 + UnoCSS 66 (Windi CSS / Tailwind Wind3) + Element Plus 2.13
- Pinia 3 (Setup Store syntax) + Vue Router 5 (filesystem routing)
- Axios (自定义响应拦截器, 自动解包 `Result<T>`)
- Zustand-like state management via Pinia with manual localStorage persistence
- VeeValidate 4 + Zod 4 for form validation

### Key Commands
```bash
pnpm dev            # 启动开发服务器 (port 9000)
pnpm build          # 类型检查 + 构建
pnpm lint           # 全量检查: tsc + eslint + stylelint
pnpm lint:tsc       # vue-tsc 类型检查
pnpm lint:eslint    # ESLint (Antfu config) --fix
pnpm lint:stylelint # Stylelint --fix
pnpm test:run       # Vitest 单次运行
pnpm test           # Vitest watch 模式
pnpm test:coverage  # Vitest 覆盖率
pnpm commit         # cz-git 交互式提交
```

### Architecture
```
src/
├── api/            # API 层: index.ts (Axios实例+拦截器) + modules/*.ts (按领域划分)
│   ├── types.ts    # 所有 API 类型定义 (ApiResult<T>, PaginatedResult<T> 等)
│   └── modules/    # auth, records, image, statistics, system, user, oss 等
├── components/     # 业务复用组件 (每个组件一个目录, index.vue 为入口)
├── layouts/        # 布局组件 (Header, Sidebar, Topbar 等)
├── router/         # Vue Router: index.ts + routes.ts + guards.ts + extensions.ts
├── store/modules/  # Pinia stores (user, settings, menu, route, tabbar, keepAlive)
├── ui/             # Fa* 通用 UI 组件库 (41个组件) + shadcn-vue 组件
├── utils/          # 工具函数 + composables + directives
└── views/          # 页面组件 (vite-plugin-pages 自动注册, kebab-case 目录名)
```

### Key Patterns
- **API 调用**: 使用 `getRequest<T>()` / `postRequest<T>()` 等类型安全封装，返回 `Promise<ApiResult<T>>`
- **响应解包**: Axios 拦截器自动处理 `code/msg/data`，API 函数直接返回 `data` 字段
- **Store**: 组合式 API `defineStore('name', () => { ref, computed, actions; return {...} })`
- **路由守卫顺序**: setupRoutes → setupProgress → setupTitle → setupKeepAlive → setupOther
- **CSS**: UnoCSS 原子类优先 → scoped CSS → 全局 CSS
- **SFC 顺序**: `<route>` → `<script setup>` → `<template>` → `<style scoped>`
- **组件命名**: PascalCase, 目录 kebab-case, 函数 camelCase 动词前缀
- **v-auth 指令**: 权限控制指令, 支持字符串和数组参数

### Testing
- Vitest 3.2 + jsdom 26 + @vue/test-utils 2 + @pinia/testing 1
- 测试文件位置: `src/**/__tests__/*.test.ts` (被 tsconfig 排除在编译之外)
- 现有测试: `src/utils/__tests__/object.test.ts` (merge/diffTwoObj 纯函数, 14 个用例)

## Backend (backend-repo)

### Tech Stack
- Java 21 + Spring Boot 4.0.5 + MyBatis 4.0
- PostgreSQL 16 (主库, schema: app) + SQLite (开发/测试用)
- JWT + AES 认证, SpringDoc OpenAPI 3.0
- Resilience4j 2.4, iText PDF 9.5, AWS S3 SDK (OSS 集成)
- JaCoCo + Maven Surefire/Failsafe

### Key Commands
```bash
# 开发启动 (需要先启动 PostgreSQL)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 测试
mvn test                          # 单元测试
mvn verify                        # 集成测试 + JaCoCo 报告

# 构建 (排除本地配置模板)
mvn clean package -DskipTests -Dlocal.config.exclude=true
```

### Backend Architecture
```
com.zjcxph.imgapi/
├── controller/     # REST 控制器 (Scan, Image, User, Auth, Statistics, OSS 等)
├── service/        # 业务逻辑层
├── mapper/         # MyBatis 数据访问层
├── entity/         # 数据库实体
├── dto/            # 请求/响应 DTO (req/ + resp/)
├── config/         # Spring 配置 (Security, Cache, OSS, Async 等)
├── common/         # 公共类 (Result<T>, AuthSession, ResultCode)
├── exception/      # 全局异常处理
├── annotation/     # 自定义注解 (@RequirePermissions)
├── filter/         # 过滤器 (请求日志, 认证)
├── interceptor/    # 拦截器
├── scheduler/      # 定时任务 (日志清理)
└── utils/          # 工具类 (AES, JWT, Password, Pagination)
```

### Backend Testing
- JUnit 5 + Mockito, 位于 `src/test/java/`
- 13 个测试类覆盖: utils (AES, JWT, Password, Pagination), service (Auth, Scan, Search, Statistics), controller (Image, Scan, User), common (AuthSession, Result)

## Database

- PostgreSQL 16 (本地开发可选): `docker compose up -d postgres`
- SQLite (开发/测试): `schema.sql` 中 `main.` 前缀表
- 关键表: `mr_scan` (扫描记录), `mr_patient` (患者信息), `mr_auth_user` (用户), `mr_auth_role` (角色), `access_log` (访问日志), `mr_statistics` (统计)

## 部署与 Docker 约束

- 本项目正式部署不使用 Docker；Dockerfile 和 `docker-compose.yml` 仅用于本地开发、测试或演示。
- 不要将 Docker Compose 启动方式视为生产部署流程。

```bash
docker compose up -d postgres  # 仅用于本地开发模式
```
- PostgreSQL: port 5432（本地开发）
- Backend: port 18045（本地开发）
- Frontend: 开发使用 pnpm dev → port 9000

## Infrastructure

- **CI**: GitHub Actions 对 `main` 的 Pull Request 执行类型检查、Lint、单元测试、Chromium E2E、后端集成测试、监控配置校验和 Windows 离线包构建。
- **Git Hooks**: simple-git-hooks (pre-commit: lint-staged, commit-msg: commitlint)
- **Commit**: Conventional Commits (cz-git), 类型: feat/fix/docs/style/refactor/perf/test/chore/ci
- **Mock**: `vite-plugin-fake-server` (开发环境)

## Superpowers (Codex Plugin)

项目安装了 [Superpowers](https://github.com/obra/superpowers) v6.1.1 插件，提供自动触发的开发工作流技能。无需手动调用，Agent 会在合适的时机自动激活对应技能。

### 核心技能及其触发时机

| 技能 | 触发时机 | 作用 |
|------|----------|------|
| **brainstorming** | 开始写代码之前 | 先理清需求、探索方案，分块展示设计供确认，保存设计文档 |
| **using-git-worktrees** | 设计批准后 | 创建隔离的 git worktree 分支，验证测试基线 |
| **writing-plans** | 设计确认后 | 将工作拆分为 2-5 分钟的小任务，每个任务包含具体文件路径和验证步骤 |
| **subagent-driven-development** | 计划就绪后 | 为每个任务派发独立子代理，两阶段审查（规格合规 → 代码质量） |
| **test-driven-development** | 实现阶段 | 强制 RED-GREEN-REFACTOR 循环：先写失败测试 → 最少代码通过 → 重构 → 提交 |
| **requesting-code-review** | 任务之间 | 对照计划审查代码，按严重程度报告问题 |
| **systematic-debugging** | 调试时 | 4 阶段根因分析：复现 → 诊断 → 修复 → 验证 |
| **verification-before-completion** | 修完后 | 确保问题确实已解决，不只靠测试通过 |
| **finishing-a-development-branch** | 任务完成 | 验证测试、展示选项（合并/PR/保留/丢弃），清理 worktree |

### 关键原则

- **TDD 优先**: 永远先写测试，后写代码
- **系统化而非临时**: 流程驱动，不靠猜测
- **YAGNI**: 不写不需要的代码
- **证据优于声称**: 在宣布完成之前验证

### 更新

```bash
Codex plugin update superpowers@superpowers-marketplace
```

## 分支与认证边界

- `main` 是唯一正式主分支；功能和修复分支都从 `main` 创建，并向 `main` 提交 Pull Request。
- 管理端默认使用后端真实 JWT、账号状态、密码版本和 RBAC 权限校验。
- 无 Token 请求默认返回 401；只有显式启用的旧影像档案袋兼容模式可以建立受限只读会话。
- 旧兼容模式默认关闭，并同时受启动开关、可信代理、客户端 IP/CIDR 白名单、只读接口白名单和访问审计约束。
- `VITE_APP_DEMO_MODE=true` 仅用于 `.env.mock` 的无后端开发与自动化测试，不得用于生产构建。
- 不得重新引入无条件 `dev/ADMIN` 会话、全权限 Token 或跳过登录的生产逻辑。
