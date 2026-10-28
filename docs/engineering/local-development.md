# 可重复的本地开发与启动

关联提案：[#453](https://github.com/weepwood/MRR/issues/453)。风险等级 P1：本批修改构建与 CI 入口。

## 范围与复用

正式交付继续采用 Windows Server、Nginx、JAR 与 PostgreSQL16。本地入口运行原生 Java/Vite，仅数据库使用 Docker；凭据与数据库由开发入口独立创建。复用 engineering_preflight 的版本检查、run_live_e2e 的环境白名单及资源隔离约定、现有 Maven Wrapper、统一 API 与真实鉴权。

本批不改变业务接口、数据表、历史迁移、生产配置或权限语义。SBOM、依赖策略收紧、ArchUnit 和真实 Chrome86 现场验收另开独立批次。

## 版本与构建输入

- Java21；Maven 由 backend-repo/.mvn/wrapper/maven-wrapper.properties 固定并校验 SHA256。
- Node 开发版本下限见前端 .node-version，允许范围见 package.json；pnpm 由 packageManager 固定，通过 Corepack 调用。
- CI 和容器使用 Wrapper。Node Docker 镜像默认版本与 .node-version 一致，预检拒绝漂移。
- 容器构建提前复制 pnpm-workspace.yaml，使用冻结锁文件，不依赖本地 .npmrc。
- 镜像构建需要 MRR_GIT_COMMIT 为40位小写十六进制提交 SHA；前端和 JAR 均写入该身份。源码归档缺少该值直接失败。正式候选发布继续由现有工作流核对检出提交及制品身份。
- 构建镜像安装 unzip，避免 Wrapper 在缺少解压工具时改下 tar.gz，进而与 ZIP 哈希不符。

## Windows 开发命令

在仓库根目录运行，支持 Windows PowerShell5.1。Python3.10+ 需安装并可执行；如 python 命中 Windows 商店占位符，显式指定实际路径。

```powershell
.\scripts\dev.ps1 doctor -PythonExe 'C:\Python312\python.exe'
.\scripts\dev.ps1 up -PythonExe 'C:\Python312\python.exe'
.\scripts\dev.ps1 status -PythonExe 'C:\Python312\python.exe'
.\scripts\dev.ps1 down -PythonExe 'C:\Python312\python.exe'
```

up 冻结安装前端依赖并用 Wrapper 打包后端；后端命令显式使用 `-DskipTests`，因此启动流程不会运行测试套件。启动耗时主要来自依赖安装与 `clean package` 构建。首次需要联网下载依赖。Web 默认9200；`-WebPort 0` 自动分配本机端口，后端与管理端口自动分配。使用 `-SkipBuild` 可复用本工作树已有 JAR 和前端依赖，适用于只需重新启动的开发场景。完整测试由 PR GitHub Actions 按改动范围执行，本地仅在调试问题时按需运行相关测试。

Java、Vite 和 PostgreSQL 均仅绑定本机；管理端口不公开。启动就绪要求登录页200且无Token的auth/me返回401。账号通过现有注册审批或系统建号流程建立，不提供跳过鉴权的开发账号。

状态、随机本地密钥和日志存放在忽略目录 .mrr-dev；Windows限制为当前用户/SYSTEM可访问。该目录同时被Docker构建上下文排除。日志只在本地查看，不上传为诊断Artifact。停止时根据进程创建时间和容器归属标签校验所有权；保留命名数据库卷和密钥供下次启动使用。停止失败保留状态供重试，禁止按端口或名称通配结束其他项目进程。

## 开发容器与干净检出验证

开发Compose保留前后端和独立文档Nginx入口，支持通过项目名并行运行。数据库、应用及Web端口分别可用MRR_POSTGRES_PORT、MRR_APP_PORT、MRR_WEB_PORT调整。18046只用于容器内健康检查。

```powershell
python -m scripts.smoke_dev
```

该命令忽略本地.env，生成本次专用随机密钥、项目名和端口，构建全部镜像，等待健康状态，并验证直连与Nginx入口的登录页200和无Token401。无论成功失败均清理自己创建的容器、网络和数据库卷；不会复用开发数据库。完整构建需要Docker及网络，可在Linux CI和Windows Docker Desktop执行。

## 实施顺序、验证与回滚

1. 修复Dockerfile、指南、预检及CI工具链；以配置漂移失败测试和真实无缓存Wrapper下载验证。
2. 增加dev.py/dev.ps1及私有目录约束；验证外来容器拒绝清理、PID复用拒绝结束、损坏状态拒绝执行、重复up/down、启动失败清理与Windows实际启停。
3. 扩展现有ci_gate范围和pr-gate，新增development检查。仅构建输入或启动脚本变化触发开发镜像冒烟；保留正式候选构建与恢复演练。选中检查若失败、取消或意外跳过，聚合门禁必须失败。

执行Python治理测试、test_inventory、release_baseline、文档一致性、git diff检查，以及PowerShell5.1解析和上述实际启停/Compose命令。P1合并前需独立规格和代码审查，记录实际远端CI，不以本机结果替代。

回滚按工具链、开发入口、CI接入三个独立提交revert。回滚前先执行开发down；命名开发数据卷和本地密钥保留。无业务数据变更和数据库回退。
