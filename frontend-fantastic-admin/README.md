**中文** | [English](./README.EN.md)

# MRR 管理系统前端

本目录是 MRR 的 Vue 3 前端应用，服务于病案扫描记录、影像调阅、统计分析、访问审计和系统管理。

完整的项目介绍、部署说明及许可证请参阅[仓库根目录 README](../README.md)。

## 本地开发

```bash
pnpm install
pnpm dev
```

开发服务器默认运行在 `http://localhost:9000`，后端 API 通过开发代理访问 `http://localhost:18045`。

## 常用命令

```bash
pnpm lint:tsc
pnpm lint:eslint
pnpm lint:stylelint
pnpm test:run
pnpm build
```

## 技术栈

- Vue 3、TypeScript、Vite
- Element Plus、UnoCSS、Pinia
- Vue Router、Axios、Vitest

## 目录说明

- `src/api`：后端接口与类型定义
- `src/views`：业务页面
- `src/components`：业务复用组件
- `src/store`：Pinia 状态管理
- `src/router`：路由与守卫

项目工程约定见 [ENGINEERING.md](./ENGINEERING.md)。
