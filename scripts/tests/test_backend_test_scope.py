import unittest

from scripts.backend_test_scope import classify_paths


class BackendTestScopeTest(unittest.TestCase):

    def test_frontend_only_skips_backend(self):
        scope = classify_paths([
            "frontend-fantastic-admin/src/views/users/index.vue",
            "frontend-fantastic-admin/src/components/MrrTableActions/index.vue",
        ])

        self.assertFalse(scope.backend_changed)
        self.assertFalse(scope.integration_changed)
        self.assertEqual(scope.reason, "frontend-or-docs-only")

    def test_regular_backend_change_runs_unit_tests_only(self):
        scope = classify_paths([
            "backend-repo/src/main/java/com/zjcxph/imgapi/controller/AuthController.java",
            "backend-repo/src/test/java/com/zjcxph/imgapi/controller/AuthControllerTest.java",
        ])

        self.assertTrue(scope.backend_changed)
        self.assertFalse(scope.integration_changed)
        self.assertEqual(scope.reason, "backend-unit-change")

    def test_mapper_and_migration_changes_run_integration_tests(self):
        for path in (
            "backend-repo/src/main/java/com/zjcxph/imgapi/mapper/ScanMapper.java",
            "backend-repo/src/main/resources/mapper/ScanMapper.xml",
            "backend-repo/src/main/resources/db/migration/V20260723090000__example.sql",
            "backend-repo/src/test/java/com/zjcxph/imgapi/integration/mapper/ScanMapperPostgresqlIT.java",
            "backend-repo/pom.xml",
        ):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope.backend_changed)
                self.assertTrue(scope.integration_changed)
                self.assertEqual(scope.reason, "database-or-integration-change")

    def test_release_change_runs_full_backend_verification(self):
        for path in ("VERSION", "release-baseline.json", "release-notes/0.6.6.md"):
            with self.subTest(path=path):
                scope = classify_paths([path])
                self.assertTrue(scope.backend_changed)
                self.assertTrue(scope.integration_changed)
                self.assertEqual(scope.reason, "release-change")

    def test_gate_control_change_tests_the_gate_itself(self):
        scope = classify_paths([".github/workflows/quality-gate.yml"])

        self.assertTrue(scope.backend_changed)
        self.assertTrue(scope.integration_changed)
        self.assertEqual(scope.reason, "backend-gate-control")

    def test_empty_path_list_is_safe(self):
        scope = classify_paths([])

        self.assertFalse(scope.backend_changed)
        self.assertFalse(scope.integration_changed)
        self.assertEqual(scope.reason, "no-changed-files")


if __name__ == "__main__":
    unittest.main()
