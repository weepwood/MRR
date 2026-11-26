#!/usr/bin/env python3
"""Opt-in full application soak. Creates a fresh PG16 and local files; never accepts an existing DB URL."""
import argparse
import base64
import concurrent.futures
from contextlib import closing
import hashlib
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import tempfile
import time
import uuid
import zlib

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend-repo"


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def jwt(user_id, username, secret, duration=3600):
    def encode(value):
        return base64.urlsafe_b64encode(value).rstrip(b"=")
    jti = str(uuid.uuid4())
    header = encode(b'{"alg":"HS256","typ":"JWT"}')
    body = encode(json.dumps(dict(id=user_id, username=username, tokenType="access",
                                  passwordVersion=1, jti=jti, exp=int(time.time()) + duration),
                             separators=(",", ":")).encode())
    signing = header + b"." + body
    return (signing + b"." + encode(hmac.digest(secret.encode(), signing, "sha256"))).decode(), jti


def png():
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body))
    pixels = bytes((x * 17 + y * 31) % 256 for y in range(64) for x in range(64 * 3))
    rows = b"".join(b"\0" + pixels[y * 192:(y + 1) * 192] for y in range(64))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 64, 64, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def request(connection, path, token=None, method="GET"):
    headers = {"Authorization": "Bearer " + token} if token else {}
    connection.request(method, path, headers=headers)
    response = connection.getresponse()
    return response.status, dict(response.getheaders()), response.read()


