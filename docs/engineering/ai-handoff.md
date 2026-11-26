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

## 09 下载并发配额与临时文件租约（2026-11-25）

- #171/#519/#523/#524/#350 P1，`fix/230-export-download-leases`，worktree `/Volumes/data/Project/MRR-worktrees/230-export-download-leases`。78d3a240修正macOS路径夹具后实际RED 2项1失败（第三次并发请求未拒绝）；17737864引入ArchiveExportTempFileManager实际FileChannel租约，默认全局8/每用户2（ARCHIVE_EXPORT_MAX_CONCURRENT_DOWNLOADS、ARCHIVE_EXPORT_MAX_CONCURRENT_DOWNLOADS_PER_USER），受现有单文件配额、权限和过期约束；活跃租约阻止过期清理，持实际句柄的长度/Range；429拒绝、416/异步拒绝/客户端写入失败释放，一次性close；复用PendingImageStream所有权拦截。
- 本地43项、9a5969ac新增全局跨用户与异步拒绝后44项零失败/错误/跳过；同步 #776 正式main 913acd6e，最新CI待验收。当前配额为单进程、仅异步导出下载入口，不覆盖其他同步下载接口；默认数值需现场负载评估，真实Windows删除语义、Tomcat慢客户端写阻塞及多实例不在本次已验收范围。独立revert，无生产变更。#776 head4a7a6959、governance45433259307、quality45433259445，1162单测/146 PG16通过。

- #781已合并38ed106c，head c5a95683，quality45436736527，1169单测及148 PG16零失败/错误/跳过。

## 03 千万级完整基准证据（2026-11-25）

- #99/#350 P2，main21c8b960实际1000万合成PG16运行，报告complete_with_legacy_timeouts，10000000扫描/100000主档，造数350.102秒、索引40.047秒，两索引各301MB valid/ready，同页通过，8并发随机多键673982事务、P99 0.753ms、零失败；5秒兼容COUNT超时明确记录，实例已停止。详见 [基准报告](synthetic-scan-benchmark-2026-11-24.md)，3000万未启动、不宣称生产验收。独立文档分支 `docs/99-ten-million-evidence`。
- #777已合并21c8b960；#778已合并6454b5a4（1164单测/147 PG16）；#779已合并8f598234（1166单测，PG16按范围跳过）。真实pg_trgm容量、写入代价、稀疏关键词及兼容游标待专项验收。

- #780已合并039307d3，head947b937d，quality45438121029，1171单测/148 PG16零失败/错误/跳过，Development通过。

## 03 前端兼容游标及模拟场景修复（2026-11-25）

- #99/#350 P2，`feat/99-records-compatible-cursor`，worktree `/Volumes/data/Project/MRR-worktrees/99-records-compatible-cursor`。#781 已合并38ed106c，1169单测/148真实PG16通过。eca7435a 实际RED 6项1失败；f5a4ae57 接入 legacyContains 显式选择、提交条件快照、游标重置及加载保护，默认精确、旧模式条件不混用，不新增COUNT。记录页原已使用游标。演示数据补充SQL LIKE及前导零语义。
- 51fb751d 修复 Node 模拟模块直接读取不存在的 import.meta.env.MODE；真实 bundle-import 原TypeError，修复后六模块全部加载成功，保持认证不变。固定Node24.15.0，实际 Chromium 7项（含预热）全部通过，覆盖兼容查询、翻页、条件变化、窄屏双主题。定向Vitest、类型及lint实际通过。最初Node26与固定Node24浏览器均失败，定位修复后重跑通过。依赖安装恢复corepack并保留原Git hooks检查。无生产变更；独立revert。最新PR CI待验收。
- #780 已合并039307d3（1171单测/148 PG16），#783 已合并92750126（文档范围scope/pr-gate通过，其他跳过）。30m实验另有独立日志，尚不可视作验收通过。

- #785 首次治理45439870682因PR正文缺少六个标准章节失败，已修正正文并以本次文档提交触发重新验收；未修改门禁。

## 04.3 NAS正文原生操作隔离（2026-11-25）

- #230/#350 P1，分支 `fix/230-nas-body-isolation`，worktree `/Volumes/data/Project/MRR-worktrees/230-nas-body-isolation`。8ae21669实际RED 5项1错误：模拟不响应中断的原生正文read阻塞调用方；b9e5ff15 每个已许可的NAS流一个串行原生I/O所有者，16KiB私有缓冲，读取/seek/close独立等待预算，超时不取消原生任务或提前释放许可，排队一次真实close；保留真实长度与Range seek。新增 ARCHIVE_IMAGE_SOURCE_NAS_BODY_TIMEOUT 默认PT30S。实际36项联合零失败/错误/跳过，覆盖迟到读取、许可持有/恢复、Range、网关及ZIP/PDF。同步#779正式main 8f598234，最新CI待验收。
- #779已合并8f598234，head e29c6297，governance45435859334、quality45435859775，1166单测零失败/错误/跳过，PG16按范围跳过。原生正文仍可能永久挂起，实际许可保持直到真实关闭；不声称OS硬取消、Windows/NAS现场测试或Tomcat慢客户端硬截止。正文故障计入节点熔断仍需单独验证；独立revert，无生产变更。

- 追加实际14项定向回归全部通过：模拟原生close忽略中断，超时后重复关闭一次、许可在实际close前不释放；真实嵌入Tomcat+Spring MVC+loopback TCP客户端停止读后RST，流真实关闭、失败审计、下一请求成功；实际AWS SDK Apache 2连接池饱和，第三请求未到源站，部分响应abort后恢复。仅测试配置，非生产50连接池、非真实NAS、非持续静默客户端硬截止。#781已合并38ed106c（1169单测/148 PG16）。最新CI待验收。

- #780已合并039307d3，head947b937d，quality45438121029，1171单测/148 PG16零失败/错误/跳过，Development通过。

- #782 head27423c60，quality45439948011后端真实1175项1失败：ControllerAccessPolicyTest扫描到测试探针Probe#stream。测试改为仅在独立MVC上下文显式注册RequestMapping，不修改生产权限规则或扫描器。修复后本地16项（含完整Controller访问策略检查、真实Tomcat、NAS、OSS、节点状态）零失败/错误/跳过；新CI待验证。

- #785已合并b7980728，head2c6dff9e，governance45440104179、quality45440104187、deploy-pages-demo45440104039通过，933前端单测/138文件。

## 03 pg_trgm 隔离评估（2026-11-25）

- #99/#350 P2，分支 `perf/99-trgm-lab`，worktree `/Volumes/data/Project/MRR-worktrees/99-trgm-lab`。复用合成PG16脚本新增 --trgm，仅新建隔离库构建 BAH/SJH GIN，不改Flyway、生产DDL或查询预算。记录计划实际索引名、共享块、容量、构建时间、5秒超时；1000行插入在事务中回滚，实际总行数不变。10万及100万实跑均结束、实例已停止。详见专题 [GIN隔离实验](synthetic-trgm-benchmark-2026-11-25.md)。
- 3000万独立原工具运行：`/Volumes/data/Project/MRR-worktrees/mrr-scan-lab-3a6p6oh4`，日志 `/tmp/mrr-synthetic-30m-r1.log`，启动shell PID2140，2400秒总预算、15秒多键、10万/批；本轮尚未结束，不能宣称实际3000万、索引或尾延迟通过。不要并发重复启动；下一轮先读report.json并核查pg_ctl状态。#781已合并38ed106c，1169单测/148 PG16通过。

- #785已合并b7980728，head2c6dff9e，governance45440104179、quality45440104187、deploy-pages-demo45440104039通过，933前端单测/138文件。

- #782 已合并437f428a，head68116d01、governance45441171525、quality45441171863，1175单测/148 PG16全部通过，Development通过。

## 04.3 真实HTTP正文熔断与半开恢复验收（2026-11-25）

- #230/#350 P1边界内的验证补充，`test/230-network-body-circuit-recovery`，独立worktree。NetworkSourceCircuit仅增加包内构造注入以使用测试时钟，生产默认3次/30秒/单调时钟完全不变。实际loopback HTTP返回首字节后停发、TimedHttpBody实际读取超时三次、SourcePermitGuard单许可释放，第四次拒绝未触达源站；模拟推进冷却时钟，半开单探针、响应头/部分正文不能恢复、完整正文后同节点恢复。定向6项零失败/错误/跳过，原始 /tmp/mrr-network-body-circuit.log。#782已合并437f428a，1175单测/148真实PG16通过。
- 不声称NAS正文故障已联动节点熔断：NAS原有打开/size熔断与正文隔离仍分阶段。无现场NAS/Windows、Nginx、真实OSS或永久静默慢客户端硬截止结论。独立revert，不修改生产配置与权限，最新CI待验收。

- #784 已合并c3590798，head4bcc7e8d、governance45442058698、quality45442058011，scope/pr-gate通过，其他按范围跳过。

## 03 兼容游标真实PG16与MVC契约（2026-11-25）

- #99/#350 P2，仅补验收，分支 `test/99-legacy-cursor-http-contract`，独立worktree。真实PG16新增 legacyContains 游标锁等待取消（SQLSTATE 57014、原连接恢复），及真实Mapper+Service+Controller standalone MVC JSON契约：默认精确/显式包含、前导零、两页ID、hasMore/nextCursorId/size、无total。未修改权限规则、SQL超时、生产数据库或接口。
- 本地复用隔离基准临时副本增加测试类选择（副本已删除），`mrr-scan-lab-fuaf38hd` 实际9单测/26 PG16零失败/错误/跳过，报告server_stopped=true，pg_ctl确认停止；原始flyway-and-mapper.log保留。MVC契约不代表完整认证链或真实浏览器直连后端。#785已合并b7980728，933前端测试/138文件，演示Pages通过。医院副本端到端尚未验收。独立revert，无生产操作。

- #782 已合并437f428a，head68116d01、governance45441171525、quality45441171863，1175单测/148 PG16全部通过，Development通过。

- #784 已合并c3590798，head4bcc7e8d、governance45442058698、quality45442058011，scope/pr-gate通过，其他按范围跳过。

- #787 已合并ee5cd8d2，head a05e4ec9、governance45442340980、quality45442341293，1176单测零失败/错误/跳过，PG16按范围跳过。

## 03 三千万级完整隔离基准证据（2026-11-25）

- #99/#350 P2，`docs/99-thirty-million-evidence`，独立worktree。mrr-scan-lab-3a6p6oh4 实际COUNT确认30000000/300000，造数1442.415秒，索引144.977秒，两个902MB valid/ready；同页通过、真实8并发多键325796事务、P99 1.028ms、零失败。旧兼容COUNT前后均5秒超时，报告complete_with_legacy_timeouts，server_stopped=true，pg_ctl确认停止。详见[完整基准报告](synthetic-scan-benchmark-2026-11-24.md)。原始日志保留，勿重复启动。
- #785已合并b7980728（933前端测试/138文件，演示Pages成功）；#782已合并437f428a（1175单测/148真实PG16）；#784已合并c3590798（scope/pr-gate通过，其他按范围跳过）。#786和#787在最新main同步后仍待CI。已确认#780/#781/#783/#785/#782/#784合并树等价、干净和上游一致，归档本地报告至`/Volumes/data/Project/MRR-worktrees/merged-evidence-2026-11-25`后正常移除其专属worktree与本地分支，远端分支保留。未做生产操作或正式发布。

- #787 已合并ee5cd8d2，head a05e4ec9、governance45442340980、quality45442341293，1176单测零失败/错误/跳过，PG16按范围跳过。

- #786 已合并07c06e32，head e0c41350，governance45442917009、quality45442917470通过，1176单测/150真实PG16零失败。

## 04.3 NAS 正文与节点熔断联动（2026-11-25）

- #230/#350 P1，`fix/230-nas-body-circuit`，独立worktree。c6bcfcf0实际RED 7项1失败：正文三次超时后第四次仍回源。NasOperationIsolation 将同一次节点尝试交给已领取的正文流，保留open/size工作槽和实际流许可的不同生命周期；NasBodyStream在read/seek/close明确SocketTimeout时只报告一次BODY_FAILURE，关闭仍排在实际阻塞操作之后，迟到结果不重置。实际成功读取（含有效Range）且关闭才确认恢复，未读取的关闭/取消/普通文件错误中性。SourceNodeCircuit新增METADATA_SUCCESS：已观察正文失败时size成功不清零，半开size成功让出探针但不关闭熔断，真实正文成功才恢复。默认3次/30秒、鉴权、超时、原生隔离均不放宽。
- 本地初次GREEN 11项；最终34项零失败/错误/跳过，包含控制时钟下metadata不能掩盖正文失败、单半开探针、健康节点、阻塞read/close、Tomcat、Controller策略、网关和导出。日志 /tmp/mrr-nas-body-circuit-red.log、/tmp/mrr-nas-body-circuit-final.log。真实NAS/Windows及永久挂起OS取消仍未验收；实际许可不会提前释放。独立revert，不改生产。#786已合并07c06e32（1176单测/150 PG16），#788已合并2be73973（scope/pr-gate通过，其余范围跳过）。最新CI待验收。

## 03 三千万级编号索引部署容量与回滚（2026-11-25）

- #99/#350 P1运维边界，`docs/99-index-rollout` 独立worktree。复用既有并发索引构建脚本与同名定义检查，新增只读 inspect-code-key-index-capacity.sql：估算行数、实际表/索引容量、表空间/维护配置、已存在状态及实时构建进度，不执行全表COUNT或DDL；依据三千万真实净增1892573184字节给出非上界参考量。手册增加服务器各卷余量、临时排序与WAL/归档、现场审批停止阈值、保留构建前索引身份、单条并发回滚、禁止删除已有索引及CASCADE。无自动生产操作。
- 两次新建万行PG16隔离验收：mrr-scan-lab-m5041psp、最终 mrr-scan-lab-lh6n43nd，实际两个335872字节索引valid/ready/live，预检估算10000、参考630858字节；分别DROP CONCURRENTLY后两对象不存在、总行数仍10000，实际容量5988352→5316608字节、实例停止。临时测试脚本副本已删除，原始capacity-after/rollback.log和report.json保留。未验收Windows现场卷峰值或生产部署；本PR只读脚本及手册可独立revert。#786已合并07c06e32、#788已合并2be73973。

- #789已合并ff8b541a，head05c9b20a，governance45445428184、quality45445428182通过，1177单测零失败，PG16按范围跳过。

## 03 300万GIN选择性与COUNT策略（2026-11-25）

