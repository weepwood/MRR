from pathlib import Path
import re
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

    def test_pull_requests_defer_browser_e2e_to_main_and_release_checks(self):
        quality = (ROOT / ".github/workflows/quality-gate.yml").read_text(encoding="utf-8")
        self.assertNotIn("pnpm test:e2e", quality)
        self.assertIn("live_checks: ${{ github.event_name != 'pull_request' }}", quality)

        package = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        mock_e2e = package.split("- name: Full mock-backed browser tests", 1)[1].split("- name:", 1)[0]
        self.assertIn("if: inputs.full", mock_e2e)
        self.assertIn("pnpm test:e2e", mock_e2e)
        real_e2e = package.split("- name: Real login, import, query and refusal", 1)[1]
        self.assertIn("if: inputs.full || inputs.live_checks", real_e2e)

    def test_pages_demo_pr_job_is_skipped_for_style_only_changes(self):
        workflow = (ROOT / ".github/workflows/deploy-pages-demo.yml").read_text(encoding="utf-8")
        self.assertIn("pages_demo_smoke: ${{ steps.select.outputs.pages_demo_smoke }}", workflow)
        self.assertIn("if: always() && (github.event_name != 'pull_request' || needs.scope.outputs.pages_demo_smoke != 'false')",
                      workflow)
        self.assertIn("python3 -m scripts.ci_gate scope --mode pr", workflow)
        scope = workflow.split("  build:", 1)[0]
        self.assertIn("fetch-depth: 0", scope)
        self.assertNotIn("ref: ${{ github.event.pull_request.head.sha }}", scope)

    def test_main_and_manual_scope_have_distinct_verification_modes(self):
        quality = (ROOT / ".github/workflows/quality-gate.yml").read_text(encoding="utf-8")
        self.assertIn("--mode main --runs main-runs.json", quality)
        self.assertIn("--mode integration --output", quality)
        self.assertNotIn("--mode integration --runs main-runs.json", quality)
        read = quality.split("- name: Read last successful main quality gate", 1)[1].split("- name:", 1)[0]
        self.assertIn("if: github.event_name == 'push'", read)

    def test_pr_integration_skips_frontend_only_when_nothing_consumes_it(self):
        quality = (ROOT / ".github/workflows/quality-gate.yml").read_text(encoding="utf-8")
        self.assertIn("integration_frontend: ${{ steps.scope.outputs.integration_frontend }}", quality)
        integration = quality.split("  integration:", 1)[1].split("\n  windows:", 1)[0]
        self.assertIn("frontend_checks: ${{ github.event_name != 'pull_request' || needs.scope.outputs.integration_frontend == 'true' }}",
                      integration)

        package = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        triggers = package.split("permissions:", 1)[0]
        # workflow_call 与 workflow_dispatch 两种入口都必须默认执行前端，未显式传入时不能跳过
        self.assertEqual(2, triggers.count("frontend_checks:"))
        self.assertEqual(2, len(re.findall(r"frontend_checks:\n\s+type: boolean\n\s+default: true\n", triggers)))
        frontend = package.split("- name: Test, build and embed frontend", 1)[1].split("- name:", 1)[0]
        self.assertIn("if: inputs.full || inputs.live_checks || inputs.frontend_checks", frontend)

    def test_main_integration_skips_jacoco_but_full_candidate_keeps_coverage(self):
        package = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        backend = package.split("- name: Test and package backend once", 1)[1].split(
            "- name: Verify backend build identity", 1
        )[0]
        self.assertIn('if [ "${FULL}" != true ]; then', backend)
        self.assertIn("jacoco_args+=(-Djacoco.skip=true)", backend)
        self.assertIn('"${jacoco_args[@]}"', backend)
        self.assertIn('if [ "${FULL}" = true ]; then', backend)
        self.assertIn("jacoco_coverage.py", backend)

    def test_windows_and_standalone_share_the_verified_jar(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        self.assertIn('python scripts/build_standalone_jar.py build', workflow)
        self.assertIn('--source "${BACKEND_JAR}"', workflow)
        self.assertIn('steps.standalone.outputs.jar_name', workflow)
        self.assertIn('scripts.run_live_e2e', workflow)
        self.assertIn('--embedded', workflow)
        self.assertIn('scripts.release_candidate manifest', workflow)

    def test_live_only_checks_install_their_browser_before_running(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        setup_name = "- name: Install Chromium for selected browser checks"
        self.assertIn(setup_name, workflow)
        setup = workflow.split(setup_name, 1)[1].split("- name:", 1)[0]
        self.assertIn("if: inputs.full || inputs.live_checks", setup)
        self.assertIn("working-directory: frontend-fantastic-admin", setup)
        self.assertIn("pnpm exec playwright install --with-deps chromium", setup)
        self.assertEqual(workflow.count("pnpm exec playwright install --with-deps chromium"), 1)
        self.assertLess(workflow.index(setup_name), workflow.index("- name: Full mock-backed browser tests"))
        self.assertLess(workflow.index(setup_name), workflow.index("- name: Real login, import, query and refusal"))

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
