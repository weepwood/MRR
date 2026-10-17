import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("worker", Path(__file__).with_name("worker.py"))
worker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(worker)


class WorkerBoundaryTest(unittest.TestCase):
    def test_rejects_input_outside_managed_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            managed = root / "managed"
            managed.mkdir()
            outside = root / "outside.ocr"
            outside.write_bytes(b"synthetic")
            with self.assertRaises(ValueError):
                worker.input_path(str(outside), managed.resolve())

    def test_rejects_unpinned_manifest_without_importing_engine(self):
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "manifest.json").write_text('{"model":"default"}', encoding="utf-8")
            with self.assertRaises(ValueError):
                worker.load_manifest(folder)


if __name__ == "__main__":
    unittest.main()
