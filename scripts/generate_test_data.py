#!/usr/bin/env python3
"""MRR 离线合成夹具。仅使用 Python 3.10+ 标准库，不访问网络或数据库。"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import random
import shutil
import struct
import zlib
from contextlib import ExitStack
from datetime import date, timedelta
from pathlib import Path

HEADERS = {
    "patients": "bah,name,idcard,ruyuan,admissiontime,department,bingqu,chuangwei".split(","),
    "statistics": "bah,cid,openerno,date,type,pages,sjh,patientname,inpatientdepartment,patientid,dischargedate".split(","),
    "scan": "sjh,bah,brxh,folder,filename,btype,filesize".split(","),
    "archive-boxes": "bah,sjh,box_no,expected_box_no,status,remark".split(","),
}
SOURCES = {
    key: "backend-repo/src/main/java/com/zjcxph/imgapi/service/" + name + ".java"
    for key, name in {
        "patients": "PatientImportService", "statistics": "StatisticsDataExchangeService",
        "scan": "ScanDataExchangeService", "archive-boxes": "ArchiveBoxDataExchangeService",
    }.items()
}
STORAGE_SOURCE = "backend-repo/src/main/java/com/zjcxph/imgapi/storage/ArchiveImagePathSupport.java"
TYPE_SOURCE = "backend-repo/src/main/java/com/zjcxph/imgapi/domain/MedicalRecordType.java"
TYPE_LABELS = ["暂未分类", "病案首页", "病程录", "手术记录", "术后病程录", "护理记录", "会诊单",
               "特殊检查", "检验单", "医嘱", "体温单", "新生儿", "出院记录", "大病史", "其它", "分娩记录"]

# 防护上限：避免一次误用的参数组合占满磁盘或产出百万级文件。
MAX_SCAN_ROWS = 20_000_000
MAX_IMPORT_FILES = 20_000
BYTES_PER_SCAN_ROW = 82
INCOMPLETE_MARKER = "_INCOMPLETE"
GENERATED_ENTRIES = ("imports", "cases", "media", "storage", "contracts", "migration",
                     "cases.json", "storage-cases.json", "expected.json", "manifest.json", "README.txt",
                     "cases.tmp", "storage-cases.tmp", "expected.tmp", "manifest.tmp", INCOMPLETE_MARKER)


def write_json(path: Path, value) -> None:
    """原子写入：先写同目录临时文件再替换，避免中断留下半截 JSON。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def digest(path: Path, algorithm: str = "sha256") -> str:
    result = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def csv_bytes(headers, rows, encoding: str = "utf-8") -> bytes:
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, lineterminator="\r\n")
    writer.writerow(headers)
    writer.writerows(rows)
    return stream.getvalue().encode(encoding)


def verdict_of(expected: dict) -> str:
    """把期望结果归一为单一判定，供脚本消费；probe 型不再是“确定通过/拒绝”。"""
    if "exception" in expected:
        return "exception"
    if expected.get("canImport") is True:
        return "accept"
    if expected.get("canImport") is False:
        return "reject"
    return "observe"


class CsvParts:
    """流式分卷，不把大批量病案保存在内存；按批提交以降低逐行写入开销。"""
    FLUSH_ROWS = 2000

    def __init__(self, root: Path, dataset: str, chunk_rows: int):
        self.root, self.dataset, self.chunk_rows = root, dataset, chunk_rows
        self.count = 0
        self.stream = None
        self.writer = None
        self.pending = []

    def __enter__(self) -> "CsvParts":
        return self

    def __exit__(self, exc_type, exc, traceback) -> bool:
        if exc_type is None:
            self.close()
        else:
            # 异常路径下关闭失败不得覆盖原始异常。
            try:
                self.close()
            except OSError:
                pass
        return False

    def add(self, row) -> None:
        if self.count % self.chunk_rows == 0:
            self.close()
            part = self.count // self.chunk_rows + 1
            path = self.root / f"{self.dataset}-{part:04d}.csv"
            self.stream = path.open("w", encoding="utf-8", newline="")
            self.writer = csv.writer(self.stream, lineterminator="\r\n")
            self.writer.writerow(HEADERS[self.dataset])
        self.pending.append(row)
        self.count += 1
        if len(self.pending) >= self.FLUSH_ROWS:
            self.flush()

    def flush(self) -> None:
        # pending 上限为 chunk_rows（跨卷前必定 close），因此不会把数据串到下一卷。
        if self.pending and self.writer is not None:
            self.writer.writerows(self.pending)
            self.pending.clear()

    def close(self) -> None:
        if self.stream is None:
            return
        try:
            self.flush()
        finally:
            self.stream.close()
            self.stream = None
            self.writer = None


