import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class DocumentationPackagingPolicyTest(unittest.TestCase):

    def test_release_workflows_do_not_embed_documentation_into_backend_jar(self):
        standalone = (ROOT / ".github/workflows/standalone-jar-release.yml").read_text(encoding="utf-8")
        windows = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")

        self.assertNotIn("--mount docs", standalone)
        self.assertNotIn("--mount docs", windows)
        self.assertNotIn("Build and embed documentation", standalone)
        self.assertNotIn("Embed documentation in backend JAR", windows)

    def test_windows_package_keeps_documentation_as_external_files(self):
        windows = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")

        self.assertIn("Build documentation for external hosting", windows)
        self.assertIn('cp -a vitepress-doc/.vitepress/dist-user/. "${PACKAGE_DIR}/docs/user/"', windows)
        self.assertIn('cp -a vitepress-doc/.vitepress/dist-internal/. "${PACKAGE_DIR}/docs/internal/"', windows)
        self.assertIn('test -f "${PACKAGE_DIR}/docs/user/index.html"', windows)
        self.assertIn('test -f "${PACKAGE_DIR}/docs/internal/index.html"', windows)


if __name__ == "__main__":
    unittest.main()
