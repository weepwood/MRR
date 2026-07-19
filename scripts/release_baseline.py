#!/usr/bin/env python3
"""Validate and generate MRR release baseline metadata without third-party dependencies."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "VERSION"
BASELINE_FILE = ROOT / "release-baseline.json"
POM_FILE = ROOT / "backend-repo" / "pom.xml"
MIGRATION_DIR = ROOT / "backend-repo" / "src" / "main" / "resources" / "db" / "migration"
SEMVER_PATTERN = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$")
MIGRATION_PATTERN = re.compile(r"^V(\d{14})__[A-Za-z0-9_]+\.sql$")


class BaselineError(RuntimeError):
    pass


def read_version() -> str:
    version = VERSION_FILE.read_text(encoding="utf-8").strip()
    if not SEMVER_PATTERN.fullmatch(version):
        raise BaselineError(f"VERSION 必须是 SemVer，当前值为: {version!r}")
    return version


def read_baseline() -> dict[str, Any]:
    try:
        baseline = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise BaselineError(f"release-baseline.json 不是有效 JSON: {exc}") from exc
    if not isinstance(baseline, dict):
        raise BaselineError("release-baseline.json 顶层必须是对象")
    return baseline


def require_mapping(parent: dict[str, Any], key: str) -> dict[str, Any]:
    value = parent.get(key)
    if not isinstance(value, dict):
        raise BaselineError(f"release-baseline.json 缺少对象字段: {key}")
    return value


def validate_pom_version(version: str) -> None:
    root = ElementTree.parse(POM_FILE).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    project_version = root.findtext("m:version", namespaces=namespace)
    revision = root.findtext("m:properties/m:revision", namespaces=namespace)
    if project_version != "${revision}":
        raise BaselineError("backend-repo/pom.xml 的项目版本必须使用 ${revision}")
    if revision != version:
        raise BaselineError(f"pom.xml revision={revision!r} 与 VERSION={version!r} 不一致")


def migration_versions() -> list[str]:
    versions: list[str] = []
    invalid_names: list[str] = []
    for path in sorted(MIGRATION_DIR.glob("V*.sql")):
        match = MIGRATION_PATTERN.fullmatch(path.name)
        if match:
            versions.append(match.group(1))
        else:
            invalid_names.append(path.name)
    if invalid_names:
        raise BaselineError("迁移文件不符合 VyyyyMMddHHmmss__description.sql: " + ", ".join(invalid_names))
    if not versions:
        raise BaselineError("未找到正式 Flyway 迁移")
    if len(versions) != len(set(versions)):
        raise BaselineError("Flyway 正式迁移存在重复版本号")
    return versions


def validate() -> tuple[str, dict[str, Any]]:
    version = read_version()
    baseline = read_baseline()
    if baseline.get("manifestSchemaVersion") != 1:
        raise BaselineError("manifestSchemaVersion 当前只支持 1")

    database = require_mapping(baseline, "database")
    rollback = require_mapping(baseline, "applicationRollback")
    configuration = require_mapping(baseline, "configuration")

    minimum = database.get("minimumCompatibleMigration")
    maximum = database.get("maximumCompatibleMigration")
    if not isinstance(minimum, str) or not re.fullmatch(r"\d{14}", minimum):
        raise BaselineError("database.minimumCompatibleMigration 必须是 14 位迁移版本")
    if not isinstance(maximum, str) or not re.fullmatch(r"\d{14}", maximum):
        raise BaselineError("database.maximumCompatibleMigration 必须是 14 位迁移版本")
    if minimum > maximum:
        raise BaselineError("数据库最低兼容迁移不能晚于最高兼容迁移")
    if not isinstance(database.get("backwardCompatibleWithPreviousApplication"), bool):
        raise BaselineError("database.backwardCompatibleWithPreviousApplication 必须是布尔值")
    if not isinstance(rollback.get("allowed"), bool):
        raise BaselineError("applicationRollback.allowed 必须是布尔值")
    if rollback.get("allowed") is False and not str(rollback.get("reason", "")).strip():
        raise BaselineError("禁止应用回滚时必须填写 applicationRollback.reason")
    if not isinstance(configuration.get("schemaVersion"), int) or configuration["schemaVersion"] < 1:
        raise BaselineError("configuration.schemaVersion 必须是正整数")

    versions = migration_versions()
    if minimum not in versions:
        raise BaselineError(f"最低兼容迁移 V{minimum} 不存在于正式迁移目录")
    if maximum not in versions:
        raise BaselineError(f"最高兼容迁移 V{maximum} 不存在于正式迁移目录")
    if maximum != max(versions):
        raise BaselineError(
            f"最高兼容迁移 V{maximum} 不是当前最新正式迁移 V{max(versions)}，请显式更新发布基线"
        )

    validate_pom_version(version)
    return version, baseline


def resolve_git_commit(explicit: str | None) -> str:
    if explicit:
        return explicit
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def build_manifest(version: str, baseline: dict[str, Any], commit: str, build_time: str) -> dict[str, Any]:
    return {
        "manifestSchemaVersion": baseline["manifestSchemaVersion"],
        "productVersion": version,
        "gitCommit": commit,
        "buildTime": build_time,
        "database": baseline["database"],
        "applicationRollback": baseline["applicationRollback"],
        "configuration": baseline["configuration"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="MRR 发布基线校验与 manifest 生成")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("validate", help="校验 VERSION、Maven 版本、数据库迁移和兼容基线")

    manifest_parser = subparsers.add_parser("manifest", help="生成发布 manifest.json")
    manifest_parser.add_argument("--output", required=True, type=Path)
    manifest_parser.add_argument("--git-commit")
    manifest_parser.add_argument("--build-time")

    args = parser.parse_args()
    try:
        version, baseline = validate()
        if args.command == "validate":
            print(f"MRR release baseline valid: v{version}")
            return 0

        build_time = args.build_time or datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        manifest = build_manifest(version, baseline, resolve_git_commit(args.git_commit), build_time)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(args.output)
        return 0
    except (BaselineError, OSError, ElementTree.ParseError) as exc:
        print(f"release baseline error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
