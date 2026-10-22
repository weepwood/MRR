import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).parent
spec = importlib.util.spec_from_file_location("build_bundle", ROOT / "build_bundle.py")
build_bundle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_bundle)


class PinnedRuntimeDependenciesTest(unittest.TestCase):
    def test_bundle_rejects_runtime_version_different_from_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / "requirements.lock"
            lock.write_text("certifi==2026.10.19\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "RUNTIME_DEPENDENCY_MISMATCH"):
                build_bundle.validate_runtime_packages({"certifi": "2026.7.22"}, lock)

    def test_bundle_accepts_pinned_runtime_version(self):
        with tempfile.TemporaryDirectory() as directory:
            lock = Path(directory) / "requirements.lock"
            lock.write_text("certifi==2026.10.19\n", encoding="utf-8")
            build_bundle.validate_runtime_packages({"certifi": "2026.10.19"}, lock)

    def test_validated_lock_excludes_vulnerable_certifi(self):
        pinned = build_bundle.read_pinned_requirements(ROOT / "requirements-validated.lock")
        version = tuple(map(int, pinned["certifi"].split(".")))
        self.assertGreaterEqual(version, (2026, 10, 18))


if __name__ == "__main__":
    unittest.main()
