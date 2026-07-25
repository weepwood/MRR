#!/usr/bin/env python3
"""MRR pull-request governance checks.

The guard blocks only non-negotiable repository rules. Heuristics such as PR size
and missing adjacent tests are warnings during the first phase.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence

REQUIRED_SECTIONS = (
    "变更说明",
    "关联事项",
    "风险等级",
    "验证证据",
    "回滚方案",
    "AI 参与说明",
)

MIGRATION_PREFIX = "backend-repo/src/main/resources/db/migration/"
MIGRATION_RE = re.compile(r"(?:^|/)V[^/]+\.sql$", re.IGNORECASE)
ISSUE_RE = re.compile(r"(?:#\d+|https://github\.com/[^\s]+/issues/\d+)", re.IGNORECASE)
RISK_RE = re.compile(
    r"(?:^|\n)\s*(?:[-*]\s*)?(?:风险等级\s*[:：]?\s*)?`?(P[0-3])`?",
    re.IGNORECASE,
)
NO_ISSUE_RE = re.compile(r"无关联\s*Issue\s*[:：].{4,}", re.IGNORECASE)
PLACEHOLDER_RE = re.compile(r"(?:TODO|TBD|待填写|请填写|<!--)", re.IGNORECASE)

SENSITIVE_BASENAMES = {
    ".env",
    "application-local.properties",
    "application-prod.properties",
    "application-production.properties",
    "credentials.json",
    "secrets.json",
    "id_rsa",
    "id_dsa",
    "id_ecdsa",
    "id_ed25519",
}
SENSITIVE_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore"}
TEMPLATE_MARKERS = (".example", ".template", ".sample")
ENV_FILE_RE = re.compile(r"^\.env(?:\..+)?$", re.IGNORECASE)
APPLICATION_CONFIG_RE = re.compile(
    r"^application-(?:local|prod|production)(?:[-.].*)?\.(?:properties|ya?ml)$",
    re.IGNORECASE,
)
SECRET_CONFIG_RE = re.compile(
    r"^(?:credentials?|secrets?)(?:[-_.].*)?\.(?:json|ya?ml)$",
    re.IGNORECASE,
)

DOMAIN_RULES = {
    "frontend": ("frontend-fantastic-admin/",),
    "backend": ("backend-repo/src/main/java/",),
    "database": (
        "backend-repo/src/main/resources/db/",
        "mrr-db/",
    ),
    "deployment": ("deploy/", ".github/workflows/", "docker-compose"),
    "documentation": ("docs/", "vitepress-doc/", "README.md", "CHANGELOG.md"),
}

BACKEND_TEST_PREFIX = "backend-repo/src/test/"
FRONTEND_TEST_MARKERS = ("/__tests__/", ".test.", ".spec.", "/e2e/")


@dataclass
class Change:
    status: str
    path: str
    old_path: str | None = None
    additions: int | None = None
    deletions: int | None = None


@dataclass
class Report:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.errors


def heading_content(body: str, heading: str) -> str | None:
    pattern = re.compile(
        rf"(?ms)^##+\s*{re.escape(heading)}\s*$\n(.*?)(?=^##+\s|\Z)"
    )
    match = pattern.search(body or "")
    return match.group(1).strip() if match else None


def validate_pr_body(body: str, actor: str = "", enforce: bool = True) -> Report:
    report = Report()
    body = body or ""
    if actor.lower().startswith("dependabot"):
        report.warnings.append(
            "Dependabot PR 使用自动生成正文；跳过模板完整性阻塞，但仍需人工审查依赖风险"
        )
        return report
    if not enforce:
        report.warnings.append(
            "该 PR 创建于严格治理启用前；仅执行代码与迁移硬性检查"
        )
        return report

    for section in REQUIRED_SECTIONS:
        content = heading_content(body, section)
        if content is None:
            report.errors.append(f"PR 正文缺少必填章节：{section}")
        elif not content or PLACEHOLDER_RE.search(content):
            report.errors.append(f"PR 章节“{section}”仍为空或包含占位内容")

    relation = heading_content(body, "关联事项") or ""
    if not ISSUE_RE.search(relation) and not NO_ISSUE_RE.search(relation):
        report.errors.append(
            "关联事项必须引用 Issue，或写明“无关联 Issue：具体原因”"
        )

    risk = heading_content(body, "风险等级") or ""
    risk_match = RISK_RE.search(risk)
    if not risk_match:
        report.errors.append("风险等级必须明确填写 P0、P1、P2 或 P3")
    else:
        report.notes.append(f"声明风险等级：{risk_match.group(1).upper()}")
    return report


def parse_name_status(text: str) -> list[Change]:
    changes: list[Change] = []
    for raw in text.splitlines():
        if not raw.strip():
            continue
        parts = raw.split("\t")
        status = parts[0]
        if status.startswith(("R", "C")) and len(parts) >= 3:
            changes.append(Change(status=status[0], old_path=parts[1], path=parts[2]))
        elif len(parts) >= 2:
            changes.append(Change(status=status[0], path=parts[1]))
    return changes


def apply_numstat(changes: list[Change], text: str) -> None:
    by_path = {change.path: change for change in changes}
    for raw in text.splitlines():
        if not raw.strip():
            continue
        parts = raw.split("\t")
        if len(parts) < 3:
            continue
        additions = None if parts[0] == "-" else int(parts[0])
        deletions = None if parts[1] == "-" else int(parts[1])
        path = parts[-1]
        change = by_path.get(path)
        if change:
            change.additions = additions
            change.deletions = deletions


def is_versioned_migration(path: str) -> bool:
    return path.startswith(MIGRATION_PREFIX) and bool(MIGRATION_RE.search(path))


def is_template_path(path: str) -> bool:
    basename = Path(path.lower()).name
    return any(marker in basename for marker in TEMPLATE_MARKERS)


def is_sensitive_path(path: str) -> bool:
    normalized = path.lower()
    basename = Path(normalized).name
    if is_template_path(normalized):
        return False
    if basename in SENSITIVE_BASENAMES:
        return True
    if ENV_FILE_RE.fullmatch(basename):
        return True
    if APPLICATION_CONFIG_RE.fullmatch(basename):
        return True
    if SECRET_CONFIG_RE.fullmatch(basename):
        return True
    return any(normalized.endswith(suffix) for suffix in SENSITIVE_SUFFIXES)


def classify_domains(paths: Iterable[str]) -> set[str]:
    domains: set[str] = set()
    for path in paths:
        for domain, prefixes in DOMAIN_RULES.items():
            if any(path.startswith(prefix) or path == prefix for prefix in prefixes):
                domains.add(domain)
    return domains


def validate_changes(changes: Sequence[Change]) -> Report:
    report = Report()
    paths = [change.path for change in changes]

    for change in changes:
        migration_paths = [change.path]
        if change.old_path:
            migration_paths.append(change.old_path)
        if any(is_versioned_migration(path) for path in migration_paths):
            if change.status != "A":
                prefix = f"{change.old_path} -> " if change.old_path else ""
                report.errors.append(
                    "禁止修改、重命名或删除既有 Flyway 版本迁移："
                    f"{prefix}{change.path}"
                )
        if is_sensitive_path(change.path):
            report.errors.append(f"禁止提交本地配置或凭据文件：{change.path}")

    domains = classify_domains(paths)
    if len(domains) >= 3:
        report.warnings.append(
            "本 PR 同时跨越多个工程域："
            + "、".join(sorted(domains))
            + "；建议拆分或说明不可拆分原因"
        )

    changed_files = len(changes)
    additions = sum(change.additions or 0 for change in changes)
    deletions = sum(change.deletions or 0 for change in changes)
    if changed_files > 50 or additions + deletions > 1500:
        report.warnings.append(
            f"变更规模较大：{changed_files} 个文件，约 {additions + deletions} 行增删；"
            "建议拆分并进行分阶段审查"
        )

    backend_code = any(
        path.startswith("backend-repo/src/main/java/") for path in paths
    )
    backend_tests = any(path.startswith(BACKEND_TEST_PREFIX) for path in paths)
    if backend_code and not backend_tests:
        report.warnings.append(
            "后端业务代码发生变化，但本 PR 未修改后端测试；"
            "请说明复用的既有测试或补充测试"
        )

    frontend_code = any(
        path.startswith("frontend-fantastic-admin/src/") for path in paths
    )
    frontend_tests = any(
        any(marker in path for marker in FRONTEND_TEST_MARKERS) for path in paths
    )
    if frontend_code and not frontend_tests:
        report.warnings.append(
            "前端源码发生变化，但本 PR 未修改前端测试；"
            "请说明手工证据或补充回归测试"
        )

    if not changes:
        report.warnings.append("没有检测到相对 base 的文件变化")
    return report


def merge_reports(*reports: Report) -> Report:
    merged = Report()
    for report in reports:
        merged.errors.extend(report.errors)
        merged.warnings.extend(report.warnings)
        merged.notes.extend(report.notes)
    return merged


def run_git(args: Sequence[str]) -> str:
    completed = subprocess.run(
        ["git", *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout


def load_event(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as file:
        return json.load(file)


def pull_request_author(event: dict) -> str:
    """Return the PR author, not the user who triggered the current event."""
    pull_request = event.get("pull_request") or {}
    return ((pull_request.get("user") or {}).get("login") or "").strip()


def render_markdown(report: Report, changes: Sequence[Change]) -> str:
    lines = ["# MRR AI 工程治理检查", ""]
    lines.append(f"- 变更文件：{len(changes)}")
    lines.append(f"- 阻塞问题：{len(report.errors)}")
    lines.append(f"- 警告：{len(report.warnings)}")
    lines.append("")
    for title, items, icon in (
        ("阻塞问题", report.errors, "❌"),
        ("警告", report.warnings, "⚠️"),
        ("说明", report.notes, "ℹ️"),
    ):
        lines.append(f"## {title}")
        if items:
            lines.extend(f"- {icon} {item}" for item in items)
        else:
            lines.append("- 无")
        lines.append("")
    return "\n".join(lines)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", default=os.environ.get("GITHUB_EVENT_PATH"))
    parser.add_argument("--base", required=True)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--summary", default=os.environ.get("GITHUB_STEP_SUMMARY"))
    parser.add_argument("--enforce-from-pr", type=int, default=280)
    args = parser.parse_args(argv)

    if not args.event:
        parser.error("--event 或 GITHUB_EVENT_PATH 必须提供")

    event = load_event(args.event)
    pr = event.get("pull_request") or {}
    body = pr.get("body") or ""
    author = pull_request_author(event)
    pr_number = int(pr.get("number") or event.get("number") or 0)
    enforce_body = pr_number == 0 or pr_number >= args.enforce_from_pr

    name_status = run_git(
        ["diff", "--name-status", "--find-renames", f"{args.base}...{args.head}"]
    )
    changes = parse_name_status(name_status)
    numstat = run_git(["diff", "--numstat", f"{args.base}...{args.head}"])
    apply_numstat(changes, numstat)

    report = merge_reports(
        validate_pr_body(body, author, enforce_body), validate_changes(changes)
    )
    markdown = render_markdown(report, changes)
    print(markdown)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as file:
            file.write(markdown + "\n")
    return 0 if report.ok else 1


if __name__ == "__main__":
    sys.exit(main())
