#!/usr/bin/env python3
"""Classify changed repository paths into frontend unit and end-to-end test scopes."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


CI_CONTROL_PATHS = {
    ".github/workflows/quality-gate.yml",
    "scripts/frontend_test_scope.py",
    "scripts/tests/test_frontend_test_scope.py",
}

RELEASE_EXACT_PATHS = {
    "VERSION",
    "release-baseline.json",
    ".github/workflows/windows-release-package.yml",
    ".github/workflows/standalone-jar-release.yml",
}

RELEASE_PREFIXES = (
    "release-notes/",
)

FRONTEND_EXACT_PATHS = {
    "frontend-fantastic-admin/package.json",
    "frontend-fantastic-admin/pnpm-lock.yaml",
    "frontend-fantastic-admin/vite.config.ts",
    "frontend-fantastic-admin/vitest.config.ts",
    "frontend-fantastic-admin/playwright.config.ts",
    "frontend-fantastic-admin/tsconfig.json",
    "frontend-fantastic-admin/tsconfig.app.json",
    "frontend-fantastic-admin/tsconfig.node.json",
}

E2E_PREFIXES = (
    "frontend-fantastic-admin/e2e/",
    "frontend-fantastic-admin/src/api/",
    "frontend-fantastic-admin/src/components/",
    "frontend-fantastic-admin/src/layouts/",
    "frontend-fantastic-admin/src/mock/",
    "frontend-fantastic-admin/src/router/",
    "frontend-fantastic-admin/src/store/",
    "frontend-fantastic-admin/src/ui/",
    "frontend-fantastic-admin/src/views/",
)


@dataclass(frozen=True)
class FrontendTestScope:
    frontend_changed: bool
    e2e_changed: bool
    reason: str


def _normalize(path: str) -> str:
    normalized = path.strip().replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def _matches_prefix(path: str, prefixes: tuple[str, ...]) -> bool:
    return any(path.startswith(prefix) for prefix in prefixes)


def classify_paths(paths: Iterable[str]) -> FrontendTestScope:
    normalized = sorted({path for raw in paths if (path := _normalize(raw))})
    if not normalized:
        return FrontendTestScope(False, False, "no-changed-files")

    if any(path in CI_CONTROL_PATHS for path in normalized):
        return FrontendTestScope(True, True, "frontend-gate-control")

    if any(
        path in RELEASE_EXACT_PATHS or _matches_prefix(path, RELEASE_PREFIXES)
        for path in normalized
    ):
        return FrontendTestScope(True, True, "release-change")

    frontend_paths = [
        path for path in normalized
        if path.startswith("frontend-fantastic-admin/")
    ]
    if not frontend_paths:
        return FrontendTestScope(False, False, "backend-or-docs-only")

    e2e_changed = any(
        path in FRONTEND_EXACT_PATHS or _matches_prefix(path, E2E_PREFIXES)
        for path in frontend_paths
    )
    return FrontendTestScope(
        frontend_changed=True,
        e2e_changed=e2e_changed,
        reason="frontend-user-flow-change" if e2e_changed else "frontend-unit-change",
    )


def _read_paths(path_file: Path) -> list[str]:
    if not path_file.exists():
        raise SystemExit(f"Changed path file does not exist: {path_file}")
    return path_file.read_text(encoding="utf-8").splitlines()


def _write_github_output(output_file: Path, scope: FrontendTestScope) -> None:
    with output_file.open("a", encoding="utf-8") as handle:
        handle.write(f"frontend_changed={str(scope.frontend_changed).lower()}\n")
        handle.write(f"e2e_changed={str(scope.e2e_changed).lower()}\n")
        handle.write(f"reason={scope.reason}\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path_file", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    scope = classify_paths(_read_paths(args.path_file))
    if args.github_output:
        _write_github_output(args.github_output, scope)

    print(
        "frontend_changed="
        f"{str(scope.frontend_changed).lower()} "
        "e2e_changed="
        f"{str(scope.e2e_changed).lower()} "
        f"reason={scope.reason}"
    )


if __name__ == "__main__":
    main()
