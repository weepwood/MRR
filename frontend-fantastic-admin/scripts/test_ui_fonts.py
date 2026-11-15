"""Unicode boundary and source-census tests; no font tool dependency needed."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ui_fonts", Path(__file__).with_name("build-ui-fonts.py"))
fonts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fonts)


class UiFontRangesTest(unittest.TestCase):
    def test_complement_preserves_unicode_endpoints_and_holes(self):
        common = fonts.ranges([0x10FFFF, 66, 65, 1, 0])
        self.assertEqual(common, [[0, 1], [65, 66], [0x10FFFF, 0x10FFFF]])
        self.assertEqual(fonts.complement(common), [[2, 64], [67, 0x10FFFE]])

    def test_empty_and_full_ranges(self):
        self.assertEqual(fonts.complement([]), [[0, 0x10FFFF]])
        self.assertEqual(fonts.complement([[0, 0x10FFFF]]), [])

    def test_css_round_trip_accepts_formatter_whitespace(self):
        expected = [[0x20, 0x7E], [0x7528, 0x7528], [0x10FFFF, 0x10FFFF]]
        self.assertEqual(fonts.parse_unicode_css("U+20-7E, U+7528, U+10FFFF"), expected)
        self.assertEqual(fonts.parse_unicode_css(fonts.unicode_css(expected)), expected)

    def test_census_excludes_tests_and_generated_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {"src/page.vue": "用", "src/mock/demo.ts": "例", "src/__tests__/test.ts": "龘",
                     "src/page.test.ts": "龖", "src/types/components.d.ts": "麤", "src/iconify/icons.json": "靐"}
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            with patch.object(fonts, "ROOT", root):
                points = fonts.source_characters()
            self.assertTrue({ord("用"), ord("例")} <= points)
            self.assertTrue({ord(character) for character in "龘龖麤靐"}.isdisjoint(points))


if __name__ == "__main__":
    unittest.main()
