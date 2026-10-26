# MRR 合成测试资产

入口：[生成、使用和验证说明](../docs/engineering/synthetic-test-data.md)。

```powershell
python scripts/generate_test_data.py --output test-data/generated/my-suite
python scripts/generate_test_data.py --verify test-data/generated/my-suite
```

生成代码：`scripts/generate_test_data.py`。只写新目录，不连接数据库，不使用真实患者或凭据。

本地交付的 `generated/smoke-v2/` 可用于小规模功能测试；`generated/scale-1000-v2/` 用于分卷导入/查询测试。先看各套 `expected.json` 和 `cases.json`。批量集最多包含48张实际扫描图，不能用来断言所有源文件存在。旧版无v2后缀目录是中间产物，不应使用。

`generated/` 不入Git，按需重建；ZIP交付包含同一生成器、测试、说明和两套已生成数据。异常数据逐个隔离执行。权限、票据和OCR合同需要在现有测试服务中绑定，不是直接导入表。
