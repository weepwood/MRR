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
                     "frontend-fantastic-admin/vite.config.ts",
                     "frontend-fantastic-admin/.env.production", "frontend-fantastic-admin/.node-version",
                     "frontend-fantastic-admin/tsconfig.app.json", "frontend-fantastic-admin/postcss.config.js",
                     "frontend-fantastic-admin/uno.config.ts", "frontend-fantastic-admin/index.html",
                     "frontend-fantastic-admin/public/favicon.svg", "frontend-fantastic-admin/src/assets/test.svg",
                     "backend-repo/.mvn/wrapper/maven-wrapper.properties"):
            self.assertFalse(needs_release_build([path]), path)
        for path in ("scripts/ci_build_scope.py", "scripts/build_standalone_jar.py",
                     "scripts/embed_frontend_in_jar.py", "scripts/release_baseline.py"):
            self.assertTrue(needs_release_build([path]), path)

    def test_pr_summary_always_runs_and_checks_selected_jobs(self):
        workflow = (ROOT / ".github/workflows/quality-gate.yml").read_text(encoding="utf-8")
        trigger = workflow.split("permissions:", 1)[0]
        self.assertNotIn("paths:", trigger)
        summary = workflow.split("  pr-gate:", 1)[1]
        self.assertIn("if: always()", summary)
        self.assertIn("python -m scripts.ci_gate check", summary)
        release = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        self.assertNotIn("pull_request:", release.split('permissions:', 1)[0])

    def test_windows_and_standalone_share_the_verified_jar(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        self.assertIn('python scripts/build_standalone_jar.py build', workflow)
        self.assertIn('--source "${BACKEND_JAR}"', workflow)
        self.assertIn('steps.standalone.outputs.jar_name', workflow)
        self.assertIn('scripts.run_live_e2e', workflow)
        self.assertIn('--embedded', workflow)
        self.assertIn('scripts.release_candidate manifest', workflow)

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
