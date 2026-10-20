"""Reject missing, skipped or failed PostgreSQL integration test reports."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def validate(reports: Path, expected: set[str] | None = None) -> list[str]:
    problems: list[str] = []
    seen: set[str] = set()
    for path in reports.glob("TEST-*.xml"):
        try:
            suite = ET.parse(path).getroot()
            name = suite.get("name", "").rsplit(".", 1)[-1]
            if not name.endswith("PostgresqlIT"):
                continue
            seen.add(name)
            if int(suite.get("tests", "0")) <= 0:
                problems.append(f"{name}: no executed tests")
            for counter in ("skipped", "failures", "errors"):
                if int(suite.get(counter, "0")):
                    problems.append(f"{name}: {counter} must be zero")
        except (ET.ParseError, ValueError):
            problems.append(f"{path.name}: invalid test report")
    if not seen:
        problems.append("No PostgreSQL integration test reports found")
    for name in sorted((expected or set()) - seen):
        problems.append(f"{name}: missing report")
    return problems


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", type=Path, required=True)
    parser.add_argument("--source", type=Path, default=Path(__file__).resolve().parents[1] /
                        "backend-repo/src/test/java/com/zjcxph/imgapi/integration")
    args = parser.parse_args()
    expected = {path.stem for path in args.source.rglob("*PostgresqlIT.java")}
    findings = validate(args.reports, expected)
    print("\n".join(findings) if findings else f"All {len(expected)} PostgreSQL test classes executed without skips.")
    raise SystemExit(bool(findings))
