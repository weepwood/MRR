# 分层 CI 与发布验收

关联 #450；风险 P1（合并门禁与发布链路）。本策略替代 #362 中“八项检查逐 PR 必需”的配置。生产架构、权限、Flyway、医院数据和部署组件不变。

## PR 快速验证

main 仅要求两个 GitHub Actions App（15368）绑定状态：`governance`、`pr-gate`。2026-11-24 维护者确认关闭 strict 与 stale approval，仓库目标按 [分支保护实施记录](main-branch-protection.md) 同步；此前 strict=true 仅是历史策略。管理员受限、解决审查对话、线性历史、禁止强推和删除保持不变。P0/P1 主动同步最新 main、验证实际合并树并按 AGENTS.md 5.5 审查最新 SHA；普通独立 P2/P3 不因无关主线更新强制重测。后续 CI 去重不会取消这两项门禁。

`quality-gate.yml` 的 scope job 一次计算完整改动范围，并执行版本/迁移基线、focused test 和活跃主分支引用检查。CI/脚本/基线变化额外执行仓库 Python 测试。范围计算或静态检查失败都阻断。

| 改动 | PR 检查 |
| --- | --- |
| 普通 Markdown、发布说明 | 治理与静态不变量；不构建应用 |
| VitePress 文档或 VERSION | 文档测试与内外站构建，版本不变量；不构建应用（其他累计风险除外） |
| 普通前端页面、样式、公共 UI | 类型、变更 lint、前端全量单测及映射 Mock 回归；未知共享输入回退完整 Mock，不采集覆盖率 |
| 前端根配置、依赖或构建工具 | 上述检查 + 生产构建与 Chrome 86 兼容检查 |
| 登录、认证客户端、路由、导入关键页面或浏览器用例 | 类型、变更 lint、前端全量单测；关键源码/共享测试输入完整 Mock，独立 spec 变化定向 Mock |
| 普通后端 Controller/DTO/工具 | 编译与全量单测 |
| Mapper、资源、数据库、配置、Service、身份权限/审计/票据/导入导出 | PostgreSQL 16 集成测试；真实浏览器链路延后到 main 集成与发布候选验收 |
| Windows 部署资产 | PS5.1 解析与 manager 自检 |
| 数据库恢复、备份、恢复调度 | 增加隔离 Windows PostgreSQL 恢复与失败清理 |
| CI 发布编排、候选文件校验 | 增加无发布权限的完整候选验收 |

PR 的 frontend job 并行执行类型检查与全量单测，任一失败即失败；backend job 关闭 JaCoCo 插桩，覆盖率回退仅由完整发布验收阻断。

普通 PR 上的集成 job 只在前端确有改动时执行前端检查与构建（`integration_frontend`）；仅后端身份/导入类改动不再重复构建未改动的前端。若草稿延期了累计变更要求的完整候选，已选中的高风险真实集成仍执行真实 JAR 联调并嵌入前端。main 集成与完整发布验收始终构建并嵌入前端。

Pages 演示工作流在 PR 上按改动范围选择：仅明确列入展示层白名单的帮助/组件演示页面、通用视觉组件、全局样式与 Markdown 不再额外构建演示站；业务页面、未知前端源码、前端关键流程、构建配置、Mock 数据、静态演示冒烟脚本、`VERSION`、`release-baseline.json` 或 Pages workflow 变化继续执行演示冒烟。单独 Mock/live spec 或测试 fixture 变更不再触发静态演示构建，除非累计变更中另有静态构建输入。范围输出缺失或非法时继续运行冒烟；范围 job 自身失败仍会使 workflow 失败。main push 与手动调度仍按原触发条件构建、冒烟和部署。此优化不改变 PR 的前端全量单测、类型或 lint 门禁。

Service 保守进入 PostgreSQL 范围，因为仅凭路径无法证明没有事务或 SQL 影响。风险选择不读取作者的 P3 标签来减少检查。未运行的测试不能作为成功证据。

复用 `frontend_test_scope.py`、`backend_test_scope.py`；`ci_gate.py` 统一编排两者和 Windows/真实链路范围。git diff 关闭重命名折叠，删除、重命名前后的路径均参与分类。`pr-gate` 使用 always() 检查每个实际选中的 job，失败、取消、输出缺失或意外跳过均拒绝。整个必需 workflow 不使用顶层 paths 过滤。

Mock 业务 E2E 只由 Quality Gate 负责，Pages 不再重复执行同一 Vite Mock suite。已明确映射的患者、装箱、设置、统计和样式变化选择相关 spec；新增/修改的 spec 保留，并保留既有相关用例扩展。关键源码、共享 fixture、Mock API、测试配置或未映射的应用输入回退完整 Mock。删除/重命名导致选中路径缺失时回退完整 Mock。完整候选已经拥有全量 Mock 和文档构建时，独立 e2e/docs job 不再重复。定向保留20分钟步骤预算，原断言、重试、预热不变。后端高风险路径保留 PostgreSQL 集成测试；main 真实 JWT/PostgreSQL/JAR 浏览器联调与完整发布不变。

