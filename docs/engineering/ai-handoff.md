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
| 04 | #763、#765、#766 已合并；#767 已合并 297419bf，详见下方 04.2/04.3 及 [历史交接](ai-handoff-2026-11-24.md)。#230、#694、#636、#350：统一网关剩余路径、日志分区一致性与恢复、Nginx 只读诊断、容量与备份演练；生产切换另行批准。 |
| 09 | #171、#519、#523、#524：复核现有 ZIP/PDF 状态、取消/过期/下载预算、选页与预览生命周期，真实混合来源及 Range 验收。 |
| 07 | #350、#619：固定 SHA，复跑全量候选、PG16、真实旧浏览器、Windows 恢复、产物身份与升级/回滚；未完成现场证据不得发布 V1.0。 |

每批独立 PR、中文提交、按风险分级执行测试、AI 自审与最新 SHA 检查，持续更新专题文档与关联 Issue。P1 合并前同步最新 main；不得降低门禁或执行生产操作。历史轮次使用 GitHub API 独立分支；本地接续见上方专属 worktree 记录。

## 04.2 统一影像网关专项

- #230 P1 分支 `fix/230-gateway-pending-stream`（专属 worktree `/Volumes/data/Project/MRR-worktrees/230-gateway-pending-stream`），从 main 3bc4a36c 建立。异步调度拒绝前已打开上游影像流，原回调未运行导致资源泄漏。e6967007 实际 RED 4项/1失败；a204eed7 采用 Spring MVC CallableProcessingInterceptor 与原 try-with-resources 所有权交接，完成/超时/错误时仅关闭尚未被任务领取的流；外部未启动任务记录一次 FAILED 审计，晚到任务不再读流。GREEN 14项，扩展26项均零失败/错误/跳过。#765 已合并 c0b0f51e，最新 CI 证据见 04.3。
- 保留既有权限、票据、审计、来源顺序和 direct 默认配置。已开始的任务仍负责关闭源，不提前释放许可。真实 Tomcat 客户端断开、现场并发和大文件 Range 未验收；当前网关为全量200流，不伪造206。遗留 OSS 管理签名URL及直连页面需区分管理端与受控病案下发，单独核对兼容。回滚独立 revert。
- #230 P1 分支 `fix/230-active-legacy-image-urls`，worktree `/Volumes/data/Project/MRR-worktrees/230-active-legacy-image-urls`。fb6836af 实际 RED 40项/2失败：软删除影像仍返回普通URL和OSS签名。修复复用 ScanService/ScanMapper.findActiveByIds，不读缓存的无过滤记录；普通URL、旧OSS跳转和单记录签名拒绝删除/不存在/非法ID，有效记录保留原行为。扩展66项全部通过；新增实际 PG16 有效/删除记录查询测试，PR #767 已合并 297419bf，head 898e14b6，governance 45422378378、quality 45422378563 成功，实际1141单测、145 PG16集成（ScanMapper 15项）均零失败/错误/跳过。遗留四段路径 `/api/v1/img/image/{BAH}/{BRXH}/{FOLDER}/{FILENAME}` 仍使用路径参数，开发者兼容允许名单依赖此入口；不得在没有调用方证据时直接关闭，下一步设计有界有效记录定位/受控退役及日志脱敏。OSS目录浏览/迁移日志签名与普通调阅分开核对。影像网关当前流式200，不支持Range；导出文件的 HttpByteRange 依赖可随机访问文件，不直接复用到未验证的多来源流。后续应按实际所选来源长度和能力支持单区间、416/If-Range、断开关闭及鉴权审计，不做无界跳读或虚假206。回滚独立 revert。

## 04.3 受控影像来源诊断

