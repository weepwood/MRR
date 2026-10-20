# 后端工程与本地验证

本项目使用 Java 21、Spring Boot、MyBatis、Flyway、PostgreSQL 16。依赖版本以 pom.xml 为准，产品版本以根目录 VERSION 为准。正式部署是 Windows Server + Nginx + JAR + PostgreSQL，不依赖 Docker。

## 开发入口

先读根目录 AGENTS.md、ARCHITECTURE.md 和 docs/engineering/ai-assisted-development-governance.md。功能和修复从最新 origin/main 创建 codex/ 分支；已有工作区有未提交文件时先保留，不直接切换或覆盖。

仓库提供 Maven Wrapper，固定 Maven 3.9.9 并校验分发包 SHA-256。首次使用需要联网下载 Maven 与依赖；医院离线部署只消费已验证发布包，不在服务器临时构建。

在根目录运行只读环境检查：

```powershell
python scripts/engineering_preflight.py --scope backend --integration
```

Python 3.10+ 用于仓库工具和 OCR 的真实子进程测试；Maven 不会自动继承运行预检的 Python，需指定实际路径：

```powershell
$env:JAVA_HOME = '<本机 Java 21 目录>'
$env:MRR_TEST_PYTHON = '<本机 Python 可执行文件绝对路径>'
cd backend-repo
.\mvnw.cmd -v
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp verify
```

Linux 使用 `./mvnw`。预检失败会返回非零退出码，不读取本地业务配置、不输出连接口令、不安装或修改工具。

## 配置与隐私

只提交 application-local.template.properties 等模板，本机配置与凭据保持忽略。不得把生产配置、患者编号、姓名、身份证、令牌、内部地址或密钥放入日志、测试夹具、截图及 Artifact。

日志沿用现有脱敏、请求跟踪与可信会话身份机制。不要开启全局 SQL DEBUG，也不要按旧文档另行安装 ELK、Vault 或独立追踪平台。日志采集开关、私有目录和恢复边界见 docs/engineering/mrr-data-operations-runbook.md。

## 测试分层

- `test`：单元和切片测试，包含需要 Python 的 OCR 子进程测试。
- `verify`：单元和集成测试；SQL、锁、Flyway、事务和 Mapper 以 PostgreSQL 16 为事实标准。
- PostgreSQL 可使用 MRR_TEST_POSTGRES_URL / USERNAME / PASSWORD 指定隔离测试库；测试会清表，只能使用可销毁的测试数据库。
- 本机没有外部测试库时使用 Testcontainers 启动 PostgreSQL 16；Docker 只用于开发和测试。
- 检查报告中的 skipped 数量，不能把未执行的数据库用例认定为验证通过。
- H2 的普通审计测试不证明 PostgreSQL 专有行为。

CI 的实际入口是 .github/workflows/quality-gate.yml、governance-gate.yml 和发布工作流。覆盖率与产物检查以这些文件和 quality/ 下的基线为准，不假定存在旧的 ci-cd.yml、固定50%硬门槛或 Docker生产发布。

## 发布与现场验收

发布前执行根目录治理命令：

```powershell
python -m unittest discover -s scripts/tests -p 'test_*.py' -v
python scripts/test_inventory.py --fail-on-focused
python scripts/release_baseline.py validate
python scripts/check_engineering_docs.py
```

接口权限拒绝、边界、失败恢复及独立审查需要实际证据。CI 的合成数据测试不等同于医院 Windows Server、Chrome 86、真实 Nginx/NAS/OSS、大表性能或备份恢复验收。现场步骤和未完成验收由 Issue #350 跟踪。

回滚代码通过独立提交和现有发布流程处理；数据库只做前向修复，不能删除或改写已进入 main 的 Flyway 迁移。