### 草稿反馈与最终准入（2026-11-27）

长期 PR 中的 `release-baseline.json` 或发布工具变更会持续选中完整候选。只对实际 GitHub draft 事件使用 `--mode draft`：仍比较完整累计差异并执行已选中的开发/数据库/真实联调/Mock/文档/Windows 专项，但可延期完整候选的覆盖率及制品链路。此时 `candidate_deferred=true`，必需的 `pr-gate` **有意不通过**，不是可合并绿灯。正常 PR、ready_for_review 及相关同步/编辑事件重新计算整个累计差异；非草稿仍要求原完整候选。缺少/非法 draft 状态或延期输出、必需 job 缺失/失败/取消/意外跳过一律拒绝。草稿也可使用现有 Release dry-run 独立验证完整包，但手动运行的汇总检查名为 manual-integration-gate，不复用必需 pr-gate 状态。选择器自身变化即使同时存在定向 spec 也回退完整 Mock。未更改远程分支保护、治理、版本号、生产权限或正式 Release。

日常 L1 在本地按风险执行 lint、类型及定向测试；L2 以累计 PR 选中检查为准，草稿延期不是合并批准；L3 走原固定 SHA 的完整候选及正式发布链路。不使用最后一次 commit 或缓存绿灯代替累计风险。

### 缓存、诊断与回滚

Chromium 缓存键统一为 OS/架构/Playwright 版本，缓存命中仍安装系统依赖并确认锁定浏览器可用。固定 Windows 运行时仅缓存原始下载文件，键含版本与仓库预期摘要；命中和新下载都先核验 `quality/windows-runtime-sha256.txt` 再解压。新摘要基于官方 HTTPS 再下载并与历史已验收包逐文件比对，不冒充上游签名认证。任何摘要不一致立即失败，更新版本必须独立审查新来源及摘要。候选 JAR、ZIP、身份清单和测试绿灯不进入跨提交缓存。

Quality Gate 汇总实际选择、结果、比较基线、源码 SHA 和延期状态；Mock JUnit 与失败 trace 按 run/attempt 保留。回滚使用本批独立 Git revert，恢复原工作流和选择器（无需修改分支保护或数据库）。

P3 PR 可省略 AI 参与报告，正文保留变更、关联原因、风险、验证与回滚。P0/P1 必须关联 Issue/提案并记录符合 AGENTS.md 5.5 的证据化审查；隐私和历史迁移硬门禁不减。

## main 按风险去重与手动集成（#717）

main push 执行同一 quality-gate，使用 `--mode main` 查询此前成功的 main push run，比较该 SHA 至当前提交的累计差异。前一次取消、失败不能当作已验收基线；没有可用历史或历史不是当前祖先时验证整棵树。成功的轻量 run 仅证明该范围内门禁通过，不证明完整应用集成。

仅当累计变化限于 `ci_gate.py` 中明确列出的帮助/组件演示页面、通用视觉组件、`src/assets/styles/` 中的样式文件和 Markdown，且不匹配现有关键流程、构建或后端/Windows/工具链范围时，main 只运行仓库不变量与门禁，不重复 PR 的前端单测，也不无条件启动后端 PG16。Pages 仍正常构建与冒烟。API、Store、路由、工具函数、构建依赖、后端、未知路径以及用户/患者/影像/档案袋/存储/运维等业务页面不走该快路径。新增展示层白名单必须经审查并补充范围回归测试。选择依据为路径和风险规则，不使用作者标签或旧 SHA 的测试结果。此规则接受普通展示层组合回归延至完整候选的取舍，不能把未跑的检查标作已通过。

选中的应用变化在同一 runner 上构建生产前端、运行后端 PostgreSQL 测试并做真实联调，不通过 Artifact 来回搬运应用。复用 windows-release-package 的 full=false 模式，只执行应用验证，不组装 ZIP、不构建文档、不下载 Windows 运行时。前端通过既有 embed 工具嵌入 JAR，浏览器直接访问后端提供的生产资源。文档变化不触发应用构建。

同一 main 分支取消旧运行，集中验证最新提交。这些检查不成为其他 PR 的合并前置。正式发布 admission 仍要求本次固定 SHA 的 main push quality-gate 成功（可能是轻量范围），并在 Release 中独立执行完整候选验收，不能以轻量检查代替。手动运行 quality-gate 使用 `--mode integration` 检查整棵树，不读取轻量 push 的历史基线；需要完整发布包、覆盖率或全部 E2E 时使用 Release dry-run。

