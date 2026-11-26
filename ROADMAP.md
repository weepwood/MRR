# MRR 唯一活跃任务池

定位：Windows 单机、医院内网、模块化单体。当前处于本地架构简化与产品收口，非正式发布。以实际 Git 工作树为准；不擅自推送、合并、发布。历史文档和证据不再作为重复任务队列。

| 优先级 | 当前任务 | 状态 / 停止条件 |
|---|---|---|
| P1 | 医疗权限及字段 | 优先患者/病案、影像、导出、OCR、外部调阅和存储写入。复用[276条矩阵](docs/backend/api-resource-review-batch13.tsv)，54条已有定向真实响应，222条未有；同类风险参数化验收，不追求重复覆盖数量。病案主档有效关联及字段投影PG已验，实际HTTP和旧调用方待验；OCR工作者历史事实错配已修复，OCR与分类25项PG定向通过，未穷尽并发源变化；导出成功文件长度错配拒绝已修复，34项PG及45项Java定向通过，同长度损坏及现场存储仍待验；凭据改密/重置条件更新及冲突处理已修复，64Java/2PG通过，账号停用/恢复递增凭据版本已修复，82Java/4PG通过；管理员在途授权：0ea9eb4e 上真实 PostgreSQL 16 新增批准/拒绝注册竞争测试均 RED（6项中4通过2失败，单测23/23），原有测试保留；生产修复命令被工具安全检查阻断，未修复、未复验，禁止发布。创建账号/管理员重置密码等相邻路径需定向验证，非已证实缺陷；患者敏感字段独立授权需求仍待核对；导出显式异常日志及患者诊断字符串已收敛，83Java定向通过，全局异常现有脱敏编码器21Java复验通过；框架自由文本与外部账本仍待审查。 |
| P1 | 默认审计可靠性 | 明确医院审计保留、故障与容量要求；内存模式崩溃/满容量丢失风险不得隐瞒。已复核默认执行器500队列、1000含在途缓冲、失败恢复50条/5秒定时边界及现有监控规则缺项，复用38项历史定向测试（后端源码未变），补充[默认访问审计故障处置](docs/engineering/mrr-data-operations-runbook.md)；实际采集、停机丢失和现场告警阈值仍待核实。关键审计缺陷按真实要求最小修复，禁止扩张冻结引擎。 |
| P1 | 迁移及外部调阅 | 保留UUID/来源/条件更新和[独占维护](docs/backend/migration-maintenance-batch13.md)，实际OSS/NAS、外部流终态和断流待验；无多实例生产需求不做自动跨主机接管。 |
| P2 | 核心业务交付 | 病案查询/影像阅读/患者/装箱/OCR/导出/系统管理，优先已证实的操作缺陷；不新增业务平台。 |
| P2 | 代码与UI维护 | 仅有证据的重复、失效机制及过度封装；遵循设计规范和Chrome86，不因行数拆类、不改变视觉契约。患者列表请求归属复用已有逻辑，26前端/4浏览器通过；患者统计及编辑在途生命周期8个故障场景已修复，37前端/10浏览器通过；装箱明细/箱号查询及编辑删除生命周期5个故障场景已修复，36前端/4浏览器通过；影像分类/查询/外部会话生命周期4个故障场景已修复，123前端/6浏览器通过；统一设置确认快照及提交互斥4个故障场景已修复，71前端/6浏览器通过；运维维护模式与权限快照6个故障场景已修复，85前端/5浏览器通过；数据治理人工决策历史缺失响应被误作空白已复现（新增测试RED 1/5），复用现有错误状态与重试修复，数据关系5个测试文件43/43通过，定向ESLint及vue-tsc通过；诊断既有取消/重试/窄屏浏览器用例已复核，导出取消后迟到轮询恢复处理中已实际RED（1/6），复用共享导出任务代次，取消互斥及失败恢复轮询，扩展23个数据关系/影像/导出测试文件195/195通过；Chromium定向24个业务用例+1个预热25/25通过（含三种文件真实下载解析、诊断及修复工单窄屏/深色），ESLint及vue-tsc通过；推送ad9c2399后Pages冒烟45872350415在整案ZIP下载超时；延迟患者响应的真实浏览器RED复现按钮早启用、点击静默返回，双布局禁用条件与现有查询生命周期同步；任务状态GET复用限次重试（最多额外2次，不重试写操作）；60项API/生命周期测试、5项Chromium业务用例及本地完整Pages构建和静态冒烟通过，Mock静态匹配231/231。远端新SHA验收仍待；人工决策追加历史在服务端403后残留案件与决策证据已复现（RED 1/6），复用isSessionAccessDenied及现有代次清空当前上下文，普通网络失败保留已加载证据，过期拒绝不影响当前案件；扩展数据关系5文件47/47、人工决策实际Chromium 1/1（含复验拒绝及恢复）、ESLint和vue-tsc通过；客户端PDF切换选页/离页后继续取图与合成已复现（新增2项RED），复用AbortController与现有代次主动取消fetch并在解码边界停止后续合成，29项PDF/打印/取图测试及实际ZIP/PDF Chromium 1/1、ESLint、vue-tsc通过；单张已开始的解码不能即时中断，完成后释放，不宣称长期内存压力已验；其他跨会话回调、真实后端并发编辑、长期影像资源压力及其他页面待审。 |
| P1 | 版本收口 | 固定候选SHA，按依赖顺序分段审查累计diff；944ff7e3首轮全前端1164/1164、Python255通过5跳过，后端1304项发现1个历史隐私断言漂移，已精确同步测试/文档且34项定向通过；a7ded32d双独立PG解决环境阻塞，1304单测/235集成及45PG类/两项JaCoCo门禁通过；生产前端、Chrome86静态、双文档构建通过；补16项业务API及会话测试后1180前端、函数73.17%覆盖率门禁通过；318d0c6c完整Chromium477/499，22失败均有通过的修复后定向复验，模拟用户差异已定位为注册分步异步重入意外POST，修复后原复现链及正常显式注册20/20，0ea9eb4e 完整 Chromium Mock 499/499通过（11.1分钟，退出0），原22项失败已收口；修复后1180前端/覆盖率门禁、真实JAR原生PG16联调2/2通过；固定Windows运行时取得并核验，0ea9eb4e 内部JAR与Windows ZIP外层3份SHA256、包内1195文件SHA256、路径及内嵌Git身份复核通过；该SHA真实JAR+PG16联调2/2及Python258通过5跳过、构建通过。新增P1授权竞争仍阻塞新候选；Windows实机及正式CI仍待；发布迁移上限已同步；旧正式基线合成数据直接/分段升级及空库安装3/3通过。合成旧库原生备份/独立恢复/升级及损坏归档拒绝已验；实际医院副本、Windows恢复脚本与ACL、完整CI、真实Chrome86/存储及独立安全复核未完成。授权后执行，现不发布。 |

