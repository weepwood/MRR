# 合成扫描查询实验 · 2026-11-24

Related to #99/#350。P2 独立工具与合成基准，禁止连接现场数据库，不调整生产查询、索引、Flyway 或演示造数限制。

## 使用与防护

`scripts/benchmark_synthetic_scan.py` 只接受 PG16 二进制目录、新实验目录父路径、Java21 路径与有界数据量/批次/时间；不接受 DSN 或现有数据库。每次创建全新 PG16 集群和私有 socket，JDBC 仅监听 localhost 且使用随机测试密码；通过真实 Flyway 与 Mapper 集成测试建立 schema，确认扫描/主档均为空后才写入。每批最多10万行，启动磁盘余量为8GiB+每行4096字节，运行中保留8GiB；总时间上限7200秒。实验退出尝试停止自己的集群并写 JSON；不自动删除报告或目录，失败检查 server_stopped。非生产 Windows 部署工具。

示例（macOS 本地，路径按实际环境设置）：

```sh
python3 scripts/benchmark_synthetic_scan.py --pg-bin /opt/homebrew/opt/postgresql@16/bin \
  --lab-parent /Volumes/data/Project/MRR-worktrees \
  --java-home /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  --rows 1000000 --max-seconds 360
```

可选择 1000万或3000万，必须先检查容量、时间、权限和隔离实例；非 CI 默认大规模执行。使用现有 build-code-key-indexes.sql、inspect-query-plans.sql；第一/重复采样不等同严格冷/热操作系统缓存。固定键 pgbench prepared 1/8客户端各5秒，不代表 HTTP、真实工作负载、P95/P99或院内吞吐。

## 实测证据

本机 PostgreSQL16.16、Java21，main 479712c1 schema；100万扫描、1万合成主档，10万行/批，种子88.476秒；两个实际并发索引构建6.492秒，均 valid/ready，扫描表含索引490520576→553648128字节。相同页脚本实际两次 SAME_PAGE_VERIFIED。原始报告在实验目录 mrr-scan-lab-ouwo0eqc（本地保留，实例已停止）。

| 查询 | 首次/重复，索引前 ms | 首次/重复，索引后 ms | 首次共享块前→后 |
| --- | --- | --- | --- |
| archive_id（99条） | 3.591 / 0.106 | 0.135 / 0.049 | 10→10 |
| BAH 精确游标（51条） | 3.637 / 4.140 | 0.105 / 0.094 | 112→5 |
| SJH 精确游标（51条） | 3.605 / 4.397 | 0.040 / 1.232 | 122→5 |
| 兼容模糊COUNT | 229.644 / 76.344 | 58.280 / 42.165 | 20323→20323 |

执行时间不含规划；样本数少、数据分布简化且缓存未受控，不宣称生产提速倍率。模糊 COUNT 的共享块不减，耗时差异不归因于表达式索引。索引后固定BAH pgbench：1客户端 7090.42 TPS/0.141ms均值，8客户端47147.49 TPS/0.170ms均值，两档零失败事务。非端到端性能。

完善报告完整性、同页和pgbench失败检查后的脚本，已在 main 35bbae75 实际重复1万行冒烟：同页验证、两档零失败、正常停止，报告 mrr-scan-lab-837d8r2b。ScanServiceCacheTest 7项零失败/跳过；真实Spring/Caffeine代理、模拟Mapper，8并发冷键回源8次、8并发热键回源0次。现有代际栅栏不可移除；同键合并需独立测试空值不缓存、失效后并发回源及异常，不直接启用 sync=true 破坏现有 unless 语义。

## 未完成

实际1000万/3000万规模、真实等量副本与现场负载、冷缓存独立实验、多键/长时间/尾延迟、HTTP基准、同键并发合并。不得据此关闭 #99/#350 或发布V1.0。回滚独立revert；实验集群已停止，报告和目录保留供复查，不操作生产。

## 多键与千万级尝试（后续轮次）

新增 --multikey、--sample-seconds 1..60。固定合成SJH随机键覆盖全部生成主档，逐事务日志数量严格匹配pgbench实际成功事务，nearest-rank取P50/P95/P99；日志留在独立实验目录。初次实跑100000扫描/1000主档，数据写入2.962秒，索引构建0.127秒，扫描表及索引50659328→57016320字节，同页验证成功、实例停止（mrr-scan-lab-u6qetqo3）。

| 并发 | 样本 | TPS | P50 ms | P95 ms | P99 ms | 失败 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 65433 | 13086.24 | 0.062 | 0.125 | 0.234 | 0 |
| 8 | 230263 | 46051.58 | 0.107 | 0.315 | 0.981 | 0 |

实际1000万尝试（100000/批，--max-seconds 360，--multikey --sample-seconds 10），PG16.16独立集群 mrr-scan-lab-cpyqt5ff；报告 status=incomplete、seeded_rows=7900000、plans={}、server_stopped=true；pg_ctl status确认未运行。最后确认批次790万，下一批超时，不声称精确最终行数或千万行验收。未运行该规模索引/计划/多键测试，3000万未启动。磁盘余量444GiB，未触碰保留阈值；实验停止、目录和证据保留。下一轮按实际造数速率为1000万预留更长独立执行窗口，完成后再评估3000万；不减少真实schema/约束、不放宽生产门禁。百万级历史证据与本轮10万多键冒烟独立成立。

## 1000万第二次实测（2026-11-25）

PG16.16，独立集群 mrr-scan-lab-jp9vfwfe，原脚本900秒总预算、10万/批、--multikey --sample-seconds 15。实际10000000扫描/100000主档，写入372.8秒，COUNT确认，ANALYZE完成；扫描表及已有索引5919105024字节。实际索引构建前查询（执行时间，不含规划）：archive 0.587/0.038ms（99条，10共享块）；BAH游标4.202/1.747ms（51条，112块）；SJH游标2.022/2.010ms（51条，122块）。兼容模糊COUNT实际5秒statement_timeout，服务日志确认 canceling statement due to statement timeout；脚本原先中断，报告incomplete、server_stopped=true，pg_ctl status确认停止。索引构建、同页及多键在这个规模未执行，3000万未启动。

后续工具仅允许可选兼容COUNT的明确语句超时记录为 statement_timeout（5秒），保留失败日志，继续独立精确查询及索引；其他错误立即失败，报告 complete_with_legacy_timeouts 与 complete 区别。不得据此声称千万级完整验收；需重新运行并保留超时证据。未修改生产查询、索引、语句预算或Flyway。