def pct(values, p):
    values = sorted(values)
    if not values:
        return None
    return round(values[max(0, min(len(values)-1, int(len(values)*p + .999999)-1))], 3)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pg-bin", type=Path, required=True)
    parser.add_argument("--maven-repo", type=Path, required=True)
    parser.add_argument("--lab-root", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--extended", action="store_true", help="explicitly authorize a longer isolated soak (up to 7200s)")
    args = parser.parse_args()
    if not 30 <= args.seconds <= (7200 if args.extended else 600):
        parser.error("duration must be 30..600 seconds (7200 with --extended)")
    if shutil.disk_usage(args.lab_root).free < 5 * 1024**3:
        parser.error("isolated lab requires at least 5 GiB free")
    lab = Path(tempfile.mkdtemp(prefix="mrr-http-soak-", dir=args.lab_root))
    print("LAB=" + str(lab), flush=True)
    for name in ("socket", "app-tmp", "home", "images", "exports"):
        (lab / name).mkdir()
    pg = args.pg_bin
    assert "PostgreSQL) 16." in subprocess.check_output([str(pg / "initdb"), "--version"], text=True)
    dbport, httpport, management = port(), port(), port()
    # Independent PG instance; no caller-supplied DB endpoint.
    def pgcmd(*command, **kwargs):
        return subprocess.run([str(pg / command[0]), *map(str, command[1:])], check=True, **kwargs)
    def sql(statement):
        return subprocess.check_output([str(pg / "psql"), "-h", str(lab / "socket"), "-p", str(dbport),
                                        "-U", "imgapi", "-d", "imgapi", "-At", "-v", "ON_ERROR_STOP=1",
                                        "-c", statement], text=True).strip()
    pgcmd("initdb", "-D", lab / "data", "-U", "imgapi", "-A", "trust", "--no-locale",
          stdout=subprocess.DEVNULL)
    pgcmd("pg_ctl", "-D", lab / "data", "-l", lab / "pg.log",
          "-o", f"-h 127.0.0.1 -p {dbport} -k {lab / 'socket'} -c shared_preload_libraries=pg_stat_statements", "-w", "start")
    app = None
    probe_server = None
    try:
        # Only the readiness HEAD probe is a fixture; image bytes are read by the real
        # gateway from the isolated local file. Do not bypass the readiness interceptor.
        class ProbeHandler(BaseHTTPRequestHandler):
            def do_HEAD(self):
                self.send_response(200)
                self.end_headers()
            def log_message(self, *_args):
                pass
        probe_server = ThreadingHTTPServer(("127.0.0.1", 0), ProbeHandler)
        Thread(target=probe_server.serve_forever, daemon=True).start()
        probe_url = f"http://127.0.0.1:{probe_server.server_port}"
        pgcmd("createdb", "-h", lab / "socket", "-p", dbport, "-U", "imgapi", "imgapi")
        sql("CREATE EXTENSION pg_stat_statements WITH SCHEMA public")
        secret = secrets.token_hex(32)
        config = (BACKEND / "src/main/resources/application.properties").read_text()
        config += "\n" + "\n".join(f"{key}={value}" for key, value in {
            "server.address": "127.0.0.1", "server.port": httpport,
            "management.server.address": "127.0.0.1", "management.server.port": management,
            "spring.datasource.url": f"jdbc:postgresql://127.0.0.1:{dbport}/imgapi?currentSchema=app",
            "spring.datasource.username": "imgapi", "spring.datasource.password": "synthetic",
            "spring.datasource.hikari.maximum-pool-size": 4, "spring.datasource.hikari.minimum-idle": 0,
            "spring.datasource.hikari.connection-timeout": 3000,
            "spring.datasource.hikari.connection-init-sql": "SET search_path TO app, public",
            "logging.file.name": lab / "app-runtime.log", "image.basePath": lab / "images",
            "image.url": "", "image.server-url-default": probe_url, "image.server-url-ba01": "",
            "image.server-url-ba02": "", "image.server-url-ba03": "", "image.username": "", "image.password": "",
            "oss.endpoint": "127.0.0.1:1", "oss.base-url": "http://127.0.0.1:1",
            "oss.access-key-id": "", "oss.access-key-secret": "", "oss.bucket": "synthetic-soak",
            "archive.image-delivery.mode": "gateway", "archive.export.temp-directory": lab / "exports",
            "app.status.enabled": "false", "app.log-retention.enabled": "false",
            "app.operations.nginx.enabled": "false", "app.operations.nginx.root": "",
            "app.operations.history.enabled": "false", "app.data-quality.enabled": "false",
            "app.data-quality.async-enabled": "true", "app.data-quality.run-budget-seconds": 45,
            "app.access-log-partition.maintenance-cron": "-", "mrr.developer-mode.allowed": "false",
            "aes.secret.key": "", "spring.devtools.restart.enabled": "false",
        }.items()) + "\n"
        (lab / "application.properties").write_text(config)
        env = {"HOME": os.environ["HOME"], "JAVA_HOME": str(args.java_home),
               "PATH": str(args.java_home / "bin") + ":/opt/homebrew/bin:/usr/bin:/bin",
               "LANG": "en_US.UTF-8", "SPRING_CONFIG_LOCATION": "file:" + str(lab / "application.properties"),
               "JWT_SECRET_KEY": secret, "MRR_AUDIT_HMAC_KEY": secrets.token_hex(32)}
        cmd = [str(BACKEND / "mvnw"), "-o", "-B", "-ntp",
               "-Dmaven.repo.local=" + str(args.maven_repo),
               "-Dspring-boot.run.workingDirectory=" + str(lab),
               "-Dspring-boot.run.jvmArguments=-Xmx512m -Djava.io.tmpdir=" + str(lab / "app-tmp")
               + " -Duser.home=" + str(lab / "home") + " -Xlog:gc:file=" + str(lab / "gc.log")
               + " -XX:StartFlightRecording=filename=" + str(lab / "soak.jfr") + ",settings=profile,maxsize=64m,dumponexit=true",
               "spring-boot:run"]
        with (lab / "boot.log").open("w") as output:
            app = subprocess.Popen(cmd, cwd=BACKEND, env=env, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
            for _ in range(120):
                if app.poll() is not None:
                    raise RuntimeError(f"Spring Boot exited {app.returncode}; see isolated boot.log")
                try:
                    with closing(http.client.HTTPConnection("127.0.0.1", management, timeout=1)) as conn:
                        status, _, _ = request(conn, "/actuator/health")
                    if status in (200, 503):
                        break
                except OSError:
                    pass
                time.sleep(1)
            else:
                raise RuntimeError("full application did not become reachable")
            print("FULL_APP_REACHABLE", flush=True)
            username = "synthetic-full-soak"
            sql("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES "
                "('SYNTHETIC_SOAK','synthetic','system:read,system:manage,record:read,log:read')")
            uid = int(sql("INSERT INTO app.mr_auth_user(username,display_name,password_hash,role_code,status,must_change_password,password_version) "
                          f"VALUES ('{username}','synthetic','not-a-password','SYNTHETIC_SOAK','active',false,1) RETURNING id").splitlines()[0])
            token, jti = jwt(uid, username, secret, args.seconds + 600)
            content = png()
            (lab / "images" / "synthetic.png").write_bytes(content)
            sql("INSERT INTO app.mr_scan(bah,brxh,filename,folder,uploadflag,pages,source_type,source_ref) "
                "SELECT '7654300','7654300','synthetic.png','synthetic-soak',1,n,'LOCAL','synthetic.png' FROM generate_series(1,1000) n")
            sql("ANALYZE app.mr_scan")
            scan = int(sql("SELECT min(id) FROM app.mr_scan WHERE folder='synthetic-soak'"))
            run = int(sql("INSERT INTO app.mrr_data_quality_run(status,triggered_by) VALUES ('SUCCESS','synthetic-full-soak') RETURNING id").splitlines()[0])
            sql(f"INSERT INTO app.mrr_data_quality_issue(run_id,check_code,check_name,severity,entity_id) VALUES ({run},'SOAK','synthetic','WARNING','synthetic')")
            paths = ["/api/v1/system/data-quality/summary",
                     f"/api/v1/system/data-quality/issues?limit=10&runId={run}",
                     "/api/v1/scan/page?page=30&size=20", "/api/v1/scan/cursor?afterId=0&size=20",
                     f"/api/v1/img/content/{scan}"]
            def checked(conn, path):
                code, headers, body = request(conn, path, token)
                if code != 200:
                    raise RuntimeError(f"{path.split('?')[0]} status={code}")
                if "/img/content/" in path:
                    if body != content or "no-store" not in headers.get("Cache-Control", ""):
                        raise RuntimeError("image bytes or cache-control mismatch")
                elif json.loads(body).get("code") != 200:
                    raise RuntimeError("business response failure")
                request_id = next((v for k,v in headers.items() if k.lower()=="x-request-id"), None)
                if not request_id:
                    raise RuntimeError("missing request trace")
                return request_id
            # Warm up and use actual async submit API; any rejected submission is reported.
            with closing(http.client.HTTPConnection("127.0.0.1", httpport, timeout=8)) as conn:
                for path in paths:
                    checked(conn, path)
                readiness_status, _, readiness_body = request(conn, "/api/v1/operations/readiness/refresh", token, "POST")
                readiness = json.loads(readiness_body)
                if readiness_status != 200 or readiness.get("code") != 200:
                    raise RuntimeError(f"readiness refresh rejected: {readiness_status}")
                print("READINESS " + json.dumps(readiness.get("data"), ensure_ascii=False), flush=True)
                status, _, body = request(conn, "/api/v1/system/data-quality/runs", token, "POST")
                print("ASYNC_SUBMIT_HTTP=" + str(status), flush=True)
                managed_id = json.loads(body).get("data", {}).get("latestRun", {}).get("id") if status == 200 else None
                if not managed_id:
                    raise RuntimeError(f"async admission failed: HTTP {status}, code {json.loads(body).get('code')}")
            def scrape():
                with closing(http.client.HTTPConnection("127.0.0.1", management, timeout=3)) as conn:
                    status, _, body = request(conn, "/actuator/prometheus")
                if status != 200:
                    return {"status": status}
                metrics = {}
                for line in body.decode().splitlines():
                    if not line or line.startswith("#"):
                        continue
                    name = line.split("{", 1)[0].split(" ", 1)[0]
                    if name in {"process_cpu_usage", "process_cpu_time_ns", "process_files_open_files", "process_files_max_files", "jvm_threads_live_threads", "jvm_gc_pause_seconds_count",
                                "jvm_gc_pause_seconds_sum", "hikaricp_connections_active",
                                "hikaricp_connections_pending", "hikaricp_connections_usage_seconds_count",
                                "hikaricp_connections_usage_seconds_sum", "hikaricp_connections_acquire_seconds_count",
                                "hikaricp_connections_acquire_seconds_sum"} or (name == "jvm_memory_used_bytes" and 'area="heap"' in line):
                        value = float(line.rsplit(" ", 1)[-1])
                        metrics[name] = metrics.get(name, 0) + value
                return metrics
            initial = scrape()
            start = time.monotonic()
            deadline = start + args.seconds
            def worker(index):
                results = []
                errors = []
                with closing(http.client.HTTPConnection("127.0.0.1", httpport, timeout=8)) as conn:
                    i = index
                    while time.monotonic() < deadline and not stop.is_set():
                        tick = time.monotonic()
                        path = paths[i % len(paths)]
                        try:
                            trace = checked(conn, path)
                            results.append((i % len(paths), tick-start, (time.monotonic()-tick)*1000, trace))
                        except Exception as exc:
                            errors.append(type(exc).__name__ + ": " + str(exc))
                            conn.close()
                        i += 1
                        time.sleep(.06)
                return results, errors
            samples = []
            stop = __import__("threading").Event()
            with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
                futures = [pool.submit(worker, i) for i in range(8)]
                while time.monotonic() < deadline:
                    time.sleep(min(5, max(0, deadline-time.monotonic())))
                    if shutil.disk_usage(lab).free < 5 * 1024**3:
                        stop.set()
                        raise RuntimeError("disk budget exhausted")
                    try:
                        samples.append({"elapsed": round(time.monotonic()-start, 3), **scrape()})
                    except Exception:
                        stop.set()
                        raise
                    print("SOAK_SAMPLE " + json.dumps(samples[-1]), flush=True)
                results, errors = [], []
                for future in futures:
                    ok, failed = future.result(timeout=12)
                    results.extend(ok); errors.extend(failed)
            elapsed = time.monotonic()-start
            final = scrape()
            # Allow production asynchronous audit executor and 5s scheduled flush to drain.
            time.sleep(6)
            counts = sql(f"SELECT count(*),count(*) FILTER (WHERE audit_action='VIEW_IMAGE'),"
                         "count(*) FILTER (WHERE audit_action='VIEW_IMAGE' AND audit_target LIKE 'hmac:v1:%') "
                         f"FROM app.access_log WHERE username='{username}'")
            audit_rows, image_rows, protected_rows = map(int, counts.split("|"))
            # Compare the exact returned request identities, not just an aggregate count.
            observed = sql(f"SELECT request_id FROM app.access_log WHERE username='{username}'").splitlines()
            from collections import Counter
            counts_by_trace = Counter(observed)
            missing_trace = sum(counts_by_trace[r[3]] == 0 for r in results)
            duplicate_trace = sum(counts_by_trace[r[3]] > 1 for r in results)
            assert len({r[3] for r in results}) == len(results), "duplicate HTTP response trace"
            windows = []
            for w in range(0, args.seconds, 15):
                v = [r[2] for r in results if w <= r[1] < w+15]
                if v:
                    windows.append(dict(start=w, requests=len(v), p95=pct(v,.95), p99=pct(v,.99)))
            stats = dict(seconds=round(elapsed,3), success=len(results), errors=len(errors),
                         rps=round(len(results)/elapsed,2),
                         p50=pct([r[2] for r in results],.5), p95=pct([r[2] for r in results],.95),
                         p99=pct([r[2] for r in results],.99),
                         by_path=[sum(r[0]==i for r in results) for i in range(len(paths))],
                         audit_rows=audit_rows, image_audit_rows=image_rows, missing_trace=missing_trace, duplicate_trace=duplicate_trace,
                         protected_image_audit_rows=protected_rows,
                         initial=initial, final=final, samples=samples, windows=windows,
                         async_submit_http=status)
            with closing(http.client.HTTPConnection("127.0.0.1", httpport, timeout=8)) as conn:
                if managed_id:
                    status, _, body = request(conn, f"/api/v1/system/data-quality/runs/{managed_id}", token)
                    stats["async_final_status"] = json.loads(body).get("data", {}).get("latestRun", {}).get("status")
                    assert status == 200 and stats["async_final_status"] == "SUCCESS"
                sql("UPDATE app.mr_auth_role SET permissions='system:read' WHERE code='SYNTHETIC_SOAK'")
                assert request(conn, paths[1], token)[0] == 403
                assert request(conn, "/api/v1/system/data-quality/runs", token, "POST")[0] == 403
                digest = hashlib.sha256(jti.encode()).hexdigest()
                sql(f"INSERT INTO app.token_revocation(token_hash,expires_at) VALUES ('{digest}',CURRENT_TIMESTAMP+interval '1 hour')")
                assert request(conn, paths[0], token)[0] == 401
            (lab / "result.json").write_text(json.dumps(stats, indent=2))
            (lab / "errors.json").write_text(json.dumps(errors[:20], indent=2))
            print("SOAK_RESULT " + json.dumps(stats), flush=True)
            assert not errors, errors[:5]
            assert image_rows >= stats["by_path"][4] and protected_rows == image_rows
            assert audit_rows >= len(results), "missing audit rows"
            assert missing_trace == duplicate_trace == 0, "audit trace mismatch"
    finally:
        if app is not None and app.poll() is None:
            os.killpg(app.pid, signal.SIGTERM)
            try:
                app.wait(timeout=30)
            except subprocess.TimeoutExpired:
                os.killpg(app.pid, signal.SIGKILL)
                app.wait(timeout=5)
        if probe_server is not None:
            probe_server.shutdown()
            probe_server.server_close()
        pgcmd("pg_ctl", "-D", lab / "data", "-m", "fast", "-w", "stop")


if __name__ == "__main__":
    main()
