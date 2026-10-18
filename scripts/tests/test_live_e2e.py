import unittest
from unittest.mock import patch

from scripts.run_live_e2e import isolated_environment, stop_process


class LiveE2eTest(unittest.TestCase):
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
