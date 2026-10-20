from pathlib import Path
import tempfile
import unittest

from scripts.check_test_execution import validate


class TestExecutionTest(unittest.TestCase):
    def check(self, xml):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "TEST-TestPostgresqlIT.xml").write_text(xml, encoding="utf-8")
            return validate(root)

    def test_requires_a_postgresql_report(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertTrue(validate(Path(folder)))

    def test_rejects_skipped_or_failed_postgresql_tests(self):
        for attribute in ('skipped="1"', 'failures="1"', 'errors="1"'):
            self.assertTrue(self.check(
                f'<testsuite name="app.TestPostgresqlIT" tests="1" {attribute}/>'))

    def test_accepts_executed_tests(self):
        self.assertEqual([], self.check(
            '<testsuite name="app.TestPostgresqlIT" tests="2" failures="0" errors="0" skipped="0"/>'))

    def test_rejects_empty_suite(self):
        self.assertTrue(self.check('<testsuite name="app.TestPostgresqlIT" tests="0"/>'))
