#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    content = file.read_text(encoding='utf-8')
    if old not in content:
        raise SystemExit(f'{path}: expected block not found:\n{old}')
    file.write_text(content.replace(old, new, 1), encoding='utf-8', newline='\n')


def patch_installer() -> None:
    replace_once(
        'deploy/windows/install.ps1',
        """    [Parameter(Mandatory = $true)]
    [string]$WinSWPath,

    [Parameter(Mandatory = $true)]
    [string]$NginxPath,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
""",
        """    [string]$WinSWPath,

    [string]$NginxPath,

    [Parameter(Mandatory = $true)]
    [string]$JavaHome,
""",
    )
    replace_once(
        'deploy/windows/install.ps1',
        """$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resolvedWinSW = (Resolve-Path -LiteralPath $WinSWPath).Path
$resolvedNginx = (Resolve-Path -LiteralPath $NginxPath).Path
$resolvedJava = (Resolve-Path -LiteralPath $JavaHome).Path
""",
        """$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($WinSWPath)) {
    $WinSWPath = Join-Path $scriptDir '..\\..\\runtime\\winsw\\WinSW-x64.exe'
}
if ([string]::IsNullOrWhiteSpace($NginxPath)) {
    $NginxPath = Join-Path $scriptDir '..\\..\\runtime\\nginx'
}

$resolvedWinSW = (Resolve-Path -LiteralPath $WinSWPath).Path
$resolvedNginx = (Resolve-Path -LiteralPath $NginxPath).Path
$resolvedJava = (Resolve-Path -LiteralPath $JavaHome).Path
""",
    )
    replace_once(
        'deploy/windows/install.ps1',
        """Copy-Item -LiteralPath (Join-Path $scriptDir 'mrrctl.ps1') -Destination (Join-Path $Root 'ops\\mrrctl.ps1') -Force
""",
        """Copy-Item -LiteralPath (Join-Path $scriptDir 'mrrctl.ps1') -Destination (Join-Path $Root 'ops\\mrrctl.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'nginxctl.ps1') -Destination (Join-Path $Root 'ops\\nginxctl.ps1') -Force
Copy-Item -LiteralPath (Join-Path $scriptDir 'nginx-control.cmd') -Destination (Join-Path $Root 'ops\\nginx-control.cmd') -Force
""",
    )
    replace_once(
        'deploy/windows/install.ps1',
        """Write-Host '3. 将发布包放入 $Root\\packages'
Write-Host '4. 执行：$Root\\ops\\mrrctl.ps1 deploy <发布包路径>'
""",
        """Write-Host '3. 将发布包放入 $Root\\packages'
Write-Host '4. 执行：$Root\\ops\\mrrctl.ps1 deploy <发布包路径>'
Write-Host '5. Nginx 控制：$Root\\ops\\nginx-control.cmd status|start|stop|restart|reload|test|pause|resume'
""",
    )


