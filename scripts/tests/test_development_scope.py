from pathlib import Path
import unittest

from scripts.ci_gate import classify_paths, validate_results
from scripts.smoke_dev import source_revision

ROOT = Path(__file__).resolve().parents[2]


class DevelopmentScopeTest(unittest.TestCase):
    def test_full_release_does_not_replace_native_windows_development_checks(self):
        scope = classify_paths(['scripts/dev.py', '.github/workflows/quality-gate.yml'])
        self.assertTrue(scope['release'])
        self.assertTrue(scope['windows'])
        self.assertTrue(scope['development'])

    def test_build_inputs_and_lifecycle_select_smoke(self):
        for path in ('backend-repo/Dockerfile', 'frontend-fantastic-admin/Dockerfile',
                     'docker-compose.yml', 'scripts/dev.py', 'scripts/dev.ps1',
                     'scripts/smoke_dev.py', 'frontend-fantastic-admin/pnpm-workspace.yaml',
                     'frontend-fantastic-admin/nginx.conf', 'scripts/run_live_e2e.py',
                     'backend-repo/.mvn/wrapper/maven-wrapper.properties'):
            self.assertTrue(classify_paths([path])['development'], path)
        self.assertFalse(classify_paths(['README.md'])['development'])

    def test_selected_smoke_cannot_be_skipped(self):
        with self.assertRaises(ValueError):
            validate_results({'development': True}, {'scope': 'success', 'development': 'skipped'})

    def test_source_archive_identity_cannot_be_missing_or_injected(self):
        self.assertEqual('a' * 40, source_revision('a' * 40))
        for value in ('', 'main', 'a' * 39, 'a' * 40 + '\n', 'a' * 39 + ';'):
            with self.assertRaises(ValueError):
                source_revision(value)

    def test_private_development_state_is_not_a_build_input(self):
        self.assertIn('.mrr-dev', (ROOT / '.dockerignore').read_text())
