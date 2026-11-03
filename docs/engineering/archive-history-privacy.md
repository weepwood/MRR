# 档案袋查询历史会话隔离

关联 #480，风险 P1。任务负责人：Codex；分支 `codex/archive-history-privacy`；worktree `D:/Project/CodexWorkTress/archive-history-privacy/MRR`；状态：待合并。基线 `2b03f9c8`，2026-10-29。

## 调查与边界

调用链：ArchiveHeader/useArchiveImages → useArchiveSearchHistory → 现有 archive-search-history API → 已有按真实用户归属的后端服务。旧浏览器共享键没有可信归属，退出不清理，服务端空结果会回退旧历史，失败正文直接落盘。

复用既有用户 Store、统一 Axios、后端历史与收藏。删除旧共享键，不迁移它；浏览器只保留当前已验证且具有稳定用户 ID 的会话内存缓存，最多100条、30天。刷新后从既有服务端历史恢复；本地缓存不可用不会阻止病案查询。服务器历史与收藏不删除，不新增 API、Store、表、迁移或认证协议。

身份或 Token 变化、跨标签页认证存储变化时清空旧展示。查询开始时捕获会话，异步创建/同步/收藏回调核对同一会话；统一 Axios 对这些请求在发送前及认证副作用前核对绑定 Token。失败历史仅保留固定“查询失败”，不再持久化或上传异常正文。收藏失败按单条操作标记回退，保留其他历史变化。

不包含记录工作台 #469 的移植、服务器历史留存政策、全局认证生命周期重构、影像查询/下载/外部票据变更及任何医疗原文件操作。

## 验证证据

- RED：现有实现运行新增及修订规格的15项历史测试，12失败、3通过；复现共享键残留、跨会话回退、未验证身份、失败正文、晚到响应、条数与到期边界。
- GREEN：`node node_modules/vitest/vitest.mjs run src/views/statistics/archive/composables/__tests__/useArchiveSearchHistory.test.ts src/views/statistics/archive/composables/__tests__/useArchiveImagesConcurrency.test.ts src/api/__tests__/session-bound-request.test.ts`：3文件24项通过。
- `node node_modules/vue-tsc/bin/vue-tsc.js -b`：通过。变更的TS/Vue及新增E2E文件定向ESLint通过。
- 统一Axios原始二进制响应与认证存储两文件12项通过；ArchiveHeader定向Stylelint通过。
- Playwright真实Chromium + 独立Mock Vite端口：新增 `e2e/archive-history-privacy.spec.ts` 1项通过。浏览器实际加载源码模块与用户Store，以合成会话验证不落盘、退出清空、B服务端空结果无A记录。未使用真实患者或生产Token。
- 测试资产、发布基线、工程文档、架构边界和 `git diff --check` 通过。本地未运行全量测试套件。

两个依赖清单与主工作区一致，使用已有依赖junction；pnpm的依赖提示不会视为执行成功，测试直接调用已安装的Node入口。没有安装/升级共享依赖。Mock启动产生的env.d.ts改动已恢复，测试进程已停止。

## 独立审查

独立只读代理 `privacy_review` 使用全新上下文直接读取Issue、diff、测试及架构规范。初审实际复现两项额外竞态：旧会话401导致新会话退出；收藏失败被全局revision变化阻止回退。两项均修复并补交错回归。

增量审查复核原探针，2文件23项通过；实际挂载ArchiveHeader的身份/storage清空2项通过；使用真实历史模块的旧成功/失败/未验证请求晚到3项通过。最终未发现阻止合并的问题。独立探针在内存中注入，未修改源码。

## 回滚与剩余验收

无数据库迁移。安全退路是关闭浏览器历史展示/写入并保留受保护查询；不得恢复共享键读取或把旧键分配给当前账号。服务端历史保留，不能通过回滚恢复已清理的未知归属浏览器数据。

远端CI以PR当前提交结果为准。真实双标签页并发、Chrome86与医院旧键部署范围尚未现场验收；本机Chromium/Mock及jsdom不能替代医院验收。P1进入正式部署前仍按#350核对适用项目。
