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
    ROOT / 'deploy/windows/install.ps1',
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'MRR-管理中心.cmd') -Destination (Join-Path $Root 'ops\\MRR-管理中心.cmd') -Force\n",
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'MRR-Manager.cmd') -Destination (Join-Path $Root 'ops\\MRR-Manager.cmd') -Force\n"
    "Copy-Item -LiteralPath (Join-Path $scriptDir 'MRR-Manager.cmd') -Destination (Join-Path $Root 'ops\\MRR-管理中心.cmd') -Force\n",
)

replace_once(
    ROOT / 'deploy/windows/install.ps1',
    'Write-Host "6. 一键管理中心：双击 $Root\\ops\\MRR-管理中心.cmd"\n',
    'Write-Host "6. 一键管理中心：双击 $Root\\ops\\MRR-管理中心.cmd（或 MRR-Manager.cmd）"\n',
)

replace_once(
    ROOT / 'deploy/windows/README.md',
    'C:\\MRR\\ops\\MRR-管理中心.cmd\n```\n\n程序会自动请求管理员权限',
    'C:\\MRR\\ops\\MRR-管理中心.cmd\n```\n\n若解压或文件系统不支持中文文件名，也可以运行 `C:\\MRR\\ops\\MRR-Manager.cmd`。两个入口指向同一个管理程序。\n\n程序会自动请求管理员权限',
)

Path(__file__).unlink()