- #230/#636 P1 分支 `fix/230-controlled-source-diagnostics`，worktree `/Volumes/data/Project/MRR-worktrees/230-controlled-source-diagnostics`。6b6edf25 实际 RED 1项/1失败：旧 direct HEAD 跟随重定向，访问跳转目标。修复拒绝3xx并区分权限拒绝、不存在、超时、繁忙、HTTP失败；保留 direct 模式兼容，不自动切换。
- gateway 诊断复用 ImageContentMapper.findActiveById（软删除/不存在不探测）、PathDO.fromScan、ResolvedImageStorage 的实际有序候选、现有认证和并发许可；每个尝试返回固定来源与错误分类，选中流立即关闭，不签发 URL、不额外 HEAD。OSS SDK 状态、Nginx 403、NAS 文件与许可、超时、脱敏及诊断权限专项实际 GREEN 21项，扩展37项零失败/错误/跳过。
- 探测范围 OPEN_AND_CLOSE，只证明流可打开关闭，不验证完整内容或真实网络吞吐；源适配器现有超时和许可保持，NAS 文件系统及 SDK 关闭可能阻塞/读取剩余数据，尚无统一硬截止时间。无真实病案/生产源访问，不声称现场验证。PR #766 已合并 f5ccc03f，最新 head 85007d4f，governance 45421600547、quality 45421600674 成功，实际1137单测及144 PG16集成均零失败/错误/跳过；回滚独立 revert，不改配置、数据、来源优先级或权限。
- #763 已合并 8c68eca9；最新 head 8ee2307a，governance 45420699666、quality 45420061742 实际1129单测、144 PostgreSQL16.16集成，零失败/错误/跳过。#765 已合并 c0b0f51e，head c36379a8，governance 45421013514、quality 45421013440 实际1131单测零失败/错误/跳过，PG16按范围跳过（无数据库改动）。两项均为 AI 自审非独立审批。现场吞吐、真实 Tomcat 断开、Range、旧直连管理端兼容和来源网络隔离仍未完成。

## 04.2 影像 Range 首批（2026-11-24）

- #230 P1，分支 `feat/230-image-range`，worktree `/Volumes/data/Project/MRR-worktrees/230-image-range`。68bc3350 实际 RED 5项/1失败（真实临时文件经NAS适配器，Range返回200）；5e68e84b 引入同一打开句柄长度/定位及内外网关共用响应协议。同步 main 297419bf 后，本地47项零失败/错误/跳过，Java21 Maven Wrapper；#768 已合并 479712c1，head 1ea4c43a，governance 45423606714、quality 45423606729 成功，实际1145单测、145 PG16集成均零失败/错误/跳过。
- LOCAL/NAS 使用 FileChannel 获取实际打开文件长度、单区间有界读取、206/416、后缀区间、无整图缓存，保留许可所有权及异步流释放。Nginx/HTTP/OSS 从实际 GET 元数据获取长度（未知则不发送），不做额外HEAD，不依据可能过期的数据库长度。非随机访问源忽略Range返回200/Accept-Ranges:none；多区间、未知单位、任何无法校验的If-Range保守200。未提供强ETag或Last-Modified，不声称支持跨版本断点续传。外部先授权再读，成功及失败审计不变，416关闭流。
- 已执行真实本地临时文件/模拟NAS配置、模拟远端响应、客户端输出异常、异步拒绝与外部审计；非真实医院NAS/OSS/Nginx、Tomcat中断、Windows及大文件吞吐验收。远端原生Range、可靠强校验器、流中内容变动与全局超时仍待专项。独立revert，不改数据库、配置或默认下发模式。

## 04.2 遗留直连治理首批（2026-11-24）

- #230/#350 P1，分支 `fix/230-legacy-image-lookup`，worktree `/Volumes/data/Project/MRR-worktrees/230-legacy-image-lookup`。5b0fd20c 实际 RED 16项/1失败（无有效记录仍能打开影像）；548995b0 通过 ImageContentMapper 精确匹配 BAH/BRXH/folder/filename，利用现有索引，LIMIT 2、3秒查询超时，拒绝无有效记录404、歧义409及非法路径400。高位病案号无法由四段路径确认上架号时拒绝，不猜测病案。保持已有路由和权限，使用完整 Scan 记录及统一来源解析，绑定审计目标，不再直接拼接物理路径。
- d2eec5f0 实际 RED 9项/1失败（网关模式仍返回 OSS 签名）；67f7e24c 网关模式旧单记录签名接口返回409，direct 模式保留原行为。管理端目录浏览、迁移日志等其他签名入口不在本批改动范围，须单独核验调用方和权限。
- e4df1511 同步 #768 后复用 ImageContentResponse，统一真实长度、Range及关闭语义；最终定向43项零失败/错误/跳过，之前扩展45项通过。#769 原head 8f4c1fd0，quality 45425139606：1147单测通过、146 PG16中1失败；本地全新PG16.16实际复现16项1失败。JdbcTemplate写入绕过MyBatis事务内一级缓存；补充SQL实际候选数3的断言、清理测试会话缓存后仍严格要求LIMIT 2，PG16全部16项通过（0失败/错误/跳过），业务SQL不改。已合并 #769：35bbae75，head be2269ac，governance 45426127129、quality 45426127420 成功，1147单测、146 PG16集成零失败/错误/跳过。未进行真实旧客户端现场兼容验收，不关闭原路由；独立revert，不改数据库或生产配置。

