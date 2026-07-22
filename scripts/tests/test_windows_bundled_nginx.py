import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class WindowsBundledNginxTest(unittest.TestCase):
    def test_release_workflow_bundles_pinned_nginx_and_winsw(self):
        workflow = (ROOT / '.github/workflows/windows-release-package.yml').read_text(encoding='utf-8')

        self.assertRegex(workflow, r'NGINX_VERSION:\s*[0-9]+\.[0-9]+\.[0-9]+')
        self.assertRegex(workflow, r'WINSW_VERSION:\s*[0-9]+\.[0-9]+\.[0-9]+')
        self.assertIn('nginx/Windows-', workflow)
        self.assertIn('WinSW-x64.exe', workflow)
        self.assertIn('runtime/nginx/nginx.exe', workflow)
        self.assertIn('runtime/winsw/WinSW-x64.exe', workflow)
        self.assertIn('runtime/SHA256SUMS', workflow)

    def test_installer_uses_packaged_runtime_by_default(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')

        self.assertNotRegex(installer, r'\[Parameter\(Mandatory\s*=\s*\$true\)\]\s*\[string\]\$NginxPath')
        self.assertNotRegex(installer, r'\[Parameter\(Mandatory\s*=\s*\$true\)\]\s*\[string\]\$WinSWPath')
        self.assertIn("Join-Path $scriptDir '..\\..\\runtime\\nginx'", installer)
        self.assertIn("Join-Path $scriptDir '..\\..\\runtime\\winsw\\WinSW-x64.exe'", installer)
        self.assertIn("Copy-Item -LiteralPath (Join-Path $scriptDir 'nginxctl.ps1')", installer)

    def test_dedicated_nginx_controller_exposes_lifecycle_commands(self):
        controller = (ROOT / 'deploy/windows/nginxctl.ps1').read_text(encoding='utf-8')

        for action in ('status', 'start', 'stop', 'restart', 'reload', 'test', 'pause', 'resume'):
            self.assertIn(f"'{action}'", controller)
        self.assertIn('function Set-NginxMaintenance', controller)
        self.assertIn("return 503", controller)
        self.assertIn("Start-Service $GatewayService", controller)
        self.assertIn("Stop-Service $GatewayService", controller)

    def test_double_click_wrapper_uses_nginx_controller(self):
        wrapper = (ROOT / 'deploy/windows/nginx-control.cmd').read_text(encoding='utf-8')

        self.assertIn('nginxctl.ps1', wrapper)
        self.assertIn('%*', wrapper)


if __name__ == '__main__':
    unittest.main()
