#!/usr/bin/env python3
"""Prepare the one-time MRR v0.7.4 release baseline."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREVIOUS_VERSION = "0.7.3"
VERSION = "0.7.4"
TAG = f"v{VERSION}"
RELEASE_DATE = "2026-07-24"

RELEASE_NOTES = f"""# MRR {VERSION} 发布说明

发布日期：{RELEASE_DATE}

## 版本定位

{VERSION} 是帮助文档交付方式修正版。单体 JAR 继续提供后端、Vue 管理端和 Springdoc 实时 API，但不再内嵌 VitePress 用户手册、开发文档和运维指南；帮助中心的三类文档入口改为系统设置中的可配置链接。

## 重点更新

- “系统设置 → 帮助与文档”新增用户使用手册、开发文档和运维指南三项链接配置；
- 帮助中心通过公开白名单接口读取三项文档链接，不暴露其他系统设置；
- 文档链接支持 `http://`、`https://` 和以单个 `/` 开头的站内路径，清空后可停用对应入口；
- 危险协议、协议相对地址和包含账号密码的 URL 会被拒绝；
- 实时 API 文档使用当前服务相对地址 `/swagger-ui/index.html#/`，不再绑定固定 IP 或端口；
- 单体 JAR 不再构建或内嵌 VitePress 文档，只保留后端和 Vue 管理端；
- Windows 离线 ZIP 继续携带外置用户手册和内部文档，供 Nginx 独立托管；
- 发布工作流增加文档外置策略检查，防止后续误将文档重新写入 JAR。

## 默认文档入口

Windows 离线包使用以下默认值：

- 用户使用手册：`/docs/`
- 开发文档：`/docs/internal/`
- 运维指南：`/docs/internal/deployment.html`

仅运行单体 JAR 时，应在系统设置中改为独立文档服务器地址，或者清空不需要的入口。

## 数据库与回滚

- 最低兼容迁移：`20260715113552`；
- 最高兼容迁移：`20260723163000`；
- 本版本不新增 Flyway 迁移；
- 配置结构版本保持 `1`；
- 三项文档链接使用现有系统设置键值表存储，不新增数据表或字段；
- 数据库和配置兼容 v{PREVIOUS_VERSION}，可以回滚到 v{PREVIOUS_VERSION}。回滚后会恢复旧版本的单体 JAR 文档内嵌行为，新增链接配置仍会保留但不会被旧版本使用。

## 正式资产

正式标签：`{TAG}`

- Windows 离线包：`MRR-v{VERSION}.zip`
- 单体 JAR：`MRR-v{VERSION}-standalone.jar`
- 单体 JAR 校验：`MRR-v{VERSION}-standalone.jar.sha256`
"""

CHANGELOG_SECTION = f"""## [{VERSION}] - {RELEASE_DATE}

### 新增

- 系统设置新增“帮助与文档”分类，可配置用户使用手册、开发文档和运维指南链接。
- 新增公开文档配置白名单接口，只向帮助中心返回三项文档 URL。

### 变更

- 单体 JAR 不再构建或内嵌 VitePress 文档，只包含后端、Vue 管理端和 Springdoc。
- Windows 离线包继续携带外置文档目录，由 Nginx 或独立文档服务提供。
- 帮助中心实时 API 文档改用相对地址 `/swagger-ui/index.html#/`，不再绑定固定 IP 和端口。
- 文档入口允许清空停用，并支持 `http://`、`https://` 和站内绝对路径。

### 安全

- 拒绝危险协议、协议相对地址、反斜杠路径以及包含账号密码的文档 URL。
- 普通用户只能读取三项白名单文档链接，不能读取其他系统设置。

### 发布与兼容性

- 新增文档外置打包策略测试，确保文档不会重新写入单体 JAR。
- 最低兼容迁移保持 `20260715113552`，最高兼容迁移保持 `20260723163000`，本版本不新增 Flyway 迁移。
- 配置结构版本保持 `1`，数据库与配置兼容 v{PREVIOUS_VERSION}，允许回滚到 v{PREVIOUS_VERSION}。
- 正式资产包含 `MRR-v{VERSION}.zip`、`MRR-v{VERSION}-standalone.jar` 及单体 JAR SHA-256 校验文件。

"""

USER_GUIDE_SECTION = f"""## {VERSION} 重点变化

### 文档链接可配置

- “系统设置 → 帮助与文档”可以维护用户使用手册、开发文档和运维指南链接；
- 链接可以指向独立文档服务器，也可以使用站内绝对路径；
- 清空某项链接后，帮助中心会显示该入口未配置；
- 帮助中心只读取三项白名单链接，不会公开其他系统设置。

### 单体 JAR 文档外置

- 单体 JAR 继续包含后端和 Vue 管理端，但不再包含 VitePress 用户手册、开发文档和运维指南；
- Windows 离线 ZIP 仍包含外置文档目录，并由 Nginx 提供默认 `/docs/` 路径；
- 仅运行单体 JAR 时，应把文档链接改为独立文档站地址。

### 访问与安全

