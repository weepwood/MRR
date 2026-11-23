import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class BackendShutdownBudgetTest(unittest.TestCase):
    """WinSW 强制结束前必须留够优雅停机与执行器排空时间。"""

    def test_service_stop_timeout_covers_graceful_shutdown_and_executors(self):
        service = (ROOT / 'deploy/windows/services/mrr-backend.xml').read_text(encoding='utf-8')
        prod = (ROOT / 'deploy/windows/templates/application-prod.properties').read_text(encoding='utf-8')
        config = (ROOT / 'backend-repo/src/main/java/com/zjcxph/imgapi/config/AsyncConfig.java').read_text(encoding='utf-8')

        stop_timeout = int(re.search(r'<stoptimeout>(\d+) sec</stoptimeout>', service).group(1))
        lifecycle = int(re.search(r'spring\.lifecycle\.timeout-per-shutdown-phase=(\d+)s', prod).group(1))
        executor_wait = int(re.search(r'SHUTDOWN_AWAIT_SECONDS = (\d+);', config).group(1))
        draining_executors = config.count('setAwaitTerminationSeconds(SHUTDOWN_AWAIT_SECONDS)')

        self.assertEqual(draining_executors, 2)
        other_waits = sum(map(int, re.findall(r'setAwaitTerminationSeconds\((\d+)\)', config)))
        self.assertGreaterEqual(stop_timeout, lifecycle + executor_wait * draining_executors + other_waits + 10)


if __name__ == '__main__':
    unittest.main()
