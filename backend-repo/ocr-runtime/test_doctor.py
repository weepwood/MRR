import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("doctor", Path(__file__).with_name("doctor.py"))
doctor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(doctor)


class OfflineBundleVerificationTest(unittest.TestCase):
    def test_modified_runtime_file_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "worker.py"
            file.write_bytes(b"synthetic-runtime")
            manifest = {"schemaVersion": 1, "files": {"worker.py": hashlib.sha256(file.read_bytes()).hexdigest()}}
            (root / "runtime-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            doctor.verify(root)
            file.write_bytes(b"modified-runtime")
            with self.assertRaises(ValueError):
                doctor.verify(root)

    def test_manifest_cannot_reference_files_outside_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            outer = Path(directory)
            root = outer / "bundle"
            root.mkdir()
            (outer / "outside").write_bytes(b"synthetic")
            manifest = {"schemaVersion": 1, "files": {"../outside": hashlib.sha256(b"synthetic").hexdigest()}}
            (root / "runtime-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(ValueError):
                doctor.verify(root)


if __name__ == "__main__":
    unittest.main()