## 冻结及非目标

[实验候选及发布阻塞](docs/backend/governance-stage-freeze-batch13.md) 是既有边界。自定义审计存储、双阶段分组、快照优化、条件OSS、外部严格候选保持默认关闭；不删除密文/账本/钥匙，不自动删除孤儿对象。无新生产证据不重复性能基准，不增加新框架、消息中间件或部署组件。

## 证据和历史

[交接索引](docs/engineering/ai-handoff.md) · [后端治理历史](docs/backend/ROADMAP-history-20261126.md) · [产品历史](docs/product/ROADMAP-history-20261126.md) · [正式发布门禁](docs/engineering/v1.2-release-preparation.md)。

每次只更新本任务池相关状态，必要时附一份实施/验证记录；不重复写入多个历史文件。

本轮完成记录：[架构简化与产品收口](docs/engineering/v1.2-simplification-closure.md)。

稳定期第一轮：[发布元数据及病案主档查询收口](docs/engineering/v1.2-stability-release-baseline.md)。

稳定期第二轮：[旧数据库前向升级验收](docs/engineering/v1.2-stability-upgrade.md)。

稳定期第三轮：[备份恢复及候选检查](docs/engineering/v1.2-stability-backup.md)。

稳定期第四轮：[OCR工作者事实复用边界](docs/engineering/v1.2-stability-ocr-review.md)。

稳定期第五轮：[导出文件交付边界](docs/engineering/v1.2-stability-export-review.md)。

稳定期第六轮：[凭据更新一致性](docs/engineering/v1.2-stability-credential-review.md)。

稳定期第七轮：[账号状态与会话失效](docs/engineering/v1.2-stability-account-state.md)。

稳定期第八轮：[影像导出诊断与患者对象日志](docs/engineering/v1.2-stability-privacy-review.md)。

稳定期第九轮：[患者列表请求归属](docs/engineering/v1.2-stability-patient-list.md)。

稳定期第十轮：[患者编辑与统计请求归属](docs/engineering/v1.2-stability-patient-edit.md)。

稳定期第十一轮：[装箱操作生命周期](docs/engineering/v1.2-stability-box-lifecycle.md)。

稳定期第十二轮：[影像查询及分类回调归属](docs/engineering/v1.2-stability-image-lifecycle.md)。

稳定期第十三轮：[统一设置确认与提交归属](docs/engineering/v1.2-stability-settings-review.md)。

稳定期第十四轮：[运维确认与状态归属](docs/engineering/v1.2-stability-operations-review.md)。

累计集成第一阶段：[候选范围与隐私契约](docs/engineering/v1.2-cumulative-integration-review.md)。

累计集成第二阶段：[本地交付验收与覆盖率](docs/engineering/v1.2-delivery-qualification.md)。

累计集成第三阶段：[候选浏览器及真实制品](docs/engineering/v1.2-delivery-browser-review.md)。

累计集成第四阶段：[注册分步提交一致性](docs/engineering/v1.2-registration-submit-review.md)。
