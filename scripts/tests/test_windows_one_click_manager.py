import os
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WINDOWS_DEPLOY = ROOT / 'deploy/windows'
UTF8_BOM = b'\xef\xbb\xbf'


class WindowsOneClickManagerTest(unittest.TestCase):
    def test_manager_reuses_existing_control_scripts(self):
        manager = (WINDOWS_DEPLOY / 'mrr-manager.ps1').read_text(encoding='utf-8-sig')

        self.assertIn('System.Windows.Forms', manager)
        self.assertIn("Join-Path $Root 'ops\\mrrctl.ps1'", manager)
        self.assertIn("Join-Path $Root 'ops\\nginxctl.ps1'", manager)
        self.assertIn("@('start', 'all')", manager)
        self.assertIn("@('stop', 'all')", manager)
        self.assertIn("@('restart', 'all')", manager)
        self.assertIn("@('maintenance', 'on'", manager)
        self.assertIn("@('maintenance', 'off')", manager)
        self.assertIn("@('deploy', $dialog.FileName)", manager)
        self.assertIn("@('doctor')", manager)
        self.assertIn("@('versions')", manager)
        self.assertIn("@('test')", manager)
        self.assertIn("@('reload')", manager)

    def test_background_invocation_splats_each_argument(self):
        manager = (WINDOWS_DEPLOY / 'mrr-manager.ps1').read_text(encoding='utf-8-sig')

        self.assertIn('`$invokeArguments = @($literalArguments)', manager)
        self.assertIn('& $scriptLiteral @invokeArguments', manager)
        self.assertNotIn('& $scriptLiteral @($literalArguments)', manager)

    def test_manager_has_uac_double_click_entry_and_self_test(self):
        manager = (WINDOWS_DEPLOY / 'mrr-manager.ps1').read_text(encoding='utf-8-sig')
        wrapper = (WINDOWS_DEPLOY / 'MRR-Manager.cmd').read_text(encoding='utf-8')

        self.assertIn('-Verb RunAs', manager)
        self.assertIn('-STA', manager)
        self.assertIn('[switch]$SelfTest', manager)
        self.assertIn("'MRR 一键管理中心'", manager)
        self.assertIn('mrr-manager.ps1', wrapper)
        self.assertIn('-STA', wrapper)
        self.assertIn('%*', wrapper)
        self.assertIn('if /I "%~1"=="-SelfTest"', wrapper)

    def test_windows_powershell_scripts_use_utf8_bom(self):
        scripts = sorted(WINDOWS_DEPLOY.rglob('*.ps1'))
        self.assertGreaterEqual(len(scripts), 4)

        for script in scripts:
            raw = script.read_bytes()
            self.assertTrue(
                raw.startswith(UTF8_BOM),
                f'{script.relative_to(ROOT)} must use UTF-8 with BOM for Windows PowerShell 5.1',
            )
            raw.decode('utf-8-sig')

    def test_cmd_launchers_use_utf8_without_bom_and_switch_code_page(self):
        launchers = sorted(WINDOWS_DEPLOY.glob('*.cmd'))
        self.assertGreaterEqual(len(launchers), 2)

        for launcher in launchers:
            raw = launcher.read_bytes()
            self.assertFalse(
                raw.startswith(UTF8_BOM),
                f'{launcher.relative_to(ROOT)} must not start with a BOM because cmd.exe would treat it as command text',
            )
            text = raw.decode('utf-8')
            lines = [line.strip() for line in text.splitlines() if line.strip()]
            self.assertEqual(lines[0].lower(), '@echo off')
            chcp_index = next((index for index, line in enumerate(lines) if line.lower().startswith('chcp 65001')), None)
            self.assertIsNotNone(chcp_index, f'{launcher.relative_to(ROOT)} must switch to UTF-8 code page')
            first_non_ascii = next(
                (index for index, line in enumerate(lines) if any(ord(char) > 127 for char in line)),
                None,
            )
            if first_non_ascii is not None:
                self.assertLess(chcp_index, first_non_ascii)

    @unittest.skipUnless(os.name == 'nt', 'CMD launcher integration test requires Windows')
    def test_cmd_launcher_runs_manager_self_test_on_windows(self):
        wrapper = WINDOWS_DEPLOY / 'MRR-Manager.cmd'
        completed = subprocess.run(
            [
                os.environ.get('ComSpec', 'cmd.exe'),
                '/d',
                '/c',
                str(wrapper),
                '-SelfTest',
                '-Root',
                str(ROOT),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding='utf-8',
            errors='replace',
            check=False,
        )
        self.assertEqual(
            completed.returncode,
            0,
            'CMD launcher self-test failed:\n'
            f'stdout:\n{completed.stdout}\n'
            f'stderr:\n{completed.stderr}',
        )

    def test_installer_copies_manager_to_ops(self):
        installer = (WINDOWS_DEPLOY / 'install.ps1').read_text(encoding='utf-8-sig')

        self.assertIn("Join-Path $scriptDir 'mrr-manager.ps1'", installer)
        self.assertIn("Join-Path $Root 'ops\\mrr-manager.ps1'", installer)
        self.assertIn("Join-Path $scriptDir 'MRR-Manager.cmd'", installer)
        self.assertIn("Join-Path $Root 'ops\\MRR-Manager.cmd'", installer)
        self.assertIn("Join-Path $Root 'ops\\MRR-管理中心.cmd'", installer)

    def test_release_workflow_packages_entire_windows_deployment_directory(self):
        workflow = (ROOT / '.github/workflows/windows-release-package.yml').read_text(encoding='utf-8')

        self.assertIn('cp -a deploy/windows/. "${PACKAGE_DIR}/deploy/windows/"', workflow)
        self.assertIn('find backend frontend docs deploy runtime', workflow)

    def test_start_all_restores_access(self):
        controller = (WINDOWS_DEPLOY / 'mrrctl.ps1').read_text(encoding='utf-8-sig')

        self.assertIn("if ($Name -eq 'all') { Set-Maintenance $false }", controller)


if __name__ == '__main__':
    unittest.main()
