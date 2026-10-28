"""Select PR/main checks and fail closed when a selected job did not succeed."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

from scripts.backend_test_scope import classify_paths as backend_scope
from scripts.backend_test_scope import requires_real_checks
from scripts.ci_build_scope import needs_release_build
from scripts.frontend_test_scope import classify_paths as frontend_scope

JOBS = ('frontend', 'backend', 'integration', 'windows', 'recovery', 'release', 'development')
DEVELOPMENT_INPUTS = {
    'docker-compose.yml', '.dockerignore', 'backend-repo/Dockerfile', 'frontend-fantastic-admin/Dockerfile',
    'backend-repo/pom.xml', 'backend-repo/mvnw', 'backend-repo/mvnw.cmd',
    'frontend-fantastic-admin/package.json', 'frontend-fantastic-admin/pnpm-lock.yaml',
    'frontend-fantastic-admin/pnpm-workspace.yaml', 'frontend-fantastic-admin/.node-version',
    'frontend-fantastic-admin/nginx.conf', 'scripts/run_live_e2e.py',
    'frontend-fantastic-admin/scripts/require-package-manager.mjs',
    'backend-repo/src/main/resources/application.properties', 'backend-repo/src/main/resources/logback-spring.xml',
    'vitepress-doc/package.json', 'vitepress-doc/package-lock.json',
    'scripts/dev.py', 'scripts/dev.ps1', 'scripts/smoke_dev.py', 'scripts/engineering_preflight.py',
    'scripts/tests/test_dev.py', 'scripts/tests/test_toolchain_contract.py',
    'scripts/tests/test_development_scope.py', '.github/workflows/quality-gate.yml',
}

def classify_paths(paths, mode='pr') -> dict[str, bool]:
    paths = [p.strip().replace('\\', '/').removeprefix('./') for p in paths if p.strip()]
    frontend, backend = frontend_scope(paths), backend_scope(paths)
    database_tools = any(p.startswith('mrr-db/') and not p.lower().endswith('.md') for p in paths)
    recovery = any(p.startswith('deploy/windows/') and any(word in p.lower() for word in ('backup', 'restore', 'database'))
                   or p.startswith('scripts/test_windows_') and 'recovery' in p
                   or p in ('.github/workflows/windows-database-recovery.yml', 'deploy/windows/mrrctl.ps1') for p in paths)
    live = frontend.e2e_changed or requires_real_checks(paths) or any(
        p.startswith(('scripts/run_live_e2e.py', 'frontend-fantastic-admin/e2e-live/'))
        or p == '.github/workflows/live-e2e.yml' for p in paths)
    development = any(p in DEVELOPMENT_INPUTS or p.startswith(('backend-repo/.mvn/', 'backend-repo/examples/', 'vitepress-doc/.vitepress/')) for p in paths)
    scope = dict(development=development, frontend=frontend.frontend_changed, backend=backend.backend_changed or live or recovery or database_tools,
                 build=frontend.build_changed, postgres=backend.integration_changed or database_tools or live,
                 e2e=frontend.e2e_changed,
                 integration=live,
                 live=live, windows=any(p.startswith('deploy/windows/') or p in ('scripts/dev.ps1', 'scripts/dev.py') for p in paths),
                 recovery=recovery, release=needs_release_build(paths),
                 tooling=any(p.startswith(('scripts/', '.github/', 'quality/')) for p in paths))
    if mode == 'integration':
        if frontend.frontend_changed or backend.backend_changed or live or database_tools:
            scope.update(integration=True, build=True, postgres=True, live=True)
        scope['release'] = False
        scope['e2e'] = False
    elif mode != 'pr':
        raise ValueError('Unsupported verification mode.')
    if scope['integration']:
        # Production frontend/backend/live checks share one runner and no large intermediate artifacts.
        scope.update(frontend=False, backend=recovery)
    if scope['release']:
        # Full candidate validation already covers all application and Windows checks.
        # Native development lifecycle checks are additional to the production candidate.
        native_windows = any(p in ('scripts/dev.py', 'scripts/dev.ps1', 'scripts/tests/test_dev.py') for p in paths)
        scope.update(frontend=False, backend=False, integration=False, windows=native_windows, recovery=False)
    return scope


def validate_results(selected: dict, results: dict) -> None:
    if results.get('scope') != 'success':
        raise ValueError('Check scope did not complete successfully.')
    for job, required in selected.items():
        if type(required) is not bool:
            raise ValueError(f'Invalid selection for {job}.')
        result = results.get(job)
        if result != 'success' and (required or result != 'skipped'):
            raise ValueError(f'{job}: required={required}, result={result!r}.')


def successful_base(runs: list[dict]) -> str | None:
    return next((r['head_sha'] for r in runs if r.get('conclusion') == 'success'
                 and r.get('event') == 'push' and r.get('head_branch') == 'main'), None)


def git(*args) -> str:
    return subprocess.check_output(['git', *args], text=True, encoding='utf-8').strip()


def main() -> None:
    parser = argparse.ArgumentParser(__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    select = commands.add_parser('scope')
    select.add_argument('--mode', choices=('pr', 'integration'), required=True)
    select.add_argument('--base')
    select.add_argument('--head', default='HEAD')
    select.add_argument('--runs', type=Path)
    select.add_argument('--output', type=Path, required=True)
    check = commands.add_parser('check')
    check.add_argument('needs', type=Path)
    args = parser.parse_args()
    if args.command == 'check':
        needs = json.loads(args.needs.read_text(encoding='utf-8'))
        outputs = needs['scope']['outputs']
        selected = {}
        for job in JOBS:
            value = outputs.get(job)
            if value not in ('true', 'false'):
                raise ValueError(f'Missing/invalid scope output: {job}.')
            selected[job] = value == 'true'
        validate_results(selected, {key: value['result'] for key, value in needs.items()})
        print('All selected checks succeeded.')
        return
    base = args.base
    if args.runs:
        pages = json.loads(args.runs.read_text(encoding='utf-8'))
        if isinstance(pages, dict):
            pages = [pages]
        base = successful_base([r for page in pages for r in page['workflow_runs']])
        if base and subprocess.run(['git', 'merge-base', '--is-ancestor', base, args.head]).returncode:
            base = None
    # No previous green main means verify the entire tree, not merely the last commit.
    raw = (git('diff', '--name-only', '--no-renames', '-z', f'{base}...{args.head}') if base
           else git('ls-tree', '-r', '--name-only', '-z', args.head))
    paths = raw.split('\0')
    scope = classify_paths(paths, args.mode)
    with args.output.open('a', encoding='utf-8') as output:
        for key, value in scope.items():
            output.write(f'{key}={str(value).lower()}\n')
        output.write(f'base={base or ""}\n')
    print(json.dumps(scope, sort_keys=True))


if __name__ == '__main__':
    main()
