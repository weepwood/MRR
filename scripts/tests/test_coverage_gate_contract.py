from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


class CoverageGateContractTest(unittest.TestCase):
    def test_manual_coverage_consumer_finds_both_actual_artifact_directory_layouts(self):
        workflow = (ROOT / '.github/workflows/coverage-baseline.yml').read_text(encoding='utf-8')
        command = re.search(r'xml_path="\$\((.*?)\)"', workflow).group(1)
        bash = (Path(shutil.which('git')).parent.parent / 'bin/bash.exe'
                if os.name == 'nt' else Path(shutil.which('bash')))
        for layout in ('artifacts/integration/site/jacoco/jacoco.xml',
                       'artifacts/integration/jacoco/jacoco.xml'):
            with self.subTest(layout=layout), tempfile.TemporaryDirectory() as directory:
                report = Path(directory) / layout
                report.parent.mkdir(parents=True)
                report.write_text('<report name="synthetic"/>', encoding='utf-8')
                found = subprocess.check_output([str(bash), '-c', command], cwd=directory, text=True).strip()
                self.assertEqual(layout, found)

    def test_verify_generates_combined_report(self):
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        tree = ET.parse(ROOT / "backend-repo/pom.xml")
        reports = tree.findall(".//m:plugin[m:artifactId='jacoco-maven-plugin']/m:executions/m:execution", ns)
        self.assertTrue(any(e.findtext("m:phase", namespaces=ns) == "verify" for e in reports))

    def test_critical_database_tests_cannot_silently_skip_for_missing_docker(self):
        for path in (ROOT / "backend-repo/src/test/java/com/zjcxph/imgapi/integration").rglob("*PostgresqlIT.java"):
            self.assertNotIn("disabledWithoutDocker", path.read_text(encoding="utf-8"), str(path))

    def test_full_coverage_comparison_is_required_before_release(self):
        workflow = (ROOT / ".github/workflows/windows-release-package.yml").read_text(encoding="utf-8")
        self.assertGreaterEqual(workflow.count("--fail-on-regression"), 3)
        self.assertIn("scripts/check_test_execution.py", workflow)
        release = (ROOT / ".github/workflows/release-on-version-bump.yml").read_text(encoding="utf-8")
        self.assertIn("needs: [identity, qualify]", release)
        self.assertIn("./.github/workflows/release-validation.yml", release)