## 04.3 来源超时与取消首批（2026-11-24）

- #230/#636/#350 P1，分支 `fix/230-source-cancellation`，worktree `/Volumes/data/Project/MRR-worktrees/230-source-cancellation`。848bfb49 实际 RED 6项/2失败（取消后仍回源，未读OSS流关闭未abort）；9ddc4731 引入单次来源解析/open/size共享单调时钟预算，默认120秒（ARCHIVE_IMAGE_SOURCE_OPEN_BUDGET），约束许可等待、HTTP/Nginx请求及OSS API调用/attempt超时。取消保留线程中断并停止回源；过预算返回的迟到流立即关闭，许可随关闭释放。
- OSS未读/部分读取关闭时abort，完整消费正常close，连接获取超时10秒；b54c3a44 同步 #768 解决 OssObjectReader 冲突，保留实际GET长度和取消包装。首轮30项、同步后联合49项零失败/错误/跳过；SDK关闭行为模拟验证、NAS迟到流为模拟，不是实际网络阻塞验收。
- 预算不是流式正文传输、SDK关闭或原生文件系统调用的硬截止时间。NAS不可中断阻塞、真实连接池回收与断开、按节点熔断/半开及现场压测尚未完成，避免把404/权限失败当成全局来源故障。不变更来源优先级、权限、默认下发模式或生产配置。原head 8f1ff624：governance 45425159179、quality 45425159324 成功，1148单测、145 PG16集成零失败/错误/跳过；同步 #769 后须核验新CI；独立revert。

## 03 合成扫描性能实验（2026-11-24）

- #99/#350，分支 `perf/99-synthetic-query-lab`，worktree `/Volumes/data/Project/MRR-worktrees/99-synthetic-query-lab`。新增独立实验工具、实际百万行PG16证据和现有缓存并发回源基准，详见 [合成扫描实验](synthetic-scan-benchmark-2026-11-24.md)。实际100万扫描/1万主档，两个表达式索引构建、同页及1/8客户端pgbench完成；完善脚本后1万行冒烟成功；两实例停止。真实Caffeine代理+模拟Mapper 8冷回源/8热零回源，ScanServiceCacheTest 7项通过。1000万/3000万、同键回源合并、真实端到端/现场容量仍待验证。不改生产、现有索引脚本和演示造数上限，独立revert。
- #770 已合并 20d29651，head 165c0558，governance 45426949813、quality 45426949942 成功，实际1150单测、146 PG16集成，零失败/错误/跳过。其他待合并分支以实际GitHub为准。

## 04.3 影像正文传输预算（2026-11-24）

- #230/#350 P1，分支 `fix/230-image-transfer-budget`，worktree `/Volumes/data/Project/MRR-worktrees/230-image-transfer-budget`，56d2ccc4 实际RED 5项/1失败（超过截止时间返回的字节仍写出）；4c60fc59 采用单调时钟及16KiB逐块读写、前后预算/中断检查，默认 `ARCHIVE_IMAGE_SOURCE_TRANSFER_BUDGET=PT120S`，从构造响应起含异步等待。d04011ce 同步 main 20d29651 保留打开/正文两项配置，最终35项零失败/错误/跳过；外部失败审计与流关闭保留。所有网关含旧路径复用现有协议。
- 预算为协作式截止，不能强行结束已阻塞的原生读、网络写或close；已提交的响应不能改写为新HTTP错误。未完成真实Tomcat/Windows/NAS/OSS中断、连接池及大文件压力验收。无权限、来源顺序或生产配置变更，独立revert。#770已合并20d29651，最新165c0558，governance45426949813、quality45426949942，1150单测、146 PG16均零失败/错误/跳过。

## 04.3 NAS原生打开隔离与节点熔断（2026-11-24）

