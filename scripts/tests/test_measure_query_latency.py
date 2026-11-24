import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import measure_query_latency as bench


class LatencyEvidenceTest(unittest.TestCase):
    def test_rejects_empty_wrong_count_and_malformed_response(self):
        self.assertEqual(bench.response_status(200, {"code": 200, "data": []}, 2), "COUNT_MISMATCH")
        self.assertEqual(bench.response_status(200, {"code": 200, "data": [{}]}, 2), "COUNT_MISMATCH")
        self.assertEqual(bench.response_status(200, {"code": 200, "data": [{}, {}]}, 2), "200")
        self.assertEqual(bench.response_status(200, [], 2), "INVALID_RESULT")
        self.assertEqual(bench.response_status(200, {"code": 500, "data": [{}, {}]}, 2), "INVALID_RESULT")
        self.assertEqual(bench.response_status(201, {"code": 200, "data": [{}, {}]}, 2), "201")

    def test_report_rejects_warmup_failure_and_records_throughput(self):
        report = bench.build_report("logs-off", "case", 2, 1, 2,
                                    [(4, "200"), (8, "200")],
                                    ["200"] * 4 + ["FAILED"], 0.5)
        self.assertFalse(report["valid"])
        self.assertEqual(report["successThroughputPerSecond"], 4)
        self.assertEqual(report["warmupStatuses"], {"200": 4, "FAILED": 1})
        self.assertEqual(report["failureRate"], 0)
        self.assertEqual(report["p95Ms"], 8)
        failed = bench.build_report("logs-off", "case", 2, 1, 2,
                                    [(4, "200"), (8, "COUNT_MISMATCH")], ["200"] * 5, 0.5)
        self.assertFalse(failed["valid"])
        self.assertEqual(failed["failureRate"], 0.5)
        self.assertEqual(failed["successThroughputPerSecond"], 2)

    def test_baseline_requires_same_version_case_count_and_valid_metrics(self):
        base = bench.build_report("logs-off", "case", 2, 1, 2,
                                  [(10, "200"), (10, "200")], ["200"] * 5, 0.5)
        current = bench.build_report("logs-on", "case", 2, 1, 2,
                                     [(10, "200"), (11, "200")], ["200"] * 5, 0.5)
        self.assertTrue(bench.compare_baseline(base, current)["comparable"])
        self.assertTrue(bench.compare_baseline(base, current)["withinTenPercentTarget"])
        for change in ({"schemaVersion": 1}, {"caseId": "other"}, {"expectedCount": 3},
                       {"p95Ms": 0}, {"p95Ms": float("nan")}, {"valid": False},
                       {"warmupStatuses": {"FAILED": 5}}, {"statuses": {"500": 2}}):
            self.assertFalse(bench.compare_baseline({**base, **change}, current)["comparable"])
        self.assertFalse(bench.compare_baseline([], current)["comparable"])

if __name__ == "__main__":
    unittest.main()
