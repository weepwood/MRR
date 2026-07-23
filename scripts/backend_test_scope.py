#!/usr/bin/env python3
"""Classify changed repository paths into backend unit or integration test scopes."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


CI_CONTROL_PATHS = {
    ".github/workflows/quality-gate.yml",
    "scripts/backend_test_scope.py",
    "scripts/tests/test_backend_test_scope.py",
}

RELEASE_EXACT_PATHS = {
    "VERSION",
    "release-baseline.json",
    ".github/workflows/windows-release-package.yml",
}

RELEASE_PREFIXES = (
    "release-notes/",
)

INTEGRATION_EXACT_PATHS = {
    "backend-repo/pom.xml",
}

INTEGRATION_PREFIXES = (
    "backend-repo/src/main/resources/",
    "backend-repo/src/test/java/com/zjcxph/imgapi/integration/",
    "backend-repo/src/main/java/com/zjcxph/imgapi/mapper/",
    "backend-repo/src/main/java/com/zjcxph/imgapi/entity/",
    "backend-repo/src/main/java/com/zjcxph/imgapi/repository/",
    "backend-repo/src/main/java/com/zjcxph/imgapi/config/",
)


@dataclass(frozen=True)
class BackendTestScope:
    backend_changed: bool
    integration_changed: bool
    reason: str


def _normalize(path: str) -> str:
    normalized = path.strip().replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def _matches_prefix(path: str, prefixes: tuple[str, ...]) -> bool:
    return any(path.startswith(prefix) for prefix in prefixes)


def classify_paths(paths: Iterable[str]) -> BackendTestScope:
    normalized = sorted({path for raw in paths if (path := _normalize(raw))})
    if not normalized:
        return BackendTestScope(False, False, "no-changed-files")

    if any(path in CI_CONTROL_PATHS for path in normalized):
        return BackendTestScope(True, True, "backend-gate-control")

    if any(
        path in RELEASE_EXACT_PATHS or _matches_prefix(path, RELEASE_PREFIXES)
        for path in normalized
    ):
        return BackendTestScope(True, True, "release-change")

    backend_paths = [path for path in normalized if path.startswith("backend-repo/")]
    if not backend_paths:
        return BackendTestScope(False, False, "frontend-or-docs-only")

    integration_changed = any(
        path in INTEGRATION_EXACT_PATHS or _matches_prefix(path, INTEGRATION_PREFIXES)
        for path in backend_paths
    )
    return BackendTestScope(
        backend_changed=True,
        integration_changed=integration_changed,
        reason="database-or-integration-change" if integration_changed else "backend-unit-change",
    )


def _read_paths(path_file: Path) -> list[str]:
    if not path_file.exists():
        raise SystemExit(f"Changed path file does not exist: {path_file}")
    return path_file.read_text(encoding="utf-8").splitlines()


def _write_github_output(output_file: Path, scope: BackendTestScope) -> None:
    with output_file.open("a", encoding="utf-8") as handle:
        handle.write(f"backend_changed={str(scope.backend_changed).lower()}\n")
        handle.write(f"integration_changed={str(scope.integration_changed).lower()}\n")
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
        "backend_changed="
        f"{str(scope.backend_changed).lower()} "
        "integration_changed="
        f"{str(scope.integration_changed).lower()} "
        f"reason={scope.reason}"
    )


if __name__ == "__main__":
    main()
