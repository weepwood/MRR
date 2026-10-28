"""Run real browser -> application -> disposable PostgreSQL 16, never an external DB."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import secrets
import re
import shutil
import socket
import signal
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

try:
    from .embed_frontend_in_jar import verify_frontend
except ImportError:  # Support the existing direct script entry point.
    from embed_frontend_in_jar import verify_frontend

ROOT = Path(__file__).resolve().parents[1]

# OpenAPI 规格与 Swagger UI 由后端直接提供，不属于 /api/**，必须单独校验访问会话。
# 安全审计 H1 的缺陷正是“拦截器存在却未被真正调用”，只有对真实进程发请求才能守住。
DOCUMENTATION_PATHS = (
    "/v3/api-docs",
    "/v3/api-docs.yaml",
    "/v3/api-docs/swagger-config",
    "/swagger-ui/index.html",
)


def assert_documentation_requires_session(base_url: str) -> None:
    """断言未携带文档会话的匿名请求无法读取接口规格与 Swagger UI。"""
    for path in DOCUMENTATION_PATHS:
        request = urllib.request.Request(base_url + path, headers={"Accept": "*/*"})
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                raise RuntimeError(
                    f"Documentation endpoint {path} is publicly reachable with HTTP {response.status}."
                )
        except urllib.error.HTTPError as error:
            if error.code != 401:
                raise RuntimeError(
                    f"Documentation endpoint {path} answered HTTP {error.code} instead of 401."
                ) from None
            body = error.read().decode("utf-8", errors="replace").replace(" ", "")
            if '"code":401' not in body:
                raise RuntimeError(
                    f"Documentation endpoint {path} did not return the documentation-session error body."
                ) from None


class SyntheticImageSource(BaseHTTPRequestHandler):
    def do_HEAD(self):
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


def isolated_environment() -> dict[str, str]:
    allowed = {"PATH", "SystemRoot", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP",
               "HOME", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "JAVA_HOME", "CI",
               "PLAYWRIGHT_BROWSERS_PATH", "LANG", "ProgramFiles", "ProgramFiles(x86)",
               "ProgramW6432", "ProgramData", "PROGRAMDATA", "PROGRAMFILES", "PROGRAMFILES(X86)", "PROGRAMW6432"}
    return {key: value for key, value in os.environ.items() if key in allowed}


def stop_process(process: subprocess.Popen | None) -> None:
    if process is not None and process.poll() is None:
        if os.name == "nt" and isinstance(process.pid, int):
            subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                           capture_output=True, timeout=15,
                           creationflags=subprocess.CREATE_NO_WINDOW)
        if os.name != "nt" and isinstance(process.pid, int):
            os.killpg(process.pid, signal.SIGTERM)
        else:
            process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            if os.name != "nt" and isinstance(process.pid, int):
                os.killpg(process.pid, signal.SIGKILL)
            else:
                process.kill()
            process.wait(timeout=15)


def docker_command(args: list[str], env: dict[str, str], sql: str | None = None) -> str:
    hidden = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
    try:
        completed = subprocess.run(["docker", *args], input=sql, text=True, encoding="utf-8",
                                   capture_output=True, env=env, timeout=90, **hidden)
    except (subprocess.TimeoutExpired, OSError):
        raise RuntimeError("Disposable PostgreSQL command timed out or could not start.") from None
    if completed.returncode:
        raise RuntimeError("Disposable PostgreSQL command failed.")
    return completed.stdout.strip()


def cleanup_resources(actions) -> None:
    failed = False
    for action in actions:
        try:
            action()
        except Exception:
            failed = True
    if failed:
        raise RuntimeError("Live E2E cleanup failed; inspect owned test resources locally.") from None


def free_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def run(jar: Path, embedded: bool = False) -> None:
    if not jar.is_file():
        raise ValueError("Build the backend executable JAR first (--jar).")
    if embedded:
        verify_frontend(jar)
    env = isolated_environment()
    java = str(Path(env["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if env.get("JAVA_HOME") else "java"
    node = shutil.which("node", path=env.get("PATH"))
    if not node:
        raise ValueError("Node.js is required.")
    frontend = ROOT / "frontend-fantastic-admin"
    backend_port = free_port()
    web_port = backend_port if embedded else free_port()
    while not embedded and web_port == backend_port:
        web_port = free_port()
    container = "mrr-live-e2e-" + secrets.token_hex(8)
    password = secrets.token_hex(24)
    backend = browser = vite = None
    hidden = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {"start_new_session": True}

    def docker(*args: str, sql: str | None = None) -> str:
        return docker_command(list(args), env | {"POSTGRES_PASSWORD": password}, sql)

    with tempfile.TemporaryDirectory(prefix="mrr-live-e2e-") as directory:
        temporary = Path(directory)
        image_source = ThreadingHTTPServer(("127.0.0.1", 0), SyntheticImageSource)
        Thread(target=image_source.serve_forever, daemon=True).start()
        try:
            docker("run", "--detach", "--name", container, "--label", "mrr.test=live-e2e",
                   "-p", "127.0.0.1::5432", "-e", "POSTGRES_PASSWORD",
                   "-e", "POSTGRES_DB=mrr_e2e", "postgres:16-alpine")
            mapping = docker("port", container, "5432/tcp")
            db_port = int(mapping.rsplit(":", 1)[1])
            deadline = time.monotonic() + 60
            while True:
                try:
                    docker("exec", container, "pg_isready", "-U", "postgres", "-d", "mrr_e2e")
                    break
                except RuntimeError:
                    if time.monotonic() > deadline:
                        raise
                    time.sleep(1)
            app_env = env | {
                "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{db_port}/mrr_e2e?currentSchema=app",
                "SPRING_DATASOURCE_USERNAME": "postgres", "SPRING_DATASOURCE_PASSWORD": password,
                "JWT_SECRET_KEY": secrets.token_hex(32), "AES_SECRET_KEY": secrets.token_hex(16),
                "MRR_AUDIT_HMAC_KEY": secrets.token_hex(32), "APP_STATUS_ENABLED": "false",
                "APP_LOG_RETENTION_ENABLED": "false", "SERVER_PORT": str(backend_port),
                "MANAGEMENT_SERVER_PORT": "0", "SERVER_ADDRESS": "127.0.0.1",
                "ARCHIVE_EXPORT_TEMP_DIRECTORY": str(temporary / "exports"),
                "IMAGE_SERVER_URL_DEFAULT": f"http://127.0.0.1:{image_source.server_port}",
            }
            with (temporary / "backend.log").open("w", encoding="utf-8") as log:
                backend = subprocess.Popen([java, "-jar", str(jar.resolve()),
                                            "--spring.config.location=classpath:/application.properties"],
                                           cwd=temporary, env=app_env, stdout=log, stderr=subprocess.STDOUT, **hidden)
                deadline = time.monotonic() + 150
                url = f"http://127.0.0.1:{backend_port}"
                while True:
                    if backend.poll() is not None:
                        raise RuntimeError("Test backend exited before readiness.")
                    try:
                        urllib.request.urlopen(url + "/api/v1/auth/me", timeout=2).close()
                    except urllib.error.HTTPError as error:
                        if error.code == 401:
                            break
                    except (OSError, urllib.error.URLError):
                        pass
                    if time.monotonic() > deadline:
                        raise RuntimeError("Test backend readiness timed out.")
                    time.sleep(1)

                assert_documentation_requires_session(url)
                # These fixtures exist only after Flyway in our newly created container.
                sql = f"""CREATE EXTENSION IF NOT EXISTS pgcrypto;
