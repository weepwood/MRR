# MRR 后端治理任务池

2026-11-26 维护者授权：从 5926edc8 起优先后端可靠性、安全和实际性能；前端仅必要的兼容/安全联动。沿用 Java21、Spring Boot、MyBatis、PG16、Nginx 与本地/NAS/OSS；不启动发布流程。所有测试仅隔离合成数据，历史迁移不可改。

风险：P1 医疗数据、权限、事务、审计；P2 性能/可靠性。状态“源码证据”不是确认可利用漏洞，“待测”不是已修复。当前仅第一批定向审查，并非全量代码或完整渗透测试。

| ID | 风险 | 证据 / 任务 | 状态与验收条件 |
| --- | --- | --- | --- |
| AUD-01 | P1 | 53 Controller目录文件、393 Java生产文件/44763行（基线），ApiAccessPolicy/LoginInterceptor/AuthorizationInterceptor/WebConfig | 第一批源码调查与访问策略测试；后续逐路由记录 HTTP 方法、权限、数据分类、所属资源、读写、事务、异常、限流；覆盖外部调阅、开发者模式、影像与导出 |
| SEC-01 | P1 | DataQualityController.repairPreview 原 system:read；findArchiveCandidates 返回姓名与科室；Manifest 双读权限 | 已修复双权限；测试匿名、单权限、双权限与继承；旧客户端仅系统权限会403，现有错误处理保留；不扩大其他端点权限 |
| SEC-02 | P1 | 数据质量 issues/detail 包含 BAH/SJH 与历史样本，监控页面也消费 | 待核对真实角色授权、字段最小化、缓存及前端分区权限；不擅自把监控角色判定为违规 |
| SEC-03 | P1 | LoginInterceptor.applyDeveloperPermissionBypass 有显式全权限模式；Nginx proxy.inc 覆盖转发头，IpUtil只信任回环代理 | 待系统测试启动默认关闭、受信来源、撤销、代理绕过、外部签名/重放、真实资源所有权；保留原安全语义 |
| SEC-04 | P1 | DatabaseBrowseService、NginxBrowserService、ImageContentController、存储来源实现 | 动态SQL允许列表、文件路径/符号链接/Windows路径、SSRF、Range、敏感信息与审计；Mapper XML无直接 ${...} 不等于全项目无注入 |
| SEC-05 | P1 | Maven、OCR Python 与部署依赖 | 生成实际依赖树/SBOM，核对最新公告和可达性；本轮没有全量CVE扫描 |
| TX-01 | P1 | SystemSettingServiceImpl 在提交前向共享内存发布配置 | 已修复提交后失效而非重放值；真实Spring代理/PG16独立线程回滚和提交通过；待长事务TTL跨越、跨缓存交错及并发修改矩阵 |
| TX-02 | P1 | DataQualityRepairOrderService、ArchiveManifest、OCR分类、迁移、导出 | 已复验工单实际锁冲突和审计失败回滚；待其它事务、幂等、锁顺序、死锁恢复与中途失败 |
| SQL-01 | P2 | DataQualityServiceImpl.getIssues 最新批次与样本两次SELECT | 已合并单SQL，排序/上限/空结果不变；真实PG16与一万行短基准通过，详细报告见本批 |
| SQL-02 | P2 | ScanMapper旧BAH/BRXH/Folder无SQL LIMIT；旧OFFSET与同步COUNT | 先查所有调用者、旧客户端契约、行数分布；保留精确总数语义，候选有界/游标；避免静默截断 |
| SQL-03 | P2 | 已有三千万行精确BAH/SJH游标表达式索引实验，旧模糊COUNT两次5秒超时；GIN写入WAL额外成本 | 复用历史证据；按实际慢查询与计划评估选择性、深页、并发写入，不重复造数，不直接部署GIN |
| SQL-04 | P2 | DataQualityServiceImpl 18项规则，每条计数与样本10秒预算，样本逐条INSERT，手动同步执行 | 单条预算不等于批次预算；研究批写、异步运行身份、最近成功与失败/部分结果，避免不受限查询 |
| PERF-01 | P2 | LoginInterceptor 每有效请求撤销/用户读；Hikari默认20；日志 CallerRunsPolicy；Caffeine按条目容量 | 待JFR/连接池/SQL次数与尾延迟实测；不得弱化撤销/审计；缓存权重和热点回源专项 |
| PERF-02 | P2 | 本轮小规模SQL对照；历史精确游标/GIN实验；真实PG超时/锁失败已复验 | 待实际HTTP混合负载、前后台竞争、长时间与资源释放、P95/P99及回归阈值；仅本地合成数据 |

## 下一批建议

按优先级：SEC-02/03 真实角色和外部调阅边界，SQL-02 旧查询调用者与预算，TX-02 业务锁与幂等，SQL-04 数据质量运行模型；PERF-01/02 逐步增加实际端到端证据。优先对明确风险编写RED用例，再最小修复、定向验证、独立提交。P1 本地提案记录，恢复正式发布时补原有Issue与治理门禁。
