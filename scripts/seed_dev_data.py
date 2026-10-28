#!/usr/bin/env python3
"""MRR 直连造数器：把纯合成数据批量灌入本地/独立 PostgreSQL 测试库。

与 scripts/generate_test_data.py 的关系：后者面向导入接口测试（CSV/契约，不连库）；
本工具面向开发演示，直接生成多行 INSERT SQL 并经 psql 单事务入库，
复用前者的合成编号规范（SYNTH 前缀、前导零、高位编号必须有上架号）与 PNG 绘制逻辑。

落库只写三张表并依赖应用自身的触发器：
  app.mr_patient     直接插入；
  app.mr_statistics  BEFORE INSERT 触发器 trg_mr_statistics_sync_archive 自动创建/同步 app.mr_archive；
  app.mr_scan        BEFORE INSERT 触发器 trg_mr_scan_resolve_archive 自动解析 archive_id。

仅 Python 3.10+ 标准库；数据库写入通过外部 psql 完成，本脚本不实现数据库驱动。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_test_data as fixtures  # noqa: E402  复用 PNG 绘制、digest、manifest 校验

# 防护上限：直连造数面向万级演示数据；更大规模走导入夹具或专用压测流程。
DEFAULT_MAX_PATIENTS = 10_000
DEFAULT_MAX_SCAN_ROWS = 200_000
HARD_MAX_SCAN_ROWS = 2_000_000
# 与 MedicalRecordCodeUtils.ARCHIVE_BAH_UNIQUE_LIMIT 保持一致；测试会读取 Java 源码断言。
BAH_UNIQUE_LIMIT = 10_000_000
MATERIALIZED_IMAGE_CAP = 48
INCOMPLETE_MARKER = "_INCOMPLETE"
INSERT_BATCH_ROWS = 500
DEPARTMENTS = ("合成内科", "合成外科", "合成儿科", "合成骨科")
FORBIDDEN_DB_NAMES = {"postgres", "template0", "template1"}
FORBIDDEN_DB_FRAGMENTS = ("prod",)

PATIENT_COLUMNS = ("bah", "name", "idcard", "admissiontime", "department", "ruyuan", "bingqu", "chuangwei")
STATISTICS_COLUMNS = ("bah", "cid", "openerno", "date", "type", "pages", "sjh",
                      "patientname", "inpatientdepartment", "patientid", "dischargedate")
SCAN_COLUMNS = ("brxh", "bah", "sjh", "filename", "btype", "pages", "openerno", "uploaddate",
                "uploadflag", "folder", "file_size", "checksum_md5", "migration_status")

README_TEXT = """MRR 直连造数器产物（纯合成数据，无真实患者或凭据）。

使用：
  psql "<dsn>" -v ON_ERROR_STOP=1 -f seed.sql
  （或生成时使用 --apply 由本工具调用 psql 自动执行）

影像：
  images/ 内的目录结构需整体复制到后端 image.basePath 指向的存储根目录，
  保持相对路径不变（例如 robocopy images\\ D:\\data\\ba-img /E）。

说明：
  全部病案号、上架号、brxh、患者标识带 SYNTH/合成 标记；身份证为空或 SYNTH-ID。
  app.mr_archive 与 mr_scan.archive_id 由数据库触发器自动创建/解析，不由 SQL 直接写入。
  重复执行 seed.sql 会产生重复统计行；清理用 cleanup.sql（仅限独立测试库执行）。
  回滚首选：销毁并重建独立测试库（DROP SCHEMA app CASCADE 后重放 Flyway）。
  不要在共享数据库按编号批量删除。
