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
import io
import zipfile
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
    parser.add_argument("--audit-outage", action="store_true", help="stop only the newly created isolated PG, then verify durable admission and restart")
    parser.add_argument("--grouped-audit", action="store_true", help="use sealed group commit and batched SQL candidate")
    parser.add_argument("--durable-audit", action="store_true", help="opt in to experimental encrypted POSIX spool")
    parser.add_argument("--medical-admission",action="store_true",help="enable experimental separate medical event gate")
    parser.add_argument("--admission-capacity",action="store_true",help="test actual first-byte admission and logical capacity rejection, then exit")
    parser.add_argument("--medical-probes", action="store_true", help="exercise synthetic patient, OCR ownership, migration log and export-plan responses")
    parser.add_argument("--signed-external", action="store_true", help="real HMAC ticket and session chain plus role probes")
    parser.add_argument("--external-probes", action="store_true", help="exercise synthetic external session, current scope and privacy headers before load")
    args = parser.parse_args()
    if args.admission_capacity and not args.medical_admission:
        parser.error("capacity requires explicit medical admission")
    if args.grouped_audit and not args.durable_audit:
        parser.error("grouped audit requires explicit durable candidate")
    if args.audit_outage and not args.durable_audit:
        parser.error("audit outage requires explicit durable candidate")
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
        integration_secret = secrets.token_hex(32)
        spool_key = base64.b64encode(secrets.token_bytes(32)).decode()
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
        if args.signed_external:
            config += "\nmrr.integration.enabled=true\nmrr.integration.clients[0].client-id=synthetic-his\nmrr.integration.clients[0].enabled=true\nmrr.integration.clients[0].allowed-ips[0]=127.0.0.1\n"
        if args.durable_audit:
            (lab/"audit-spool").mkdir(mode=0o700)
            config += f"\nmrr.audit.spool.enabled=true\nmrr.audit.spool.directory={(lab/'audit-spool').resolve()}\n"
        if args.grouped_audit:
            config += "\nmrr.audit.spool.grouped=true\n"
        if args.medical_admission:
            (lab/"medical-spool").mkdir(mode=0o700)
            config += f"\nmrr.audit.admission.enabled=true\nmrr.audit.admission.directory={(lab/'medical-spool').resolve()}\n"
            if args.admission_capacity:
                config += "mrr.audit.admission.max-records=2\n"
        (lab / "application.properties").write_text(config)
        env = {"HOME": os.environ["HOME"], "JAVA_HOME": str(args.java_home),
               "PATH": str(args.java_home / "bin") + ":/opt/homebrew/bin:/usr/bin:/bin",
               "LANG": "en_US.UTF-8", "SPRING_CONFIG_LOCATION": "file:" + str(lab / "application.properties"),
               "JWT_SECRET_KEY": secret, "MRR_AUDIT_HMAC_KEY": secrets.token_hex(32)}
        if args.signed_external:
            env["SPRING_APPLICATION_JSON"]=json.dumps({"mrr":{"integration":{"clients":[{"client-id":"synthetic-his","enabled":True,"allowed-ips":["127.0.0.1"],"secret":integration_secret}]}}})
        if args.durable_audit:
            env["MRR_AUDIT_SPOOL_KEY"]=spool_key
        if args.medical_admission:
            env["MRR_MEDICAL_AUDIT_KEY"]=base64.b64encode(secrets.token_bytes(32)).decode()
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
            if args.admission_capacity:
                # Hold an independent real PG table lock so a full two-slot gate cannot drain.
                with (lab/"admission-lock.log").open("w") as lock_output:
                    locker=subprocess.Popen([str(pg/"psql"),"-h",str(lab/"socket"),"-p",str(dbport),"-U","imgapi","-d","imgapi",
                        "-v","ON_ERROR_STOP=1","-c","BEGIN; LOCK TABLE app.medical_audit_event IN ACCESS EXCLUSIVE MODE; SELECT pg_sleep(8); COMMIT"],
                        stdout=lock_output,stderr=subprocess.STDOUT)
                    try:
                        end=time.monotonic()+5
                        while time.monotonic()<end:
                            if sql("SELECT count(*) FROM pg_locks WHERE relation='app.medical_audit_event'::regclass AND mode='AccessExclusiveLock' AND granted")=="1":break
                            time.sleep(.05)
                        else:raise RuntimeError("gate lab did not acquire exclusive lock")
                        path=f"/api/v1/img/content/{scan}"
                        with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=5)) as conn:
                            conn.request("GET",path,headers={"Authorization":"Bearer "+token})
                            response=conn.getresponse()
                            assert response.status==200
                            first_trace=response.getheader("X-Request-Id")
                            frames=list((lab/"medical-spool").glob("*.pending"))
                            assert frames and all(first_trace.encode() not in f.read_bytes() for f in frames)
                            assert response.read()==content
                            code,h,body=request(conn,path,token)
                            assert code==503 and json.loads(body)["code"]==503 and body!=content
                            denied_trace=h["X-Request-Id"]
                            print("MEDICAL_CAPACITY first-byte encrypted intent exists; second stream rejected 503",flush=True)
                    finally:
                        locker.wait(timeout=20)
                deadline=time.monotonic()+15
                while time.monotonic()<deadline:
                    count=sql(f"SELECT count(*) FROM app.medical_audit_event WHERE request_id='{first_trace}'")
                    if count=="2" and not list((lab/"medical-spool").glob("*.pending")):break
                    time.sleep(.1)
                assert count=="2"
                assert sql(f"SELECT count(*) FROM app.medical_audit_event WHERE request_id='{denied_trace}'")=="0"
                assert not list((lab/"medical-spool").glob("*.pending"))
                assert sql(f"SELECT count(*) FROM app.medical_audit_event WHERE request_id='{first_trace}' AND phase='OUTCOME' AND http_status='200'")=="1"
                # After releasing capacity the same protected request is admitted again.
                with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=5)) as conn:
                    code,h,b=request(conn,path,token)
                    assert code==200 and b==content
                    third_trace=h["X-Request-Id"]
                deadline=time.monotonic()+15
                while time.monotonic()<deadline:
                    if sql(f"SELECT count(*) FROM app.medical_audit_event WHERE request_id='{third_trace}'")=="2" and not list((lab/"medical-spool").glob("*.pending")):break
                    time.sleep(.1)
                assert sql(f"SELECT count(*) FROM app.medical_audit_event WHERE request_id='{third_trace}'")=="2"
                assert not list((lab/"medical-spool").glob("*.pending"))
                (lab/"admission-capacity.json").write_text(json.dumps({"first":200,"denied":503,"recovered":200,
                    "first_trace":first_trace,"denied_trace":denied_trace,"third_trace":third_trace,"events":4,"pending":0}))
                print("MEDICAL_CAPACITY recovered after actual lock release; four events, no event for denied request",flush=True)
                return
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
            if args.signed_external:
                # Real signed POST and exchange, no fabricated session DB row.
                scan_id = int(sql("INSERT INTO app.mr_scan(bah,brxh,filename,folder,uploadflag,pages,source_type,source_ref) "
                    "VALUES ('7654393','7654393','synthetic.png','synthetic-signed',1,1,'LOCAL','synthetic.png') RETURNING id").splitlines()[0])
                signed_probes=[]
                with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=12)) as conn:
                    ticket_path="/api/v1/integration/archive/tickets"
                    prefix="/api/v1/external/archive"
                    body=json.dumps({"externalUserId":"synthetic-doctor","bah":"7654393","allowDownload":True},separators=(",",":")).encode()
                    timestamp=str(int(time.time()))
                    nonce=uuid.uuid4().hex
                    canonical="\n".join(("POST",ticket_path,timestamp,nonce,hashlib.sha256(body).hexdigest()))
                    signed={"X-MRR-Client-Id":"synthetic-his","X-MRR-Timestamp":timestamp,"X-MRR-Nonce":nonce,
                            "X-MRR-Signature":hmac.new(integration_secret.encode(),canonical.encode(),hashlib.sha256).hexdigest(),
                            "Content-Type":"application/json"}
                    def call(method,path,expected,headers=None,body=None):
                        conn.request(method,path,body=body,headers=headers or {})
                        response=conn.getresponse()
                        payload=response.read(); h=dict(response.getheaders())
                        assert response.status==expected,(method,path,response.status)
                        assert "no-store" in h.get("Cache-Control",""),(method,path,"no-store")
                        assert h.get("Referrer-Policy")=="no-referrer",(method,path,"referrer")
                        signed_probes.append(dict(method=method,path=path,status=response.status))
                        return payload,h
                    invalid=dict(signed);invalid["X-MRR-Signature"]="0"*64
                    call("POST",ticket_path,401,invalid,body)
                    payload,_=call("POST",ticket_path,200,signed,body)
                    ticket=json.loads(payload)["data"]["ticket"]
                    call("POST",ticket_path,409,signed,body)
                    exchange=json.dumps({"ticket":ticket}).encode()
                    payload,h=call("POST",prefix+"/session",200,{"Content-Type":"application/json"},exchange)
                    cookie=h["Set-Cookie"].split(";",1)[0]
                    assert "HttpOnly" in h["Set-Cookie"] and "SameSite=Lax" in h["Set-Cookie"]
                    session={"Cookie":cookie}
                    call("POST",prefix+"/session",401,{"Content-Type":"application/json"},exchange)
                    call("GET",prefix+"/context",200,session)
                    payload,_=call("GET",prefix+"/images?bah=7654393",200,session)
                    assert [x["id"] for x in json.loads(payload)["data"]]==[scan_id]
                    payload,_=call("GET",prefix+f"/image/{scan_id}",200,session)
                    assert payload==content
                    payload,_=call("GET",prefix+"/download?bah=7654393",200,session)
                    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
                        assert len(archive.namelist())==1 and archive.read(archive.namelist()[0])==content
                    call("GET",prefix+"/download?bah=7654300",403,session)
                    call("POST",prefix+"/logout",200,session)
                    call("GET",prefix+"/context",401,session)
                sql(f"DELETE FROM app.mr_scan WHERE id={scan_id}")
                sql("ANALYZE app.mr_scan")
                (lab/"signed-external-probes.json").write_text(json.dumps(signed_probes,indent=2))
                print("SIGNED_EXTERNAL_PROBES "+json.dumps(signed_probes),flush=True)
                # Browser allowlist and medical permission in real application. Use synthetic historical free text.
                sql("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES "
                    "('SYNTHETIC_BROWSER','synthetic','system:db:browse'),"
                    "('SYNTHETIC_BROWSER_MED','synthetic','system:db:browse,record:read'),"
                    "('SYNTHETIC_BROWSER_OPS','synthetic','system:db:browse,system:manage')")
                browser_tokens={}
                for role in ("SYNTHETIC_BROWSER","SYNTHETIC_BROWSER_MED","SYNTHETIC_BROWSER_OPS"):
                    name=role.lower()
                    bid=int(sql("INSERT INTO app.mr_auth_user(username,display_name,password_hash,role_code,status,must_change_password,password_version) "
                        f"VALUES ('{name}','synthetic','not-a-password','{role}','active',false,1) RETURNING id").splitlines()[0])
                    browser_tokens[role]=jwt(bid,name,secret,args.seconds+600)[0]
                sql("UPDATE app.access_log SET request_uri='/synthetic/SYNTHETIC_PRIVATE_BROWSE',endpoint_template='SYNTHETIC_PRIVATE_BROWSE',"
                    "audit_target='SYNTHETIC_PRIVATE_BROWSE',audit_description='SYNTHETIC_PRIVATE_BROWSE',"
                    f"error_message='SYNTHETIC_PRIVATE_BROWSE',user_agent='SYNTHETIC_PRIVATE_BROWSE' WHERE id={historical_id}")
                browse_probes=[]
                with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=8)) as conn:
                    prefix="/api/v1/system/database/browse/tables"
                    def browse(path,t,expected):
                        code,h,payload=request(conn,path,t)
                        assert code==expected,(path,code,expected)
                        assert "no-store" in h.get("Cache-Control",""),(path,"no-store")
                        assert b"SYNTHETIC_PRIVATE_BROWSE" not in payload
                        browse_probes.append(dict(path=path,status=code))
                        return json.loads(payload)
                    basic=browser_tokens["SYNTHETIC_BROWSER"]
                    med=browser_tokens["SYNTHETIC_BROWSER_MED"]
                    ops=browser_tokens["SYNTHETIC_BROWSER_OPS"]
                    tables=browse(prefix,basic,200)
                    assert "scan" not in [x["tableKey"] for x in tables["data"]]
                    browse(prefix+"/scan/rows",basic,404)
                    browse(prefix+"/scan",med,200)
                    rows=browse(prefix+"/scan/rows?size=1",med,200)["data"]["rows"]
                    assert rows and "oss_url" not in rows[0] and rows[0]["bah"]!="7654300"
                    browse(prefix+"/access_log/rows",med,404)
                    browse(prefix+"/access_log",ops,200)
                    browse(prefix+"/access_log/rows?order=asc&size=1",ops,200)
                (lab/"browse-probes.json").write_text(json.dumps(browse_probes,indent=2))
                print("BROWSE_PROBES "+json.dumps(browse_probes),flush=True)
            if args.medical_probes:
                # Use only synthetic identities. Separate role tokens from the measured load identity.
                medical=[]
                sql("INSERT INTO app.mr_patient(bah,idcard,name,department) VALUES ('7654381','SYNTHETIC-ID-9','SYNTHETIC_PATIENT_9','SYNTHETIC_DEPT')")
                sql("INSERT INTO app.mr_auth_role(code,name,permissions) VALUES "
                    "('SYNTHETIC_MED_NONE','synthetic','system:read'),"
                    "('SYNTHETIC_MED_READ','synthetic','record:read'),"
                    "('SYNTHETIC_MED_OCR','synthetic','record:read,ocr:run,ocr:batch-run'),"
                    "('SYNTHETIC_MED_MANAGE','synthetic','record:read,record:manage')")
                actors={}
                for label,role in (("none","SYNTHETIC_MED_NONE"),("read","SYNTHETIC_MED_READ"),("ocr1","SYNTHETIC_MED_OCR"),("ocr2","SYNTHETIC_MED_OCR"),("manage","SYNTHETIC_MED_MANAGE")):
                    name="synthetic-med-"+label
                    actor=int(sql("INSERT INTO app.mr_auth_user(username,display_name,password_hash,role_code,status,must_change_password,password_version) "
                        f"VALUES ('{name}','synthetic','not-a-password','{role}','active',false,1) RETURNING id").splitlines()[0])
                    actors[label]=(actor,jwt(actor,name,secret,args.seconds+600)[0])
                batch_ids=[str(uuid.uuid4()),str(uuid.uuid4())]
                for batch_id,label in zip(batch_ids,("ocr1","ocr2")):
                    owner=actors[label][0]
                    sql("INSERT INTO app.mr_ocr_batch(id,owner_user_id,created_by,idempotency_key,status,after_id,upper_id,cursor_id,max_pages,config_snapshot,rule_set_version,lexicon_version) "
                        f"VALUES ('{batch_id}',{owner},'synthetic-med-{label}','{batch_id}','PAUSED',0,1,0,1,'{{}}','synthetic','synthetic')")
                sql(f"INSERT INTO app.image_migration_log(scan_id,local_path,oss_url,migration_status,error_message) VALUES ({scan},'SYNTHETIC_PRIVATE_PATH','SYNTHETIC_HISTORICAL_OBJECT_KEY','success','SYNTHETIC_PRIVATE_ERROR')")
                with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=10)) as conn:
                    def med(path,label,expected,private=False,forbidden=()):
                        auth=token if label=="load" else actors[label][1]
                        code,h,b=request(conn,path,auth)
                        assert code==expected,(path,label,code,expected)
                        if private: assert "no-store" in h.get("Cache-Control",""),(path,"no-store")
                        assert all(marker.encode() not in b for marker in forbidden),(path,"private data")
                        medical.append(dict(path=path,role=label,status=code))
                        return json.loads(b)
                    for path in ("/api/v1/patients?keyword=SYNTHETIC_PATIENT_9","/api/v1/patients/bah/7654381"):
                        med(path,"none",403,True,("SYNTHETIC_PATIENT_9","SYNTHETIC-ID-9"))
                        data=med(path,"read",200,True)["data"]
                        rows=data["list"] if isinstance(data,dict) else data
                        assert len(rows)==1 and rows[0]["name"]=="SYNTHETIC_PATIENT_9"
                    prefix="/api/v1/ocr/batches"
                    med(prefix,"read",403)
                    own=med(prefix,"ocr1",200,True)["data"]
                    assert [x["id"] for x in own]==[batch_ids[0]]
                    assert med(prefix+"/"+batch_ids[0],"ocr1",200,True)["data"]["id"]==batch_ids[0]
                    med(prefix+"/"+batch_ids[1],"ocr1",403,True)
                    med(prefix+"/"+batch_ids[1]+"/jobs","ocr1",403,True)
                    med(prefix+"/"+batch_ids[0]+"/jobs","ocr1",200,True)
                    for path in ("/api/v1/oss/migration/logs?status=success",f"/api/v1/oss/migration/management/logs?status=success&scanId={scan}"):
                        med(path,"none",403,True,("SYNTHETIC_HISTORICAL_OBJECT_KEY","SYNTHETIC_PRIVATE_PATH","SYNTHETIC_PRIVATE_ERROR"))
                        rows=med(path,"read",200,True,("SYNTHETIC_HISTORICAL_OBJECT_KEY","SYNTHETIC_PRIVATE_PATH","SYNTHETIC_PRIVATE_ERROR"))["data"]["list"]
                        assert rows and rows[0]["scanId"]==scan and rows[0].get("ossUrl") is None
                    med("/api/v1/oss/browser","read",403,True)
                    med("/api/v1/oss/browser","manage",200,True)
                    med("/api/v1/oss/browser/url?key=medical-records/SYNTHETIC_PRIVATE_KEY.jpg","read",403,True,("SYNTHETIC_PRIVATE_KEY",))
                    med("/api/v1/oss/browser/url?key=medical-records/SYNTHETIC_PRIVATE_KEY.jpg","manage",409,True,("SYNTHETIC_PRIVATE_KEY",))
                    med("/api/v1/archive-exports/plan/zip?bah=7654300","read",403)
                    assert med("/api/v1/archive-exports/plan/zip?bah=7654300","load",200)["data"]["totalCount"]==1000
                (lab/"medical-probes.json").write_text(json.dumps(medical,indent=2))
                print("MEDICAL_PROBES "+json.dumps(medical),flush=True)
            if args.audit_outage:
                # Stop only the fresh lab database, not the developer or production service.
                spool_dir=lab/"audit-spool"
                deadline=time.monotonic()+15
                while any(spool_dir.glob("*.pending")) and time.monotonic()<deadline:
                    time.sleep(.1)
                assert not any(spool_dir.glob("*.pending")), "pre-outage audit backlog"
                pgcmd("pg_ctl","-D",lab/"data","-m","fast","-w","stop")
                outage_trace=None
                try:
                    assert not (lab/"data"/"postmaster.pid").exists()
                    with closing(http.client.HTTPConnection("127.0.0.1",httpport,timeout=10)) as conn:
                        code,h,_=request(conn,"/api/v1/external/archive/context")
                        assert code==401,(code,"anonymous external context must remain denied")
                        outage_trace=next(v for k,v in h.items() if k.lower()=="x-request-id")
                    deadline=time.monotonic()+5
                    while not any(spool_dir.glob("*.pending")) and time.monotonic()<deadline:
                        time.sleep(.05)
                    frames=list(spool_dir.glob("*.pending"))
                    assert frames and all(outage_trace.encode() not in f.read_bytes() for f in frames)
                    print("AUDIT_OUTAGE encrypted pending="+str(len(frames))+" db_stopped=true",flush=True)
                finally:
                    pgcmd("pg_ctl","-D",lab/"data","-l",lab/"pg.log",
                          "-o",f"-h 127.0.0.1 -p {dbport} -k {lab/'socket'} -c shared_preload_libraries=pg_stat_statements","-w","start")
                deadline=time.monotonic()+25
                while time.monotonic()<deadline:
                    count=int(sql(f"SELECT count(*) FROM app.access_log WHERE request_id='{outage_trace}'"))
                    if count==1 and not any(spool_dir.glob("*.pending")):break
                    time.sleep(.25)
                assert count==1 and not any(spool_dir.glob("*.pending")), "outage recovery did not converge"
                (lab/"audit-outage.json").write_text(json.dumps({"pg_stopped":True,"ciphertext_pending":len(frames),"recovered":count,"pending_after":0}))
                print("AUDIT_OUTAGE recovered exactly once; pending=0",flush=True)
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
                    if name.startswith(("mrr_audit_","mrr_medical_audit_")) or name in {"process_cpu_usage", "process_cpu_time_ns", "process_files_open_files", "process_files_max_files", "jvm_threads_live_threads", "jvm_gc_pause_seconds_count",
                                "jvm_gc_pause_seconds_sum", "hikaricp_connections_active",
                                "hikaricp_connections_pending", "hikaricp_connections_usage_seconds_count",
                                "hikaricp_connections_usage_seconds_sum", "hikaricp_connections_acquire_seconds_count",
                                "hikaricp_connections_acquire_seconds_sum"} or (name == "jvm_memory_used_bytes" and 'area="heap"' in line):
                        value = float(line.rsplit(" ", 1)[-1])
                        metrics[name] = metrics.get(name, 0) + value
                return metrics
            def audit_sql_counts():
                # Query text remains inside the isolated database; output only categories and counts.
                raw=sql("""SELECT
                    coalesce(sum(calls) FILTER (WHERE query LIKE 'SELECT pg_advisory_xact_lock(%'),0),
                    coalesce(sum(calls) FILTER (WHERE query LIKE 'SELECT count(*) FROM access_log WHERE request_id=%'),0),
                    coalesce(sum(calls) FILTER (WHERE query LIKE 'SELECT EXISTS (SELECT 1 FROM access_log WHERE request_id=%'),0),
                    coalesce(sum(calls) FILTER (WHERE query LIKE 'WITH incoming(request_id,%'),0),
                    coalesce(sum(calls) FILTER (WHERE query LIKE 'INSERT INTO access_log (request_id,%'),0)
                    FROM public.pg_stat_statements""")
                return dict(zip(("lock","identity","exact","batch_reconcile","insert"),map(int,raw.split("|"))))
            initial_sql=audit_sql_counts()
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
            final_sql=audit_sql_counts()
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
            # Preserve the fixed-six-second observation even if the candidate needs longer.
            pre_drain = dict(audit_rows=audit_rows,image_audit_rows=image_rows,
                             missing_trace=missing_trace,duplicate_trace=duplicate_trace)
            if args.durable_audit:
                # Record both the latency failure and the final reconciliation, not one in place of the other.
                (lab/"synthetic-load-traces.json").write_text(json.dumps([{"id":r[3],"image":r[0]==4} for r in results]))
                drain_started=time.monotonic()
                pending_at_six_seconds=len(list((lab/"audit-spool").glob("*.pending")))
                drain_deadline=drain_started+25
                while any((lab/"audit-spool").glob("*.pending")) and time.monotonic()<drain_deadline:
                    time.sleep(.1)
                drain_elapsed=round(time.monotonic()-drain_started,3)
                remaining=len(list((lab/"audit-spool").glob("*.pending")))
                assert remaining==0,"durable audit drain deadline exceeded"
                counts=sql(f"SELECT count(*),count(*) FILTER (WHERE audit_action='VIEW_IMAGE'),"
                           "count(*) FILTER (WHERE audit_action='VIEW_IMAGE' AND audit_target LIKE 'hmac:v1:%') "
                           f"FROM app.access_log WHERE username='{username}'")
                audit_rows,image_rows,protected_rows=map(int,counts.split("|"))
                counts_by_trace=Counter(sql(f"SELECT request_id FROM app.access_log WHERE username='{username}'").splitlines())
                missing_trace=sum(counts_by_trace[r[3]]==0 for r in results)
                duplicate_trace=sum(counts_by_trace[r[3]]>1 for r in results)
                pre_drain.update(pending_files=pending_at_six_seconds,extra_drain_seconds=drain_elapsed)
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
                         async_submit_http=status, periodic_verified=verified, resource_probes=len(probe_results),
                         medical_probes=len(medical) if args.medical_probes else 0,
                         audit_sql_delta={k:final_sql[k]-initial_sql[k] for k in initial_sql})
            if args.durable_audit:
                stats["fixed_six_second_reconciliation"]=pre_drain
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
            if args.durable_audit:
                # HTTP completion callbacks can run immediately after the last control response.
                # Do not destroy the synthetic key or stop PG with unacknowledged ciphertext.
                time.sleep(.5)
                deadline=time.monotonic()+25
                while any((lab/"audit-spool").glob("*.pending")) and time.monotonic()<deadline:
                    time.sleep(.1)
                stats["spool_pending_after_controls"]=len(list((lab/"audit-spool").glob("*.pending")))
                assert stats["spool_pending_after_controls"]==0, "durable audit shutdown backlog"
                stats["audit_final_metrics"]={k:v for k,v in scrape().items() if k.startswith("mrr_audit_")}
                assert stats["audit_final_metrics"].get("mrr_audit_dropped",0)==0,"durable admission failures"
            if args.medical_admission:
                end=time.monotonic()+25
                while any((lab/"medical-spool").glob("*.pending")) and time.monotonic()<end:time.sleep(.1)
                assert not any((lab/"medical-spool").glob("*.pending")), "medical admission drain incomplete"
                from collections import Counter
                observed=Counter(tuple(row.split("|")) for row in sql(
                    f"SELECT request_id,phase FROM app.medical_audit_event WHERE actor_id={uid}").splitlines())
                expected=[r[3] for r in results if r[0] in (2,3,4)]
                assert all(observed[(trace,phase)]==1 for trace in expected for phase in ("INTENT","OUTCOME"))
                assert all(observed[(r[3],phase)]==0 for r in results if r[0] in (0,1) for phase in ("INTENT","OUTCOME"))
                stats["medical_final_metrics"]={k:v for k,v in scrape().items() if k.startswith("mrr_medical_audit_")}
                assert stats["medical_final_metrics"].get("mrr_medical_audit_pending")==0
                assert stats["medical_final_metrics"].get("mrr_medical_audit_reserved")==0
                assert stats["medical_final_metrics"].get("mrr_medical_audit_rejected_total")==0
                assert stats["medical_final_metrics"].get("mrr_medical_audit_failure_total")==0
                stats["medical_admission"]={"protected_requests":len(expected),"exact_pairs":len(expected),"pending":0,
                    "unprotected_requests":len(results)-len(expected)}
                (lab/"medical-admission-traces.json").write_text(json.dumps(expected))
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
