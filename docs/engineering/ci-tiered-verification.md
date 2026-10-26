# 分层 CI 与发布验收

关联 #450；风险 P1（合并门禁与发布链路）。本策略替代 #362 中“八项检查逐 PR 必需”的配置。生产架构、权限、Flyway、医院数据和部署组件不变。

## PR 快速验证

最终 main 仅要求两个 GitHub Actions App（15368）绑定状态：`governance`、`pr-gate`。继续 strict=true、管理员受限、解决审查对话、线性历史、禁止强推和删除。P0/P1 的独立审查要求继续执行。

`quality-gate.yml` 的 scope job 一次计算完整改动范围，并执行版本/迁移基线、focused test 和活跃主分支引用检查。CI/脚本/基线变化额外执行仓库 Python 测试。范围计算或静态检查失败都阻断。

| 改动 | PR 检查 |
| --- | --- |
| 普通 Markdown、VERSION、发布说明 | 治理与静态不变量；不构建应用 |
| 普通前端页面、样式、公共 UI | 类型、变更 lint、前端全量单测；不采集覆盖率 |
| 前端根配置、依赖或构建工具 | 上述检查 + 生产构建与 Chrome 86 兼容检查 |
| 登录、认证客户端、路由、导入关键页面或浏览器用例 | 增加浏览器测试、真实 JWT/PostgreSQL 链路 |
| 普通后端 Controller/DTO/工具 | 编译与全量单测 |
| Mapper、资源、数据库、配置、Service、身份权限/审计/票据/导入导出 | PostgreSQL 16 集成测试；关键身份/导入链路再增加真实浏览器 |
| Windows 部署资产 | PS5.1 解析与 manager 自检 |
| 数据库恢复、备份、恢复调度 | 增加隔离 Windows PostgreSQL 恢复与失败清理 |
| CI 发布编排、候选文件校验 | 增加无发布权限的完整候选验收 |

Service 保守进入 PostgreSQL 范围，因为仅凭路径无法证明没有事务或 SQL 影响。风险选择不读取作者的 P3 标签来减少检查。未运行的测试不能作为成功证据。

复用 `frontend_test_scope.py`、`backend_test_scope.py`；`ci_gate.py` 统一编排两者和 Windows/真实链路范围。git diff 关闭重命名折叠，删除、重命名前后的路径均参与分类。`pr-gate` 使用 always() 检查每个实际选中的 job，失败、取消、输出缺失或意外跳过均拒绝。整个必需 workflow 不使用顶层 paths 过滤。

P3 PR 可省略 AI 参与报告，正文保留变更、关联原因、风险、验证与回滚。P0/P1 必须关联 Issue/提案并记录独立审查；隐私和历史迁移硬门禁不减。

## main 集成验证

main push 执行同一 quality-gate，查询此前成功的 main push run，比较该 SHA 至当前提交的累计差异。前一次取消、失败不能当作已验收基线；没有可用历史或历史不是当前祖先时验证整棵树。

应用变化在同一 runner 上构建生产前端、运行后端 PostgreSQL 测试并做真实联调，不通过 Artifact 来回搬运应用。复用 windows-release-package 的 full=false 模式，只执行应用验证，不组装 ZIP、不构建文档、不下载 Windows 运行时。前端通过既有 embed 工具嵌入 JAR，浏览器直接访问后端提供的生产资源。文档变化不触发应用构建。

同一 main 分支取消旧运行，集中验证最新提交。这些检查不成为其他 PR 的合并前置；正式发布 admission 要求本次固定 SHA 的 main push 集成已经成功。

## 正式发布

1. 功能与版本说明通过 PR 合并 main，等待该提交的 main 集成成功。
2. Actions 选择 `Release`（文件名暂保留 release-on-version-bump.yml），ref 选择 main。
3. 首次验收保留 dry_run=true；完整候选构建与 Windows 恢复都会运行，但不会创建 tag/Release。
4. 正式发布设 dry_run=false。开始时固定 github.sha；中途 main 前进不改变本次提交。
5. 完整验证成功后才创建不可移动的 `v<VERSION>` tag，上传草稿并核验所有资产摘要，再公开 Release。

更新 VERSION 不再自动创建 tag。人工直接推 tag 不触发第二套发布；正式入口只有 Release。开发分支只能 dry-run，生产发布必须从 main 调度。

