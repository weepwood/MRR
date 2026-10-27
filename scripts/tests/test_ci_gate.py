import unittest
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from unittest.mock import patch

from scripts.ci_gate import classify_paths, validate_results, successful_base, main


class CiGateTest(unittest.TestCase):
    def test_main_cli_includes_application_changes_before_cancelled_run_even_when_latest_commit_is_docs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                return subprocess.check_output(['git', '-c', 'commit.gpgsign=false',
                                                '-c', 'core.hooksPath=' + str(root / 'empty-hooks'),
                                                *args], cwd=root, text=True).strip()

            git('init', '-q', '-b', 'main')
            git('config', 'user.name', 'Synthetic CI')
            git('config', 'user.email', 'synthetic@example.invalid')
            (root / 'README.md').write_text('initial', encoding='utf-8')
            git('add', '.')
            git('commit', '-qm', 'initial')
            base = git('rev-parse', 'HEAD')
            frontend = root / 'frontend-fantastic-admin/src/utils'
            frontend.mkdir(parents=True)
            (frontend / 'date.ts').write_text('export const synthetic = 1', encoding='utf-8')
            git('add', '.')
            git('commit', '-qm', 'application')
            cancelled = git('rev-parse', 'HEAD')
            (root / 'README.md').write_text('latest change is only docs', encoding='utf-8')
            git('add', '.')
            git('commit', '-qm', 'docs')
            runs = root / 'runs.json'
            runs.write_text(json.dumps({'workflow_runs': [
                dict(head_sha=cancelled, conclusion='cancelled', event='push', head_branch='main'),
                dict(head_sha=base, conclusion='success', event='push', head_branch='main'),
            ]}), encoding='utf-8')
            output = root / 'outputs.txt'
            previous = Path.cwd()
            try:
                os.chdir(root)
                with patch.object(sys, 'argv', ['ci_gate', 'scope', '--mode', 'integration',
                                               '--runs', str(runs), '--output', str(output)]):
                    main()
            finally:
                os.chdir(previous)
            self.assertIn('integration=true', output.read_text(encoding='utf-8'))
            self.assertIn('base=' + base, output.read_text(encoding='utf-8'))

    def test_docs_and_version_do_not_build_applications(self):
        for path in ('README.md', 'vitepress-doc/user-guide/index.md', 'VERSION', 'release-notes/1.0.0.md'):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertFalse(any(scope[key] for key in ('frontend', 'backend', 'live', 'recovery', 'release')))

    def test_small_frontend_change_only_runs_fast_frontend_checks(self):
        for path in ('frontend-fantastic-admin/src/views/users/index.vue',
                     'frontend-fantastic-admin/src/assets/styles/globals.css'):
            scope = classify_paths([path])
            self.assertTrue(scope['frontend'])
            self.assertFalse(any(scope[key] for key in ('build', 'backend', 'live', 'postgres', 'windows', 'recovery', 'release')))

    def test_build_dependencies_require_production_build(self):
        scope = classify_paths(['frontend-fantastic-admin/pnpm-lock.yaml'])
        self.assertTrue(scope['frontend'])
        self.assertTrue(scope['build'])

    def test_authentication_and_sql_cannot_hide_as_ordinary_business_changes(self):
        for path in ('backend-repo/src/main/java/com/zjcxph/imgapi/service/impl/AuthServiceImpl.java',
                     'backend-repo/src/main/java/com/zjcxph/imgapi/mapper/ScanMapper.java',
                     'backend-repo/src/main/java/com/zjcxph/imgapi/service/importer/TabularImportFileReader.java'):
            scope = classify_paths([path])
            self.assertTrue(scope['postgres'], path)
        self.assertTrue(classify_paths(['frontend-fantastic-admin/src/router/guards.ts'])['live'])

    def test_existing_identity_entrypoints_require_real_checks(self):
        for path in ('backend-repo/src/main/java/com/zjcxph/imgapi/utils/JwtUtil.java',
                     'backend-repo/src/main/java/com/zjcxph/imgapi/controller/UserController.java',
                     'backend-repo/src/main/java/com/zjcxph/imgapi/utils/PasswordUtil.java',
                     'frontend-fantastic-admin/src/utils/auth-storage.ts',
                     'frontend-fantastic-admin/src/utils/composables/useAuth.ts',
                     'frontend-fantastic-admin/src/utils/permission.ts',
                     'frontend-fantastic-admin/src/utils/session.ts',
                     'frontend-fantastic-admin/src/utils/unauthorized-session.ts',
                     'frontend-fantastic-admin/src/views/patients/components/PatientImportDialog.vue',
                     'frontend-fantastic-admin/src/views/data-exchange/components/DatasetImportPanel.vue',
                     'frontend-fantastic-admin/src/views/password/change-required.vue',
                     'frontend-fantastic-admin/src/ui/components/FaAuth/index.vue'):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope['integration'])

    def test_login_image_and_generated_import_types_do_not_trigger_real_identity_checks(self):
        for path in ('frontend-fantastic-admin/src/assets/images/login-banner.png',
                     'frontend-fantastic-admin/src/types/auto-imports.d.ts'):
            scope = classify_paths([path])
            self.assertTrue(scope['frontend'])
            self.assertFalse(scope['integration'])

    def test_packaging_inputs_require_complete_candidate_validation(self):
        for path in ('scripts/build_standalone_jar.py', 'scripts/embed_frontend_in_jar.py',
                     'scripts/release_baseline.py', 'scripts/tests/test_build_standalone_jar.py',
                     'scripts/ci_build_scope.py'):
            with self.subTest(path=path):
                self.assertTrue(classify_paths([path])['release'])

    def test_recovery_changes_use_windows_rehearsal_but_other_scripts_do_not(self):
        self.assertTrue(classify_paths(['deploy/windows/restore-database.ps1'])['recovery'])
        scope = classify_paths(['deploy/windows/mrr-manager.ps1'])
        self.assertTrue(scope['windows'])
        self.assertFalse(scope['recovery'])

    def test_main_application_changes_verify_the_combined_production_application(self):
        scope = classify_paths(['backend-repo/src/main/java/example/Example.java'], mode='integration')
        self.assertTrue(all(scope[key] for key in ('integration', 'build', 'postgres', 'live')))
        self.assertFalse(scope['frontend'])
        self.assertFalse(scope['backend'])
        self.assertFalse(scope['release'])

    def test_release_control_changes_require_full_dry_run(self):
        self.assertTrue(classify_paths(['.github/workflows/release-on-version-bump.yml'])['release'])

    def test_gate_rejects_failure_cancelled_unexpected_skip_and_missing_job(self):
        for result in ('failure', 'cancelled', 'skipped', None):
            with self.subTest(result=result):
                with self.assertRaises(ValueError):
                    validate_results({'frontend': True}, {'scope': 'success', 'frontend': result})
        with self.assertRaises(ValueError):
            validate_results({'frontend': False}, {'scope': 'failure', 'frontend': 'skipped'})

    def test_gate_accepts_only_intentionally_unselected_skips(self):
        validate_results({'frontend': True, 'backend': False},
                         {'scope': 'success', 'frontend': 'success', 'backend': 'skipped'})
        with self.assertRaises(ValueError):
            validate_results({'frontend': 'false'}, {'scope': 'success', 'frontend': 'skipped'})

    def test_cumulative_main_base_ignores_cancelled_failed_and_unrelated_runs(self):
        runs = [
            {'head_sha': 'new', 'conclusion': 'cancelled', 'event': 'push', 'head_branch': 'main'},
            {'head_sha': 'bad', 'conclusion': 'failure', 'event': 'push', 'head_branch': 'main'},
            {'head_sha': 'branch', 'conclusion': 'success', 'event': 'pull_request', 'head_branch': 'feature'},
            {'head_sha': 'verified', 'conclusion': 'success', 'event': 'push', 'head_branch': 'main'},
        ]
        self.assertEqual('verified', successful_base(runs))
        self.assertIsNone(successful_base(runs[:3]))


if __name__ == '__main__':
    unittest.main()
