# PMR 文档中心

这是项目的 VitePress 文档站点入口，用于统一维护产品说明、开发说明和部署说明。

## 你现在可以做什么

- 本地写文档并实时预览：`npm run docs:dev`
- 构建静态文档站：`npm run docs:build`
- 预览静态产物：`npm run docs:preview`

## 建议维护方式

- 新增说明文档时，优先放在 `docs/` 目录下。
- 历史说明文档可逐步迁移到 `docs/reference/`。
- 发布前执行一次 `npm run docs:build` 验证静态产物。
