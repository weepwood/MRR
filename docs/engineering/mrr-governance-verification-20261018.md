# MRR 数据治理实施与验证记录（2026-10-18）

风险等级P1，关联 #349；医院副本与上线验收跟踪 #350。实现基于最新main `98d4e4e210f88aa5b8ebde4418d3df3e627b6c05`，保留该版本OCR离线交付及审计分类入口。原工作区的合成数据未提交文件未修改。代码在独立工作树实现，按九个主题分批审查，不直接修改main。

## 已实现范围

| 批次分支（codex/） | 结果与边界 |
|---|---|
| record-code-correctness | 编号只trim、保留原始前导零；删除递归补零；兼容查询拒绝歧义和跨病案扩大。高位BAH仍需SJH。 |
| scan-archive-link-refresh | 新前向触发器迁移重新验证编号变更后的关联，明确冲突时清空错误关联；不批量重写历史数据。 |
| persistent-token-revocation | PostgreSQL持久化jti摘要，重启不恢复已撤销Token；存储失败拒绝认证，登出失败503。 |
| private-diagnostics-logs | 结构化脱敏、独立HMAC摘要、安全历史读取、诊断隐藏存储细节；旧OSS影像转内容网关，受保护影像private,no-store。 |
| runtime-log-journal | 脱敏文件续传、月分区、原子投影收据、开关切换去重、受控补录、游标检索、积压/容量/缺口展示；默认关闭。 |
| query-diagnostics | 安全原因码与Request ID，管理员显式分阶段诊断，无主档也可检查扫描，权限拒绝不透露存在性；前端请求版本隔离。 |
| data-quality-performance | 统计快照/未知状态、有界质量检查、完整比较键与ID游标、独立并发索引、只读计划与实例绑定检查点。 |
| ui-consistency | 日期/指标/暗色主题统一，减少动态效果支持，静态入口清单与首批页面回归。 |
| windows-governance-operations | PS5.1取证、密钥初始化、受控备份和隔离空库恢复入口、真实NTFS DACL测试、现场手册与正确Git版本元数据。 |

四个新增前向迁移：`20261018100000`关联触发器、`20261018101000`撤销、`20261018101500`错误投影收据、`20261018102000`运行日志；main历史Flyway未修改。月分区由采集器按需创建，创建失败不确认断点。独立大索引不放入Flyway事务。原部署prod/secrets模板不修改，新参数放在独立 `.properties.example`，不绕过敏感配置门禁。

## 实际验证

本地Windows、Java21、Maven3.9.9、PostgreSQL16 Testcontainers；Docker只用于本地测试。正式架构仍是Windows/Nginx/SpringBoot/PostgreSQL。前端复用现有已安装依赖，未更新依赖或锁文件；本地pnpm因minimumReleaseAge元数据提示使用 `--config.verifyDepsBeforeRun=false`，CI仍应执行frozen-lockfile安装。

| 命令（模块内运行） | 实际结果 | 本地证据文件 |
|---|---|---|
| `mvn -B -ntp clean verify` | 799项单元/切片 + 74项PostgreSQL集成，失败0、错误0、跳过0，BUILD SUCCESS | backend-repo/governance-clean-final.log |
| `pnpm --config.verifyDepsBeforeRun=false lint:tsc` | 通过；生产build也包含vue-tsc | frontend-fantastic-admin/governance-tsc-main.log |
| `pnpm --config.verifyDepsBeforeRun=false test:run` | 60个文件、347项通过 | frontend-fantastic-admin/governance-vitest-main.log |
| `pnpm --config.verifyDepsBeforeRun=false build` | 生产构建通过 | frontend-fantastic-admin/governance-build-final.log |
| `pnpm --config.verifyDepsBeforeRun=false test:compat:chrome86` | 静态产物检查通过，仍提示依赖中color-mix等降级项；不等于Chrome86实机验证 | frontend-fantastic-admin/governance-compat-final.log |
| `pnpm --config.verifyDepsBeforeRun=false test:e2e --workers=2` | 37项通过（含预热），Chromium+MockAPI | frontend-fantastic-admin/governance-e2e-full.log |
| `python -m unittest discover -s scripts/tests -p 'test_*.py' -v` | 79项通过，含WindowsPowerShell5.1真实NTFS DACL边界 | governance-tools-final.log |
| `python scripts/test_inventory.py --fail-on-focused` | 245个测试文件，未发现only或Disabled | governance-tools-final.log |
| `python scripts/release_baseline.py validate` | v0.8.0基线通过 | governance-tools-final.log |

