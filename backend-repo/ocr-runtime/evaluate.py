"""Generate aggregate independent-validation evidence without emitting patient text or identifiers."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys


def distance(left, right):
    try:
        from rapidfuzz.distance import Levenshtein
        return Levenshtein.distance(left, right)
    except ImportError:
        # Standard-library fallback; the optional native implementation speeds up large validation sets.
        if len(left) > len(right):
            left, right = right, left
        previous = list(range(len(left) + 1))
        for row, r in enumerate(right, 1):
            current = [row]
            for column, l in enumerate(left, 1):
                current.append(min(current[-1] + 1, previous[column] + 1, previous[column - 1] + (l != r)))
            previous = current
        return previous[-1]


def evaluate(records, development_groups, rule_version, model_checksum):
    report = {"schemaVersion": 1, "independent": True, "ruleVersion": rule_version,
              "modelChecksum": model_checksum, "totalPages": 0, "autoCandidates": 0,
              "autoErrors": 0, "clearPrintedPages": 0, "clearPrintedCer": 0,
              "byType": {str(t): {"count": 0, "errors": 0} for t in range(1, 16)}}
    edits = characters = 0
    seen_pages = set()
    for row in records:
        group = row.get("groupHash", "")
        if not re.fullmatch(r"[a-f0-9]{64}", group) or group in development_groups:
            raise ValueError("INVALID_OR_OVERLAPPING_GROUP")
        page = row.get("pageHash", "")
        if not re.fullmatch(r"[a-f0-9]{64}", page) or page in seen_pages:
            raise ValueError("INVALID_OR_DUPLICATE_PAGE")
        seen_pages.add(page)
        expected, predicted = row.get("expectedType"), row.get("predictedType")
        if type(expected) is not int or expected not in range(16) or (predicted is not None and (type(predicted) is not int or predicted not in range(1, 16))):
            raise ValueError("INVALID_LABEL")
        if type(row.get("autoEligible")) is not bool or type(row.get("clearPrinted")) is not bool:
            raise ValueError("INVALID_LABEL")
        report["totalPages"] += 1
        if report["totalPages"] > 100000:
            raise ValueError("VALIDATION_LIMIT")
        if row["autoEligible"]:
            if predicted is None:
                raise ValueError("INVALID_LABEL")
            error = int(expected != predicted)
            report["autoCandidates"] += 1
            report["autoErrors"] += error
            report["byType"][str(predicted)]["count"] += 1
            report["byType"][str(predicted)]["errors"] += error
        if row["clearPrinted"]:
            reference, recognized = row.get("referenceText"), row.get("recognizedText")
            if not isinstance(reference, str) or not isinstance(recognized, str) or not reference or max(len(reference), len(recognized)) > 20000:
                raise ValueError("INVALID_TRANSCRIPT")
            reference = "".join(reference.split())
            recognized = "".join(recognized.split())
            if not reference:
                raise ValueError("INVALID_TRANSCRIPT")
            characters += len(reference)
            edits += distance(reference, recognized)
            report["clearPrintedPages"] += 1
    report["clearPrintedCer"] = edits / characters if characters else 1
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--validation", required=True, type=Path)
    parser.add_argument("--development-groups", required=True, type=Path)
    parser.add_argument("--rule-version", required=True)
    parser.add_argument("--model-checksum", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-f0-9]{64}", args.model_checksum) or not 1 <= len(args.rule_version) <= 64:
        raise ValueError("INVALID_VERSION")
    if args.development_groups.stat().st_size > 16 * 1024 * 1024:
        raise ValueError("VALIDATION_LIMIT")
    groups = set(args.development_groups.read_text(encoding="utf-8").splitlines())
    if not groups or not all(re.fullmatch(r"[a-f0-9]{64}", group) for group in groups):
        raise ValueError("INVALID_DEVELOPMENT_GROUPS")
    with args.validation.open(encoding="utf-8") as stream:
        def rows():
            for raw in stream:
                if len(raw) > 100000:
                    raise ValueError("VALIDATION_LIMIT")
                yield json.loads(raw)
        report = evaluate(rows(), groups, args.rule_version, args.model_checksum)
    for name, path in (("validationChecksum", args.validation), ("developmentGroupsChecksum", args.development_groups)):
        with path.open("rb") as stream:
            report[name] = hashlib.file_digest(stream, "sha256").hexdigest()
    # New immutable artifact only; never overwrite previously pinned validation evidence.
    with args.output.open("x", encoding="utf-8") as stream:
        json.dump(report, stream, ensure_ascii=False, allow_nan=False, indent=2)
    with args.output.open("rb") as stream:
        print("REPORT_SHA256=" + hashlib.file_digest(stream, "sha256").hexdigest())


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("VALIDATION_REPORT_FAILED: check private input format, split, and output path", file=sys.stderr)
        sys.exit(1)
