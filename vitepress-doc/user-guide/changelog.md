---
title: 更新记录
description: 从 Git 第一父级提交历史自动生成的 MRR 项目更新记录
outline: [2, 3]
---

# 更新记录

> 本页由 `vitepress-doc/scripts/generate-git-changelog.mjs` 从 Git 提交历史自动生成，请勿手工维护提交列表。启动或构建 VitePress 文档时会自动刷新。

- 当前快照分支：`dev-no-login`
- 更新至：2026-07-17 · `f3782b0`
- 生成规则：读取当前分支最近 200 条第一父级提交，合并提交优先采用 Pull Request 描述首行

## 2026-07-17

- **修复** `patient` 将病区字段 `binqu` 更正为 `bingqu`（[`f3782b0`](https://github.com/weepwood/MRR/commit/f3782b051c61be25c5acd0e7fb4130560e23e525) · [#75](https://github.com/weepwood/MRR/pull/75)）
- **修复** `archive` 保留本地搜索次数与收藏状态（[`b3fcc78`](https://github.com/weepwood/MRR/commit/b3fcc78488b0a2fc88901ce0f8bcb18e6490263c)）
- **修复** 支持图片类型 `0` 表示暂未分类（[`0877fa6`](https://github.com/weepwood/MRR/commit/0877fa61dbb6000f53ec9f91c8e5ec0e74a9735f) · [#73](https://github.com/weepwood/MRR/pull/73)）
- **新增** `patient` 增加入院日期、病区和床位字段（[`bd764a9`](https://github.com/weepwood/MRR/commit/bd764a9e36ad0fe84bd7e536fd2b1b320df7be1d) · [#72](https://github.com/weepwood/MRR/pull/72)）
- **新增** 影像档案袋增加本地搜索记录（[`2858cd2`](https://github.com/weepwood/MRR/commit/2858cd2241643af815b57397d0df784d1c3cb876) · [#68](https://github.com/weepwood/MRR/pull/68)）
- **新增** 全局统一病案类型为 1～15（[`09a2be9`](https://github.com/weepwood/MRR/commit/09a2be915813c02fa4e469cd056e93d096f8da66) · [#71](https://github.com/weepwood/MRR/pull/71)）

## 生成方式

在 `vitepress-doc` 目录执行：

```bash
npm run docs:changelog
```

`docs:dev:user`、`docs:dev:internal`、`docs:build:user` 和 `docs:build:internal` 也会在启动或构建前自动刷新本页。

可通过环境变量 `MRR_CHANGELOG_LIMIT` 调整记录数量，允许范围为 1～1000。
