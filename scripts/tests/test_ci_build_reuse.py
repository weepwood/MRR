import fnmatch
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CiBuildReuseTest(unittest.TestCase):
    def test_ordinary_application_changes_use_quality_gate_not_full_offline_builds(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        trigger = workflow.split("permissions:", 1)[0]
        paths = re.findall(r"^      - '([^']+)'", trigger, re.M)
        for path in ("backend-repo/src/main/java/example/Example.java",
                     "frontend-fantastic-admin/src/views/example/index.vue"):
            self.assertFalse(any(fnmatch.fnmatchcase(path, pattern) for pattern in paths))
        for path in ("backend-repo/pom.xml", "deploy/windows/install.ps1",
                     "scripts/build_standalone_jar.py", "frontend-fantastic-admin/vite.config.ts"):
            self.assertTrue(any(fnmatch.fnmatchcase(path, pattern) for pattern in paths), path)

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
