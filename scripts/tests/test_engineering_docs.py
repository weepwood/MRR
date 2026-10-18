from pathlib import Path
import tempfile
import unittest

from scripts.check_engineering_docs import validate


class EngineeringDocsTest(unittest.TestCase):
    def test_repository_has_no_stale_versions_or_branch_workflow(self):
        self.assertEqual([], validate(Path(__file__).resolve().parents[2]))

    def test_detects_stale_explicit_version_and_branch(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "VERSION").write_text("1.2.3", encoding="utf-8")
            (root / "README.md").write_text("当前值为 **0.1.0**", encoding="utf-8")
            (root / "frontend-fantastic-admin").mkdir()
            (root / "frontend-fantastic-admin/ENGINEERING.md").write_text("git checkout dev", encoding="utf-8")
            problems = validate(root)
            self.assertTrue(any("README" in p for p in problems))
            self.assertTrue(any("ENGINEERING" in p for p in problems))
