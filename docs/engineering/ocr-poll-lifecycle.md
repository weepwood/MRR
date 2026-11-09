# OCR 只读进度轮询生命周期

关联 #531，风险 P2，基线 main d66235c3。分支 codex/ocr-poll-lifecycle，专属 worktree D:/Project/CodexWorkTress/ocr-poll-lifecycle/MRR，负责人当前 Codex，独立代理审查；最终状态与命令结果见 PR。

调查发现 useOcrJob 的 generation 能隔离旧请求，却不能阻止已销毁实例再次调用 follow；follow 只清空 job，旧 error 在新请求返回前仍显示。原定向测试仅验证过期响应和终态停止。

最小修复在原 composable 内增加 disposed 守护，销毁时先标记再 stop，follow/poll/可见性回调不再重启销毁实例；新 follow 立即清空旧错误。沿用既有 generation、计时器、visibilitychange 和 API，没有抽象通用轮询平台。stop 保持原语义，未主动清空最终 job 或变更服务端任务。

新增 4 项回归，旧实现 3 项行为失败、隐藏暂停恢复 1 项通过。修复后加原测试共 5 项通过：销毁后调用、旧请求与销毁交错、新跟踪错误清理、隐藏暂停恢复、终态停止和旧响应隔离。测试全部使用合成任务数据。

验证执行定向 Vitest、TypeScript、变更 ESLint、生产 build、Chrome86 静态和仓库治理，完整前端验证由当前 SHA 的 CI 执行。真实终端仍由 #350 现场验收，静态兼容不能替代实机。

本批只改变浏览器只读进度，不改变 OCR 执行/分类/自动应用、权限、审计、API、数据库或默认开关，遵守 ADR-0004。没有患者数据或真实后端连接；没有全仓性能提升的声明。项目继续保持模块化单体；后续复杂迁移页面和服务的职责拆分需另行定界，不能因一个轮询问题重写状态框架。

回滚为独立 revert 本批提交，重建前端，无迁移或医疗数据恢复。既有整合候选 #528 不包含本批代码，不应把该候选的旧 CI 当作本批验证。