"""

_IMAGE_CACHE: dict = {}


def requires_sjh_for_bah(bah: str) -> bool:
    """镜像 MedicalRecordCodeUtils.requiresSjhForBah：纯数字且 >= 唯一性阈值。"""
    text = (bah or "").strip()
    return bool(re.fullmatch(r"[0-9]+", text)) and int(text) >= BAH_UNIQUE_LIMIT


def _segment(value, field: str) -> str:
    text = (value or "").strip()
    if not text or text in (".", "..") or any(ch in text for ch in "/\\:\0"):
        raise ValueError(f"{field} 非法: {value!r}")
    return text


def image_relative_path(folder: str, bah: str, sjh, brxh: str, filename: str) -> str:
    """镜像 ArchiveImagePathSupport.legacyRelativePath（AUTO/历史来源分支）。"""
    folder = _segment(folder, "folder")
    if len(folder) < 5:
        raise ValueError("folder 长度不足 5 位")
    bah = _segment(bah, "bah")
    filename = _segment(filename, "filename")
    directory_key = _segment(sjh if requires_sjh_for_bah(bah) else brxh, "directoryKey")
    return f"{folder[:5]}/{folder}/{directory_key}-{bah}/{filename}"


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int) and not isinstance(value, bool):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def image_info(btype: int) -> dict:
    """每种类型只绘制一次；PNG 内容固定，字节与 MD5 可确定性复现。"""
    if btype not in _IMAGE_CACHE:
        data = fixtures.png_page(f"TYPE {btype:02d}")
        _IMAGE_CACHE[btype] = {"bytes": data, "size": len(data), "md5": hashlib.md5(data).hexdigest()}
    return _IMAGE_CACHE[btype]


def record_at(index: int, plan: dict) -> dict:
    """第 index 条记录；以 (seed, index) 播种，纯函数，任意子集可独立复现。"""
    rng = random.Random(f"{plan['seed']}:{index}")
    day = date(2024, 1, 1) + timedelta(days=rng.randrange(900))
    ordinal = index + 1
    if index < plan["no_sjh_count"]:
        # 覆盖前导零与 7 位编号探针；其余低位编号保持唯一，保证触发器可唯一解析主档。
        bah = "00000001" if index == 0 else ("9999999" if index == 1 else f"{ordinal:08d}")
        sjh = None
    else:
        bah = str(BAH_UNIQUE_LIMIT + index - plan["no_sjh_count"])
        sjh = f"SYNTH-S{ordinal:08d}"
    name = f"合成测试患者{ordinal:08d}"
    department = DEPARTMENTS[index % len(DEPARTMENTS)]
    patient = {
        "bah": bah, "name": name,
        "idcard": "" if index % 4 == 0 else f"SYNTH-ID{index // 2:08d}",
        "admissiontime": f"{day} 08:30:00", "department": department,
        "ruyuan": str(day - timedelta(days=5)), "bingqu": "合成病区",
        "chuangwei": f"T{index % 20:02d}",
    }
    statistics = {
        "bah": bah, "cid": f"SYNTH-C{ordinal:08d}", "openerno": f"SYNTH-OP{ordinal:08d}",
        "date": str(day + timedelta(days=5)), "type": "住院病案",
        "pages": plan["scans_per_archive"], "sjh": sjh, "patientname": name,
        "inpatientdepartment": department, "patientid": f"SYNTH-P{ordinal:08d}",
        "dischargedate": str(day + timedelta(days=4)),
    }
    folder = day.strftime("%y.%m.%d")
    scans = []
    for page in range(plan["scans_per_archive"]):
        image = image_info((index * plan["scans_per_archive"] + page) % 16)
        scans.append({
            "brxh": f"SYNTH-BR{ordinal:08d}", "bah": bah, "sjh": sjh, "folder": folder,
            "filename": f"{ordinal:08d}-{page + 1:04d}.png",
            "btype": (index * plan["scans_per_archive"] + page) % 16,
            "pages": page + 1, "openerno": f"SYNTH-OP{ordinal:08d}",
            "uploaddate": str(day), "uploadflag": 1,
            "file_size": image["size"], "checksum_md5": image["md5"],
            "migration_status": "not_migrated",
        })
    return {"patient": patient, "statistics": statistics, "scans": scans}


def build_plan(patients: int, scans_per_archive: int, no_sjh_count: int, seed: int,
               materialized_images: int = MATERIALIZED_IMAGE_CAP, no_images: bool = False,
               max_scan_rows: int = DEFAULT_MAX_SCAN_ROWS) -> dict:
    if patients < 1:
        raise ValueError("patients 至少为 1")
    if patients > DEFAULT_MAX_PATIENTS:
        raise ValueError(
            f"患者数 {patients} 超过上限 {DEFAULT_MAX_PATIENTS}；"
            "直连造数面向万级以内演示数据，更大规模请用导入夹具或专用压测流程")
    if not 1 <= scans_per_archive <= 100:
        raise ValueError("scans-per-archive 必须为 1..100")
    no_sjh_count = max(0, min(no_sjh_count, patients))
    scan_rows = patients * scans_per_archive
    cap = min(max_scan_rows, HARD_MAX_SCAN_ROWS)
    if scan_rows > cap:
        raise ValueError(
            f"扫描行数 {scan_rows} 超过上限 {cap}；确认余量后可用 --max-rows 显式放宽")
    plan = {
        "seed": seed, "patients": patients, "scans_per_archive": scans_per_archive,
        "no_sjh_count": no_sjh_count, "scan_rows": scan_rows,
        "materialized_images": 0 if no_images else min(materialized_images, scan_rows),
        "no_images": no_images,
    }
    plan["sample"] = _sample(plan)
    return plan


def _sample(plan: dict) -> dict:
    """两条编号分支各取一条示例；只调用 record_at，不做任何写入。"""
    samples = {}
    if plan["no_sjh_count"] > 0:
        samples["no_sjh"] = _sample_at(0, plan)
    if plan["no_sjh_count"] < plan["patients"]:
        samples["with_sjh"] = _sample_at(plan["no_sjh_count"], plan)
    return samples


def _sample_at(index: int, plan: dict) -> dict:
    record = record_at(index, plan)
    first_scan = record["scans"][0]
    return {
        "patient": record["patient"], "statistics": record["statistics"],
        "scan": first_scan,
        "image_relative_path": image_relative_path(
            first_scan["folder"], first_scan["bah"], first_scan["sjh"],
            first_scan["brxh"], first_scan["filename"]),
    }


def build_records(plan: dict) -> dict:
    patients, statistics, scans = [], [], []
    for index in range(plan["patients"]):
        record = record_at(index, plan)
        patients.append(record["patient"])
        statistics.append(record["statistics"])
        scans.extend(record["scans"])
    return {"patients": patients, "statistics": statistics, "scans": scans}


def _insert_statement(table: str, columns, rows) -> str:
    values = ",\n".join(
        "(" + ", ".join(sql_literal(row[column]) for column in columns) + ")"
        for row in rows)
    return f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n{values};"


def build_seed_sql(records: dict, plan: dict | None = None) -> str:
    plan = plan or {}
    header = (f"-- MRR 直连造数批次：seed={plan.get('seed')} patients={len(records['patients'])} "
              f"scan_rows={len(records['scans'])}\n"
              "-- 纯合成数据，仅供独立测试库；不要在生产或共享库执行。\n")
    lines = [header, "SET client_encoding = 'UTF8';", "BEGIN;", ""]
    for table, columns, rows in (
            ("app.mr_patient", PATIENT_COLUMNS, records["patients"]),
            ("app.mr_statistics", STATISTICS_COLUMNS, records["statistics"]),
            ("app.mr_scan", SCAN_COLUMNS, records["scans"])):
        for start in range(0, len(rows), INSERT_BATCH_ROWS):
            lines.append(_insert_statement(table, columns, rows[start:start + INSERT_BATCH_ROWS]))
            lines.append("")
    lines.append("COMMIT;")
    return "\n".join(lines)


def build_cleanup_sql() -> str:
    return "\n".join([
        "-- 清理本工具产出的合成数据；仅在独立测试库执行。",
        "-- 首选回滚仍是销毁重建独立测试库；本脚本只是辅助手段。",
        "BEGIN;",
        "DELETE FROM app.mr_scan WHERE brxh LIKE 'SYNTH-BR%';",
        "DELETE FROM app.mr_statistics WHERE cid LIKE 'SYNTH-C%';",
        "DELETE FROM app.mr_archive WHERE sjh LIKE 'SYNTH-S%' OR patient_name LIKE '合成测试患者%';",
        "DELETE FROM app.mr_patient WHERE name LIKE '合成测试患者%';",
        "COMMIT;",
        "",
    ])


def write_images(records: dict, materialized_count: int, base: Path) -> int:
    base.mkdir(parents=True, exist_ok=True)
    written = 0
    for scan in records["scans"][:materialized_count]:
        relative = image_relative_path(scan["folder"], scan["bah"], scan["sjh"],
                                       scan["brxh"], scan["filename"])
        path = base.joinpath(*relative.split("/"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(image_info(scan["btype"])["bytes"])
        written += 1
    return written


def mark_incomplete(root: Path, error: BaseException) -> None:
    try:
        (root / INCOMPLETE_MARKER).write_text(
            "生成中断，本目录不可用于测试。删除该目录后重跑。\n"
            f"原因: {type(error).__name__}: {error}\n", encoding="utf-8")
    except OSError:
        pass


def emit(output, plan: dict) -> dict:
    """产出 seed.sql、cleanup.sql、影像目录与 manifest；目录必须不存在。"""
    root = Path(output)
    if root.exists():
        raise FileExistsError(f"输出目录已存在：{root}（每次必须使用新目录）")
    root.mkdir(parents=True)
    try:
        records = build_records(plan)
        (root / "seed.sql").write_text(build_seed_sql(records, plan), encoding="utf-8", newline="\n")
        (root / "cleanup.sql").write_text(build_cleanup_sql(), encoding="utf-8", newline="\n")
        (root / "README.txt").write_text(README_TEXT, encoding="utf-8")
        materialized = 0
        if not plan["no_images"]:
            materialized = write_images(records, plan["materialized_images"], root / "images")
        manifest = {
            "version": 2, "generator": "scripts/seed_dev_data.py", "seed": plan["seed"],
            "patients": plan["patients"], "scans_per_archive": plan["scans_per_archive"],
            "no_sjh_count": plan["no_sjh_count"], "scan_rows": plan["scan_rows"],
            "materialized_images": materialized,
            "tables": {"patients": len(records["patients"]),
                       "statistics": len(records["statistics"]),
                       "scans": len(records["scans"])},
            "files": [],
        }
        for path in sorted(root.rglob("*")):
            if path.is_file():
                manifest["files"].append({
                    "path": path.relative_to(root).as_posix(),
                    "bytes": path.stat().st_size,
                    "sha256": fixtures.digest(path),
                })
        fixtures.write_json(root / "manifest.json", manifest)
        return manifest
    except BaseException as error:
        mark_incomplete(root, error)
        raise


def extract_dbname(dsn: str) -> str:
    text = dsn.strip()
    if "://" in text:
        return urllib.parse.urlparse(text).path.lstrip("/").split("?")[0]
    match = re.search(r"\bdbname=([^\s]+)", text)
    return match.group(1) if match else ""


def check_target_database(dbname: str) -> None:
    """拒绝维护库与名字含 prod 的目标；其余目标必须显式 --confirm-target。"""
    if not dbname:
        raise ValueError("无法从 --dsn 解析数据库名；请使用 postgresql://host/dbname 形式")
    low = dbname.lower()
    if low in FORBIDDEN_DB_NAMES or any(fragment in low for fragment in FORBIDDEN_DB_FRAGMENTS):
        raise ValueError(f"拒绝目标数据库 {dbname!r}：维护库或疑似生产库")


def apply(dsn: str, plan: dict, storage_root=None, psql_path: str | None = None,
          confirm_target: bool = False, create_storage_root: bool = False) -> dict:
    if not confirm_target:
        raise ValueError("--apply 必须显式携带 --confirm-target 确认目标库")
    dbname = extract_dbname(dsn)
    check_target_database(dbname)
    psql = psql_path or shutil.which("psql")
    if not psql:
        raise ValueError("找不到 psql；请安装 PostgreSQL 客户端或用 --psql-path 指定")
    records = build_records(plan)
    materialized = 0
    if not plan["no_images"]:
        base = Path(storage_root) if storage_root else None
        if base is None:
            raise ValueError("写入影像需要 --storage-root，或使用 --no-images 仅造元数据")
        if not base.is_dir() and not create_storage_root:
            raise ValueError(f"--storage-root 不存在：{base}（确认后可加 --create-storage-root）")
        materialized = write_images(records, plan["materialized_images"], base)
    with tempfile.TemporaryDirectory(prefix="mrr-seed-") as temporary:
        sql_file = Path(temporary) / "seed.sql"
        sql_file.write_text(build_seed_sql(records, plan), encoding="utf-8", newline="\n")
        result = subprocess.run(
            [psql, dsn, "-v", "ON_ERROR_STOP=1", "-q", "-f", str(sql_file)],
            capture_output=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise RuntimeError(
            "psql 执行失败（事务已回滚；已写入的影像文件保留，可修正后重跑）：\n"
            + (result.stderr or result.stdout or "").strip()[-2000:])
    return {"applied": True, "dbname": dbname, "patients": len(records["patients"]),
            "scan_rows": len(records["scans"]), "materialized_images": materialized}


def plan(patients: int, scans_per_archive: int, no_sjh_count: int, seed: int, **kwargs) -> dict:
    """--plan 模式：只返回规模预估与样例，不写任何文件。"""
    return build_plan(patients, scans_per_archive, no_sjh_count, seed, **kwargs)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        description="把纯合成测试数据批量灌入本地/独立 PostgreSQL 测试库（经 psql 单事务）。",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--plan", action="store_true", help="只输出规模预估与样例，不写任何东西")
    modes.add_argument("--emit", type=Path, metavar="DIR", help="产出 SQL 与影像目录，不连库")
    modes.add_argument("--apply", action="store_true", help="生成并经 psql 单事务入库")
    modes.add_argument("--emit-cleanup", type=Path, metavar="DIR", help="只产出 cleanup.sql")
    modes.add_argument("--verify", type=Path, metavar="DIR", help="校验 --emit 产物的 manifest")
    parser.add_argument("--patients", type=int, default=200)
    parser.add_argument("--scans-per-archive", type=int, default=8)
    parser.add_argument("--no-sjh-count", type=int, default=2,
                        help="前 N 条走无上架号分支（低位唯一编号）")
    parser.add_argument("--seed", type=int, default=20261025)
    parser.add_argument("--materialized-images", type=int, default=MATERIALIZED_IMAGE_CAP,
                        help=f"实际落盘的 PNG 张数上限，默认 {MATERIALIZED_IMAGE_CAP}")
    parser.add_argument("--no-images", action="store_true", help="只造元数据，不写任何影像文件")
    parser.add_argument("--max-rows", type=int, default=DEFAULT_MAX_SCAN_ROWS,
                        help=f"扫描行数上限，默认 {DEFAULT_MAX_SCAN_ROWS}；仅用于显式放宽")
    parser.add_argument("--dsn", help="postgresql://user:pass@host:5432/dbname")
    parser.add_argument("--storage-root", type=Path, help="后端 image.basePath 指向的存储根目录")
    parser.add_argument("--create-storage-root", action="store_true",
                        help="允许 --apply 创建不存在的 --storage-root")
    parser.add_argument("--confirm-target", action="store_true",
                        help="确认目标数据库允许写入（--apply 必需）")
    parser.add_argument("--psql-path", help="psql 可执行文件路径；默认从 PATH 查找")
    args = parser.parse_args(argv)

    try:
        if args.verify:
            errors = fixtures.verify(args.verify)
            print(json.dumps({"ok": not errors, "errors": errors}, ensure_ascii=False))
            return int(bool(errors))
        if args.emit_cleanup:
            root = Path(args.emit_cleanup)
            if root.exists():
                parser.error(f"输出目录已存在：{root}")
            root.mkdir(parents=True)
            (root / "cleanup.sql").write_text(build_cleanup_sql(), encoding="utf-8", newline="\n")
            print(json.dumps({"written": str(root / "cleanup.sql")}, ensure_ascii=False))
            return 0
        plan_kwargs = dict(materialized_images=args.materialized_images, no_images=args.no_images,
                           max_scan_rows=args.max_rows)
        if args.plan:
            print(json.dumps(plan(args.patients, args.scans_per_archive, args.no_sjh_count,
                                  args.seed, **plan_kwargs), ensure_ascii=False, indent=2))
            return 0
        if args.emit:
            manifest = emit(args.emit, build_plan(args.patients, args.scans_per_archive,
                                                  args.no_sjh_count, args.seed, **plan_kwargs))
            print(json.dumps({key: manifest[key] for key in
                              ("patients", "scan_rows", "materialized_images")}, ensure_ascii=False))
            return 0
        if not args.dsn:
            parser.error("--apply 需要 --dsn")
        result = apply(args.dsn, build_plan(args.patients, args.scans_per_archive,
                                            args.no_sjh_count, args.seed, **plan_kwargs),
                       storage_root=args.storage_root, psql_path=args.psql_path,
                       confirm_target=args.confirm_target,
                       create_storage_root=args.create_storage_root)
        print(json.dumps(result, ensure_ascii=False))
        return 0
    except (ValueError, OSError, RuntimeError, FileNotFoundError) as error:
        parser.exit(2, f"失败: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
