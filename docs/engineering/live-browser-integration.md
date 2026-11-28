# 浏览器与真实后端联调测试

关联 #362；风险 P1（登录、写入拒绝和导入验证）。正式部署仍使用 Windows/Nginx/Spring Boot/PostgreSQL；Docker 是 CI 默认的一次性 PostgreSQL 16 测试环境；本地 POSIX 也可显式使用原生 PostgreSQL 16。

## 执行

准备 Java 21、Python 3.10+、Node 24.15+、pnpm 10.33 和 Docker，然后：

```powershell
mvn -B -ntp -f backend-repo/pom.xml package -DskipTests -Dlocal.config.exclude=true
cd frontend-fantastic-admin
pnpm install --frozen-lockfile
pnpm exec playwright install chromium
cd ..
python scripts/run_live_e2e.py --jar backend-repo/target/imgapi-0.8.0.jar
```

涉及关键链路的 PR 和 main 集成在统一应用验证中复用现有运行器；工作流 `Live browser integration` 保留手动/可复用诊断入口。
发布完整验收直接使用已嵌入生产前端的候选 JAR（`python -m scripts.run_live_e2e --jar <candidate.jar> --embedded`），不再启动 Vite。
它与现有 Mock E2E 使用独立配置和目录，不共享登录状态、不拦截接口、不伪造 Token。

在没有 Docker 的 macOS/Linux 开发机，可显式提供 PostgreSQL 16 的可执行文件目录，使用同一生产 JAR 与同一真实浏览器断言：

```bash
python -m scripts.run_live_e2e --jar <candidate.jar> --embedded --native-pg-bin <postgresql16-bin>
```

原生选项只接受可执行文件目录，不接受已有数据库地址、口令或数据目录。每次新建临时集群；TCP 只监听回环地址且要求 SCRAM，禁用 Unix socket，初始化口令文件权限为 0600 并在初始化后删除。结束或失败时只停止本次自建集群。Windows/CI 继续使用原 Docker 默认路径，原生结果不替代正式发布 CI。

运行器只创建随机名称的测试容器或独立临时集群，PostgreSQL 和后端只监听回环地址，端口随机分配。后端读取 JAR 内标准配置，环境变量采用白名单隔离，JWT/AES/审计密钥和测试密码每次生成。Flyway 初始化空库后，只在该临时数据库内插入两个虚构测试账号，分别使用现有 DOCTOR 和 READER 角色。测试禁止复用已有前端服务；结束或失败时清理自建进程和容器/临时集群，不连接外部数据库。

本测试不调阅影像，运行器提供仅响应 HEAD 的回环合成影像源，满足已有部署就绪检查；不关闭只读降级拦截器，不授予账号额外权限。该夹具不证明真实 Nginx/影像源可用。

浏览器使用正式表单登录，在数据交换页面校验错误文件、预览合法文件、确认写入并查询结果。测试验证预览前后数据库无写入、正式导入只插入一行、无 Token 返回 401、只读角色写入返回 403；运行器最后用 PostgreSQL 查询再次确认行数。账户与数据均为合成夹具。

不生成登录截图、视频或 Trace，不上传后端日志或浏览器测试目录；临时目录随运行清理。
本检查由 pr-gate 或发布完整验收汇总，不再单独要求每个普通 PR 提供 live-e2e 状态；迁移见 [分层 CI](ci-tiered-verification.md)。

## 回滚与限制

撤销本批即可移除测试链路；没有数据库迁移和生产配置变更。独立前端路由入口修复同批验证：当前默认使用手写路由，数据交换页面此前只有文件路由声明，导致患者页的入口跳回首页。测试实际复现后补入既有手写路由表，沿用页面的权限要求。

这是当前 Chromium 与真实 PostgreSQL 的合成数据验证，不替代 Chrome 86、院内 Windows Server、Nginx、NAS/OSS 或生产规模验收（#350）。
