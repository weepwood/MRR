import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class WindowsBundledNginxTest(unittest.TestCase):
    def test_release_workflow_bundles_pinned_nginx_and_winsw(self):
        workflow = (ROOT / '.github/workflows/windows-release-package.yml').read_text(encoding='utf-8')

        nginx_version = re.search(r'NGINX_VERSION:\s*([0-9]+\.[0-9]+\.[0-9]+)', workflow)
        self.assertIsNotNone(nginx_version)
        self.assertGreaterEqual(
            tuple(map(int, nginx_version.group(1).split('.'))),
            (1, 31, 7),  # CVE-2026-15176 and related October 2026 fixes
        )
        self.assertRegex(workflow, r'WINSW_VERSION:\s*[0-9]+\.[0-9]+\.[0-9]+')
        self.assertIn('nginx/Windows-', workflow)
        self.assertIn('WinSW-x64.exe', workflow)
        self.assertIn('runtime/nginx/nginx.exe', workflow)
        self.assertIn('runtime/winsw/WinSW-x64.exe', workflow)
        self.assertIn('runtime/SHA256SUMS', workflow)

    def test_installer_uses_and_verifies_packaged_runtime_by_default(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')

        self.assertNotRegex(installer, r'\[Parameter\(Mandatory\s*=\s*\$true\)\]\s*\[string\]\$NginxPath')
        self.assertNotRegex(installer, r'\[Parameter\(Mandatory\s*=\s*\$true\)\]\s*\[string\]\$WinSWPath')
        self.assertIn("Join-Path $scriptDir '..\\..\\runtime\\nginx'", installer)
        self.assertIn("Join-Path $scriptDir '..\\..\\runtime\\winsw\\WinSW-x64.exe'", installer)
        self.assertIn("Join-Path $scriptDir '..\\..\\runtime\\SHA256SUMS'", installer)
        self.assertIn('function Assert-BundledRuntimeChecksums', installer)
        self.assertIn('Get-FileHash', installer)
        self.assertIn("Copy-Item -LiteralPath (Join-Path $scriptDir 'nginxctl.ps1')", installer)

    def test_installer_does_not_delete_nginx_when_source_is_destination(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')

        self.assertIn('$nginxSourceIsDestination', installer)
        self.assertIn(
            '$nginxSourceFull.Equals($nginxDestinationFull, [StringComparison]::OrdinalIgnoreCase)',
            installer,
        )
        self.assertIn('使用目标安装目录中已存在的包内 Nginx', installer)
        self.assertRegex(
            installer,
            r'if \(\$nginxSourceIsDestination\) \{[\s\S]*?\}\s*else \{[\s\S]*?Remove-Item',
        )

    def test_dedicated_nginx_controller_exposes_lifecycle_commands(self):
        controller = (ROOT / 'deploy/windows/nginxctl.ps1').read_text(encoding='utf-8')

        for action in ('status', 'start', 'stop', 'restart', 'reload', 'test', 'pause', 'resume'):
            self.assertIn(f"'{action}'", controller)
        self.assertIn('function Set-NginxMaintenance', controller)
        self.assertIn('return 503', controller)
        self.assertIn('Start-Service $GatewayService', controller)
        self.assertIn('Stop-Service $GatewayService', controller)

    def test_double_click_wrapper_offers_chinese_control_menu(self):
        wrapper = (ROOT / 'deploy/windows/nginx-control.cmd').read_text(encoding='utf-8')

        self.assertIn('nginxctl.ps1', wrapper)
        self.assertIn('%*', wrapper)
        self.assertIn('if not "%~1"=="" goto execute', wrapper)
        self.assertIn('启动 Nginx', wrapper)
        self.assertIn('暂停访问（维护模式）', wrapper)
        self.assertIn('恢复访问', wrapper)
        self.assertIn('set /p "choice=', wrapper)


class NginxSecurityHeadersTest(unittest.TestCase):
    """Nginx 的 add_header 不继承：location 内出现 add_header 会丢弃 server 级头。"""

    @property
    def config(self) -> str:
        return (ROOT / 'deploy/windows/templates/nginx.conf').read_text(encoding='utf-8')

    def blocks_with_add_header(self) -> list[str]:
        blocks = []
        for match in re.finditer(r'location[^{]*\{', self.config):
            start = match.end()
            depth = 1
            cursor = start
            while cursor < len(self.config) and depth:
                if self.config[cursor] == '{':
                    depth += 1
                elif self.config[cursor] == '}':
                    depth -= 1
                cursor += 1
            body = self.config[start:cursor - 1]
            if 'add_header' in body:
                blocks.append(match.group(0) + body)
        return blocks

    def test_every_location_declaring_add_header_reincludes_security_headers(self):
        blocks = self.blocks_with_add_header()
        self.assertTrue(blocks, '预期至少存在一个自行声明 add_header 的 location')
        for block in blocks:
            with self.subTest(block=block.split('{')[0].strip()):
                self.assertIn(
                    'config/nginx/security-headers.inc',
                    block,
                    'location 内声明 add_header 会覆盖继承，必须同时 include security-headers.inc',
                )

    def test_security_headers_have_a_single_definition_site(self):
        fragment = (ROOT / 'deploy/windows/templates/security-headers.inc').read_text(encoding='utf-8')

        for header in ('X-Content-Type-Options nosniff',
                       'X-Frame-Options SAMEORIGIN',
                       'Referrer-Policy strict-origin-when-cross-origin'):
            self.assertIn(header, fragment)
            self.assertNotIn(header, self.config, '安全头只允许在 security-headers.inc 中定义')
        self.assertIn('config/nginx/security-headers.inc', self.config, 'server 级必须 include 该片段')

    def test_installer_ships_the_fragment(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')

        self.assertIn("templates\\security-headers.inc", installer)
        self.assertIn("config\\nginx\\security-headers.inc", installer)

    def test_doctor_warns_when_deployed_config_predates_the_fragment(self):
        controller = (ROOT / 'deploy/windows/mrrctl.ps1').read_text(encoding='utf-8')
        doctor = controller.split('function Doctor {', 1)[1].split('\nswitch ($Command)', 1)[0]

        self.assertIn('Assert-SecurityHeaders', doctor)
        self.assertIn('function Assert-SecurityHeaders', controller)
        self.assertIn('security-headers\\.inc', controller)


if __name__ == '__main__':
    unittest.main()
