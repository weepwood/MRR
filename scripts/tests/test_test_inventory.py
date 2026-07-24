import tempfile
import unittest
from pathlib import Path

from scripts.test_inventory import build_inventory, render_markdown


class TestInventoryTest(unittest.TestCase):

    def test_counts_test_layers_and_detects_focused_tests(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self._write(
                root,
                "backend-repo/src/test/java/com/example/service/UserServiceTest.java",
                "class UserServiceTest {}",
            )
            self._write(
                root,
                "backend-repo/src/test/java/com/example/integration/UserMapperIT.java",
                "@Disabled class UserMapperIT {}",
            )
            self._write(
                root,
                "frontend-fantastic-admin/src/utils/date.test.ts",
                "it('formats', () => {})",
            )
            self._write(
                root,
                "frontend-fantastic-admin/e2e/login.spec.ts",
                "test.only('login', async () => {})",
            )

            inventory = build_inventory(root)

            self.assertEqual(inventory.backend_unit_files, 1)
            self.assertEqual(inventory.backend_integration_files, 1)
            self.assertEqual(inventory.frontend_unit_files, 1)
            self.assertEqual(inventory.frontend_e2e_files, 1)
            self.assertEqual(inventory.total_files, 4)
            self.assertEqual(len(inventory.focused_or_disabled), 2)

    def test_markdown_contains_all_test_layers(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            inventory = build_inventory(Path(temp_dir))
            report = render_markdown(inventory)

            self.assertIn("后端单元/切片测试", report)
            self.assertIn("后端 PostgreSQL 集成测试", report)
            self.assertIn("前端 Vitest 单元/组件测试", report)
            self.assertIn("前端 Playwright E2E", report)

    @staticmethod
    def _write(root: Path, relative_path: str, content: str) -> None:
        path = root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
