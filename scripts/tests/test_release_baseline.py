from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "release_baseline.py"
SPEC = importlib.util.spec_from_file_location("mrr_release_baseline", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load {SCRIPT_PATH}")
release_baseline = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = release_baseline
SPEC.loader.exec_module(release_baseline)


class ReleaseBaselineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        root = Path(self.temp_dir.name)

        self.original_paths = {
            "ROOT": release_baseline.ROOT,
            "VERSION_FILE": release_baseline.VERSION_FILE,
            "BASELINE_FILE": release_baseline.BASELINE_FILE,
            "POM_FILE": release_baseline.POM_FILE,
            "MIGRATION_DIR": release_baseline.MIGRATION_DIR,
        }
        self.addCleanup(self.restore_paths)

        release_baseline.ROOT = root
        release_baseline.VERSION_FILE = root / "VERSION"
        release_baseline.BASELINE_FILE = root / "release-baseline.json"
        release_baseline.POM_FILE = root / "backend-repo" / "pom.xml"
        release_baseline.MIGRATION_DIR = (
            root / "backend-repo" / "src" / "main" / "resources" / "db" / "migration"
        )
        release_baseline.MIGRATION_DIR.mkdir(parents=True)
        release_baseline.POM_FILE.parent.mkdir(parents=True, exist_ok=True)
        self.write_valid_repository()

    def restore_paths(self) -> None:
        for name, value in self.original_paths.items():
            setattr(release_baseline, name, value)

    def write_valid_repository(self) -> None:
        release_baseline.VERSION_FILE.write_text("0.4.0\n", encoding="utf-8")
        release_baseline.POM_FILE.write_text(
            """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.zjcxph</groupId>
  <artifactId>imgapi</artifactId>
  <version>${revision}</version>
  <properties>
    <revision>0.4.0</revision>
  </properties>
</project>
""",
            encoding="utf-8",
        )
        (release_baseline.MIGRATION_DIR / "V20260101000000__baseline.sql").write_text(
            "SELECT 1;\n", encoding="utf-8"
        )
        (release_baseline.MIGRATION_DIR / "V20260720000000__latest.sql").write_text(
            "SELECT 1;\n", encoding="utf-8"
        )
        release_baseline.BASELINE_FILE.write_text(
            json.dumps(
                {
                    "manifestSchemaVersion": 1,
                    "database": {
                        "minimumCompatibleMigration": "20260101000000",
                        "maximumCompatibleMigration": "20260720000000",
                        "backwardCompatibleWithPreviousApplication": False,
                    },
                    "applicationRollback": {
                        "allowed": False,
                        "reason": "The latest schema is not backward compatible.",
                    },
                    "configuration": {"schemaVersion": 1},
                }
            ),
            encoding="utf-8",
        )

    def test_validate_accepts_consistent_release_metadata(self) -> None:
        version, baseline = release_baseline.validate()

        self.assertEqual("0.4.0", version)
        self.assertEqual("20260720000000", baseline["database"]["maximumCompatibleMigration"])

    def test_validate_rejects_version_drift_between_version_file_and_pom(self) -> None:
        release_baseline.VERSION_FILE.write_text("0.4.1\n", encoding="utf-8")

        with self.assertRaisesRegex(release_baseline.BaselineError, "与 VERSION='0.4.1' 不一致"):
            release_baseline.validate()

    def test_validate_requires_baseline_to_track_latest_migration(self) -> None:
        (release_baseline.MIGRATION_DIR / "V20260721000000__newer.sql").write_text(
            "SELECT 1;\n", encoding="utf-8"
        )

        with self.assertRaisesRegex(release_baseline.BaselineError, "不是当前最新正式迁移"):
            release_baseline.validate()

    def test_migration_versions_rejects_invalid_filename(self) -> None:
        (release_baseline.MIGRATION_DIR / "V4__legacy_name.sql").write_text(
            "SELECT 1;\n", encoding="utf-8"
        )

        with self.assertRaisesRegex(release_baseline.BaselineError, "迁移文件不符合"):
            release_baseline.migration_versions()

    def test_manifest_identity_requires_full_sha_and_real_utc_time(self) -> None:
        release_baseline.validate_manifest_identity(
            "a" * 40,
            "2026-07-20T03:15:00Z",
        )

        with self.assertRaisesRegex(release_baseline.BaselineError, "40 位 Git SHA"):
            release_baseline.validate_manifest_identity("abc123", "2026-07-20T03:15:00Z")
        with self.assertRaisesRegex(release_baseline.BaselineError, "不是有效时间"):
            release_baseline.validate_manifest_identity("a" * 40, "2026-02-30T03:15:00Z")
        with self.assertRaisesRegex(release_baseline.BaselineError, "UTC 时间"):
            release_baseline.validate_manifest_identity("a" * 40, "2026-07-20T11:15:00+08:00")

    def test_build_manifest_preserves_release_contract(self) -> None:
        version, baseline = release_baseline.validate()

        manifest = release_baseline.build_manifest(
            version,
            baseline,
            "b" * 40,
            "2026-07-20T03:15:00Z",
        )

        self.assertEqual(1, manifest["manifestSchemaVersion"])
        self.assertEqual("0.4.0", manifest["productVersion"])
        self.assertIs(manifest["database"], baseline["database"])
        self.assertIs(manifest["applicationRollback"], baseline["applicationRollback"])
        self.assertIs(manifest["configuration"], baseline["configuration"])


if __name__ == "__main__":
    unittest.main()