# 小型 5x7 字体，仅为扫描测试页绘制明显的英文合成标识，不依赖系统字体。
FONT = dict(zip("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-: ", (
    "0E11111F111111", "1E11111E11111E", "0E11101010110E", "1E11111111111E",
    "1F10101E10101F", "1F10101E101010", "0E11101711110F", "1111111F111111",
    "0E04040404040E", "0702020212120C", "11121418141211", "1010101010101F",
    "111B1515111111", "11191513111111", "0E11111111110E", "1E11111E101010",
    "0E11111115120D", "1E11111E141211", "0F10100E01011E", "1F040404040404",
    "1111111111110E", "11111111110A04", "1111111515150A", "11110A040A1111",
    "11110A04040404", "1F01020408101F", "0E11131519110E", "040C040404040E",
    "0E11010204081F", "1E01010601011E", "02060A121F0202", "1F10101E01011E",
    "0E10101E11110E", "1F010204080808", "0E11110E11110E", "0E11110F01010E",
    "0000001F000000", "00040000040000", "00000000000000",
)))


def png_page(label: str, blank: bool = False, rotate: bool = False) -> bytes:
    width, height = 640, 480
    pixels = bytearray([255]) * (width * height)

    def line(text, y, scale=3):
        for index, char in enumerate(text):
            pattern = bytes.fromhex(FONT.get(char, FONT[" "]))
            for row, bits in enumerate(pattern):
                top = y + row * scale
                if top >= height:
                    return
                for col in range(5):
                    if bits & (1 << (4 - col)):
                        left = 20 + index * 6 * scale + col * scale
                        for dy in range(scale):
                            row_start = (top + dy) * width
                            start = row_start + left
                            end = min(start + scale, row_start + width)
                            # 超出右边界时裁剪，不静默折行到下一行像素。
                            if end > start:
                                pixels[start:end] = bytes(end - start)

    if not blank:
        line("MRR SYNTHETIC TEST ONLY", 30)
        line("NO REAL PATIENT DATA", 80)
        line(label, 140)
        for y in range(220, 420, 35):
            pixels[y * width + 20:y * width + 610] = bytes(590)
    if rotate:
        pixels = bytearray(pixels[(height - 1 - x) * width + y] for y in range(width) for x in range(height))
        width, height = height, width

    def chunk(kind, content):
        return struct.pack(">I", len(content)) + kind + content + struct.pack(">I", zlib.crc32(kind + content))

    raw = b"".join(b"\0" + pixels[y * width:(y + 1) * width] for y in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 0, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def two_page_tiff():
    """两页未压缩灰度 TIFF，测试多页解码；不依赖本机编解码器。"""
    width, height = 64, 64
    ifd_size = 2 + 9 * 12 + 4
    data_start = 8 + 2 * ifd_size
    output = bytearray(b"II" + struct.pack("<HI", 42, 8))
    for page in range(2):
        entries = [(256, 4, width), (257, 4, height), (258, 3, 8), (259, 3, 1), (262, 3, 1),
                   (273, 4, data_start + page * width * height), (277, 3, 1), (278, 4, height),
                   (279, 4, width * height)]
        output.extend(struct.pack("<H", len(entries)))
        for tag, field_type, value in entries:
            output.extend(struct.pack("<HHII", tag, field_type, 1, value))
        output.extend(struct.pack("<I", 8 + ifd_size if page == 0 else 0))
    output.extend(bytes([220]) * (width * height))
    output.extend(bytes([120]) * (width * height))
    return bytes(output)


def two_page_pdf():
    """固定 ASCII 文本 PDF；明确的合成标识，供解码和导出故障排查。"""
    objects = [b"<< /Type /Catalog /Pages 2 0 R >>",
               b"<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>"]
    for stream in (6, 7):
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents {stream} 0 R >>".encode())
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    for page in (1, 2):
        content = (f"BT /F1 22 Tf 50 770 Td (MRR SYNTHETIC TEST ONLY) Tj 0 -45 Td "
                   f"(NO REAL PATIENT DATA - PAGE {page}) Tj ET").encode()
        objects.append(f"<< /Length {len(content)} >>\nstream\n".encode() + content + b"\nendstream")
    output, offsets = bytearray(b"%PDF-1.4\n"), [0]
    for index, obj in enumerate(objects, 1):
        offsets.append(len(output))
        output.extend(f"{index} 0 obj\n".encode() + obj + b"\nendobj\n")
    xref = len(output)
    output.extend(f"xref\n0 {len(offsets)}\n0000000000 65535 f \n".encode())
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode())
    output.extend(f"trailer\n<< /Size {len(offsets)} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return bytes(output)


def generate_cases(root: Path, baseline: dict) -> int:
    cases = []

    def case(name, dataset, rows, expected, *, headers=None, encoding="utf-8", setup="独立空测试库", raw=None):
        if dataset == "archive-boxes" and setup == "独立空测试库":
            setup = "独立测试库先导入基线统计（或对应低位编号统计）；不预先导入装箱记录"
        expected = dict(expected)
        expected["verdict"] = verdict_of(expected)
        relative = f"cases/{name}.csv"
        (root / relative).write_bytes(raw if raw is not None else csv_bytes(headers or HEADERS[dataset], rows, encoding))
        cases.append(dict(id=name, dataset=dataset, file=relative, encoding=encoding, setup=setup, expected=expected))

    for dataset, row in baseline.items():
        note = "新库；装箱须先导入基线统计" if dataset == "archive-boxes" else "新库；仅在独立测试库中重复导入同一行"
        case(dataset + "-duplicate", dataset, [row, row],
             {"canImport": True, "validRows": 1, "duplicateRows": 1, "note": note})
        case(dataset + "-missing-header", dataset, [row[:-1]], {"canImport": False}, headers=HEADERS[dataset][:-1])
        case(dataset + "-duplicate-header", dataset, [row + [row[0]]], {"canImport": False}, headers=HEADERS[dataset] + [HEADERS[dataset][0]])
        case(dataset + "-unknown-header", dataset, [row + ["ignored?"]], {"canImport": False}, headers=HEADERS[dataset] + ["unknown_column"])
        case(dataset + "-bom", dataset, [row], {"canImport": True}, encoding="utf-8-sig")
        case(dataset + "-gb18030", dataset, [row], {"canImport": True}, encoding="gb18030")
        case(dataset + "-empty", dataset, [], {"canImport": True, "totalRows": 0})
        case(dataset + "-extra-cell-probe", dataset, [row + ["MUST-NOT-DISAPPEAR"]],
             {"probe": "多余单元格不得静默丢失；当前toValueMap可能忽略，记录实际行为"})

    def mutated(name, dataset, changes, expected=None):
        row = dict(zip(HEADERS[dataset], baseline[dataset]))
        row.update(changes)
        case(name, dataset, [[row[h] for h in HEADERS[dataset]]], expected or {"canImport": False, "errorRows": 1})
        if dataset == "scan" and not row["sjh"] and expected and expected.get("canImport"):
            cases[-1]["setup"] = "独立库先导入bah=9999999且sjh为空的统计，建立唯一低位主档"

    mutated("patients-scientific", "patients", {"bah": "1.234E+7", "idcard": "9.999E+17"})
    mutated("patients-bad-date", "patients", {"ruyuan": "2026-99-01", "admissiontime": "bad-time"})
    mutated("patients-missing-name", "patients", {"name": ""},
            {"canImport": None, "probe": "当前患者导入只要求bah非空，name允许空；若业务要求必填，另行确认需求"})
    mutated("patients-long-name", "patients", {"name": "合" * 2001})
    mutated("patients-quoted", "patients", {"name": '合成,患者"引号"\n第二行'}, {"canImport": True, "validRows": 1})
    mutated("patients-formula-literal", "patients", {"name": "=1+1"},
            {"canImport": None, "probe": "导出应作为文本保护；不应在表格中执行公式"})
    mutated("patients-invisible-code", "patients", {"bah": "00000001\u200b"},
            {"probe": "零宽字符不得与正常编号静默错关联；检查错误提示和存储原值"})
    case("patients-error-cap", "patients", [["", "合成错误行", "", "", "", "", "", ""]] * 205,
         {"canImport": False, "errorRows": 205, "errorsTruncated": True, "reportedErrors": 200})
    case("patients-empty-file", "patients", [], {"exception": "空文件应拒绝"}, raw=b"")
    for dataset in ("statistics", "scan", "archive-boxes"):
        mutated(dataset + "-high-missing-sjh", dataset, {"bah": "10000000", "sjh": ""})
        mutated(dataset + "-no-identifiers", dataset, {"bah": "", "sjh": ""})
        mutated(dataset + "-low-no-sjh", dataset, {"bah": "9999999", "sjh": ""},
                {"canImport": True, "note": "装箱先导入同编号统计；低位编号不补零"})
    mutated("statistics-negative-pages", "statistics", {"pages": "-1"})
    mutated("statistics-int-overflow", "statistics", {"pages": "2147483648"})
    mutated("statistics-bad-date", "statistics", {"date": "2026-99-01"})
    mutated("statistics-calendar-probe", "statistics", {"date": "2025-02-29"},
            {"probe": "当前基线（含 7756c98d / #361，uuuu-M-d + STRICT）应拒绝并定位到 date；"
                      "若观测到归一化到月末，说明被测版本早于该提交"})
    mutated("scan-type-16", "scan", {"btype": "16"})
    mutated("scan-negative-size", "scan", {"filesize": "-1"})
    mutated("scan-long-overflow", "scan", {"filesize": "9223372036854775808"})
    mutated("scan-empty-folder", "scan", {"folder": ""})
    mutated("scan-empty-filename", "scan", {"filename": ""})
    mutated("scan-path-traversal", "scan", {"folder": "../outside", "filename": "test.png"},
            {"probe": "导入是否接受须实测；内容网关必须拒绝越界读取，不得泄露路径"})
    mutated("scan-encoded-traversal", "scan", {"folder": "%2e%2e/outside"},
            {"probe": "内容网关拒绝编码穿越；记录导入与读取两个阶段的结果"})
    mutated("scan-unc-probe", "scan", {"folder": "\\\\storage.invalid\\synthetic"},
            {"probe": "不应访问未配置 UNC 源；只用测试 stub，禁止真实网络探测"})
    mutated("archive-boxes-status", "archive-boxes", {"status": "INVALID"})
    mutated("archive-boxes-empty-box", "archive-boxes", {"box_no": ""})
    mutated("archive-boxes-missing", "archive-boxes", {"box_no": "", "status": "MISSING"}, {"canImport": True})
    mutated("archive-boxes-unresolved", "archive-boxes", {"bah": "10000099", "sjh": "SYNTH-NOT-FOUND"})
    for dataset, field, value in (("statistics", "pages", "99"), ("scan", "btype", "15"), ("archive-boxes", "remark", "合成冲突")):
        row = list(baseline[dataset])
        row[HEADERS[dataset].index(field)] = value
        case(dataset + "-conflict", dataset, [baseline[dataset], row], {"canImport": False, "errorRows": 1})
    write_json(root / "cases.json", cases)
    return len(cases)


def generate(root, records=12, scans_per_record=4, chunk_rows=50000, seed=20261018,
             max_scan_rows=MAX_SCAN_ROWS, force=False) -> dict:
    root = Path(root)
    if not 12 <= records <= 10_000_000:
        raise ValueError("records 必须为 12..10000000（预留编号边界场景）")
    if not 1 <= scans_per_record <= 10000 or not 1 <= chunk_rows <= 100000:
        raise ValueError("scans-per-record 必须为 1..10000，chunk-rows 为 1..100000")
    scan_rows = records * scans_per_record
    if scan_rows > max_scan_rows:
        estimated = scan_rows * BYTES_PER_SCAN_ROW
        size = (f"{estimated / 1024 ** 3:.1f} GiB" if estimated >= 1024 ** 3
                else f"{estimated / 1024 ** 2:.0f} MiB")
        raise ValueError(
            f"扫描行数 {scan_rows} 超过上限 {max_scan_rows}（约需 {size} 磁盘）；"
            "确认余量后可用 --max-scan-rows 显式放宽")
    record_parts = -(-records // chunk_rows)
    scan_parts = -(-scan_rows // chunk_rows)
    import_files = record_parts * (len(HEADERS) - 1) + scan_parts
    if import_files > MAX_IMPORT_FILES:
        raise ValueError(
            f"分卷过细：病案类各 {record_parts} 卷、扫描 {scan_parts} 卷，合计 {import_files} 个 CSV，"
            f"超过上限 {MAX_IMPORT_FILES}；请调大 --chunk-rows")
    if force and root.exists():
        if not (root / INCOMPLETE_MARKER).is_file():
            raise FileExistsError(
                f"拒绝清理完整目录：{root}（--force 只回收带 {INCOMPLETE_MARKER} 标记的中断目录）")
        discard_incomplete(root)
    # mkdir(exist_ok=False) 拒绝重用目录，也不会删除或覆盖原始文件。
    root.mkdir(parents=True, exist_ok=False)
    try:
        return _populate(root, records, scans_per_record, chunk_rows, seed)
    except BaseException as error:
        mark_incomplete(root, error)
        raise


def mark_incomplete(root: Path, error: BaseException) -> None:
    """写入中断标记，使下次可以 --force 安全回收，而不是只留一条 WinError 183。"""
    try:
        (root / INCOMPLETE_MARKER).write_text(
            "生成中断，本目录不可用于测试。\n"
            "删除该目录后重跑，或加 --force 直接回收。\n"
            f"原因: {type(error).__name__}: {error}\n",
            encoding="utf-8")
    except OSError:
        pass


def discard_incomplete(root: Path) -> None:
    """只删除生成器自己创建的顶层条目；目录内出现其他文件时拒绝回收，交人工确认。"""
    if not root.is_dir():
        raise FileExistsError(f"拒绝回收：{root} 不是目录")
    unknown = [path.name for path in root.iterdir() if path.name not in GENERATED_ENTRIES]
    if unknown:
        raise FileExistsError(
            f"拒绝回收：{root} 含非生成器条目 {sorted(unknown)[:3]}，请人工确认后删除")
    for name in GENERATED_ENTRIES:
        target = root / name
        if target.is_dir():
            shutil.rmtree(target)
        elif target.exists():
            target.unlink()
    root.rmdir()


def _populate(root: Path, records: int, scans_per_record: int, chunk_rows: int, seed: int) -> dict:
    for directory in ("imports", "cases", "media", "storage", "contracts", "migration"):
        (root / directory).mkdir()
    rng = random.Random(seed)
    image_cache = {}

    def image_for(btype: int) -> bytes:
        # 只生成实际出现的类型，小规模场景不必白算 16 张。
        if btype not in image_cache:
            image_cache[btype] = png_page(f"TYPE {btype:02d}")
        return image_cache[btype]

    baseline, materialized = {}, []
    with ExitStack() as stack:
        writers = {key: stack.enter_context(CsvParts(root / "imports", key, chunk_rows)) for key in HEADERS}
        for index in range(records):
            bah = ["00000001", "9999999", "10000000", "10000000"][index] if index < 4 else str(10000001 + index)
            sjh = f"SYNTH-S{index + 1:08d}"
            name = f"合成测试患者{index + 1:08d}"
            day = date(2024, 1, 1) + timedelta(days=rng.randrange(900))
            department = ["合成内科", "合成外科", "合成儿科"][index % 3]
            patient = [bah, name, "" if index % 4 == 0 else f"SYNTH-ID{index // 2:08d}", str(day),
                       f"{day} 08:30:00", department, "合成病区", f"T{index % 20:02d}"]
            statistics = [bah, "SYNTH-C", "SYNTH-OP", str(day + timedelta(days=5)), "住院病案",
                          scans_per_record, sjh, name, department, f"SYNTH-P{index + 1:08d}", str(day + timedelta(days=4))]
            box = [bah, sjh, f"SYNTH-BOX{index // 10:06d}", f"SYNTH-BOX{index // 10:06d}", "NORMAL", "纯合成数据"]
            for key, row in (("patients", patient), ("statistics", statistics), ("archive-boxes", box)):
                writers[key].add(row)
                baseline.setdefault(key, row)
            # folder/brxh 只随记录变化，提到扫描循环外，避免每个扫描行重复格式化。
            folder = day.strftime("%y.%m.%d")
            brxh = f"SYNTH-BR{index + 1:08d}"
            for page in range(scans_per_record):
                sequence = index * scans_per_record + page
                btype = sequence % 16
                filename = f"{index + 1:08d}-{page + 1:04d}.png"
                scan = [sjh, bah, brxh, folder, filename, btype, len(image_for(btype))]
                writers["scan"].add(scan)
                baseline.setdefault("scan", scan)
                if sequence < 48:
                    # 对齐现有 ArchiveImagePathSupport.legacyRelativePath；folder必须是单段。
                    directory_key = sjh if int(bah) >= 10000000 else brxh
                    path = root / "storage" / folder[:5] / folder / (directory_key + "-" + bah) / filename
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(image_for(btype))
                    materialized.append({"path": path.relative_to(root).as_posix(), "bah": bah, "sjh": sjh,
                                         "btype": btype, "md5": digest(path, "md5"), "bytes": path.stat().st_size})
    (root / "media/blank.png").write_bytes(png_page("", blank=True))
    (root / "media/rotated.png").write_bytes(png_page("TYPE 01", rotate=True))
    (root / "media/multipage.tiff").write_bytes(two_page_tiff())
    (root / "media/two-pages.pdf").write_bytes(two_page_pdf())
    (root / "media/corrupt.png").write_bytes(b"\x89PNG\r\n\x1a\nTRUNCATED SYNTHETIC")
    (root / "media/zero-byte.png").write_bytes(b"")
    cases_count = generate_cases(root, baseline)
    (root / "migration/legacy-patients.csv").write_bytes(csv_bytes(
        ["brxh", "id", "bah", "name", "idcard", "ruyuan", "admissiontime", "department", "chuangwei", "bingqu", "keshicode", "bingqucode"],
        [["SYNTH-BR01", "SYNTH-OLD-ID", "00000001", "合成历史患者", "SYNTH-LEGACY-ID", "2024/02/29", "2024-02-29 08:30:00", "合成科室", "T01", "合成病区", "SYNTH-K", "SYNTH-B"],
         ["SYNTH-BR02", "SYNTH-OLD-ID2", "1", "合成前导零探针", "", "2025-02-29", "", "合成科室", "T02", "合成病区", "SYNTH-K", "SYNTH-B"]]))
    (root / "migration/legacy-scan.csv").write_bytes(csv_bytes(HEADERS["scan"], [
        ["", "10000000", "SYNTH-BR01", "26.01/synthetic", "0001.png", "0", "0"],
        [" 000123 ", " 00000001 ", "SYNTH-BR02", "26.01/synthetic", "0002.png", "15", "2147483648"],
        ["123", "1", "SYNTH-BR03", "26.01/synthetic", "0002.png", "16", "-1"],
    ]))
    write_json(root / "migration/expectations.json", {
        "legacy-patients.csv": ["字段顺序与现代模板不同，必须按表头映射bingqu/chuangwei", "brxh/id/keshicode/bingqucode被现有患者导入显式忽略，不可误认为已落库", "前导零保留；1与00000001精确查询及兼容歧义需分别验证", "2025-02-29作为非法日期探针"],
        "legacy-scan.csv": ["第2行高位编号缺sjh，整文件禁止正式写入", "第3行trim但不补零，filesize超过32位仍需正确读取", "第4行同目录同文件冲突、类型越界及负大小均需定位"],
        "procedure": ["保留源文件与SHA256", "只读预检统计行数/空值/冲突", "分卷staging；异常隔离", "使用现有导入服务执行小批事务", "核对成功/跳过/失败数与关联", "重跑同卷检查幂等", "失败回滚及游标恢复"],
        "quality_state_recipes": {
            "SCAN_ARCHIVE_LINK_MISSING": "在独立库通过测试fixture建立未关联扫描，保留有效uploadflag；注意当前质量检测有最近ID窗口/估算",
            "SCAN_ARCHIVE_LINK_MISMATCH_RECENT": "在测试事务内把扫描archive_id指向不同编号主档；事务结束回滚",
            "MIGRATED_WITHOUT_OSS_URL": "测试事务设置migration_status=migrated、oss_url为空",
            "MIGRATION_SUCCESS_UNVERIFIED": "测试事务建立迁移成功但verified_at为空的日志",
            "DUPLICATE_SJH": "000123与123比较键冲突应检测；若当前约束拒绝则断言约束拒绝，不禁用约束",
            "MIGRATION_SCAN_ORPHAN": "如外键拒绝孤儿日志，断言拒绝；不删除外键以凑样本",
        },
        "status": "原始脏数据和注入配方；不能作为正常基线导入，不含自动数据库写入SQL",
    })
    write_json(root / "storage-cases.json", {
        "good": materialized,
        "missing": {"path": "storage/synthetic/missing/0001.png", "expected": "受控失败；迁移计入失败；可重试"},
        "corrupt": {"path": "media/corrupt.png", "expected": "解码失败，不输出半成品成功状态"},
        "zero_byte": {"path": "media/zero-byte.png", "expected": "拒绝或明确记录空文件"},
        "checksum_mismatch": {"path": materialized[0]["path"], "expected_md5": "0" * 32,
                              "actual_md5": materialized[0]["md5"], "expected": "校验失败，不标记 verified，不删源文件"},
        "size_mismatch": {"path": materialized[0]["path"], "declared_bytes": materialized[0]["bytes"] + 1,
                          "expected": "源与目标分别核对大小/校验和；CSV filesize 是元数据，不证明文件完整"},
        "fault_injection": ["读取超时", "目标写入失败", "第2批提交前中断", "第2批提交后响应丢失", "取消", "源读取权限拒绝", "目标校验失败"],
        "recovery": "测试 adapter 注入故障；记录批次、游标、成功/失败数；重试不得重复或跳过；不对真实 NAS/OSS 注入故障",
    })
    write_json(root / "contracts/ocr.json", [
        {"id": f"type-{i:02d}", "formal_type": i, "ocr_text": f"纯合成测试 {label} 无真实患者内容",
         "suggested_type": i, "expected": "OCR文本、建议与正式分类分离；已有正式分类不得静默覆盖",
         "status": "规则关键词与阈值需按待测版本配置；非准确率基准"}
        for i, label in enumerate(TYPE_LABELS)
    ] + [{"id": "ambiguous", "formal_type": 0, "ocr_text": "病案首页 手术记录", "suggested_type": None,
          "expected": "多类型冲突不得静默确认为正式分类"}])
    write_json(root / "contracts/security.json", {
        "provisioning": "通过现有账号服务创建临时测试用户；口令、Token、票据运行时生成，不写入夹具",
        "sessions": ["anonymous", "active-without-permission", "pending", "rejected", "disabled", "password-version-stale", "temporary-password-expired", "valid-authorized"],
        "assertions": ["无Token返回401", "无权限拒绝且无副作用", "外部票据过期/篡改/不可信IP被拒绝", "跨用户任务不可下载或取消", "敏感响应无public缓存", "审计操作者取服务端身份", "错误不含存储路径或凭据"],
        "status": "场景契约，不能直接导入；搭配已有Auth/ExternalArchive/Export测试执行",
    })
    write_json(root / "expected.json", {
        "setup": "独立空库先运行现有 Flyway；依次导入 patients、statistics、scan、archive-boxes；每个分卷先 dryRun=true",
        "first_import": {key: {"canImport": True, "insertedRows": writer.count} for key, writer in writers.items()},
        "repeat_import": {"patients": {"insertedRows": 0, "duplicateRows": records},
                          "statistics": {"insertedRows": 0, "updatedRows": records},
                          "scan": {"insertedRows": 0, "updatedRows": 0, "duplicateRows": records * scans_per_record},
                          "archive-boxes": {"insertedRows": 0, "updatedRows": records}},
        "record_totals": records, "scan_totals": records * scans_per_record,
        "page_totals": records * scans_per_record,
        "quality": {"PATIENT_ARCHIVE_AMBIGUOUS": 2, "note": "两份10000000病案对应不同上架号；这些是有意的歧义样本"},
        "rollback": "销毁独立测试数据库；删除本次新建的输出目录；不要按 bah 在共享数据库批量删除",
    })
    (root / "README.txt").write_text(
        "MRR 纯合成离线测试数据，无真实患者或凭据。\n"
        "使用说明：docs/engineering/synthetic-test-data.md\n"
        "imports/ 是可导入基线；cases/ 逐个隔离执行，按 cases.json 判断结果。\n"
        "contracts/ 是测试契约，不可导入；media/ 包含故意损坏样本。\n"
        "最多实体化48张扫描图；大数据规模的其余记录仅供导入/查询测试，不可用于完整性通过断言。\n"
        "expected.json 是预期，不是实际执行结果。manifest.json 可核验文件完整性。\n", encoding="utf-8")
    manifest = {"version": 1, "generator": "scripts/generate_test_data.py", "seed": seed,
                "records": records, "scans_per_record": scans_per_record, "chunk_rows": chunk_rows,
                "materialized_images": len(materialized), "metadata_only_scans": records * scans_per_record - len(materialized),
                "cases": cases_count, "sources": SOURCES,
                "storage_path_source": STORAGE_SOURCE, "type_source": TYPE_SOURCE,
                "files": []}
    for path in sorted(root.rglob("*")):
        if path.is_file():
            manifest["files"].append({"path": path.relative_to(root).as_posix(), "bytes": path.stat().st_size, "sha256": digest(path)})
    write_json(root / "manifest.json", manifest)
    return manifest


def _listed_files(root: Path) -> set:
    return {path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()}


def verify(root) -> list:
    """核验产物：既要求清单内文件存在且校验一致，也要求磁盘上没有清单外文件。"""
    root = Path(root).resolve()
    manifest_path = root / "manifest.json"
    if not manifest_path.is_file():
        return [f"缺少 manifest: {manifest_path.name}（生成可能被中断，或目录不完整）"]
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except ValueError as error:
        return [f"manifest 解析失败: {error}"]
    if not isinstance(manifest, dict) or not isinstance(manifest.get("files"), list):
        return ["manifest 结构无效: files 必须是数组"]
    errors = []
    declared = set()
    for item in manifest["files"]:
        if not isinstance(item, dict) or not {"path", "bytes", "sha256"} <= set(item):
            errors.append(f"manifest 条目结构无效: {item!r}")
            continue
        declared.add(str(item["path"]))
        path = (root / item["path"]).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            errors.append(f"缺失或越界: {item['path']}")
        elif path.stat().st_size != item["bytes"] or digest(path) != item["sha256"]:
            errors.append(f"校验不一致: {item['path']}")
    for extra in sorted(_listed_files(root) - declared - {manifest_path.name, INCOMPLETE_MARKER}):
        errors.append(f"未登记文件: {extra}")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--verify", type=Path)
    parser.add_argument("--records", type=int, default=12)
    parser.add_argument("--scans-per-record", type=int, default=4)
    parser.add_argument("--chunk-rows", type=int, default=50000)
    parser.add_argument("--seed", type=int, default=20261018)
    parser.add_argument("--max-scan-rows", type=int, default=MAX_SCAN_ROWS,
                        help=f"扫描行数上限，默认 {MAX_SCAN_ROWS}；仅用于显式放宽磁盘占用防护")
    parser.add_argument("--force", action="store_true",
                        help=f"回收带 {INCOMPLETE_MARKER} 标记的中断目录后重新生成；完整目录仍拒绝覆盖")
    args = parser.parse_args()
    if bool(args.output) == bool(args.verify):
        parser.error("必须且只能指定 --output 或 --verify")
    try:
        if args.verify:
            errors = verify(args.verify)
            print(json.dumps({"ok": not errors, "errors": errors}, ensure_ascii=False))
            return int(bool(errors))
        result = generate(args.output, args.records, args.scans_per_record, args.chunk_rows, args.seed,
                          max_scan_rows=args.max_scan_rows, force=args.force)
        print(json.dumps({key: result[key] for key in ("records", "cases", "materialized_images", "metadata_only_scans")}, ensure_ascii=False))
        return 0
    except (ValueError, OSError, KeyError, TypeError) as error:
        if args.output and (Path(args.output) / INCOMPLETE_MARKER).is_file():
            parser.exit(2, f"生成失败: {error}\n"
                           f"输出目录不完整（已写入 {INCOMPLETE_MARKER} 标记）：{args.output}\n"
                           "删除该目录后重跑，或加 --force 直接回收。\n")
        parser.exit(2, f"生成/校验失败: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
