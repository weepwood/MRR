# 模块依赖边界渐进治理

关联 #362；风险 P1（用户身份隔离的兼容重构）。

本批将 `ArchiveSearchHistoryController` 的数据访问与计数归一化移入 `ArchiveSearchHistoryService`，Controller 保留参数、协议、权限注解和当前会话身份提取。请求与响应使用 DTO，客户端旧的 id/userId/数据库时间字段被忽略；主键由数据库生成，userId 由真实会话提供。路径、响应字段、失败消息、已有收藏归属 SQL 均保持兼容。未修改 Mapper、SQL、事务或 Flyway。

验证覆盖空查询拒绝、失败查询计数、默认时间、负数归一化、可信用户归属、未登录拒绝、旧客户端伪造身份字段被忽略及收藏更新限定用户。

`scripts/check_architecture_boundaries.py` 检查 Controller 不能新增 Mapper 依赖，公共 UI 不能导入 api/views/store（含别名和相对路径）。现有 PatientController、ClassificationController 的直接依赖，以及 OcrBatchController 暴露 Mapper 内部结果类型各保留显式类型集合；即使沿用 `mapper.*`，新增 Mapper 字段/构造参数也会被拒绝。减少依赖时必须同步缩小集合，不能悄悄扩大允许范围。治理单元测试会对实际仓库执行检查，因此已有 Quality gate 的必需 release-baseline-gate 会阻断新增问题。

这是可执行的有限规则，不是完整 Java/TypeScript 语义分析；不声称能检测反射、动态拼接或所有间接依赖。暂不改造剩余复杂 Controller、大表查询、审计、OCR 和既有接口，以独立问题继续处理，避免本批扩大。

回滚可独立撤销本批，恢复原 Controller；不需要数据库回滚。Java DTO 保持现有 JSON 结构，测试直接验证旧请求字段与可信身份边界。
