from pathlib import Path
import unittest

from scripts.ci_build_scope import needs_release_build

ROOT = Path(__file__).resolve().parents[2]


class CiBuildReuseTest(unittest.TestCase):
    def test_ordinary_application_changes_use_quality_gate_not_full_offline_builds(self):
        for path in ("backend-repo/src/main/java/example/Example.java",
                     "frontend-fantastic-admin/src/views/example/index.vue"):
            self.assertFalse(needs_release_build([path]))
        for path in ("backend-repo/pom.xml", "deploy/windows/install.ps1",
                     "scripts/build_standalone_jar.py", "frontend-fantastic-admin/vite.config.ts",
                     "frontend-fantastic-admin/.env.production", "frontend-fantastic-admin/.node-version",
                     "frontend-fantastic-admin/tsconfig.app.json", "frontend-fantastic-admin/postcss.config.js",
                     "frontend-fantastic-admin/uno.config.ts", "frontend-fantastic-admin/index.html",
                     "frontend-fantastic-admin/public/favicon.svg", "frontend-fantastic-admin/src/assets/test.svg",
                     "scripts/ci_build_scope.py", "backend-repo/.mvn/wrapper/maven-wrapper.properties"):
            self.assertTrue(needs_release_build([path]), path)

    def test_release_summary_always_runs_and_checks_selected_build(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        trigger = workflow.split("permissions:", 1)[0]
        self.assertNotIn("paths:", trigger)
        summary = workflow.split("  windows-package-gate:", 1)[1]
        self.assertIn("if: always()", summary)
        self.assertIn('test "${SCOPE_RESULT}" = success', summary)
        self.assertIn('test "${BUILD_RESULT}" = success', summary)

    def test_windows_and_standalone_share_the_verified_jar(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        self.assertIn('python scripts/build_standalone_jar.py build', workflow)
        self.assertIn('--source "${BACKEND_JAR}"', workflow)
        self.assertIn('steps.standalone.outputs.jar_name', workflow)
        self.assertIn('needs.build.outputs.standalone_name', workflow)
        self.assertIn('"${ASSETS[@]}"', workflow)

    def test_legacy_standalone_and_normalization_workflows_are_manual(self):
        for name in ("standalone-jar-release.yml", "backend-normalization-check.yml"):
            trigger = (ROOT / ".github/workflows" / name).read_text(encoding="utf-8").split("permissions:", 1)[0]
            self.assertNotIn("pull_request:", trigger)
            self.assertNotIn("push:", trigger)
            self.assertIn("workflow_dispatch:", trigger)

    def test_monitoring_does_not_duplicate_application_compilation(self):
        workflow = (ROOT / ".github/workflows/monitoring-validation.yml").read_text(encoding="utf-8")
        self.assertNotIn("mvn ", workflow)
        self.assertNotIn("pnpm build", workflow)
        self.assertIn("check config", workflow)