- #230/#350 P1，分支 `fix/230-nas-open-isolation`，worktree `/Volumes/data/Project/MRR-worktrees/230-nas-open-isolation`。25a46204 实际RED 4项1错误（等待原生模拟阻塞的请求未及时返回）；cfaa8930 有界独立线程池与每配置节点槽位，默认 `ARCHIVE_IMAGE_SOURCE_NAS_OPEN_TIMEOUT=PT30S` 且受打开共享预算约束，取消/超时后迟到结果由工作线程关闭，源许可不提前释放。全局工作线程上限沿用nasMaxConcurrency，节点槽位按配置节点数分配（至少1）；节点数超过全局工作线程数时不能保证每个健康节点有独立线程。
- ce29e67f 仅对NAS实际进入原生调用后的超时计数，默认3次超时/30秒冷却/单探针半开；成功恢复，旧代际不改变新状态，文件缺失、访问拒绝、未开始I/O的排队或许可饱和不触发熔断，半开中性结果不证明恢复。状态局限于本进程配置节点，重启清零，固定安全分类，不泄露根目录。
- 同步main 20d29651 后联合34项出现2错误，其中真实原因是成功交接后节点槽位释放竞争，另一个为Mockito连带未完成stub；b70abe57 以一次性释放及调用方成功移交同步处理，保留原资源许可，最终34项零失败/错误/跳过。首轮26项与熔断28项通过。真实Windows/NAS native阻塞、正文read/write/close不可中断、更多节点并发、网络源熔断尚未验收，不承诺操作系统硬取消。独立revert，无生产配置、来源顺序或权限修改。#770已合并20d29651，最新165c0558，1150单测/146 PG16通过；本PR最新CI待核验。

## 03 同键并发回源合并（2026-11-24）

- #99/#350 P1，`perf/99-scan-single-flight`，worktree `/Volumes/data/Project/MRR-worktrees/99-scan-single-flight`。#771已合并 e17d87e1，head d1b2cbe6，governance45428334878、quality45428335084，1151单测零失败，PG16按范围跳过，CI实测冷键8次/热键0次回源。fb40719e实际RED 9项1失败，预期1实际8；cd38d173复用GenerationAwareScanCache同代际同键加载，最多1000个在途登记（满后按普通查询执行）、数据库查询不持有全局锁；五个原注解使用自定义同步加载，空/空列表/零条ArchiveLookupResult不缓存，异常通知等待者并移除登记，旧代际不加入新请求或回填。未更改缓存键、TTL、签名缓存及DB语义。
- 实际53项定向通过（ScanServiceCacheTest、MigrationServiceImplTest、ScanServiceArchiveLookupTest、ArchiveExportSummaryServiceTest），模拟Mapper冷并发8→1、热0；新代际不等待旧阻塞加载、异常/空结果后重查均通过。非真实端到端吞吐；等待者受首请求实际SQL耗时约束，不声称独立硬超时。同步main dae8bef7，最新CI待验收；独立revert，无生产变更。

## 04.3 HTTP/Nginx 正文停发及实际SDK中止（2026-11-24）

- #230/#350 P1，分支 `fix/230-http-body-idle-timeout`，worktree `/Volumes/data/Project/MRR-worktrees/230-http-body-idle-timeout`。e5dc75a0 实际RED 2项1错误：loopback HTTP 返回响应头及首字节后停发，原BodyHandlers.ofInputStream读取不能在3秒内完成。7e4668bd 用JDK BodySubscriber单次需求、2项有界事件队列、空闲读取超时，复用各节点既有readTimeout，取消subscription，不新增每读工作线程；保留实际长度、资源许可、认证和来源顺序。24项回归通过。
- 增加真实AWS SDK loopback S3协议响应首字节后停发、部分读取关闭abort、下一对象在5秒内读取，非生产凭据/服务；扩展28项零失败/错误/跳过，原始测试 /tmp/mrr-http-oss-final.log。仅证明本机HTTP/SDK路径，并非真实Nginx/NAS/OSS多节点连接池压力或正文网络写硬截止。网络来源按节点熔断待#773合并后复用其状态逻辑，避免建立第二套机制。同步main dae8bef7，最新CI待验收；独立revert，无生产改动。

## 09 导出断点续传与逐块取消（2026-11-24）

