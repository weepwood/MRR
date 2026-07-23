# MRR 单体 JAR

正式 GitHub Release 除 Windows 离线 ZIP 外，同时提供：

```text
MRR-vX.Y.Z-standalone.jar
MRR-vX.Y.Z-standalone.jar.sha256
```

该 JAR 已内嵌 Vue 管理端，不需要单独部署前端或 Nginx。默认业务端口为 `8002`，仍可通过 `SERVER_PORT` 环境变量覆盖。

## 最小运行条件

- JDK 21；
- PostgreSQL 16；
- 数据库连接、JWT 和 AES 密钥；
- 按实际情况配置本地/Nginx/OSS 图片来源。

## Windows 启动示例

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/imgapi?currentSchema=app'
$env:SPRING_DATASOURCE_USERNAME='postgres'
$env:SPRING_DATASOURCE_PASSWORD='请替换为数据库密码'
$env:JWT_SECRET_KEY='请替换为足够长的随机密钥'
$env:AES_SECRET_KEY='请替换为至少 32 字节的随机密钥'

java -jar .\MRR-vX.Y.Z-standalone.jar
```

启动后访问：

```text
http://localhost:8002
```

## 修改端口

```powershell
$env:SERVER_PORT='9000'
java -jar .\MRR-vX.Y.Z-standalone.jar
```

## 与 Windows 离线包的区别

| 资产 | 默认端口 | 内容 | 适用场景 |
|---|---:|---|---|
| `MRR-vX.Y.Z-standalone.jar` | 8002 | 后端 + 内嵌前端 | 直接运行、已有反向代理或轻量部署 |
| `MRR-vX.Y.Z.zip` | 由外部配置设置为 18045 | JAR、Nginx、WinSW、文档和运维脚本 | Windows Server 正式受管理部署 |

单体 JAR 不包含数据库密码、密钥或医院环境配置，这些内容必须通过环境变量、命令行参数或外部 Spring 配置提供。
