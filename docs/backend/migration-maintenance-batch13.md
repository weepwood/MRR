# 迁移独占维护恢复及失联接管边界
## 使用约束
默认 app.migration.recover-on-startup=false。不因启动、旧时间戳或暂时不可达自动释放活跃领取。仅授权管理员在已核实所有迁移工作者、手工上传与外部存储写入均停止的维护窗口执行。不得把SQL锁当作跨主机/对象存储租约。
1. 停止入口流量，停止全部旧实例和迁移工作者；核查现场主机、运行任务、存储未完成请求和备份。任何状态未知时保持暂停，不自动清除。
2. 以只读事务核对任务及领取状态，按实际ID受控核对提交不明的来源、对象存在、长度、内容校验值及当前引用。不得仅根据年龄判为失联。以下只读摘要不返回病案号、路径、对象Key；大表超时即未知，不降低限制。
```sql
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT status,count(*) FROM app.migration_job
 WHERE status IN ('pending','running','cancelling') GROUP BY status;
SELECT migration_status,count(*) FROM app.mr_scan
 WHERE migration_status='migrating' GROUP BY migration_status;
ROLLBACK;
```
3. 已确认独占且证据保存后，仅启动一套维护实例并显式开启 recover-on-startup。MigrationMaintenanceRecovery 在同一事务以NOWAIT取得migration_job和mr_scan的SHARE ROW EXCLUSIVE锁，再中断任务、释放领取；30s事务/25s语句预算，冲突或失败整体回滚，记录异常类型。失败时不可宣称已恢复。
4. 检查恢复结果及保留对象、审计日志；关闭该开关，恢复正常启动及流量前再次核对。不要自动删除无引用对象，不要自动覆盖、签名或重试提交未知的记录。
## 围栏与下一步
现有scan领取UUID+来源快照保护数据库完成/失败；job ID+活动状态保护进度/终态，执行循环在每批及每张影像前检查活动状态。旧工作者已进入外部操作的竞态仍存在。真正自动接管须以数据库时钟的租约、不可复用代次、续租失败立即停止新外部副作用、对象条件创建/不可变版本及独立提交核对共同保证。现场OSS语义、跨主机停机与断网未验，因此本批不实现按时间强制重领或部署租约候选。可用现有OCR租约作数据库侧参考，但不能直接套用到OSS。
## 验收边界
隔离PG真实独立事务持有mr_scan写锁，维护NOWAIT失败后job及claim不变；释放锁后同一事务恢复两个域。迟到worker更新已interrupted job的progress/complete均0行，保留原计数。前十二批真实对象与数据库确认丢失证据继续有效于其对应版本。未对真实医院执行恢复、对象盘点或备份操作。
