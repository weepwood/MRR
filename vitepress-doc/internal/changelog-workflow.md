---
title: 更新日志工作流
description: 在本地和 GitHub Actions 中生成包含 Git 提交、Pull Request 与 Issue 的更新日志
outline: [2, 3]
---

# 更新日志工作流

MRR 的更新日志由 `vitepress-doc/scripts/generate-git-changelog.mjs` 自动生成，输出到：

```text
vitepress-doc/user-guide/changelog.md
```

生成器始终以当前仓库的第一父级 Git 提交历史为基础。在提供 GitHub Token 时，还会补充：

- 已合并 Pull Request 的标题、作者和链接；
- PR 正文中 `Closes`、`Fixes`、`Resolves`、`Related to` 等关联的 Issue；
- 记录范围内独立关闭且不是 `not planned` 的 Issue；
- Squash Merge 提交末尾的 `(#PR编号)`；
- GitHub API 数据的本地缓存。

GitHub API 不可用时，生成器会自动降级为纯 Git 日志，不会阻止 VitePress 启动或构建。

## Windows 推荐流程

### 1. 准备环境

需要安装：

- Git；
- Node.js 20 或更高版本；
- npm；
- GitHub CLI，建议安装但不是强制要求。

首次使用 GitHub CLI 时执行：

```powershell
gh auth login
```

登录时选择能够访问 `weepwood/MRR` 私有仓库的 GitHub 账号。

### 2. 一键更新

在仓库的 `vitepress-doc` 目录执行：

```powershell
.\update-changelog.ps1
```

脚本会依次：

1. 检查当前分支是否配置上游分支；
2. 有上游分支时执行 `git pull --ff-only`；
3. 优先读取现有 `GITHUB_TOKEN` 或 `GH_TOKEN`；
4. 未设置环境变量时，尝试通过 `gh auth token` 获取临时令牌；
5. 生成 Git + PR + Issue 更新日志；
6. 显示 `user-guide/changelog.md` 的 Git 差异；
7. 清除脚本临时设置的 Token。

### 3. 常用参数

跳过拉取远程代码：

```powershell
.\update-changelog.ps1 -SkipPull
```

只读取本地 Git 历史，不访问 GitHub API：

```powershell
.\update-changelog.ps1 -GitOnly
```

两个参数可以组合：

```powershell
.\update-changelog.ps1 -SkipPull -GitOnly
```

## 手动执行

### 使用 GitHub CLI Token

```powershell
cd vitepress-doc

$env:GITHUB_TOKEN = gh auth token
$env:MRR_CHANGELOG_GITHUB = 'true'

npm run docs:changelog

git diff -- user-guide/changelog.md

Remove-Item Env:GITHUB_TOKEN
Remove-Item Env:MRR_CHANGELOG_GITHUB
```

### 使用个人访问令牌

```powershell
cd vitepress-doc

$env:GITHUB_TOKEN = 'github_pat_xxxxxxxxx'
$env:MRR_CHANGELOG_GITHUB = 'true'

npm run docs:changelog
```

令牌需要能够读取仓库内容、Issue 和 Pull Request。不要把令牌写入 Git 跟踪的文件。

### 纯 Git 模式

```powershell
cd vitepress-doc

$env:MRR_CHANGELOG_GITHUB = 'false'
npm run docs:changelog
```

## 验证与预览

运行更新日志单元测试：

```powershell
npm run docs:changelog:test
```

启动用户文档：

```powershell
npm run docs:dev:user
```

启动内部文档：

```powershell
npm run docs:dev:internal
```

构建两套文档：

```powershell
npm run docs:build
```

开发和构建命令会在执行 VitePress 前自动刷新更新日志。

## 提交生成结果

确认内容正确后，在仓库根目录执行：

```powershell
git add vitepress-doc/user-guide/changelog.md
git commit -m "docs(changelog): refresh GitHub changes"
git push
```

更新日志是生成文件，不应直接手工编辑条目。需要调整格式或筛选规则时，应修改生成器及其测试。

## 环境变量

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `GITHUB_TOKEN` / `GH_TOKEN` | 无 | GitHub API 访问令牌 |
| `MRR_CHANGELOG_GITHUB` | 自动 | `true` 强制启用，`false` 强制禁用 |
| `MRR_CHANGELOG_BASE_BRANCH` | 远程默认分支 | 获取已合并 PR 的目标分支 |
| `MRR_CHANGELOG_CACHE_TTL` | `1800` | GitHub 缓存有效期，单位为秒 |
| `MRR_CHANGELOG_GITHUB_PAGES` | `10` | GitHub API 最大分页数，最大 20 |
| `MRR_GITHUB_REPOSITORY` | 从 `origin` 推导 | 仓库名称，例如 `weepwood/MRR` |

缓存文件位于：

```text
vitepress-doc/.cache/github-changelog.json
```

该目录已加入 `.gitignore`，不会提交到仓库。

## 降级规则

生成器按以下顺序处理 GitHub 数据：

1. 有效期内的本地缓存；
2. GitHub API 最新数据；
3. API 失败时使用过期缓存；
4. API 和缓存都不可用时，仅生成本地 Git 日志。

无论 GitHub API 是否可用，只要本地 Git 历史可以读取，更新日志生成过程都应成功结束。
