# CI 构建职责与发布产物复用

关联：#362；风险 P1（发布链路）。

普通 Java / Vue 业务代码 PR 由 `Quality gate` 执行相应测试、类型检查和前端构建。发布依赖、资源、Vite 构建配置、Windows 部署资产、文档或打包脚本变化，额外触发 `Windows release package`。标签与手动运行仍执行完整离线包构建。

`Monitoring Validation` 只验证 Prometheus、Alertmanager、Grafana 配置；`Backend Normalization Check` 和旧 `Standalone JAR Release` 保留手动入口，不再在每个业务 PR 重复编译应用。

正式标签构建在 Windows 打包工作流内仅编译一次后端和前端。经身份校验并嵌入前端的 `BACKEND_JAR` 同时用于离线 ZIP 和独立 JAR；独立 JAR 只调整默认端口为 8002。发布任务下载两种产物，校验独立 JAR 的 SHA-256，再上传到同一 Release。PR 不发布 Release；写权限仍只授予标签的发布任务。

这不消除质量门禁与发布验收之间的所有重复：修改发布资产时，两类检查仍独立运行。普通业务 PR 不构建完整离线包，发布相关 PR 则保留完整验收。

## 验证

- `python -m unittest scripts.tests.test_ci_build_reuse -v`：验证业务/发布路径分流、共享 JAR、手动兼容入口及监控职责。
- `pnpm build`、`pnpm test:compat:chrome86`。
- 实际嵌入生产前端后，执行 `embed_frontend_in_jar.py verify` 和 `build_standalone_jar.py build/verify`，验证前端资源与默认端口。
- PR 的 Windows 打包构建验证离线包组装；正式标签发布仅在发行时验证，不为测试创建版本标签。

## 回滚

独立撤销本批提交即可恢复四个工作流的旧触发条件和发布职责；不修改数据库、生产配置或历史发行产物。如暂需使用旧独立 JAR 流水线，可在 Actions 手动运行其保留入口。
