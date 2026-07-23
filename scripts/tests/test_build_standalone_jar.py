import tempfile
import unittest
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile

from scripts.build_standalone_jar import build_standalone_jar, verify_standalone_jar


ROOT = Path(__file__).resolve().parents[2]
APPLICATION_PROPERTIES = "BOOT-INF/classes/application.properties"


class BuildStandaloneJarTest(unittest.TestCase):

    def create_embedded_jar(self, path: Path, properties: str | None = None) -> None:
        with ZipFile(path, "w") as archive:
            archive.writestr(
                APPLICATION_PROPERTIES,
                properties or "server.port=${SERVER_PORT:18045}\nfeature.enabled=true\n",
            )
            archive.writestr(
                "BOOT-INF/classes/static/index.html",
                "<html><body>MRR</body></html>",
            )
            archive.writestr(
                "BOOT-INF/classes/static/assets/app.js",
                "console.log('mrr')",
            )
            archive.writestr(
                "BOOT-INF/lib/dependency.jar",
                b"nested-jar",
                compress_type=ZIP_STORED,
            )

    def test_builds_separate_jar_with_embedded_frontend_and_port_8002(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.jar"
            output = root / "MRR-v0.7.0-standalone.jar"
            self.create_embedded_jar(source)

            build_standalone_jar(source, output)
            entry_count, asset_count = verify_standalone_jar(output)

            self.assertGreater(entry_count, 0)
            self.assertEqual(asset_count, 1)
            with ZipFile(source) as archive:
                self.assertIn(
                    "server.port=${SERVER_PORT:18045}",
                    archive.read(APPLICATION_PROPERTIES).decode("utf-8"),
                )
            with ZipFile(output) as archive:
                properties = archive.read(APPLICATION_PROPERTIES).decode("utf-8")
                self.assertIn("server.port=${SERVER_PORT:8002}", properties)
                self.assertNotIn("server.port=${SERVER_PORT:18045}", properties)
                self.assertEqual(
                    archive.getinfo("BOOT-INF/lib/dependency.jar").compress_type,
                    ZIP_STORED,
                )

    def test_rejects_jar_without_embedded_frontend(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.jar"
            output = root / "standalone.jar"
            with ZipFile(source, "w") as archive:
                archive.writestr(APPLICATION_PROPERTIES, "server.port=${SERVER_PORT:18045}\n")

            with self.assertRaisesRegex(ValueError, "frontend"):
                build_standalone_jar(source, output)

    def test_rejects_missing_or_duplicated_port_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            configurations = (
                "feature.enabled=true\n",
                "server.port=${SERVER_PORT:18045}\nserver.port=${SERVER_PORT:9000}\n",
            )
            for index, properties in enumerate(configurations):
                source = root / f"source-{index}.jar"
                output = root / f"output-{index}.jar"
                self.create_embedded_jar(source, properties)
                with self.assertRaisesRegex(ValueError, "exactly one"):
                    build_standalone_jar(source, output)

    def test_rejects_invalid_port_and_in_place_rewrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.jar"
            self.create_embedded_jar(source)

            with self.assertRaisesRegex(ValueError, "valid range"):
                build_standalone_jar(source, root / "invalid.jar", 70000)
            with self.assertRaisesRegex(ValueError, "different"):
                build_standalone_jar(source, source)

    def test_release_workflow_builds_and_uploads_standalone_jar(self):
        workflow = (ROOT / ".github/workflows/standalone-jar-release.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn("scripts/build_standalone_jar.py", workflow)
        self.assertIn("--default-port 8002", workflow)
        self.assertIn("MRR-v${PRODUCT_VERSION}-standalone.jar", workflow)
        self.assertIn("steps.standalone.outputs.jar", workflow)
        self.assertIn("gh release upload", workflow)
        self.assertIn("Waiting for GitHub Release", workflow)


if __name__ == "__main__":
    unittest.main()
