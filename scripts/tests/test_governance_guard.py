import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "governance_guard.py"
SPEC = importlib.util.spec_from_file_location("governance_guard", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

VALID_BODY = """## 变更说明
修复明确问题，并限制在治理文件。

## 关联事项
Closes #279

## 风险等级
P2

## 验证证据
执行 Python 单元测试并通过。

## 回滚方案
回滚本 PR 即可。

## AI 参与说明
AI 负责初稿，人负责审查规则边界。
"""


class GovernanceGuardTest(unittest.TestCase):
    def test_valid_pr_body_passes(self):
        report = MODULE.validate_pr_body(VALID_BODY)
        self.assertEqual([], report.errors)

    def test_legacy_pr_body_is_warning_only(self):
        report = MODULE.validate_pr_body("", enforce=False)
        self.assertEqual([], report.errors)
        self.assertTrue(report.warnings)

    def test_dependabot_body_is_warning_only(self):
        report = MODULE.validate_pr_body("", actor="dependabot[bot]")
        self.assertEqual([], report.errors)
        self.assertTrue(report.warnings)

    def test_missing_issue_reference_fails(self):
        body = VALID_BODY.replace("Closes #279", "本次没有事项")
        report = MODULE.validate_pr_body(body)
        self.assertTrue(any("关联事项" in item for item in report.errors))

    def test_explicit_no_issue_reason_passes(self):
        body = VALID_BODY.replace("Closes #279", "无关联 Issue：仅修正文档错别字")
        report = MODULE.validate_pr_body(body)
        self.assertFalse(any("关联事项" in item for item in report.errors))

    def test_missing_risk_fails(self):
        body = VALID_BODY.replace("P2", "中等")
        report = MODULE.validate_pr_body(body)
        self.assertTrue(any("风险等级" in item for item in report.errors))

    def test_existing_migration_change_fails(self):
        changes = [
            MODULE.Change(
                "M",
                "backend-repo/src/main/resources/db/migration/V1__base.sql",
            )
        ]
        report = MODULE.validate_changes(changes)
        self.assertTrue(any("Flyway" in item for item in report.errors))

    def test_new_migration_is_allowed(self):
        changes = [
            MODULE.Change(
                "A",
                "backend-repo/src/main/resources/db/migration/V2__new.sql",
            )
        ]
        report = MODULE.validate_changes(changes)
        self.assertEqual([], report.errors)

    def test_migration_rename_fails(self):
        changes = [
            MODULE.Change(
                "R",
                "backend-repo/src/main/resources/db/migration/V2__renamed.sql",
                "backend-repo/src/main/resources/db/migration/V1__old.sql",
            )
        ]
        report = MODULE.validate_changes(changes)
        self.assertTrue(any("Flyway" in item for item in report.errors))

    def test_sensitive_file_fails_but_template_passes(self):
        bad = MODULE.validate_changes(
            [MODULE.Change("A", "backend-repo/application-local.properties")]
        )
        good = MODULE.validate_changes(
            [MODULE.Change("A", "backend-repo/application-local.template.properties")]
        )
        self.assertTrue(any("凭据" in item for item in bad.errors))
        self.assertEqual([], good.errors)

    def test_backend_code_without_tests_warns(self):
        report = MODULE.validate_changes(
            [MODULE.Change("M", "backend-repo/src/main/java/example/Service.java")]
        )
        self.assertTrue(any("后端测试" in item for item in report.warnings))

    def test_parse_rename(self):
        changes = MODULE.parse_name_status("R100\told.txt\tnew.txt\n")
        self.assertEqual("R", changes[0].status)
        self.assertEqual("old.txt", changes[0].old_path)
        self.assertEqual("new.txt", changes[0].path)


if __name__ == "__main__":
    unittest.main()
