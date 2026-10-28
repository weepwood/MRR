from pathlib import Path
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from scripts.dev import stop_owned_process, stop_database, save_json, load_json, process_stamp, loopback_port
from scripts.dev import lock, private_directory
from scripts.dev import verify_local_config_excluded
from scripts.dev import run_build
from scripts.dev import down, OWNER
from zipfile import ZipFile
from scripts.run_live_e2e import isolated_environment


class DevLifecycleTest(unittest.TestCase):
    def test_cleanup_failure_still_attempts_database_and_preserves_state(self):
        with tempfile.TemporaryDirectory() as folder, patch('scripts.dev.DIRECTORY', Path(folder)), \
             patch('scripts.dev.stop_owned_process', side_effect=RuntimeError('synthetic')), \
             patch('scripts.dev.stop_database') as database:
            state = Path(folder) / 'state.json'
            save_json(state, {'owner': OWNER, 'processes': [{'pid': 123, 'stamp': 'synthetic'}]})
            with self.assertRaises(RuntimeError):
                down()
            database.assert_called_once()
            self.assertTrue(state.exists())

    def test_build_interruption_stops_its_process_tree(self):
        with patch('scripts.dev.subprocess.Popen') as start, patch('scripts.dev.stop_process') as stop:
            start.return_value.wait.side_effect = KeyboardInterrupt()
            with self.assertRaises(KeyboardInterrupt):
                run_build(['synthetic'], Path('.'), {}, None)
            stop.assert_called_once_with(start.return_value)

    def test_reused_jar_with_local_configuration_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / 'app.jar'
            with ZipFile(jar, 'w') as output:
                output.writestr('BOOT-INF/classes/application-local.properties', 'synthetic=true')
            with self.assertRaisesRegex(RuntimeError, 'Local configuration'):
                verify_local_config_excluded(jar)

    @unittest.skipUnless(os.name == 'nt', 'NTFS DACL requires Windows')
    def test_first_use_protects_new_directory(self):
        with tempfile.TemporaryDirectory() as folder, patch('scripts.dev.DIRECTORY', Path(folder) / 'new'):
            with lock():
                pass

    def test_lock_is_exclusive_and_reusable_after_release(self):
        with tempfile.TemporaryDirectory() as folder, patch('scripts.dev.DIRECTORY', Path(folder)), \
             patch('scripts.dev.private_directory'):
            with lock():
                with self.assertRaisesRegex(RuntimeError, 'Another'):
                    with lock():
                        self.fail('Concurrent lifecycle operation entered')
            with lock():
                pass

    @unittest.skipUnless(os.name == 'nt', 'NTFS DACL requires Windows')
    def test_existing_broad_permissions_are_removed(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / 'private'
            root.mkdir()
            file = root / 'synthetic.json'
            file.write_text('{}')
            subprocess.run(['icacls', str(file), '/grant', '*S-1-1-0:R'], check=True, capture_output=True)
            with patch('scripts.dev.DIRECTORY', root):
                private_directory()
            result = subprocess.check_output(['powershell.exe', '-NoProfile', '-Command',
                '$ErrorActionPreference="Stop"; $a=[IO.File]::GetAccessControl($env:MRR_ACL_TEST); $a.Access | ForEach-Object { $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value }'],
                env=isolated_environment() | {'MRR_ACL_TEST': str(file)}, text=True)
            self.assertNotIn('S-1-1-0', result)
            self.assertEqual(2, len(result.strip().splitlines()))

    def test_reused_pid_is_never_stopped(self):
        with patch('scripts.dev.process_stamp', return_value='new'), patch('scripts.dev.subprocess.run') as run:
            stop_owned_process({'pid': 123, 'stamp': 'old'})
            run.assert_not_called()

    def test_foreign_database_is_never_removed(self):
        with patch('scripts.dev.database_labels', return_value={'mrr.dev.owner': 'other'}), \
             patch('scripts.dev.docker') as command:
            with self.assertRaisesRegex(RuntimeError, 'ownership'):
                stop_database('mrr-dev-test', 'mine')
            command.assert_not_called()

    def test_missing_database_cleanup_is_idempotent(self):
        with patch('scripts.dev.database_labels', return_value=None), patch('scripts.dev.docker') as command:
            stop_database('mrr-dev-test', 'mine')
            command.assert_not_called()

    def test_state_roundtrip_and_corruption_fail_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / 'state.json'
            save_json(file, {'pid': 123})
            self.assertEqual({'pid': 123}, load_json(file))
            file.write_text('{broken', encoding='utf-8')
            with self.assertRaisesRegex(RuntimeError, 'state'):
                load_json(file)

    def test_process_identity_and_port_boundaries(self):
        self.assertIsNotNone(process_stamp(os.getpid()))
        self.assertTrue(1024 <= loopback_port(0) <= 65535)
        for port in (-1, 80, 65536):
            with self.assertRaises(ValueError):
                loopback_port(port)
