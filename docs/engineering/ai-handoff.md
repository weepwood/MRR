# AI 开发交接索引

更新基线：2026-11-24，main 71d36fd036f49909deeb431dd18f9d17258285fc（交接 PR 创建前）。此处为接续入口而非实时状态；新工具先核对实际 GitHub 状态。

## 当前交接快照（优先阅读）

[2026-11-24 生产稳定性专项完整交接](ai-handoff-2026-11-24.md)：维护者选定的开发顺序、已合并成果与 CI 证据、#763 失败根因、未完成事项、现场验收边界及接续任务。最新重点为 04 运维与存储可靠性；先处理 #763，再推进统一影像网关。以下为历史阶段索引，若与快照或实时 GitHub 状态冲突，以实时状态为准。

## 2026-11-24 本地接续（#763，P1）

- #764 已 squash 合并至 main 3bc4a36cffd062804178c6ab31e291b7bccb0e74；governance 45417633142、quality 45417635601 的 scope/pr-gate 成功；文档范围其他任务按计划跳过。原快照保留。
- #763 从 78290f25 接手，专属本地 worktree `/Volumes/data/Project/MRR-worktrees/230-migration-source-cache-eviction`，分支 `fix/230-migration-source-cache-eviction`；34b41afb 同步最新 main，仅两处文档冲突已处理。
- 测试先提交 a7990828，Java 21 / Maven Wrapper 定向 RED 实际运行 25 项，1 失败（success 被误标 retry_wait）、1 错误（afterCommit 异常外溢）、0 跳过；原 Mockito 严格参数夹具已修复。
- 实现 9ffe44a1：各缓存分别隔离 RuntimeException，继续清理剩余缓存，日志仅固定缓存名和异常类型；不改变已完成迁移结果、权限、SQL、TTL 或零行更新既有语义。GREEN 同一专项 25 项，0 失败/错误/跳过（本地 Java 21，`./mvnw -o -B -ntp -Dtest=MigrationServiceImplTest test`）。
- 状态：本地修复完成，最新远端 CI 待核验；未执行 PG16 集成、生产迁移、现场吞吐或正式发布。失效失败的单个缓存仍可能在 TTL 内保留旧值；在途旧查询回填以及每次遍历成本尚未解决，不能宣称完全一致性。合并前核对最新 head、main、P1 必需检查和风险；必要时另立统一缓存专项，不扩展到无关模块。
- 04.2 初查：ImageContentServiceImpl 直接使用 ImageContentMapper.findActiveById，不经 scanById 缓存；现有 ImageUrlService 已支持 direct/gateway，不改默认模式。后续核对遗留 URL 下发、授权、审计、流关闭和 Range，见 #230 与 ADR-0009。
- 本地已有 Homebrew JDK21 与 PG16，默认 Java 27，无 Docker CLI；使用隔离可销毁 PG16 或既有 CI，不使用医院库。#752/#722/#712 最新查询仍 mergeable=false，保留；后两项仍缺现场兼容证据。
- 回滚：独立 revert #763，不删除或回退已迁移 OSS 对象；不修改生产配置。接续先核验 GitHub 最新 CI 和上述未解决风险。

## #763 旧回填专项复验（2026-11-24）

- 7b9ed5d0 的 governance 45419250290、quality 45419224037 均成功；随后因确认旧回填竞态继续修复，不以旧 CI 作为新提交的合并依据。
- 82e03280 实际 RED：ScanServiceCacheTest 6 项，2 失败（单 ID evict、组合查询 clear 后在途旧值回填），0 错误/跳过。0c8beb34 复用原 Caffeine 原生缓存、键、TTL、容量和统计；五个非 sync Cacheable 查询使用 per-invocation CacheResolver 视图，失效与回填共享短临界区和缓存级 generation。迁移选择性失效、普通 evict/clear 递增 generation，阻止旧查询重填或覆盖新值；数据库查询不持锁，无关已缓存值保留。
- 实际 GREEN 31 项；扩展回归 `./mvnw -o -B -ntp -Dtest=ScanServiceCacheTest,MigrationServiceImplTest,ScanServiceImplTest,ScanServiceArchiveLookupTest,OssSignedUrlCacheTest test` 63 项全部通过，0 失败/错误/跳过。迁移提交回调覆盖五类真实代际缓存、空键旧回填、覆盖新值、无关缓存及签名缓存不变。
- 边界：在途旧调用仍可返回其自身读到的快照；每次失效保守丢弃该缓存其他在途加载的回填，并保留原四缓存遍历成本。普通写路径原事务/缓存切面提交顺序、外部进程及其他写路径未新增承诺。缓存提供者异常仍有旧数据风险；现场批量吞吐、并发命中率、真实 PG16 最新 CI 待验证。回滚独立 revert，不动已迁移对象。
- 最新 P1 提交必须重新核验 governance/pr-gate、后端及 PG16 实际报告；保持本地 worktree，不提前合并。

## 当前重点

