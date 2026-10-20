import unittest

from scripts.engineering_preflight import check_version


class EngineeringPreflightTest(unittest.TestCase):
    def test_rejects_wrong_java_and_missing_command(self):
        self.assertFalse(check_version("java", 'openjdk version "17.0.1"')[0])
        self.assertFalse(check_version("java", "")[0])

    def test_accepts_java21_and_supported_node(self):
        self.assertTrue(check_version("java", 'openjdk version "21.0.12"')[0])
        for version in ("v24.15.0", "v24.16.1", "v26.0.0"):
            self.assertTrue(check_version("node", version)[0])
        for version in ("v22.0.0", "v24.14.0", "v25.0.0"):
            self.assertFalse(check_version("node", version)[0])

    def test_requires_pinned_pnpm(self):
        self.assertTrue(check_version("pnpm", "10.33.0")[0])
        self.assertFalse(check_version("pnpm", "10.32.0")[0])
