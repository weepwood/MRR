import json
from pathlib import Path
import tempfile
import unittest

from scripts.jacoco_coverage import (
    compare_coverage,
    load_baseline,
    parse_jacoco_xml,
    render_markdown,
)

XML = '''<?xml version="1.0" encoding="UTF-8"?>
<report name="sample">
  <counter type="INSTRUCTION" missed="50" covered="50"/>
  <counter type="BRANCH" missed="60" covered="40"/>
  <counter type="LINE" missed="45" covered="55"/>
  <counter type="COMPLEXITY" missed="70" covered="30"/>
  <counter type="METHOD" missed="35" covered="65"/>
  <counter type="CLASS" missed="20" covered="80"/>
</report>
'''


class JacocoCoverageTest(unittest.TestCase):

    def test_parses_root_counters(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "jacoco.xml"
            path.write_text(XML, encoding="utf-8")
            counters = parse_jacoco_xml(path)

        self.assertEqual(counters["LINE"].covered, 55)
        self.assertEqual(counters["LINE"].total, 100)
        self.assertAlmostEqual(counters["LINE"].percentage, 55.0)

    def test_rejects_incomplete_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "jacoco.xml"
            path.write_text(
                '<report><counter type="LINE" missed="1" covered="1"/></report>',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "missing counters"):
                parse_jacoco_xml(path)

    def test_loads_lowercase_baseline_metrics(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "baseline.json"
            path.write_text(
                json.dumps({"metrics": {"line": 54.5, "branch": 39.0}}),
                encoding="utf-8",
            )
            baseline = load_baseline(path)

        self.assertEqual(baseline["LINE"], 54.5)
        self.assertEqual(baseline["BRANCH"], 39.0)

    def test_marks_only_regressions_beyond_tolerance(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "jacoco.xml"
            path.write_text(XML, encoding="utf-8")
            counters = parse_jacoco_xml(path)

        comparisons = compare_coverage(
            counters,
            {"LINE": 55.4, "BRANCH": 41.0},
            max_regression=0.5,
        )
        by_type = {item.type: item for item in comparisons}

        self.assertFalse(by_type["LINE"].regressed)
        self.assertTrue(by_type["BRANCH"].regressed)

    def test_markdown_includes_baseline_delta(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "jacoco.xml"
            path.write_text(XML, encoding="utf-8")
            counters = parse_jacoco_xml(path)

        comparisons = compare_coverage(counters, {"LINE": 54.0}, 0.5)
        report = render_markdown(counters, comparisons, 0.5)

        self.assertIn("| 行 | 55 / 100 | 55.00% | 54.00% | +1.00 pp |", report)
        self.assertIn("未发现超过 0.50 个百分点", report)


if __name__ == "__main__":
    unittest.main()