本次不修改远程分支保护或 governance/pr-gate。#720 已合入宽松同步目标及脚本；根据维护者 2026-11-24 自主审查授权，同步 AGENTS.md 5.5 及治理文档：优先独立审查，不能取得时允许记录真实的 AI 自审证据，不等待额外人工审批；不减少必需验证。高风险或存在相关依赖变化的 PR 仍主动同步最新 main 并验证实际合并树，不能复用未验证的新合并树的旧状态。

## 正式发布

1. 功能与版本说明通过 PR 合并 main，等待该提交的 main quality-gate 成功（按实际风险范围）。
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

1. 首批 #451 合并时保留 quality-gate 中的 legacy-gates 矩阵。七个旧状态转发真正 pr-gate 结果，governance 独立保留。旧工作流不再重复启动。
2. 在新 main 上确认 pr-gate 实际成功，然后执行下面的只读预览和迁移。工具只 PATCH required_status_checks，保留其他保护字段；遇到新增的未知检查或不同 App 时拒绝覆盖。

```powershell
python -m scripts.ci_protection
python -m scripts.ci_protection --apply --snapshot "$env:TEMP\MRR-ci-protection-before-450.json"
```

成功后读回两个状态、strict 与 App 绑定；再以小 PR 删除 legacy-gates 矩阵，才消除过渡用 runner。2026-10-27 已在 main 成功后实际切为两个状态，并完成恢复原八项再切回的回滚演练，全部其他保护字段与原快照完全一致；详见 [实施记录](./main-branch-protection.md)。当前工作流已删除过渡矩阵。目标 JSON 不是远程已经生效的证据。

回滚先通过 PR 恢复旧工作流能产生相应检查，再恢复已保存配置：

```powershell
python -m scripts.ci_protection --restore "$env:TEMP\MRR-ci-protection-before-450.json"
```

回退仅修改 required checks；代码使用独立中文提交 revert。无数据库修复。原八状态迁移和发布入口相互依赖，因此首批在同一 PR 中审查，按范围、发布、文档分别提交；不能单独删除旧状态或先关闭发布保护。

## 验证与限制

先测试文档/页面/SQL/权限/恢复分类、意外跳过/失败、累计 main 基线、错误 SHA、损坏/缺失文件、版本冲突、草稿上传恢复；再执行 actionlint、仓库治理和真实 CI 的完整候选验收。生产标签不用于试验。

目标普通 PR 2–5 分钟，文档约 1 分钟；以迁移后 20 个 PR 的中位数/P95确认，不能把目标当作已测结果。

CI 使用合成数据与隔离实例；Chrome 86 检查是生产资源兼容检查，浏览器使用当前 Chromium。它不替代 #350 的医院 Windows/Nginx、Chrome 86 真机、真实 NAS/OSS 和生产数据规模验收。

## 2026-11-27 本地实施与测量记录

关联 #450 / 当前累计 PR #850。风险 P1；未修改生产业务、医疗授权、数据库迁移及正式发布工作流。完整差异判定保留，不使用最后一次提交推断验收范围。当前宽范围草稿仍需要全量 Mock、真实 JAR 集成、PostgreSQL 与文档验收；完整候选被延期时，必需的 PR gate 明确失败，Ready for review 后重新要求完整候选。候选要求与延期状态均须有效输出，必要候选未选择时拒绝通过。

- 历史成功运行 45887753572：候选构建 30.7 分钟、完整 Mock 22.3 分钟，Quality Gate 各 job 墙钟合计 35.0 分钟；Pages 45887753527 合计 14.92 分钟，其中重复定向回归 13.2 分钟。合计 49.92 job-min 是墙钟代理量，不是实际计费分钟；不同操作系统、并发和排队不可直接相加为用户等待时间。
- 本地：Python 273 项，268 通过、5 既有跳过；三份工作流 actionlint 通过；视觉共享写入回归先红后绿，定向 43 项及扩大 84 项通过，包含预热且零重试。静态 Pages 构建与浏览器冒烟、231/231 Mock 静态路由覆盖、文档测试及双站构建通过。
- 固定 Nginx 1.31.8 / WinSW 2.12.0 原始下载物摘要，官方 HTTPS 下载与历史验收包 34 个文件比对一致，篡改单字节校验拒绝；这不是上游签名认证。浏览器缓存按 OS/架构/Playwright 版本隔离，命中仍检查系统依赖和安装可用性。不缓存候选制品、验证成功状态及凭据。
- 两分片对照：同一 Mac、独立源码副本/端口/Vite 缓存，共享只读安装依赖。43 个 Chromium 业务用例单次 60.31s（另含一次预热），双分片 22+21 个业务用例，墙钟 52.41s、进程时间合计 102.72s，精确覆盖且无重复/跳过/失败/重试。首次复制工作区 pnpm 交互式依赖检查阻塞，随后使用相同已安装入口完成。墙钟约下降 13%，进程时间代理量约上升 70%；不是独立 Linux Runner 实测或账单。暂不默认启用分片，完整规模远端测量尚待实施。
- 真实服务器同步场景仍保留，不宣称所有 Mock 可变状态已按 Worker 完全隔离。缓存远端命中、优化后完整 CI 与实际计费收益尚待核实。原未提交医疗权限测试保持逐字节一致，其失败不因本次 CI 优化而解除。

