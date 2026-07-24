# 单体 JAR 部署

MRR 正式 Release 同时提供 Windows 离线包和可直接运行的单体 JAR。两种制品都内嵌 Vue 管理端，但默认端口、配套组件和适用场景不同。

## 1. 制品对照

| 制品 | 默认业务端口 | 包含内容 | 适用场景 |
| --- | ---: | --- | --- |
| `MRR-vX.Y.Z-standalone.jar` | `8002` | Spring Boot 后端 + 内嵌 Vue 前端 | 直接运行、已有反向代理、轻量部署 |
| `MRR-vX.Y.Z.zip` | `18045` | JAR、内嵌/外置前端、Nginx、WinSW、文档、运维脚本 | Windows Server 正式受管理部署 |

Actuator 默认仍监听：

```text
127.0.0.1:18046
```

两种制品都可以通过 `SERVER_PORT` 覆盖业务端口。

## 2. 下载与校验

Release 中的单体 JAR包含：

```text
MRR-vX.Y.Z-standalone.jar
MRR-vX.Y.Z-standalone.jar.sha256
```

Windows PowerShell 校验：

```powershell
$jar = '.\MRR-vX.Y.Z-standalone.jar'
$expected = (Get-Content "$jar.sha256").Split(' ')[0].Trim().ToLowerInvariant()
$actual = (Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actual -ne $expected) {
    throw "SHA-256 校验失败：expected=$expected actual=$actual"
}

Write-Host 'SHA-256 校验通过'
```

校验失败时不要运行文件，应重新下载并核对 Release 来源。

## 3. 最小运行条件

- JDK 21；
- PostgreSQL 16；
- 数据库连接信息；
- JWT 签名密钥；
- AES-GCM 密钥；
- 与医院环境匹配的图片来源配置；
- 对日志和临时导出目录的读写权限。

单体 JAR不包含数据库、Nginx、WinSW、医院密码、密钥或 OSS 凭据。

## 4. Windows 启动

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app'
$env:SPRING_DATASOURCE_USERNAME='mrr_app'
$env:SPRING_DATASOURCE_PASSWORD='请替换为数据库密码'
$env:JWT_SECRET_KEY='请替换为足够长的随机密钥'
$env:AES_SECRET_KEY='请替换为满足实现要求的随机密钥'

java -jar .\MRR-vX.Y.Z-standalone.jar
```

默认访问：

```text
http://localhost:8002
```

## 5. 修改端口

```powershell
$env:SERVER_PORT='9000'
java -jar .\MRR-vX.Y.Z-standalone.jar
```

访问：

```text
http://localhost:9000
```

不要同时在环境变量、命令行和外部配置文件中重复设置端口。

## 6. 使用外部配置文件

建议把生产配置放在 JAR 外部：

```text
C:\MRR-Standalone\
├── MRR-vX.Y.Z-standalone.jar
├── config\
│   └── application-prod.properties
├── logs\
└── exports\
```

启动：

```powershell
java -jar .\MRR-vX.Y.Z-standalone.jar `
  --spring.profiles.active=prod `
  --spring.config.additional-location=file:.\config\
```

`application-prod.properties` 不应提交到 Git，也不应随意发送给他人。

## 7. 建议生产配置

```properties
server.port=${SERVER_PORT:8002}

spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app
spring.datasource.username=mrr_app
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}

logging.file.name=C:/MRR-Standalone/logs/img-api.log
archive.export.temp-directory=C:/MRR-Standalone/exports

management.server.port=18046
management.server.address=127.0.0.1
```

密钥继续通过环境变量注入，不要写入示例配置。

## 8. 启动验证

### 页面

```text
http://localhost:8002/
```

应加载管理端登录页，而不是返回 404 或目录列表。

### 健康检查

```text
http://127.0.0.1:18046/actuator/health
```

Actuator 默认只允许本机访问。

### 数据库迁移

启动日志应显示 Flyway 校验和迁移成功。当前主分支兼容上限为：

```text
20260723163000
```

该迁移会创建 `app.system_error_event` 并补充运行错误权限。

### 业务抽样

至少验证：

- 登录；
- 用户权限；
- 患者与病案查询；
- 影像档案袋；
- 图片读取；
- ZIP/PDF 导出；
- `/status`；
- 运行错误中心权限；
- OSS/Nginx 图片来源。

## 9. 放到反向代理后

单体 JAR可以直接被现有 Nginx、IIS ARR 或其他受控代理转发。以 Nginx 为例：

```nginx
upstream mrr_standalone {
    server 127.0.0.1:8002;
    keepalive 32;
}

server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://mrr_standalone;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
    }
}
```

页面和 `/api/**` 都由同一个 JAR处理，不需要再把前端静态目录单独配置到 Nginx。VitePress 用户手册、开发文档和运维指南不在 JAR 中，帮助中心入口应在“系统设置 → 帮助与文档”中指向独立文档服务。

## 10. 作为 Windows 服务运行

单体 JAR本身没有内置服务管理器。可使用 WinSW、NSSM 或 Windows 任务计划程序，但正式长期运行更推荐使用仓库提供的 Windows 离线包，因为它已经包含：

- WinSW；
- Nginx；
- 一键管理中心；
- 安装、升级、回滚和维护模式脚本；
- 发布清单与完整 SHA-256；
- 用户手册和内部文档。

## 11. 升级

1. 记录当前版本、Git Commit 和数据库迁移；
2. 备份数据库和外部配置；
3. 下载新 JAR与 `.sha256`；
4. 校验摘要；
5. 阅读 `release-baseline.json` 和更新说明；
6. 停止旧进程；
7. 启动新 JAR；
8. 验证 Flyway、健康检查和关键业务；
9. 保留旧 JAR，但不要在不恢复数据库的情况下直接回滚。

当前 0.7.4 基线允许回滚到 0.7.3，因为没有新增数据库迁移或配置结构变更。回滚前仍应备份数据库、外部配置和完整应用目录，并重新验证文档入口与关键业务。

## 12. 常见问题

### 启动后仍监听 18045

可能运行的是普通后端 JAR或 Windows 离线包中的 JAR，而不是 Release 的 `-standalone.jar`。检查文件名和构建来源，也可以显式设置：

```powershell
$env:SERVER_PORT='8002'
```

### 页面 404，但 API 正常

说明 JAR中没有正确内嵌前端，或者下载的不是单体 JAR。正式工作流会验证：

```text
BOOT-INF/classes/static/index.html
BOOT-INF/classes/static/assets/
```

### 数据库启动失败

检查：

- PostgreSQL 是否运行；
- 数据库名和账号；
- `currentSchema=app`；
- 密码环境变量；
- Flyway 权限；
- 迁移是否被错误修改。

### 图片无法显示

单体 JAR只合并前后端，不会自动提供医院图片目录。仍需配置本地、Nginx、NAS/HTTP 或 OSS 图片来源。
