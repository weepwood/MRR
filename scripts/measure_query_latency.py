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
import uuid


def percentile(values: list[float], percent: float) -> float:
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent) - 1)]


def response_status(status: int, body: object, expected_count: int) -> str:
    if status != 200:
        return str(status)
    if not isinstance(body, dict) or body.get('code') != 200 or not isinstance(body.get('data'), list):
        return 'INVALID_RESULT'
    if len(body['data']) != expected_count:
        return 'COUNT_MISMATCH'
    return '200'


def frequencies(statuses: list[str]) -> dict[str, int]:
    return {status: statuses.count(status) for status in sorted(set(statuses))}


def build_report(label: str, case_id: str, samples: int, concurrency: int, expected_count: int,
                 rows: list[tuple[float, str]], warmup: list[str], elapsed_seconds: float) -> dict:
    values = [row[0] for row in rows]
    statuses = frequencies([row[1] for row in rows])
    warmup_statuses = frequencies(warmup)
    successes = statuses.get('200', 0)
    return {'schemaVersion': 2, 'measuredAt': datetime.now(timezone.utc).isoformat(), 'label': label,
            'caseId': case_id, 'expectedCount': expected_count,
            'samples': samples, 'concurrency': concurrency, 'statuses': statuses,
            'warmup': 5, 'warmupStatuses': warmup_statuses,
            'elapsedSeconds': elapsed_seconds,
            'successThroughputPerSecond': successes / max(elapsed_seconds, 1e-9),
            'failureRate': (samples - successes) / samples,
            'p50Ms': percentile(values, .50), 'p95Ms': percentile(values, .95),
            'p99Ms': percentile(values, .99),
            'valid': statuses == {'200': samples} and warmup_statuses == {'200': 5},
            'scope': 'HTTP metadata query, warm cache; no response content, URL, token or patient value saved'}


def compare_baseline(baseline: object, report: dict) -> dict:
    def valid_metrics(candidate: dict) -> bool:
        p95 = candidate.get('p95Ms')
        return (type(p95) in (int, float) and math.isfinite(p95) and p95 > 0
                and candidate.get('valid') is True
                and candidate.get('statuses') == {'200': candidate.get('samples')}
                and candidate.get('warmupStatuses') == {'200': 5})
    comparable = (isinstance(baseline, dict) and baseline.get('label') == 'logs-off'
                  and report.get('label') == 'logs-on' and valid_metrics(baseline) and valid_metrics(report)
                  and baseline.get('schemaVersion') == report.get('schemaVersion') == 2
                  and all(baseline.get(key) == report.get(key)
                          for key in ('caseId', 'expectedCount', 'samples', 'concurrency', 'warmup')))
    if not comparable:
        return {'comparable': False}
    ratio = report['p95Ms'] / baseline['p95Ms']
    return {'comparable': True, 'p95OverheadRatio': ratio - 1, 'withinTenPercentTarget': ratio <= 1.10}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--replica', action='store_true', help='Confirm this is an authorized test replica')
    parser.add_argument('--label', required=True, choices=['logs-off', 'logs-on'])
    parser.add_argument('--case-id', required=True, help='Random UUIDv4 for the acceptance case, never a patient identifier')
    parser.add_argument('--expected-count', required=True, type=int, help='Known positive synthetic fixture result count')
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
    try:
        case_id = uuid.UUID(args.case_id)
    except ValueError:
        parser.error('Use a random UUIDv4 acceptance case identifier.')
    if case_id.version != 4 or not 1 <= args.expected_count <= 100000:
        parser.error('Use a UUIDv4 and expected count 1..100000.')
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
                    status = response_status(response.status, body, args.expected_count)
        except urllib.error.HTTPError as error:
            status = str(error.code)
            error.close()
        except Exception:
            status = 'FAILED'
        return (time.perf_counter() - started) * 1000, status
    # Warmup failures are evidence too, not silently discarded.
    warmup = [sample(n)[1] for n in range(5)]
    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        rows = list(pool.map(sample, range(args.samples)))
    elapsed = time.perf_counter() - started
    report = build_report(args.label, str(case_id), args.samples, args.concurrency,
                          args.expected_count, rows, warmup, elapsed)
    if args.baseline:
        try:
            baseline = json.loads(args.baseline.read_text(encoding='utf-8'))
        except (OSError, ValueError):
            baseline = None
        report.update(compare_baseline(baseline, report))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Latency report saved; failures invalidate performance acceptance.')
    accepted = report['valid'] and (not args.baseline or report.get('withinTenPercentTarget') is True)
    return 0 if accepted else 1


if __name__ == '__main__':
    raise SystemExit(main())