- #99/#350 P2，`perf/99-trgm-selectivity`，独立worktree。扩展已有 --trgm 无匹配长词、一/三字符、SJH首页/90%深游标；COUNT 5秒、游标3秒，记录实际计划、返回行、共享块及预算；不强制planner、不改生产查询或DDL。首次3m造数后原精确测量分支误用budget导致NameError，实例确认停止；修复后万行冒烟完整通过，第二次3m实际COUNT确认3000000/30000，完整报告complete，server_stopped=true，pg_ctl确认停止。两个GIN31424512/38019072字节，5.324秒。无匹配COUNT117.453→0.710ms、SJH游标195.426→2.443ms；一/两字符仍非GIN，稀疏BAH仍主键；写入单次30.771→22.225ms与百万级反向，不宣称稳定收益或生产验收。详见[GIN隔离报告](synthetic-trgm-benchmark-2026-11-25.md)。默认精确游标、显式包含、无同步COUNT，旧COUNT语义不变。
- #786/#788已合并07c06e32/2be73973，核对等价合并树、干净与上游后归档本地测试报告并移除两独立worktree/本地分支，保留远端。独立revert，无生产修改，最新CI待验收。

- #789已合并ff8b541a，head05c9b20a，governance45445428184、quality45445428182通过，1177单测零失败，PG16按范围跳过。

- #790已合并c825539d，head12a0399f，governance45445973554、quality45445973767通过，1177单测零失败，PG16按范围跳过。

## 03 GIN提交更新删除并发成本（2026-11-25）

- #99/#350 P2，`perf/99-gin-write-cost` 独立worktree，新增 --trgm-write 仅在新建隔离PG16运行固定事务ABBA对照、实际提交更新/删除重插、WAL增量、TPS与P95，不重复精确/GIN查询基准。mrr-scan-lab-9xi2ubwn 实际100000行、16组14400事务零失败，逐轮COUNT一致、报告complete、pg_ctl停止。8客户端有GIN WAL约多38–39%，TPS/P95波动，非生产吞吐。详见[GIN隔离实验](synthetic-trgm-benchmark-2026-11-25.md)。保留既有语义、不强制planner、不改生产DDL；最新CI待验收。
- #790已合并c825539d（1177单测），#791已合并2d82ce80（governance45446800153、quality45446800105成功，其他按范围跳过）。

## 04.3 NAS节点许可隔离与被动可观测性（2026-11-25）

- #230/#350 P1，`fix/230-nas-node-permits` 独立worktree。真实RED 8项1失败：单节点超时后仍能占用其余全局许可。复用SourcePermitGuard增加节点级许可（max(1,全局上限/配置节点数)，向下取整），全局上限不变，先节点再全局；真实read/close未结束仍保留两层许可；节点数超过全局上限时不能保证每节点独占份额。无需新接口或主动网络探测；既有system:read总览返回进程内NAS序号、状态及许可，按配置键排序，不输出名称/地址/路径，不等价现场健康。未扩展HTTP/OSS/Nginx快照。
- 本地38项初次GREEN，最终42项零失败/错误/跳过；定向ESLint、类型及真实Chromium运维看板6项（含预热）通过，模拟接口非现场。真实原生阻塞采用不响应中断的测试替身，半开竞争/节点恢复仍需现场压力验证。独立revert，保留之前正文隔离，未改生产配置。#790/#791/#792合并c825539d/2d82ce80/a62e19a7。最新CI待验收。

## 03 运维总览索引就绪与容量快照（2026-11-25）

- #99/#350 P2，`feat/99-operations-capacity`，独立worktree。复用已有 DeploymentReadinessService 系统目录统计查询与 OperationsCenterService 总览；展示估算行数、表/索引容量及两索引参考量，未知统计不伪装为空表；仅白名单已知状态、数值及预期索引名进入总览，路径/异常不输出。不新增API/权限/DDL/COUNT，WARNING不触发自动只读。容量参考非服务器可用空间或峰值上限。
- 真实RED 11项2失败，最终本地14项通过；独立PG16 mrr-scan-lab-o81rbd7l 9单测/24集成通过、pg_ctl停止（未知统计补充前）；类型/lint通过，Chromium单独复验6项含预热通过。首次并行工作区使用相同9000端口，新增卡片未找到，单独复验通过；不声称生产UI或服务器卷实测。#790/#791/#792已合并c825539d/2d82ce80/a62e19a7。独立revert，最新CI待验收。
- #793 NAS隔离及看板另PR，存在同一总览Vue/Mock/测试文件，按依赖顺序同步并合并，勿覆盖另一专项。此次同步已保留双方卡片、mock和用例，独立Chromium联合7项（含预热）全部通过；原#794 CI 1179单测/150 PG16/933前端单测，合并冲突解决后最新CI待验收。

- #793已合并52c50d38，1178单测/150 PG16/933前端单测，治理45448123145、质量45448123245、演示45448123155通过。

## 04.3 网络来源监控、正文恢复与并发竞争（2026-11-25）

- #230/#350 P1，`feat/230-network-runtime` 独立worktree。真实RED 2项1失败（38a685e），NetworkSourceCircuit 元数据成功误清正文失败；复用已有 BODY_FAILURE/METADATA_SUCCESS 与半开机制修复。HTTP/Nginx/OSS 复用现有许可与节点状态，返回脱敏序号、状态及应用许可/估算等待数；无网络或文件探测、不初始化客户端、不新增接口/权限。OSS Apache SDK MetricPublisher 仅保留最近一次真实池采样（时间、leased/available/pending/max），非实时仪表，不输出地址/凭据。JDK HttpClient 不公开池内指标，不把应用许可冒充连接数。现有NAS独立配额与全局上限不改。
- 运维总览增加网络来源分组、异常优先排序、相邻成功快照熔断/半开数量变化（非历史审计）、未采样与过期提示、已有权限控制下的日志/体检入口；保留容量/NAS卡片与演示Mock。8路真实线程半开仅一胜、FIFO许可取消不泄漏、两NAS节点持续原生替身阻塞时第三节点可用且实际关闭后才归还，真实loopback OSS采样及HTTP正文回归通过。原39/40/43定向逐步通过，合并主线后55项后端定向含Controller策略/容量/脱敏零失败错误跳过，类型/lint/hooks及联合Chromium8项含预热通过。日志 /tmp/mrr-network-meta-red.log、/tmp/mrr-network-runtime-unified-backend.log、/tmp/mrr-network-runtime-unified.log。独立revert；无生产配置、数据库、迁移、门禁变更；真实医院NAS/Windows/Nginx/OSS、长期池压力与实际网络故障仍须验收。无重复大型数据库实验。
- #793已合并52c50d38，1178单测/150 PG16/933前端，治理45448123145、质量45448123245、演示45448123155通过。#794已合并3e921b01，head5111a69f，解决三个冲突保留双方卡片/测试，联合Chromium7项含预热；1180单测/150 PG16/933前端，治理45449527872、质量45449528367、演示45449526515通过。新分支同步3e921b01后最新CI待验收。

## 07 V1.0 发布准备与历史欠账（2026-11-25）

- #350/#230 P2文档，`docs/350-v1-release-readiness`，独立worktree。[当前验收矩阵与部署恢复顺序](v1-release-readiness-2026-11-25.md)；#795已合并6ac73ed4（1184单测/150PG16/933前端，治理45450605471、质量45450605334、演示45450605080通过）。VERSION仍0.9.0，发布基线校验/17工具单测通过；Desktop Commander gh 发起 dry_run=true 45451701999，固定6ac73ed4，identity成功，完整候选/恢复待验收。该SHA不等于最终V1.0；未创建Tag/Release或修改版本。
- 实际剩余历史PR #712/#722可合并但旧身份证GET退役需现场调用方清单与迁移审批，#752草稿冲突仅演示OCR、依赖#751已合并；全部保留，不为清理直接退役或扩展Mock。现有数据质量超时、数据库基准与恢复工具复用，不重做。医院A1–F4/E5、真实Windows/存储、RPO/RTO、制品身份及最终审批仍未满足。独立revert文档，无生产操作。

## 04.3 存储进程内异常历史及重复连接池耗尽恢复（2026-11-25）

- #230/#350 P2可观测性、P1资源专项回归，`feat/230-storage-event-history` 独立worktree。测试先定义history契约，旧实现testCompile因缺少history失败（非运行时RED）；复用SourceNodeCircuit，在既有真实状态变化记录OPENED/PROBE_STARTED/METADATA_ONLY/RECOVERED，单节点16条环形历史及进程累计计数，不新增表、不主动探测、不改变状态机阈值、权限或生产配置。NAS补齐估算等待许可；总览聚合最近12条与快照前30分钟每5分钟已保留的熔断/恢复计数，明确非完整统计、非故障率、重启清空；脱敏序号和静态排障建议，不含路径/凭据，不自动重置熔断。计数/时间为进程内观察，不等同现场健康或持久审计。
- 初次GREEN 34项；实际loopback AWS SDK Apache池2连接、12轮每轮两占用/一次获取超时不回源/abort释放/下一请求成功，固定测试客户端无重试（生产策略未改），首次编译API错误已修正。最终46项后端定向零失败错误跳过，含既有多NAS竞争/实际关闭、权限策略；类型/lint及最终独立Chromium9项含预热通过。日志 /tmp/mrr-storage-history-red.log、/tmp/mrr-storage-history-pressure-r2.log、/tmp/mrr-storage-history-browser-final.log。12轮是有界重复压力，不是长时间生产soak，现场50连接、Windows/NAS真实压力、资源耗尽与恢复证据仍待。
- #795已合并6ac73ed4，1184单测/150 PG16/933前端；治理45450605471、质量45450605334、演示45450605080通过。#796发布准备矩阵另PR；不重复数据库实验，不改VERSION或正式发布。独立revert，最新CI待验收。

- 额外真实loopback固定6线程/每轮释放服务端闩锁，400轮1200实际到达源站请求、400次池耗尽未回源、400次abort后成功，专项1项零失败、70.39秒，/tmp/mrr-storage-pool-400.log；日常默认12轮，参数限制1–600。非小时级生产soak。#796已合并5173b548，治理45452425799、质量45452425774通过，文档范围其他跳过。同步后CI待验收。

## 07 发布预演实际通过与候选冻结边界（2026-11-25）

- #350 P2文档，`docs/350-v1-dry-run-evidence`，独立worktree。[V1验收矩阵](v1-release-readiness-2026-11-25.md)追加实际dry-run45451701999 attempt1，SHA6ac73ed4，identity/candidate/recovery/complete/summary成功，publish跳过。1184单测/150 PG16/933前端/266 Mock浏览器/2真实候选浏览器；覆盖率全门禁通过，Windows PS5.1/PG16.15真实空库恢复通过。下载JAR/manifest，verify_manifest jar_only=true 实际身份/哈希一致，完整ZIP/独立sha文件未保留；不得伪称下载校验。#797已合并d361a71a，1186后端/933前端，PG16按范围跳过，最新门禁通过。当前非最终RC，保留VERSION0.9.0，不创建Tag/Release，兼容POST专项与院内验收仍待。
- 准备RC说明草稿，正式版本文件变更留待兼容专项收口，不提前绑定旧SHA；现场调用者迁移、等量副本与Windows/NAS实际运行仍为BLOCKED。工程文档、release工具与基线校验，不放宽门禁。

## 07 历史身份证协议兼容迁移（2026-11-25）

- #715/#112 P1，`fix/715-compatible-patient-post` 独立worktree；复用#722测试及#712扫描页候选实现但不退役任何旧HTTP GET、不移除加密接口或改变默认开关。患者新增精确POST /api/v1/patients/idcard，复用DTO/Mapper及record:read，合法15/18位校验；旧GET保留原查询、两者private no-store；只读精确allowlist不放行import。客户端及Mock新增POST，旧GET保留。普通扫描查询在 /idcard 当前页面内存，调用既有search:read POST，返回/刷新/账号变化清空，销毁后拒绝迟到响应；合法旧明文深链仍可发起POST，返回查询替换为/idcard，旧URL历史/Referer/定制Nginx日志风险未消除。旧Search GET及archive token/HMAC不改。API文档明确现场消费者清单、迁移与另行退役审批。
- 患者实际RED 13项4失败（1b45ef27），GREEN 31项；扫描浏览器RED等待POST超时，第一轮6通过1失败（返回时URL尚未替换），等待实际导航后8项含预热通过，验证普通URL无身份证、旧深链、刷新返回、迟到响应不继续请求影像。后端最终70项定向零失败错误跳过（含真实AuthorizationInterceptor+standalone MVC 401/403/允许POST、旧GET拒绝、日志脱敏/只读），非完整登录链或院内联调。日志 /tmp/mrr-compatible-patient-red.log、/tmp/mrr-compatible-patient-final.log、/tmp/mrr-scan-privacy-red.log、/tmp/mrr-scan-privacy-green-r2.log。类型/lint/hooks及文档检查。保留#712/#722待现场审批，不擅自关闭；#752仍暂缓。独立安全前向修复，未改数据、Flyway、生产配置及版本。
- #797已合并d361a71a（1186单测/933前端，PG16按范围跳过），#798已合并141f53b9（governance45458082461、quality45458082439，其他文档范围跳过），完整dry-run45451701999 PASS（旧SHA6ac73ed4，Windows恢复/覆盖率/JAR哈希实证）。同步141f53b9，最新CI待验收；RC说明已准备，最终SHA和VERSION待兼容专项合并及版本范围审批。未正式发布。

## 07 V1.0 RC1 候选冻结准备（2026-11-25）

- #350 P1发布链路，维护者已批准准备1.0.0-rc.1，明确不创建正式Tag/Release、不停用旧GET、不重复数据库基准。独立worktree `/Volumes/data/Project/MRR-worktrees/350-v1-rc1`，分支 `release/350-v1-rc1`。#799已合并e0ac2036（head65e5b5be，治理45458426170、质量45458425598、演示45458425802通过；1191单测/150PG16/933前端零失败，Chrome86静态通过有装饰性降级警告）。保留#712/#722现场审批、#752暂缓，不再扩大功能。
- VERSION/Maven revision/README/正式release-notes/1.0.0-rc.1.md一致，release-baseline及Flyway不变。新增[RC交付与现场清单](v1-rc-delivery-checklist.md)，旧0.9.0预演仅绑定6ac73ed4。Release现有手动dry_run=true默认不变，新增retain_candidate可选默认false；显式true时同轮留存完整ZIP/JAR/独立sha/manifest并使Windows检查完整文件，publish仍须dry_run=false且main，不修改原门禁。RED真实契约1项失败后修复，版本及完整RC候选单测补齐；实际259工具测试（254通过、5按环境跳过），release_baseline及工程文档通过；首次README版本漂移检查失败后修正。同步e0ac2036，独立revert版本与留存选项（不涉及业务/数据），未正式发布。
- 最新版本PR尚待实际CI及自主复审；通过后合并并记录确切main SHA，核查main quality及手动Release `dry_run=true retain_candidate=true` 的实际run/attempt/sha。完整验证与制品下载检查未执行前不得标记通过；任何后续代码改动重验最终SHA。现场A1–F4/E5/旧协议/容量/RPO/RTO/真实Chrome86及存储全部待授权，安全前向修复，不直接回退应用。源码不预填自己的未来SHA；以PR/Issue及run/manifest记录实际身份。

## 08 V1.1 文件完整性第一增量（2026-11-25）