| 任务 | 已核实状态 | 后续入口 |
| --- | --- | --- |
| GitHub Pages 演示数据引擎 | M1 审计、M2 首批、M3.1–M3.3 基础及 M3.4 首批已合并 | [#729](https://github.com/weepwood/MRR/issues/729)，[实施记录](mrr-demo-engine.md) |
| OSS 演示数据一致性 | #741、#742、#744、#745、#747 已合并；M3.6 与 M3.7 统计首批已合并 | [实施记录](mrr-demo-engine.md)，下一步任务执行与目录/对象浏览 |
| PR 收口 / 数据库性能 / 运维 |  #726 已合并 57bb88f；其余需核对最新合并树 | #728、#726、#721、#724、#722、#712（草稿） |
| 前端体验 | #743、#746 已合并，实际定向 E2E 通过 | #736、#737 后续体验优化 |
| V1.0 发布候选 | 未完成正式验收 | [治理规范](ai-assisted-development-governance.md) |

## 最近已验证的演示交付

- #734 OSS 路由去重与任务创建、取消、回读。
- #735 会话时间轴与近期筛选。
- #738 共享患者/扫描数据及初始审计关联；32 患者、64 扫描。
- #739 自包含 SVG、档案袋共享查询与缓存刷新；governance 45386348099、quality 45386348303、Pages 45386348091 均通过。生产 Chrome 86 构建按范围跳过，未完成正式发布验收。

- #741 共享统计与领取资格：e303f18，三项最新 CI 通过。
- #742 上传重试写回与合成影像：e3d3343，三项最新 CI 通过（见专题记录）。

## 待验收分支（本轮同步 SHA）

| PR | HEAD | 事项及状态 |
| --- | --- | --- |
| #763 | 78290f2 | OSS 来源缓存选择性失效，quality 45416755045 失败；先修复与复审 |
| #752 | 5da2f42 | 分类审核草稿，有冲突 |
| #722 | de997f4 | 患者 POST 协议，有冲突且缺现场兼容证据 |
| #712 | db3678e | 旧协议退役草稿，有冲突且缺现场兼容证据 |

上述状态仅为交接快照。#721 已合并 6757334（真实 PostgreSQL 集成通过）；#745 已合并 12cf734；#728 已合并 1e4dee3（后端单测及真实 PostgreSQL 集成）；#747 已合并 c201c68（真实 Pages 任务、分页、影像解码）；#749 已合并 57154d9（选择器/治理/Pages 通过，无 spec 变更故定向步骤跳过）。#724 已合并 e6377f8（运维定向 E2E 成功），#746 已合并 8c191cf（监控定向 5 项通过），#748 已合并 1084799（历史与统计 CSV 一致性 Pages 通过）。#743 已合并 45b0fc7（实际定向 2 passed）；#750 已合并 8d87cd8（真实 Pages 档案盒 CRUD）。#751 已合并 e196d80，三项 CI 成功。M3.6 新分支 feat/729-demo-history-scenarios，详见 [历史样本记录](mrr-demo-history.md)。

## OCR 专项最新交接

#751 已合并 e196d80，治理、质量及真实 Pages 测试均通过。#752 草稿自动转向 main，仍有冲突，原同步调用被安全检查拦截；保留现有分支，不绕过。#753 已合并 bdcdc3f，governance 45403837667、quality 45403837638、Pages 45403837620（实际定向 OCR 批次 E2E）均通过，见 [历史 OCR 批次记录](mrr-demo-ocr-batches.md)。整合时确保单病案、批次与分类审核共用 OCR 实例。后续原图预览、规则管理、影像导出与持久化。

## M6 契约门禁与阻塞

#754 已合并 e0ea092，governance 45404921525、quality 45404921082、Pages 45404921139 成功，六模块严格契约门禁生效。#752 草稿仍冲突，feat/729-archive-export-plan 当前只有导出测试 dfe5022，前次实现提交被安全检查拦截；未绕过。保留分支，待可用且合规的实施方式继续。#722、#712 未完成现场兼容验收。本轮不改生产配置或数据。

## M5 场景控制首批

#755 已合并 a990203，最新 6587ce9，governance 45407306971、quality 45407306989、Pages 45407306948 通过（真实场景切换、刷新与恢复默认）。见 [场景控制记录](mrr-demo-scenarios.md)。业务内容仍在内存，暂不新增演示专项。

## 接续顺序（维护者最新选择）

维护者选定 01 历史 PR 与工程欠账、03 三千万级数据库、04 运维存储可靠性、07 V1.0、09 影像档案袋与导出。执行次序 01→03→04→09→07；05 演示与06 UI 不再作为新增开发专项，保留既有 PR 收口。

| 阶段 | 首批任务与验收 |
| --- | --- |
| 01 | #755 已合并 a990203（真实 Pages 测试通过）；#723、#727 已按合并与 CI 证据关闭；#752 草稿冲突，既有同步操作被工具拦截，保留；#722、#712 最新查询均冲突，旧 CI 不代表当前合并树，兼容退役需现场证据，不提前合并。逐项核对 #736、#737、#717、#719 历史验收，不以 merged 自动关闭所有 Issue。 |
| 03 | #756 已合并 f836ff3，governance 45407989735、quality 45407989745（真实 PG16 136项，锁等待7项，无失败或跳过）。#99 后续 P1 分支 perf/99-scan-last-page-count，非空短末页免 COUNT、溢出拒绝；测试先提交 7492b24（RED 未执行），见 [扫描查询时限与精确计数](scan-legacy-query-timeout.md)。#757 已合并 3fe158b，governance 45408967982、quality 45408967772（单测1118，PG16集成137，均零失败/错误/跳过）。#758 已合并 3b0a05b，governance 45409847390、quality 45409847512（后端1118、PG16集成140、索引就绪6项均通过）。#759 已合并 676b6dd，governance 45410672727、quality 45410673024（单测1118、PG16集成143、构建专项6项均通过）。#760 已合并 114b448，governance 45411756024、quality 45411756050（1118单测、144 PG16集成、ScanMapper14项均通过）。#761 已合并 4ffab09，governance 45414401426、quality 45414401584（实际Python256项，新增3项通过）；#762 已合并 71d36fd，governance 45415453201、quality 45415453095（1121单测、144 PG16集成、迁移19项均通过）；迁移汇总缓存代际及提交后失效，见 [运维手册](mrr-data-operations-runbook.md)。千万级合成数据基准与病案缓存失效/并发回源仍待实施；现有演示造数上限不放宽。 |
| 04 | #763 当前失败，详细复现、风险及接续步骤见 [完整交接](ai-handoff-2026-11-24.md)。#230、#694、#636、#350：统一网关剩余路径、日志分区一致性与恢复、Nginx 只读诊断、容量与备份演练；生产切换另行批准。 |
| 09 | #171、#519、#523、#524：复核现有 ZIP/PDF 状态、取消/过期/下载预算、选页与预览生命周期，真实混合来源及 Range 验收。 |
| 07 | #350、#619：固定 SHA，复跑全量候选、PG16、真实旧浏览器、Windows 恢复、产物身份与升级/回滚；未完成现场证据不得发布 V1.0。 |

每批独立 PR、中文提交、按风险分级执行测试、AI 自审与最新 SHA 检查，持续更新专题文档与关联 Issue。P1 合并前同步最新 main；不得降低门禁或执行生产操作。历史轮次使用 GitHub API 独立分支；本地接续见上方专属 worktree 记录。

## 04.2 统一影像网关专项

- #230 P1 分支 `fix/230-gateway-pending-stream`（专属 worktree `/Volumes/data/Project/MRR-worktrees/230-gateway-pending-stream`），从 main 3bc4a36c 建立。异步调度拒绝前已打开上游影像流，原回调未运行导致资源泄漏。e6967007 实际 RED 4项/1失败；a204eed7 采用 Spring MVC CallableProcessingInterceptor 与原 try-with-resources 所有权交接，完成/超时/错误时仅关闭尚未被任务领取的流；外部未启动任务记录一次 FAILED 审计，晚到任务不再读流。GREEN 14项，扩展26项均零失败/错误/跳过。最新 P1 CI 待核验。
- 保留既有权限、票据、审计、来源顺序和 direct 默认配置。已开始的任务仍负责关闭源，不提前释放许可。真实 Tomcat 客户端断开、现场并发和大文件 Range 未验收；当前网关为全量200流，不伪造206。遗留 OSS 管理签名URL及直连页面需区分管理端与受控病案下发，单独核对兼容。回滚独立 revert。
- #230 P1 分支 `fix/230-active-legacy-image-urls`，worktree `/Volumes/data/Project/MRR-worktrees/230-active-legacy-image-urls`。fb6836af 实际 RED 40项/2失败：软删除影像仍返回普通URL和OSS签名。修复复用 ScanService/ScanMapper.findActiveByIds，不读缓存的无过滤记录；普通URL、旧OSS跳转和单记录签名拒绝删除/不存在/非法ID，有效记录保留原行为。扩展66项全部通过；新增实际 PG16 有效/删除记录查询测试，最新CI待执行。遗留四段路径 `/api/v1/img/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME}` 仍使用路径参数，开发者兼容允许名单依赖此入口；不得在没有调用方证据时直接关闭，下一步设计有界有效记录定位/受控退役及日志脱敏。OSS目录浏览/迁移日志签名与普通调阅分开核对。影像网关当前流式200，不支持Range；导出文件的 HttpByteRange 依赖可随机访问文件，不直接复用到未验证的多来源流。后续应按实际所选来源长度和能力支持单区间、416/If-Range、断开关闭及鉴权审计，不做无界跳读或虚假206。回滚独立 revert。

## 每轮交接模板

- 更新时间、任务、风险、目标及非目标：
- 当前 main / 工作分支 / PR / head SHA：
- 已完成（含证据）：
- 实际执行检查与结果 / 未执行项：
- 未完成、阻塞、关键决策及原因：
- 下一步（精确到模块或文件）、回滚方式：
- 工作区状态（无本地 worktree 时明确说明）：

不复制真实病案、凭据、敏感内网配置；不将 CI 运行中写成通过。历史以 Git 和 Issue 为准。