- 实时 Swagger UI 使用 `/swagger-ui/index.html#/` 相对地址；
- 文档链接只接受 HTTP、HTTPS 或站内绝对路径；
- 本版本没有新增数据库迁移或配置结构变更，可以回滚到 v{PREVIOUS_VERSION}。

"""


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one occurrence of {old!r}, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    version_path = ROOT / "VERSION"
    current = version_path.read_text(encoding="utf-8").strip()
    if current not in {PREVIOUS_VERSION, VERSION}:
        raise RuntimeError(f"unexpected current VERSION: {current}")
    version_path.write_text(VERSION + "\n", encoding="utf-8")

    replace_once(
        ROOT / "backend-repo" / "pom.xml",
        f"<revision>{PREVIOUS_VERSION}</revision>",
        f"<revision>{VERSION}</revision>",
    )

    baseline_path = ROOT / "release-baseline.json"
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    baseline["database"]["backwardCompatibleWithPreviousApplication"] = True
    baseline["applicationRollback"] = {
        "allowed": True,
        "reason": (
            f"{VERSION} 未新增数据库迁移或配置结构变更，可以回滚到 {PREVIOUS_VERSION}。"
            f"回滚后会恢复 {PREVIOUS_VERSION} 的单体 JAR 文档内嵌行为；"
            "新增的三项文档链接仍保留在现有系统设置表中，但旧版本不会使用这些配置。"
            "回滚前仍应备份数据库、外部配置和完整发布目录。"
        ),
    }
    baseline_path.write_text(
        json.dumps(baseline, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    replace_once(
        ROOT / "README.md",
        f"当前值为 **{PREVIOUS_VERSION}**，正式标签为 `v{PREVIOUS_VERSION}`",
        f"当前值为 **{VERSION}**，正式标签为 `v{VERSION}`",
    )
    replace_once(
        ROOT / "vitepress-doc" / "internal" / "index.md",
        f"根目录 `VERSION` 当前为 **{PREVIOUS_VERSION}**，正式标签为 `v{PREVIOUS_VERSION}`",
        f"根目录 `VERSION` 当前为 **{VERSION}**，正式标签为 `v{VERSION}`",
    )

    changelog_path = ROOT / "CHANGELOG.md"
    changelog = changelog_path.read_text(encoding="utf-8")
    if f"## [{VERSION}]" not in changelog:
        marker = "## [Unreleased]\n\n"
        if marker not in changelog:
            raise RuntimeError("CHANGELOG.md missing Unreleased marker")
        changelog = changelog.replace(marker, marker + CHANGELOG_SECTION, 1)
        changelog_path.write_text(changelog, encoding="utf-8")

    user_notes_path = ROOT / "vitepress-doc" / "user-guide" / "release-notes.md"
    replace_once(
        user_notes_path,
        f"> 本页对应正式版本 **v{PREVIOUS_VERSION}**。",
        f"> 本页对应正式版本 **v{VERSION}**。",
    )
    user_notes = user_notes_path.read_text(encoding="utf-8")
    if f"## {VERSION} 重点变化" not in user_notes:
        marker = f"## {PREVIOUS_VERSION} 重点变化\n"
        if marker not in user_notes:
            raise RuntimeError("user release notes missing previous version marker")
        user_notes_path.write_text(
            user_notes.replace(marker, USER_GUIDE_SECTION + marker, 1),
            encoding="utf-8",
        )

    standalone_path = ROOT / "vitepress-doc" / "internal" / "standalone-jar.md"
    replace_once(
        standalone_path,
        "页面和 `/api/**` 都由同一个 JAR处理，不需要再把前端静态目录单独配置到 Nginx。",
        "页面和 `/api/**` 都由同一个 JAR处理，不需要再把前端静态目录单独配置到 Nginx。"
        "VitePress 用户手册、开发文档和运维指南不在 JAR 中，帮助中心入口应在“系统设置 → 帮助与文档”中指向独立文档服务。",
    )
    replace_once(
        standalone_path,
        "当前基线明确禁止只替换旧 JAR回滚。需要回滚时，应恢复发布前数据库、配置和完整应用目录备份。",
        f"当前 {VERSION} 基线允许回滚到 {PREVIOUS_VERSION}，因为没有新增数据库迁移或配置结构变更。"
        "回滚前仍应备份数据库、外部配置和完整应用目录，并重新验证文档入口与关键业务。",
    )

    deployment_path = ROOT / "vitepress-doc" / "internal" / "deployment.md"
    replace_once(
        deployment_path,
        "完整操作见 [单体 JAR 部署](./standalone-jar.md)。",
        "帮助中心的用户使用手册、开发文档和运维指南入口由系统设置维护，可以指向 Windows 包中的外置文档或独立文档服务。\n\n"
        "完整操作见 [单体 JAR 部署](./standalone-jar.md)。",
    )

    release_notes_path = ROOT / "release-notes" / f"{VERSION}.md"
    release_notes_path.parent.mkdir(parents=True, exist_ok=True)
    release_notes_path.write_text(RELEASE_NOTES, encoding="utf-8")


if __name__ == "__main__":
    main()