- #144/#230/#636 P1，RC1 24152bee保持冻结（#350最终验收PASS；现场仍BLOCKED），独立worktree feat/144-v1.1-integrity。[实现边界、验证与剩余](v1.1-file-integrity-increment.md)。单图真实正文有界校验及双权限POST、运维UI和Mock，20定向+2 Chromium含预热通过。持久化病案Manifest及后台任务尚未实现；最新CI待核对，不合入冻结main。
## 08 V1.1 数据质量修复前置及历史欠账（2026-11-25）

- #166/#469/#729 P1，独立worktree fix/166-v1.1-repair-preview；[现状、测试及历史Issue审计](v1.1-quality-repair-increment.md)。修复预览当前值核对、禁止过时/失效/冲突候选、抽屉代次隔离及失败重试、Mock补齐。23后端/2 Chromium含预热通过；仍只读，工单审批及事务执行待专项。RC1 24152bee冻结不变，最新CI待核对。
## 08 V1.1 运维历史保留专项（2026-11-25）

- #116/#92 P1，独立worktree fix/116-v1.1-availability-retention；[持续清理边界和实际PG16证据](v1.1-availability-retention.md)。独立每小时1000条到期已关闭监控区间，5s超时、SKIP LOCKED，不影响心跳，不涉及医疗事实。实际7项（6单测+1 PG16.16）通过，1000/1/0批次及3条保留，隔离库已停止。最新CI待验收，RC1 24152bee冻结不变。

## 08 V1.1 独立集成与持久化病案观察（2026-11-25）

- RC1 main 固定 24152bee64f3fabcc337af49ecaa6f0280b09f7c；独立 integration/v1.1 worktree /Volumes/data/Project/MRR-worktrees/v1.1-integration。#801/#802/#803 最终 pr-gate SUCCESS，#802 修复用户会话 store 测试隔离（93a02bcf，7项本地 Vitest）；保留原提交集成至 d21f7671。不向 main 合并，不发布。
- #144 P1：14f61b00 新增 V20261125100000（仅V1.1更新兼容基线），持久化版本、页码/扫描ID游标、扫描ID上界、汇总/异常记录、全局单活任务及带租约恢复，使用已有索引与单图正文校验。默认关闭 app.archive-manifest.enabled，维护/只读停止后台校验，双权限接口、可信会话操作者、no-store，不变更医疗事实。每轮1图、最多5000图、异常100条分页；过期租约不可提交。COMPLETED 仅指有界枚举结束，不代表全部校验通过或严格快照；并发新增/删除/页码变化仍可能影响覆盖，禁止以此自动归档。没有复制3000万行明细；完整清单导出、保留策略、现场大病案验收与UI仍待。
- 测试先行：test-compile RED（缺新repository）；实际新建隔离 PG16.16、48迁移、4集成（2 Manifest、历史保留、空库Flyway）通过并停止；34后端定向及后续11项服务/授权测试通过（有重复权限用例），日志 /tmp/mrr-v11-manifest-red.log、/tmp/mrr-v11-manifest-pg-green.log、/tmp/mrr-v11-manifest-service.log。release_baseline 与 engineering docs 通过。未跑全量 CI、医院库或真实存储；不重复性能基准。回滚先关闭调度，保留新表及审计，采用前向修复，不删生产数据、不修改既有迁移。

## 08 V1.1 受控关联修复与工作台接线（2026-11-25）

- d7c115e8 已推送：新增 V20261125101000、修复工单与同事务事件审计；仅支持成功检查样本 SCAN_ARCHIVE_LINK_MISSING_ESTIMATED 的单条缺失扫描关联，默认关闭 app.data-quality.repair-enabled。双管理权限、真实会话身份、提交人与审批人分离、24小时审批时限、锁内复查源和目标指纹及唯一候选；原审批人显式执行，复验、取消和条件补偿，不自动写入。主档 SHARE NOWAIT 表锁保证候选域不出现幻读，可能与正常写入冲突；真实医院并发压力待验证。补偿只允许源/目标事实与执行后版本仍一致、无SJH且既有解析触发器不会重新关联的情况；禁止绕过触发器。已验证工单保留独占扫描约束，后续同一记录再次缺失需设计显式归档/重开流程。审计失败真实PG16保存点回滚，未做独立多连接锁竞争及完整Spring事务代理链验收。
- PG16.16 隔离库 /tmp/mrr-v11-repair-pg.JgZhcC：空库49条迁移成功；最终12单测+7集成（5工单+2 Manifest）均零失败、零跳过，数据库已停止；日志 /tmp/mrr-v11-repair-pg-final.log。权限拒绝、过期、重复候选、变化拒绝、执行/复验、补偿禁止与许可、审计失败回滚均在合成记录上验证。未运行真实医疗数据修复。兼容基线只更新集成分支。
- 1bcb89b5 已推送：复用 MrrSectionCard/Element Plus 的 Manifest 卡片、异常分页、状态与显式刷新/启停、账号/病案切换清理及安全SJH影像入口；合成关系/病案详情/Manifest Mock 与异常详情补齐，严格Mock覆盖新增 data-relations,archive-manifest。9项前端单测、vue-tsc、ESLint/stylelint、严格Mock与5工具测试通过；未做真实浏览器及截图验收。演示只展示未校验样例，写操作拒绝。修复审批列表/事件查看/工作台交互未接入，OCR其他演示欠账与无用依赖审计未执行；不要声称已完成。
- 下一批优先：运维持久化历史指标及故障趋势、固定且默认关闭的 Windows nginx.exe -t 校验（复用 deploy/windows/mrrctl.ps1 固定路径与参数，不开放任意命令），完整修复工单列表与审计审核UI，Manifest 严格快照/分段与保留策略，真实浏览器/锁竞争专项，OCR与历史Issue及依赖审计。冻结 main 与正式版本身份不变；新迁移采用安全前向修复，禁止擅自发布。

## 08 V1.1 工作台、严格成员快照与工程欠账（2026-11-25）

- [专项实施、真实测试、77项开放 Issue 分类与回滚](v1.1-workbench-snapshot.md)。基于 b22f7964，独立分支 feat/v1.1-workbench-snapshot；#801/#802/#803 已关闭且原提交保留于 integration/v1.1。main 24152bee 冻结。P1，仅向集成分支提交 PR；最终门禁及合并状态以 GitHub 为准。
- 50条迁移的隔离 PG16 与真实事务代理/多连接竞争通过；工单及成员历史 UI、共享影像核对、OCR Mock、gsap/FaHoverCard 清理；48 路由缺口及医院现场验收仍待。禁止真实修复、正式发布和重复性能基准。

## 08 V1.1 #804 门禁修复（2026-11-25）

- 旧 head e678ce6a 实际失败三项；已修复六个工单双权限方法声明、OCR Pages 合成状态契约、固定校验和 Wrapper 引导有限重试。真实 Java21 12项、工具链12项、静态 Pages 构建和 Chromium 全冒烟成功；Docker 干净镜像待远端验证。详见 [专项证据](v1.1-workbench-snapshot.md)。不得在最新门禁通过前合并，不动冻结 main 或生产。

## 08 V1.1 固定成员版本比较与演示统计（2026-11-25）

- [Manifest 比较及专项验证](v1.1-manifest-comparison.md)，独立依赖分支暂基于 #804 1d904fe6；须在其门禁通过合并后同步 integration/v1.1。另有患者统计独立分支 ed281f15；五项有证据的历史 Issue 已关闭，72 项开放。真实生产、正式发布及 main 不变。

## 08 V1.1 独立病案工作台与集中增量（2026-11-25）

- #804 已合并 integration/v1.1 7bab1280，冻结 main 24152bee 不变；[入口、集成和验证](v1.1-workbench-entry.md)。后续集中分支 feat/v1.1-workbench-entry 吸收 Manifest 与患者统计原增量，保留旧分支，避免重复 PR；业务菜单 /archive-workbench，原权限不变。嵌入有界影像核对及现有 OCR，修复实际 Blob 契约和会话/身份隔离。本地 24 项单测及 10 Chromium（含预热）通过；最终门禁及合并以 GitHub 为准。

## 08 V1.1 运维历史与本地 Nginx 检查（2026-11-25）

- #805 已合并 integration/v1.1 ba1487ba，main 24152bee 冻结；独立分支 feat/v1.1-operations-history，详见 [运维历史及Nginx专项](v1.1-operations-history.md)。新增有界独立采样表、历史趋势与相邻观察时间线；固定本地 Windows Nginx 语法校验及元数据；两个特性默认关闭。不执行真实 Windows 生产命令。最终 PR/门禁以 GitHub 为准，真实医院验收仍独立。

## 09 V1.1 工单异常重开与完整性报告（2026-11-25）

- #806 的 scope/Pages 初次失败分别修复兼容迁移及 NAS 标签定位，最新 head 8120e18c；远端最终门禁以实际结果为准。独立后续 feat/v1.1-repair-manifest 基于该 head，详见 [重开与报告](v1.1-repair-manifest-report.md)。只允许新成功检查、同一影像缺失关联和唯一候选的明确重提，旧工单 SUPERSEDED、保留审计、新工单独立审批；固定成员报告只读分段及汇总。17 PG专项、15 Vitest、4 Chromium（含预热）通过。未合并 #806 前不重复建立依赖 PR；合并后从最新 integration/v1.1 吸收该增量。main 24152bee 冻结，未执行生产操作。

## 10 V1.1 Manifest任务恢复与公开配置演示（2026-11-25）

- 后续 feat/v1.1-repair-manifest 基于 bbe12df8，新增 [显式任务恢复与公开配置](v1.1-manifest-recovery.md)，仅最新FAILED固定成员任务、显式确认、原游标恢复、旧租约拒绝、最多三次、原子恢复审计；54迁移及18后端、5前端专项通过，Pages静态冒烟通过，179/219路由匹配。#806 最新候选函数覆盖率问题另在父分支补10项真实行为/契约测试，本地完整覆盖率门禁73.26%恢复，远端仍须复核。父PR未合并前不创建重复PR；main冻结、不执行真实医疗数据或生产操作。

## 11 V1.1 病案影像分页与内嵌OCR（2026-11-25）

- 后续 feat/v1.1-repair-manifest 基于 40a0c31b，详见 [影像分页与内嵌OCR](v1.1-workbench-scan-pagination.md)：真实双权限只读游标接口，重复/空页码遍历、每组50条、有界请求，内嵌识别保留现有档案袋抽屉。PG定向13/13、权限11/11、相关组件19/19与新增OCR专项3/3（2项重叠）、Chromium4/4（含预热）通过。静态路由180/220、40缺失。#806 27bcd270 Pages/governance/development已过、候选仍执行，不提前合并。main冻结，未执行真实医疗或生产操作。

## 12 V1.1 持久存储熔断事件（2026-11-25）

- [专项记录](v1.1-storage-circuit-history.md)：既有状态机脱敏事件采集、UUID+sequence去重、七天有界历史；PG12/12、权限12/12、组件3/3、Chromium14/14及静态冒烟通过。#806 候选build通过，Windows恢复待确认；main冻结。

## 13 V1.1 导出版本与断点校验（2026-11-25）

- #806 完整候选及Windows恢复通过，已合并 integration/v1.1 c3e6bdba。上一轮熔断历史已推送9966fb50。新增 [导出版本与断点校验](v1.1-export-representation.md)，全部已有前缀分块核验、If-Range/ETag/Content-Range及总长度校验，保留128MiB预算与权限。61 Vitest、4 Chromium（含预热）通过。后续集中PR基于c3e6bdba，main冻结。

## 14 V1.1 正常装箱关联修复（2026-11-25）

- 后续 feat/v1.1-box-repair 基于 #807 6e675c00，详见 [正常装箱关联修复](v1.1-box-repair.md)：显式类型+实体ID、正常装箱唯一候选、原审批与条件补偿；统计触发器涉及主档事实，不开放。PG及权限22/22、56迁移验证、预览17/17、前端18/18、Chromium4/4（含预热）。#807 最新Pages/development/governance通过，完整候选待确认。main冻结，暂不重复创建依赖PR。

## 15 V1.1 异常人工决策与历史（2026-11-25）

- [专项记录](v1.1-quality-case-review.md)：独立案件、确认/暂缓/重开、版本CAS与审计，样本清理保留历史，决策不修改医疗事实或检查计数、不自动推断自然恢复。PG专项16/16、最新权限13/13（12重叠）、组件21/21、Chromium5/5含预热、严格Mock185/225。#807完整候选仍执行，不提前合并；main冻结，后续集中吸收装箱及决策增量。

## 16 V1.1 PR #807 最终收口（2026-11-25）

- #807 最新6e675c00 完整候选45545886254、Windows数据库恢复、全Mock浏览器与真实候选JAR验收、Pages45545886315、治理45545886329、pr-gate成功。AI自审评论7153064369后按确切head squash合并至integration/v1.1 0b094976；远端代码树与已验收head一致，main 24152bee冻结。后续装箱修复2b3fc759及异常人工决策3c6e20a2仍在独立分支，未纳入本次合并。此次仅完成第1项，不扩大开发范围。

## 17 V1.1 逐记录只读复验与集中集成（2026-11-25）

- 基于 #807 merge 0b094976 无冲突集中吸收装箱、人工决策和交接增量，feat/v1.1-quality-governance。新增 [逐记录只读复验](v1.1-quality-case-recheck.md)，仅保留样本与当前唯一关联一致时报告已核对，不自动关闭案件。PG28/28、最新权限/访问策略16/16（有重叠）、组件22/22、Chromium5/5含预热、完整Vitest978/978及原覆盖率门禁、Pages静态冒烟通过，186/226路由。完整候选及Windows恢复以新PR结果为准，main冻结。

## 18 V1.1 Nginx运行观察与脱敏日志（2026-11-25）

- 后续 feat/v1.1-nginx-observation 基于 #808 830ff23a，详见 [Nginx运行观察](v1.1-nginx-runtime-observation.md)：固定PID/程序时点核对，64KiB日志白名单字段投影，默认关闭，双日志权限，无启停、重载、原文或网络探测。25后端、3组件、15 Chromium（含预热），190/228 Mock；#808父PR完整候选仍运行，不重复创建依赖PR。main冻结，Windows现场、版本和监听未验收。

## 19 V1.1 Nginx安装版本与监听文本（2026-11-25）

- 上一轮48f41634远端推送与Pages静态冒烟已核实。后续新增固定 -v、与 -t 共享许可及有界版本投影；主配置仅提取数字listen文本提示，不能视作生效监听。后端28/28、最新权限16/16（重叠）、组件13/13、Chromium2/2含预热，严格Mock192/230。详见 [Nginx专项](v1.1-nginx-runtime-observation.md)。#808完整候选仍运行，main冻结，SCM及实际Windows现场仍待。

## 20 V1.1 Windows 网关状态与 TCP 监听（2026-11-25）