回滚：视觉测试隔离提交 `0f0cf85f` 与工作流/范围判定/缓存提交分别可逆；回滚工作流须同时回滚配套脚本、测试及约定，禁止仅移除必需门禁或缓存校验。证据位于本机独立 artifacts 目录，不纳入 Git。


### 远端两分片可选实验

现有 `frontend-mock` 手动入口增加 `shard_probe=true`（优先于 cross_browser），仅启动两个独立 Ubuntu Runner 的完整 Chromium 分片，均保持 2 worker、原预热和原 CI 重试；任一失败直接保留，另外一片不被 fail-fast 取消。两片分别保存同 SHA/run/attempt 的 JUnit，与同一合并模拟树的单次完整 Mock 结果对照时必须先核对源码树身份（PR merge SHA 与分支 SHA 可能不同）。比较两个分片用例集合的并集、交集、失败、重试、浏览器阶段墙钟及各 Job 时间之和；缓存冷热与 Runner 排队单独记录。此入口不产生 pr-gate 或发布产物、不影响默认日常及正式发布门禁。一次完整规模实验有额外 Runner 成本，不在每次推送重复执行。


补充验收：实际 `playwright --list --project=chromium` 单次清单 521 个唯一 Chromium spec ID，两个分片为 261+260，交集/遗漏/额外均零；预热分别保留。这仅为完整测试清单验证，非521个已执行通过。真实 CI 模式下本地隔离测试生成 JUnit 和 JSON 两种报告且零失败/重试；正常 Mock 与两分片入口均保存这两种报告，以 JSON spec ID、状态、尝试次数对照，JUnit 保持原有消费者兼容。新增门禁测试旧实现实际5失败，新增手动分片契约和报告契约分别先失败后通过；最终 Python 278项中273通过、5既有跳过，四份工作流 actionlint、ESLint 和工程文档通过。旧707a49d3的Pages已通过，未将旧SHA结果当成新提交验收。未提交医疗权限测试保持不变。

优化前最终对照（707a49d3）：GitHub 45929413272 完整 Quality Gate 与 Windows 恢复/候选总门禁成功，45929413251 Pages、治理和监控均成功。Pages build 20.65min（定向Mock18.68min、静态冒烟0.55min），361项通过、1项flaky；完整候选 build 26.65min（Mock19.83min、后端2.95min、前端1.90min、Chromium准备0.23min）。各Job与步骤实际时间来自已完成Job元数据；最终全量日志/总Runner时长仍需独立补取，不将旧SHA验收当成新SHA通过。


## 2026-11-27 首轮新版远端结果与 NAS 回归

47063b90 的治理、监控、Pages 通过；45932224231 Pages build 1.87min（旧20.65min，减少18.78min，静态冒烟32s仍保留）。Quality 45932224227 的 scope/development/e2e/docs 通过，release 在草稿阶段延期；integration/build 后端测试1309项1错误（NasArchiveImageSourceTest.consecutiveBodyFailuresTripNodeDespiteSuccessfulOpensAndPreserveHealthyNode），pr-gate 实际拒绝 integration 失败，不能解释为仅有意草稿延期。下载实际 Mock JSON/JUnit：单次业务521项，519首过、2 flaky（login-layout 动画及 users-query-feedback，分别2/3次尝试），预热1通过，无最终失败/跳过。E2E step 29.65min 高于旧19.83min，尚不能宣称整体提速。Docs job 0.58min。Pages去重收益不等于整条流水线等待时间或实际计费收益。

NAS 实际旧源码存在释放标志先于 semaphore.release 的竞争；单隔离器本地10000次未复现，随后旧源码四独立隔离器同条件压力测试实际抛出 CapacityException；仅同步同一次释放操作后共400000次通过，纳入新增回归，NAS/限流/熔断相关24项零失败/跳过。保留原超时、容量、迟到资源回收、权限检查；新源码须Linux复验，不将增量JaCoCo历史数据警告当成完整覆盖率验收。

手动双分片 45936230623 已提交并运行，head 47063b90，PR测试合并提交2b0b48be8a9ae527d3fe32db8fab3f8045475d07，两者Git tree 81d540a1f7f3d758cf94b554dc81dd468350722b 完全相同。保留原单次报告用于同树精确比较；分片尚未完成，不宣称独立Runner加速或稳定性结果。原权限测试逐字节保留。
