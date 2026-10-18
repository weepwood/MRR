"""Measure an authorized replica query without saving patient values or response bodies."""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request


def percentile(values: list[float], percent: float) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent) - 1)]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--replica', action='store_true', help='Confirm this is an authorized test replica')
    parser.add_argument('--label', required=True, choices=['logs-off', 'logs-on'])
    parser.add_argument('--samples', type=int, default=100)
    parser.add_argument('--concurrency', type=int, default=1)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--baseline', type=Path)
    args = parser.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    if not args.replica or parsed.scheme not in ('http', 'https') or parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error('Use an authorized replica URL without credentials, query or fragment.')
    if not 1 <= args.samples <= 10000 or not 1 <= args.concurrency <= 8:
        parser.error('Samples must be 1..10000 and concurrency 1..8.')
    token = os.environ.get('MRR_BENCH_TOKEN', '')
    conditions = {key: os.environ.get('MRR_BENCH_' + key.upper(), '').strip() for key in ('bah', 'sjh')}
    if not token or not any(conditions.values()):
        parser.error('Set MRR_BENCH_TOKEN and synthetic MRR_BENCH_BAH / MRR_BENCH_SJH in this process.')
    url = args.base_url.rstrip('/') + '/api/v1/img/search?' + urllib.parse.urlencode(conditions)
    # Redirects must not forward credentials to another origin.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    def sample(_: int) -> tuple[float, str]:
        request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/json'})
        started = time.perf_counter()
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=10) as response:
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    status = 'OVERSIZED'
                else:
                    body = json.loads(raw)
                    status = str(response.status) if body.get('code') == 200 and isinstance(body.get('data'), list) else 'INVALID_RESULT'
        except urllib.error.HTTPError as error:
            status = str(error.code)
            error.close()
        except Exception:
            status = 'FAILED'
        return (time.perf_counter() - started) * 1000, status
    # Warm cache separately; samples and failures remain inspectable in the report.
    for n in range(5):
        sample(n)
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        rows = list(pool.map(sample, range(args.samples)))
    values = [row[0] for row in rows]
    statuses = {status: sum(row[1] == status for row in rows) for status in sorted({row[1] for row in rows})}
    report = {'measuredAt': datetime.now(timezone.utc).isoformat(), 'label': args.label,
              'samples': args.samples, 'concurrency': args.concurrency, 'statuses': statuses,
              'warmup': 5, 'p50Ms': percentile(values, .50), 'p95Ms': percentile(values, .95),
              'p99Ms': percentile(values, .99), 'valid': statuses == {'200': args.samples},
              'scope': 'HTTP metadata query, warm cache; no response content, URL, token or patient value saved'}
    if args.baseline:
        baseline = json.loads(args.baseline.read_text(encoding='utf-8'))
        comparable = baseline.get('label') == 'logs-off' and args.label == 'logs-on' and baseline.get('valid') and report['valid'] and baseline.get('samples') == args.samples and baseline.get('concurrency') == args.concurrency
        report['comparable'] = bool(comparable)
        if comparable:
            report['p95OverheadRatio'] = report['p95Ms'] / baseline['p95Ms'] - 1
            report['withinTenPercentTarget'] = report['p95OverheadRatio'] <= .10
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Latency report saved; failures invalidate performance acceptance.')
    return 0 if report['valid'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
