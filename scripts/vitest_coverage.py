#!/usr/bin/env python3
"""Summarize Vitest coverage JSON and compare it with a recorded baseline."""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path

METRIC_ORDER = ("lines", "statements", "functions", "branches")
DISPLAY_NAMES = {
    "lines": "行",
    "statements": "语句",
    "functions": "函数",
    "branches": "分支",
}


@dataclass(frozen=True)
class CoverageMetric:
    name: str
    total: int
    covered: int
    skipped: int
    percentage: float


@dataclass(frozen=True)
class CoverageComparison:
    name: str
    current: float
    baseline: float | None
    delta: float | None
    regressed: bool


def parse_vitest_summary(path: Path) -> dict[str, CoverageMetric]:
    if not path.is_file():
        raise FileNotFoundError(f"Vitest coverage summary does not exist: {path}")

    payload = json.loads(path.read_text(encoding="utf-8"))
    total = payload.get("total")
    if not isinstance(total, dict):
        raise ValueError("Vitest coverage summary must contain an object named 'total'")

    metrics: dict[str, CoverageMetric] = {}
    for name in METRIC_ORDER:
        value = total.get(name)
        if not isinstance(value, dict):
            raise ValueError(f"Vitest coverage summary is missing metric: {name}")
        metrics[name] = CoverageMetric(
            name=name,
            total=int(value.get("total", 0)),
            covered=int(value.get("covered", 0)),
            skipped=int(value.get("skipped", 0)),
            percentage=float(value.get("pct", 0.0)),
        )
    return metrics


def load_baseline(path: Path | None) -> dict[str, float]:
    if path is None:
        return {}
    if not path.is_file():
        raise FileNotFoundError(f"Frontend coverage baseline does not exist: {path}")

    payload = json.loads(path.read_text(encoding="utf-8"))
    values = payload.get("metrics")
    if not isinstance(values, dict):
        raise ValueError("Frontend coverage baseline must contain an object named 'metrics'")
    return {
        name: float(values[name])
        for name in METRIC_ORDER
        if values.get(name) is not None
    }


def compare_coverage(
    metrics: dict[str, CoverageMetric],
    baseline: dict[str, float],
    max_regression: float,
) -> list[CoverageComparison]:
    comparisons: list[CoverageComparison] = []
    for name in METRIC_ORDER:
        current = metrics[name].percentage
        baseline_value = baseline.get(name)
        delta = None if baseline_value is None else current - baseline_value
        comparisons.append(CoverageComparison(
            name=name,
            current=current,
            baseline=baseline_value,
            delta=delta,
            regressed=delta is not None and delta < -max_regression,
        ))
    return comparisons


def render_markdown(
    metrics: dict[str, CoverageMetric],
    comparisons: list[CoverageComparison],
    max_regression: float,
) -> str:
    has_baseline = any(item.baseline is not None for item in comparisons)
    lines = [
        "## 前端 Vitest 覆盖率",
        "",
        "| 指标 | 已覆盖 / 总数 | 当前覆盖率 | 基线 | 变化 |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]

    for item in comparisons:
        metric = metrics[item.name]
        baseline_text = "—" if item.baseline is None else f"{item.baseline:.2f}%"
        display_delta = None if item.delta is None else (0.0 if abs(item.delta) < 0.005 else item.delta)
        delta_text = "—" if display_delta is None else f"{display_delta:+.2f} pp"
        if item.regressed:
            delta_text += " ⚠️"
        lines.append(
            f"| {DISPLAY_NAMES[item.name]} | {metric.covered} / {metric.total} | "
            f"{metric.percentage:.2f}% | {baseline_text} | {delta_text} |"
        )

    lines.append("")
    if has_baseline:
        regressions = [item for item in comparisons if item.regressed]
        if regressions:
            labels = "、".join(DISPLAY_NAMES[item.name] for item in regressions)
            lines.append(
                f"覆盖率基线比较发现超过 {max_regression:.2f} 个百分点的下降：{labels}。"
            )
        else:
            lines.append(f"未发现超过 {max_regression:.2f} 个百分点的覆盖率下降。")
    else:
        lines.append("本次未提供前端覆盖率基线，仅生成当前报告。")
    lines.append("")
    return "\n".join(lines)


def build_json_report(
    metrics: dict[str, CoverageMetric],
    comparisons: list[CoverageComparison],
    max_regression: float,
) -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "maxRegressionPercentagePoints": max_regression,
        "regressionDetected": any(item.regressed for item in comparisons),
        "metrics": {
            name: {
                **asdict(metrics[name]),
                "baseline": next(item.baseline for item in comparisons if item.name == name),
                "delta": next(
                    None if item.delta is None else round(item.delta, 4)
                    for item in comparisons
                    if item.name == name
                ),
            }
            for name in METRIC_ORDER
        },
    }


def _write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output-json", type=Path)
    parser.add_argument("--output-markdown", type=Path)
    parser.add_argument("--github-summary", type=Path)
    parser.add_argument("--max-regression", type=float, default=0.50)
    parser.add_argument("--fail-on-regression", action="store_true")
    args = parser.parse_args()

    if args.max_regression < 0:
        parser.error("--max-regression must be zero or greater")

    metrics = parse_vitest_summary(args.summary)
    baseline = load_baseline(args.baseline)
    comparisons = compare_coverage(metrics, baseline, args.max_regression)
    markdown = render_markdown(metrics, comparisons, args.max_regression)
    report = build_json_report(metrics, comparisons, args.max_regression)

    print(markdown)
    if args.output_markdown:
        _write_text(args.output_markdown, markdown)
    if args.output_json:
        _write_text(args.output_json, json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    if args.github_summary:
        args.github_summary.parent.mkdir(parents=True, exist_ok=True)
        with args.github_summary.open("a", encoding="utf-8") as handle:
            handle.write(markdown)

    if args.fail_on_regression and report["regressionDetected"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
