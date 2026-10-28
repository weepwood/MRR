import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.release_candidate import create_manifest, verify_manifest, validate_identity, validate_existing_assets, preflight, publish, file_identity

SHA = 'a' * 40


class ReleaseCandidateTest(unittest.TestCase):
    def candidate(self, root):
        files = []
        for name in ('MRR-v1.2.3.zip', 'MRR-v1.2.3-standalone.jar', 'MRR-v1.2.3-standalone.jar.sha256'):
            file = root / name
            file.write_bytes(b'synthetic release file')
            files.append(file)
        return create_manifest(root, '1.2.3', SHA, '123', '1', files)

    def test_preflight_rejects_other_checkout_and_failed_main_before_publication(self):
        with patch('subprocess.check_output', return_value='b' * 40):
            with self.assertRaises(ValueError):
                preflight('1.2.3', SHA, 'refs/heads/main', 'example/repo', False)
        with patch('subprocess.check_output', return_value=SHA), patch('pathlib.Path.is_file', return_value=True), \
                patch('scripts.release_candidate.tag_commit', return_value=None), \
                patch('scripts.release_candidate.gh_json', return_value={'workflow_runs': [
                    {'head_sha': SHA, 'conclusion': 'failure'}, {'head_sha': 'b' * 40, 'conclusion': 'success'},
                ]}):
            with self.assertRaises(ValueError):
                preflight('1.2.3', SHA, 'refs/heads/main', 'example/repo', False)

    def test_corrupted_candidate_has_no_github_side_effects(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.candidate(root)
            (root / 'MRR-v1.2.3.zip').write_bytes(b'corrupted')
            with patch('scripts.release_candidate.gh_json') as api:
                with self.assertRaises(ValueError):
                    publish(root, SHA, '123', '1', 'refs/heads/main', 'example/repo')
                api.assert_not_called()

    def test_partial_draft_can_resume_same_verified_files_without_clobber(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            candidate = self.candidate(root)
            expected = candidate['files'] | {'release-candidate.json': file_identity(root / 'release-candidate.json')}
            assets = [{'name': name, 'digest': 'sha256:' + value['sha256']} for name, value in expected.items()]
            existing = dict(id=7, tag_name='v1.2.3', draft=True, assets=assets[:1])
            complete = existing | {'assets': assets}
            with patch('scripts.release_candidate.tag_commit', return_value=SHA), \
                    patch('subprocess.check_output', return_value=json.dumps([[existing]])), \
                    patch('subprocess.run') as upload, \
                    patch('scripts.release_candidate.gh_json', side_effect=[complete, complete]) as api:
                publish(root, SHA, '123', '1', 'refs/heads/main', 'example/repo')
                command = upload.call_args.args[0]
                self.assertNotIn('--clobber', command)
                self.assertNotIn(str(root / assets[0]['name']), command)
                self.assertEqual({'draft': False}, api.call_args.kwargs['payload'])
                self.assertEqual('PATCH', api.call_args.kwargs['method'])

    def test_failed_remote_asset_verification_never_makes_draft_public(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.candidate(root)
            existing = dict(id=7, tag_name='v1.2.3', draft=True, assets=[])
            bad = existing | {'assets': [{'name': 'MRR-v1.2.3.zip', 'digest': 'sha256:' + '0' * 64}]}
            with patch('scripts.release_candidate.tag_commit', return_value=SHA), \
                    patch('subprocess.check_output', return_value=json.dumps([[existing]])), \
                    patch('subprocess.run'), patch('scripts.release_candidate.gh_json', return_value=bad) as api:
                with self.assertRaises(ValueError):
                    publish(root, SHA, '123', '1', 'refs/heads/main', 'example/repo')
                self.assertEqual(1, api.call_count)
                self.assertEqual({}, api.call_args.kwargs)

    def test_real_release_requires_main_and_full_sha(self):
        validate_identity('1.2.3', SHA, 'refs/heads/main', dry_run=False)
        for sha, ref in ((SHA, 'refs/heads/feature'), ('abc123', 'refs/heads/main')):
            with self.assertRaises(ValueError):
                validate_identity('1.2.3', sha, ref, dry_run=False)

    def test_existing_tag_must_never_move(self):
        validate_identity('1.2.3', SHA, 'refs/heads/main', dry_run=False, tag_sha=SHA)
        with self.assertRaises(ValueError):
            validate_identity('1.2.3', SHA, 'refs/heads/main', dry_run=False, tag_sha='b' * 40)

    def test_dry_run_can_validate_a_review_branch_without_creating_release(self):
        validate_identity('1.2.3', SHA, 'refs/heads/feature', dry_run=True)

    def test_candidate_is_bound_to_files_commit_run_and_attempt(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            files = []
            for name in ('MRR-v1.2.3.zip', 'MRR-v1.2.3-standalone.jar', 'MRR-v1.2.3-standalone.jar.sha256'):
                file = root / name
                file.write_bytes(b'synthetic release file')
                files.append(file)
            create_manifest(root, '1.2.3', SHA, '123', '1', files)
            verify_manifest(root, SHA, '123', '1')
            for identity in (('b' * 40, '123', '1'), (SHA, '456', '1'), (SHA, '123', '2')):
                with self.assertRaises(ValueError):
                    verify_manifest(root, *identity)
            files[0].write_bytes(b'corrupted')
            with self.assertRaises(ValueError):
                verify_manifest(root, SHA, '123', '1')
            files[0].unlink()
            with self.assertRaises(ValueError):
                verify_manifest(root, SHA, '123', '1')

    def test_windows_can_validate_only_jar_but_publishing_still_requires_the_complete_candidate(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.candidate(root)
            (root / 'MRR-v1.2.3.zip').unlink()
            (root / 'MRR-v1.2.3-standalone.jar.sha256').unlink()
            verify_manifest(root, SHA, '123', '1', jar_only=True)
            with self.assertRaises(ValueError):
                verify_manifest(root, SHA, '123', '1')
            (root / 'MRR-v1.2.3-standalone.jar').write_bytes(b'wrong jar')
            with self.assertRaises(ValueError):
                verify_manifest(root, SHA, '123', '1', jar_only=True)
    def test_manifest_rejects_missing_files_empty_files_and_path_escape(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            with self.assertRaises(ValueError):
                create_manifest(root, '1.2.3', SHA, '123', '1', [])
            (root / 'release-candidate.json').write_text(json.dumps({
                'version': '1.2.3', 'source_sha': SHA, 'run_id': '123', 'run_attempt': '1',
                'files': {'../outside': {'sha256': SHA, 'size': 1}},
            }), encoding='utf-8')
            with self.assertRaises(ValueError):
                verify_manifest(root, SHA, '123', '1')

    def test_upload_retry_accepts_only_existing_assets_with_identical_digest(self):
        expected = {'a.zip': {'sha256': 'a' * 64, 'size': 10}}
        validate_existing_assets(expected, [{'name': 'a.zip', 'digest': 'sha256:' + 'a' * 64}])
        for asset in ({'name': 'a.zip', 'digest': 'sha256:' + 'b' * 64},
                      {'name': 'a.zip', 'digest': None}, {'name': 'unexpected.zip', 'digest': 'sha256:' + 'a' * 64}):
            with self.assertRaises(ValueError):
                validate_existing_assets(expected, [asset])


if __name__ == '__main__':
    unittest.main()