- #171/#519/#523/#524/#350 P1，分支 `fix/230-export-download-if-range`，worktree `/Volumes/data/Project/MRR-worktrees/230-export-download-if-range`。0fb988c7实际RED 1项1失败（旧If-Range仍返回206）；9981bc59 使用已有SHA256强ETag，不匹配/无法校验回退200，匹配仍206；完整和区间下载严格按声明长度输出，提前EOF报错，下载次数日志仅异常类型。保留任务归属、权限、过期及既有单文件配额。
- a478aed7实际RED 11项1失败（PDF收到取消后读取5次，期望1）；5f0f639d 在既有readNBytes单图100MiB上限内逐块检查取消，保持输出大小限制。实际36项联合通过。b0a90bb0 新增真实本地临时NAS+loopback HTTP混合来源ZIP条目内容和PDF两页解析，扩展52项零失败/错误/跳过，覆盖任务策略、归属、恢复、失败及预检。并非真实医院混合源或并发下载配额验收；未强制中止已阻塞的原生读写。
- #771已合并e17d87e1，#772已合并dae8bef7（1152单测/146 PG16），#773已合并8d7f2ece（1155单测/146 PG16）。本分支需同步最新main并复验后合并；独立revert，无生产改动。

## 03 多键尾延迟及千万级尝试（2026-11-24）

- #99/#350 P2，`perf/99-scan-multikey-benchmark`，worktree `/Volumes/data/Project/MRR-worktrees/99-scan-multikey-benchmark`。b6b99b90 增加逐事务日志与P50/P95/P99、批次已确认进度、失败样本校验。实际10万行多键，1/8客户端65433/230263样本、P99 0.234/0.981ms，零失败、实例停止。实际1000万尝试触发360秒总预算，确认790万批次、报告incomplete且无计划，pg_ctl确认停止；3000万未启动。详见 [合成扫描基准](synthetic-scan-benchmark-2026-11-24.md)。无生产操作，独立revert，保留实验报告。
- #771合并e17d87e1（1151单测，PG16按范围跳过）；#772合并dae8bef7（head45943a77，governance45429093867、quality45429094026，1152单测/146 PG16）；#773合并8d7f2ece（head06e4e749，governance45429869305、quality45429869300，1155单测/146 PG16）。均为AI自审非独立审批，真实现场仍待验收。

- 2026-11-25 第二次1000万实验真实COUNT确认10000000扫描、100000主档，写入372.8秒、已有表索引5919105024字节，6个索引前精确计划完成，兼容模糊COUNT触发5秒语句超时，未构建表达式索引；报告incomplete、实例确认停止。fc92747b记录该证据并仅对可选兼容COUNT明确语句超时继续其他独立测量，保留5秒上限，不视为完整验收。3000万未启动。

- 新版本1万行多键冒烟完整完成（mrr-scan-lab-8tud2a6t），实例停止；未实际触发超时分支，千万级仍需再次完整复验。#776已合并913acd6e，head4a7a6959，governance45433259307、quality45433259445，1162单测/146 PG16零失败。

## 03 缓存多键并发实库回归（2026-11-25）

- #99/#350 P1，`test/99-scan-cache-concurrency`，worktree `/Volumes/data/Project/MRR-worktrees/99-scan-cache-concurrency`。#774 已合并 f2056cdd，head6345e19a、governance45430947318、quality45430948538，1157单测/146 PG16均通过，CI冷8→1、热0。新增不同键独立加载回归，本地ScanServiceCacheTest 11项零失败/错误/跳过（8等待者共享一次异常、下一次加载恢复通过）；新增真实PG16 MyBatis+Spring Cache 两键16线程/64请求、两轮共128次、实际导入提交后更新可见的测试。本地单独PG16启动命令被工具安全检查拦截，未绕过，新集成用例待CI真实执行，不能宣称已通过。仅测试，独立revert，无生产操作。

- 原head 2c50e95c，quality45433926281，1161单测及147真实PG16零失败/错误/跳过；新实库并发用例在CI执行通过，主线变更后再次验证。#777合并21c8b960。

## 04.3 网络节点熔断复用（2026-11-25）

