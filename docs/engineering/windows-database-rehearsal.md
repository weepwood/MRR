# Windows 数据库恢复自动演练

关联 #362；风险 P1（备份恢复验证）。执行器只对临时新建的 PostgreSQL 集群操作，不能传入生产连接、已有数据目录或服务名。

## 执行

需要 Windows PowerShell 5.1、Java 21、PostgreSQL 16 的 Windows 二进制目录和已打包的 MRR JAR：

```powershell
powershell.exe -NoProfile -NonInteractive -File scripts/test_windows_database_recovery.ps1 `
  -PostgresBin C:\test-runtime\pgsql\bin `
  -Jar backend-repo\target\imgapi-0.8.0.jar
```

执行器建立具有私有 ACL 的随机临时目录；原生 initdb / pg_ctl 启动仅监听回环地址、随机端口、SCRAM 认证的新集群。隔离应用环境后，实际启动 JAR 并运行全部 Flyway 迁移；停止应用写进程后插入一条虚构患者夹具。复用正式 `deploy/windows/database-recovery.ps1` 完成整库 Backup、Verify 和恢复至新建空副本。恢复后核对患者内容、行数及成功迁移历史数，并确认拒绝覆盖备份、未明确指定隔离副本和向非空数据库再次恢复。

finally 停止自建应用和集群，恢复原进程 PG 环境变量及 PATH，验证绝对路径位于此次随机临时目录后清理。集群无法停止时保留目录，不删除仍运行的数据文件。备份、口令、应用输出和数据库内容不上传 Artifact。

`scripts/test_windows_recovery_cleanup.ps1 -PostgresBin <目录>` 另行提取执行器实际 finally 块，用原生 pg_ctl 对不存在的随机数据目录触发停止失败，验证错误传播、PG 环境/PATH 仍恢复且目录保留。该故障测试同样在 CI 的 PowerShell 5.1 下执行。

CI `Windows PostgreSQL recovery rehearsal` 使用相同执行器。PostgreSQL 16.15-5 的 ZIP 取自 [EDB 官方二进制下载页](https://www.enterprisedb.com/download-postgresql-binaries) 对应的 [固定下载地址](https://get.enterprisedb.com/postgresql/postgresql-16.15-5-windows-x64-binaries.zip)，SHA-256 固定为 `43bb45f173a6f08cf1d29a97a6d8deb119e8e8093a24c00d2d1001a0ccaa8281`。该值为本次通过官方 HTTPS 下载后计算的复现校验值，不声称有独立发布的上游签名证明。更新运行时时需人工核对来源、校验值并重跑演练。

## 验收边界与回滚

这是 Windows 原生工具、真实 PostgreSQL 16 和当前 MRR 迁移的合成数据恢复证明。它不证明医院备份介质、生产体量 RTO/RPO、NAS/OSS 文件一致性和院内 Windows Server 权限已验收，相关真实环境证据继续由 #350 跟踪。

撤销本批即可移除执行器和 CI，不改变正式恢复脚本、数据库迁移或生产配置。新检查进入 main 后再按分支保护流程纳入必需检查，避免旧 PR 缺少上下文。
