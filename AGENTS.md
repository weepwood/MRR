# AGENTS.md

本文件是 AI Agent 在 MRR 仓库中工作的强制入口。它定义读取顺序、开发流程、风险边界和完成标准。不得只依靠当前对话或局部代码推断整个项目规则。

## 1. 项目定位

MRR（Medical Record Repository）是运行在医院内网 Windows 服务器上的医疗影像与病案管理系统，包含病案查询、影像档案袋、统计分析、权限、审计、导入导出、OCR、外部系统调阅和运维能力。

正式架构：

```text
浏览器 / HIS 外部系统
        ↓
本机 Nginx
        ↓
Spring Boot 4 单体应用
        ↓
PostgreSQL 16 + 本地/NAS/Nginx/OSS 影像来源
```

正式部署不使用 Docker。Dockerfile 与 Compose 只用于本地开发、测试或演示。

## 2. 修改前必读

按顺序读取：

1. 当前 Issue、PR 描述和验收标准；
2. `ARCHITECTURE.md`；
3. `docs/engineering/ai-assisted-development-governance.md`；
4. 本文件；
5. 与修改模块相关的工程文档：
   - `backend-repo/ENGINEERING_GUIDE.md`；
   - `frontend-fantastic-admin/ENGINEERING.md`；
   - `frontend-fantastic-admin/DESIGN.md`；
   - `backend-repo/docs/`；
   - `vitepress-doc/internal/`；
6. 相关测试、历史 ADR 和同类实现。

涉及架构、权限、数据库、部署、外部接口或大规模数据时，先检查 `docs/adr/` 和“高风险变更提案” Issue。

## 3. 仓库结构

```text
MRR/
├── backend-repo/              # Spring Boot 4 / Java 21 / MyBatis / PostgreSQL
├── frontend-fantastic-admin/  # Vue 3 / TypeScript / Vite / Pinia
├── mrr-db/                    # 数据整理与辅助脚本
├── vitepress-doc/             # 用户、开发、运维文档
├── deploy/                    # Windows 与 Nginx 部署资产
├── scripts/                   # 测试、发布、治理工具
├── docs/engineering/          # 工程治理规范
├── docs/adr/                  # 架构决策记录
└── .github/                   # Issue、PR、Actions、Dependabot、CODEOWNERS
```

## 4. 风险等级

开始实现前必须声明风险等级：

- **P0**：生产故障、数据损坏、严重安全或隐私风险；
- **P1**：登录权限、审计、数据库迁移、删除导出、外部调阅、发布链路；
- **P2**：重要功能、跨模块改造、明显性能或兼容风险；
- **P3**：低风险 UI、文档、测试或内部整理。

风险取最高影响，不按代码行数判断。P0/P1 必须有 Issue、失败路径测试、独立审查和可执行回滚。

## 5. 强制工作流

### 5.1 调查

先只读，不直接修改。输出：

- 相关文件和调用链；
- 已有可复用机制；
- 数据表、接口、权限和部署影响；
- 已确认事实、推断和未知项；
- 最小修改范围；
- 风险与验证计划。

禁止看到一个局部实现后立即建立第二套 API、Store、工具、状态机、缓存或错误处理机制。

### 5.2 计划

计划精确到文件或模块，并写明：

- 每步修改内容；
- 为什么在这里改；
- 如何验证；
- 哪些相邻问题明确不处理；
- 如何独立提交与回滚。

### 5.3 测试先行

以下逻辑优先先写失败测试：

- 权限和账号状态；
- 数据转换、唯一性和状态机；
- 数据库事务、锁、迁移和复杂 SQL；
- 重试、超时、中断恢复和缓存；
- 审计、导入导出、路径与存储解析；
- OCR 自动分类和正式类型变更；
- 安全、隐私和外部票据。

执行 RED → GREEN → REFACTOR，不允许先完成实现再补一个无法证明问题的表面测试。

### 5.4 小范围实现

- 一个 PR 只解决一个主要问题；
- 不混合业务功能、无关重构和全局格式化；
- 不为未确认的未来需求提前抽象；
- 发现相邻问题时建立 Issue，不顺手扩大范围；
- 超过 50 个文件或约 1500 行增删时，默认拆分或说明不可拆分原因；
- 提交说明使用中文。

### 5.5 独立审查

实现完成后至少进行：

1. 规格审查：是否符合 Issue、验收标准和非目标；
2. 代码审查：是否存在错误、重复机制、权限、隐私、事务、并发、SQL、性能、迁移和兼容风险。

不得仅使用实现时的同一上下文自行宣布“没有问题”。审查应直接读取 Issue、diff、测试和架构规范。

### 5.6 完成验证

宣布完成前必须提供实际证据：

- 执行过的命令与结果；
- 失败路径、权限拒绝和边界条件；
- PostgreSQL、Windows、Chrome 86 或真实存储验证（适用时）；
- 数据库迁移、日志、审计或性能证据；
- 回滚或功能关闭验证；
- 仍未确认的假设和剩余风险。

“代码看起来正确”“CI 应该会通过”不属于证据。

## 6. 不可妥协规则

### Git 与范围

- `main` 是唯一正式主分支；
- 不直接修改或推送 `main`；
- 功能和修复从最新 `main` 创建分支并通过 PR；
- 不修改与当前 Issue 无关的文件；
- 不使用强制推送改写已公开审查历史，除非明确处理分支事故。

