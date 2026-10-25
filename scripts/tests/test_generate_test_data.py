"""合成夹具的契约测试；不连接数据库。"""
import csv
import importlib.util
import json
import re
import shutil
import struct
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "generate_test_data.py"
REPO = MODULE.parent.parent


class SyntheticDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("generate_test_data", MODULE)
        cls.generator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.generator)
        cls._tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls._tmp.name)
        cls.suite = cls.root / "suite"
        # 默认规模只生成一次；需要篡改的用例复制副本，避免每个用例各生成一遍。
        cls.generator.generate(cls.suite)

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def generate(self, root, **kwargs):
        return self.generator.generate(Path(root), **kwargs)

    def copy_suite(self):
        copies = self.__dict__.get("_copies", 0) + 1
        self.__dict__["_copies"] = copies
        target = self.root / f"copy-{self._testMethodName}-{copies}"
        shutil.copytree(self.suite, target)
        return target

    def rows(self, root, relative):
        with (Path(root) / relative).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream))

    def cases(self, root=None):
        return json.loads(((Path(root) if root else self.suite) / "cases.json").read_text(encoding="utf-8"))

    def test_reproducible_and_integrity_checked(self):
        target = self.root / "reproducible"
        self.generate(target)
        self.assertEqual((self.suite / "manifest.json").read_bytes(), (target / "manifest.json").read_bytes())
        self.assertEqual([], self.generator.verify(target))
        (target / "imports/patients-0001.csv").write_text("tampered", encoding="utf-8")
        self.assertTrue(self.generator.verify(target))

    def test_relations_types_and_page_totals(self):
        root = self.suite
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
        root = self.root / "chunked"
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
        marker = self.root / "keep.txt"
        marker.write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError):
            self.generate(self.root)
        for kwargs in ({"records": 0}, {"records": 10000001}, {"scans_per_record": 0}, {"chunk_rows": 100001}):
            with self.assertRaises(ValueError):
                self.generate(self.root / "bad", **kwargs)
        self.assertEqual("keep", marker.read_text(encoding="utf-8"))

    def test_guards_reject_oversized_requests(self):
        # 单参数合法但乘积巨大时也必须拒绝，否则会写满磁盘或产出百万级文件。
        with self.assertRaises(ValueError) as rows_guard:
            self.generate(self.root / "too-many-rows", records=12, scans_per_record=100, max_scan_rows=1000)
        self.assertIn("超过上限", str(rows_guard.exception))
        with self.assertRaises(ValueError) as parts_guard:
            self.generate(self.root / "too-many-parts", records=100000, chunk_rows=1)
        self.assertIn("分卷过细", str(parts_guard.exception))
        scan_parts_root = self.root / "too-many-scan-parts"
        with patch.object(self.generator, "_populate", side_effect=AssertionError("必须在写文件前拒绝")):
            with self.assertRaises(ValueError) as scan_parts_guard:
                self.generate(scan_parts_root, records=12, scans_per_record=2000, chunk_rows=1)
        self.assertIn("分卷过细", str(scan_parts_guard.exception))
        self.assertFalse((self.root / "too-many-rows").exists())
        self.assertFalse((self.root / "too-many-parts").exists())
        self.assertFalse(scan_parts_root.exists())

    def test_incomplete_generation_is_marked_and_reclaimable(self):
        root = self.root / "interrupted"
        original = self.generator._populate

        def boom(*args, **kwargs):
            raise OSError("模拟磁盘写入失败")

        self.generator._populate = boom
        try:
            with self.assertRaises(OSError):
                self.generate(root)
        finally:
            self.generator._populate = original
        self.assertTrue((root / self.generator.INCOMPLETE_MARKER).is_file())
        self.assertFalse((root / "manifest.json").exists())
        with self.assertRaises(FileExistsError):
            self.generate(root)
        self.generate(root, force=True)
        self.assertTrue((root / "manifest.json").is_file())
        self.assertFalse((root / self.generator.INCOMPLETE_MARKER).exists())
        with self.assertRaises(FileExistsError):
            self.generate(root, force=True)

    def test_force_reclaims_interrupted_manifest_temp_file(self):
        root = self.root / "interrupted-manifest"
        original = self.generator.write_json

        def fail_manifest(path, value):
            if path.name == "manifest.json":
                path.with_suffix(".tmp").write_text("partial", encoding="utf-8")
                raise OSError("模拟 manifest 原子写入中断")
            return original(path, value)

        with patch.object(self.generator, "write_json", side_effect=fail_manifest):
            with self.assertRaises(OSError):
                self.generate(root)
        self.assertTrue((root / self.generator.INCOMPLETE_MARKER).is_file())
        self.assertTrue((root / "manifest.tmp").is_file())
        self.generate(root, force=True)
        self.assertEqual([], self.generator.verify(root))

    def test_force_refuses_directory_with_foreign_entries(self):
        # --force 只回收生成器自己写的内容；目录里混进其他文件时必须停手，交人工确认。
        root = self.root / "foreign"
        root.mkdir()
        (root / self.generator.INCOMPLETE_MARKER).write_text("stub", encoding="utf-8")
        keep = root / "someone-else.txt"
        keep.write_text("keep", encoding="utf-8")
        with self.assertRaises(FileExistsError) as guard:
            self.generate(root, force=True)
        self.assertIn("非生成器条目", str(guard.exception))
        self.assertEqual("keep", keep.read_text(encoding="utf-8"))

    def test_cases_carry_single_verdict(self):
        cases = self.cases()
        self.assertGreaterEqual(len(cases), 40)
        self.assertEqual(len(cases), len({case["id"] for case in cases}))
        for case in cases:
            expected = case["expected"]
            self.assertTrue(expected)
            self.assertIn(expected.get("verdict"), {"accept", "reject", "observe", "exception"}, case["id"])
            self.assertTrue((self.suite / case["file"]).is_file())
            if "probe" in expected:
                self.assertIsNone(expected.get("canImport"), f"{case['id']}: probe 与确定判定并存，读者无法区分已验收与待观察")
        by_id = {case["id"]: case for case in cases}
        for key in ("statistics-high-missing-sjh", "scan-type-16", "patients-scientific", "archive-boxes-status"):
            self.assertEqual("reject", by_id[key]["expected"]["verdict"])
        self.assertEqual(205, by_id["patients-error-cap"]["expected"]["errorRows"])

    def test_case_ids_share_dataset_prefix(self):
        for case in self.cases():
            self.assertTrue(
                case["id"].startswith(case["dataset"] + "-"),
                f"{case['id']} 未使用数据集前缀 {case['dataset']}-，按前缀筛选会漏",
            )

    def test_storage_cases_and_media_are_consistent(self):
        storage = json.loads((self.suite / "storage-cases.json").read_text(encoding="utf-8"))
        self.assertFalse((self.suite / storage["missing"]["path"]).exists())
        self.assertNotEqual(storage["checksum_mismatch"]["expected_md5"], storage["checksum_mismatch"]["actual_md5"])
        for item in storage["good"]:
            self.assertTrue((self.suite / item["path"]).is_file())

    def test_media_decodes_and_pdf_pages(self):
        # 标准库验证格式结构；独立编解码器/渲染器验证记录于交付文档。
        root = self.suite
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
        for dataset, source in self.generator.SOURCES.items():
            path = REPO / source
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

    def test_declared_source_files_exist(self):
        # manifest 里登记的来源路径若失效，生成物会指向不存在的文件而没有任何测试报警。
        for name in (self.generator.STORAGE_SOURCE, self.generator.TYPE_SOURCE, *self.generator.SOURCES.values()):
            self.assertTrue((REPO / name).is_file(), f"契约来源文件缺失：{name}")

    def test_type_labels_match_backend_enum(self):
        text = (REPO / self.generator.TYPE_SOURCE).read_text(encoding="utf-8")
        labels = [re.sub(r"^\d\d-", "", label) for label in re.findall(r"\(\d+,\s*\"([^\"]+)\"\)", text)]
        self.assertEqual(16, len(labels), "MedicalRecordType 枚举条目数变化，生成器的 16 类假设需复核")
        self.assertEqual(
            self.generator.TYPE_LABELS,
            labels,
            "病案类型标签与 MedicalRecordType 不一致；类型表变更后必须同步生成器",
        )

    def test_verification_rejects_manifest_traversal(self):
        root = self.copy_suite()
        path = root / "manifest.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["files"][0]["path"] = "../outside"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        self.assertTrue(self.generator.verify(root))

    def test_verification_detects_missing_materialized_file(self):
        # 中断生成会留下“目录在、文件不全”的状态，verify 必须拒绝而不是当作已完成产物。
        root = self.copy_suite()
        images = sorted((root / "storage").rglob("*.png"))
        self.assertTrue(images, "基线应至少实体化一张扫描图")
        images[0].unlink()
        errors = self.generator.verify(root)
        self.assertTrue(errors, "删除实体化扫描图后 verify 仍返回通过，完整性校验存在缺口")
        self.assertTrue(
            any("storage" in str(error).replace("\\", "/") for error in errors),
            f"错误信息未指向缺失的存储文件：{errors}",
        )

    def test_verification_reports_unregistered_files(self):
        # 只做单向校验时，套件里混进旧版残留也会报 ok。
        root = self.copy_suite()
        (root / "injected-extra.csv").write_text("bah\n123\n", encoding="utf-8")
        errors = self.generator.verify(root)
        self.assertTrue(any("未登记文件" in error for error in errors), f"未检测到清单外文件：{errors}")

    def test_verification_handles_corrupt_manifest(self):
        root = self.copy_suite()
        (root / "manifest.json").write_text('{"files": {}}', encoding="utf-8")
        errors = self.generator.verify(root)
        self.assertTrue(any("结构无效" in error for error in errors), f"manifest 结构损坏未被识别：{errors}")
        (root / "manifest.json").write_text("{not json", encoding="utf-8")
        errors = self.generator.verify(root)
        self.assertTrue(any("解析失败" in error for error in errors), f"manifest 解析失败未给出可读错误：{errors}")

    def test_lower_bound_parameters_are_accepted(self):
        # 文档声明 records 下限为 12、chunk_rows 下限为 1，边界值必须可生成且分卷正确。
        root = self.root / "lower-bound"
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