- #808 最新830ff23a 完整候选45548967642及Windows恢复/Pages/治理/pr-gate通过，已合并7b28a5d5，远端树一致。集中吸收Nginx两批增量，新分支 feat/v1.1-gateway-diagnostics；[Windows网关观察](v1.1-windows-gateway-observation.md) 增加固定本地服务状态及已核验进程的TCP监听，只读、默认关闭、无网络探测和启停；服务名与进程监听独立。实际Windows Server现场尚未验证，#636保持开放。main冻结。

## 21 #809 PostgreSQL CI 连接池治理（2026-11-25）

- #809 后端45552190741 真实失败为日志分区锁测试连接耗尽。测试共享 Hikari minIdle=0/maxPool=4；隔离PG16.16 max_connections=100 原失败用例通过，177/178，唯一剩余本机无Docker导致第二实例用例不可执行，不跳过，远端必须完整验收。详见Windows网关专项。main冻结。

## 22 V1.1 病案工作台布局及影像导航（2026-11-25）

- 后续分支 feat/v1.1-archive-workbench-layout，详见 [病案工作台布局及导航](v1.1-archive-workbench-layout.md)：独立工作台双栏、候选高亮、分区焦点、当前组影像导航、专注原图及严格游标；旧入口兼容。26组件、5 Chromium含预热、390px窄屏通过。#809实际后端连接耗尽，独立worktree v1.1-gateway-ci处理测试池；待完整PG及远端门禁通过再合并，不重复创建依赖PR。main冻结。

## 23 V1.1 影像查看器与OCR安全交互（2026-11-25）

- #809 745e6546 CI全部必要门禁通过，已合并9fc4fc36，远端树一致，main冻结。后续 [查看器与OCR](v1.1-archive-viewer-ocr.md) 增加有界缩放、旋转、复位与轮询任务/病案身份及权限边界，13项定向、Chromium5/5含预热，生成三种截图；不新增依赖或迁移。集中吸收a35b7760布局与导航增量，完整前端门禁待最终核验。

## 24 V1.1 工作台集中集成（2026-11-25）

- 基于9fc4fc36吸收工作台两批改动，合并交接文档冲突时保留双方记录。完整Vitest990/990、原覆盖率门禁、Pages静态冒烟通过；实际深色主题纠正仅切换class的无效截图，修正内嵌OCR高度，最终Chromium6/6含预热。后续统一收口本批PR及38条缺失Mock，main冻结。详见查看器与OCR专项。

## 25 V1.1 演示系统完整性（2026-11-25）

- #810 57bc56c1 前端/Pages通过；治理只因AI 参与说明标题缺空格，修正文案后45554910682通过，合并df9053de，树一致。新分支feat/v1.1-demo-completeness新增3条共享合成数据库浏览路由及15条明确不可用契约，211/231匹配，20缺失。3单元、2 Chromium含预热、Pages构建及静态冒烟通过；详见 [演示专项](v1.1-demo-completeness.md)。后续集中处理真实合成导出及剩余接口，不伪造文件/认证/写入，不拆分小PR，main冻结。

## 26 V1.1 合成影像导出（2026-11-25）

- feat/v1.1-demo-completeness 首批c0f7123f远端核实，第二批11影像导出路由补齐，222/231，9缺失。真实JSZip文件、按需生成任务/取消/区间及浏览器PDF；静态JSON有界解码不影响生产ETag，服务端PDF明确不可用。24项定向及最终2项Chromium含预热，Pages构建及真实ZIP/PDF静态下载冒烟通过；首次SVG解码失败已修正。详见 [演示专项](v1.1-demo-completeness.md)。后续集中补齐9条路由再统一PR，main冻结。

## 27 V1.1 演示系统完整性集中收口（2026-11-25）

- 三批补齐38条缺失路由，dev/Pages 231/231、0缺失、0重复。真实合成ZIP/PDF/CSV/XLSX、数据库与审计、旧影像、12项运行设置/目录规则；认证/真实写入明确拒绝，路由匹配不等于全部业务可执行。完整Vitest1000/1000、覆盖率门禁通过；最后目录增量21/21、最终Pages构建及实际静态冒烟通过，Chromium8/8含预热。集中PR仅integration/v1.1，main冻结，后续检查CI并按风险收口历史Issue、双主题及V1.1候选。详见 [演示专项](v1.1-demo-completeness.md)。

## 28 V1.1 工程收口与历史Issue审计（2026-11-25）

- #811 14a85d47必要CI通过，已合并9ff5000d，树一致；main冻结。实际关闭#485/#501/#503/#505，72→68；剩余标题全量初筛及重点证据见[工程收口](v1.1-engineering-closure.md)。新分支feat/v1.1-engineering-governance修复#562详情竞态与权限隔离（17项错误中心、共24定向及2 Chromium含预热），删除#114无人调用client-zip、保留演示JSZip。后续集中处理#569/#116、双主题、生产chunk和候选门禁，不提前关闭或拆小PR。

## 29 V1.1 路由统一及保留指标（2026-11-25）

- 同一feat/v1.1-engineering-governance基于9ff5000d：#569旧路由来源安全归一显式注册，移除自动扫描和9块冗余元数据；首轮浏览器失败修复后6/6含预热。#116新增5项进程级Micrometer指标，8/8。完整前端1006/1006、函数73.59%门禁通过；最后首页演示引入限定Pages，生产167个JS map/2017源引用无Mock/JSZip/旧路由，最终Pages231/231及静态冒烟通过。离线依赖卸载因元数据不足未改清单锁文件。集中PR待CI，main冻结；下一批双主题与剩余历史Issue、候选验收。详见[工程收口](v1.1-engineering-closure.md)。

- #116 后续独立真实调度回归：test/v1.1-retention-scheduling，10/10 定向通过，详见 [工程收口第四批](v1.1-engineering-closure.md)。不触碰生产配置或候选源码，待远端门禁。

## 30 V1.1 双主题入口体验（2026-11-25）

- #812 e6c81feb必要CI成功，合并757aac40、树一致；main冻结。新分支feat/v1.1-visual-quality，29入口两主题58张基线截图、116次1440/390px公共容器观测无已观测溢出/脚本异常（并非全站就绪视觉审查）。实际改进独立病案空态与检索、授权数据表快捷目录及空态底距；原联合13项通过，新增定位修正后3/3含预热、8张新版截图，最终Pages231/231/真实静态冒烟通过。详见[视觉专项](v1.1-visual-quality.md)，后续集中公共控件与数据状态、跨浏览器、Issue及候选，不拆小PR。

## 31 V1.1 加载状态与日志详情（2026-11-25）

- 同一视觉分支c80bfe37，数据库浏览代次、会话权限清理（含3001）、错表错页拒绝、分页失败保留/重试、筛选标签及窄屏；日志详情请求身份、错误重试及会话/关窗隔离，长弹窗内部滚动。联合定向24/24、Chromium10/10及最终日志3/3含预热，完整1013/1013函数73.51%门禁通过，最终Pages231/231和真实静态冒烟通过。两批集中PR仅integration/v1.1，main冻结；登录抽查无改动，完整跨浏览器、列表缓存会话及现场待后续。详见[视觉专项](v1.1-visual-quality.md)。

- #562/#624后续独立运行错误中心概览/列表解耦：基线2cd1896f，真实浏览器3/3、Vitest34/34、类型与lint通过；独立重试、普通故障保留数据、权限拒绝清除及代次隔离。详见[运行错误中心](v1.1-runtime-error-experience.md)。待PR门禁，不改main。

## 32 V1.1 会话生命周期与审计隔离（2026-11-25）

- #813 cf3377bd 必要CI成功，合并9641aba8树一致；main冻结。新分支feat/v1.1-session-lifecycle，用户Store代次、旧token限时撤销、布局缓存、三公共列表及审计统计/详情/导出隔离，关联Issue见[会话专项](v1.1-session-lifecycle.md)。定向60/60、完整1021/1021函数73.66%门禁、Chromium21/21含预热、Pages真实冒烟通过。静态230/230（独立注销传输不在旧扫描范围），未宣称真实服务端撤销或全站所有接口隔离。后续先收口集中PR，再处理剩余权限拒绝/全局状态、历史工程欠账及候选。

## 33 V1.1 全局会话、跨标签页与权限拒绝（2026-11-25）

- #815 afc171c6同SHA必要检查成功，合并30afc87b树一致；首次治理正文错误已修正，main冻结。新分支feat/v1.1-global-session-guard，普通Axios请求拦截时token/epoch、旧认证结果/重试保护、运行错误角标、三页面403清理、跨标签页不删除共享新token；#814继续关联。定向76/76及拒绝30/30有重叠，完整1030/1030函数73.78%门禁，Chromium16/16含两个真实标签页合成token测试及预热，Pages真实静态冒烟230/230。详见[会话专项第二批](v1.1-session-lifecycle.md)。未宣称所有请求、已登记通知、同token动态权限和真实服务端撤销完成。下一轮收口PR，审计历史事项、依赖及候选。

## 34 V1.1 历史事项与依赖治理（2026-11-25）

- #816 已合并14ba5b83、原4151ba41树一致，main冻结。真实复核关闭#508/#621/#519/#521/#523/#524，open69→63；现场事项保留。新分支feat/v1.1-dependency-closure，#140/#569旧路由插件清单/锁文件纯删除、resilience4j未使用依赖、编译/缓存配置与系统信息注入精简，保留iText正式导出。后端31/31、前端9/9、Chromium3/3含预热、TypeScript/生产构建/Chrome86静态及冻结锁文件校验通过。详见[工程收口第三批](v1.1-engineering-closure.md)。#140/#389/#569范围未全完结，下一步远端CI及候选，不重复三千万基准。

## 35 V1.1 候选验收与跨浏览器矩阵（2026-11-25）

- #817 59eeba00全部必要CI成功，合并28d9ca60树一致；main冻结24152bee。Release dry-run 45577278120精确集成SHA、retain_candidate=true，identity成功，完整验证执行中，不发布。独立分支feat/v1.1-candidate-qualification新增可选矩阵/工作流：22入口双主题双宽度、Chromium45/45含预热、88张截图；Firefox/WebKit179项含预热尚未执行。本机下载慢已停止专用安装，改手动CI，默认PR/Chromium门禁不变。464项测试资产清单及定向3/3、文档通过。详见[候选验收](v1.1-candidate-qualification.md)，待候选制品、远端跨浏览器和实际缺陷收口，不更改VERSION或main。

## 36 V1.1 完整候选通过及WebKit复验（2026-11-25）

- 28d9ca60完整dry-run 45577278120/1全部通过（Windows实际备份恢复、精确制品文件SHA256校验，publish跳过），保留artifact15005671535；本地下载未完成。#818首轮45578342299 176/179，Firefox89/89、WebKit86/89；显式焦点与长用例拆分，失败trace，Chromium17/17，待WebKit复验。#389官方审计见Issue评论；#624不凭JaCoCo报告关闭。产品源码/版本/main保持冻结。详见[候选验收](v1.1-candidate-qualification.md)。

## 37 V1.1 跨浏览器第三轮与调度验收（2026-11-25）

- #819 合并3d158652，树与b63af633一致，CI后端1244/1244；#818第三轮45590192907实际180/181，Firefox日志深色label焦点失败，限定选择框关闭状态后Chromium定向5/5，远端待复验。详见[候选验收](v1.1-candidate-qualification.md)。main冻结，不改产品源码。

## 38 V1.1 依赖安全阶段治理（2026-11-25）

- 独立feat/v1.1-dependency-security基线28d9ca60，Zod4.6.6与四账号表单兼容适配，Handlebars4.7.10、shell-quote1.12.0，Vitest3.2.6覆盖率原门禁通过；双override同步后正常冻结安装及exec通过。兼容1033/1033、函数73.84%、认证Chromium19/19、生产构建/Chrome86静态通过；默认Vite仅回环。官方prod0、all53/critical2，Tinypool及Vitest4覆盖率口径迁移仍待处理；Vitest4试验1033/1033但原门禁失败，未修改基线。详见[依赖安全](v1.1-dependency-security.md)。#818另行验收，产品变化须重新资格验证，不改变main或VERSION。

## 39 V1.1 第四轮跨浏览器及集成同步（2026-11-25）

- #818 de98b917 第四轮45593766647 实际181/181，21分钟，全部通过；#819/#820/#821分别合并3d158652/2cd1896f/1c9dc516，#821前端1038/1038、合并树与40870dd2一致。同步最新集成，仅两处交接冲突，保留双方专题；旧候选28d9ca60本地制品verify退出0，仅历史证据。最新产品树需重新跨浏览器及候选验证，main冻结。

## V1.1 影像核对逐张跨组导航（2026-11-25）

- #822，基线1c9dc516；独立worktree v1.1-workbench-navigation，显式跨组、专注模式、故障重试、焦点和身份隔离，普通翻组不选图。31/31、Chromium2/2含预热、类型和lint通过。详见[工作台布局](v1.1-archive-workbench-layout.md)，待PR门禁及最新产品验收，不修改main。

## V1.1 原图查看器局部操作（2026-11-25）

- #823已合并db301746，实际远端1043/1043，树与c82c884c一致。#824原图局部锚点缩放、鼠标平移、键盘、解码释放，独立worktree同步db301746，联合36/36及真实Chromium8/8，类型/lint通过。详见[影像查看器](v1.1-archive-viewer-ocr.md)。待远端门禁，不修改main。

## V1.1 病案检索与详情联动（2026-11-25）

- #825合并e462d5c4，远端1045/1045、树一致；https://github.com/weepwood/MRR/issues/826 检索独立加载、条件变更清理、取消及重复请求防护，39/39、Chromium2/2含预热、类型/lint通过。详见[工作台布局](v1.1-archive-workbench-layout.md)。#818第五轮183/183，仅0d8de5a3；工作台集中收口后统一同步候选，不重复长矩阵。main冻结。

## V1.1 完整性报告与异常分页工作台（2026-11-25）

- #827 已合并f1bc9d9e，1048/1048、合并树一致；首次治理因AI 参与说明标题缺空格失败，正文修正后45601663680通过。#144/#147增量：报告与异常分页独立、严格游标、局部筛选、紧凑布局、明确处理进度，基线同步后42/42、最终Chromium3/3，前一轮联合7/7，类型和lint通过。详见[工作台布局](v1.1-archive-workbench-layout.md)。未修改main，待远端门禁及集中候选。

## V1.1 可调整布局与工单独立审计（2026-11-25）

- #828已合并3f069ecf，1051/1051、合并树一致。#166增量：独立审计、迟到隔离、100条游标分页、当前拒绝清理及原生宽度控制；同步#828解决2处同文件冲突，保留双方布局。最终46/46、Chromium8/8含预热、类型及lint通过。详见[工作台布局](v1.1-archive-workbench-layout.md)，待远端门禁；main冻结。

## V1.1 文档安全与遗留PR治理（2026-11-25）

- #829合并d7d67310，1055/1055、树一致；#389文档告警12→6，20测试、双文档构建通过；#752旧建议版本回归RED1，修复后联合49/49、TS/lint通过。#712/#722仍兼容迁移，#555许可证范围待确认，均不擅自关闭。详见[工程收口](v1.1-engineering-closure.md)，待本批门禁后同步#818，main冻结。