### 数据库

- PostgreSQL 16 是生产事实标准；
- 已进入 `main` 的 `backend-repo/src/main/resources/db/migration/V...__*.sql` 不得修改、重命名或删除；
- 错误迁移通过新的前向修复迁移处理；
- Mapper、Flyway、PostgreSQL 方言、事务、锁和复杂 SQL 使用 PostgreSQL 集成测试；
- 不使用 H2/SQLite 结果证明 PostgreSQL 专有行为；
- 三千万级 `mr_scan` 查询禁止无界扫描、全量加载和未评估的大事务；
- 数据修复必须先有只读统计、分批、重试、审计和恢复策略。

### 权限与隐私

- 所有医疗影像和病案访问必须经过后端权限或受控外部票据；
- 前端隐藏按钮不是权限控制；
- 无 Token 默认返回 401；
- 不重新引入无条件 `dev/ADMIN`、全权限 Token 或生产跳过登录逻辑；
- 外部兼容模式默认关闭，只读、限 IP/CIDR、限接口并记录审计；
- 医疗影像、下载包和敏感响应不得设置公共缓存；
- 真实患者姓名、身份证、病案号、访问令牌、密码、密钥、生产配置和敏感内网信息不得进入代码、测试、日志、截图、Issue、PR 或 Artifact。

### OCR 与分类

- OCR 文本、分类建议和正式类型必须分离；
- 已分类影像不得被自动分类静默覆盖；
- 正式类型修改必须有真实操作者身份、权限和不可伪造审计；
- 批量确认必须有阈值、范围、失败恢复和抽样复核；
- 不接受前端传入任意本地可执行程序路径或命令。

### 存储、下载与导出

- 前端不得直接获得生产存储凭据；
- 受保护影像优先通过后端统一内容网关；
- ZIP/PDF 大任务必须受权限、配额、过期、取消、Range、临时文件清理和审计约束；
- 不在前端无界加载整份大病案；
- OSS/NAS/Nginx 失败信息不得向客户端暴露密钥、内部路径或底层异常。

### 架构与部署

- 维持模块化单体，不因局部需求擅自引入微服务、Kafka、Redis、ELK 或独立任务平台；
- Controller 不承载复杂事务和 SQL；
- 页面不直接创建第二套请求与认证机制；
- 公共 UI 不依赖具体病案业务；
- 新基础设施或改变依赖方向必须先写 ADR；
- 正式部署以 Windows Server、Nginx、Spring Boot、PostgreSQL 为边界；
- 前端关键流程必须兼容 Chrome 86；
- PowerShell 运维脚本必须兼容 Windows PowerShell 5.1。

## 7. 前端规则

技术：Vue 3 Composition API + `<script setup>`、TypeScript、Vite、Pinia、Element Plus、UnoCSS。

常用命令：

```bash
cd frontend-fantastic-admin
pnpm install --frozen-lockfile
pnpm lint:tsc
pnpm build
pnpm test:run
pnpm test:e2e
```

依赖方向：

```text
views / layouts
      ↓
components / store / composables
      ↓
api/modules
      ↓
统一 Axios 实例
```

要求：

- API 使用现有类型安全封装；
- Store 使用组合式 `defineStore`；
- 优先复用现有组件、设计 Token 与交互模式；
- 页面、路由、API、Store、权限和关键交互变化应补充 Vitest 或 Playwright；
- 禁止只为测试通过改变真实业务语义。

## 8. 后端规则

技术：Java 21、Spring Boot 4、MyBatis、PostgreSQL 16、JUnit 5、Mockito、Testcontainers、JaCoCo。

常用命令：

```bash
cd backend-repo
mvn -B -ntp test
mvn -B -ntp verify
mvn clean package -DskipTests -Dlocal.config.exclude=true
```

依赖方向：

```text
controller
    ↓
service
    ↓
mapper
    ↓
PostgreSQL / storage adapters
```

要求：

- Controller 负责协议与参数，不隐藏复杂业务；
- Service 承担业务规则、事务和审计编排；
- Mapper 只负责数据访问；
- DTO 与数据库实体分离；
- 修改公共 API 时说明兼容影响；
- 异步任务必须有状态、边界、取消/恢复、配额和清理机制。

## 9. 仓库治理命令

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/test_inventory.py --fail-on-focused
python scripts/release_baseline.py validate
```

新 PR 还必须通过 `governance-gate`。该门禁会检查 PR 正文、风险等级、Issue 关联、Flyway 历史迁移和敏感配置文件，并对超大或跨模块变更给出警告。

## 10. 需要停止并升级处理的情况

遇到以下情况不得继续猜测实现：

- Issue、代码与数据库事实互相矛盾；
- 需要修改已发布 Flyway 迁移；
- 无法确认生产数据规则或唯一性；
- 需要绕过权限、审计或登录才能让功能工作；
- 测试只能通过删除断言、扩大权限或吞掉异常；
- 需要引入新的运维组件才能完成局部需求；
- 真实环境证据与本地 Mock 结论冲突；
- 变更会删除、覆盖或批量修改医疗数据但没有恢复方案。

此时应记录阻塞事实、最小复现和可选方案，创建或更新 Issue，而不是生成一个看似可运行的临时实现。
