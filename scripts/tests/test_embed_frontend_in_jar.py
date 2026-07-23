import tempfile
import unittest
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile

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
                archive.writestr(
                    "BOOT-INF/lib/dependency.jar",
                    b"nested-jar",
                    compress_type=ZIP_STORED,
                )

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
                self.assertEqual(
                    archive.getinfo("BOOT-INF/lib/dependency.jar").compress_type,
                    ZIP_STORED,
                )

    def test_replaces_existing_static_resources_without_duplicate_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "app.jar"
            dist = root / "dist"
            (dist / "assets").mkdir(parents=True)
            (dist / "index.html").write_text("<html>new frontend</html>", encoding="utf-8")
            (dist / "assets/new.js").write_text("new", encoding="utf-8")
            with ZipFile(jar, "w") as archive:
                archive.writestr("BOOT-INF/classes/static/index.html", "<html>old frontend</html>")
                archive.writestr("BOOT-INF/classes/static/assets/old.js", "old")
                archive.writestr("BOOT-INF/classes/application.properties", "keep=true")

            embed_frontend(jar, dist)

            with ZipFile(jar) as archive:
                names = archive.namelist()
                self.assertEqual(names.count("BOOT-INF/classes/static/index.html"), 1)
                self.assertNotIn("BOOT-INF/classes/static/assets/old.js", names)
                self.assertIn("BOOT-INF/classes/static/assets/new.js", names)
                self.assertEqual(
                    archive.read("BOOT-INF/classes/static/index.html"),
                    b"<html>new frontend</html>",
                )
                self.assertEqual(
                    archive.read("BOOT-INF/classes/application.properties"),
                    b"keep=true",
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

    def test_reembedding_replaces_previous_frontend(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            jar = root / "app.jar"
            dist = root / "dist"
            (dist / "assets").mkdir(parents=True)
            (dist / "index.html").write_text("<html>first</html>", encoding="utf-8")
            (dist / "assets/app.js").write_text("first", encoding="utf-8")
            with ZipFile(jar, "w") as archive:
                archive.writestr("BOOT-INF/classes/application.properties", "keep=true")

            embed_frontend(jar, dist)
            (dist / "index.html").write_text("<html>second</html>", encoding="utf-8")
            (dist / "assets/app.js").write_text("second", encoding="utf-8")
            embed_frontend(jar, dist)

            with ZipFile(jar) as archive:
                self.assertEqual(
                    archive.namelist().count("BOOT-INF/classes/static/index.html"),
                    1,
                )
                self.assertEqual(
                    archive.read("BOOT-INF/classes/static/index.html"),
                    b"<html>second</html>",
                )
                self.assertEqual(
                    archive.read("BOOT-INF/classes/static/assets/app.js"),
                    b"second",
                )


if __name__ == "__main__":
    unittest.main()