`release-validation.yml` 调用 Windows package 和 Windows recovery，并在完整汇总 job 中拒绝任何失败/取消/跳过。package 内执行：

- 仓库工具测试、版本/迁移基线和工程文档检查；
- 一次 `mvn clean verify`，真实 PG16 测试实际执行，单测与合并覆盖率回退阻断；
- 前端全量单测/覆盖率、一次生产构建、Chrome 86 静态兼容与完整 Mock E2E；
- 复用同一嵌入前端的后端 JAR 制作 ZIP 与默认端口 8002 的 standalone JAR；
- 使用最终 standalone JAR 的生产前端执行真实登录、401/403、导入预览/写入/查询；
- 外部文档构建、Windows 运行时组装、完整文件清单和校验和；
- Windows job 下载同一候选 JAR，运行 PS5.1 解析、自检、PG16 恢复和失败清理，不再次 Maven 构建。

候选 Artifact 名包含 run ID、构建 attempt 和 SHA；`release-candidate.json` 绑定版本、完整 SHA、构建 attempt、文件名、大小与 SHA-256。dry-run 只上传 JAR 与身份清单给 Windows，Windows 显式核验该 JAR；Linux 已核验完整 ZIP。正式发布上传完整候选，Windows 与发布 job 都复核全部文件。候选保留 1 天，普通 main 联调不上传大型中间产物。PR 不产生公开 Release。

发布 job 单独获得 contents:write；构建/PR 只读。发布不重新编译、不使用 --clobber、不移动 tag。资产摘要不一致、缺失或候选过期时失败。

### 发布失败恢复

- 测试失败：没有正式 tag/公开 Release；修复后重新完整验收。
- 发布上传中断：草稿不公开。在同一 run 上重跑失败的 publish job，复用原候选和原构建 attempt；不重跑成功构建。
- 上传成功的同名资产必须与候选摘要一致，才跳过该文件、继续剩余上传。
- tag 指向不同提交、资产内容不同或缺少可核验摘要时拒绝操作，不能自动覆盖。
- 已公开且完整一致的版本可幂等结束；不能改写不完整的公开版本。

新的一次完整构建可能含不同构建时间和摘要，不能拿它覆盖上一轮已上传的资产。

## 迁移与回滚

迁移分两步，不能先移除远程仍必需的检查：

1. 合并本批时保留 quality-gate 中的 legacy-gates 矩阵。七个旧状态转发真正 pr-gate 结果，governance 独立保留。旧工作流不再重复启动。
2. 在新 main 上确认 pr-gate 实际成功，然后执行下面的只读预览和迁移。工具只 PATCH required_status_checks，保留其他保护字段；遇到新增的未知检查或不同 App 时拒绝覆盖。

```powershell
python -m scripts.ci_protection
python -m scripts.ci_protection --apply --snapshot "$env:TEMP\MRR-ci-protection-before-450.json"
```

成功后读回两个状态、strict 与 App 绑定；再以小 PR 删除 legacy-gates 矩阵，才消除过渡用 runner。目标 JSON 不是远程已经生效的证据。

回滚先通过 PR 恢复旧工作流能产生相应检查，再恢复已保存配置：

```powershell
python -m scripts.ci_protection --restore "$env:TEMP\MRR-ci-protection-before-450.json"
```

回退仅修改 required checks；代码使用独立中文提交 revert。无数据库修复。原八状态迁移和发布入口相互依赖，因此首批在同一 PR 中审查，按范围、发布、文档分别提交；不能单独删除旧状态或先关闭发布保护。

## 验证与限制

先测试文档/页面/SQL/权限/恢复分类、意外跳过/失败、累计 main 基线、错误 SHA、损坏/缺失文件、版本冲突、草稿上传恢复；再执行 actionlint、仓库治理和真实 CI 的完整候选验收。生产标签不用于试验。

目标普通 PR 2–5 分钟，文档约 1 分钟；以迁移后 20 个 PR 的中位数/P95确认，不能把目标当作已测结果。

CI 使用合成数据与隔离实例；Chrome 86 检查是生产资源兼容检查，浏览器使用当前 Chromium。它不替代 #350 的医院 Windows/Nginx、Chrome 86 真机、真实 NAS/OSS 和生产数据规模验收。
