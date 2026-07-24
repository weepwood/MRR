#!/usr/bin/env python3
"""Prepare the repository state for the MRR 0.7.3 release."""

from __future__ import annotations

import json
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if text.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one occurrence of {old!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def main() -> None:
    Path("VERSION").write_text("0.7.3\n", encoding="utf-8")

    replace_once(
        "backend-repo/pom.xml",
        "<revision>0.7.2</revision>",
        "<revision>0.7.3</revision>",
    )

    baseline_path = Path("release-baseline.json")
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    baseline["database"]["backwardCompatibleWithPreviousApplication"] = True
    baseline["applicationRollback"]["allowed"] = True
    baseline["applicationRollback"]["reason"] = (
        "0.7.3 未新增数据库迁移或配置结构变更，可以回滚到 0.7.2。"
        "回滚后单体 JAR 不再内嵌用户手册和内部文档；仍应保留数据库、外部配置和完整发布目录备份。"
    )
    baseline_path.write_text(
        json.dumps(baseline, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    changelog_path = Path("CHANGELOG.md")
    changelog = changelog_path.read_text(encoding="utf-8")
    marker = "## [Unreleased]\n\n## [0.7.2] - 2026-07-24"
    section = """## [Unreleased]

## [0.7.3] - 2026-07-24

### 新增

- 单体 JAR 同时内嵌 Vue 管理端、用户手册和内部文档，直接运行一个 JAR 即可访问完整帮助内容。
- 单体 JAR 与 Windows 发布工作流增加三套静态站点的构建、嵌入和最终制品校验。

### 变更

- 帮助中心实时 API 文档入口统一为 `http://192.2.1.135:8002/swagger-ui/index.html#/`。
- 静态站点嵌入工具支持按 `/`、`/docs/`、`/docs/internal/` 挂载路径更新资源。
- README、用户手册和内部文档同步说明单体 JAR 已包含帮助文档。

### 修复

- 修复单体 JAR 发布资产缺少用户手册和内部文档的问题。
- 修复帮助中心 Swagger 链接仍指向旧后端端口 `18045` 的问题。
- 修复帮助页面原有样式未通过变更文件 Stylelint 检查的问题。

### 发布与兼容性

- 最低兼容迁移保持 `20260715113552`，最高兼容迁移保持 `20260723163000`，本版本不新增 Flyway 迁移。
- 配置结构版本保持 `1`，数据库与配置兼容 v0.7.2，可回滚到 v0.7.2。
- 正式资产包含 `MRR-v0.7.3.zip`、`MRR-v0.7.3-standalone.jar` 及单体 JAR SHA-256 校验文件。

## [0.7.2] - 2026-07-24"""
    if changelog.count(marker) != 1:
        raise SystemExit("CHANGELOG.md release marker not found exactly once")
    changelog_path.write_text(changelog.replace(marker, section, 1), encoding="utf-8")

    replace_once(
        "README.md",
        "> 根目录 [`VERSION`](VERSION) 是唯一产品版本源，当前值为 **0.7.2**，正式标签为 `v0.7.2`。正式版本、数据库兼容范围和回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。",
        "> 根目录 [`VERSION`](VERSION) 是唯一产品版本源，当前值为 **0.7.3**，正式标签为 `v0.7.3`。正式版本、数据库兼容范围和回滚条件以 [`release-baseline.json`](release-baseline.json) 为准。",
    )
    replace_once(
        "README.md",
        "| `MRR-vX.Y.Z-standalone.jar` | `8002` | 后端 + 内嵌 Vue 前端 | 直接运行、已有反向代理或轻量部署 |",
        "| `MRR-vX.Y.Z-standalone.jar` | `8002` | 后端 + 内嵌 Vue 前端 + 用户手册 + 内部文档 | 直接运行、已有反向代理或轻量部署 |",
    )
    replace_once(
        "README.md",
        "单体 JAR不包含数据库、Nginx、WinSW、文档或医院环境配置。",
        "单体 JAR 已包含管理端、用户手册、内部文档和实时 API 文档入口；不包含数据库、Nginx、WinSW 或医院环境配置。",
    )

    replace_once(
        "vitepress-doc/internal/index.md",
        "> 面向开发、测试、数据库、部署和运维人员。根目录 `VERSION` 当前为 **0.7.2**，正式标签为 `v0.7.2`。事实来源依次为当前代码与 Flyway、`application.properties`、`VERSION`、`release-baseline.json`、自动化测试和运行中的 OpenAPI。",
        "> 面向开发、测试、数据库、部署和运维人员。根目录 `VERSION` 当前为 **0.7.3**，正式标签为 `v0.7.3`。事实来源依次为当前代码与 Flyway、`application.properties`、`VERSION`、`release-baseline.json`、自动化测试和运行中的 OpenAPI。",
    )

    release_doc_path = Path("vitepress-doc/user-guide/release-notes.md")
    release_doc = release_doc_path.read_text(encoding="utf-8")
    release_marker = (
        "# 更新说明\n\n"
        "> 本页对应正式版本 **v0.7.2**。完整提交历史见 [Git 更新记录](./changelog)，"
        "数据库和回滚条件见内部文档中的发布基线。\n\n"
        "## 0.7.2 重点变化"
    )
    release_prefix = """# 更新说明

> 本页对应正式版本 **v0.7.3**。完整提交历史见 [Git 更新记录](./changelog)，数据库和回滚条件见内部文档中的发布基线。

## 0.7.3 重点变化

### 单体 JAR 完整帮助文档

- 单体 JAR 同时包含 Vue 管理端、用户手册和内部文档；
- 用户手册通过 `/docs/` 访问；
- 内部文档通过 `/docs/internal/` 访问；
- 实时 Swagger UI 通过 `/swagger-ui/index.html#/` 访问。

### 统一访问端口

- 单体 JAR 默认业务端口继续使用 `8002`；
- 帮助中心实时 API 文档链接由旧端口 `18045` 改为 `8002`；
- 管理端、帮助文档和实时 API 文档可以由同一个 JAR、同一个端口提供。

### 发布质量

- 发布工作流分别验证管理端、用户手册和内部文档的入口及构建资源；
- 静态资源嵌入支持独立挂载，避免前端和文档互相覆盖；
- 本版本没有新增数据库迁移或配置结构变更，可以回滚到 v0.7.2。

## 0.7.2 重点变化"""
    if release_doc.count(release_marker) != 1:
        raise SystemExit("user release notes marker not found exactly once")
    release_doc_path.write_text(
        release_doc.replace(release_marker, release_prefix, 1),
        encoding="utf-8",
    )

    release_notes = """# MRR 0.7.3 发布说明

发布日期：2026-07-24

## 版本定位

0.7.3 是单体交付完善版本。管理端、用户手册、内部文档和 Springdoc 实时 API 文档现在可以通过同一个可执行 JAR、同一个默认端口提供，减少内网轻量部署时需要单独准备文档站点的问题。

## 重点更新

- 单体 JAR 内嵌 Vue 管理端、VitePress 用户手册和内部文档；
- 用户手册入口为 `/docs/`，内部文档入口为 `/docs/internal/`；
- Swagger UI 入口统一为 `/swagger-ui/index.html#/`；
- 帮助中心实时 API 文档链接统一使用默认端口 `8002`；
- 单体 JAR和 Windows 发布工作流增加管理端、用户文档、内部文档三套静态资源的独立校验；
- 静态资源嵌入工具支持按挂载目录更新，避免不同站点的资源互相覆盖。

## 部署方式

直接运行：

```powershell
java -jar .\\MRR-v0.7.3-standalone.jar
```

默认入口：

- 管理端：`http://服务器地址:8002/`
- 用户手册：`http://服务器地址:8002/docs/`
- 内部文档：`http://服务器地址:8002/docs/internal/`
- 实时 API 文档：`http://服务器地址:8002/swagger-ui/index.html#/`

业务端口仍可通过 `SERVER_PORT` 环境变量覆盖。

## 数据库与回滚

- 最低兼容迁移：`20260715113552`；
- 最高兼容迁移：`20260723163000`；
- 本版本不新增 Flyway 迁移；
- 配置结构版本保持 `1`；
- 数据库和配置兼容 v0.7.2，可以回滚到 v0.7.2。回滚后单体 JAR 不再内嵌用户手册和内部文档。

## 正式资产

正式标签：`v0.7.3`

- Windows 离线包：`MRR-v0.7.3.zip`
- 单体 JAR：`MRR-v0.7.3-standalone.jar`
- 单体 JAR 校验：`MRR-v0.7.3-standalone.jar.sha256`
"""
    Path("release-notes/0.7.3.md").write_text(release_notes, encoding="utf-8")


if __name__ == "__main__":
    main()