## V1.1 最新集成候选同步（2026-11-25）

- #830已合并3d14dbce，1057/1057、Pages 230/230静态匹配及冒烟、development及governance通过，合并树与50c0aae1一致。#818第五轮45597655935为0d8de5a3、183/183，非最新产品。合并#823/#825/#827/#828/#829/#830后的集成基线，仅ai-handoff冲突保留双方；新增五个工作台测试文件纳入Firefox/WebKit候选配置，统一复验。main及VERSION冻结，不发布，不改变生产源码。

## V1.1 完整候选通过与第六轮超时拆分（2026-11-25）

- 0360e80c完整dry-run 45605152087成功，1244/178/1057/308及真实JAR2/2、Windows恢复，制品独立下载待核验。跨浏览器45605145718实际193/195，已下载两份trace，确认双页流程耗尽30秒；拆分保留断言及时间限制，Chromium5/5、两引擎枚举199。仅测试/文档，不重建原制品；新矩阵待验，不合并、不发布。详见[候选验收](v1.1-candidate-qualification.md)。

## V1.1收口及V1.2专业工作台（2026-11-25）

- #818已合并92896f7c，199/199、JUnit独立复核、tree一致，0360e80c完整制品verify与离线1228/1228。V1.2独立分支8a14c503仅新增CI触发范围。#831分隔条、适宽/实际大小、双向旋转及快捷键，47/47、Chromium联合9/9及最终5/5，类型和lint通过。见[专业工作台](v1.2-professional-workbench.md)，待远端门禁；V1.1与main冻结。

## V1.2 多面板及独立预览（2026-11-25）

- #832已合并f54770c2，1058/1058及合并树一致，MRR-preview独立部署45624360695成功，两版本公网验收及原站HTML哈希一致。#833共享分隔条、实际容器布局、OCR按需展开、原图工具栏及专注深化；8文件53/53、最终Chromium7/7、类型及lint通过，真实窄屏溢出已定位修复。详见[专业工作台](v1.2-professional-workbench.md)，待远端门禁，V1.1/main冻结。

## V1.2 开发重心调整（2026-11-25）

- 用户明确全站UI及公共组件规范优先，暂缓专属工作台功能扩张。#835见[UI治理路线](../design/v1.2-ui-system-roadmap.md)，首批展厅真实原生/封装表单及目录，联合Chromium13/13。#834仍需独立修复远端Linux窄屏溢出，不能合并；生产main与V1.1冻结，预览仅跟随已合并集成。

## V1.2 字段控件集中治理（2026-11-25）

- #836合并dbd81c50，1058/1058、治理/Pages/代码树通过。#834修正窄屏并同步6ea409fe，待远端。独立字段令牌、主题所有权、FaTextarea与只读/错误、展厅四主题对照，68/68样式、15/15展厅、49/49业务E2E，见[UI治理路线](../design/v1.2-ui-system-roadmap.md)，不修改业务数据或冻结分支。

## V1.2 唯一基础控件治理（2026-11-25）

- 用户要求同类控件只保留一种实现，选Element Plus为唯一目标；[逐文件归属清单](../design/component-ownership.md)，121文件基线调查，先迁移全部27处FaSwitch，删除重复实现、样式与类型，69/69样式守护、最终15/15。#834已合并03b560cd（1060/1060、Linux2/2、tree一致）。#837集中追加迁移并同步基线，待最新远端CI；V1.1/main冻结。

## V1.2 控件迁移视觉保护（2026-11-25）

- #837已合并3014094e（1064/1064、Pages19/19、tree一致）。新用户约束：迁移保持页面既有视觉。AppConfigPanel五字段及两组三按钮，56组实际前后几何0px、所测计算样式一致，联合28/28，像素差异与边界见[归属清单](../design/component-ownership.md)。登录/注册暂保留，后续按表单合同迁移。V1.1/main冻结。

## V1.2 账户表单控件归一（2026-11-25）

- #838已合并3c93d1f0，159文件1064/1064、门禁与tree一致。登录/注册7单行、1文本域、4按钮迁移，24组实际几何0px差，72/72样式/账户schema测试，联合37/38（Mock响应提示层级错误）并定向复验2/2，参见[控件归属](../design/component-ownership.md)。修改密码、找回密码及复选未迁移，保持原接口/权限。预览45631698306仅重跑失败发布成功，公网缓存刷新身份3014094e；V1.1/main冻结。

## V1.2 复选框与个人修改密码（2026-11-25）

- #839已合并968bc901，1064/1064、Pages7/7、tree一致。新批次迁移3处复选/3处个人改密输入/1按钮，删除FaCheckbox，修复原表单双事件语义及布局差异；73/73单测、34/34联合浏览器、20组视觉几何0px；改密提交锁及失败恢复/成功注销、注册焦点切换通过，见[归属清单](../design/component-ownership.md)。V1.1/main冻结，不触及实际账号。

## V1.2 滑块与选择器归一（2026-11-25）

- #840合并ef5e5e35（1065/1065、Pages34/34、tree一致）。新批次3处调用、删除FaSlider/FaSelect共16个文件、71/71样式守护；历史17/18（G2开关像素4>3）；规范截图采集后G2连续6/6及最终18/18，16组几何0px，待远端CI，视觉证据见[归属清单](../design/component-ownership.md)。标签页待迁移。预览45637085330在线身份门禁失败，下载包内968bc901、公网no-cache仍3014094e；独立预览6d52400及45641735418待验证。V1.1/main冻结。

## V1.2 标签页本地检查点（2026-11-25）

- #841 远端Linux滑块6/255>3，已推送诊断8563d5a8，尚未合并。预览6d52400、运行45641735418成功，公网ef5e5e35。标签页本地基线993f7427，四组迁移及32组旧视觉基准；72/72样式、tsc通过，最终after4为1/5，桌面分类几何一致但主题选项仍偏右3px。详见[归属清单](../design/component-ownership.md)，待新基线同步及搜索/键盘/窄屏验收，未推送。

## V1.2 标签页视觉收口（2026-11-25）

- #841已合并2e9db6ef（head40786f77，远端1066/1066、Pages18/18、tree一致）。标签页32组几何0px/所测属性一致，恢复旧轻阴影后RGB差>8像素比例全0%；联合40/40、最终6/6、72/72。Home/End、搜索、跟随系统、抽屉独立标识通过。已重放至2e9db6ef，无冲突，最终9/9；详见[归属清单](../design/component-ownership.md)。

## V1.2 全局工具栏按钮（2026-11-25）

- #842已合并bec3cd9a（1067/1067、Pages6/6、tree一致），本批9处全局按钮迁ElButton，8组顶栏对照、52个可见实例、73/73样式；补采16对侧栏，修复头像24→16和折叠标志span误隐藏。联合30/34后收窄选择器，最终15/15、24对截图几何0px/通道差>8比例0%，见[归属清单](../design/component-ownership.md)。预览0831685与45646958524成功，公网bec3cd9a。

## V1.2 基础按钮与字段归一（2026-11-25）

- #843合并fc2a56f7（1068/1068、Pages10/10、tree一致），合并API502后实际确认MERGED。新批45按钮、3按钮组、8输入、1文本域，删除10个旧文件。48字段对照几何0px、字段联合33/33；64按钮对照最大1px、5/5，75/75样式；联合35/36，修复夹具aria-busy后7/7（未重跑全部36）。见[归属清单](../design/component-ownership.md)，旧找回密码未启用，专用上传/预览需后续专项。预览手动dispatch HTTP500未确认更新。

## V1.2 提示与轻量气泡（2026-11-26）

- #844远端1070/1070、Pages31/31全绿，合并接口异常仍OPEN。基线f6934742新批8提示/1气泡迁EP，删除11旧实现；16对几何0px及RGB差>8比例0%，联合26/32后修复导航ARIA与窄屏定位，最终10/10，80/80单测；未重跑32全套。详见[归属清单](../design/component-ownership.md)，待#844合并tree确认后重放。

## V1.2 对话框与抽屉（2026-11-26）

- #844合并2dff0e4b，#845合并3cff46b1（1071/1071、Pages5/5、tree一致）。7处浮层及夹具统一EP，删除23旧文件，24对视觉几何0px、最大RGB差>8比例0.4705%，77/77样式，联合39/40后修复时序/间距，最终21/21；未重跑全40。详见[归属清单](../design/component-ownership.md)。账户Dropdown及其他专用浮层完整视觉/关闭专项后续；预览dispatch500未上线。

## V1.2 账户与右键菜单（2026-11-26）

- #846合并adc06302（1072/1072、Pages5/5、tree一致）。账户与标签页右键迁ElDropdown，删除33个旧文件；10对截图RGB差>8比例0%，78/78样式、26/26联合、3/3键盘右键复验。个人设置关闭限制已补验。旧头像/进度/通知/上传预览等仍有Reka调用，下一批按能力归一，保留专用组件。详见[归属清单](../design/component-ownership.md)。

## V1.2 残余基础控件及无调用链（2026-11-26）

- #847合并9c9022a1（1073/1073、Pages7/7、tree一致）。7处实际Avatar/Progress/Divider归一，删除49个旧实现含无调用卡片/折叠/通知/上传预览；移除class-variance-authority，保留Reka专用依赖。28对宽高0px、最大RGB差>8比例0.10745%，旧一例入场位置无效；92/92单测、37/37联合。详见[归属清单](../design/component-ownership.md)。预览恢复dispatch run45656099309，尚未确认上线。

## V1.2 样式工程治理（2026-11-26）

- #848首次Chrome86静态门禁失败，b4a4e8bc修复等价显式选择器，本地兼容通过，远端复验中。独立样式工作区基于该提交，EP桥接8模块保持AST完全一致，删除5个无调用辅助组件，通知契约与级联守护；90/90单测、29/29 Chromium定向、生产构建/静态兼容通过。独立检查点待#848合并后重放。预览run45656099309重试发布成功，在线V1.2 9c9022a1、V1.1冻结。详见[归属清单](../design/component-ownership.md)。

## 每轮交接模板

- 更新时间、任务、风险、目标及非目标：
- 当前 main / 工作分支 / PR / head SHA：
- 已完成（含证据）：
- 实际执行检查与结果 / 未执行项：
- 未完成、阻塞、关键决策及原因：
- 下一步（精确到模块或文件）、回滚方式：
- 工作区状态（无本地 worktree 时明确说明）：

不复制真实病案、凭据、敏感内网配置；不将 CI 运行中写成通过。历史以 Git 和 Issue 为准。

2026-11-26：#844 经 GitHub 合并接口成功 squash 至 2dff0e4b，验收 head f6934742 与合并 tree 一致；提示分支重放至新基线。此前 OPEN 为历史状态。

2026-11-26：#848最终head ca25d1f8修复Linux抽屉边界（按实际遮罩边缘，1px门禁不变），全部远端门禁通过并squash合并c50653f1，tree一致。业务视觉调查10路由×四主题×三宽度120截图，报告[实际业务视觉审查](../design/business-page-visual-audit.md)。样式治理与筛选高度修复集中交付；13个实际单选实例，120组复验、156项高度精确通过、联合21/21；预览run45659383575尚未确认上线。

2026-11-26：#849全部远端门禁通过，head5a70bb74 squash至dcb01928，tree一致。独立业务视觉批次统一筛选标签、查询反馈、记录复选框、两页眉题、错误详情、审计五指标及窄屏网格；首轮37/38修复复选框可访问名称，保留用户无人工等待和游标等业务语义。详见业务视觉审查文档。

## V1.2 业务组合与展厅契约（2026-11-26）

- #849已合并dcb01928，验收head5a70bb74与合并tree一致；main 24152bee、V1.1 92896f7c 保持冻结。前置业务视觉本地70c1290e，剩余审计标签回归须独立收口。
- P3 独立worktree `v1.2-table-gallery-contract` 基于70c1290e，展厅DataExamples使用MrrFilterActionButton、公共标签和useTableActionLayout，不新增依赖、业务API或权限变更。新增断点回归及实际展厅宽/中/窄屏操作测试，18/18单测、2/2 Chromium含预热、ESLint/类型初验通过。首次隔离端口9037测试因合成认证fixture仍绑定9000而跳登录，临时映射测试origin后通过；临时配置和fixture不提交。日志 `/tmp/mrr-v12-table-gallery-{unit,layout-unit,browser-final,tsc,eslint}.log`。未进行全站跨浏览器、生产部署及真实终端验收。前置批次验收后集中重放本批提交，避免小PR。独立revert即可回滚。

## V1.2 业务视觉集中治理（2026-11-26）

- 工作区 `/Volumes/data/Project/MRR-worktrees/v1.2-table-gallery-contract`，分支 feat/v1.2-table-gallery-contract，基线 dcb01928，含 70c1290e、0614ffe5。关联 #835，P1/P2；无 API、数据库、真实医疗数据或正式部署变更。main 24152bee、V1.1 92896f7c 已核对冻结。线上预览 V1.2 清单仍 c50653f1。
- 实施公共筛选实际宽度、高级筛选折叠和已填写计数、响应式固定操作列及选中态、统计/OCR/运维反馈、设置目录容器响应；修复 tooltip 默认键盘拦截。维持原表头、行高、分页与游标契约，未全量重构。详见 [实际业务视觉审查](../design/business-page-visual-audit.md)。
- 164/164 定向单测；首轮联合38/38，扩展59/62，最终260组截图/280选择器/500标签、所测容器零溢出。小修最终定向5/7：设置、候选键盘、审计明暗通过，日志明暗浮层残留仍阻塞。WebKit 10项无法启动（所需2388未安装）；未执行Firefox、真实Chrome86、正式构建发布与全站跨浏览器验收。证据 /tmp/mrr-v12-closure-{final-unit,final-browser,repair3,webkit,tsc6,lint6,style6}.log。不得合并未通过门禁的版本。
- 下一步：针对 Element Plus Select 的快速聚焦/关闭时序复现，保留真实关闭断言；分别验证日志/审计，之后重跑累计联合、远端CI、独立预览身份。回滚以本批提交独立 revert（前置两个本地提交同一 PR）；不改 main/V1.1。临时隔离端口配置与合成认证fixture不提交。


## V1.2 浮层与候选收口（2026-11-26）

- 基于06ffcef8，仅调整公共Select入退场过渡，重复复现前5/9（4失败）、修复后13/13，最终累计62/62 Chromium，260组截图无所测容器溢出，84/84样式、生产构建与Chrome86静态门禁通过；WebKit 10/10业务用例通过。无真实医疗数据、后端及部署变更。Firefox所需版本已安装到外置盘独立缓存，但10项均在启动时报Could not find profile folder，独立临时目录与persistent profile最小复现仍失败，不记为产品回归或通过。使用现有Linux跨浏览器专项workflow，补齐本批相关测试范围。单一草稿PR用于远端验证，不合并、不更新生产main及V1.1。后续读取实际CI状态、修复阻塞后核对合并tree与在线版本身份；Chrome86真实终端及完整正式候选仍未验收。日志见业务视觉审查。回滚本次修复可独立revert；临时配置、合成认证fixture和浏览器缓存不入Git。

