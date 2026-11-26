# MRR 后端治理任务池

2026-11-26 维护者授权：从 5926edc8 起优先后端可靠性、安全和实际性能；前端仅必要的兼容/安全联动。沿用 Java21、Spring Boot、MyBatis、PG16、Nginx 与本地/NAS/OSS；不启动发布流程。所有测试仅隔离合成数据，历史迁移不可改。

风险：P1 医疗数据、权限、事务、审计；P2 性能/可靠性。状态“源码证据”不是确认可利用漏洞，“待测”不是已修复。当前为前四批定向审查，并非全量代码或完整渗透测试。

| ID | 风险 | 证据 / 任务 | 状态与验收条件 |
| --- | --- | --- | --- |
| AUD-01 | P1 | 53 Controller目录文件、393 Java生产文件/44763行（基线），ApiAccessPolicy/LoginInterceptor/AuthorizationInterceptor/WebConfig | 第二批生成273条HTTP路径/方法声明矩阵；第四批扩展为276条，完成质量任务新增三路由及历史错误/日志导出定向审查；后续逐路由核对字段、资源、读写、事务、异常、限流；覆盖外部调阅、开发者模式、影像与导出 |
| SEC-01 | P1 | DataQualityController.repairPreview 原 system:read；findArchiveCandidates 返回姓名与科室；Manifest 双读权限 | 已修复双权限；测试匿名、单权限、双权限与继承；旧客户端仅系统权限会403，现有错误处理保留；不扩大其他端点权限 |
| SEC-02 | P1 | 数据质量 issues/detail 包含 BAH/SJH 与历史样本，监控页面也消费 | 第三批质量样本/详情双读权限与前端按权限清理；其他医疗字段、角色矩阵及资源归属仍待审查 |
| SEC-03 | P1 | LoginInterceptor.applyDeveloperPermissionBypass 有显式全权限模式；Nginx proxy.inc 覆盖转发头，IpUtil只信任回环代理 | 第二批修复外部会话撤销竞态及MyBatis同会话票据消费缓存；PG16验证nonce/过期/撤销；第三批独立连接票据竞争及回滚、HTTP降权撤销通过；第四批撤销和当前用户/权限单SQL快照、真实HTTP降权撤销通过；剩余代理、传输撤销及真实资源全矩阵待测 |
| SEC-04 | P1 | DatabaseBrowseService、NginxBrowserService、ImageContentController、存储来源实现 | 动态SQL允许列表、文件路径/符号链接/Windows路径、SSRF、Range、敏感信息与审计；Mapper XML无直接 ${...} 不等于全项目无注入 |
| SEC-05 | P1 | Maven、OCR Python 与部署依赖 | 生成实际依赖树/SBOM，核对最新公告和可达性；本轮没有全量CVE扫描 |
| TX-01 | P1 | SystemSettingServiceImpl 在提交前向共享内存发布配置 | 已修复提交后失效而非重放值；真实Spring代理/PG16独立线程回滚和提交通过；待长事务TTL跨越、跨缓存交错及并发修改矩阵 |
| TX-02 | P1 | DataQualityRepairOrderService、ArchiveManifest、OCR分类、迁移、导出 | 第二批复验Manifest、工单、导出恢复、OCR分类、迁移数据库状态；规则样本/汇总原子化；第三批质量终态条件更新、写锁超时回滚；第四批PG16复验Manifest/工单/OCR/导出/迁移及质量任务互斥取消恢复；全状态幂等、锁顺序、死锁矩阵仍待测 |
| SQL-01 | P2 | DataQualityServiceImpl.getIssues 最新批次与样本两次SELECT | 已合并单SQL，排序/上限/空结果不变；真实PG16与一万行短基准通过，详细报告见本批 |
| SQL-02 | P2 | ScanMapper旧BAH/BRXH/Folder无SQL LIMIT；旧OFFSET与同步COUNT | 第二批BAH/BRXH/Folder SQL LIMIT及3秒预算，普通旧查询超1000明确413，迁移超2000拒绝；第三批10000条深页/游标/COUNT计划对照；实际分布及更大规模负载待测 |
| SQL-03 | P2 | 已有三千万行精确BAH/SJH游标表达式索引实验，旧模糊COUNT两次5秒超时；GIN写入WAL额外成本 | 复用历史证据；按实际慢查询与计划评估选择性、深页、并发写入，不重复造数，不直接部署GIN |
| SQL-04 | P2 | DataQualityServiceImpl 18项规则，每条计数与样本10秒预算，样本逐条INSERT，手动同步执行 | 第二批原子多值INSERT+汇总CTE，失败恢复PG16通过；第三批runId、最新成功身份、计数/采样/规则写入剩余预算及终态保护；第四批新增持久化活动槽、异步提交/状态/协作式取消、过期恢复；硬全局预算、前端切换及真实进程强杀恢复待验 |
| PERF-01 | P2 | LoginInterceptor 每有效请求撤销/用户读；Hikari默认20；日志 CallerRunsPolicy；Caffeine按条目容量 | 第三批认证字段最小化，HTTP撤销/用户SQL各4001，4连接活跃峰4等待峰7；第四批实际认证SQL合并4001次（原两类各4001）；未证明吞吐提速；JFR与受控长时对照待测；不得弱化撤销/审计；缓存权重和热点回源专项 |
| PERF-02 | P2 | 本轮小规模SQL对照；历史精确游标/GIN实验；真实PG超时/锁失败已复验 | 第三批真实HTTP短时混合4000/4000，最终P95 7.486ms/P99 10.607ms，未证明提速；第四批完整Spring应用90秒级混合HTTP、真实本地影像与审计落库；小时级负载、JFR、真实Nginx/NAS/OSS和回归阈值待测；仅本地合成数据 |

## 下一批建议

按优先级：SEC-02/03 真实角色和外部调阅边界，SQL-02 旧查询调用者与预算，TX-02 业务锁与幂等，SQL-04 数据质量运行模型；PERF-01/02 逐步增加实际端到端证据。优先对明确风险编写RED用例，再最小修复、定向验证、独立提交。P1 本地提案记录，恢复正式发布时补原有Issue与治理门禁。

第二批详见 [治理报告](governance-batch2.md) 与 [API声明矩阵](api-access-matrix.tsv)。下一步优先实际权限字段审查（SEC-02/04）、独立会话/票据并发、SQL深页和质量检查运行状态、HTTP资源实验。

第三批见 [治理报告](governance-batch3.md)。优先补齐持久化异步取消、全阶段预算与恢复，认证SQL合并严格对照、真实审计存储链路长时混合实验，以及字段与资源归属剩余矩阵。

第四批见 [治理报告](governance-batch4.md)。下一步优先补齐硬预算/取消与故障恢复、完整角色资源字段矩阵、小时级受控性能对照和锁顺序/死锁矩阵；按证据收口，不重复已通过大型造数。