完整后端验证设置 `JAVA_HOME` 为Java21，`MRR_TEST_PYTHON` 指向本地Python；合入最新main后OCR真实子进程测试要求Python，首次未设置产生4项失败，补齐环境后完整重跑通过，没有跳过OCR断言。运行PS5.1测试清理了继承自PowerShell7的PSModulePath后调用系统PS5.1模块目录。

本地日志、测试报告及合成截图不提交Git。运行日志明暗主题截图位于前端test-results目录；日志中的script合成文本按文本显示，未执行。提交重排只整理未公开历史，未改变已验证的源代码内容。

## 失败路径与独立审查

新增测试先复现了短编号/前导零语义、关联刷新、日志隐私、请求竞态、错误聚合历史时间回退、未受保护备份文件等问题，再修正实现。最终补充了以下有针对性的证据：

- 旧影像公共缓存回归先失败，修复后private,no-store；旧OSS入口不再生成签名存储URL。
- 同一日志事件重复编码身份不稳定先失败，修复后fallback→journal→fallback→journal每个物理事件只累计一次，明细和错误编号保持一致。
- 真实PostgreSQL聚合SQL失败时收据也回滚；重试成功一次，再重放返回0。旧格式补录保留明细但不重新累计不可辨识的历史错误。
- 数据库失败不推进文件断点，重启后只入库一次；轮转旧私有文件恢复、未确认来源消失持久化缺口、同时间事件游标不丢行。
- 错误队列首次失败重排剩余批次并停止本轮；销毁方法不发起数据库I/O，避免逐条连接等待拖长Windows停服。
- 普通INFO/DEBUG/TRACE不虚构ERR编号，数据库缺失error_id为NULL；WARN/ERROR共享稳定身份和原始时间。
- 真实PG16验证检查点绑定不同实例，即使数据库同名也不同；并发索引正常建立/删除和无效同名索引拒绝。
- 真实NTFS拒绝Everyone显式授权、不安全密钥文件及已有partial；写入备份前和之后都校验DACL。

独立审查使用不同上下文直接读取架构、Issue、diff与测试；先后发现并促成关联冲突、日志拼接泄露、历史聚合时间、开关重复计数、公共缓存、关闭阻塞等修复。末轮复核未发现剩余代码级阻断项。该结论不能替代医院验收与PR CI。

## 启用与回退

新日志采集及接口默认关闭；副本容量/P95和恢复演练通过后设置 `app.runtime-logs.enabled=true`。关闭仍产生日志文件并保留原错误中心；共享投影收据必须与所有可重放日志同寿命，不能随错误组清理。旧格式历史日志只入明细，不重建错误次数。

深入诊断可设置 `app.operations.query-diagnostics-enabled=false` 单独关闭。维护/降级模式仅放行精确的只读诊断路由。敏感响应不缓存，普通401/403不携带病案存在信息。

数据库仅前向修复；新增表及医疗数据保留；并发索引用独立DROP CONCURRENTLY撤销。不得回退至内存撤销、泄露日志或歧义合并病案。备份与数据修复的详细边界见 `mrr-data-operations-runbook.md`。

## 待医院验收

#350跟踪：现场空结果真正根因、千万/三千万量级计划与P50/P95/P99、额外采集P95≤10%、持续容量与备份预算、WindowsServer/Nginx/真实存储、真实Chrome86、磁盘满与完整备份恢复、全历史数据质量扫描及其余页面逐页视觉验收。当前没有这些现场证据。

本地数据库事务与轮转测试不等于真实磁盘满；静态CSS检查不等于旧浏览器实机；NTFS与脚本验证不等于已成功恢复医院备份。现有备份入口未在实际Windows PostgreSQL客户端完成完整恢复。原有OSS浏览签名链接生命周期仍待核查；当前只收口主影像入口。历史数据库日志原文未批量清除，读取脱敏不代表存量已经清理。

这些PR按依赖顺序保持小范围diff；除首批以main为base外，后续草稿暂以上一批为base。仓库远端CI只监听main，后续批次在前置合并后需转向main并通过正式governance-gate及相应CI，不能直接按本地组合验证宣布可上线。
