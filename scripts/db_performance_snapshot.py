#!/usr/bin/env python3
"""MRR 数据库性能基线快照。

用途：在调优前后各跑一次，用同一套只读 SQL 产出可比对的基线，作为变更效果证据。
依赖：本机 psql（PostgreSQL 客户端），不需要 Python 第三方库。

用法：
    set MRR_DB_DSN=postgresql://mrr_monitor:***@127.0.0.1:5432/imgapi
    python scripts/db_performance_snapshot.py --label before-index-v3
    python scripts/db_performance_snapshot.py --label after-index-v3 --compare-to .workbuddy/db-snapshots/2026-11-19T0210-before-index-v3/snapshot.json

输出目录默认 .workbuddy/db-snapshots/<时间戳>-<label>/，包含：
    snapshot.json   关键标量指标，用于前后对比
    summary.md      人读摘要
    0N-*.txt        各诊断 SQL 的完整输出

隐私：DSN 中的口令不会被写入任何输出文件；诊断 SQL 可能包含未参数化的字面量，
报告目录只允许留在内网，不得提交到仓库或上传到工单。
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
DIAG_DIR = REPO_ROOT / "monitoring" / "postgresql" / "diagnostics"
DEFAULT_OUT_ROOT = REPO_ROOT / ".workbuddy" / "db-snapshots"

SNAPSHOT_SQL = """
SELECT 'database_size_bytes', pg_database_size(current_database())::text
UNION ALL
SELECT 'cache_hit_pct',
       round(100 * sum(blks_hit)::numeric / NULLIF(sum(blks_hit + blks_read), 0), 3)::text
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'temp_bytes_total', coalesce(sum(temp_bytes), 0)::text
  FROM pg_stat_database WHERE datname = current_database()
UNION ALL
SELECT 'dead_tuple_ratio',
       coalesce(round(max(n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0)), 4), 0)::text
  FROM pg_stat_user_tables
UNION ALL
SELECT 'tables_over_1gb',
       count(*)::text
  FROM pg_stat_user_tables WHERE pg_total_relation_size(relid) > 1073741824
UNION ALL
SELECT 'longest_xact_seconds',
       coalesce(round(max(extract(epoch FROM now() - xact_start)), 1), 0)::text
  FROM pg_stat_activity WHERE xact_start IS NOT NULL
UNION ALL
SELECT 'connections_used_pct',
       round(100 * count(*)::numeric / current_setting('max_connections')::int, 1)::text
  FROM pg_stat_activity
UNION ALL
SELECT 'idle_in_transaction',
       count(*)::text
  FROM pg_stat_activity WHERE state LIKE 'idle in transaction%'
"""

STATEMENTS_SQL = """
SELECT 'statements_tracked', count(*)::text FROM pg_stat_statements
UNION ALL
SELECT 'avg_latency_ms',
       coalesce(round((sum(total_exec_time) / NULLIF(sum(calls), 0))::numeric, 3), 0)::text
  FROM pg_stat_statements WHERE calls > 0
UNION ALL
SELECT 'worst_mean_ms',
       coalesce(round(max(mean_exec_time)::numeric, 3), 0)::text
  FROM pg_stat_statements WHERE calls > 0
UNION ALL
SELECT 'slow_over_200ms',
       (count(*) FILTER (WHERE calls > 0 AND mean_exec_time > 200))::text
  FROM pg_stat_statements
UNION ALL
SELECT 'slow_over_1s',
       (count(*) FILTER (WHERE calls > 0 AND mean_exec_time > 1000))::text
  FROM pg_stat_statements
