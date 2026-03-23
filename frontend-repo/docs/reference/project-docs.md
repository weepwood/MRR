# 现有说明文档索引

当前仓库根目录已有多份说明文档，可按专题逐步迁移到 `docs/` 目录统一维护。

## 根目录文档

- `ADMIN_DASHBOARD_README.md`
- `MULTI_SELECT_PRINT_README.md`
- `功能演示说明.md`
- `多选Bug修复说明.md`
- `打印功能修复说明.md`
- `病案管理功能说明.md`
- `病案统计页面说明.md`
- `系统监控功能说明.md`

## 迁移建议

1. 每份文档迁移到 `docs/reference/` 的独立页面。
2. 在 `.vitepress/config.mts` 的 `sidebar` 中补充对应入口。
3. 迁移后删除重复文档，避免出现多份不一致说明。
