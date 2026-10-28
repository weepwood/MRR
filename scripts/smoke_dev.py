"""Build and boot development Compose in a disposable project with synthetic secrets."""
import argparse
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile

from scripts.run_live_e2e import isolated_environment, free_port
from scripts.dev import http_status

ROOT = Path(__file__).resolve().parents[1]


def source_revision(value: str) -> str:
    if not re.fullmatch('[0-9a-f]{40}', value):
        raise ValueError('A full source commit SHA is required.')
    return value


def smoke():
    revision = source_revision(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip())
    project = 'mrr-smoke-' + secrets.token_hex(6)
    app_port, web_port, db_port = free_port(), free_port(), free_port()
    if len({app_port, web_port, db_port}) != 3:
        raise RuntimeError('Could not allocate distinct smoke ports.')
    env = isolated_environment() | {'POSTGRES_PASSWORD': secrets.token_hex(24),
        'JWT_SECRET_KEY': secrets.token_hex(32), 'AES_SECRET_KEY': secrets.token_hex(16),
        'MRR_GIT_COMMIT': revision, 'MRR_APP_PORT': str(app_port),
        'MRR_WEB_PORT': str(web_port), 'MRR_POSTGRES_PORT': str(db_port)}
    # Explicitly ignore local .env files; only synthetic per-run values enter this project.
    empty = tempfile.NamedTemporaryFile(prefix='mrr-smoke-', suffix='.env', delete=False)
    empty.close()
    command = ['docker', 'compose', '--project-name', project, '--env-file', empty.name,
               '-f', str(ROOT / 'docker-compose.yml')]

    def compose(*args):
        subprocess.run(command + list(args), cwd=ROOT, env=env, check=True, timeout=1200)

    try:
        compose('build')
        compose('up', '-d', '--wait', '--wait-timeout', '180')
        for port in (app_port, web_port):
            if http_status(f'http://127.0.0.1:{port}/login') != 200:
                raise RuntimeError('Development login page is unavailable.')
            if http_status(f'http://127.0.0.1:{port}/api/v1/auth/me') != 401:
                raise RuntimeError('Unauthenticated request was not rejected with 401.')
        print('Development Compose: healthy, application/proxy HTTP 200, unauthenticated API 401.')
    finally:
        # Project is unpredictable, created in this call, and never supplied by the caller.
        try:
            compose('down', '--volumes', '--remove-orphans', '--rmi', 'local')
        finally:
            Path(empty.name).unlink(missing_ok=True)


if __name__ == '__main__':
    argparse.ArgumentParser(__doc__).parse_args()
    smoke()
