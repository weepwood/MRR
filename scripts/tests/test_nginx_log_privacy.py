import json
import re
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / 'deploy/windows/templates/nginx.conf'


class NginxLogPrivacyTest(unittest.TestCase):
    def setUp(self):
        self.config = CONFIG.read_text(encoding='utf-8')

    def fragments(self):
        mapping = re.search(r'map \$uri \$mrr_log_route \{[^}]+\}', self.config)
        self.assertIsNotNone(mapping, '入口日志必须有独立的安全路由分类')
        formats = []
        for name in ('main', 'audit_json'):
            match = re.search(r'log_format ' + name + r'\b[\s\S]*?;', self.config)
            self.assertIsNotNone(match)
            formats.append(match.group())
        return mapping.group(), formats

    def test_log_formats_never_write_raw_request_path(self):
        mapping, formats = self.fragments()
        self.assertIn('default "[route omitted]";', mapping)
        # 分类结果不得插值或使用正则捕获值。
        for line in mapping.splitlines()[1:-1]:
            self.assertNotIn('$', line)
        for fmt in formats:
            self.assertIn('$mrr_log_route', fmt)
            self.assertNotRegex(fmt, r'\$(?:uri|request_uri|request|args|query_string|http_referer)\b')

    @unittest.skipUnless(shutil.which('nginx'), '需要本机 Nginx 运行时')
    def test_normalized_and_unknown_paths_do_not_leak(self):
        mapping, formats = self.fragments()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with socket.socket() as sock:
                sock.bind(('127.0.0.1', 0))
                port = sock.getsockname()[1]
            config = root / 'nginx.conf'
            config.write_text(
                f'worker_processes 1; pid {root}/nginx.pid; '
                f'error_log {root}/error.log warn; events {{ worker_connections 64; }} '
                f'http {{ {mapping} {" ".join(formats)} '
                f'access_log {root}/access.log main; '
                f'access_log {root}/audit.jsonl audit_json; '
                f'server {{ listen 127.0.0.1:{port}; location / {{ return 200 "ok"; }} }} }}',
                encoding='utf-8',
            )
            command = ['nginx', '-p', directory, '-c', str(config)]
            subprocess.run(command + ['-t'], check=True, capture_output=True)
            process = subprocess.Popen(command + ['-g', 'daemon off;'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                base = f'http://127.0.0.1:{port}'
                for _ in range(50):
                    try:
                        with urllib.request.urlopen(base + '/healthz.txt', timeout=1) as response:
                            self.assertEqual(response.status, 200)
                        break
                    except OSError:
                        time.sleep(0.05)
                else:
                    self.fail('隔离 Nginx 未启动')
                for route in (
                    '/api/v1/search/getBAHByID/synthetic-marker',
                    '/idcard/synthetic-marker',
                    '/api/v1/img/synthetic-marker',
                    '/unknown/synthetic-marker',
                    '/api/v1/search/getBAHByID/synthetic%2Dmarker?ticket=synthetic-secret',
                ):
                    with urllib.request.urlopen(base + route, timeout=2) as response:
                        self.assertEqual(response.status, 200)
            finally:
                process.terminate()
                process.wait(timeout=5)
            access = (root / 'access.log').read_text(encoding='utf-8')
            audit = (root / 'audit.jsonl').read_text(encoding='utf-8')
            for log in (access, audit):
                self.assertNotIn('synthetic-marker', log)
                self.assertNotIn('synthetic-secret', log)
                self.assertIn('/healthz.txt', log)
                self.assertEqual(len(log.splitlines()), 6)
            rows = [json.loads(line) for line in audit.splitlines()]
            self.assertEqual(rows[1]['uri'], '/api/v1/search/getBAHByID/[redacted]')
            self.assertEqual(rows[2]['uri'], '/idcard/[redacted]')
            self.assertEqual(rows[4]['uri'], '[route omitted]')


if __name__ == '__main__':
    unittest.main()
