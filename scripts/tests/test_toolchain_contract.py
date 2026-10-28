from pathlib import Path
import shutil
import tempfile
import unittest
import subprocess
import os

from scripts.engineering_preflight import configuration_problems

ROOT = Path(__file__).resolve().parents[2]


class ToolchainContractTest(unittest.TestCase):
    def test_package_manager_guard_uses_no_network_and_rejects_other_installers(self):
        script = ROOT / 'frontend-fantastic-admin/scripts/require-package-manager.mjs'
        for agent, expected in (('pnpm/10.33.0 npm/? node/v24', 0), ('npm/11.0.0', 1), ('pnpm/12.0.0', 1)):
            result = subprocess.run(['node', str(script)], env=os.environ | {'npm_config_user_agent': agent}, capture_output=True)
            self.assertEqual(expected, result.returncode)

    def test_repository_configuration_agrees(self):
        self.assertEqual([], configuration_problems(ROOT))

    def test_rejects_node_image_and_global_maven_drift(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ('frontend-fantastic-admin', 'backend-repo', '.github/workflows'):
                (root / name).mkdir(parents=True)
            for relative in ('frontend-fantastic-admin/.node-version',
                             'frontend-fantastic-admin/package.json', 'backend-repo/Dockerfile',
                             'frontend-fantastic-admin/Dockerfile'):
                shutil.copy2(ROOT / relative, root / relative)
            (root / 'backend-repo/Dockerfile').write_text('FROM node:22-alpine\n', encoding='utf-8')
            (root / '.github/workflows/broken.yml').write_text('run: mvn test\n', encoding='utf-8')
            problems = configuration_problems(root)
            self.assertTrue(any('Node' in problem for problem in problems))
            self.assertTrue(any('Wrapper' in problem for problem in problems))

    def test_frontend_install_copies_workspace_configuration_first(self):
        source = (ROOT / 'backend-repo/Dockerfile').read_text(encoding='utf-8')
        install = source.index('RUN pnpm install --frozen-lockfile')
        self.assertIn('frontend-fantastic-admin/pnpm-workspace.yaml', source[:install])
        self.assertNotIn('frontend-fantastic-admin/.npmrc', source)

    def test_source_archive_requires_explicit_identity(self):
        source = (ROOT / 'backend-repo/Dockerfile').read_text(encoding='utf-8')
        self.assertIn('ARG SOURCE_REVISION', source)
        self.assertIn('git.commit.id=', source)
        self.assertLess(source.index('SOURCE_REVISION must be a full commit SHA'),
                        source.index('RUN pnpm install'))
