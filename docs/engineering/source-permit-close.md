# 影像来源流的关闭所有权

关联 #503，Related to #171；风险 P1。负责人 Codex；分支 `codex/503-source-permit-close`；worktree `D:/Project/CodexWorkTress/source-permit-close/MRR`；状态进行中。基线 `origin/main / 2b03f9c8`。

## 调查与最小方案

Local/NAS/HTTP/Nginx/OSS 适配器均使用 SourcePermitGuard；读取取得并发名额，流关闭时归还，元数据 call 在 finally 归还。返回流的 close 在调用底层 close 后使用普通 boolean released；它没有表达关闭线程的独占所有权。重复 close 可能在第一次底层关闭尚未完成时归还名额，普通 if/赋值也不是原子的。未声称发生现场故障。

1. 先补确定性交错测试：首个底层 close 由 latch 阻塞，第二个 close 不能让来源并发1变成可用；完成或失败后只能恢复一个名额。覆盖重复 close、异常关闭、supplier 失败/空流、call 失败和中断。
2. 只在现有返回流内用 AtomicBoolean 认领关闭权；只有成功认领的线程执行底层 close，在 finally 归还一次。其余 close 返回，不增加包装层、限流器、线程池或客户端。
3. 定向存储测试、独立审查、当前提交 CI。无 SQL、迁移、API、权限、配置、路径或医疗数据修改。

非目标：浏览器整份 Blob 下载、启动恢复全量查询、HTTP 正文读取截止时间、NAS 底层中断及现场吞吐。等待获取名额仍由现有 acquire-timeout 控制，不能将这项修复说成底层 close 可被强制超时。

## 验证记录

- RED：`mvnw.cmd -o -B -ntp -Dtest=SourcePermitGuardTest -Dlocal.config.exclude=true test`，7项中2项断言失败、1项重复关闭异常。证明关闭尚未完成却可再次获取名额、重复底层close以及重复关闭再次抛异常。
- GREEN：`mvnw.cmd -o -B -ntp -Dtest=SourcePermitGuardTest,NginxArchiveImageSourceTest,HttpArchiveImageSourceTest,NasArchiveImageSourceTest,OssArchiveImageSourceTest,ResolvedImageStorageFallbackTest,MixedArchiveImageSourceFallbackTest -Dlocal.config.exclude=true test`，7类19项通过，失败/错误/跳过均0。新增交错用latch安排，不依赖随机竞争；确认释放后只有一个名额。
- 架构边界、工程文档、发布基线、测试资产、git diff --check通过。本地只运行定向测试，无SQL改动；当前提交CI待记录。合成流/本地回环HTTP不代表真实NAS/OSS验收。

## 独立审查

source_permit_review 使用新上下文直接读取 #503、架构/治理/ADR、diff、测试、完整适配器与内容/导出调用链及RED/GREEN日志，未发现阻塞问题。确认关闭所有权、finally释放、既有失败路径和权限/凭据边界保持；未重复Maven或现场验收。中断用例验证预置中断标志的保留和未多释放，不包含排队后由其他线程中断的情形；acquire实现本批未改。

## 回滚

暂停新读取/导出，等活动请求与任务结束，独立 revert 本批并重建包；无迁移、医疗数据恢复或文件删除。保留所有影像、任务记录与成功输出。回滚会恢复旧关闭竞态，应优先前向修复。
