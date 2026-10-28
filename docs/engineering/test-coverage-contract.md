# 测试执行与覆盖率门禁

关联 #362、#244、#450。覆盖率不是业务正确性的替代品；本门禁用于阻止已有覆盖能力静默回退。

## 同步检查

完整发布候选验收在 `windows-release-package.yml` 调用现有比较器并传
`--fail-on-regression`；失败使 release-validation/qualify 失败，无法创建正式 tag 和发布。
普通 PR 保留单测，按风险执行 PostgreSQL/浏览器检查，不逐 PR 采集全仓覆盖率。
`coverage-baseline` 只保留手动补充报表，不能替代完整验收中的同步门禁。
Artifact 上传失败不跳过测试或改变测试结果。详见 [分层 CI](ci-tiered-verification.md)。

基线来源是已成功的 main `7756c98d`，quality-gate run `39125525630`，
2026-10-18 的真实 Artifact。容忍下降为 0.5 个百分点；新增功能应补充有意义的行为测试，
不能为通过门禁删除断言、排除业务文件或任意调低基线。

## 后端报告口径

- `mvn test` 在 `target/site/jacoco` 生成单测报告。
- `mvn verify` 仍保留单测报告，并在集成测试完成后生成
  `target/site/jacoco-combined`，避免把测试前的报告误称为集成覆盖率。
- 两种报告都与同一单测基线比较，因此集成汇总的增量不能掩盖单测覆盖回退。
- 正式对比建议 `mvn clean verify`，避免前一次 `jacoco.exec` 累积影响本地报告。

## PostgreSQL 必须实际执行

移除仅因缺少 Docker 就跳过整个测试类的注解。已有共享支持类优先使用
`MRR_TEST_POSTGRES_URL/USERNAME/PASSWORD`，没有外部测试库才启动 Testcontainers。
既无外部测试库又无 Docker 时 `verify` 失败，不能返回“绿色但跳过”。

CI 的验证命令：

```powershell
python scripts/check_test_execution.py --reports backend-repo/target/failsafe-reports
```

脚本按当前源码中的 `*PostgresqlIT.java` 核对报告完整性，拒绝未执行、跳过、失败、
错误及缺失报告。只应对完整验证运行此检查；选择性集成测试仍可本地执行，
但不能以其结果冒充完整 PostgreSQL 门禁。

测试数据库必须可销毁，严禁设置为医院业务库。真实 SQL/事务测试不证明 Windows Server、
存储、Chrome 86 或医院恢复演练已经验收。

## 本轮验证

本地 `clean verify`：830 单元、81 集成全部通过，无跳过；20 个 PostgreSQL 类报告完整。
前端 60 个文件、347 项测试通过。单测与集成汇总、前端三项基线比较均实际通过。
覆盖率汇总的行覆盖率高于单测报告，验证报告阶段分离生效。
回滚撤销本批独立提交即可，无业务数据或数据库迁移变化。