## V1.2 本地验收优先（2026-11-26）

- 维护者明确要求先在本地持续开发并检查实际页面，按反馈调整，只有收到整理/合并 PR 的明确指令时才推送累计改动并进行合并。现有 #850 保持草稿，远端候选 91083764，main 与 V1.1 冻结；不主动发布预览站。工作区 /Volumes/data/Project/MRR-worktrees/v1.2-table-gallery-contract。
- 本地用户预览：frontend-fantastic-admin 目录执行 `VITE_APP_DEMO_MODE=true VITE_BUILD_MOCK=true VITE_OPEN_PROXY=false pnpm exec vite --mode mock --host 127.0.0.1 --port 9037 --strictPort`。本轮 PID 23293（仅本次会话记录，重启需重新核验）。实际 Chromium 检查 /ui-components、/logs、/audit-images 成功，观测到 /proxy/ 请求均为本地 9037；已打开本机浏览器组件展厅。开发模式使用 history 路由，不使用 Pages 的 #/ 路由。用户预览端口固定，后续自动化使用不同端口及隔离会话，不关闭其他应用，不使用真实医疗数据。
- 保留原有风险分级、本地证据、成熟公共组件、中文提交及 AGENTS.md 更新；累计改动待维护者授权后统一进入 #850（届时核验分支和最新门禁），不得宣称本地未推送改动已通过远端 CI。
- 远端候选 91083764 的 Linux 完整跨浏览器验收 run 45665582083 最终 270 passed / 11 failed（21.6m），日志 /tmp/mrr-pr850-cross-failed.log；定向 WebKit 10/10 不代表完整矩阵通过。后续本地按失败具体断言与实际容器布局分类复现修复，不触发远端 CI，不降低门禁，用户预览继续运行。

## V1.2 本地残余控件及样式归属（2026-11-26）

- 基线 0161abbd，独立工作区 feat/v1.2-table-gallery-contract。71 个原生按钮/34 文件、475 个外部 EP 引用/60 文件/290 条 CSS 规则逐项审查，3 组相同选择器属性候选中合并两个滑块规则、保留不同密码字段内边距。详见 [本地控件归属审计](../design/local-ui-ownership-audit.md)。
- 三处普通业务按钮与 Manifest 两个选择器、OCR 只读原文、导航搜索、追踪编号使用统一底层；版本号保持数值、支持清空，异常筛选只作用已加载成员。授权数据浏览复用公共查询反馈，保留分页、估算与权限；图标尺寸/间距令牌及滑块样式共享，展厅添加真实数值选择示例；专用 FA 不机械替换。运维取消维护恢复触发按钮焦点。
- 原 11 个 Linux WebKit 失败在本机隔离基线 12/12（含预热）；共享 Mock 服务端 UI 设置可能跨用例影响，针对本地视觉契约隔离，不改生产优先级，未证明全部远端失败的根因。扩展首轮 WebKit 45/61（含预热），失败包含 Manifest 选择、焦点及 WebKit 不支持剪贴板权限。定向复验 WebKit 19/20、Chromium 11/12，唯一剩余版本比较通过等待真实可用状态和键盘展开修复，双引擎最终 3/3（含预热）。实际剪贴板验证仍由 Chromium 承担；不把跳过视为通过。最终样式与 Manifest 单测 94/94、设置测试 20/20（共114项）；类型、生产构建及 Chrome86 静态校验通过，存量容器查询/选择器/color-mix/动效提示仍保留，非实机结论。日志 /tmp/mrr-local-governance-*.log、/tmp/mrr-local-webkit-baseline.log。未执行全量 Linux/Firefox、真实 Chrome86 终端或本轮全部改动的像素对照；保留远端 #850 原草稿，不推送、不合并、不发布。

## V1.2 本地公共组件深化第二批（2026-11-26）

- 基线 2a10a360；数据浏览共享筛选、刷新、首次骨架与分页，四处普通按钮及四页业务图标归一。新增公开状态页匿名刷新/日期筛选和数据表延迟游标 E2E；Chrome86 基础按钮及导航/分类回退守护。无业务API、数据库、权限变更。
- Chromium/WebKit 定向26 passed / 1 skipped（含warmup；WebKit剪贴板权限不支持，Chromium实际复制通过）；116/116单测。最终生产构建及 Chrome86 静态门禁通过；未包在 @supports 内的 color-mix 产物提示由107降至104，完全无 @supports not 回退块的分块为无，仍有1处容器查询、29处选择器及8处动效时长提示。证据 /tmp/mrr-v12-next-{browser,unit,tsc,build,chrome86}.log。保持9037预览、独立9041测试，不推送、不创建PR、不合并、不发布。后续对剩余普通命令、专用FA滚动和PIN行为及真实Chrome86、视觉基线集中验收。

## V1.2 本地专用组件交互深化（2026-11-26）

- 基线 e6e89159；FaScrollArea 实际尺寸检测、稳定 viewport API、边界/水平滚轮及单次事件映射，Tabbar/NavSearch 解耦嵌套引用；FaPinInput 初始化、部分输入和外部值同步；FaBackToTop 焦点保持及计时器清理。导航链接 tabindex=0。保留成熟 Reka 底层及未启用的真实重置密码业务，FaPageMain 替换因确认页缺可达视觉基线而撤回。展厅增加专用交互及合成游标示例。
- 首轮双引擎19项：15通过、4失败（2 WebKit Tab 焦点、2 switch 隐藏输入点击）；补显式 tabindex、隔离本地外观、真实键盘切换后23项中21通过、2失败（aria-checked=true/实际批次正确，但原生 checked 不一致）。最终以可访问状态与完整业务断言、单次120px映射/边界不消费/原生水平滚动、新增三类交互双引擎7/7（含预热）。最终变更后的宽范围导航组合尚未整体重跑，不宣称全矩阵通过。
- 类型、116/116定向单测、生产构建、Chrome86静态校验通过；CSS静态提示104处未包裹 color-mix、31处Selectors Level4（前批29，增加2处产物选择器，待真实兼容对照）、1处容器查询、8处动效时长。未验证实际医院Chrome86、Linux全矩阵、真实验证码粘贴/提交、外链确认页及全部四主题像素对照。证据 /tmp/mrr-v12-special-{browser,final,target,unit,tsc3,build,chrome86}.log。
- 本地9037持续运行，测试9041独立；不改API、数据库、权限、正式版本或远端草稿#850。临时配置及合成认证fixture不提交；证据迁出工作区。

## V1.2 后台页头全站统一（2026-11-26）

- 基线 30b8b550。系统设置、帮助中心迁入 MrrPageHeader + fluid MrrPageShell，其他后台页删除 size 分档；page-header.css 收口唯一几何。保留保存/重置/重新加载、权限状态、长说明、实时元信息与 plain/card 风格。独立登录、强制改密、公开状态和外部任务页保留专用壳。
- 实际 26 路由 × Chromium/WebKit = 52 组几何；2400px 视口下均1720px宽、x=456、22px标题/13px描述/上下16px/左右20px，无溢出；设置、用户、帮助等90.39px高。数据关系说明及日志/错误中心元信息自然增高。用户、设置、帮助四主题五宽度共60组截图及几何通过。39/39浏览器用例（含预热），91/91定向单测，类型、生产构建与Chrome86静态门禁通过；存量103处color-mix提示、31处选择器、1处容器查询及8处动效时长提示。未执行真实Chrome86、全站四主题窄屏矩阵或全量Linux验收。
- 证据 /tmp/mrr-header-unify-{browser,visual,unit,tsc-final,build,chrome86}.log；截图 /tmp/mrr-header-unify-screenshots。仅本地提交，9037持续运行，9041测试隔离，禁止推送/合并/发布。

## 用户管理指标卡等高试验（2026-11-26）

仅用户管理页 opt-in `mrr-metric-grid--header-aligned`，card 页头模式下指标卡最小高度按公共页头的单行标题/说明、留白及边框计算；图标块44→40px，上下留白16→8px，缩小标签与说明间距，保留26px数值、12px说明及本页口径徽标。2400/1440px 下实测页头与四卡均90.39px（旧指标卡110px）；Chromium/WebKit × MRR/GR8R 明暗 × 2400/1440/1024/760/375共40组，未发现内容溢出；窄屏自然增高，不强制等高。其他页面和plain模式不变。用户尚未确认推广，不视为全站标准；未执行全量回归或Chrome86实机测试。证据 /tmp/mrr-user-metrics-trial.log、/tmp/mrr-user-metrics-trial-webkit.log、/tmp/mrr-user-metrics-before.png、/tmp/mrr-user-metrics-after-*.png。9037已打开用户管理，禁止远端操作。

用户管理等高试验第二版：根据维护者反馈，移除四张普通状态卡第三行 note（总数仍显示于用户列表），保留本页口径与原始值；图标块40→48px、字形20→24px，间距12→16px、上下留白8→12px，保持卡片页头等高试验。仅 users 采用该两行展示，其他页未推广。Chromium/WebKit × 四主题 × 五视口共40组通过，1440/2400px 等高，所有样本无内容溢出。真实截图 /tmp/mrr-user-metrics-two-line-*.png，日志 /tmp/mrr-user-metrics-two-line.log。尚未进行全量回归及 Chrome86 实机验收；等待维护者查看后决定是否推广，9037保持运行，不推送。

## V1.2 全站公共指标卡统一（2026-11-26）

- 基线 b98ef069；按维护者反馈取消重复「本页」徽标及无图标模式。用户页头统一说明当前页口径；记录管理增加三种图标并移除重复说明，装箱、统计详情等精简冗余文本，必要估算、样本范围、动态状态、附加操作保留。所有公共 MrrMetricCard 调用显式 icon，统一48px图标块/24px字形、26px数值、约90.39px最小高度，不强制裁剪复杂卡。删除 scope 类型、无图标与试验局部覆盖；dense仅控制网格间距。独立公开服务状态页保留专用摘要。
- Chromium/WebKit，14个后台业务路由桌面/窄屏及用户/记录/运维四主题重点矩阵共92组无溢出，65种图标资源全部存在。TypeScript、100项定向单测通过；首轮19个浏览器用例18通过/1失败（移动视口模式已切换但侧栏几何过渡未结束，瞬时正文21px），等待实际原宽度断言稳定后复验7/7含预热及记录查询游标。生产构建、Chrome86静态校验通过，未完成全量Linux跨浏览器/真实Chrome86验收或所有条件态截图。证据 /tmp/mrr-metric-unify-{matrix,e2e,recheck,unit,tsc2,build,chrome86}.log；截图 /tmp/mrr-metric-unify-{users,records}.png。
- 保持9037预览、仅本地开发；不推送、不创建/合并PR、不发布。不提交临时9041配置和认证fixture。

## 指标卡说明与光学比例收口（2026-11-26）

- 基线 d7bc5999。按维护者反馈将所有公共 MrrMetricCard note 移至标题行右侧 MrrInfoTip，取消第三行及 extra；原 note 字符串和统计值保留。没有 note 不显示 i。共享信息提示支持悬停/聚焦、点击固定、再次点击/外部点击/Escape关闭。运行错误整卡筛选使用独立按钮，运维查看日志移至整卡权限条件操作，避免嵌套按钮。
- 48px 图标容器不变，内部线性字形24→28px，比例50%→58.3%；数值26px、图文间距与约90.39px基础高度保留。实际用户页截图 /tmp/mrr-metric-optical-users.png；数据关系四卡实测90.390625px、图形28px。并非所有图标实际墨量已测量，长标题/数值自然折行。
- 首轮浏览器3通过/6失败（EP tooltip 管理 aria-expanded、错误中心提示被整卡覆盖、误用带元信息页头高度作几何基准）；独立 wrapper 叠层及真实浮层/基础高度断言修复后 Chromium/WebKit 9/9含预热，覆盖触屏、键盘、信息与主操作独立。122/122单测、类型、生产构建、Chrome86静态门禁通过；仍有103处color-mix、31处选择器、1处容器查询与8处动效提示。未重跑全站四主题矩阵、全部共享InfoTip调用及真实Chrome86。日志 /tmp/mrr-metric-info-{final,unit-final,tsc-final,build,chrome86}.log。
- 9037本地预览保持，独立9041测试；不推送、不创建PR、不合并、不发布。

## OSS 迁移四卡摘要（2026-11-26）

基线 73d019e0。根据维护者反馈，OSS 迁移顶部七卡收为四卡（总图片记录、已迁移、当前可领取、暂不可领取），复用公共 four 网格；桌面单排，窄屏自然换行。核查 ScanMapper.countMigrationStats 与 MigrationServiceImpl：总量、已迁移/verified 与可领取条件，后两者互斥；暂不可领取=同一快照总量−已迁移−当前可领取，缺字段或负值不伪造数值。四类细分状态在 i 提示保留，重试和缺上架号口径可与其他状态重叠，不求和；有永久失败用危险色提示。未改接口、迁移执行、筛选与权限。另检查访问审计分析五卡、运维完整性六卡，保留独立诊断维度，本批不改。TypeScript通过，Chromium/WebKit 新四卡及既有任务历史/详情共7/7（含预热）；1440px 单排、375px 无卡片溢出、合成60/31/18/11及全部细分数验证。未运行全量CI、真实Chrome86或全部四主题视觉矩阵。9037保持运行，不推送、不创建PR、不合并、不发布。

## 用户列表视觉精修（2026-11-26）

基线 17106a3c。按维护者截图，取消创建按钮旁重复总数，分页左侧保留唯一真实总数；多页保留 jumper，单页不显示。用户身份列合并账号与显示名（账号为主、显示名为次），头像32px，申请/角色/凭据/账号状态/最近登录/原固定操作列保留。搜索优先占剩余宽度，两个枚举筛选176px，窄屏自然换行；角色中性标签，正常凭据轻量语义点，异常保留共享状态标签；最近登录使用次级文字和等宽数字。未改接口、筛选参数、权限、账号状态或凭据业务。Chromium/WebKit 31/31（含预热，用户凭据生命周期、筛选、共享几何、多页翻页及单一计数）；Chromium四主题×五宽度20/20无筛选/分页溢出，实际截图 /tmp/mrr-users-polish-*.png。TypeScript通过；未执行全量CI、Chrome86实机及所有异常状态截图。仅用户页验证，其他列表不自动推广。9037继续运行，不推送、不创建PR、不合并、不发布。

## Quiet Enterprise UI 用户列表（2026-11-26）

