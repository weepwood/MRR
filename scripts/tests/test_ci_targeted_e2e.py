import unittest

from scripts.ci_targeted_e2e import select_specs, playwright_args


class TargetedMockE2eTest(unittest.TestCase):
    def test_selects_only_changed_mock_specs(self):
        paths = [
            "frontend-fantastic-admin/e2e/statistics-year-shortcut.spec.ts",
            "frontend-fantastic-admin/e2e/warmup.setup.ts",
            "frontend-fantastic-admin/src/views/statistics/index.vue",
            "backend-repo/test/foo.spec.ts",
        ]
        self.assertEqual(select_specs(paths), ["e2e/statistics-year-shortcut.spec.ts"])

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
