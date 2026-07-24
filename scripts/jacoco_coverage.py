#!/usr/bin/env python3
"""Summarize JaCoCo XML coverage and compare it with a recorded baseline."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
import xml.etree.ElementTree as ET

COUNTER_ORDER = ("INSTRUCTION", "BRANCH", "LINE", "COMPLEXITY", "METHOD", "CLASS")
DISPLAY_NAMES = {
    "INSTRUCTION": "指令",
    "BRANCH": "分支",
    "LINE": "行",
    "COMPLEXITY": "复杂度",
    "METHOD": "方法",
    "CLASS": "类",
}


@dataclass(frozen=True)
class CoverageCounter:
    type: str
    missed: int
    covered: int

    @property
    def total(self) -> int:
        return self.missed + self.covered

    @property
    def percentage(self) -> float:
        if self.total == 0:
            return 100.0
        return self.covered / self.total * 100


@dataclass(frozen=True)
class CoverageComparison:
    type: str
    current: float
    baseline: float | None
    delta: float | None
    regressed: bool


def parse_jacoco_xml(path: Path) -> dict[str, CoverageCounter]:
    if not path.is_file():
        raise FileNotFoundError(f"JaCoCo XML does not exist: {path}")

    root = ET.parse(path).getroot()
    counters: dict[str, CoverageCounter] = {}
    for node in root.findall("counter"):
        counter_type = node.attrib.get("type", "").upper()
        if counter_type not in COUNTER_ORDER:
            continue
        counters[counter_type] = CoverageCounter(
            type=counter_type,
            missed=int(node.attrib["missed"]),
            covered=int(node.attrib["covered"]),
        )

    missing = [counter_type for counter_type in COUNTER_ORDER if counter_type not in counters]
    if missing:
        raise ValueError(f"JaCoCo XML is missing counters: {', '.join(missing)}")
    return counters


def load_baseline(path: Path | None) -> dict[str, float]:
    if path is None:
        return {}
    if not path.is_file():
        raise FileNotFoundError(f"Coverage baseline does not exist: {path}")

    payload = json.loads(path.read_text(encoding="utf-8"))
    metrics = payload.get("metrics")
    if not isinstance(metrics, dict):
        raise ValueError("Coverage baseline must contain an object named 'metrics'")

    baseline: dict[str, float] = {}
    for counter_type in COUNTER_ORDER:
        value = metrics.get(counter_type.lower())
        if value is not None:
            baseline[counter_type] = float(value)
    return baseline


def compare_coverage(
    counters: dict[str, CoverageCounter],
    baseline: dict[str, float],
    max_regression: float,
) -> list[CoverageComparison]:
    comparisons: list[CoverageComparison] = []
    for counter_type in COUNTER_ORDER:
        current = counters[counter_type].percentage
        baseline_value = baseline.get(counter_type)
        delta = None if baseline_value is None else current - baseline_value
        comparisons.append(CoverageComparison(
            type=counter_type,
            current=current,
            baseline=baseline_value,
            delta=delta,
            regressed=delta is not None and delta < -max_regression,
        ))
    return comparisons


def render_markdown(
    counters: dict[str, CoverageCounter],
    comparisons: list[CoverageComparison],
    max_regression: float,
) -> str:
    has_baseline = any(item.baseline is not None for item in comparisons)
    lines = [
        "## 后端 JaCoCo 覆盖率",
        "",
        "| 指标 | 已覆盖 / 总数 | 当前覆盖率 | 基线 | 变化 |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]

    for item in comparisons:
        counter = counters[item.type]
        baseline_text = "—" if item.baseline is None else f"{item.baseline:.2f}%"
        display_delta = None if item.delta is None else (0.0 if abs(item.delta) < 0.005 else item.delta)
        delta_text = "—" if display_delta is None else f"{display_delta:+.2f} pp"
        if item.regressed:
            delta_text += " ⚠️"
        lines.append(
            f"| {DISPLAY_NAMES[item.type]} | {counter.covered} / {counter.total} | "
            f"{item.current:.2f}% | {baseline_text} | {delta_text} |"
        )

    lines.extend([""])
    if has_baseline:
        regressions = [item for item in comparisons if item.regressed]
        if regressions:
            labels = "、".join(DISPLAY_NAMES[item.type] for item in regressions)
            lines.append(
                f"覆盖率基线比较发现超过 {max_regression:.2f} 个百分点的下降：{labels}。"
            )
        else:
            lines.append(
                f"未发现超过 {max_regression:.2f} 个百分点的覆盖率下降。"
            )
    else:
        lines.append("本次未提供覆盖率基线，仅生成当前报告。")
    lines.append("")
    return "\n".join(lines)


def build_json_report(
    counters: dict[str, CoverageCounter],
    comparisons: list[CoverageComparison],
    max_regression: float,
) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "maxRegressionPercentagePoints": max_regression,
        "regressionDetected": any(item.regressed for item in comparisons),
        "metrics": {
            counter_type.lower(): {
                **asdict(counters[counter_type]),
                "total": counters[counter_type].total,
                "percentage": round(counters[counter_type].percentage, 4),
                "baseline": next(
                    item.baseline for item in comparisons if item.type == counter_type
                ),
                "delta": next(
                    None if item.delta is None else round(item.delta, 4)
                    for item in comparisons if item.type == counter_type
                ),
            }
            for counter_type in COUNTER_ORDER
        },
    }


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output-json", type=Path)
    parser.add_argument("--output-markdown", type=Path)
    parser.add_argument("--github-summary", type=Path)
    parser.add_argument("--max-regression", type=float, default=0.50)
    parser.add_argument("--fail-on-regression", action="store_true")
    args = parser.parse_args()

    if args.max_regression < 0:
        parser.error("--max-regression must be zero or greater")

    counters = parse_jacoco_xml(args.xml)
    baseline = load_baseline(args.baseline)
    comparisons = compare_coverage(counters, baseline, args.max_regression)
    markdown = render_markdown(counters, comparisons, args.max_regression)
    json_report = build_json_report(counters, comparisons, args.max_regression)

    print(markdown)
    if args.output_markdown:
        _write_text(args.output_markdown, markdown)
    if args.output_json:
        _write_text(
            args.output_json,
            json.dumps(json_report, ensure_ascii=False, indent=2) + "\n",
        )
    if args.github_summary:
        args.github_summary.parent.mkdir(parents=True, exist_ok=True)
        with args.github_summary.open("a", encoding="utf-8") as handle:
            handle.write(markdown)

    if args.fail_on_regression and json_report["regressionDetected"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