- #230/#350 P1，`fix/230-network-source-circuit`，worktree `/Volumes/data/Project/MRR-worktrees/230-network-source-circuit`。eba877bd 修正夹具后实际RED 6项1失败，重复OSS超时第四次仍回源；4b54789d 将NasNodeCircuit泛化为SourceNodeCircuit，HTTP按配置节点、Nginx按已验证authority、OSS按单配置endpoint/bucket各自状态，3次源故障/30秒冷却/单探针；保留NAS原隔离。只计明确连接/读超时、连接拒绝、SDK调用超时及502/503/504、声明长度提前EOF；401/403/404、许可饱和、本地预算耗尽、取消/提前close中性；完整正文或成功HEAD才确认恢复。无新增依赖或公开节点地址。
- 本地29项及新增真实HTTP两节点503后拒绝第四次/健康节点仍读取的30项联合回归通过；同步 #775 正式合并 main 5612d503 并解决测试冲突，b85653db，最新CI待验收。#775 head f7b8e0a1，governance45432184945、quality45432184906，1159单测零失败/错误/跳过、PG16按范围跳过。未验收现场多节点、连接池压力、原生NAS正文/慢客户端硬阻塞，独立revert，不改生产。

- #778已合并6454b5a4，head55d792ef，quality45435023953，1164单测及147 PG16零失败/错误/跳过。#779原head a6612f36质量45433783245实际1161单测通过，PG16按范围跳过，最新合并树待验收。

## 03 兼容模糊游标分页（2026-11-25）

- #99/#350 P1，分支 `perf/99-legacy-cursor`，worktree `/Volumes/data/Project/MRR-worktrees/99-legacy-cursor`。4c90d9d9实际RED 18项1失败（精确游标忽略文件名条件）。现有 /api/v1/scan/cursor/condition 增加显式 legacyContains=true，复用旧LIKE及全部筛选谓词、id游标、size+1、不执行COUNT，3秒Mapper语句超时不变；默认精确游标仍为精确编号，遇不支持的非空筛选明确400。旧OFFSET/COUNT接口及其结果语义不变。未修改Flyway或生产索引。
- 实际51项定向零失败/错误/跳过；新增真实PG16包含/前导零/附加筛选、同页OFFSET等价测试，待CI执行。尚未迁移前端或验证千万级模糊游标的稀疏结果性能，不能声称解决旧COUNT瓶颈；后续隔离库评估pg_trgm。main 6454b5a4，#778已合并，1164单测/147 PG16通过。独立revert，无生产操作。

## 04.3 NAS正文原生操作隔离（2026-11-25）

- #230/#350 P1，分支 `fix/230-nas-body-isolation`，worktree `/Volumes/data/Project/MRR-worktrees/230-nas-body-isolation`。8ae21669实际RED 5项1错误：模拟不响应中断的原生正文read阻塞调用方；b9e5ff15 每个已许可的NAS流一个串行原生I/O所有者，16KiB私有缓冲，读取/seek/close独立等待预算，超时不取消原生任务或提前释放许可，排队一次真实close；保留真实长度与Range seek。新增 ARCHIVE_IMAGE_SOURCE_NAS_BODY_TIMEOUT 默认PT30S。实际36项联合零失败/错误/跳过，覆盖迟到读取、许可持有/恢复、Range、网关及ZIP/PDF。同步#779正式main 8f598234，最新CI待验收。
- #779已合并8f598234，head e29c6297，governance45435859334、quality45435859775，1166单测零失败/错误/跳过，PG16按范围跳过。原生正文仍可能永久挂起，实际许可保持直到真实关闭；不声称OS硬取消、Windows/NAS现场测试或Tomcat慢客户端硬截止。正文故障计入节点熔断仍需单独验证；独立revert，无生产变更。

- 追加实际14项定向回归全部通过：模拟原生close忽略中断，超时后重复关闭一次、许可在实际close前不释放；真实嵌入Tomcat+Spring MVC+loopback TCP客户端停止读后RST，流真实关闭、失败审计、下一请求成功；实际AWS SDK Apache 2连接池饱和，第三请求未到源站，部分响应abort后恢复。仅测试配置，非生产50连接池、非真实NAS、非持续静默客户端硬截止。#781已合并38ed106c（1169单测/148 PG16）。最新CI待验收。

## 每轮交接模板

- 更新时间、任务、风险、目标及非目标：
- 当前 main / 工作分支 / PR / head SHA：
- 已完成（含证据）：
- 实际执行检查与结果 / 未执行项：
- 未完成、阻塞、关键决策及原因：
- 下一步（精确到模块或文件）、回滚方式：
- 工作区状态（无本地 worktree 时明确说明）：

不复制真实病案、凭据、敏感内网配置；不将 CI 运行中写成通过。历史以 Git 和 Issue 为准。