"""

DSN_RE = re.compile(r"://([^:/@]+):[^@]*@")


def mask(text: str) -> str:
    return DSN_RE.sub(r"://\1:***@", text)


def resolve_psql(explicit: str | None) -> str:
    candidate = explicit or os.environ.get("MRR_PSQL") or "psql"
    found = shutil.which(candidate) or (candidate if Path(candidate).is_file() else None)
    if not found:
        sys.exit(
            "找不到 psql。请用 --psql 指定完整路径，例如\n"
            '  --psql "D:/App/DevEnv/PostgreSQL/bin/psql.exe"'
        )
    return found


def run_psql(psql: str, dsn: str, args: list[str]) -> subprocess.CompletedProcess:
    env = dict(os.environ)
    env.setdefault("PGCLIENTENCODING", "UTF8")
    return subprocess.run(
        [psql, dsn, "-v", "ON_ERROR_STOP=1", "-X", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )


def read_scalars(psql: str, dsn: str, sql: str) -> tuple[dict, str | None]:
    proc = run_psql(psql, dsn, ["-tA", "-F", "|", "-c", sql])
    if proc.returncode != 0:
        return {}, mask((proc.stderr or "").strip())[:400]
    data = {}
    for line in proc.stdout.splitlines():
        line = line.strip()
        if not line or "|" not in line:
            continue
        key, value = line.split("|", 1)
        data[key] = value
    return data, None


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 MRR 数据库性能基线快照")
    parser.add_argument("--dsn", default=os.environ.get("MRR_DB_DSN"), help="PostgreSQL 连接串，默认取 MRR_DB_DSN")
    parser.add_argument("--psql", default=None, help="psql 可执行文件路径")
    parser.add_argument("--label", default="snapshot", help="快照标签，例如 before-index-v3")
    parser.add_argument("--out", default=None, help="输出目录，默认 .workbuddy/db-snapshots/<时间戳>-<label>")
    parser.add_argument("--compare-to", default=None, help="与既有 snapshot.json 对比")
    args = parser.parse_args()

    if not args.dsn:
        sys.exit("缺少连接串，请用 --dsn 或环境变量 MRR_DB_DSN 提供")

    psql = resolve_psql(args.psql)
    stamp = dt.datetime.now().strftime("%Y-%m-%dT%H%M")
    out_dir = Path(args.out) if args.out else DEFAULT_OUT_ROOT / f"{stamp}-{args.label}"
    out_dir.mkdir(parents=True, exist_ok=True)

    scalars, snapshot_error = read_scalars(psql, args.dsn, SNAPSHOT_SQL)
    stmt_scalars, stmt_error = read_scalars(psql, args.dsn, STATEMENTS_SQL)
    if snapshot_error:
        sys.exit(f"基础快照查询失败：{snapshot_error}")
    scalars.update(stmt_scalars)

    files = []
    for sql_file in sorted(DIAG_DIR.glob("*.sql")):
        proc = run_psql(psql, args.dsn, ["-f", str(sql_file)])
        target = out_dir / f"{sql_file.stem}.txt"
        body = proc.stdout if proc.returncode == 0 else proc.stdout + "\n-- 执行失败 --\n" + proc.stderr
        target.write_text(mask(body), encoding="utf-8")
        files.append({"file": target.name, "ok": proc.returncode == 0, "sql": f"monitoring/postgresql/diagnostics/{sql_file.name}"})
        if proc.returncode != 0:
            print(f"[warn] {sql_file.name} 执行失败，详见 {target}")

    snapshot = {
        "label": args.label,
        "captured_at": dt.datetime.now().isoformat(timespec="seconds"),
        "scalars": scalars,
        "statement_stats_available": not stmt_error,
        "statement_stats_error": stmt_error,
        "diagnostics": files,
    }
    (out_dir / "snapshot.json").write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [f"# 数据库性能基线：{args.label}", "", f"- 采集时间：{snapshot['captured_at']}", ""]
    lines.append("| 指标 | 值 |")
    lines.append("| --- | --- |")
    for key, value in scalars.items():
        lines.append(f"| `{key}` | {value} |")
    if stmt_error:
        lines += ["", "> 查询级指标缺失：pg_stat_statements 未预加载、扩展未安装或查询失败。", f"> {stmt_error}"]
    if args.compare_to:
        try:
            prev = json.loads(Path(args.compare_to).read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001 - 对比失败不应阻断本次采集
            lines += ["", f"> 对比基线读取失败：{exc}"]
        else:
            lines += ["", f"## 与 {prev.get('label', 'baseline')} 对比", "", "| 指标 | 之前 | 现在 | 变化 |", "| --- | --- | --- | --- |"]
            for key, value in scalars.items():
                before = prev.get("scalars", {}).get(key, "-")
                try:
                    delta = f"{float(value) - float(before):+.4g}"
                except (TypeError, ValueError):
                    delta = "-"
                lines.append(f"| `{key}` | {before} | {value} | {delta} |")
    lines += ["", "## 诊断输出", ""]
    for item in files:
        state = "ok" if item["ok"] else "失败"
        lines.append(f"- `{item['file']}`（{state}）来源 `{item['sql']}`")
    (out_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(f"快照已写入 {out_dir}")
    print(f"摘要：{out_dir / 'summary.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
