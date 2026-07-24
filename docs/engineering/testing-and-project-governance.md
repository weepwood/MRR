# MRR 测试与工程治理基线

## 1. 当前判断

MRR 已具备后端 JUnit、Mockito、Testcontainers、H2、JaCoCo，以及前端 Vitest、Vue Test Utils、Playwright 等基础设施。主要问题不是工具缺失，而是测试层级、触发范围、报告产物和协作入口没有统一成一套可持续执行的规则。

本轮治理的目标是：

1. 后端、前端按变更范围选择测试，避免无关 PR 执行全部重型任务；
2. 保留稳定的 `frontend-gate` 与 `backend-gate` 汇总状态，便于设置分支保护；
3. 在每次质量门禁中生成测试资产清单，并阻止 `.only` 与 `@Disabled` 混入主分支；
4. 统一 Issue、PR、依赖更新和代码所有权入口；
5. 让测试失败时保留可下载的 Surefire、Failsafe、JaCoCo、Playwright、JUnit XML 和测试清单。

## 2. 测试分层

| 层级 | 目录或命名 | 主要目标 | 默认触发 |
| --- | --- | --- | --- |
| 后端单元/切片 | `backend-repo/src/test/java` 非 `integration` 目录 | 业务规则、权限、工具类、Controller/Service 隔离测试 | 普通后端代码变更 |
| 后端 PostgreSQL 集成 | `backend-repo/src/test/java/**/integration/**`，类名建议 `*IT` | Mapper、Flyway、SQL、事务与 PostgreSQL 行为 | Mapper、实体、配置、资源、迁移、POM 变更 |
| 前端单元/组件 | `frontend-fantastic-admin/src/**/*.test.ts(x)` | Utils、Store、API 封装、组件行为 | 任意前端源码或配置变更 |
| 前端 E2E | `frontend-fantastic-admin/e2e/*.spec.ts` | 登录、权限、路由、核心业务流程和兼容性 | 页面、路由、API、Store、布局、组件、Mock、E2E 配置变更 |
| 发布与运维 | `scripts/tests`、Windows 自检 | 版本一致性、发布基线、脚本编码和 PowerShell 5.1 | 每个 PR 与 main/tag |

## 3. CI 门禁

### 前端

`scripts/frontend_test_scope.py` 根据 PR 文件变化输出：

- `frontend_changed=false`：后端或纯文档变更，跳过 Node 安装和前端测试；
- `frontend_changed=true, e2e_changed=false`：执行类型检查、变更文件 lint、构建和 Vitest；
- `e2e_changed=true`：在上述检查后安装 Chromium 并执行 Playwright。

`frontend-gate` 始终产生最终状态，因此分支保护只需要依赖该汇总 Job，而不必依赖会被跳过的内部 Job。

### 后端

沿用 `scripts/backend_test_scope.py`：

- 普通 Java 业务代码：`mvn test`；
- Mapper、实体、配置、资源、Flyway 或 POM：PostgreSQL 服务下执行 `mvn verify`；
- 后端无关变更：跳过 Maven 构建。

`backend-gate` 负责汇总结果。

## 4. 测试资产清单

执行：

```bash
python scripts/test_inventory.py --fail-on-focused
```

脚本按测试文件统计四个层级，并检查：

- 前端 `describe.only`、`it.only`、`test.only`；
- 后端 `@Disabled`。

CI 会把清单写入 Job Summary，并上传 `test-inventory.md`。该清单反映测试资产数量，不等同于代码覆盖率或测试质量。

## 5. 本地提交前检查

### 后端普通改动

```bash
cd backend-repo
mvn -B -ntp test
```

涉及 SQL、Mapper、Flyway、实体或配置时：

```bash
cd backend-repo
mvn -B -ntp verify
```

### 前端普通改动

```bash
cd frontend-fantastic-admin
pnpm lint:tsc
pnpm build
pnpm test:run
```

涉及登录、权限、路由、页面、API、Store、布局或核心交互时：

```bash
pnpm test:e2e
```

### 仓库工具

```bash
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/test_inventory.py --fail-on-focused
python scripts/release_baseline.py validate
```

## 6. 分支保护建议

为 `main` 设置：

- 必须通过 `quality-gate / release-baseline-gate`；
- 必须通过 `quality-gate / frontend-gate`；
- 必须通过 `quality-gate / backend-gate`；
- 必须通过 `quality-gate / windows-management-gate`；
- 合并前至少一次 Review；
- 所有 Review 对话必须解决；
- 禁止直接推送和强制推送；
- 建议使用 squash merge，并保留中文、可追踪的提交说明。

仓库设置无法通过本次代码 PR 自动强制，需在 GitHub Branch protection rules 中配置。

## 7. 后续测试债务

1. **覆盖率门禁尚未启用。** 后端 JaCoCo 当前只生成报告；前端 `test:coverage` 还需要补充与锁文件一致的 coverage provider。应先记录当前基线，再分阶段提高阈值，避免用低价值测试机械追求数字。
2. **数据库集成测试仍偏少。** 优先覆盖复杂查询、Flyway 迁移、病案号/上架号规则、导入导出事务和大数据分页。
3. **E2E 应保持少而关键。** 重点保护登录、首次改密、权限拒绝、影像调阅、外部 Ticket、ZIP/PDF 导出和设置持久化；不应把所有视觉细节都放入 E2E。
4. **性能测试应与功能门禁分离。** 三千万级数据、OSS/NAS、超大 ZIP/PDF 使用定时或手动基准测试，避免阻塞每个普通 PR。
5. **测试数据必须脱敏。** 任何夹具、日志、截图和 CI Artifact 都不得包含真实患者信息或生产凭据。
