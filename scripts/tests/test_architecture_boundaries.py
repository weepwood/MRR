from pathlib import Path
import tempfile
import unittest

from scripts.check_architecture_boundaries import violations

ROOT = Path(__file__).resolve().parents[2]


class ArchitectureBoundaryTest(unittest.TestCase):
    def test_repository_has_no_new_boundary_violations(self):
        self.assertEqual(violations(ROOT), [])

    def test_controller_mapper_import_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "backend-repo/src/main/java/com/zjcxph/imgapi/controller/NewController.java"
            path.parent.mkdir(parents=True)
            path.write_text("import com.zjcxph.imgapi.mapper.*;", encoding="utf-8")
            self.assertTrue(any("NewController" in item for item in violations(root, {})))

    def test_shared_ui_cannot_import_business_layer(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "frontend-fantastic-admin/src/ui/Example.vue"
            path.parent.mkdir(parents=True)
            path.write_text("import api from '@/api/modules/patients'", encoding="utf-8")
            self.assertTrue(any("Example.vue" in item for item in violations(root, {})))

    def test_removed_legacy_dependency_must_remove_allowance(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertTrue(any("stale" in item for item in violations(Path(directory), {"legacy.java": {"LegacyMapper"}})))

    def test_wildcard_import_does_not_allow_new_mapper_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            relative = "backend-repo/src/main/java/com/zjcxph/imgapi/controller/LegacyController.java"
            path = root / relative
            path.parent.mkdir(parents=True)
            mappers = path.parent.parent / "mapper"
            mappers.mkdir()
            for name in ("OldMapper", "SearchMapper"):
                (mappers / f"{name}.java").write_text(f"interface {name} {{}}", encoding="utf-8")
            path.write_text("import com.zjcxph.imgapi.mapper.*;\nclass LegacyController { OldMapper old; SearchMapper added; }", encoding="utf-8")
            self.assertTrue(any("controller must" in item for item in violations(root, {relative: {"*", "OldMapper"}})))
