import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class WindowsOneClickManagerTest(unittest.TestCase):
    def test_manager_reuses_existing_control_scripts(self):
        manager = (ROOT / 'deploy/windows/mrr-manager.ps1').read_text(encoding='utf-8')

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

    def test_manager_has_uac_and_double_click_entry(self):
        manager = (ROOT / 'deploy/windows/mrr-manager.ps1').read_text(encoding='utf-8')
        wrapper = (ROOT / 'deploy/windows/MRR-管理中心.cmd').read_text(encoding='utf-8')

        self.assertIn('-Verb RunAs', manager)
        self.assertIn('-STA', manager)
        self.assertIn('mrr-manager.ps1', wrapper)
        self.assertIn('-STA', wrapper)

    def test_installer_copies_manager_to_ops(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')

        self.assertIn("Join-Path $scriptDir 'mrr-manager.ps1'", installer)
        self.assertIn("Join-Path $Root 'ops\\mrr-manager.ps1'", installer)
        self.assertIn("Join-Path $scriptDir 'MRR-管理中心.cmd'", installer)
        self.assertIn("Join-Path $Root 'ops\\MRR-管理中心.cmd'", installer)

    def test_release_workflow_packages_entire_windows_deployment_directory(self):
        workflow = (ROOT / '.github/workflows/windows-release-package.yml').read_text(encoding='utf-8')

        self.assertIn('cp -a deploy/windows/. "${PACKAGE_DIR}/deploy/windows/"', workflow)
        self.assertIn('find backend frontend docs deploy runtime', workflow)

    def test_start_all_restores_access(self):
        controller = (ROOT / 'deploy/windows/mrrctl.ps1').read_text(encoding='utf-8')

        self.assertIn("if ($Name -eq 'all') { Set-Maintenance $false }", controller)


if __name__ == '__main__':
    unittest.main()
