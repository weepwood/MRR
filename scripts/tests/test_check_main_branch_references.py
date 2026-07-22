import tempfile
import unittest
from pathlib import Path

from scripts.check_main_branch_references import find_forbidden_references


class MainBranchReferenceCheckTest(unittest.TestCase):
    def test_reports_active_legacy_branch_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'README.md'
            path.write_text('git checkout dev-no-login\n', encoding='utf-8')

            self.assertEqual(
                find_forbidden_references(root, ('README.md',)),
                ['README.md:1: git checkout dev-no-login'],
            )

    def test_accepts_main_branch_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'README.md'
            path.write_text('git switch main\n', encoding='utf-8')

            self.assertEqual(find_forbidden_references(root, ('README.md',)), [])


if __name__ == '__main__':
    unittest.main()
