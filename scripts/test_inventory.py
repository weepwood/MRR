#!/usr/bin/env python3
"""Generate a lightweight test inventory and reject accidentally focused tests."""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


FRONTEND_TEST_SUFFIXES = (".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx")
FOCUSED_FRONTEND_PATTERN = re.compile(r"\b(?:describe|it|test)\.only\s*\(")
DISABLED_JAVA_PATTERN = re.compile(r"@Disabled\b")


@dataclass(frozen=True)
class TestInventory:
    backend_unit_files: int
    backend_integration_files: int
    frontend_unit_files: int
    frontend_e2e_files: int
    focused_or_disabled: tuple[str, ...]

    @property
    def total_files(self) -> int:
        return (
            self.backend_unit_files
            + self.backend_integration_files
            + self.frontend_unit_files
            + self.frontend_e2e_files
        )


def _relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def _iter_files(root: Path, pattern: str) -> Iterable[Path]:
    if not root.exists():
        return ()
    return (path for path in root.rglob(pattern) if path.is_file())


def build_inventory(root: Path) -> TestInventory:
    backend_test_root = root / "backend-repo" / "src" / "test" / "java"
    frontend_root = root / "frontend-fantastic-admin"
    frontend_src = frontend_root / "src"
    frontend_e2e = frontend_root / "e2e"

    backend_unit = 0
    backend_integration = 0
    frontend_unit = 0
    frontend_e2e_count = 0
    focused_or_disabled: list[str] = []

    for path in _iter_files(backend_test_root, "*.java"):
        relative = _relative(path, root)
        if "/integration/" in f"/{relative}":
            backend_integration += 1
        else:
            backend_unit += 1

        text = path.read_text(encoding="utf-8", errors="replace")
        if DISABLED_JAVA_PATTERN.search(text):
            focused_or_disabled.append(f"{relative}: contains @Disabled")

    for path in _iter_files(frontend_src, "*"):
        if not path.name.endswith(FRONTEND_TEST_SUFFIXES):
            continue
        frontend_unit += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        if FOCUSED_FRONTEND_PATTERN.search(text):
            focused_or_disabled.append(f"{_relative(path, root)}: contains focused test")

    for path in _iter_files(frontend_e2e, "*.spec.ts"):
        frontend_e2e_count += 1
        text = path.read_text(encoding="utf-8", errors="replace")
        if FOCUSED_FRONTEND_PATTERN.search(text):
            focused_or_disabled.append(f"{_relative(path, root)}: contains focused test")

    return TestInventory(
        backend_unit_files=backend_unit,
        backend_integration_files=backend_integration,
        frontend_unit_files=frontend_unit,
        frontend_e2e_files=frontend_e2e_count,
        focused_or_disabled=tuple(sorted(focused_or_disabled)),
    )


def render_markdown(inventory: TestInventory) -> str:
    lines = [
        "# MRR 测试资产清单",
        "",
        "| 测试层级 | 文件数 |",
        "| --- | ---: |",
        f"| 后端单元/切片测试 | {inventory.backend_unit_files} |",
        f"| 后端 PostgreSQL 集成测试 | {inventory.backend_integration_files} |",
        f"| 前端 Vitest 单元/组件测试 | {inventory.frontend_unit_files} |",
        f"| 前端 Playwright E2E | {inventory.frontend_e2e_files} |",
        f"| **合计** | **{inventory.total_files}** |",
        "",
    ]
    if inventory.focused_or_disabled:
        lines.extend([
            "## 需要处理的测试",
            "",
            *[f"- {item}" for item in inventory.focused_or_disabled],
            "",
        ])
    else:
        lines.extend(["未发现 `.only` 或 `@Disabled`。", ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output", type=Path)
    parser.add_argument("--github-summary", type=Path)
    parser.add_argument("--fail-on-focused", action="store_true")
    args = parser.parse_args()

    inventory = build_inventory(args.root.resolve())
    report = render_markdown(inventory)
    print(report)

    if args.output:
        args.output.write_text(report, encoding="utf-8")
    if args.github_summary:
        with args.github_summary.open("a", encoding="utf-8") as handle:
            handle.write(report)

    if args.fail_on_focused and inventory.focused_or_disabled:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
