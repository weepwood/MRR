import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread
from unittest.mock import patch

from scripts.run_live_e2e import (
    DOCUMENTATION_PATHS,
    assert_documentation_requires_session,
    cleanup_resources,
    docker_command,
    isolated_environment,
    stop_process,
    run,
)


def serve_documentation(status: int, body: bytes, seen: list[str]):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            seen.append(self.path)
            self.send_response(status)
            self.send_header("Content-Type", "application/json;charset=UTF-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    Thread(target=server.serve_forever, daemon=True).start()
    return server, f"http://127.0.0.1:{server.server_port}"


class DocumentationSessionLiveE2eTest(unittest.TestCase):
    def test_every_documentation_path_is_checked(self):
        seen: list[str] = []
        server, base_url = serve_documentation(
            401, b'{"code":401,"message":"Documentation session required"}', seen
        )
        try:
            assert_documentation_requires_session(base_url)
        finally:
            server.shutdown()
            server.server_close()
        self.assertEqual(seen, list(DOCUMENTATION_PATHS))
        self.assertIn("/v3/api-docs", seen)
        self.assertIn("/swagger-ui/index.html", seen)

    def test_publicly_reachable_documentation_fails_the_gate(self):
        seen: list[str] = []
        server, base_url = serve_documentation(200, b'{"openapi":"3.1.1"}', seen)
        try:
            with self.assertRaisesRegex(RuntimeError, "publicly reachable"):
                assert_documentation_requires_session(base_url)
        finally:
            server.shutdown()
            server.server_close()

    def test_unexpected_status_fails_the_gate(self):
        seen: list[str] = []
        server, base_url = serve_documentation(403, b'{"code":403}', seen)
        try:
            with self.assertRaisesRegex(RuntimeError, "instead of 401"):
                assert_documentation_requires_session(base_url)
        finally:
            server.shutdown()
            server.server_close()

    def test_401_without_documentation_session_body_fails_the_gate(self):
        seen: list[str] = []
        server, base_url = serve_documentation(401, b"", seen)
        try:
            with self.assertRaisesRegex(RuntimeError, "error body"):
                assert_documentation_requires_session(base_url)
        finally:
            server.shutdown()
            server.server_close()


class LiveE2eTest(unittest.TestCase):
    def test_embedded_mode_rejects_jar_without_production_frontend_before_starting_database(self):
        from pathlib import Path
        import tempfile
        from zipfile import ZipFile
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / 'candidate.jar'
            with ZipFile(jar, 'w') as archive:
                archive.writestr('synthetic', 'not a production frontend')
            with patch('scripts.run_live_e2e.docker_command') as docker:
                with self.assertRaises(ValueError):
                    run(jar, embedded=True)
                docker.assert_not_called()

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
