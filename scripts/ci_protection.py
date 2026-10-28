"""Migrate only required status checks after the new gate actually succeeds on main."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess

LEGACY = {'governance', 'release-baseline-gate', 'frontend-gate', 'backend-gate',
          'windows-management-gate', 'windows-package-gate', 'live-e2e', 'windows-database-recovery'}
TARGET = {'governance', 'pr-gate'}


def status_payload(current, target):
    for policy in (current, target):
        checks = policy['checks']
        names = {c['context'] for c in checks}
        if names not in (LEGACY, TARGET) or len(names) != len(checks):
            raise ValueError('Unexpected required checks; inspect concurrent policy changes first.')
        if policy['strict'] is not True or any(c['app_id'] != 15368 for c in checks):
            raise ValueError('Strict checks bound to the GitHub Actions App are required.')
    return {'strict': True, 'checks': [{k: c[k] for k in ('context', 'app_id')} for c in target['checks']]}


def require_live_gate(checks):
    if not any(c['name'] == 'pr-gate' and c.get('conclusion') == 'success'
               and c.get('status') == 'completed' and c.get('app', {}).get('id') == 15368 for c in checks):
        raise ValueError('pr-gate must actually succeed on the current main SHA before migration.')


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True, encoding='utf-8'))


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('--repo', default='weepwood/MRR')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--apply', action='store_true')
    modes.add_argument('--restore', type=Path)
    parser.add_argument('--snapshot', type=Path, help='New snapshot file outside tracked source; required for --apply.')
    args = parser.parse_args()
    if args.apply and args.snapshot is None:
        parser.error('--apply requires --snapshot; existing snapshots are never overwritten')
    endpoint = f'repos/{args.repo}/branches/main/protection'
    current = api(endpoint)
    target = json.loads((args.restore or Path('quality/main-branch-protection.json')).read_text(encoding='utf-8'))
    payload = status_payload(current['required_status_checks'], target['required_status_checks'])
    if not args.restore:
        sha = api(f'repos/{args.repo}/commits/main')['sha']
        checks = api(f'repos/{args.repo}/commits/{sha}/check-runs?per_page=100')['check_runs']
        require_live_gate(checks)
    print(json.dumps(payload, indent=2))
    if args.apply:
        with args.snapshot.open('x', encoding='utf-8') as snapshot:
            json.dump(current, snapshot, indent=2)
    if args.apply or args.restore:
        # Updating this subresource preserves reviews, admin enforcement and all other protection.
        subprocess.run(['gh', 'api', endpoint + '/required_status_checks', '--method', 'PATCH', '--input', '-'],
                       input=json.dumps(payload), text=True, encoding='utf-8', check=True, stdout=subprocess.DEVNULL)
        actual = api(endpoint)['required_status_checks']
        if status_payload(actual, actual) != payload:
            raise ValueError('Remote policy read-back differs from the requested required checks.')
        print('Remote required checks applied and read back successfully.')


if __name__ == '__main__':
    main()
