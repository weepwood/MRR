# MRR 测试与工程治理基线

## 1. 当前判断

MRR 已具备后端 JUnit、Mockito、Testcontainers、H2、JaCoCo，以及前端 Vitest、Vue Test Utils、Playwright 等基础设施。主要问题不是工具缺失，而是测试层级、触发范围、报告产物和协作入口没有统一成一套可持续执行的规则。

本轮治理的目标是：

1. 后端、前端按变更范围选择测试，避免无关 PR 执行全部重型任务；
2. 保留稳定的 `frontend-gate` 与 `backend-gate` 汇总状态，便于设置分支保护；
3. 在每次质量门禁中生成测试资产清单，并阻止 `.only` 与 `@Disabled` 混入主分支；
4. 统一 Issue、PR、依赖更新和代码所有权入口；
5. 让测试失败时保留可下载的 Surefire、Failsafe、JaCoCo、Vitest Coverage、Playwright、JUnit XML 和测试清单；
6. 建立可追踪的前后端覆盖率基线，先观察趋势，再逐步启用阻塞门禁。

## 2. 测试分层

| 层级 | 目录或命名 | 主要目标 | 默认触发 |
| --- | --- | --- | --- |
| 后端单元/切片 | `backend-repo/src/test/java` 非 `integration` 目录 | 业务规则、权限、工具类、Controller/Service 隔离测试 | 普通后端代码变更 |
| 后端 PostgreSQL 集成 | `backend-repo/src/test/java/**/integration/**`，类名建议 `*IT` | Mapper、Flyway、SQL、事务与 PostgreSQL 行为 | Mapper、实体、配置、资源、迁移、POM 变更 |
| 前端单元/组件 | `frontend-fantastic-admin/src/**/*.test.ts(x)` | Utils、Store、API 封装、组件行为与覆盖率趋势 | 任意前端源码或配置变更 |
| 前端 E2E | `frontend-fantastic-admin/e2e/*.spec.ts` | 登录、权限、路由、核心业务流程和兼容性 | 页面、路由、API、Store、布局、组件、Mock、E2E 配置变更 |
| 发布与运维 | `scripts/tests`、Windows 自检 | 版本一致性、发布基线、脚本编码和 PowerShell 5.1 | 每个 PR 与 main/tag |

## 3. CI 门禁

### 前端

`scripts/frontend_test_scope.py` 根据 PR 文件变化输出：

- `frontend_changed=false`：后端或纯文档变更，跳过 Node 安装和前端测试；
- `frontend_changed=true, e2e_changed=false`：执行类型检查、变更文件 lint、构建、Vitest 和覆盖率报告；
- `e2e_changed=true`：在上述检查后安装 Chromium 并执行 Playwright；
- 覆盖率脚本、基线或质量门禁变更：执行完整前端验证。

`frontend-gate` 始终产生最终状态，因此分支保护只需要依赖该汇总 Job，而不必依赖会被跳过的内部 Job。

### 后端

沿用 `scripts/backend_test_scope.py`：

- 普通 Java 业务代码：`mvn test`；
- Mapper、实体、配置、资源、Flyway 或 POM：PostgreSQL 服务下执行 `mvn verify`；
- 覆盖率脚本、基线或覆盖率工作流：执行完整 PostgreSQL 后端验证；
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

## 5. 后端覆盖率基线

当前基线来自 `quality-gate` 的完整 PostgreSQL `mvn verify`，记录在 `quality/coverage-baseline.json`：

| 指标 | 当前基线 |
| --- | ---: |
| 指令覆盖率 | 50.04% |
| 分支覆盖率 | 41.56% |
| 行覆盖率 | 51.27% |
| 复杂度覆盖率 | 35.67% |
| 方法覆盖率 | 59.47% |
| 类覆盖率 | 75.11% |

`coverage-baseline` 工作流会在 `quality-gate` 成功后读取后端测试 Artifact，使用 `scripts/jacoco_coverage.py` 生成：

- GitHub Actions Job Summary；
- `coverage-summary.json`，便于后续趋势分析；
- `coverage-summary.md`，便于人工审查；
- 与基线相比的百分点变化和超过 `0.50` 个百分点的下降提示。

