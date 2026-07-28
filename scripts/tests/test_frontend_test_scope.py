import unittest

from scripts.frontend_test_scope import classify_paths


class FrontendTestScopeTest(unittest.TestCase):

    def test_backend_only_skips_frontend(self):
        scope = classify_paths([
            "backend-repo/src/main/java/com/zjcxph/imgapi/controller/AuthController.java",
            "backend-repo/src/test/java/com/zjcxph/imgapi/controller/AuthControllerTest.java",
        ])

        self.assertFalse(scope.frontend_changed)
        self.assertFalse(scope.e2e_changed)
        self.assertEqual(scope.reason, "backend-or-docs-only")

    def test_frontend_utility_change_runs_unit_gate_only(self):
        scope = classify_paths([
            "frontend-fantastic-admin/src/utils/date.ts",
            "frontend-fantastic-admin/src/utils/date.test.ts",
        ])

        self.assertTrue(scope.frontend_changed)
        self.assertFalse(scope.e2e_changed)
        self.assertEqual(scope.reason, "frontend-unit-change")

    def test_user_flow_change_runs_e2e(self):
        for path in (
            "frontend-fantastic-admin/src/views/login.vue",
            "frontend-fantastic-admin/src/router/guards.ts",
            "frontend-fantastic-admin/src/api/index.ts",
            "frontend-fantastic-admin/e2e/login-layout.spec.ts",
            "frontend-fantastic-admin/playwright.config.ts",
            "frontend-fantastic-admin/package.json",
        ):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope.frontend_changed)
                self.assertTrue(scope.e2e_changed)
                self.assertEqual(scope.reason, "frontend-user-flow-change")

    def test_release_change_runs_full_frontend_verification(self):
        for path in ("VERSION", "release-baseline.json", "release-notes/0.7.5.md"):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope.frontend_changed)
                self.assertTrue(scope.e2e_changed)
                self.assertEqual(scope.reason, "release-change")

    def test_gate_control_change_tests_the_gate_itself(self):
        for path in (
            ".github/workflows/quality-gate.yml",
            "quality/frontend-coverage-baseline.json",
            "scripts/vitest_coverage.py",
            "scripts/tests/test_vitest_coverage.py",
        ):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope.frontend_changed)
                self.assertTrue(scope.e2e_changed)
                self.assertEqual(scope.reason, "frontend-gate-control")

    def test_empty_path_list_is_safe(self):
        scope = classify_paths([])

        self.assertFalse(scope.frontend_changed)
        self.assertFalse(scope.e2e_changed)
        self.assertEqual(scope.reason, "no-changed-files")


if __name__ == "__main__":
    unittest.main()
