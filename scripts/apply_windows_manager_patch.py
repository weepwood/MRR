from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding='utf-8')
    if new in text:
        return
    if old not in text:
        raise SystemExit(f'Expected block not found in {path}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8', newline='\n')


replace_once(
    ROOT / 'deploy/windows/mrrctl.ps1',
    "    if ($Name -in @('gateway','all')) { Start-Service $S.Gateway }\n}",
    "    if ($Name -in @('gateway','all')) { Start-Service $S.Gateway }\n"
    "    if ($Name -eq 'all') { Set-Maintenance $false }\n"
    "}",
)

replace_once(
    ROOT / 'deploy/windows/install.ps1',
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'nginx-control.cmd') -Destination (Join-Path $Root 'ops\\nginx-control.cmd') -Force\n",
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'nginx-control.cmd') -Destination (Join-Path $Root 'ops\\nginx-control.cmd') -Force\n"
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'mrr-manager.ps1') -Destination (Join-Path $Root 'ops\\mrr-manager.ps1') -Force\n"
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'MRR-管理中心.cmd') -Destination (Join-Path $Root 'ops\\MRR-管理中心.cmd') -Force\n",
)

replace_once(
    ROOT / 'deploy/windows/install.ps1',
    'Write-Host "5. Nginx 控制：$Root\\ops\\nginx-control.cmd status|start|stop|restart|reload|test|pause|resume"\n',
    'Write-Host "5. Nginx 控制：$Root\\ops\\nginx-control.cmd status|start|stop|restart|reload|test|pause|resume"\n'
    'Write-Host "6. 一键管理中心：双击 $Root\\ops\\MRR-管理中心.cmd"\n',
)

replace_once(
    ROOT / 'deploy/windows/README.md',
    '- **mrrctl.ps1**：状态、启停、维护、部署、版本和回滚入口；\n',
    '- **mrrctl.ps1**：状态、启停、维护、部署、版本和回滚入口；\n'
    '- **MRR 一键管理中心**：双击运行的 Windows 图形化管理入口，复用受控脚本执行操作；\n',
)

replace_once(
    ROOT / 'deploy/windows/README.md',
    '安装脚本会创建目录、复制配置模板、保护敏感配置目录、安装后端和网关服务并校验 Nginx。首次安装不会自动启动业务服务。\n\n## 配置\n',
    '安装脚本会创建目录、复制配置模板、保护敏感配置目录、安装后端和网关服务并校验 Nginx。首次安装不会自动启动业务服务。\n\n'
    '## 一键管理中心\n\n'
    '安装完成后，直接双击：\n\n'
    '```text\n'
    'C:\\MRR\\ops\\MRR-管理中心.cmd\n'
    '```\n\n'
    '程序会自动请求管理员权限，并集中展示产品版本、后端服务、Nginx 网关、健康状态、维护模式和磁盘空间。可执行：\n\n'
    '- 一键启动、停止和重启全部服务；\n'
    '- 开启维护模式与恢复正常访问；\n'
    '- 选择受管理 ZIP 执行部署；\n'
    '- 查看版本列表和执行系统诊断；\n'
    '- 检查或平滑重载 Nginx；\n'
    '- 打开系统首页、日志、配置和发布包目录。\n\n'
    '所有服务、维护和部署操作仍由 `mrrctl.ps1` 或 `nginxctl.ps1` 执行，管理中心不绕过发布基线、健康检查、构建身份或 SHA-256 校验。\n\n'
    '## 配置\n',
)

Path(__file__).unlink()
