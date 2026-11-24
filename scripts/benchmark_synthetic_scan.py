#!/usr/bin/env python3
"""Disposable PostgreSQL 16 scan-query lab; never accepts a DSN or existing cluster."""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import secrets
import shlex
import re
import shutil
import socket
import subprocess
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
MAX_ROWS = 30_000_000
PAGES = 100


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pg-bin", type=Path, required=True)
    parser.add_argument("--lab-parent", type=Path, required=True)
    parser.add_argument("--rows", type=int, default=100_000)
    parser.add_argument("--batch", type=int, default=100_000)
    parser.add_argument("--max-seconds", type=int, default=1800)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--sample-seconds", type=int, default=5)
    parser.add_argument("--multikey", action="store_true")
    args = parser.parse_args()
    if not 10_000 <= args.rows <= MAX_ROWS or not 1000 <= args.batch <= 100_000:
        parser.error("rows must be 10,000..30,000,000; batch 1,000..100,000")
    if not 60 <= args.max_seconds <= 7200:
        parser.error("max-seconds must be 60..7200")
    if not 1 <= args.sample_seconds <= 60:
        parser.error("sample-seconds must be 1..60")
    parent = args.lab_parent.resolve(strict=True)
    if not parent.is_dir():
        parser.error("lab-parent must be an existing directory")
    # Conservative space guard, including indexes, WAL and an 8 GiB reserve.
    reserve = 8 * 1024**3
    if shutil.disk_usage(parent).free < reserve + args.rows * 4096:
        parser.error("insufficient free space for synthetic lab")
    pg = args.pg_bin.resolve(strict=True)
    java = args.java_home.resolve(strict=True)
    lab = Path(tempfile.mkdtemp(prefix="mrr-scan-lab-", dir=parent))
    lab.chmod(0o700)
    print(f"Lab: {lab}", flush=True)
    data = lab / "pgdata"
    sock = lab / "socket"
    sock.mkdir(mode=0o700)
    password = secrets.token_urlsafe(24)
    pwfile = lab / "pw"
    pwfile.write_text(password + "\n")
    pwfile.chmod(0o600)
    env = {k: v for k, v in os.environ.items()
           if not k.startswith(("PG", "MRR_TEST_POSTGRES_"))}
    env.update(JAVA_HOME=str(java), PATH=str(java / "bin") + os.pathsep + env.get("PATH", ""), LC_ALL="C")
    deadline = time.monotonic() + args.max_seconds
    started = False
    report = {"requested_rows": args.rows, "batch": args.batch, "status": "incomplete", "plans": {}}

    def run(cmd, *, log=None, **kw):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("lab time budget exhausted")
        result = subprocess.run([str(v) for v in cmd], env=env, text=True,
                                capture_output=True, timeout=remaining, **kw)
        if log:
            (lab / log).write_text((result.stdout + result.stderr).replace(password, "[REDACTED]"))
        if result.returncode:
            raise RuntimeError(f"{Path(cmd[0]).name} failed ({result.returncode}); see lab logs")
        return result.stdout

    try:
        version = run([pg / "postgres", "--version"]).strip()
        if not version.startswith("postgres (PostgreSQL) 16."):
            raise RuntimeError("PostgreSQL 16 required")
        report["postgres"] = version
        run([pg / "initdb", "-D", data, "-U", "imgapi", "--auth-local=trust",
             "--auth-host=scram-sha-256", "--pwfile", pwfile, "--no-locale", "-E", "UTF8"], log="init.log")
        pwfile.unlink()
        # Local JDBC only; an occupied port fails startup rather than attaching to an existing server.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        run([pg / "pg_ctl", "-D", data, "-l", lab / "server.log", "-o",
             f"-h 127.0.0.1 -p {port} -k {shlex.quote(str(sock))}", "-w", "start"])
        started = True
        env["PGPASSWORD"] = password
        connect = ["-h", str(sock), "-p", str(port), "-U", "imgapi"]
        run([pg / "createdb", *connect, "imgapi"])
        env.update(MRR_TEST_POSTGRES_URL=f"jdbc:postgresql://127.0.0.1:{port}/imgapi",
                   MRR_TEST_POSTGRES_USERNAME="imgapi", MRR_TEST_POSTGRES_PASSWORD=password)
        backend = ROOT / "backend-repo"
        run([backend / "mvnw", "-o", "-B", "-ntp", "-Dtest=ImageContentServiceImplTest",
             "-Dit.test=ScanMapperPostgresqlIT", "verify"], cwd=backend, log="flyway-and-mapper.log")
        psql = [pg / "psql", *connect, "-d", "imgapi", "-X", "-v", "ON_ERROR_STOP=1"]

        def sql(query):
            return run([*psql, "-At", "-c", query]).strip()

        if sql("SELECT (SELECT count(*) FROM app.mr_scan)+(SELECT count(*) FROM app.mr_archive)") != "0":
            raise RuntimeError("fresh migrated test database not empty; refusing seed")
        archive_count = math.ceil(args.rows / PAGES)
        sql(f"""
            INSERT INTO app.mr_archive(id,bah,sjh,page_count)
            SELECT n, CASE WHEN n%5=0 THEN (10000000+n/50)::text ELSE (100000+n)::text END,
                   (20000000+n)::text, {PAGES}
            FROM generate_series(1,{archive_count}) n
        """)
        t = time.monotonic()
        for lo in range(1, args.rows + 1, args.batch):
            if shutil.disk_usage(parent).free < reserve:
                raise RuntimeError("disk reserve reached; stopping seed")
            hi = min(args.rows, lo + args.batch - 1)
            sql(f"""
                INSERT INTO app.mr_scan(id,archive_id,bah,sjh,brxh,filename,folder,pages,uploadflag,file_size)
                SELECT n,a.id,CASE WHEN a.id%7=0 AND a.id%5<>0 THEN lpad(a.bah,8,'0') ELSE a.bah END,
                       a.sjh,a.id::text,'synthetic-'||n||'.jpg','synthetic-'||(a.id/1000),
                       (n-1)%{PAGES}+1,CASE WHEN n%97=0 THEN 0 ELSE 1 END,65536
                FROM generate_series({lo},{hi}) n
                JOIN app.mr_archive a ON a.id=(n-1)/{PAGES}+1
            """)
            report["seeded_rows"] = hi
            print(f"Seeded {hi}/{args.rows}", flush=True)
        report["seed_seconds"] = round(time.monotonic() - t, 3)
        sql("ANALYZE app.mr_scan; ANALYZE app.mr_archive")
        report["actual_rows"] = int(sql("SELECT count(*) FROM app.mr_scan"))
        if report["actual_rows"] != args.rows:
            raise RuntimeError("seed row count mismatch")
        report["archive_rows"] = archive_count
        report["before_bytes"] = int(sql("SELECT pg_total_relation_size('app.mr_scan')"))
        # Existing production-style predicates; no forced planner settings.
        archive = min(55, archive_count)
        bah = sql(f"SELECT bah FROM app.mr_archive WHERE id={archive}")
        sjh = sql(f"SELECT sjh FROM app.mr_archive WHERE id={archive}")
        if not (bah.isdecimal() and sjh.isdecimal()):
            raise RuntimeError("synthetic code invariant failed")
        cases = {
            "archive": f"SELECT * FROM app.mr_scan WHERE archive_id={archive} AND uploadflag<>0 ORDER BY pages,id LIMIT 1001",
            "bah_cursor": f"SELECT * FROM app.mr_scan WHERE app.numeric_code_key(bah)='{bah}' AND id>0 ORDER BY id LIMIT 51",
            "sjh_cursor": f"SELECT * FROM app.mr_scan WHERE app.numeric_code_key(sjh)='{sjh}' AND id>0 ORDER BY id LIMIT 51",
            "legacy_count": f"SELECT count(*) FROM app.mr_scan WHERE bah LIKE '%{bah}%'",
        }

        def measure(phase):
            for name, query in cases.items():
                for sample in ("first", "repeat"):
                    key = f"{phase}-{name}-{sample}"
                    log = key + ".log"
                    try:
                        result = run([*psql, "-At", "-c",
                            "BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SET LOCAL lock_timeout='1s'; "
                            "EXPLAIN (ANALYZE,BUFFERS,SETTINGS,FORMAT JSON) " + query + "; ROLLBACK;"], log=log)
                    except RuntimeError:
                        # Only the explicitly optional compatibility COUNT can be recorded as timed out.
                        # Keep the budget, never reinterpret a failed query as a zero-row result.
                        evidence = (lab / log).read_text() if (lab / log).exists() else ""
                        if name != "legacy_count" or "canceling statement due to statement timeout" not in evidence:
                            raise
                        report["plans"][key] = {"status": "statement_timeout", "budget_ms": 5000}
                        report.setdefault("legacy_timeouts", []).append(key)
                        break
                    # -q suppresses transaction status lines, so isolate the JSON array.
                    plan = json.loads(result[result.index("["):result.rindex("]")+1])[0]
                    (lab / (key + ".json")).write_text(json.dumps(plan, indent=2))
                    node = plan["Plan"]
                    report["plans"][key] = {"execution_ms": plan["Execution Time"],
                        "planning_ms": plan["Planning Time"], "rows": node["Actual Rows"],
                        "shared_hit": node.get("Shared Hit Blocks", 0), "shared_read": node.get("Shared Read Blocks", 0)}
        measure("before")
        t = time.monotonic()
        run([*psql, "-f", backend / "scripts/build-code-key-indexes.sql"], log="index-build.log")
        report["index_build_seconds"] = round(time.monotonic() - t, 3)
        sql("ANALYZE app.mr_scan")
        report["after_bytes"] = int(sql("SELECT pg_total_relation_size('app.mr_scan')"))
        measure("after")
        same_page = run([*psql, "-v", f"archive_id={archive}", "-v", f"code={bah}", "-v", "deep_offset=10",
             "-v", "compare_exact=1", "-f", backend / "scripts/inspect-query-plans.sql"], log="same-page-plans.log")
        if same_page.count("SAME_PAGE_VERIFIED:") != 2:
            raise RuntimeError("same-page evidence missing")
        report["same_page_verified"] = True
        # Read-only pgbench workload with fixed synthetic key; separate from application cache benchmark.
        workload = lab / "lookup.sql"
        workload.write_text(cases["bah_cursor"] + ";\n")
        report["pgbench"] = {}
        for clients in (1, 8):
            output = run([pg / "pgbench", *connect, "-n", "-M", "prepared", "-c", str(clients),
                 "-j", str(min(clients, 4)), "-T", "5", "-f", workload, "imgapi"], log=f"pgbench-{clients}.log")
            failures = re.search(r"number of failed transactions: (\d+)", output)
            tps = re.search(r"tps = ([0-9.]+)", output)
            latency = re.search(r"latency average = ([0-9.]+) ms", output)
            if not failures or int(failures[1]) or not tps or not latency:
                raise RuntimeError("pgbench incomplete or failed transactions")
            report["pgbench"][str(clients)] = {"tps": float(tps[1]), "mean_ms": float(latency[1]), "failures": 0}
        if args.multikey:
            # Uniform keys across the entire synthetic archive range, no real identifiers.
            workload = lab / "multikey.sql"
            workload.write_text(
                f"\\set archive random(1, {archive_count})\n"
                "SELECT * FROM app.mr_scan WHERE app.numeric_code_key(sjh) = "
                "(20000000 + :archive)::text AND id>0 ORDER BY id LIMIT 51;\n"
            )
            report["multikey"] = {}
            for clients in (1, 8):
                prefix = lab / f"multi-{clients}"
                output = run([pg / "pgbench", *connect, "-n", "-M", "prepared",
                    "-c", str(clients), "-j", str(min(clients, 4)), "-T", str(args.sample_seconds),
                    "-l", "--log-prefix=" + str(prefix), "-f", workload, "imgapi"],
                    log=f"multikey-{clients}.log")
                failed = re.search(r"number of failed transactions: (\d+)", output)
                done = re.search(r"number of transactions actually processed: (\d+)", output)
                tps = re.search(r"tps = ([0-9.]+)", output)
                if not failed or int(failed[1]) or not done or not tps:
                    raise RuntimeError("multikey benchmark failed")
                samples = []
                for log in sorted(lab.glob(prefix.name + ".*")):
                    for line in log.read_text().splitlines():
                        fields = line.split()
                        if len(fields) < 6 or not fields[2].isdigit():
                            raise RuntimeError("unexpected pgbench transaction log")
                        samples.append(int(fields[2]))
                if len(samples) != int(done[1]) or not samples:
                    raise RuntimeError("transaction log count mismatch")
                samples.sort()
                report["multikey"][str(clients)] = {
                    "transactions": len(samples), "tps": float(tps[1]), "failed": 0,
                    **{f"p{p}_ms": samples[math.ceil(len(samples)*p/100)-1]/1000 for p in (50,95,99)}
                }
        report["status"] = "complete_with_legacy_timeouts" if report.get("legacy_timeouts") else "complete"
    finally:
        if started or (data / "postmaster.pid").is_file():
            # Stop only the exact cluster this invocation created, including a partially failed startup.
            try:
                result = subprocess.run([str(pg / "pg_ctl"), "-D", str(data), "-m", "fast", "-w", "stop"],
                                        env=env, capture_output=True, text=True, timeout=60)
                report["server_stopped"] = result.returncode == 0
            except subprocess.TimeoutExpired:
                report["server_stopped"] = False
            if not report["server_stopped"]:
                report["status"] = "incomplete"
        if pwfile.exists():
            pwfile.unlink()
        (lab / "report.json").write_text(json.dumps(report, indent=2))
        print(json.dumps(report, indent=2), flush=True)
    if report["status"] not in ("complete", "complete_with_legacy_timeouts"):
        raise RuntimeError("lab did not complete cleanly")


if __name__ == "__main__":
    main()
