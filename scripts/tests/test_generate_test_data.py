"""合成夹具的契约测试；不连接数据库。"""
import csv
import importlib.util
import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "generate_test_data.py"


class SyntheticDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("generate_test_data", MODULE)
        cls.generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.generator)

    def generate(self, root, **kwargs):
        return self.generator.generate(Path(root), **kwargs)

    def rows(self, root, relative):
        with (Path(root) / relative).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream))

    def test_reproducible_and_integrity_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp) / "a", Path(tmp) / "b"
            self.generate(a)
            self.generate(b)
            self.assertEqual((a / "manifest.json").read_bytes(), (b / "manifest.json").read_bytes())
            self.assertEqual([], self.generator.verify(a))
            (a / "imports/patients-0001.csv").write_text("tampered", encoding="utf-8")
            self.assertTrue(self.generator.verify(a))

    def test_relations_types_and_page_totals(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root)
            patients = self.rows(root, "imports/patients-0001.csv")
            stats = self.rows(root, "imports/statistics-0001.csv")
            scans = self.rows(root, "imports/scan-0001.csv")
            boxes = self.rows(root, "imports/archive-boxes-0001.csv")
            self.assertEqual(12, len(patients))
            self.assertEqual(48, len(scans))
            self.assertEqual(set(range(16)), {int(s["btype"]) for s in scans})
            self.assertEqual(48, sum(int(s["pages"]) for s in stats))
            self.assertEqual({s["bah"] for s in stats}, {p["bah"] for p in patients})
            self.assertEqual({s["sjh"] for s in stats}, {b["sjh"] for b in boxes})
            for scan in scans:
                self.assertIn((scan["bah"], scan["sjh"]), {(s["bah"], s["sjh"]) for s in stats})
                # 来自 ArchiveImagePathSupport.legacyRelativePath：低位用brxh，高位用sjh。
                directory_key = scan["sjh"] if int(scan["bah"]) >= 10000000 else scan["brxh"]
                image = root / "storage" / scan["folder"][:5] / scan["folder"] / (directory_key + "-" + scan["bah"]) / scan["filename"]
                self.assertEqual(int(scan["filesize"]), image.stat().st_size)
            self.assertTrue(all(not p["idcard"] or p["idcard"].startswith("SYNTH-") for p in patients))
            self.assertEqual("00000001", patients[0]["bah"])
            self.assertIn("9999999", {p["bah"] for p in patients})
            self.assertIn("10000000", {p["bah"] for p in patients})

    def test_chunks_are_bounded_and_keys_stable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root, records=13, scans_per_record=5, chunk_rows=7)
            for dataset, total in (("patients", 13), ("statistics", 13), ("archive-boxes", 13), ("scan", 65)):
                parts = sorted((root / "imports").glob(dataset + "-*.csv"))
                rows = [self.rows(root, str(p.relative_to(root))) for p in parts]
                self.assertEqual(total, sum(map(len, rows)))
                self.assertTrue(all(0 < len(part) <= 7 for part in rows))
            manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(48, manifest["materialized_images"])
            self.assertEqual(17, manifest["metadata_only_scans"])

    def test_refuses_existing_directory_and_invalid_sizes(self):
        with tempfile.TemporaryDirectory() as tmp:
            marker = Path(tmp) / "keep.txt"
            marker.write_text("keep", encoding="utf-8")
            with self.assertRaises(FileExistsError):
                self.generate(Path(tmp))
            for kwargs in ({"records": 0}, {"records": 10000001}, {"scans_per_record": 0}, {"chunk_rows": 100001}):
                with self.assertRaises(ValueError):
                    self.generate(Path(tmp) / "bad", **kwargs)
            self.assertEqual("keep", marker.read_text(encoding="utf-8"))

    def test_malformed_cases_and_expected_results_are_separate(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root)
            cases = json.loads((root / "cases.json").read_text(encoding="utf-8"))
            self.assertGreaterEqual(len(cases), 40)
            self.assertEqual(len(cases), len({case["id"] for case in cases}))
            for case in cases:
                self.assertTrue(case["expected"])
                self.assertTrue((root / case["file"]).is_file())
            by_id = {case["id"]: case for case in cases}
            for key in ("statistics-high-missing-sjh", "scan-type-16", "patients-scientific", "boxes-status"):
                self.assertFalse(by_id[key]["expected"]["canImport"])
            self.assertEqual(205, by_id["patients-error-cap"]["expected"]["errorRows"])
            storage = json.loads((root / "storage-cases.json").read_text(encoding="utf-8"))
            self.assertFalse((root / storage["missing"]["path"]).exists())
            self.assertNotEqual(storage["checksum_mismatch"]["expected_md5"], storage["checksum_mismatch"]["actual_md5"])

    def test_media_decodes_and_pdf_pages(self):
        # 标准库验证格式结构；独立编解码器/渲染器验证记录于交付文档。
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root)
            for name, size in (("blank.png", (640, 480)), ("rotated.png", (480, 640))):
                data = (root / "media" / name).read_bytes()
                self.assertEqual(b"\x89PNG\r\n\x1a\n", data[:8])
                self.assertEqual(size, struct.unpack(">II", data[16:24]))
                offset = 8
                while offset < len(data):
                    length = struct.unpack(">I", data[offset:offset + 4])[0]
                    kind = data[offset + 4:offset + 8]
                    content = data[offset + 8:offset + 8 + length]
                    if kind == b"IDAT":
                        self.assertEqual(size[1] * (size[0] + 1), len(zlib.decompress(content)))
                    offset += 12 + length
            tiff = (root / "media/multipage.tiff").read_bytes()
            self.assertEqual(b"II*\0", tiff[:4])
            next_ifd = struct.unpack_from("<I", tiff, 8 + 2 + 9 * 12)[0]
            self.assertGreater(next_ifd, 8)
            self.assertEqual(0, struct.unpack_from("<I", tiff, next_ifd + 2 + 9 * 12)[0])
            pdf = (root / "media/two-pages.pdf").read_bytes()
            self.assertTrue(pdf.startswith(b"%PDF-1.4"))
            self.assertIn(b"/Count 2", pdf)
            self.assertIn(b"SYNTHETIC", pdf)

    def test_headers_match_live_import_services(self):
        import re
        repo = MODULE.parent.parent
        for dataset, source in self.generator.SOURCES.items():
            path = repo / source
            self.assertTrue(path.is_file(), f"缺少契约来源文件：{source}；导入服务可能已改名或移动")
            text = path.read_text(encoding="utf-8")
            constant = "REQUIRED_HEADERS" if dataset == "patients" else "TEMPLATE_HEADERS"
            match = re.search(constant + r"\s*=\s*List\.of\((.*?)\);", text, re.S)
            self.assertIsNotNone(
                match,
                f"{source} 中未找到 {constant} = List.of(...)；常量声明可能已重构，应更新本测试的解析方式，而不是放宽表头断言",
            )
            headers = re.findall(r'"([a-z_]+)"', match.group(1))
            self.assertEqual(
                self.generator.HEADERS[dataset],
                headers,
                f"{dataset} 生成表头与 {source} 的 {constant} 不一致；导入模板变更后必须同步生成器",
            )

    def test_verification_rejects_manifest_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root)
            path = root / "manifest.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            manifest["files"][0]["path"] = "../outside"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            self.assertTrue(self.generator.verify(root))

    def test_verification_detects_missing_materialized_file(self):
        # 中断生成会留下“目录在、文件不全”的状态，verify 必须拒绝而不是当作已完成产物。
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root)
            images = sorted((root / "storage").rglob("*.png"))
            self.assertTrue(images, "基线应至少实体化一张扫描图")
            images[0].unlink()
            errors = self.generator.verify(root)
            self.assertTrue(errors, "删除实体化扫描图后 verify 仍返回通过，完整性校验存在缺口")
            self.assertTrue(
                any("storage" in str(error).replace("\\", "/") for error in errors),
                f"错误信息未指向缺失的存储文件：{errors}",
            )

    def test_lower_bound_parameters_are_accepted(self):
        # 文档声明 records 下限为 12、chunk_rows 下限为 1，边界值必须可生成且分卷正确。
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "suite"
            self.generate(root, records=12, scans_per_record=1, chunk_rows=1)
            patients = [
                row
                for part in sorted((root / "imports").glob("patients-*.csv"))
                for row in self.rows(root, str(part.relative_to(root)))
            ]
            scans = [
                row
                for part in sorted((root / "imports").glob("scan-*.csv"))
                for row in self.rows(root, str(part.relative_to(root)))
            ]
            self.assertEqual(12, len(patients))
            self.assertEqual(12, len(scans))
            self.assertEqual(12, len(list((root / "imports").glob("patients-*.csv"))))
            self.assertEqual([], self.generator.verify(root))


if __name__ == "__main__":
    unittest.main()
