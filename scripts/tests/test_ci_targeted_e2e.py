import unittest

from scripts.ci_targeted_e2e import select_specs, playwright_args, select_plan


class TargetedMockE2eTest(unittest.TestCase):
    def test_selects_only_changed_mock_specs(self):
        paths = [
            "frontend-fantastic-admin/e2e/statistics-year-shortcut.spec.ts",
            "frontend-fantastic-admin/e2e/warmup.setup.ts",
            "frontend-fantastic-admin/src/views/statistics/index.vue",
            "backend-repo/test/foo.spec.ts",
        ]
        self.assertEqual(select_specs(paths), ["e2e/statistics-year-shortcut.spec.ts"])

    def test_domain_mapping_keeps_existing_specs_and_related_coverage(self):
        full, specs = select_plan([
            'frontend-fantastic-admin/src/views/archive-boxes/index.vue',
            'frontend-fantastic-admin/e2e/monitoring-refresh-status.spec.ts',
        ])
        self.assertFalse(full)
        self.assertEqual(specs, ['e2e/list-workspace-ui.spec.ts',
                                 'e2e/monitoring-polling-performance.spec.ts',
                                 'e2e/monitoring-refresh-status.spec.ts'])

    def test_shared_or_unknown_critical_inputs_force_full_suite(self):
        for path in ('frontend-fantastic-admin/src/mock/api.ts',
                     'frontend-fantastic-admin/src/views/patients/components/PatientImportDialog.vue',
                     'frontend-fantastic-admin/e2e/helpers/local-visual-settings.ts',
                     'frontend-fantastic-admin/playwright.config.ts'):
            with self.subTest(path=path):
                full, _ = select_plan([path, 'frontend-fantastic-admin/e2e/list-workspace-ui.spec.ts'])
                self.assertTrue(full)

    def test_includes_related_monitoring_polling_regression(self):
        paths = ["frontend-fantastic-admin/e2e/monitoring-refresh-status.spec.ts"]
        self.assertEqual(select_specs(paths), [
            "e2e/monitoring-polling-performance.spec.ts",
            "e2e/monitoring-refresh-status.spec.ts",
        ])

    def test_rejects_unexpected_and_shell_like_paths(self):
        paths = [
            "frontend-fantastic-admin/e2e/../escape.spec.ts",
            "frontend-fantastic-admin/e2e/foo;bar.spec.ts",
            "frontend-fantastic-admin/e2e/-bad.spec.ts",
            "frontend-fantastic-admin/e2e/a.spec.ts",
            "frontend-fantastic-admin/e2e/a.spec.ts",
        ]
        self.assertEqual(select_specs(paths), ["e2e/a.spec.ts"])
        self.assertEqual(playwright_args(["e2e/a.spec.ts"]),
                         ["pnpm", "exec", "playwright", "test", "--project=chromium", "--workers=2", r"e2e/a\.spec\.ts$"])
        with self.assertRaises(ValueError):
            playwright_args(["../a.spec.ts"])
        with self.assertRaises(ValueError):
            playwright_args([])


if __name__ == "__main__":
    unittest.main()
