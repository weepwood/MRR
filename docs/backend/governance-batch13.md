# 后端治理第十三批（2026-11-26）
基线30f5cc47，初始工作树干净；P1[提案](governance-batch13-proposal.md)。冻结审计引擎，无默认审计模式或历史Flyway修改，无新增依赖，不访问正式数据、不重复旧负载基准。
## 1. 医疗资源、OCR及导出
- OssController及OssMigrationManagementController使用独立Scan响应投影，保留ID、BAH/SJH、文件名、目录、现有迁移显示状态；不返回源节点/引用/内部操作员/对象key/校验值等非必要原值。保留原目录与文件名业务契约，这些自由文本尚非完整隐私审查；未修改原Scan或DB。迁移前检查activeJob也使用现有历史任务隐私保护。封闭11种已知错误码；迁移日志仅已知整串或固定前缀码可显示，历史自由文本、路径仍保护，不修改留存证据。
- 真实OCR RED：影像当前病案有效，但mr_scan_ocr.archive_id改为另一病案后，具备全文权限的用户仍得到旧文本。新factForCurrentArchive单条SQL联合当前有效scan/病案及事实archive_id；错配返回无文本PENDING，不删除历史。真实HTTP原始全文正向、仅摘要无正文、无全文权403、错配无旧文本；本人任务可见，其他OCR主体列表空、详情/明细403。仅修复当前事实接口，未证明同病案源内容变化、全部分类/搜索历史结果及并发撤销。
- 新PG测试首次在同MyBatis事务会话直接JDBC改动后命中本地缓存失败；该查询明确useCache=false和flushCache=TRUE后完整定向33/33通过；原始失败保留。HTTP最终代码复验通过。
- 实际隔离业务响应：装箱6个读路由403/200及3个写路由read403/edit200，创建/修改/删除后核对合成记录；PDF整案规划/选中规划read403/pdf200；影像网关403/200且字节相符，旧签名网关409。未运行真实PDF生成、OCR识别推理或大规模任务。
- 最终54次实际HTTP（27个200、26个403、1个409），覆盖25条不同声明，其中22新增、3历史复验。累计54/276、余222；清单api-resource-review-batch13.tsv。不把已验路由视作全部角色字段组合通过。
## 2. 迁移维护与协调
- job updateProgress仅running/cancelling，complete仅pending/running/cancelling；已interrupted或终态不再被迟到工作者覆盖。批次及逐scan前读取活动状态，进度更新0行停止；保持原scan UUID+来源快照防护、提交不明精确核对。异常日志不再输出迁移任务原始异常堆栈。
- MigrationMaintenanceRecovery复用Spring事务；仅显式启动恢复开关启用，获取两表SHARE ROW EXCLUSIVE NOWAIT后中断任务及释放领取，同事务、30s预算及25s语句；失败回滚。真实另一事务持锁→拒绝→job/claim保持→锁解除→两个域恢复；旧worker迟到progress/complete0行且原计数不变。仍须确认全部实际迁移工作者及外部写入停止；SQL锁不能围栏远程PUT。默认关闭，未实现自动时间租约/跨主机接管；见[migration维护手册](migration-maintenance-batch13.md)。不删除孤儿、不自动重试提交未知状态。
## 3. 受控业务兼容
- 迁移成功日志可按当前正整数Scan ID通过既有getReviewImage认证Blob API读取，不使用历史签名链接，不拼存储地址，不新增接口或Axios。页面标注当前影像非历史字节快照；关闭、离页、卸载撤销URL/AbortController，迟到结果不回填；非image MIME或权限拒绝显示安全提示。不改变其他界面。前端44项定向通过，含两条新增预览生命周期测试，vue-tsc及修改文件ESLint通过。实际后端影像权限与字节通过；未执行真实浏览器/Chrome86现场验收。
## 4. 风险分级验收及冻结
- Java最终74/74、隔离PG16最终33/33（4类）、前端44/44（3文件）；最终HTTP54/54，初始38、OCR RED、修复50、最终54，均是新业务验证，非旧负载。PG首次33项有1个新OCR缓存失败，修复完整复验通过；前端初次lint发现新代码样式问题，修正后通过；全部实验原始日志保留。测试组件已有MrrFilterBar未注册警告及pnpm旧配置提示保留，不影响本轮测试通过，未宣称全量lint/CI。
- 当前资源矩阵、独占维护手册、[阶段冻结清单](governance-stage-freeze-batch13.md)已建立。前十一批136文件基于上一批风险审查与本轮核心调用链复核，不是全文件独立逐行安全审查。审计实验范围冻结，既有候选继续关闭，未新增审计架构。剩余222路由、跨主机租约及存储围栏、实际OSS与Windows、真实浏览器、外部流终态/异步断流、长时和正式CI/独立审查仍为阻塞。没有正式发布授权。
- 证据 /Volumes/data/AI-Development/artifacts/mrr-backend13-20261126/。隔离PG全部停止。仅本地，不推送、不合并、不发布。
