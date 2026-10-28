"""Bind verified release files to one commit/run; publish without rebuilding or clobbering."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

MANIFEST = 'release-candidate.json'
VERSION_RE = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?')


def validate_identity(version, sha, ref, *, dry_run, tag_sha=None):
    if not VERSION_RE.fullmatch(version) or not re.fullmatch('[0-9a-f]{40}', sha):
        raise ValueError('A valid version and complete source SHA are required.')
    if not dry_run and ref != 'refs/heads/main':
        raise ValueError('Production release must be dispatched from main.')
    if tag_sha is not None and tag_sha != sha:
        raise ValueError('Existing version tag points to a different commit; never move it.')


def expected_names(version):
    return {f'MRR-v{version}.zip', f'MRR-v{version}-standalone.jar',
            f'MRR-v{version}-standalone.jar.sha256'}


def file_identity(path):
    if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
        raise ValueError('Release file is missing, empty or a symbolic link.')
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return {'sha256': digest.hexdigest(), 'size': path.stat().st_size}


def create_manifest(root, version, sha, run_id, attempt, files):
    validate_identity(version, sha, '', dry_run=True)
    if not str(run_id).isdigit() or not str(attempt).isdigit():
        raise ValueError('Numeric run identity is required.')
    if {p.name for p in files} != expected_names(version) or len(files) != 3:
        raise ValueError('Candidate requires exactly the ZIP, standalone JAR and checksum.')
    if any(p.resolve().parent != root.resolve() for p in files):
        raise ValueError('Release file is outside the candidate directory.')
    manifest = dict(version=version, source_sha=sha, run_id=str(run_id), run_attempt=str(attempt),
                    files={p.name: file_identity(p) for p in files})
    (root / MANIFEST).write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return manifest


def verify_manifest(root, sha, run_id, attempt, *, jar_only=False):
    manifest = json.loads((root / MANIFEST).read_text(encoding='utf-8'))
    if (manifest.get('source_sha'), manifest.get('run_id'), manifest.get('run_attempt')) != (sha, str(run_id), str(attempt)):
        raise ValueError('Candidate belongs to a different commit, run or build attempt.')
    version = manifest.get('version', '')
    validate_identity(version, sha, '', dry_run=True)
    if set(manifest.get('files', {})) != expected_names(version):
        raise ValueError('Candidate file set does not match the release version.')
    for name, identity in manifest['files'].items():
        if jar_only and not name.endswith('.jar'):
            continue
        if file_identity(root / name) != identity:
            raise ValueError(f'Release checksum or size mismatch: {name}.')
    return manifest


def validate_existing_assets(expected, assets):
    for asset in assets:
        name = asset['name']
        if name not in expected or asset.get('digest') != 'sha256:' + expected[name]['sha256']:
            raise ValueError('Existing release assets differ or lack a verifiable digest; no overwrite allowed.')


def gh_json(endpoint, *, method='GET', payload=None):
    args = ['gh', 'api', endpoint, '--method', method]
    if payload is not None:
        args.extend(['--input', '-'])
    response = subprocess.run(args, input=json.dumps(payload) if payload is not None else None,
                              text=True, encoding='utf-8', capture_output=True, check=True)
    return json.loads(response.stdout) if response.stdout.strip() else None


def tag_commit(repo, version):
    tag = f'refs/tags/v{version}'
    matches = gh_json(f'repos/{repo}/git/matching-refs/tags/v{version}')
    obj = next((ref['object'] for ref in matches if ref['ref'] == tag), None)
    for _ in range(5):
        if obj is None or obj['type'] == 'commit':
            return obj['sha'] if obj else None
        if obj['type'] != 'tag':
            break
        obj = gh_json(f'repos/{repo}/git/tags/{obj["sha"]}')['object']
    raise ValueError('Could not resolve the existing release tag to a commit.')


def preflight(version, sha, ref, repo, dry_run):
    validate_identity(version, sha, ref, dry_run=dry_run)
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if actual != sha:
        raise ValueError('Checked out source does not match the frozen release SHA.')
    if not Path(f'release-notes/{version}.md').is_file():
        raise ValueError('Versioned release notes are required.')
    if not dry_run:
        validate_identity(version, sha, ref, dry_run=False, tag_sha=tag_commit(repo, version))
        runs = gh_json(f'repos/{repo}/actions/workflows/quality-gate.yml/runs?branch=main&event=push&head_sha={sha}&per_page=100')
        if not any(r['head_sha'] == sha and r['conclusion'] == 'success' for r in runs['workflow_runs']):
            raise ValueError('The main integration workflow must first succeed for this exact SHA.')


def publish(root, sha, run_id, attempt, ref, repo):
    candidate = verify_manifest(root, sha, run_id, attempt)
    version = candidate['version']
    current_tag = tag_commit(repo, version)
    validate_identity(version, sha, ref, dry_run=False, tag_sha=current_tag)
    expected = candidate['files'] | {MANIFEST: file_identity(root / MANIFEST)}
    # Listing avoids treating an authorization/network failure as "not found".
    releases = subprocess.check_output(['gh', 'api', f'repos/{repo}/releases?per_page=100',
                                        '--paginate', '--slurp'], text=True, encoding='utf-8')
    release = next((r for page in json.loads(releases) for r in page if r['tag_name'] == f'v{version}'), None)
    if release:
        validate_existing_assets(expected, release['assets'])
        if not release['draft'] and {a['name'] for a in release['assets']} != set(expected):
            raise ValueError('An already public release has an incomplete asset set; refuse to modify it.')
    if current_tag is None:
        gh_json(f'repos/{repo}/git/refs', method='POST', payload={'ref': f'refs/tags/v{version}', 'sha': sha})
    if release is None:
        notes = Path(f'release-notes/{version}.md').read_text(encoding='utf-8')
        release = gh_json(f'repos/{repo}/releases', method='POST', payload={
            'tag_name': f'v{version}', 'target_commitish': sha, 'name': f'MRR v{version}',
            'draft': True, 'body': notes + f'\n\nSource: `{sha}`\nCandidate run: `{run_id}`, build attempt: `{attempt}`.\n',
        })
    existing = {a['name'] for a in release['assets']}
    missing = [str(root / name) for name in sorted(expected) if name not in existing]
    if missing:
        subprocess.run(['gh', 'release', 'upload', f'v{version}', *missing, '--repo', repo], check=True)
    release = gh_json(f'repos/{repo}/releases/{release["id"]}')
    validate_existing_assets(expected, release['assets'])
    if {a['name'] for a in release['assets']} != set(expected):
        raise ValueError('Release assets are incomplete; leave the release as a draft.')
    if release['draft']:
        gh_json(f'repos/{repo}/releases/{release["id"]}', method='PATCH', payload={'draft': False})
    print(f'Published MRR v{version} from verified commit {sha}.')


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('command', choices=('preflight', 'manifest', 'verify', 'publish'))
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--version', default=Path('VERSION').read_text(encoding='utf-8').strip())
    parser.add_argument('--sha', default=os.environ.get('GITHUB_SHA', ''))
    parser.add_argument('--run-id', default=os.environ.get('GITHUB_RUN_ID', ''))
    parser.add_argument('--attempt', default=os.environ.get('GITHUB_RUN_ATTEMPT', ''))
    parser.add_argument('--ref', default=os.environ.get('GITHUB_REF', ''))
    parser.add_argument('--repo', default=os.environ.get('GITHUB_REPOSITORY', ''))
    parser.add_argument('--dry-run', choices=('true', 'false'), default='true')
    parser.add_argument('--jar-only', action='store_true', help='Verify only the candidate JAR in the Windows dry-run handoff.')
    args = parser.parse_args()
    if args.jar_only and args.command != 'verify':
        parser.error('--jar-only is allowed only for Windows candidate verification, never publication')
    if args.command == 'preflight':
        preflight(args.version, args.sha, args.ref, args.repo, args.dry_run == 'true')
    elif args.command == 'manifest':
        create_manifest(args.directory, args.version, args.sha, args.run_id, args.attempt,
                        [args.directory / name for name in sorted(expected_names(args.version))])
    elif args.command == 'verify':
        verify_manifest(args.directory, args.sha, args.run_id, args.attempt, jar_only=args.jar_only)
    else:
        publish(args.directory, args.sha, args.run_id, args.attempt, args.ref, args.repo)


if __name__ == '__main__':
    main()
