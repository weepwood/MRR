from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WINDOWS_DEPLOY = ROOT / 'deploy/windows'
UTF8_BOM = b'\xef\xbb\xbf'


def read_ps1(path: Path) -> str:
    raw = path.read_bytes()
    if raw.startswith(UTF8_BOM):
        raw = raw[len(UTF8_BOM):]
    return raw.decode('utf-8')


def write_ps1(path: Path, text: str) -> None:
    path.write_bytes(UTF8_BOM + text.replace('\r\n', '\n').encode('utf-8'))


def replace_once(text: str, old: str, new: str, path: Path) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f'Expected block not found in {path}')
    return text.replace(old, new, 1)


manager_path = WINDOWS_DEPLOY / 'mrr-manager.ps1'
manager = read_ps1(manager_path)
manager = replace_once(
    manager,
    "param(\n    [string]$Root\n)",
    "param(\n    [string]$Root,\n    [switch]$SelfTest\n)",
    manager_path,
)
manager = replace_once(
    manager,
    "if (-not (Test-Administrator)) {",
    "if (-not $SelfTest -and -not (Test-Administrator)) {",
    manager_path,
)
manager = replace_once(
    manager,
    "if (-not (Test-Path -LiteralPath $nginxCtl -PathType Leaf)) {\n    $nginxCtl = Join-Path $scriptDir 'nginxctl.ps1'\n}\n\n$manifestPath",
    "if (-not (Test-Path -LiteralPath $nginxCtl -PathType Leaf)) {\n"
    "    $nginxCtl = Join-Path $scriptDir 'nginxctl.ps1'\n"
    "}\n\n"
    "if ($SelfTest) {\n"
    "    if (-not (Test-Path -LiteralPath $mrrCtl -PathType Leaf)) { throw \"找不到 MRR 控制脚本：$mrrCtl\" }\n"
    "    if (-not (Test-Path -LiteralPath $nginxCtl -PathType Leaf)) { throw \"找不到 Nginx 控制脚本：$nginxCtl\" }\n"
    "    [pscustomobject]@{\n"
    "        Title = 'MRR 一键管理中心'\n"
    "        PowerShellVersion = [string]$PSVersionTable.PSVersion\n"
    "        PowerShellEdition = [string]$PSVersionTable.PSEdition\n"
    "        MrrControl = $mrrCtl\n"
    "        NginxControl = $nginxCtl\n"
    "        Root = $Root\n"
    "    } | ConvertTo-Json -Depth 3\n"
    "    exit 0\n"
    "}\n\n"
    "$manifestPath",
    manager_path,
)
write_ps1(manager_path, manager)

for ps1_path in sorted(WINDOWS_DEPLOY.rglob('*.ps1')):
    if ps1_path == manager_path:
        continue
    write_ps1(ps1_path, read_ps1(ps1_path))

wrapper_path = WINDOWS_DEPLOY / 'MRR-Manager.cmd'
wrapper = wrapper_path.read_text(encoding='utf-8-sig')
wrapper = replace_once(
    wrapper,
    'powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%SCRIPT_DIR%mrr-manager.ps1"',
    'powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File "%SCRIPT_DIR%mrr-manager.ps1" %*',
    wrapper_path,
)
wrapper_path.write_text(wrapper, encoding='utf-8', newline='\r\n')

readme_path = WINDOWS_DEPLOY / 'README.md'
readme = readme_path.read_text(encoding='utf-8')
encoding_section = """## Windows 脚本编码约定

为兼容 Windows Server 2019 自带的 Windows PowerShell 5.1：

- `deploy/windows/**/*.ps1` 必须使用 **UTF-8 with BOM**，否则 PowerShell 5.1 会按系统 ANSI 代码页解释中文字符串和文件名；
- `deploy/windows/*.cmd` 必须使用 **UTF-8 without BOM**，避免 BOM 被 `cmd.exe` 当作首条命令的一部分；
- CMD 入口必须先执行 `chcp 65001`，再输出或解析中文内容；
- `mrr-manager.ps1 -SelfTest` 用于在 Windows PowerShell 5.1 下无界面验证程序集、中文文本和控制脚本路径。

该约定由 Python 防回归测试和 `windows-latest` 门禁共同检查。

"""
if encoding_section not in readme:
    marker = '## 配置\n'
    if marker not in readme:
        raise SystemExit('README configuration marker not found')
    readme = readme.replace(marker, encoding_section + marker, 1)
    readme_path.write_text(readme, encoding='utf-8', newline='\n')

Path(__file__).unlink()
