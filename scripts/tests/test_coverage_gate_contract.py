from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


class CoverageGateContractTest(unittest.TestCase):
    def test_verify_generates_combined_report(self):
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        tree = ET.parse(ROOT / "backend-repo/pom.xml")
        reports = tree.findall(".//m:plugin[m:artifactId='jacoco-maven-plugin']/m:executions/m:execution", ns)
        self.assertTrue(any(e.findtext("m:phase", namespaces=ns) == "verify" for e in reports))

    def test_critical_database_tests_cannot_silently_skip_for_missing_docker(self):
        for path in (ROOT / "backend-repo/src/test/java/com/zjcxph/imgapi/integration").rglob("*PostgresqlIT.java"):
            self.assertNotIn("disabledWithoutDocker", path.read_text(encoding="utf-8"), str(path))

    def test_coverage_comparison_is_part_of_required_quality_workflow(self):
        workflow = (ROOT / ".github/workflows/quality-gate.yml").read_text(encoding="utf-8")
        self.assertGreaterEqual(workflow.count("--fail-on-regression"), 3)
        self.assertIn("scripts/check_test_execution.py", workflow)
