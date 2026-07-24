import json
import tempfile
import unittest
from pathlib import Path

from scripts.vitest_coverage import (
    build_json_report,
    compare_coverage,
    load_baseline,
    parse_vitest_summary,
    render_markdown,
)


class VitestCoverageTest(unittest.TestCase):

    def test_parses_total_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "coverage-summary.json"
            summary.write_text(json.dumps({
                "total": {
                    "lines": {"total": 100, "covered": 60, "skipped": 0, "pct": 60},
                    "statements": {"total": 120, "covered": 72, "skipped": 0, "pct": 60},
                    "functions": {"total": 40, "covered": 20, "skipped": 0, "pct": 50},
                    "branches": {"total": 50, "covered": 20, "skipped": 0, "pct": 40},
                }
            }), encoding="utf-8")

            metrics = parse_vitest_summary(summary)

            self.assertEqual(metrics["lines"].covered, 60)
            self.assertEqual(metrics["branches"].percentage, 40.0)

    def test_requires_all_total_metrics(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "coverage-summary.json"
            summary.write_text(json.dumps({
                "total": {
                    "lines": {"total": 1, "covered": 1, "skipped": 0, "pct": 100}
                }
            }), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "statements"):
                parse_vitest_summary(summary)

    def test_compares_percentage_point_regressions(self):
        metrics = self._metrics()

        comparisons = compare_coverage(
            metrics,
            {"lines": 61.0, "statements": 60.0, "functions": 50.0, "branches": 40.0},
            max_regression=0.5,
        )

        lines = next(item for item in comparisons if item.name == "lines")
        statements = next(item for item in comparisons if item.name == "statements")
        self.assertTrue(lines.regressed)
        self.assertFalse(statements.regressed)

    def test_renders_report_and_machine_readable_result(self):
        metrics = self._metrics()
        comparisons = compare_coverage(metrics, {"lines": 60.0}, 0.5)

        markdown = render_markdown(metrics, comparisons, 0.5)
        report = build_json_report(metrics, comparisons, 0.5)

        self.assertIn("前端 Vitest 覆盖率", markdown)
        self.assertIn("60.00%", markdown)
        self.assertFalse(report["regressionDetected"])
        self.assertEqual(report["metrics"]["functions"]["percentage"], 50.0)

    def test_loads_partial_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            baseline_path = Path(directory) / "baseline.json"
            baseline_path.write_text(json.dumps({
                "metrics": {"lines": 60.0, "branches": 40.0}
            }), encoding="utf-8")

            baseline = load_baseline(baseline_path)

            self.assertEqual(baseline, {"lines": 60.0, "branches": 40.0})

    @staticmethod
    def _metrics():
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "coverage-summary.json"
            summary.write_text(json.dumps({
                "total": {
                    "lines": {"total": 100, "covered": 60, "skipped": 0, "pct": 60},
                    "statements": {"total": 120, "covered": 72, "skipped": 0, "pct": 60},
                    "functions": {"total": 40, "covered": 20, "skipped": 0, "pct": 50},
                    "branches": {"total": 50, "covered": 20, "skipped": 0, "pct": 40},
                }
            }), encoding="utf-8")
            return parse_vitest_summary(summary)


if __name__ == "__main__":
    unittest.main()
