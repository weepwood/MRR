# 快速开始

## 环境要求

- Node.js `18+`（建议 LTS）
- npm `9+`

## 安装依赖

```bash
npm install
```

## 启动业务前端

```bash
npm run dev
```

默认地址：`http://localhost:5173`

## 启动文档站点（本地调试）

```bash
npm run docs:dev
```

默认地址：`http://localhost:5173/docs/`（或命令行输出地址）。

## 构建文档

```bash
npm run docs:build
```

文档产物目录：`public/docs`

## 常见问题

### 文档页打不开

1. 先执行一次 `npm run docs:build`。
2. 确认 `public/docs/index.html` 已生成。
3. 再执行 `npm run dev` 或 `npm run docs:preview`。

### 文档改了但菜单不显示

1. 检查文件是否放在 `docs/guide` 或 `docs/reference`。
2. 检查 `docs/.vitepress/config.mts` 的 `sidebar` 是否已加入链接。
3. 重启 `npm run docs:dev`。