def patch_workflow() -> None:
    replace_once(
        '.github/workflows/windows-release-package.yml',
        """    env:
      SOURCE_SHA: ${{ github.event.pull_request.head.sha || github.sha }}
""",
        """    env:
      SOURCE_SHA: ${{ github.event.pull_request.head.sha || github.sha }}
      NGINX_VERSION: 1.31.3
      WINSW_VERSION: 2.12.0
""",
    )
    replace_once(
        '.github/workflows/windows-release-package.yml',
        """          if ($failed) { exit 1 }

      - name: Test and package backend
""",
        """          if ($failed) { exit 1 }

      - name: Download pinned Windows runtime
        shell: bash
        run: |
          set -euo pipefail
          RUNTIME_DIR="build/windows-runtime"
          DOWNLOAD_DIR="build/windows-runtime-download"
          NGINX_DISTRIBUTION="nginx/Windows-${NGINX_VERSION}"

          rm -rf "${RUNTIME_DIR}" "${DOWNLOAD_DIR}"
          mkdir -p "${RUNTIME_DIR}/nginx" "${RUNTIME_DIR}/winsw" "${DOWNLOAD_DIR}"

          curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \\
            "https://nginx.org/download/nginx-${NGINX_VERSION}.zip" \\
            --output "${DOWNLOAD_DIR}/nginx.zip"
          unzip -q "${DOWNLOAD_DIR}/nginx.zip" -d "${DOWNLOAD_DIR}"
          cp -a "${DOWNLOAD_DIR}/nginx-${NGINX_VERSION}/." "${RUNTIME_DIR}/nginx/"

          curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \\
            "https://github.com/winsw/winsw/releases/download/v${WINSW_VERSION}/WinSW-x64.exe" \\
            --output "${RUNTIME_DIR}/winsw/WinSW-x64.exe"
          curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \\
            "https://raw.githubusercontent.com/winsw/winsw/v${WINSW_VERSION}/LICENSE.txt" \\
            --output "${RUNTIME_DIR}/winsw/LICENSE.txt"

          test -f "${RUNTIME_DIR}/nginx/nginx.exe"
          test -f "${RUNTIME_DIR}/nginx/conf/mime.types"
          test -f "${RUNTIME_DIR}/winsw/WinSW-x64.exe"

          cat > "${RUNTIME_DIR}/versions.json" <<EOF
          {
            "nginx": {
              "distribution": "${NGINX_DISTRIBUTION}",
              "version": "${NGINX_VERSION}",
              "source": "https://nginx.org/download/nginx-${NGINX_VERSION}.zip"
            },
            "winsw": {
              "version": "${WINSW_VERSION}",
              "source": "https://github.com/winsw/winsw/releases/download/v${WINSW_VERSION}/WinSW-x64.exe"
            }
          }
          EOF

          (
            cd "${RUNTIME_DIR}"
            find nginx winsw versions.json -type f -print0 \\
              | sort -z \\
              | xargs -0 sha256sum > SHA256SUMS
          )

      - name: Test and package backend
""",
    )
    replace_once(
        '.github/workflows/windows-release-package.yml',
        """          mkdir -p "${PACKAGE_DIR}/backend" "${PACKAGE_DIR}/frontend" \\
                   "${PACKAGE_DIR}/docs/user" "${PACKAGE_DIR}/docs/internal" \\
                   "${PACKAGE_DIR}/deploy/windows"
""",
        """          mkdir -p "${PACKAGE_DIR}/backend" "${PACKAGE_DIR}/frontend" \\
                   "${PACKAGE_DIR}/docs/user" "${PACKAGE_DIR}/docs/internal" \\
                   "${PACKAGE_DIR}/deploy/windows" "${PACKAGE_DIR}/runtime"
""",
    )
    replace_once(
        '.github/workflows/windows-release-package.yml',
        """          cp -a deploy/windows/. "${PACKAGE_DIR}/deploy/windows/"
          cp VERSION release-baseline.json "${PACKAGE_DIR}/"
""",
        """          cp -a deploy/windows/. "${PACKAGE_DIR}/deploy/windows/"
          cp -a build/windows-runtime/. "${PACKAGE_DIR}/runtime/"
          cp VERSION release-baseline.json "${PACKAGE_DIR}/"

          test -f "${PACKAGE_DIR}/runtime/nginx/nginx.exe"
          test -f "${PACKAGE_DIR}/runtime/winsw/WinSW-x64.exe"
          test -f "${PACKAGE_DIR}/runtime/SHA256SUMS"
          test -f "${PACKAGE_DIR}/deploy/windows/nginxctl.ps1"
          test -f "${PACKAGE_DIR}/deploy/windows/nginx-control.cmd"
""",
    )
    replace_once(
        '.github/workflows/windows-release-package.yml',
        """            find backend frontend docs deploy VERSION release-baseline.json manifest.json release-notes.md -type f -print0 \\
""",
        """            find backend frontend docs deploy runtime VERSION release-baseline.json manifest.json release-notes.md -type f -print0 \\
""",
    )


