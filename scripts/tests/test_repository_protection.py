import json
from pathlib import Path
import unittest


class RepositoryProtectionTest(unittest.TestCase):
    def test_single_maintainer_policy_requires_ci_without_approval_deadlock(self):
        path = Path(__file__).resolve().parents[2] / "quality/main-branch-protection.json"
        policy = json.loads(path.read_text(encoding="utf-8"))
        self.assertTrue(policy["enforce_admins"])
        self.assertTrue(policy["required_status_checks"]["strict"])
        self.assertEqual(0, policy["required_pull_request_reviews"]["required_approving_review_count"])
        self.assertFalse(policy["required_pull_request_reviews"]["require_code_owner_reviews"])
        self.assertTrue(policy["required_conversation_resolution"])
        self.assertFalse(policy["allow_force_pushes"])
        self.assertFalse(policy["allow_deletions"])
        self.assertEqual({"governance", "release-baseline-gate", "frontend-gate", "backend-gate",
                          "windows-management-gate"}, {
                              check["context"] for check in policy["required_status_checks"]["checks"]})
        self.assertTrue(all(check["app_id"] == 15368 for check in policy["required_status_checks"]["checks"]))
