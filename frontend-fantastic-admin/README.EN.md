[中文](./README.md) | **English**

# MRR Medical Record Repository Frontend

This directory contains the Vue 3 frontend for MRR. It supports medical record scanning, image review, analytics, access auditing, and system administration.

For the project overview, deployment instructions, and license, see the [repository README](../README.md).

## Local development

```bash
pnpm install
pnpm dev
```

The development server runs on `http://localhost:9000` by default and proxies API requests to `http://localhost:18045`.

## Common commands

```bash
pnpm lint:tsc
pnpm lint:eslint
pnpm lint:stylelint
pnpm test:run
pnpm build
```

## Technology

- Vue 3, TypeScript, and Vite
- Element Plus, UnoCSS, and Pinia
- Vue Router, Axios, and Vitest

## Structure

- `src/api`: backend APIs and type definitions
- `src/views`: application pages
- `src/components`: reusable business components
- `src/store`: Pinia stores
- `src/router`: routing and route guards

See [ENGINEERING.md](./ENGINEERING.md) for project conventions.