基线 a997cf5b。参考 Carbon Data Table、Atlassian Spacing、SAP Fiori Responsive Table，定义本项目克制型业务表格视觉语言（DESIGN.md 附录）。共享 MrrDataTablePanel / MrrTableActions 新增 opt-in appearance="quiet"，仅用户页启用；统一单一表面、表头36px/普通行52px、公共左对齐线、无底色普通操作、角色纯文字、中性头像、正常凭据中性点/启用绿点，异常语义保留。筛选组480px上限/144px枚举/相邻按钮，实际截图发现初版非必要折行，改为有界网格；窄屏自然换行。未改变API、查询、审批、密码或权限语义。初轮31/31双引擎含凭据生命周期；最终布局定向11/11（含预热），双引擎四主题五视口40/40，1024及以上筛选同排、公共对齐0px偏差、无筛选/分页横溢；TypeScript通过。日志 /tmp/mrr-quiet-{browser,recheck,final-visual,tsc}.log，最终截图 /tmp/mrr-quiet-final-*.png。未执行全量CI、生产构建、Chrome86实机及所有异常状态视觉矩阵。9037保持运行；不推送、不合并、不发布，其他列表不自动推广。

## 用户列表主操作工具栏调整（2026-11-26）

基线 08961e80。维护者确认 Quiet 外观，将创建用户从卡头移到筛选工具栏最右侧，和查询/重置保持同高，保留 canManage 条件、原创建弹窗和提交逻辑。宽度不足时创建按钮换行且仍右对齐。Chromium/WebKit 四主题五宽度40/40几何检查通过，1440/2400px同排，1024/768/375px允许换行，均无溢出；TypeScript及定向浏览器9/9（含预热）通过。截图 /tmp/mrr-users-toolbar-final.png；日志 /tmp/mrr-users-toolbar-{visual,e2e,tsc}.log。未执行全量CI、生产构建、Chrome86实机。仅本地9037，不推送、不创建PR、不合并、不发布。

## 用户列表无标题与查询布局稳定性（2026-11-26）

基线 89743b87。根据维护者截图取消用户列表可见分区标题，保留 role=region / aria-label；MrrDataTablePanel title 改为可选，quiet headerless 恢复顶部16px/窄屏12px真实留白，保留外层圆角裁切、原生焦点环及浮层。删除 users 的全零隐藏规则，四卡持续占位；首次加载/失败显示—，查询在途保留上次成功数值，成功空结果显示0。未改查询参数、权限、计数口径或创建流程。Chromium/WebKit 19/19（含预热）覆盖首次、延迟查询、空结果、失败、恢复、焦点、选择器浮层及其他有标题分区；16/16公共组件单测与TypeScript通过。Chromium四主题×五宽度20/20，工具栏顶边搜索前后偏差≤1px，输入框距离顶部17px/窄屏13px（含边框），无工具栏溢出；实际空态截图 /tmp/mrr-users-stable-empty-*.png，日志 /tmp/mrr-users-stable-{browser,visual,unit,tsc}.log。其他页面的零值收纳规则暂保留，未运行全量CI、生产构建与Chrome86实机。保持9037，不推送、不创建PR、不合并、不发布。

## 用户列表文本分页与操作菜单精修（2026-11-26）

基线 b8a355ed。按维护者反馈，每页数量由 ElPagination sizes 下拉改为 slot 内文本按钮，保留10/20/50/100选项，点击或键盘激活循环切换、回到第一页、只发一次查询；桌面分页28px/箭头12px、窄屏32px。quiet 行操作通过共享 MrrTableActions popper class 和 menus.css 统一浮层，最小144px、行高32/36px、图标16px、正文13px、无尖角；重置密码中性，禁用用户危险色，禁用态统一弱化。保留原重置密码管理员密码校验、禁用确认、自身账号限制、响应式编辑收纳。分页 Chromium/WebKit 11/11（含预热），四主题五宽度20/20；菜单双引擎四主题双宽度16/16，25/25定向单测和TypeScript通过。联合浏览器首轮7通过/2失败（新增测试误写重置用户密码弹窗名称），修正定位后3/3（含预热），原禁用确认/403重试测试通过。真实截图 /tmp/mrr-quiet-menu-*.png 与 /tmp/mrr-pagination-text-*.png；日志 /tmp/mrr-pagination-text-{browser,visual,tsc}.log、/tmp/mrr-pagination-menu-{browser,recheck,visual2,unit,tsc}.log。未运行完整CI、生产构建及Chrome86实机。仅本地9037，未推送、合并或发布。

## Quiet 操作菜单内容自适应（2026-11-26）

基线 455155b5。按维护者截图删除固定144px最小宽度，使用 max-content 与视口上限，保留4px菜单内边距、8px项内距及16px图标；四字操作实测102px、左右13px，原32/36px基础行高、危险语义及自身账号限制不变，长文字可自然换行。Chromium/WebKit 四主题双宽度16/16（含模拟长文字无横溢），既有菜单确认流程和新增左右字形边界断言3/3含预热。日志 /tmp/mrr-menu-fit-{visual,e2e}.log，截图 /tmp/mrr-menu-fit.png。未执行全量CI及真实Chrome86。仅本地9037，不推送。

## 用户申请信息渐进披露（2026-11-26）

基线 1c56a76c。按维护者反馈，申请列由多行堆叠改为内容宽度约束的单行摘要按钮，申请说明优先，其他字段回退，点击共享 MrrDialog 阅读完整申请信息（身份、状态、联系方式、申请/审核时间、申请说明、拒绝原因）。管理员创建且无申请信息的账号不提供无用按钮；仅有拒绝信息或待审核状态也保留详情入口。原始文本与换行不改，超长连续字符换行、详情内部滚动，审批摘要同步改善长文本换行；无新增API/写入/权限变化。TypeScript通过；Chromium/WebKit 合成多段长文本、相同行高、完整原文、窄屏无横溢、键盘进入/关闭恢复焦点、只读请求及既有拒绝审核回归5/5（含预热）。日志 /tmp/mrr-application-{tsc,browser,lint,style}.log。未执行全量构建、四主题视觉矩阵及真实Chrome86，未操作真实数据。仅本地9037，不推送、合并或发布。

## Quiet 表格固定列悬停背景（2026-11-26）

基线 908c72ef。按维护者截图检查 Element Plus 普通单元格透明、固定列继承不透明背景及250ms独立过渡。双引擎真实截图采样复现过渡中1色阶差异，未完整复现用户截图的整块白底，因此根因不作唯一性断言。共享 Quiet 面板统一行级背景变量，普通/固定列保持不透明，保留斑马纹、悬停及选中优先级，取消单元格背景过渡，按钮反馈不变。修复后双引擎32次截图采样两列一致；首次E2E 3通过8失败（过早读取仍为基础色），等待实际悬停色后复验11/11含预热，覆盖四主题三宽度、固定列滚动、移入/移出及候选病案键盘选择。证据 /tmp/mrr-hover-{before,after}.log、/tmp/mrr-row-hover-{browser,recheck,lint,style}.log。未执行完整构建、Chrome86实机，未完全复现用户端截图严重程度。仅本地9037，不推送、不合并、不发布。

## 用户管理演示数据扩充（2026-11-26）

保留原三基础账号，新增25条纯虚构用户（总28条：待审核6、启用13、拒绝4、禁用5；第一页4/10/3/3）。包含长申请、仅联系方式、拒绝原因、已禁用、初始密码待修改、密码重置及临时密码过期。使用 example.invalid 联系方式与相对演示时钟，不生成真实凭据，原登录与审计基础映射保留。Mock 搜索补齐联系方式及角色。历史三账号布局测试使用独立 fixture，新增实际 Mock 分页及申请详情回归。9037 实际 API 核实28条、第二页8条及各筛选；定向单测2/2、TypeScript、双浏览器23/23（含预热）、ESLint通过。未执行全量构建、正式发布或真实数据库操作。

## 用户列表状态、排序和对齐（2026-11-26）

基线 cdcd6cff。恢复正常/启用的公共绿色状态标签，删除局部透明和中性色覆盖。实测申请摘要按钮内部 span 继承公共 justify-content:center，较普通来源文字偏移30px；改为左对齐，保留省略和只读弹窗。正式 AuthUserMapper.findAllWithPagination 已待审核优先、COALESCE(applied_at,created_at) DESC、id DESC；Mock 对完整筛选结果采用相同排序再分页，补齐基础账号创建时间，未修改后端。第一页6/9/3/2，第二页0/4/1/3，历史三账号布局 fixture 不改排序。双浏览器四主题四宽度32/32，全部摘要起始线偏差0px；定向浏览器9/9含预热、数据单测3/3、TypeScript通过。日志 /tmp/mrr-user-order-{browser,visual,unit,tsc}.log，截图 /tmp/mrr-user-order-final.png。未执行全量构建、Chrome86实机及真实数据库验收；仅本地9037，不推送。

## 用户操作列与注册申请恢复（2026-11-26）

基线 49d1e4a8。统一四状态铅笔+更多，固定96px，待审核打开审核窗（通过/拒绝），已拒绝确认恢复待审核，禁用编辑/恢复启用，启用编辑/密码重置/禁用。检查正式后端原无恢复接口且通用编辑明确禁止直接激活注册申请；新增 POST /api/v1/auth/users/{id}/registration/reopen，user:manage + 活动管理员验证，事务及管理员角色锁，rejected 条件更新，仅改状态和更新时间，保留原申请、拒绝决定、角色和密码，记录安全审计。重新审核后方可启用；堵住 pending/rejected 经 DELETE 禁用再编辑启用的绕过路径。无数据库迁移。静态演示新接口沿用原503写入限制；前端完整成功链使用隔离模拟响应，非真实医院数据。Java21 后端44/44单测，独立新建PG16+56条现有迁移的真实MyBatis集成1/1，确认状态、原密码哈希/版本、角色和拒绝原因保留、重复恢复失败及后续审核。Chromium/WebKit 9/9含预热，覆盖四状态三视口两入口、恢复取消/409重试/重新审核、拒绝原因和禁用原流程。TypeScript、ESLint、Stylelint定向检查；未运行全量前后端CI、Chrome86实机或真实HTTP认证链。证据 /tmp/mrr-user-actions-{backend-final,browser,tsc,lint,style}.log。9037保持运行，仅本地，不推送、不合并、不发布。

## 注册申请弹窗与审核外观归一（2026-11-26）

基线 40f6cc17。原详情/恢复为 MrrDialog，审核为直接 ElDialog，底层均 Element Plus。改为一个 MrrDialog 实例的 detail/review/reopen 三模式，共享 review-summary 双列语义字段及公共 overlay-shells.css 的 opt-in business 外观；无需新组件，540px宽度上限、16px标题、桌面20px/窄屏16px水平内边距、卡片底色、内容可滚动和对齐底栏。保留详情只读、审核角色及原拒绝原因 prompt、恢复待审核，统一忙态防关闭/重复提交，未改变后端/API。实际 Chromium 截图 /tmp/mrr-registration-{detail,review}-v2.png；四主题双宽度三模式双引擎48组布局检查及既有长文本、审核、恢复、拒绝业务回归共9/9含预热；TypeScript、ESLint、Stylelint定向通过。日志 /tmp/mrr-registration-dialog-{browser,tsc,lint,style}.log。其他普通用户编辑/创建/密码对话框和全局默认 MrrDialog 未迁移，未运行全量CI/生产构建/Chrome86实机；仅本地9037，不推送、不发布。

## 全站业务弹窗与分页微交互（2026-11-26）

基线 2ce2ad95。MrrDialog 默认 business 外观、水平垂直居中；ActionDialog 和剩余五个用户管理弹窗复用已有封装，14处直接 ElDialog 启用原生 align-center 并共享 overlay-shells.css 外壳，原宽度、回调及业务权限不变。移除原固定5vh及局部双层边距，长内容内部滚动。个人设置与导航搜索保留 custom，搜索保留顶部命令面板位置；抽屉与直接 ElMessageBox 确认框未迁移。拒绝确认采用 danger。分页统一页码4px间距，原生 jumper 无边框数字静止态，实际 focus-within 控制编辑样式，窄屏同导航区，保留原分页请求；返回顶部24px→16px，不改点击区域。弹窗双浏览器25/25含预热，含四主题48组申请弹窗几何、拒绝校验、凭据、诊断与运行设置。分页首轮3通过2失败（原生内部焦点类残留），调整焦点判定并模拟点击外部后复验5/5含预热，含真实跳页及键盘返回顶部。定向单测30/30，类型及样式检查。日志 /tmp/mrr-dialog-global-*.log、/tmp/mrr-dialog-pagination-final-*.log、/tmp/mrr-pagination-refine-*.log。未逐一打开全部原生业务弹窗，未运行全量CI/生产构建及Chrome86实机。仅本地9037，不改后端、不推送、不发布。

## 分页数字排版与大量页码（2026-11-26）

基线264e6605。公共 data-display.css 清除 Element Plus 默认16px重复间距，页码4px、桌面组间12px，等宽数字，跳页保留原生焦点编辑与范围校验。用户管理采用既有 VueUse useMediaQuery 切换原生 pager-count 7/5，省略号另计最多2项，360px及以下跳页独立换行。类型检查初次缺少导入已修正。原窄屏双引擎回归2/2，新矩阵两引擎各44组（2/50/500/5000页、1440/768/375/320px、首中尾页），数量断言按原生数字与省略号规则修正，最终请求清理等待后2/2。证据 /tmp/mrr-pagination-spacing-{recheck,final2-browser,tsc-final}.log。未增加永久Mock数据、未全量CI/构建/Chrome86实机。仅本地，不推送或发布。

## 用户列表序号（2026-11-26）

基线3648bf4f。复用 ElTableColumn index，64px最小宽度按总数位数扩展，等宽次级数字；使用成功加载请求的页码/页长计算偏移，避免刷新中旧数据提前变号。更新申请与账号状态测试定位为语义列类。实际28条Mock双引擎2/2，验证首20/次8条、筛选、重置、每页50条及既有申请详情/状态对齐；TypeScript通过。日志 /tmp/mrr-user-index-{browser,tsc}.log。未全量构建、Chrome86实机；仅本地，不推送。

## 业务表格列宽分配（2026-11-26）

基线 d9278c7c。优化用户、记录、患者、日志、访问审计、档案装箱、OCR、统计、运行错误、OSS迁移、接口分析11个页面；固定短字段，长字段弹性分配，保留原生列配置及既有固定操作列。1920px实测用户列506→240px、申请信息414→718px、记录文件名277→439px、日志URI390→602px、运行错误摘要457→604px。修正 quiet 公共首列非对称内距，排除居中首列。首轮28项中21通过，3项WebKit患者身份证测试因clipboard-write权限初始化失败，4项新增矩阵发现序号居中冲突；修复后矩阵4/4，12种表格×两主题组合×3宽度×2引擎=144组，检查固定列宽、弹性最小宽度、文档溢出、窄屏内部滚动、序号居中与用户状态标签。Chromium患者隐私3/3，记录、统计、用户Mock双引擎18/18（公共内距修正前）。保留专业动态表及其独立宽度配置，不变更后端。证据 /tmp/mrr-column-{before,after,browser,recheck,tsc}.log。未执行全量构建及Chrome86实机；仅本地开发，不推送或发布。

