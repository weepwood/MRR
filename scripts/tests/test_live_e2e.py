import unittest
from unittest.mock import patch

from scripts.run_live_e2e import cleanup_resources, docker_command, isolated_environment, stop_process


class LiveE2eTest(unittest.TestCase):
    def test_docker_timeout_does_not_expose_command_or_credentials(self):
        import subprocess
        with patch("subprocess.run", side_effect=subprocess.TimeoutExpired(["docker", "secret-marker"], 1)):
            with self.assertRaises(RuntimeError) as raised:
                docker_command(["run"], {"POSTGRES_PASSWORD": "secret-marker"})
        self.assertNotIn("secret-marker", str(raised.exception))
        self.assertTrue(raised.exception.__suppress_context__)

    def test_cleanup_attempts_every_resource_and_propagates_failure(self):
        from unittest.mock import Mock
        first = Mock(side_effect=OSError("unsafe underlying details"))
        second = Mock()
        with self.assertRaisesRegex(RuntimeError, "cleanup failed"):
            cleanup_resources([first, second])
        second.assert_called_once()

    def test_docker_nonzero_exit_is_a_failure(self):
        from unittest.mock import Mock
        with patch("subprocess.run", return_value=Mock(returncode=1)):
            with self.assertRaises(RuntimeError):
                docker_command(["rm", "--force", "owned-name"], {})

    def test_posix_process_group_escalates_when_termination_times_out(self):
        import signal
        import subprocess
        from unittest.mock import Mock
        process = Mock(pid=12345)
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("owned", 15), 0]
        with patch("os.name", "posix"), patch("signal.SIGKILL", 9, create=True), patch("os.killpg", create=True) as kill:
            stop_process(process)
        self.assertEqual(kill.call_args_list[0].args, (12345, signal.SIGTERM))
        self.assertEqual(kill.call_args_list[1].args, (12345, 9))

    def test_production_environment_cannot_reach_test_backend(self):
        with patch.dict("os.environ", {"PATH": "tools", "SPRING_DATASOURCE_URL": "production",
                                       "SPRING_CONFIG_IMPORT": "production", "JWT_SECRET_KEY": "production",
                                       "OSS_ACCESS_KEY_ID": "production"}, clear=True):
            env = isolated_environment()
        self.assertEqual(env, {"PATH": "tools"})

    def test_failed_run_terminates_owned_process(self):
        from unittest.mock import Mock
        process = Mock()
        process.poll.return_value = None
        stop_process(process)
        process.terminate.assert_called_once()
        process.wait.assert_called_once_with(timeout=15)

    def test_already_finished_process_is_not_signalled(self):
        from unittest.mock import Mock
        process = Mock()
        process.poll.return_value = 0
        stop_process(process)
        process.terminate.assert_not_called()