def patch_windows_readme() -> None:
    replace_once(
        'deploy/windows/README.md',
        """本目录提供面向单台 Windows Server 的 MRR 部署方案。生产服务器只运行 PostgreSQL、JDK、Nginx、WinSW 和 MRR 发布包，不需要安装 Node.js、pnpm、Maven，也不执行 `git pull`。
""",
        """本目录提供面向单台 Windows Server 的 MRR 部署方案。生产服务器只需要预装 PostgreSQL 和 JDK；Nginx 与 WinSW 已固定版本并包含在 MRR Windows 离线包中，不需要安装 Node.js、pnpm、Maven，也不执行 `git pull`。
""",
    )
    replace_once(
        'deploy/windows/README.md',
        """- Windows 版 Nginx；
- WinSW。
""",
        """Nginx for Windows 与 WinSW 由发布工作流下载、记录版本和 SHA256，并随离线 ZIP 交付，无需在服务器上单独准备。
""",
    )
    replace_once(
        'deploy/windows/README.md',
        """.\\deploy\\windows\\install.ps1 `
  -Root C:\\MRR `
  -WinSWPath C:\\Install\\WinSW-x64.exe `
  -NginxPath C:\\Install\\nginx-1.xx.x `
  -JavaHome 'C:\\Program Files\\Java\\jdk-21'
""",
        """.\\deploy\\windows\\install.ps1 `
  -Root C:\\MRR `
  -JavaHome 'C:\\Program Files\\Java\\jdk-21'
""",
    )
    replace_once(
        'deploy/windows/README.md',
        """deploy/windows/
VERSION
""",
        """deploy/windows/
runtime/nginx/
runtime/winsw/
runtime/versions.json
runtime/SHA256SUMS
VERSION
""",
    )
    replace_once(
        'deploy/windows/README.md',
        """# 维护模式
& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off

# 版本信息
""",
        """# 维护模式
& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off

# 独立 Nginx 控制器
$nginx = 'C:\\MRR\\ops\\nginxctl.ps1'
& $nginx status
& $nginx start
& $nginx test
& $nginx reload
& $nginx pause -Message '系统升级中，请稍后再试。'
& $nginx resume
& $nginx restart
& $nginx stop

# 也可使用 CMD 入口
C:\\MRR\\ops\\nginx-control.cmd status

# 版本信息
""",
    )
    replace_once(
        'deploy/windows/README.md',
        """MRR 不使用系统工具挂起 Java 进程。维护模式由 Nginx 返回 503 页面，后端可完成已经进入的请求，本机 Actuator 仍保持可访问。
""",
        """MRR 不使用系统工具挂起 Java 或 Nginx 进程。`nginxctl.ps1 pause` 表示开启维护模式：Nginx 保持运行并返回 503 维护页，后端可完成已经进入的请求，本机 Actuator 仍保持可访问；`resume` 恢复正常流量。
""",
    )


def patch_internal_doc() -> None:
    replace_once(
        'vitepress-doc/internal/windows-deployment.md',
        """准备 JDK 21、PostgreSQL、Windows 版 Nginx 和 WinSW 后，以管理员身份执行：
""",
        """准备 JDK 21 和 PostgreSQL 后，以管理员身份执行。Nginx for Windows 与 WinSW 已包含在离线发布包中：
""",
    )
    replace_once(
        'vitepress-doc/internal/windows-deployment.md',
        """.\\deploy\\windows\\install.ps1 `
  -Root C:\\MRR `
  -WinSWPath C:\\Install\\WinSW-x64.exe `
  -NginxPath C:\\Install\\nginx `
  -JavaHome 'C:\\Program Files\\Java\\jdk-21'
""",
        """.\\deploy\\windows\\install.ps1 `
  -Root C:\\MRR `
  -JavaHome 'C:\\Program Files\\Java\\jdk-21'
""",
    )
    replace_once(
        'vitepress-doc/internal/windows-deployment.md',
        """& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off
```

MRR 不通过挂起 JVM 实现“暂停”。维护模式由 Nginx 返回 503 页面，后端仍可完成已进入的请求，本机 Actuator 保持可用。
""",
        """& $ctl maintenance on -Message '系统升级中，请稍后再试。'
& $ctl maintenance off

$nginx = 'C:\\MRR\\ops\\nginxctl.ps1'
& $nginx status
& $nginx start
& $nginx reload
& $nginx pause -Message '系统升级中，请稍后再试。'
& $nginx resume
& $nginx stop
```

MRR 不通过挂起 JVM 或 Nginx 进程实现“暂停”。`nginxctl.ps1 pause` 开启 503 维护模式并保持网关运行，`resume` 恢复正常流量；本机 Actuator 始终保持可用。
""",
    )
    replace_once(
        'vitepress-doc/internal/windows-deployment.md',
        """deploy/windows/
VERSION
""",
        """deploy/windows/
runtime/nginx/
runtime/winsw/
runtime/versions.json
runtime/SHA256SUMS
VERSION
""",
    )


def main() -> None:
    patch_installer()
    patch_workflow()
    patch_windows_readme()
    patch_internal_doc()
    placeholder = ROOT / 'docs/placeholder-nginx-bundle.md'
    if placeholder.exists():
        placeholder.unlink()


if __name__ == '__main__':
    main()
