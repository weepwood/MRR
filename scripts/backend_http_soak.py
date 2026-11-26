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
from threading import Thread, Event
import json
import os
from pathlib import Path
import secrets
import queue
import re
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
    parser.add_argument("--checkpoint-seconds", type=int, default=60)
    parser.add_argument("--external-probes", action="store_true", help="exercise synthetic external session, current scope and privacy headers before load")
    args = parser.parse_args()
    if not 15 <= args.checkpoint_seconds <= 60:
        parser.error("checkpoint interval must be 15..60 seconds")
    if not 30 <= args.seconds <= (7200 if args.extended else 600):
        parser.error("duration must be 30..600 seconds (7200 with --extended)")
    if shutil.disk_usage(args.lab_root).free < 5 * 1024**3:
        parser.error("isolated lab requires at least 5 GiB free")
    stop = Event()
    def interrupt(_signum, _frame):
        stop.set()
        raise KeyboardInterrupt("isolated soak interrupted")
    signal.signal(signal.SIGTERM, interrupt)
    lab = Path(tempfile.mkdtemp(prefix="mrr-http-soak-", dir=args.lab_root))
    print("LAB=" + str(lab), flush=True)
    for name in ("socket", "app-tmp", "home", "images", "exports"):
        (lab / name).mkdir()
    pg = args.pg_bin
    assert "PostgreSQL) 16." in subprocess.check_output([str(pg / "initdb"), "--version"], text=True)
    dbport, httpport, management = port(), port(), port()
    # Independent PG instance; no caller-supplied DB endpoint.
    def pgcmd(*command, **kwargs):
        return subprocess.run([str(pg / command[0]), *map(str, command[1:])], check=True, timeout=45, **kwargs)
    def sql(statement):
        return subprocess.check_output([str(pg / "psql"), "-h", str(lab / "socket"), "-p", str(dbport),
                                        "-U", "imgapi", "-d", "imgapi", "-At", "-v", "ON_ERROR_STOP=1",
                                        "-c", statement], text=True, timeout=30).strip()
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
                "('SYNTHETIC_SOAK','synthetic','system:read,system:manage,record:read,record:download,log:read')")
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
            # Historical export ownership and actual serialized log fields; never use real data.
            own, foreign, legacy = (str(uuid.uuid4()) for _ in range(3))
            for job_id, owner in ((own, str(uid)), (foreign, str(uid + 100000)), (legacy, "NULL")):
                sql("INSERT INTO app.archive_export_job(id,owner_user_id,owner_username,format,scope,status,"
                    "file_name,bah,error_message,expires_at) VALUES "
                    f"('{job_id}',{owner},'{username}','ZIP','SELECTED_IMAGES','SUCCESS',"
                    "'synthetic.zip','7654300','SYNTHETIC_PRIVATE_EXPORT',CURRENT_TIMESTAMP+interval '3 hours')")
            historical_id = int(sql("INSERT INTO app.access_log(username,request_uri,endpoint_template,method,access_time,"
                "query_string,request_body,audit_action,audit_target,error_message) VALUES "
                "('synthetic-historical','/synthetic/private','/api/v1/img/content/{id}','GET',CURRENT_TIMESTAMP-interval '1200 days',"
                "'status=SYNTHETIC_PRIVATE_LOG&page=123456789012345678',"
                "'{\"status\":\"SYNTHETIC_PRIVATE_LOG\",\"page\":2}','VIEW_IMAGE',"
                "'hmac:v1:SYNTHETIC_PRIVATE_LOG','SYNTHETIC_PRIVATE_LOG') RETURNING id").splitlines()[0])
            probe_results = []
            with closing(http.client.HTTPConnection("127.0.0.1", httpport, timeout=8)) as conn:
                def probe(path, expected, method="GET", forbidden=()):
                    code, headers, body = request(conn, path, token, method)
                    assert code == expected, (method, path, code)
                    assert all(x.encode() not in body for x in forbidden), (method,path,"private response field")
                    probe_results.append(dict(method=method,path=path,status=code))
                    return headers, body
                prefix="/api/v1/archive-exports/jobs"
                _, own_body=probe(f"{prefix}/{own}",200,forbidden=("SYNTHETIC_PRIVATE_EXPORT","ownerUserId","filePath"))
                assert json.loads(own_body)["data"]["id"] == own
                _, listed=probe(prefix+"?format=ZIP&limit=20",200,forbidden=(foreign,legacy,"SYNTHETIC_PRIVATE_EXPORT","filePath"))
                assert [job["id"] for job in json.loads(listed)["data"]] == [own]
                for denied in (foreign,legacy):
                    probe(f"{prefix}/{denied}",403,forbidden=("7654300","SYNTHETIC_PRIVATE_EXPORT"))
                    probe(f"{prefix}/{denied}/download",403)
                    probe(f"{prefix}/{denied}/cancel",403,"POST")
                _, log_body=probe(f"/api/v1/logs/{historical_id}",200,forbidden=("SYNTHETIC_PRIVATE_LOG","123456789012345678"))
                assert json.loads(log_body)["data"]["id"] == historical_id
                _, searched=probe("/api/v1/logs/search?username=synthetic-historical",200,
                    forbidden=("SYNTHETIC_PRIVATE_LOG","123456789012345678"))
                assert str(historical_id).encode() in searched
                headers, exported=probe("/api/v1/logs/retention/export",200,
                    forbidden=("SYNTHETIC_PRIVATE_LOG","123456789012345678"))
                assert b"synthetic-historical" in exported
                assert "no-store" in headers.get("Cache-Control","")
            (lab / "resource-probes.json").write_text(json.dumps(probe_results,indent=2))
            print("RESOURCE_PROBES " + json.dumps(probe_results), flush=True)
            if args.external_probes:
                # A direct synthetic session fixture isolates current authorization from ticket issuance.
                # All real reads use the normal external controller and its actual storage service.
                session_token = secrets.token_urlsafe(32)
                session_hash = hashlib.sha256(session_token.encode()).hexdigest()
                sql("INSERT INTO app.mr_external_archive_session(session_hash,client_id,external_user_id,allow_download,grant_json,expires_at,created_ip) "
                    f"VALUES ('{session_hash}','synthetic-external','synthetic-external-user',false,"
                    "'[{\"bah\":\"7654391\",\"sjh\":\"\"}]'::jsonb,CURRENT_TIMESTAMP+interval '5 minutes','127.0.0.1')")
                other_file = lab / "images" / "private-fixture.png"
                other_file.write_bytes(b"synthetic-private-image")
                external_id = int(sql("INSERT INTO app.mr_scan(bah,brxh,filename,folder,uploadflag,source_type,source_ref) "
                    "VALUES ('7654391','7654391','synthetic.png','synthetic-external',1,'LOCAL','synthetic.png') RETURNING id").splitlines()[0])
                external_results = []
                with closing(http.client.HTTPConnection("127.0.0.1", httpport, timeout=8)) as conn:
                    def external(path, expected, method="GET", authenticated=True):
                        headers = {"Cookie": "MRR_ARCHIVE_SESSION="+session_token} if authenticated else {}
                        conn.request(method,path,headers=headers)
                        response=conn.getresponse()
                        payload=response.read()
                        actual=dict(response.getheaders())
                        assert response.status == expected,(method,path,response.status)
                        assert "no-store" in actual.get("Cache-Control",""),(method,path,"missing no-store")
                        assert actual.get("Referrer-Policy") == "no-referrer",(method,path,"missing referrer policy")
                        external_results.append(dict(method=method,path=path,status=response.status))
                        return payload
                    prefix="/api/v1/external/archive"
                    external(prefix+"/context",401,authenticated=False)
                    external("/api/v1/integration/archive/tickets",400,"POST",False)
                    external(prefix+"/context",200)
                    payload=json.loads(external(prefix+"/images?bah=7654391",200))
                    rows=payload["data"]
                    assert len(rows)==1 and rows[0]["id"]==external_id
                    assert all(rows[0].get(k) is None for k in ("folder","brxh","openerNo","ossUrl"))
                    assert external(prefix+f"/image/{external_id}",200)==content
                    external(prefix+"/download?bah=7654391",403)
                    external(prefix+"/images?bah=7654300",403)
                    sql(f"UPDATE app.mr_scan SET bah='7654392',filename='private-fixture.png',source_ref='private-fixture.png' WHERE id={external_id}")
                    assert b"synthetic-private-image" not in external(prefix+f"/image/{external_id}",403)
                    sql(f"UPDATE app.mr_external_archive_session SET revoked_at=CURRENT_TIMESTAMP WHERE session_hash='{session_hash}'")
                    external(prefix+"/context",401)
                sql(f"DELETE FROM app.mr_scan WHERE id={external_id}")
                sql("ANALYZE app.mr_scan")
                (lab/"external-probes.json").write_text(json.dumps(external_results,indent=2))
                print("EXTERNAL_PROBES "+json.dumps(external_results),flush=True)
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
            completed = queue.SimpleQueue()
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
                            record = (i % len(paths), tick-start, (time.monotonic()-tick)*1000, trace)
                            results.append(record)
                            completed.put(record)
                        except Exception as exc:
                            errors.append(type(exc).__name__ + ": " + str(exc))
                            conn.close()
                        i += 1
                        time.sleep(.06)
                return results, errors
            samples = []
            pending = {}
            verified = 0
            checkpoint_next = args.checkpoint_seconds
            def checkpoint(elapsed):
                nonlocal verified
                while True:
                    try:
                        item = completed.get_nowait()
                    except queue.Empty:
                        break
                    pending[item[3]] = item
                # Let the asynchronous audit writer flush before checking a trace.
                eligible = [trace for trace,item in pending.items() if item[1]+item[2]/1000 <= elapsed-10]
                missing, duplicates, mismatched = 0,0,0
                for off in range(0,len(eligible),500):
                    ids = eligible[off:off+500]
                    # Application request IDs are 16 hexadecimal characters.
                    if not all(re.fullmatch(r"[0-9a-f]{16}", t) for t in ids):
                        raise RuntimeError("unexpected request trace format")
                    values = ",".join("'" + t + "'" for t in ids)
                    observed = sql("SELECT request_id,count(*),bool_and(audit_action='VIEW_IMAGE' AND "
                        "audit_target ~ '^hmac:v1:[0-9a-f]{64}(;hmac:v1:[0-9a-f]{64})?$') "
                        f"FROM app.access_log WHERE username='{username}' AND request_id IN ({values}) GROUP BY request_id")
                    rows = {line.split("|")[0]:line.split("|")[1:] for line in observed.splitlines() if line}
                    for trace in ids:
                        if trace not in rows:
                            missing += 1
                            continue
                        count,protected = rows[trace]
                        if int(count)!=1:
                            duplicates += 1
                        elif pending[trace][0]==4 and protected!="t":
                            mismatched += 1
                        else:
                            verified += 1
                            pending.pop(trace)
                progress = dict(elapsed=round(elapsed,3),verified=verified,pending=len(pending),
                    eligible=len(eligible),missing=missing,duplicates=duplicates,unprotected_images=mismatched)
                with (lab/"audit-checkpoints.jsonl").open("a") as out:
                    out.write(json.dumps(progress)+"\n")
                print("AUDIT_CHECKPOINT "+json.dumps(progress),flush=True)
                return progress
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
                    elapsed = time.monotonic()-start
                    if elapsed >= checkpoint_next:
                        try:
                            checkpoint(elapsed)
                        except Exception:
                            stop.set()
                            raise
                        checkpoint_next += args.checkpoint_seconds
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
                         async_submit_http=status, periodic_verified=verified, resource_probes=len(probe_results))
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
            assert elapsed >= args.seconds, "duration incomplete"
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
