# 后台导出下载的页面生命周期

## 任务与边界

关联 #524，风险 P1。基于 main `d66235c3`，分支 `codex/524-download-lifecycle`，专属 worktree `D:/Project/CodexWorkTress/download-lifecycle/MRR`。当前 Codex 实现，独立代理审查；提交及 CI 证据写入 PR。状态以 Issue/PR 为准。

自动下载链路为 `useArchiveExportJob.poll → downloadExportJobToBrowser → Blob 保存`；手动重下为 `download → downloadExportJobWithResume → 文件选择器 / Range / Blob`。原先任务失效只清空页面任务，已经发出的下载仍可保存，旧通知和 finally 也可能覆盖新下载状态。

本批只修复共享 ZIP/PDF 下载的有效期和写入恢复。不变更请求鉴权、后端审计、API、数据库、任务协议或部署，不增加 Store、下载授权或持久化组件。PDF 规划与合成的独立修复由 #523 / #525 跟踪；本批不修改其分支。

## 实现职责

- `useArchiveExportJob` 继续持有任务 id 与 generation；start、discard、dismiss、离页使旧下载失效，旧 catch/finally 不再改变新下载状态。
- 自动与手动下载共用 downloading 锁；同一代的并发 SUCCESS 轮询最多触发一次自动下载，失败后保留现有任务供手动重试。
- 两个下载工具函数增加可选 `isCurrent` 参数，旧调用保持兼容。当前任务包装层总是传入有效期检查，无第二套任务状态机或请求实例。
- Blob 在请求前及取得结果后检查有效期，再校验并保存。文件选择器、getFile、前缀读取、createWritable、每个 Range、write、truncate 与最终 close 前后均检查；迟到 createWritable 先保存返回流，再进入失效清理，防止遗失流引用。
- 失效使用 AbortError，普通网络错误在失效后返回也不得启动整包回退。恢复等待结束后再次检查，防止清理期间切页后仍回退。

## 文件提交与恢复

文件流尚未开始提交时，失效优先调用可用的 abort 放弃暂存；不支持或 abort 失败时沿用原有 truncate(originalSize) + close 恢复路径。恢复失败明确报错，不以普通下载成功掩盖所选文件状态。

完整文件 close 或恢复原长度的 close 一旦开始，若拒绝，即使随后的 abort 成功，也不能据此认定提交已恢复；禁止整包回退并报告恢复失败。不对已经开始提交的同一流重复 truncate/close。

标准文件流在 close 前暂存修改，keepExistingData 保留原内容；相关依据见 [WHATWG File System createWritable](https://fs.spec.whatwg.org/#api-filesystemfilehandle-createwritable)。这不能替代目标浏览器和文件系统验收。

保证范围：失效后不新触发 Blob 保存、不继续 Range 写入或开始完整文件提交、不显示旧成功/错误提示、不清除新请求 loading。已经交给浏览器的下载、已开始的 close、已开始的网络请求不承诺撤回；发生在途写入时仍需等待其返回后清理。原有前缀检查最多 64 KiB，不是全文件 SHA 校验。

## 验收与证据

首批 14 项新回归在旧实现均因行为断言失败：自动下载期间 discard/dismiss/dispose/start、旧手动下载与新 loading、旧错误、重复点击，以及迟到 picker、前缀、Range、写入和 truncate。独立审查进一步发现提交失败后被普通下载掩盖的边界，已修复并增加回归；其中恢复 close 失败用例先 RED 再 GREEN。

最终新增 24 项与原有 7 项共 31 项定向测试，范围为：

```text
src/views/statistics/archive/composables/__tests__/archive-job-download-lifecycle.test.ts
src/views/statistics/archive/composables/__tests__/useArchiveExportJob.test.ts
src/views/statistics/archive/utils/__tests__/export-download-lifecycle.test.ts
src/views/statistics/archive/utils/__tests__/resumable-export-download.test.ts
```

同时执行 TypeScript、变更 ESLint、生产 build、Chrome86 生产资源静态检查和仓库治理检查；最终命令结果、独立审查与当前 SHA CI 记录在 PR。测试使用合成 Blob、文件流及 deferred 请求，不写患者影像或用户实际文件，不启动后端/数据库。真实 Chrome86、医院 HTTP/Nginx、磁盘不足、文件占用及原生保存取消由现场负责人执行 #350，静态兼容检查不能标记实机通过。

## 与预算及其他待合并 PR 衔接

#517 尚未进入本批基线。合并时必须同时保留其 requireArchiveExportSize、128 MiB 限额、ArchiveExportBudgetError、请求取消及 HTTP 错误禁止整包回退的规则；catch 中先完成写入清理和有效期检查，再执行预算/取消/HTTP 拒绝规则，不得使失效变成 fallback。保留 #517 的“已生成但下载未完成”提示语义和全部测试。

#520 选页 ZIP、#525 PDF 调用既有 discard 即可受本批保护；不得把几个导出实例合并成共享单例。#522 打印逻辑不受本批影响。候选版本仍须对这些变更组合验证，单批测试通过不代表整合或医院验收通过。

## 回滚

独立 revert 本批提交并重建前端，保持现有安全发布基线。无迁移、医疗数据或原始影像修改。回滚会恢复迟到下载风险，优先前向修复。下载清理无法覆盖浏览器崩溃或已开始提交后的物理恢复，现场需核对目标文件；不承诺删除已经交给浏览器的文件。
