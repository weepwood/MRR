import base64
import tempfile
import unittest
import urllib.parse
import zlib
from pathlib import Path

from scripts.check_main_branch_references import find_forbidden_references


def encode_drawio_diagram(xml: str) -> str:
    escaped = urllib.parse.quote(xml, safe="~()*!.'")
    compressor = zlib.compressobj(level=9, wbits=-15)
    compressed = compressor.compress(escaped.encode('utf-8')) + compressor.flush()
    return base64.b64encode(compressed).decode('ascii')


class MainBranchReferenceCheckTest(unittest.TestCase):
    def test_reports_active_legacy_branch_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'README.md'
            path.write_text('git checkout dev-no-login\n', encoding='utf-8')

            self.assertEqual(
                find_forbidden_references(root, ('README.md',), ()),
                ['README.md:1: dev-no-login: git checkout dev-no-login'],
            )

    def test_accepts_main_branch_reference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'README.md'
            path.write_text('git switch main\n', encoding='utf-8')

            self.assertEqual(
                find_forbidden_references(root, ('README.md',), ()),
                [],
            )

    def test_reports_incorrect_main_auth_statement(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'architecture.svg'
            path.write_text('main 分支绕过 JWT\n', encoding='utf-8')

            violations = find_forbidden_references(root, ('architecture.svg',), ())

            self.assertTrue(any('main 分支绕过' in item for item in violations))
            self.assertTrue(any('绕过 JWT' in item for item in violations))

    def test_reads_compressed_drawio_diagrams(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / 'architecture.drawio'
            payload = encode_drawio_diagram(
                '<mxGraphModel><root><mxCell value="dev-no-login"/></root></mxGraphModel>',
            )
            path.write_text(
                f'<mxfile compressed="true"><diagram>{payload}</diagram></mxfile>',
                encoding='utf-8',
            )

            violations = find_forbidden_references(root, (), ('architecture.drawio',))

            self.assertTrue(any('dev-no-login' in item for item in violations))


if __name__ == '__main__':
    unittest.main()
