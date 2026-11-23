import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TEMPLATE = ROOT / 'deploy/windows/templates/application-secrets.properties'
INSTALLER = ROOT / 'deploy/windows/install.ps1'


class WindowsSecretBootstrapTest(unittest.TestCase):
    """安装脚本必须替换应用内部密钥的占位值，且占位值必须与模板保持一致。"""

    def test_secrets_directory_is_restricted_before_writing_configuration(self):
        installer = (ROOT / 'deploy/windows/install.ps1').read_text(encoding='utf-8')
        self.assertLess(installer.index('& icacls.exe $secretsPath /inheritance:r'),
                        installer.index('Initialize-GeneratedSecrets -Path'))
        self.assertRegex(installer, r'icacls\.exe \$secretsPath /inheritance:r \| Out-Null\s+if \(\$LASTEXITCODE -ne 0\)')

    def test_installer_generates_secrets_with_csprng(self):
        installer = INSTALLER.read_text(encoding='utf-8')

        self.assertIn('function New-RandomSecret', installer)
        self.assertIn('[System.Security.Cryptography.RandomNumberGenerator]::Create()', installer)
        self.assertIn('function Initialize-GeneratedSecrets', installer)
        self.assertRegex(
            installer,
            r"Initialize-GeneratedSecrets -Path \(Join-Path \$Root 'secrets\\application-secrets\.properties'\)",
        )
        self.assertNotIn('Get-Random', installer, 'Get-Random 不是密码学安全随机数')

    def test_every_internal_placeholder_handled_by_installer_exists_in_template(self):
        installer = INSTALLER.read_text(encoding='utf-8')
        template = TEMPLATE.read_text(encoding='utf-8')
        pairs = re.findall(r"'([\w.\[\]]+)'\s*=\s*@\('(CHANGE_ME_[A-Z0-9_]+)'", installer)

        self.assertEqual(
            {key for key, _ in pairs},
            {'JWT_SECRET_KEY', 'aes.secret.key', 'mrr.integration.clients[0].secret'},
        )
        for key, placeholder in pairs:
            with self.subTest(key=key):
                self.assertIn(f'{key}={placeholder}', template, '安装脚本与模板的占位值已漂移')

    def test_generation_runs_after_template_is_written(self):
        installer = INSTALLER.read_text(encoding='utf-8')
        write_index = installer.index("templates\\application-secrets.properties")
        generate_index = installer.index('Initialize-GeneratedSecrets -Path')
        self.assertLess(write_index, generate_index)


if __name__ == '__main__':
    unittest.main()
