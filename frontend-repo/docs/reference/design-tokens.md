# 设计变量对照（Figma ↔ CSS）

## 文件位置

- Figma Variables JSON：`docs/reference/figma-variables.json`
- 前端 Tokens：`src/assets/css/tokens.css`
- 样式入口：`src/assets/css/main.css`（已统一 `@import './tokens.css';`）

## 一键对应规则

每个设计变量都包含 `cssVar` 字段，可直接映射到前端变量名。

示例：

- `color/blue/600` → `--pmr-color-blue-600`
- `color/action/primary` → `--pmr-color-action-primary`
- `space/4` → `--pmr-space-4`
- `radius/xl` → `--pmr-radius-xl`

## 研发使用建议

1. 新页面优先使用 `--pmr-*` 新变量。
2. 历史页面保留 `--primary-color` 等旧变量也可正常工作（`tokens.css` 已做别名映射）。
3. 组件中优先使用语义变量（如 `--pmr-color-text-primary`），避免直接用色板值。

## 设计侧使用建议

1. 在 Figma 中维护变量名称与 JSON 完全一致（如 `color/bg/page`）。
2. 发布设计稿时同时提交变量版本号（`meta.version`）。
3. 变量改动后，先更新 JSON，再更新 `tokens.css`，最后执行前端回归。
