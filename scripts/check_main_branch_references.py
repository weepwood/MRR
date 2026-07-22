#!/usr/bin/env python3
from __future__ import annotations

import base64
import urllib.parse
import xml.etree.ElementTree as ET
import zlib
from pathlib import Path

ACTIVE_TEXT_PATHS = (
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
    'vitepress-doc/architecture/MRR-system-architecture-overview.svg',
)

DRAWIO_PATHS = (
    'vitepress-doc/architecture/MRR-system-architecture.drawio',
)

FORBIDDEN_SNIPPETS = (
    'dev-no-login',
    'main 分支绕过',
    '绕过 JWT',
    '故意绕过登录与权限验证',
    '故意屏蔽了登录验证',
    '正式生产需恢复登录与权限校验',
    '认证屏蔽分支',
)


def _find_forbidden_lines(relative_path: str, content: str) -> list[str]:
    violations: list[str] = []
    for line_number, line in enumerate(content.splitlines(), start=1):
        for snippet in FORBIDDEN_SNIPPETS:
            if snippet in line:
                violations.append(
                    f'{relative_path}:{line_number}: {snippet}: {line.strip()}',
                )
    return violations


def _decode_drawio_diagram(payload: str, compressed: bool) -> str:
    if not compressed or payload.lstrip().startswith('<mxGraphModel'):
        return payload
    decoded = base64.b64decode(payload)
    inflated = zlib.decompress(decoded, -15).decode('utf-8')
    return urllib.parse.unquote(inflated)


def find_forbidden_references(
    root: Path,
    paths: tuple[str, ...] = ACTIVE_TEXT_PATHS,
    drawio_paths: tuple[str, ...] = DRAWIO_PATHS,
) -> list[str]:
    violations: list[str] = []

    for relative_path in paths:
        path = root / relative_path
        if not path.is_file():
            violations.append(f'{relative_path}: 文件不存在')
            continue
        violations.extend(
            _find_forbidden_lines(relative_path, path.read_text(encoding='utf-8')),
        )

    for relative_path in drawio_paths:
        path = root / relative_path
        if not path.is_file():
            violations.append(f'{relative_path}: 文件不存在')
            continue
        try:
            document = ET.parse(path)
            compressed = document.getroot().get('compressed', 'true').lower() == 'true'
            for index, diagram in enumerate(document.getroot().findall('diagram'), start=1):
                decoded = _decode_drawio_diagram(diagram.text or '', compressed)
                violations.extend(
                    _find_forbidden_lines(f'{relative_path}#diagram-{index}', decoded),
                )
        except (ET.ParseError, ValueError, zlib.error) as error:
            violations.append(f'{relative_path}: Draw.io 文件无法解析：{error}')

    return violations


def main() -> int:
    violations = find_forbidden_references(Path(__file__).resolve().parents[1])
    if violations:
        print('发现 main 分支迁移残留或错误认证说明：')
        print('\n'.join(violations))
        return 1
    print('main 分支活跃引用与架构图认证说明检查通过。')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
