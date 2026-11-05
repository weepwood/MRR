# 首轮导出与打印资源预算

关联 #504、#171、#350；风险 P1。用户已确认首轮限制导出规模，多 GB 完整下载另行实施。基线 `d66235c3`，无数据库迁移。

## 调查与任务登记

分支 `codex/504-export-browser-budget`；worktree `D:/Project/CodexWorkTress/export-browser-budget/MRR`；负责人：实施代理；状态：进行中。

现有链路：档案袋 → `useArchiveExportJob` → 已有统一 Axios → 导出任务 Controller/Service → PostgreSQL 任务表与受控临时文件。Range 写盘已存在；无 picker、前缀不符及若干错误会回退整份 Blob。默认后台文件上限为 10 GiB，而自动下载没有浏览器预算。服务端估算还会将实际估算值压到最大配额，不能用于提前拒绝。

复用现有任务、权限、所有者、幂等、取消、恢复、配额与临时清理，不新增下载票据、认证客户端、线程池、数据库表或存储组件。不以 Blob 字节数声称测得 JS 堆占用。

## 本批行为与兼容影响

- 首轮单文件默认上限 128 MiB（134217728 字节）。Java 默认、应用配置及 Windows 生产模板一致；服务端保留原环境变量。前端上限固定，单改服务端配置不能开放大文件。
- 创建任务时使用未截断、溢出饱和的估算值；超配额在 insert 和排队前返回 413，未知单图大小继续用现有保守每图估算。等于上限允许，不代表最终压缩输出一定符合上限。
- 任务文件与兼容流式 ZIP/PDF 共用实际输出限制器；超过上限抛异常，不静默截断或报告成功。旧流式接口可能已发送部分响应，失败不能作为有效文件；首轮优先使用已有后台任务。
- 自动/手动任务下载均先要求 `outputBytes` 为正的安全整数且不超过预算。未知大小不退回无界下载，提示刷新任务或减少选页。
- 内部任务与兼容 ZIP/PDF 下载均经现有 Axios，检查进度中的 total/loaded，超限主动取消，并复核实际 Blob 大小。进度回调可能延迟，不能证明峰值内存严格等于128 MiB。
- 无文件系统 API 的 Chrome86/HTTP 终端仍可下载预算内文件。Range 每块8 MiB机制保留；权限/过期等HTTP拒绝、取消不再追加整份下载请求；其他回退仍必须经过相同预算与长度校验。
- 下载被拒绝不改成功任务状态、不删除文件；保留重新下载入口，并明确“已生成，但下载未完成”。服务端下载也检查受控文件实际大小，超当前配额返回413；上线前产生的超预算成功文件不自动删除。
- 浏览器 PDF 合成累计与最终输出复用同一预算。打印一次最多50张；超限整体拒绝并提示分批，不自动只打印前50张。
- API 路径与响应结构、权限/身份、Range协议和表结构不变；超预算导出从晚期失败提前为413，默认大文件能力收紧是本次明确兼容变化。

选页分批首先复用现有选中 PDF 导出；当前整案 ZIP 按钮仍表示整案，不假称它会自动分卷或下载选中 ZIP。完整大病案 ZIP、多 GB 下载、外部 Ticket 下载专项和旧链路退役留在原 Issue，不关闭 #504 的现场验收事项。

## 测试与现场交接

先失败测试：未知/负数/非整数/非有限大小、128MiB边界与超限、无picker、Range权限拒绝重复下载、实际字节超限、传输无Content-Length、创建前估算超限/未知大小、同步ZIP/PDF绕过配额、打印超50张、浏览器PDF总量和下载失败状态。

本地使用定向 Vitest 与 Maven 单测，类型/变更 lint、构建/Chrome86静态检查及仓库治理。SQL未修改；PR按既有分层CI执行PG16回归。本机未具备可用PG测试前置环境，不能把skip当作通过。

2026-11-05 本地证据（合成夹具，不是医院验收）：

- 首轮新增前端回归先复现17项失败；补充打印/PDF及生成与下载状态回归也先失败，再修改对应实现。
- `corepack pnpm exec vitest run` 定向执行下载工具、下载API、任务状态、打印和客户端PDF五个文件：36项通过。
- `mvnw.cmd -B -ntp -Dtest=ArchiveExportJobPreflightTest,ArchiveExportOutputBudgetTest,ArchiveExportTempFileManagerTest,ArchiveExportServiceImplTest,ArchiveExportJobFailureTest,ArchiveExportJobServiceValidationTest,ArchiveExportJobPolicyTest,ArchiveExportJobOwnershipTest test`：41项通过，0失败/错误/跳过。创建前拒绝和旧文件超限回归均曾先失败。
- `corepack pnpm build`、`corepack pnpm test:compat:chrome86`通过；静态检查仍提示既有依赖CSS的装饰降级，不等于Chrome86真机通过。
- 实际命令日志保留在本worktree的忽略构建目录；独立审查、PG16 CI及医院现场结果单独记录，不包含在上述通过数内。

用户已确认具备获授权隔离PostgreSQL16副本与测试影像源，后续由现场负责人执行 #350。必须绑定同一候选SHA和制品摘要，在真实Chrome86、Nginx HTTP及混合存储上验证下载、选页、页序、打印取消、配额与内存/临时空间；具体身份、位置与原始证据留院内。这里不填写虚构PASS或RPO/RTO。

## 回滚与剩余风险

本批无迁移、不修改医疗数据。开发回滚使用独立revert并重建当前安全基线；现场暂停新导出、等待活动任务结束，保留任务、成功文件、审计和影像。恢复10GiB默认会重现浏览器资源风险，应优先前向修复，不回退Token撤销/隐私日志安全基线。

128MiB/50张只是首轮保守预算，真实终端尚待验收。PDF解码、图片加载、进度回调时机仍影响峰值资源；自动分卷、任务下载中离页取消和全量旧接口退役不在本批声称完成范围。P1合并前必须由独立审查者直接读取需求、diff、测试与架构规范，未取得独立审查时保持草稿，不自称通过。
