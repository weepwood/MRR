"""直连库造数器 seed_dev_data 的契约测试；不连接数据库。"""
import importlib.util
import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

SEEDER = Path(__file__).resolve().parents[1] / "seed_dev_data.py"
GENERATOR = Path(__file__).resolve().parents[1] / "generate_test_data.py"
REPO = GENERATOR.parent.parent

IMAGE_SUPPORT = REPO / "backend-repo/src/main/java/com/zjcxph/imgapi/storage/ArchiveImagePathSupport.java"
CODE_UTILS = REPO / "backend-repo/src/main/java/com/zjcxph/imgapi/utils/MedicalRecordCodeUtils.java"


class SeedDevDataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("seed_dev_data", SEEDER)
        cls.seeder = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.seeder)
        cls._tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls._tmp.name)

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def small_plan(self, **kwargs):
        params = {"patients": 4, "scans_per_archive": 3, "no_sjh_count": 2, "seed": 42,
                  "materialized_images": 48, "no_images": False}
        params.update(kwargs)
        return self.seeder.build_plan(**params)

    # ---------- 编号规范 ----------

    def test_numbering_rules(self):
        records = self.seeder.build_records(self.small_plan(patients=6))
        bahs = [row["bah"] for row in records["patients"]]
        self.assertEqual(len(bahs), len(set(bahs)), "病案号必须唯一，否则无上架号扫描无法唯一解析主档")
        for row in records["patients"][:2]:
            self.assertTrue(row["bah"].isdigit())
            self.assertLess(int(row["bah"]), 10_000_000, "无上架号分支必须是低位编号")
        for row in records["statistics"][:2]:
            self.assertIsNone(row["sjh"], "无上架号分支统计行 sjh 必须为空")
        for row in records["statistics"][2:]:
            self.assertGreaterEqual(int(row["bah"]), 10_000_000, "高位编号必须提供上架号")
            self.assertTrue(row["sjh"].startswith("SYNTH-S"))
        for row in records["scans"]:
            self.assertTrue(row["brxh"].startswith("SYNTH-BR"))
            self.assertIn(row["btype"], range(16))
            self.assertGreaterEqual(len(row["folder"]), 5)

    def test_no_sjh_count_clamped(self):
        plan = self.small_plan(patients=2, no_sjh_count=5)
        self.assertEqual(plan["no_sjh_count"], 2)

    def test_records_deterministic(self):
        plan = self.small_plan(patients=5, no_sjh_count=1, seed=7)
        again = self.seeder.build_records(self.small_plan(patients=5, no_sjh_count=1, seed=7))
        first = self.seeder.build_records(plan)
        self.assertEqual(first, again, "同 seed 同参数必须产出完全一致的记录")

    # ---------- 路径规则与 Java 源码一致性 ----------

    def test_requires_sjh_matches_java(self):
        source = CODE_UTILS.read_text(encoding="utf-8")
        self.assertIn("ARCHIVE_BAH_UNIQUE_LIMIT", source)
        match = re.search(r"ARCHIVE_BAH_UNIQUE_LIMIT\s*=\s*(?:BigInteger\.valueOf\()?([\d_]+)", source)
        self.assertIsNotNone(match, "Java 常量格式变化时必须同步核对造数器阈值")
        limit = int(match.group(1).replace("_", ""))
        self.assertEqual(limit, 10_000_000, "高位病案号唯一性阈值以 Java 常量为准")
        self.assertFalse(self.seeder.requires_sjh_for_bah("09999999"))
        self.assertFalse(self.seeder.requires_sjh_for_bah("00000001"))
        self.assertFalse(self.seeder.requires_sjh_for_bah("SYNTH-B1"))
        self.assertFalse(self.seeder.requires_sjh_for_bah(""))
        self.assertTrue(self.seeder.requires_sjh_for_bah("10000000"))

    def test_image_path_rule_matches_java(self):
        source = IMAGE_SUPPORT.read_text(encoding="utf-8")
        self.assertIn("folder.substring(0, 5)", source)
        self.assertIn('directoryKey + "-" + bah', source)
        self.assertIn("requiresSjhForBah", source)
        path = self.seeder.image_relative_path(
            folder="24.05.12", bah="10000000", sjh="SYNTH-S00000001",
            brxh="SYNTH-BR00000001", filename="00000001-0001.png")
        self.assertEqual(path, "24.05/24.05.12/SYNTH-S00000001-10000000/00000001-0001.png")
        low = self.seeder.image_relative_path(
            folder="24.05.12", bah="00000001", sjh=None,
            brxh="SYNTH-BR00000001", filename="00000001-0001.png")
        self.assertEqual(low, "24.05/24.05.12/SYNTH-BR00000001-00000001/00000001-0001.png")

    def test_image_path_rejects_invalid_segments(self):
        with self.assertRaises(ValueError):
            self.seeder.image_relative_path("abc", "00000001", None, "SYNTH-BR1", "a.png")
        with self.assertRaises(ValueError):
            self.seeder.image_relative_path("a/bcde", "00000001", None, "SYNTH-BR1", "a.png")
        with self.assertRaises(ValueError):
            self.seeder.image_relative_path("24.05.12", "00000001", None, "SYNTH-BR1", "")

    # ---------- SQL 生成 ----------

    def test_seed_sql_structure(self):
        sql = self.seeder.build_seed_sql(self.seeder.build_records(
            self.small_plan(patients=4, scans_per_archive=2)))
        self.assertTrue(sql.startswith("-- "), "SQL 头部必须带批次说明")
        self.assertIn("BEGIN;", sql)
        self.assertIn("COMMIT;", sql)
        self.assertIn("INSERT INTO app.mr_patient", sql)
        self.assertIn("INSERT INTO app.mr_statistics", sql)
        self.assertIn("INSERT INTO app.mr_scan", sql)
        self.assertIn("'not_migrated'", sql)
        self.assertIn("SYNTH-S", sql)
        # 无上架号统计行必须落 NULL sjh，由触发器按唯一低位病案号建主档。
        self.assertIn(", NULL, ", sql)

    def test_seed_sql_literal_escaping(self):
        self.assertEqual(self.seeder.sql_literal("O'Neil"), "'O''Neil'")
        self.assertEqual(self.seeder.sql_literal("正常"), "'正常'")
        self.assertEqual(self.seeder.sql_literal(None), "NULL")

    def test_cleanup_sql_targets_synthetic_markers(self):
        sql = self.seeder.build_cleanup_sql()
        self.assertIn("DELETE FROM app.mr_scan WHERE brxh LIKE 'SYNTH-BR%'", sql)
        self.assertIn("DELETE FROM app.mr_statistics WHERE cid LIKE 'SYNTH-C%'", sql)
        self.assertIn("BEGIN;", sql)
        self.assertIn("COMMIT;", sql)

    # ---------- 护栏 ----------

    def test_patient_cap(self):
        with self.assertRaises(ValueError) as ctx:
            self.small_plan(patients=self.seeder.DEFAULT_MAX_PATIENTS + 1)
        self.assertIn("上限", str(ctx.exception))

    def test_scan_row_cap(self):
        with self.assertRaises(ValueError) as ctx:
            self.small_plan(patients=5000, scans_per_archive=64)
        self.assertIn("上限", str(ctx.exception))
        relaxed = self.small_plan(patients=5000, scans_per_archive=64, max_scan_rows=320_000)
        self.assertEqual(relaxed["scan_rows"], 320_000)

    def test_apply_requires_confirmation_and_safe_target(self):
        with self.assertRaises(ValueError):
            self.seeder.check_target_database("mrr-prod")
        with self.assertRaises(ValueError):
            self.seeder.check_target_database("postgres")
        self.assertIsNone(self.seeder.check_target_database("imgapi-dev"))

    def test_extract_dbname(self):
        self.assertEqual(self.seeder.extract_dbname("postgresql://u:p@localhost:5432/imgapi-dev"), "imgapi-dev")
        self.assertEqual(self.seeder.extract_dbname("host=localhost dbname=mrr_test user=u"), "mrr_test")

    def test_main_mode_exclusive(self):
        with self.assertRaises(SystemExit):
            self.seeder.main(["--plan", "--emit", str(self.root / "x")])

    # ---------- emit / verify / plan ----------

    def test_emit_and_verify(self):
        out = self.root / "emit-suite"
        plan = self.small_plan(patients=4, scans_per_archive=3)
        manifest = self.seeder.emit(out, plan)
        self.assertEqual(manifest["materialized_images"], 12)
        self.assertTrue((out / "seed.sql").is_file())
        self.assertTrue((out / "cleanup.sql").is_file())
        self.assertTrue((out / "README.txt").is_file())
        first_scan = self.seeder.build_records(plan)["scans"][0]
        rel = self.seeder.image_relative_path(first_scan["folder"], first_scan["bah"],
                                              first_scan["sjh"], first_scan["brxh"],
                                              first_scan["filename"])
        self.assertTrue((out / "images" / rel).is_file())
        verify_spec = importlib.util.spec_from_file_location("generate_test_data", GENERATOR)
        generator = importlib.util.module_from_spec(verify_spec)
        verify_spec.loader.exec_module(generator)
        self.assertEqual(generator.verify(out), [])

    def test_emit_refuses_existing_dir(self):
        out = self.root / "emit-exists"
        self.seeder.emit(out, self.small_plan())
        with self.assertRaises(FileExistsError):
            self.seeder.emit(out, self.small_plan())

    def test_emit_deterministic_manifest(self):
        plan = self.small_plan(patients=3, scans_per_archive=2, seed=99)
        first = self.seeder.emit(self.root / "det-a", plan)
        second = self.seeder.emit(self.root / "det-b", plan)
        self.assertEqual(first["files"], second["files"], "同 seed 产物必须逐字节一致（manifest 不含时间戳）")

    def test_plan_makes_no_writes(self):
        before = sorted(p.name for p in self.root.iterdir())
        result = self.seeder.plan(patients=3, scans_per_archive=2, no_sjh_count=1, seed=1)
        after = sorted(p.name for p in self.root.iterdir())
        self.assertEqual(before, after, "--plan 不得写任何文件")
        self.assertEqual(result["patients"], 3)
        self.assertEqual(result["scan_rows"], 6)
        self.assertIn("sample", result)

    def test_no_images_skips_materialization(self):
        out = self.root / "emit-noimg"
        plan = self.small_plan(patients=4, scans_per_archive=3, no_images=True)
        manifest = self.seeder.emit(out, plan)
        self.assertEqual(manifest["materialized_images"], 0)
        self.assertFalse((out / "images").exists())


if __name__ == "__main__":
    unittest.main()