当前 `enforcementMode` 为 `report-only`：下降会被标记，但不会阻塞 PR。建议至少观察若干轮稳定数据，再对行覆盖率、分支覆盖率或新增代码覆盖率启用 `--fail-on-regression`。

本地解析已有 JaCoCo 报告：

```bash
python scripts/jacoco_coverage.py \
  --xml backend-repo/target/site/jacoco/jacoco.xml \
  --baseline quality/coverage-baseline.json \
  --output-json backend-repo/target/coverage-summary.json \
  --output-markdown backend-repo/target/coverage-summary.md
```

## 6. 前端覆盖率基线

前端使用与 Vitest `3.2.4` 匹配的 `@vitest/coverage-v8`。初始基线来自 `quality-gate` 的完整前端测试，记录在 `quality/frontend-coverage-baseline.json`：

| 指标 | 当前基线 |
| --- | ---: |
| 行覆盖率 | 19.46% |
| 语句覆盖率 | 19.46% |
| 函数覆盖率 | 76.33% |
| 分支覆盖率 | 77.47% |

行和语句覆盖率较低，主要原因是大量页面与应用入口尚未被单元测试执行；函数和分支覆盖率较高，说明当前测试集中于工具、Store、API 封装和业务逻辑。该数字按真实源码范围记录，不通过排除业务页面来提高表面覆盖率。

执行：

```bash
cd frontend-fantastic-admin
pnpm test:coverage
```

报告输出到 `frontend-fantastic-admin/coverage/`：

- 控制台 text 报告；
- `coverage-summary.json`；
- `lcov.info`；
- HTML 报告；
- `coverage-comparison.json` 与 `coverage-comparison.md`。

覆盖范围包含 `src/**/*.{ts,tsx,vue}`，排除测试文件、类型声明、Mock 数据及纯路由元数据。CI 使用 `scripts/vitest_coverage.py` 将总行、语句、函数和分支覆盖率写入 GitHub Actions Summary，并上传完整 Coverage Artifact。

当前策略同样为 `report-only`：先记录稳定基线和正常波动，再考虑启用 `--fail-on-regression`。本地解析：

```bash
python ../scripts/vitest_coverage.py \
  --summary coverage/coverage-summary.json \
  --baseline ../quality/frontend-coverage-baseline.json \
  --output-json coverage/coverage-comparison.json \
  --output-markdown coverage/coverage-comparison.md
```

## 7. 本地提交前检查

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
pnpm test:coverage
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

## 8. 分支保护建议

为 `main` 设置：

- 必须通过 `quality-gate / release-baseline-gate`；
- 必须通过 `quality-gate / frontend-gate`；
- 必须通过 `quality-gate / backend-gate`；
- 必须通过 `quality-gate / windows-management-gate`；
- 合并前至少一次 Review；
- 所有 Review 对话必须解决；
- 禁止直接推送和强制推送；
- 建议使用 squash merge，并保留中文、可追踪的提交说明。

仓库设置无法通过代码 PR 自动强制，需在 GitHub Branch protection rules 中配置。

## 9. 后续测试债务

1. **覆盖率门禁处于观察阶段。** 前后端已建立报告工具和真实基线，仍需积累稳定报告并确定正常波动；不应机械追求全仓库高数字。
2. **数据库集成测试仍需扩展。** 已覆盖空库迁移、重复启动、病案号边界和事务回滚；下一步关注复杂查询、唯一性、权限审计与大分页。
3. **前端页面测试仍需扩展。** 优先补充认证、权限拒绝、影像调阅、设置持久化和导入导出等高风险流程的组件或逻辑测试，而不是为提升数字批量编写低价值快照。
4. **E2E 应保持少而关键。** 重点保护登录、首次改密、权限拒绝、影像调阅、外部 Ticket、ZIP/PDF 导出和设置持久化；不应把所有视觉细节都放入 E2E。
5. **性能测试应与功能门禁分离。** 三千万级数据、OSS/NAS、超大 ZIP/PDF 使用定时或手动基准测试，避免阻塞每个普通 PR。
6. **测试数据必须脱敏。** 任何夹具、日志、截图和 CI Artifact 都不得包含真实患者信息或生产凭据。
