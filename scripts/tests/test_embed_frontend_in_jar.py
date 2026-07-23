import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

from scripts.embed_frontend_in_jar import embed_frontend, verify_frontend


class EmbedFrontendInJarTest(unittest.TestCase):

    def test_embeds_vite_distribution_into_spring_boot_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "app.jar"
            dist = root / "dist"
            (dist / "assets").mkdir(parents=True)
            (dist / "index.html").write_text("<html><body>MRR</body></html>", encoding="utf-8")
            (dist / "assets/app-123.js").write_text("console.log('mrr')", encoding="utf-8")
            (dist / "favicon.ico").write_bytes(b"ico")
            with ZipFile(jar, "w") as archive:
                archive.writestr("BOOT-INF/classes/application.properties", "server.port=18045")

            embedded_count = embed_frontend(jar, dist)
            static_count, asset_count = verify_frontend(jar)

            self.assertEqual(embedded_count, 3)
            self.assertEqual(static_count, 3)
            self.assertEqual(asset_count, 1)
            with ZipFile(jar) as archive:
                self.assertIn("BOOT-INF/classes/static/index.html", archive.namelist())
                self.assertIn("BOOT-INF/classes/static/assets/app-123.js", archive.namelist())
                self.assertEqual(
                    archive.read("BOOT-INF/classes/application.properties"),
                    b"server.port=18045",
                )

    def test_rejects_distribution_without_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "app.jar"
            dist = root / "dist"
            dist.mkdir()
            (dist / "index.html").write_text("<html></html>", encoding="utf-8")
            with ZipFile(jar, "w"):
                pass

            with self.assertRaisesRegex(ValueError, "no generated assets"):
                embed_frontend(jar, dist)

    def test_rejects_embedding_twice(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "app.jar"
            dist = root / "dist"
            (dist / "assets").mkdir(parents=True)
            (dist / "index.html").write_text("<html></html>", encoding="utf-8")
            (dist / "assets/app.js").write_text("app", encoding="utf-8")
            with ZipFile(jar, "w"):
                pass

            embed_frontend(jar, dist)

            with self.assertRaisesRegex(ValueError, "already contains bundled frontend"):
                embed_frontend(jar, dist)


if __name__ == "__main__":
    unittest.main()
