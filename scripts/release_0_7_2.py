from __future__ import annotations

import json
from pathlib import Path

VERSION = "0.7.2"


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one occurrence of {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    Path("VERSION").write_text(f"{VERSION}\n", encoding="utf-8")

    replace_once(
        "backend-repo/pom.xml",
        "<revision>0.7.0</revision>",
        f"<revision>{VERSION}</revision>",
    )

    baseline_path = Path("release-baseline.json")
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    baseline["applicationRollback"]["allowed"] = False
    baseline["applicationRollback"]["reason"] = (
        "0.7.2 新增结构化运行错误中心、错误编号、WARN/ERROR 聚合、独立错误权限和 "
        "system_error_event 表，并正式提供单体 JAR 发布资产。旧应用不了解新的权限与错误状态流转；"
        "回滚前必须恢复发布前数据库、配置和完整发布目录备份，不能仅替换旧 JAR。"
    )
    baseline_path.write_text(
        json.dumps(baseline, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    changelog_path = Path("CHANGELOG.md")
    changelog = changelog_path.read_text(encoding="utf-8")
    marker = "## [Unreleased]\n\n## [0.7.0] - 2026-07-23"
    section = """## [Unreleased]

## [0.7.2] - 2026-07-24

### 新增

- 新增后端运行错误中心，聚合脱敏后的 WARN/ERROR，支持错误编号、Request ID、指纹、状态和最近发生记录。
- 正式发布前端内嵌的单体 JAR，默认端口为 `8002`，同时生成 SHA-256 校验文件。
- 新增运维诊断中心完整用户指南、单体 JAR 部署说明和运行错误中心内部文档。

### 变更

- 新增 `system:error:read` 和 `system:error:manage` 独立权限，错误查看与处理状态修改分离。
- 运维诊断文档按后端实际 Controller 补充 `system:manage`、`record:read`、`role:read` 和 `log:read` 组合权限。
- 更新项目总览、部署、权限、影像排障和版本边界说明。

### 修复

- 未处理的 500 异常现在返回 `ERR-yyyyMMdd-xxxxxxxx` 错误编号和 `X-Error-Id`，避免直接暴露内部异常。
- 对密码、Token、Secret、Ticket、Access Key、身份证号和堆栈中的敏感信息进行统一脱敏。
- 修正文档将 `main` 未发布能力误写为 v0.7.0 正式资产的问题。

### 发布与兼容性

- 最低兼容迁移为 `20260715113552`，最高兼容迁移为 `20260723163000`。
- 新增 `system_error_event` 表，不能仅替换回 0.7.0 JAR；回滚前必须恢复数据库、配置和完整发布目录备份。
- 正式资产同时包含 `MRR-v0.7.2.zip`、`MRR-v0.7.2-standalone.jar` 及对应校验文件。

## [0.7.0] - 2026-07-23"""
    if changelog.count(marker) != 1:
        raise SystemExit("CHANGELOG.md release marker not found exactly once")
    changelog_path.write_text(changelog.replace(marker, section, 1), encoding="utf-8")

    readme_old = (
        "> 根目录 [`VERSION`](VERSION) 是唯一产品版本源，当前值为 **0.7.0**。"
        "`main` 已包含 `v0.7.0` 标签之后的未发布变更，包括运行错误中心和单体 JAR Release；"
        "正式版本、数据库兼容范围和回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。"
    )
    readme_new = (
        "> 根目录 [`VERSION`](VERSION) 是唯一产品版本源，当前值为 **0.7.2**，正式标签为 `v0.7.2`。"
        "正式版本、数据库兼容范围和回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。"
    )
    replace_once("README.md", readme_old, readme_new)

    internal_old = (
        "> 面向开发、测试、数据库、部署和运维人员。根目录 `VERSION` 当前为 **0.7.0**；"
        "`main` 已包含 `v0.7.0` 之后的未发布变更。事实来源依次为当前代码与 Flyway、"
        "`application.properties`、`VERSION`、`release-baseline.json`、自动化测试和运行中的 OpenAPI。"
    )
    internal_new = (
        "> 面向开发、测试、数据库、部署和运维人员。根目录 `VERSION` 当前为 **0.7.2**，"
        "正式标签为 `v0.7.2`。事实来源依次为当前代码与 Flyway、`application.properties`、"
        "`VERSION`、`release-baseline.json`、自动化测试和运行中的 OpenAPI。"
    )
    replace_once("vitepress-doc/internal/index.md", internal_old, internal_new)

    release_doc_path = Path("vitepress-doc/user-guide/release-notes.md")
    release_doc = release_doc_path.read_text(encoding="utf-8")
    marker = "## 0.7.0 重点变化"
    if marker not in release_doc:
        raise SystemExit("user release notes 0.7.0 marker not found")
    remainder = release_doc.split(marker, 1)[1]
    new_prefix = """# 更新说明

> 本页对应正式版本 **v0.7.2**。完整提交历史见 [Git 更新记录](./changelog)，数据库和回滚条件见内部文档中的发布基线。

## 0.7.2 重点变化

### 运行错误中心

- 聚合后端 WARN/ERROR，支持错误编号、Request ID、指纹、状态和最近发生记录；
- 未处理 500 异常会返回错误编号和 `X-Error-Id`；
- 错误查看与状态修改使用独立权限；
- 敏感凭据、身份证号和堆栈信息统一脱敏。

### 单体 JAR

- GitHub Release 同时提供前端内嵌的单体 JAR；
- 默认端口为 `8002`，可以通过 `SERVER_PORT` 覆盖；
- 同时提供 SHA-256 校验文件；
- Windows 离线 ZIP 仍是包含 Nginx、WinSW、文档和管理脚本的完整部署方案。

### 文档与运维

- 新增运维诊断中心完整指南；
- 补充全面体检、维护模式、图片诊断、权限矩阵和操作审计的真实组合权限；
- 完善图片预览、Nginx/OSS 回退与 ZIP/PDF 后台任务的排障关系；
- 明确版本标签、主分支能力、数据库迁移和回滚边界。

## 0.7.0 重点变化"""
    release_doc_path.write_text(new_prefix + remainder, encoding="utf-8")

    release_notes = """# MRR 0.7.2 发布说明

发布日期：2026-07-24

## 版本定位

0.7.2 是基于 0.7.0 的运维与交付增强版本，正式纳入运行错误中心、错误编号、独立错误权限和单体 JAR 发布资产，并同步完善部署、权限与影像排障文档。仓库未发布 v0.7.1，因此本次版本号直接从 0.7.0 提升到 0.7.2。

## 重点更新

- 新增运行错误中心，聚合后端 WARN/ERROR，支持错误指纹、发生次数、最近记录和处理状态；
- 未处理 500 异常生成 `ERR-yyyyMMdd-xxxxxxxx` 错误编号，并通过响应消息和 `X-Error-Id` 返回；
- 新增 `system:error:read` 和 `system:error:manage` 权限，分离错误查看与处理操作；
- 正式 Release 同时提供 Windows 离线 ZIP 和前端内嵌的单体 JAR；
- 单体 JAR 默认端口为 `8002`，支持 `SERVER_PORT` 覆盖，并附带 SHA-256 校验文件；
- 完善运维诊断、单体 JAR 部署、运行错误、权限和影像来源排障文档。

## 安全与稳定性

- 密码、Token、Secret、Ticket、Access Key、身份证号和敏感堆栈内容统一脱敏；
- 错误事件按日志级别、记录器、异常类型和规范化消息生成指纹，避免重复事件无限膨胀；
- 运行错误采集采用异步处理，并避免应用关闭阶段产生采集自循环；
- 诊断报告继续隐藏账号、客户端 IP、查询参数和原始错误文本。

## 升级前检查

- 备份 PostgreSQL、外部配置、密钥、Nginx 配置和当前完整发布目录；
- 确认数据库迁移不高于 `20260723163000`；
- 检查数据库、Flyway、Nginx 图片源、OSS、临时目录空间和最近备份；
- 使用 SHA-256 文件校验 Windows ZIP 或单体 JAR；
- 升级后核对前端、后端、文档、`/actuator/info` 和 `manifest.json` 的版本与 Commit。

## 数据库与回滚

- 最低兼容迁移：`20260715113552`；
- 最高兼容迁移：`20260723163000`；
- 新增 `system_error_event` 表；
- 不允许仅替换回 0.7.0 JAR。回滚前必须恢复发布前数据库、配置和完整发布目录备份。

## 正式资产

正式标签：`v0.7.2`

- Windows 离线包：`MRR-v0.7.2.zip`
- 单体 JAR：`MRR-v0.7.2-standalone.jar`
- 单体 JAR 校验：`MRR-v0.7.2-standalone.jar.sha256`
"""
    Path("release-notes/0.7.2.md").write_text(release_notes, encoding="utf-8")


if __name__ == "__main__":
    main()
