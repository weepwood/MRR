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
