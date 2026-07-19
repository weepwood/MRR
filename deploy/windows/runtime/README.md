# Windows 离线运行时

GitHub 的 `Windows release package` 工作流会自动把 Windows x64 运行时放入本目录：

```text
runtime/
├─ jdk/
│  ├─ bin/java.exe
│  └─ bin/jcmd.exe
├─ nginx/
│  ├─ nginx.exe
│  └─ conf/mime.types
├─ winsw/
│  └─ WinSW-x64.exe
└─ runtime-manifest.json
```

运行时来源：

- Eclipse Temurin JDK 21：通过 Adoptium 官方 API 下载，并使用 API 提供的 SHA-256 校验。
- nginx/Windows：固定到工作流声明的稳定版本，并验证 nginx.org 提供的分离 PGP 签名。
- WinSW：固定到稳定 2.x 版本；优先验证 GitHub Release Asset 的 SHA-256 digest，并把最终哈希写入 Manifest。

服务器部署时不需要联网下载这些组件。`install.ps1` 会自动发现运行时，并复制到 `C:\MRR\runtime` 后再注册 Windows 服务。

仓库本身不提交第三方二进制文件；它们只存在于 CI 生成的发布 ZIP 中。需要手工构建时，也可以自行准备同样的目录，或者通过 `-JavaHome`、`-NginxPath`、`-WinSWPath` 使用服务器已有安装。
