"""Native local development with an owned PostgreSQL 16 container; no external DB/config."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import ctypes
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from zipfile import ZipFile

from scripts.run_live_e2e import isolated_environment, free_port, stop_process, cleanup_resources

ROOT = Path(__file__).resolve().parents[1]
DIRECTORY = ROOT / '.mrr-dev'
OWNER = hashlib.sha256(str(ROOT).casefold().encode()).hexdigest()[:16]
DATABASE = 'mrr-dev-' + OWNER
HIDDEN = {'creationflags': subprocess.CREATE_NO_WINDOW} if os.name == 'nt' else {'start_new_session': True}


def process_stamp(pid: int) -> str | None:
    if os.name != 'nt':
        try:
            return Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()[19]
        except (OSError, IndexError):
            return None
    from ctypes import wintypes
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    handle = kernel.OpenProcess(0x1000, False, pid)
    if not handle:
        return None
    times = [wintypes.FILETIME() for _ in range(4)]
    try:
        if not kernel.GetProcessTimes(handle, *(ctypes.byref(t) for t in times)):
            return None
        return str(times[0].dwHighDateTime << 32 | times[0].dwLowDateTime)
    finally:
        kernel.CloseHandle(handle)


def stop_owned_process(record: dict) -> None:
    pid = record['pid']
    if process_stamp(pid) != record['stamp']:
        return
    if os.name == 'nt':
        result = subprocess.run(['taskkill', '/PID', str(pid), '/T', '/F'], capture_output=True,
                                timeout=20, **HIDDEN)
        if result.returncode and process_stamp(pid) == record['stamp']:
            raise RuntimeError('Owned process could not be stopped.')
    else:
        os.killpg(pid, signal.SIGTERM)


def private_directory() -> None:
    DIRECTORY.mkdir(exist_ok=True, mode=0o700)
    if DIRECTORY.is_symlink() or getattr(DIRECTORY.stat(follow_symlinks=False), 'st_file_attributes', 0) & 0x400:
        raise RuntimeError('Development state directory cannot be a link.')
    if os.name == 'nt':
        # Set only the DACL. Set-Acl with a newly constructed descriptor can also
        # request SACL/owner changes and require SeSecurityPrivilege on Python's 0700 directories.
        from ctypes import wintypes
        identity = subprocess.check_output(['whoami', '/user', '/fo', 'csv', '/nh'], **HIDDEN).decode(errors='ignore')
        sid = re.search(r'S-\d+(?:-\d+)+', identity).group()
        api = ctypes.WinDLL('advapi32', use_last_error=True)
        api.ConvertStringSecurityDescriptorToSecurityDescriptorW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, ctypes.POINTER(ctypes.c_void_p), ctypes.c_void_p]
        api.GetSecurityDescriptorDacl.argtypes = [ctypes.c_void_p, ctypes.POINTER(wintypes.BOOL), ctypes.POINTER(ctypes.c_void_p), ctypes.POINTER(wintypes.BOOL)]
        api.SetNamedSecurityInfoW.argtypes = [wintypes.LPWSTR, ctypes.c_int, wintypes.DWORD] + [ctypes.c_void_p] * 4
        api.SetNamedSecurityInfoW.restype = wintypes.DWORD
        kernel = ctypes.WinDLL('kernel32', use_last_error=True)
        kernel.LocalFree.argtypes = [ctypes.c_void_p]
        for path in [DIRECTORY, *DIRECTORY.rglob('*')]:
            if getattr(path.stat(follow_symlinks=False), 'st_file_attributes', 0) & 0x400:
                raise RuntimeError('Private state contains a link.')
            flags = 'OICI' if path.is_dir() else ''
            descriptor, acl = ctypes.c_void_p(), ctypes.c_void_p()
            present, defaulted = wintypes.BOOL(), wintypes.BOOL()
            sddl = f'D:P(A;{flags};FA;;;{sid})(A;{flags};FA;;;SY)'
            if not api.ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl, 1, ctypes.byref(descriptor), None):
                raise RuntimeError('Cannot construct private development permissions.')
            try:
                if not api.GetSecurityDescriptorDacl(descriptor, ctypes.byref(present), ctypes.byref(acl), ctypes.byref(defaulted)):
                    raise RuntimeError('Cannot inspect private development permissions.')
                if api.SetNamedSecurityInfoW(str(path), 1, 0x80000004, None, None, acl, None):
                    raise RuntimeError('Cannot protect private development permissions.')
            finally:
                kernel.LocalFree(descriptor)
    else:
        DIRECTORY.chmod(0o700)


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(value, dict):
            raise ValueError()
        return value
    except (OSError, ValueError):
        raise RuntimeError('Invalid local development state; inspect it locally before continuing.') from None


def save_json(path: Path, value: dict) -> None:
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value), encoding='utf-8')
    temporary.chmod(0o600)
    os.replace(temporary, path)


def verify_local_config_excluded(jar: Path) -> None:
    with ZipFile(jar) as archive:
        if any(name in archive.namelist() for name in (
                'BOOT-INF/classes/application-local.properties',
                'BOOT-INF/classes/application-local.yml', 'BOOT-INF/classes/application-local.yaml')):
            raise RuntimeError('Local configuration is embedded in the JAR. Rebuild with clean and local.config.exclude=true.')


def run_build(command, cwd, env, log) -> None:
    process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                               stdout=log, stderr=subprocess.STDOUT, **HIDDEN)
    try:
        if process.wait(timeout=900):
            raise RuntimeError('Development build failed; inspect .mrr-dev/build.log.')
    finally:
        stop_process(process)


@contextmanager
def lock():
    private_directory()
    file = (DIRECTORY / 'operation.lock').open('a+b')
    try:
        file.seek(0, os.SEEK_END)
        if not file.tell():
            file.write(b'0')
            file.flush()
        file.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(file.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise RuntimeError('Another development operation is running.') from None
        try:
            yield
        finally:
            file.seek(0)
            if os.name == 'nt':
                msvcrt.locking(file.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(file.fileno(), fcntl.LOCK_UN)
    finally:
        file.close()


def docker(*args: str, env: dict | None = None, optional: bool = False) -> str:
    result = subprocess.run(['docker', *args], env=env or isolated_environment(), capture_output=True,
                            text=True, timeout=90, **HIDDEN)
    if result.returncode:
        if optional:
            if args[0] not in ('inspect', 'volume') or 'no such' in result.stderr.lower():
                return ''
        raise RuntimeError('Local development Docker operation failed; check Docker and owned resources.')
    return result.stdout.strip()


def database_labels(name: str) -> dict | None:
    result = docker('inspect', '--format', '{{json .Config.Labels}}', name, optional=True)
    return json.loads(result) if result else None


def stop_database(name: str, owner: str) -> None:
    labels = database_labels(name)
    if labels is None:
        return
    if labels.get('mrr.dev.owner') != owner:
        raise RuntimeError('Database ownership mismatch; refusing cleanup.')
    docker('rm', '-f', name)  # Named database volume and local secrets remain for the next up.


def loopback_port(requested: int) -> int:
    if requested == 0:
        return free_port()
    if not 1024 <= requested <= 65535:
        raise ValueError('Development port must be 1024..65535, or 0 for automatic allocation.')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', requested))
    return requested


def wait_for(check, processes, seconds=150):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if any(process.poll() is not None for process in processes):
            raise RuntimeError('Development process exited; inspect private local logs.')
        if check():
            return
        time.sleep(1)
    raise RuntimeError('Development readiness timed out; inspect private local logs.')


def http_status(url: str) -> int:
    try:
        with urllib.request.urlopen(url, timeout=2) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code
    except OSError:
        return 0


def down() -> None:
    state = load_json(DIRECTORY / 'state.json')
    if state and state.get('owner') != OWNER:
        raise RuntimeError('Workspace ownership mismatch; refusing cleanup.')
    cleanup_resources([*(lambda p=p: stop_owned_process(p) for p in reversed(state.get('processes', []))),
                       lambda: stop_database(DATABASE, OWNER)])
    (DIRECTORY / 'state.json').unlink(missing_ok=True)
    print('Stopped owned development resources. Database volume retained.')


def up(args) -> None:
    state_file = DIRECTORY / 'state.json'
    state = load_json(state_file)
    if state:
        if state.get('owner') == OWNER and state.get('processes') and all(
                process_stamp(p['pid']) == p['stamp'] for p in state['processes']):
            print(f"Already running: {state['url']}")
            return
        raise RuntimeError('Previous development state is incomplete. Run down before up.')
    backend_port, web_port, management_port = loopback_port(args.backend_port), loopback_port(args.web_port), free_port()
    if len({backend_port, web_port, management_port}) != 3:
        raise RuntimeError('Development ports must be distinct.')
    env = isolated_environment() | {'MRR_TEST_PYTHON': sys.executable, 'CI': 'true', 'COREPACK_ENABLE_DOWNLOAD_PROMPT': '0'}
    with (DIRECTORY / 'doctor.log').open('w', encoding='utf-8') as log:
        if subprocess.run([sys.executable, str(ROOT / 'scripts/engineering_preflight.py'), '--integration'],
                          env=env, stdout=log, stderr=subprocess.STDOUT, **HIDDEN).returncode:
            raise RuntimeError('Development prerequisites failed. Run doctor.')
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if env.get('JAVA_HOME') else shutil.which('java')
    node = shutil.which('node')
    frontend = ROOT / 'frontend-fantastic-admin'
    if not java or not node:
        raise RuntimeError('Java 21 and the pinned Node.js are required. Run doctor.')
    if not args.skip_build:
        corepack = shutil.which('corepack')
        if not corepack:
            raise RuntimeError('Corepack is required to run the packageManager-pinned pnpm.')
        wrapper = ROOT / 'backend-repo' / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')
        commands = [([corepack, 'pnpm', 'install', '--frozen-lockfile'], frontend),
                    (([str(wrapper)] if os.name == 'nt' else ['bash', str(wrapper)]) +
                     ['-B', '-ntp', '-DskipTests', '-Dlocal.config.exclude=true', 'clean', 'package'], ROOT / 'backend-repo')]
        with (DIRECTORY / 'build.log').open('w', encoding='utf-8') as log:
            for command, cwd in commands:
                run_build(command, cwd, env, log)
    jar = ROOT / 'backend-repo/target' / ('imgapi-' + (ROOT / 'VERSION').read_text().strip() + '.jar')
    if not jar.is_file() or not (frontend / 'node_modules/vite/bin/vite.js').is_file():
        raise RuntimeError('Build outputs or frontend dependencies are missing. Run up without --skip-build.')
    verify_local_config_excluded(jar)
    credentials = load_json(DIRECTORY / 'secrets.json')
    if not credentials:
        credentials = {key: secrets.token_hex(size) for key, size in (('database', 24), ('jwt', 32), ('aes', 16))}
        save_json(DIRECTORY / 'secrets.json', credentials)
    state = {'owner': OWNER, 'processes': [], 'url': f'http://127.0.0.1:{web_port}'}
    save_json(state_file, state)
    processes = []
    try:
        stop_database(DATABASE, OWNER)
        volume = DATABASE + '-data'
        labels = docker('volume', 'inspect', '--format', '{{json .Labels}}', volume, optional=True)
        if labels and (json.loads(labels) or {}).get('mrr.dev.owner') != OWNER:
            raise RuntimeError('Database volume ownership mismatch.')
        docker('volume', 'create', '--label', f'mrr.dev.owner={OWNER}', volume)
        docker('run', '-d', '--name', DATABASE, '--label', f'mrr.dev.owner={OWNER}',
               '-p', '127.0.0.1::5432', '-v', volume + ':/var/lib/postgresql/data',
               '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_DB=imgapi', 'postgres:16-alpine',
               'postgres', '-c', 'shared_preload_libraries=pg_stat_statements',
               env=env | {'POSTGRES_PASSWORD': credentials['database']})
        wait_for(lambda: docker('exec', DATABASE, 'pg_isready', '-U', 'postgres', optional=True).startswith('/var/run'), [], 60)
        db_port = int(docker('port', DATABASE, '5432/tcp').rsplit(':', 1)[1])
        app_env = env | {
            'SPRING_DATASOURCE_URL': f'jdbc:postgresql://127.0.0.1:{db_port}/imgapi?currentSchema=app',
            'SPRING_DATASOURCE_USERNAME': 'postgres', 'SPRING_DATASOURCE_PASSWORD': credentials['database'],
            'JWT_SECRET_KEY': credentials['jwt'], 'AES_SECRET_KEY': credentials['aes'],
            'SERVER_PORT': str(backend_port), 'SERVER_ADDRESS': '127.0.0.1',
            'MANAGEMENT_SERVER_PORT': str(management_port), 'APP_LOG_RETENTION_ENABLED': 'false',
            'LOG_FILE_NAME': str(DIRECTORY / 'application.log'),
            'ARCHIVE_EXPORT_TEMP_DIRECTORY': str(DIRECTORY / 'exports'),
        }
        commands = [([java, '-jar', str(jar), '--spring.config.location=classpath:/application.properties'], app_env),
                    ([node, str(frontend / 'node_modules/vite/bin/vite.js'), '--host', '127.0.0.1',
                      '--port', str(web_port), '--strictPort'], env | {'VITE_APP_API_BASEURL': f'http://127.0.0.1:{backend_port}',
                      'VITE_OPEN_PROXY': 'true', 'VITE_BUILD_MOCK': 'false', 'VITE_OPEN_DEVTOOLS': 'false', 'BROWSER': 'none'})]
        for index, (command, child_env) in enumerate(commands):
            with (DIRECTORY / f'process-{index}.log').open('w', encoding='utf-8') as log:
                process = subprocess.Popen(command, cwd=frontend if index else DIRECTORY, env=child_env,
                                           stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, **HIDDEN)
            processes.append(process)
            stamp = process_stamp(process.pid)
            if stamp is None:
                raise RuntimeError('Cannot establish process ownership.')
            state['processes'].append({'pid': process.pid, 'stamp': stamp})
            save_json(state_file, state)
        wait_for(lambda: http_status(f'http://127.0.0.1:{backend_port}/api/v1/auth/me') == 401
                 and http_status(state['url'] + '/login') == 200, processes)
        print(f"Running: {state['url']} (backend 127.0.0.1:{backend_port})")
    except BaseException:
        cleanup_resources([*(lambda p=p: stop_process(p) for p in reversed(processes)), down])
        raise


def main() -> None:
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=('doctor', 'up', 'status', 'down'))
    parser.add_argument('--backend-port', type=int, default=0)
    parser.add_argument('--web-port', type=int, default=9200)
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    if args.command == 'doctor':
        raise SystemExit(subprocess.call([sys.executable, str(ROOT / 'scripts/engineering_preflight.py'), '--integration']))
    if args.command == 'status':
        state = load_json(DIRECTORY / 'state.json')
        ready = bool(state.get('processes')) and all(process_stamp(p['pid']) == p['stamp'] for p in state['processes'])
        print(f"Running: {state['url']}" if ready else 'Stopped or incomplete; run down to clean owned resources.')
        return
    with lock():
        up(args) if args.command == 'up' else down()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        message = str(error) if isinstance(error, RuntimeError) else 'Check ports and private .mrr-dev logs locally.'
        print('Development operation failed: ' + message, file=sys.stderr)
        raise SystemExit(1)
