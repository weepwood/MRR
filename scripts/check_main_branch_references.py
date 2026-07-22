#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ACTIVE_PATHS = (
    '.github/workflows/quality-gate.yml',
    '.github/workflows/backend-normalization-check.yml',
    '.github/workflows/frontend-mock.yml',
    '.github/workflows/monitoring-validation.yml',
    '.github/workflows/windows-release-package.yml',
    'README.md',
    'CONTRIBUTING.md',
    'CLAUDE.md',
    'AGENTS.md',
    'vitepress-doc/internal/index.md',
    'vitepress-doc/internal/development.md',
    'vitepress-doc/getting-started/installation.md',
    'vitepress-doc/user-guide/index.md',
    'vitepress-doc/architecture/mrr-system-architecture.md',
)

FORBIDDEN = 'dev-no-login'


def find_forbidden_references(
    root: Path,
    paths: tuple[str, ...] = ACTIVE_PATHS,
) -> list[str]:
    violations: list[str] = []
    for relative_path in paths:
        path = root / relative_path
        if not path.is_file():
            violations.append(f'{relative_path}: 文件不存在')
            continue
        for line_number, line in enumerate(
            path.read_text(encoding='utf-8').splitlines(),
            start=1,
        ):
            if FORBIDDEN in line:
                violations.append(f'{relative_path}:{line_number}: {line.strip()}')
    return violations


def main() -> int:
    violations = find_forbidden_references(Path(__file__).resolve().parents[1])
    if violations:
        print('发现活跃 dev-no-login 残留：')
        print('\n'.join(violations))
        return 1
    print('main 分支活跃引用检查通过。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