INSERT INTO app.mr_auth_user (username, display_name, password_hash, role_code, status)
VALUES ('e2e_editor', 'Synthetic Editor', public.crypt('{password}', public.gen_salt('bf', 12)), 'DOCTOR', 'active'),
       ('e2e_reader', 'Synthetic Reader', public.crypt('{password}', public.gen_salt('bf', 12)), 'READER', 'active');
"""
                docker("exec", "-i", container, "psql", "-U", "postgres", "-d", "mrr_e2e", "-v", "ON_ERROR_STOP=1", sql=sql)
                test_env = env | {"MRR_LIVE_MANAGED": "1", "MRR_LIVE_API_URL": url,
                                  "MRR_LIVE_WEB_PORT": str(web_port), "MRR_LIVE_PASSWORD": password,
                                  "MRR_LIVE_API_PREFIX": "" if embedded else "/proxy",
                                  "MRR_LIVE_RESULTS_DIR": str(temporary / "browser-results"),
                                  "BROWSER": "none", "VITE_BUILD_MOCK": "false", "VITE_APP_DEMO_MODE": "false",
                                  "VITE_APP_API_BASEURL": url, "VITE_OPEN_PROXY": "true"}
                # Own Vite directly: Playwright's detached webServer process would be
                # outside the CLI process group on Linux during a forced timeout.
                if not embedded:
                    vite = subprocess.Popen([node, "node_modules/vite/bin/vite.js", "--mode", "development",
                                             "--host", "127.0.0.1", "--port", str(web_port), "--strictPort"],
                                            cwd=frontend, env=test_env, stdout=subprocess.DEVNULL,
                                            stderr=subprocess.DEVNULL, **hidden)
                deadline = time.monotonic() + 90
                while True:
                    if vite is not None and vite.poll() is not None:
                        raise RuntimeError("Managed frontend exited before readiness.")
                    try:
                        urllib.request.urlopen(f"http://127.0.0.1:{web_port}/login", timeout=2).close()
                        break
                    except (OSError, urllib.error.URLError):
                        if time.monotonic() > deadline:
                            raise RuntimeError("Managed frontend readiness timed out.") from None
                        time.sleep(1)
                browser = subprocess.Popen([node, "node_modules/@playwright/test/cli.js", "test",
                                            "--config=playwright.live.config.ts"], cwd=frontend, env=test_env,
                                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, **hidden)
                output, _ = browser.communicate(timeout=300)
                report = output.decode("utf-8", errors="replace").replace(password, "[REDACTED]")
                report = re.sub(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED]", report)
                print(report)
                if browser.returncode:
                    raise RuntimeError("Real browser tests failed.")
                count = docker("exec", container, "psql", "-U", "postgres", "-d", "mrr_e2e", "-Atc",
                               "SELECT count(*) FROM app.mr_patient WHERE bah='E2E-SYNTHETIC-0001'")
                if count != "1":
                    raise RuntimeError("Database assertion failed: expected exactly one imported synthetic row.")
                print("Live E2E passed; PostgreSQL 16 confirms exactly one synthetic row.")
        finally:
            # The unpredictable name is created by this invocation; no other containers/volumes are touched.
            cleanup_resources([lambda: stop_process(browser), lambda: stop_process(vite),
                               lambda: stop_process(backend), image_source.shutdown,
                               image_source.server_close, lambda: docker("rm", "--force", container)])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--embedded", action="store_true", help="Exercise the production frontend served by the candidate JAR, without Vite.")
    args = parser.parse_args()
    run(args.jar, args.embedded)
