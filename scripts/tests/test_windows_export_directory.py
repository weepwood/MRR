import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class WindowsExportDirectoryTest(unittest.TestCase):
    """导出临时文件含完整病案，正式部署不得落入系统临时目录。"""

    def test_new_installation_default_is_in_the_preserved_production_template(self):
        service = (ROOT / 'deploy/windows/services/mrr-backend.xml').read_text(encoding='utf-8')
        template = (ROOT / 'deploy/windows/templates/application-prod.properties').read_text(encoding='utf-8')
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')
        self.assertNotIn('ARCHIVE_EXPORT_TEMP_DIRECTORY', service)
        self.assertIn('archive.export.temp-directory=${ARCHIVE_EXPORT_TEMP_DIRECTORY:{{MRR_URI_ROOT}}/data/exports}', template)
        self.assertIn("-Tokens $templateTokens -PreserveExisting", installer)
        self.assertIn("($PreserveExisting -or -not $Force)", installer)
        self.assertIn("Write-TemplateIfMissing `\n    -Source (Join-Path $scriptDir 'templates\\application-prod.properties')", installer)

    def test_application_reads_the_same_variable(self):
        properties = (ROOT / 'backend-repo/src/main/resources/application.properties').read_text(encoding='utf-8')
        self.assertIn('archive.export.temp-directory=${ARCHIVE_EXPORT_TEMP_DIRECTORY:', properties)

    def test_installer_creates_and_restricts_export_directory(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')
        self.assertIn("'data\\exports'", installer)
        self.assertIn("$exportsPath = Join-Path $Root 'data\\exports'", installer)
        self.assertIn('& icacls.exe $exportsPath /inheritance:r', installer)
        self.assertIn("& icacls.exe $exportsPath /grant:r '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F'", installer)

    def test_directory_exists_before_services_are_installed(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')
        self.assertLess(installer.index("$exportsPath = Join-Path $Root 'data\\exports'"),
                        installer.index('Install-WinSWService `'))


if __name__ == '__main__':
    unittest.main()
