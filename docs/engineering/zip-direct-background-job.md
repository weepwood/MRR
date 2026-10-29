# 内部ZIP直接使用现有后台任务

关联#171，风险P1。负责人Codex；分支`codex/171-zip-direct-job`；worktree `D:/Project/CodexWorkTress/zip-direct-job/MRR`；状态待合并。基线main `2b03f9c8`。

## 已确认调用链和最小范围

ArchiveExportController.planZip始终为有效请求返回BACKEND_JOB，但useArchiveImages.handleDownload先调用该完整病案规划，再通过useArchiveExportJob创建任务。规划、创建、Worker各准备整案元数据一次。

删除内部ZIP的冗余规划与不可达流式分支，直接复用exportJob.start。一次内部ZIP减少一次HTTP往返与规划阶段的整案准备。权限、无影像、loading恢复、任务进度/取消/后续下载机制仍沿用原实现；外部票据分支、PDF、旧后端API不变。参数/编号/失效影像校验仍在任务创建与准备链路执行，不以EXISTS捷径绕过。

不删除JSZip依赖或兼容API，不改数据库、审计、授权、医疗原文件和旧浏览器能力，不新增Store/API或任务框架。此批不宣称完成创建阶段轻量存在性校验/Worker只准备一次，剩余两次服务端准备仍由#171后续批次处理。

## 验证

先写useArchiveZipDownload行为测试：旧实现5项中1项失败，证明内部ZIP仍多请求一次plan；其余权限、无影像、创建失败和外部票据分支通过。删除冗余请求后，新增测试与既有影像并发/后台任务测试通过；再补外部会话失效与缺少下载授权的拒绝路径。

变更文件ESLint、vue-tsc以及测试资产/版本基线/工程文档/架构边界和git diff --check通过。使用与主工作区相同清单的现有依赖junction，直接运行已安装Node入口，未安装/升级共享依赖。本地按需运行定向测试，不运行全量套件或普通PR E2E。

最终定向命令：`node node_modules/vitest/vitest.mjs run src/views/statistics/archive/composables/__tests__/useArchiveZipDownload.test.ts src/views/statistics/archive/composables/__tests__/useArchiveImagesConcurrency.test.ts src/views/statistics/archive/composables/__tests__/useArchiveExportJob.test.ts`，三文件13项通过。

## 独立审查

只读代理zip_job_review使用新上下文直接读Issue171、规范、diff、新测试及后端/任务调用链，确认未丢失任务创建端的权限、编号歧义及空案校验，最终无阻塞问题。独立执行13项定向测试及git diff --check均通过；将实际生产diff对PR481干净worktree执行git apply --check通过。未修改文件或运行全量测试。

## 回滚与剩余风险

独立git revert本批并发布旧前端可恢复原先规划→创建流程，不删除任务或导出文件，无数据迁移。会重新增加一次规划请求与整案准备。

真实Windows Server、Chrome86和混合NAS/OSS/Nginx病案导出仍待#350现场验收；本地Mock不替代真实存储。与尚未合并#481修改同文件的不同职责区域，合并时仍应核对组合结果。

前端需与当前具有后台任务接口、ZIP规划恒为BACKEND_JOB的受支持后端配套部署；不再与缺少该接口或仍返回BACKEND_STREAM的历史服务器协商降级。保留后端旧API供旧客户端使用，不将两种兼容方向混为一谈。