## 用户管理表格回归公共外观（2026-11-26）

基线 ff084b5f。按维护者反馈取消用户管理独有 Quiet 面板和操作外观，恢复默认公共表格、斑马纹、独立用户名168px/显示名称132px、圆形头像及普通字段文字。保留申请单行摘要、连续序号、待审核排序、铅笔+更多、恢复审核、单一计数与分页交互；不回退后端。将原 Quiet 固定列同步行背景规则提升到 MrrDataTablePanel 共享范围，避免悬停断层回归。9037 实测用户、记录、患者三表42px表头、46px行高、8px圆角、13px字体、12/20/20px内容内距及表头颜色一致。Chromium/WebKit 24/24，包括12种表格144组列宽、四主题固定列悬停、申请与审核、真实Mock分页及桌面窄屏。TypeScript通过。证据 /tmp/mrr-user-default-{browser,tsc}.log。未全量构建、Chrome86实机或逐页全量验收。仅本地开发，不推送。

## 用户行操作样式及筛选分隔线修正（2026-11-26）

基线7a27d43c。按维护者反馈保留默认公共表格，仅恢复现有 MrrTableActions quiet 外观及紧凑无箭头下拉菜单；用户嵌入筛选栏局部取消底边框，不改公共分隔线规则。9037实际测量底边框0px、表格上间距12px、表头42px、操作按钮透明背景。Chromium/WebKit定向14/14，包含四主题固定列悬停、菜单32px行高/16px图标/对称留白、权限及审核恢复。未全量构建或Chrome86实机；无后端修改，仅本地开发。证据 /tmp/mrr-user-actions-restore-browser.log。

## 全站普通表格及分页底栏统一（2026-11-26，本地候选）

基线3d694c6b，保留此前12文件未提交修改。默认紧凑行操作、公共嵌入筛选无重复分隔线、单一结果总数、原生选择器轻量外观；新增 MrrCursorPagination，用于扫描记录、数据库浏览、存储文件浏览及展厅，保持游标与已提交查询条件，不虚构精确总数或任意跳页。病案明细档案袋与列表迁入 MrrDataTablePanel，实际内层宽度决定完整行页大小，保留选择、影像和导出；OSS迁移历史/日志迁入公共底栏，未修改写入逻辑。125/125定向单测，TypeScript、定向Stylelint及Diff检查通过；新增 Chromium 档案分页契约用例通过，九业务表格明暗两组合通过。联合16项Chromium首次14通过、2失败（数据库测试仍使用旧的合并页号/摘要定位，运行期间调整定位）；更新定位后数据库两项及展厅游标专项3/3通过，累计16项业务用例及预热通过。证据 /tmp/mrr-pagination-final-browser.log、/tmp/mrr-pagination-final-recheck.log；WebKit 2388 可执行文件缺失，未宣称通过。测试认证状态及产物不得提交。9037实际HTTP 200，未修改后端、未推送、未合并、未发布，当前候选仍未提交。后续恢复WebKit浏览器并复验，检查维护者实际页面效果后集中本地提交；未执行全量CI、正式生产构建及Chrome86实机。

## 分页紧凑排版（2026-11-26，本地候选）

维护者要求每页条数置右、箭头与数字标签间距缩小。调整全站原生分页 layout、用户文本 slot、游标组件顺序；公共 CSS 收缩原生选择器及跳页输入。Chromium 原联合8项中7项通过，新增专项首次因测试 route.fetch 响应销毁失败，改为隔离合成200万条总数响应后1/1通过，验证4px箭头间隔、右侧布局、100条/页及12345跳页、1440/768/375/320宽度无溢出；TypeScript、定向Stylelint和Diff检查通过。WebKit及Chrome86实机未验收，9037 HTTP200，未提交推送。日志 /tmp/mrr-pagination-spacing-{browser,recheck,tsc,style}.log。

## 分页交互最终统一（2026-11-26，本地候选）

用户页恢复原生 sizes 下拉，保留单页隐藏 jumper、右侧位置、回第一页仅一次查询；MrrCursorPagination 真实下拉及有界前往输入，越界不发请求。扫描记录不更改大表 ID 游标 API，数据库浏览遵循注册表页大小上限并事务式提交成功分页状态，OSS 目录已知 token 跳转、Nginx offset 精确跳转。档案列表增加手动条数，档案袋恢复整行自适应；OSS 迁移任务/日志补齐 jumper；展厅同步游标控件。定向单测55/55，TS通过。Chromium 初轮10通过1失败（测试在返回批次加载中点击禁用选择器），补等待后专项通过；最终存储与档案组合回归见 /tmp/mrr-pagination-dropdown-storage-browser.log。未修改后端或真实数据，未执行全量构建、WebKit/Chrome86实机及全站视觉矩阵。固定大小存储浏览、专业影像核对与批量审核仍保留业务约束。9037预览可用，本地候选未提交、推送或发布。

## 普通与游标分页控件归一（2026-11-26，本地候选）

MrrCursorPagination 改用 ElPagination 原生页码、箭头、跳页、条数选择，移除自绘控件和专属 CSS；公共 data-display.css 接管桌面28px/窄屏32px及选择器、箭头、选中态。显式引入原生组件 CSS，修复按需加载优先级。以可达 page-count 适配游标，原生越界输入提交前拦截，不触发查询；不改动后端。定向单测48/48；Chrome86 CSS 回退复验11/11；Chromium 初轮6通过、扫描样式断言失败，修复后最终4/4（扫描/档案、普通分页间距、明暗业务表格），另用户条数及展厅专项2项通过。日志 /tmp/mrr-native-pagination-{unit2,browser,recheck,final-browser,fallback-final}.log。未执行全量CI、WebKit与Chrome86实机；9037预览可用，未提交、推送或发布。

## 表格序号全站统一（2026-11-26，本地候选）

全局清点58个 Element Plus 表格实例，51个使用公共 tableSequenceColumn（其中2个原有序号归一），慢接口排行新增排名，另2个原有排名保持；排除权限矩阵、Manifest固定分段、2个导入错误源行号表。HTML文件浏览列表新增页内序号，诊断原始样本表保持原始字段。已加载结果保存连续序号偏移，扫描/数据库游标存实际读取行数的历史，避免虚构总数。定向单测50/50，主业务Chromium 9/9通过，初轮单测因显式依赖CSS在Vitest Node环境加载失败，隔离CSS后通过。扩展列宽及专业工作台测试见 /tmp/mrr-table-sequence-secondary-browser.log；扩展测试中运维看板首次加载失败用例未找到“等待 0 · 处理中 0”文本，不将其视为已通过；其余以日志最终结果为准。全量CI及WebKit/Chrome86实机未执行，9037 HTTP200，未提交、推送或修改后端。

## 表格状态及控件防溢出（2026-11-26，本地候选）

扫描记录 waiting_sjh 原映射缺失且112px列使用整单元格文本省略，现共享16种迁移状态文案映射，状态列最小132px，保留原始码title。19个页面文件37个状态列统一 mrr-table-status-column，已知文案单行优先、未知长文案换行、圆点不压缩。接口日志长Request ID与复制按钮分离，装箱和错误模块长链接内部省略。Chromium 4/4（九业务表格双主题、15路由控件几何排查、合成超长未知状态与Request ID），其中统计和慢接口路由未取得可测标签控件，其他13路由共456个匹配控件未发现越界；不能宣称所有状态组合已浏览器验收。定向单测24/24、TS、定向ESLint/Stylelint及Diff检查通过。此前序号扩展回归26/27，运维看板状态文案断言仍失败，未在本轮扩展范围。日志 /tmp/mrr-table-overflow-{after,unit,tsc2,style,eslint2}.log。未连接真实医疗数据，WebKit/Chrome86实机未验收，本地9037 HTTP200，未提交推送发布。

## V1.2 页面级UI集中精修（2026-11-26）

检查点88bf1b47（61文件）保存此前未提交成果；本批在同一工作区P2实施，具体变更及边界见 docs/design/v1.2-ui-system-roadmap.md 和 DESIGN.md 最后章节。99/99定向单测，Chromium首轮36/40，旧名称和测试环境修复后双引擎10/10，最终截图反馈两处精修后双引擎6/6。原运维状态文案断言已通过真实信息浮层交互。生产构建/Chrome86静态检查在最后两处微调前通过。未跑全量CI和全站视觉矩阵，未发布，未推送，未合并，保留真实医院数据与冻结 main 24152bee。WebKit使用现有外置缓存；本机Corepack缺失，临时入口转发仓库锁定且已安装pnpm 10.33.0，原lint-staged与commitlint正常执行，不跳过hooks。9037 Mock可用。

## 第二轮页面级UI精修（2026-11-26）

基线8555ba5c，P2共享UI和页面排版。运维总览、关系、扫描统计、用户/患者编辑、OSS任务布局，见DESIGN.md及路线图。104/104定向单测与样式，新增Chromium/WebKit 8/8，现有Chromium业务36/36；日志 /tmp/mrr-ui-compose-{unit,recheck,regression,final-tsc}.log。首轮患者测试选择器不含原生必填星号，修正为实际可访问名称并复验。截图证据存外置工作区，不提交截图与凭据。未全量CI/生产构建/Chrome86实机、未修改后端/真实数据、未推送发布。

## 第三轮设置与监控UI治理（2026-11-26）

基线6856b202；P2跨页面共享视觉。完成设置分类/状态、监控与响应分析、装箱/OCR/小组件表单及共享反馈，保留原有表格分页与后端契约。150项定向单测，最终10项复验；新增Chromium/WebKit 10/10、累计Chromium37/37；25路由100组截图/几何及最终8组复验，0受检溢出。类型、构建、Chrome86静态检查通过，存在既有CSS依赖降级提示。日志/tmp/mrr-ui-quality-*.log，截图外置artifacts/mrr-ui-quality-20261126，复现脚本scripts/ui-page-matrix.mjs；未全量CI、真实Chrome86、Firefox、生产发布和医疗数据。保持9037 Mock、main 24152bee。

## 第四轮UI候选精修（2026-11-26）

基线dd54fee9。累计33提交187文件范围审计，历史后端注册恢复相关权限/状态/事务定向阅读和40项单测通过；本轮前端语义色、显示范围、设置行、外链状态、展厅和浮层。207项定向前端单测、26项既有展厅双引擎、6项新增双引擎最终通过，先前失败由焦点恢复和原生输入定位修正。截图100组通过，证据目录artifacts/mrr-ui-candidate-20261126/verified，日志/tmp/mrr-ui-close-*.log。TypeScript/生产构建/Chrome86静态通过；保留兼容警告，未完成全量CI、真实PG专项、实际旧浏览器、医院终端、全部配色交互或逐文件累计审查。保留main 24152bee与9037 Mock，不自动推送合并发布。

## 第五轮高精度UI与候选验收（2026-11-26）

基线da556bdb，34提交197文件范围核对及注册恢复高风险API/SQL定向检查。修复EP暗色卡片级联、条纹、语义ink、图表颜色/缩放/图例、档案长字段及记录批量状态。全量前端166文件1110项通过；首次OCR三项因测试未适配ElInput失败，保留安全断言补充替身后全量复验通过。Chromium/WebKit核心风险48+48项、暗色/缩放/批量新增6项通过。PG16.16全新隔离库六类18项通过，实例停止；57迁移校验。100组视觉矩阵0失败，双浏览器6路由暗色截图实测；证据artifacts/mrr-ui-final-20261126，日志/tmp/mrr-ui-final-*.log。类型、构建和Chrome86静态通过，兼容警告仍在。未全量远端CI/所有E2E/实际旧浏览器/Firefox/医院终端/逐文件完整审查；保留main 24152bee、9037 Mock，不自动推送合并发布。

2026-11-26：设置搜索补测最初14/15；首次配置尚未挂载时搜索锚点丢失，改为等待加载结束、检查导航序号与当前分区，原焦点/滚动断言不变，Chromium/WebKit复验2/2。其余14项控件通过。

## 普通业务筛选工具栏统一（2026-11-26）

基线02ca32c9。集中复用MrrFilterBar、语义字段宽度、操作分组、容器响应式与高级条件成功应用快照；普通列表、装箱、病案明细、OSS、数据交换5种模式和展厅，数据库动态网格与专业身份工作台保留适用结构。无后端、权限、分页组件、请求参数或医疗数据变更。默认高度33px沿用令牌而非新增32px；原生分页4px间距保持不变。

最终全量前端166文件1110/1110；类型及生产构建、Chrome86静态校验通过，仍有既有依赖兼容警告。首轮Chromium34/37，原记录标签测试经真实关联标签恢复后通过；双引擎较广回归89/94，失败涉及已淘汰分页选择器/旧间距断言、WebKit重复相同条件请求与一次高级条件请求捕获时序。修正后双引擎相关10/10；不将定向复验计作完整94项重跑。最终9037 Mock明暗主题、1920/1440/375、17路由/模式共102组108栏，受检溢出/重叠0、控件实测33px。证据 /Volumes/data/AI-Development/artifacts/mrr-filter-unify-20261126/final，复现scripts/ui-filter-matrix.mjs，日志/tmp/mrr-filter-unify-*.log。未执行远端CI、真实Chrome86及医院终端、全站交互矩阵。保持main 24152bee和9037 Mock，不自动推送合并发布。

## 筛选默认收起与按钮精修（2026-11-26）

基线6d6bd521；MrrFilterBar首次默认收起，有值显示已填写数量。统一按钮几何，新增/导出使用克制的主题描边和语义前景，窄屏动作尽量合行。原截图复测默认主题按钮外框均33px，差异为视觉重量而非实际高度；gr8r为30px。首轮9037 Mock五种页面/模式×两配色×明暗×四宽度共80组几何无失败，双引擎业务44/44；最终颜色调整后新增双引擎按钮尺寸/对齐8项复验，截图保存在/Volumes/data/AI-Development/artifacts/mrr-filter-compact-20261126。未执行全量CI/生产发布/真实医院终端，不变更查询权限及数据契约。

## 系统设置工作区精修（2026-11-26）

基线d4d876cd。目录独立卡与桌面sticky，原服务器及自管面板命令迁到分区头部，内容宽度决定并排/换行；窄屏取消固定、分类分组下拉。表单双列、统一间距、常驻草稿提醒、键盘目录导航、搜索避让及外观同步说明。未改保存请求、权限、医疗数据或表格分页。设置及相关双引擎回归62/62；设置单测50/50；最终布局与搜索复验6/6。11分区×明暗×1440/1024/375共66组视觉矩阵0受检溢出，最终服务器命令宽度调整复验12组。截图 /Volumes/data/AI-Development/artifacts/mrr-settings-polish-20261126/{before,after,final}，日志/tmp/mrr-settings-polish-*.log及/tmp/mrr-settings-header-recheck.log。TypeScript、定向lint通过；未运行全量CI、生产构建、真实医院终端或所有配色/交互状态。保留9037 Mock与main 24152bee，不自动推送、合并、发布。
