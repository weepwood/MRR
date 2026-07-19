# 可选内置运行时

为了让服务器安装更简单，可以在发布 ZIP 中放入下列 Windows x64 运行时：

```text
runtime/
├─ jre/
│  └─ bin/java.exe
├─ nginx/
│  └─ nginx.exe
└─ winsw/
   └─ WinSW-x64.exe
```

`install.ps1` 会自动发现这些目录，因此管理员只需双击 `install.cmd`。

说明：

- Java 建议使用 JDK 21，而不仅是精简 JRE，因为按需 JFR 需要 `jcmd.exe`。
- Nginx 必须包含完整的 `conf` 目录和 `mime.types`。
- 仓库不提交第三方二进制文件；发布流水线或发布人员应从官方来源下载并校验 SHA-256 后放入打包目录。
- 未携带运行时时，仍可通过 `-JavaHome`、`-NginxPath` 和 `-WinSWPath` 参数使用服务器已有安装。
